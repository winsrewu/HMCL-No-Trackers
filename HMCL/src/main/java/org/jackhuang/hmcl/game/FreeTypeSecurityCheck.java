/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.util.platform.Bits;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Verifies the FreeType build that an instance would load before it is launched.
///
/// Since 1.20.5 Minecraft renders fonts through LWJGL's FreeType binding, so the native
/// library inside `org.lwjgl:lwjgl-freetype:<ver>:natives-<platform>` decides which FreeType
/// version the game executes. FreeType 2.15.0 fixes several security issues, including
/// CVE-2026-50811 (out-of-bounds read in `ttgxvar.c`) and CVE-2026-95512 (CID font loader
/// denial of service), so every build older than 2.15.0 is treated as unsafe.
///
/// Two situations are rejected:
///
/// 1. An affected native library is already present on disk.
/// 2. The instance uses FreeType and the selected runtime is 32-bit. No 2.15.0 build exists for
///    `natives-windows-x86`, so a 32-bit runtime can only load an affected build. The stale
///    x86 entries in the manifests are left untouched: removing them would turn the problem
///    into an `UnsatisfiedLinkError` at font initialization instead of an actionable message.
///
/// The version is read from the native library itself (PE `VERSIONINFO`), never guessed from
/// the LWJGL artifact version: `lwjgl-freetype:3.4.3` may embed either 2.14.3 or 2.15.0.
///
/// Only local files are inspected. This check never downloads, installs or repairs anything,
/// so downloading a patched library and launching remain possible; it only refuses to start
/// the game when an affected build would be loaded.
///
/// Detection currently covers the Windows `freetype.dll`; other platforms report
/// [Status#UNKNOWN], which is not blocking.
@NotNullByDefault
public final class FreeTypeSecurityCheck {
    /// The first FreeType release that contains the fixes.
    public static final String MINIMUM_SAFE_VERSION = "2.15.0";

    private static final VersionNumber MINIMUM_SAFE = VersionNumber.asVersion(MINIMUM_SAFE_VERSION);
    private static final String LWJGL_GROUP = "org.lwjgl";
    private static final String FREETYPE_ARTIFACT = "lwjgl-freetype";

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d{1,4})\\.(\\d{1,4})\\.(\\d{1,4})");
    private static final String[] VERSION_ANCHORS = {"FileVersion", "ProductVersion"};

    /// Result of inspecting one launch.
    @NotNullByDefault
    public enum Status {
        /// The build that would be loaded is 2.15.0 or newer.
        SAFE,

        /// The launch must not continue.
        VULNERABLE,

        /// No FreeType native was found, or its version could not be read. Not blocking,
        /// because it usually means the library has not been downloaded yet.
        UNKNOWN
    }

    /// Why a launch is blocked.
    @NotNullByDefault
    public enum Reason {
        /// Nothing is blocked.
        NONE,

        /// The native library that would be loaded is older than [#MINIMUM_SAFE_VERSION].
        OLD_NATIVE,

        /// The instance uses FreeType and the selected runtime is 32-bit.
        JAVA_32_BIT
    }

    /// One inspection result.
    ///
    /// @param status  the verdict
    /// @param reason  why the launch is blocked, [Reason#NONE] when it is not
    /// @param version the detected FreeType version, `null` when unknown
    /// @param source  the native library or runtime the verdict came from, `null` when unknown
    @NotNullByDefault
    public record Result(Status status, Reason reason, @Nullable String version, @Nullable String source) {
    }

    /// Inspects an instance without knowing the selected runtime.
    ///
    /// @param repository the game repository
    /// @param manifest   the resolved manifest of the instance
    /// @param instanceId the instance identifier
    /// @return the inspection result, never `null`
    public static @NotNull Result inspect(HMCLGameRepository repository,
                                          GameInstanceManifest manifest,
                                          GameInstanceID instanceId) {
        return inspect(repository, manifest, instanceId, null);
    }

    /// Inspects the native library and the runtime an instance would load.
    ///
    /// Extracted natives inside `natives-*/` take precedence, because LWJGL searches
    /// `java.library.path` before the jars on the classpath. When no extracted native exists,
    /// the `lwjgl-freetype` natives jar from the manifest is inspected.
    ///
    /// @param repository the game repository
    /// @param manifest   the resolved manifest of the instance
    /// @param instanceId the instance identifier
    /// @param java       the runtime that will be used, may be `null` when not resolved yet
    /// @return the inspection result, never `null`
    public static @NotNull Result inspect(HMCLGameRepository repository,
                                          GameInstanceManifest manifest,
                                          GameInstanceID instanceId,
                                          @Nullable JavaRuntime java) {
        if (java != null && java.getBits() == Bits.BIT_32 && hasFreetypeNatives(manifest)) {
            // No fixed build exists for the 32-bit natives, so this launch cannot be made safe
            // by replacing a file; the only remedy is a 64-bit runtime.
            return new Result(Status.VULNERABLE, Reason.JAVA_32_BIT,
                    java.getVersion(), java.getBinary().toString());
        }

        Path instanceRoot = repository.getInstanceRoot(instanceId);

        List<Result> extracted = new ArrayList<>();
        for (Path nativeFile : findExtractedNatives(instanceRoot)) {
            String version = detectVersion(readAllBytes(nativeFile));
            if (version != null)
                extracted.add(new Result(classify(version), Reason.OLD_NATIVE, version, nativeFile.toString()));
        }

        if (!extracted.isEmpty()) {
            // Several extracted copies may exist; the first one LWJGL finds is unknown, so any
            // affected copy blocks the launch.
            for (Result result : extracted) {
                if (result.status() == Status.VULNERABLE)
                    return result;
            }
            return extracted.get(0);
        }

        Library library = findFreetypeNatives(manifest);
        if (library == null)
            return new Result(Status.UNKNOWN, Reason.NONE, null, null);

        Path jar = repository.getLibraryFile(manifest, library);
        if (!Files.isRegularFile(jar))
            return new Result(Status.UNKNOWN, Reason.NONE, null, jar.toString());

        String version = readVersionFromJar(jar);
        if (version == null)
            return new Result(Status.UNKNOWN, Reason.NONE, null, jar.toString());

        return new Result(classify(version), Reason.OLD_NATIVE, version, jar.toString());
    }

    /// Shows the blocking dialog for an instance that must not be launched.
    ///
    /// Must be called on the JavaFX application thread.
    ///
    /// @param instanceId the instance identifier
    /// @param result     the inspection result, must be [Status#VULNERABLE]
    public static void showBlockedDialog(GameInstanceID instanceId, Result result) {
        String version = String.valueOf(result.version());
        String source = String.valueOf(result.source());

        LOG.error("Blocked the launch of " + instanceId.id() + ": FreeType check failed ("
                + result.reason() + ", version " + version + ", " + source + ")");

        String message = result.reason() == Reason.JAVA_32_BIT
                ? i18n("freetype.security.blocked_32bit", instanceId.id(), version, MINIMUM_SAFE_VERSION)
                : i18n("freetype.security.blocked", instanceId.id(), version, source, MINIMUM_SAFE_VERSION);

        Controllers.dialog(message, i18n("freetype.security.title"), MessageType.ERROR);
    }

    /// Reads the FreeType version out of a natives jar.
    ///
    /// @param jar the natives jar
    /// @return the detected version, or `null` when the jar has no readable FreeType native
    private static @Nullable String readVersionFromJar(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = null;
            for (ZipEntry candidate : zip.stream().toList()) {
                if (candidate.isDirectory())
                    continue;

                String name = candidate.getName().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".dll") && !name.endsWith(".so") && !name.endsWith(".dylib"))
                    continue;
                if (!name.contains("freetype"))
                    continue;

                entry = candidate;
                break;
            }

            if (entry == null)
                return null;

            try (InputStream input = zip.getInputStream(entry)) {
                return detectVersion(input.readAllBytes());
            }
        } catch (IOException e) {
            LOG.warning("Failed to read the FreeType native from " + jar, e);
            return null;
        }
    }

    /// Detects the version of a Windows native library from its `VERSIONINFO` resource.
    ///
    /// Reading the PE resource directory is unnecessary here: the version resource stores its
    /// strings as UTF-16LE, so the first version-like string after a `FileVersion` or
    /// `ProductVersion` key is the value Windows itself reports in the file properties.
    ///
    /// @param data the native library bytes, may be empty
    /// @return the detected version, or `null` when it cannot be read
    static @Nullable String detectVersion(byte[] data) {
        if (data.length == 0)
            return null;

        for (String anchor : VERSION_ANCHORS) {
            String version = findVersionAfter(data, anchor);
            if (version != null)
                return version;
        }

        return null;
    }

    /// Finds the first version-like UTF-16LE string after an anchor key.
    ///
    /// @param data   the native library bytes
    /// @param anchor the UTF-16LE key to look for
    /// @return the detected version, or `null`
    private static @Nullable String findVersionAfter(byte[] data, String anchor) {
        byte[] key = anchor.getBytes(StandardCharsets.UTF_16LE);
        int index = indexOf(data, key);
        if (index < 0)
            return null;

        int start = index + key.length;
        int end = Math.min(data.length, start + 512);
        if (end <= start)
            return null;

        String text = new String(data, start, end - start, StandardCharsets.UTF_16LE);
        Matcher matcher = VERSION_PATTERN.matcher(text);
        if (!matcher.find())
            return null;

        return matcher.group(1) + "." + matcher.group(2) + "." + matcher.group(3);
    }

    /// Returns whether a manifest declares any `lwjgl-freetype` natives, for any platform.
    ///
    /// @param manifest the resolved manifest
    /// @return `true` when the instance renders fonts through FreeType
    static boolean hasFreetypeNatives(GameInstanceManifest manifest) {
        for (Library library : manifest.getLibraries()) {
            String classifier = library.classifier();
            if (classifier == null || !classifier.startsWith("natives"))
                continue;
            if (LWJGL_GROUP.equals(library.groupId()) && FREETYPE_ARTIFACT.equals(library.artifactId()))
                return true;
        }

        return false;
    }

    /// Finds the FreeType natives library of the current platform in a manifest.
    ///
    /// @param manifest the resolved manifest
    /// @return the matching library, or `null` when the instance does not use FreeType
    private static @Nullable Library findFreetypeNatives(GameInstanceManifest manifest) {
        for (Library library : manifest.getLibraries()) {
            if (!library.appliesToCurrentEnvironment())
                continue;
            if (!LWJGL_GROUP.equals(library.groupId()) || !FREETYPE_ARTIFACT.equals(library.artifactId()))
                continue;

            String classifier = library.classifier();
            if (classifier != null && classifier.startsWith("natives"))
                return library;
        }

        return null;
    }

    /// Lists the `freetype` natives that were already extracted into the instance directory.
    ///
    /// @param instanceRoot the instance root
    /// @return the extracted native files, empty when there are none
    private static @NotNull List<Path> findExtractedNatives(Path instanceRoot) {
        List<Path> natives = new ArrayList<>();
        if (!Files.isDirectory(instanceRoot))
            return natives;

        try (Stream<Path> children = Files.list(instanceRoot)) {
            for (Path child : children.toList()) {
                if (!Files.isDirectory(child))
                    continue;

                String name = child.getFileName().toString();
                if (!name.startsWith("natives-"))
                    continue;

                try (Stream<Path> files = Files.walk(child, 8)) {
                    files.filter(Files::isRegularFile)
                            .filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).startsWith("freetype."))
                            .forEach(natives::add);
                }
            }
        } catch (IOException e) {
            LOG.warning("Failed to scan the natives of " + instanceRoot, e);
        }

        return natives;
    }

    /// Classifies a detected version against the minimum safe build.
    ///
    /// @param version the detected version, e.g. `2.13.2`
    /// @return [Status#SAFE] when the version is at least [#MINIMUM_SAFE_VERSION]
    static @NotNull Status classify(String version) {
        return VersionNumber.asVersion(version).compareTo(MINIMUM_SAFE) >= 0
                ? Status.SAFE
                : Status.VULNERABLE;
    }

    /// Reads all bytes of a file, returning an empty array when it cannot be read.
    ///
    /// @param file the file
    /// @return the file content, or an empty array
    private static byte[] readAllBytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            LOG.warning("Failed to read " + file, e);
            return new byte[0];
        }
    }

    /// Returns the first index of a byte sequence.
    ///
    /// @param data   the haystack
    /// @param needle the needle
    /// @return the index, or `-1` when not found
    private static int indexOf(byte[] data, byte[] needle) {
        outer:
        for (int i = 0; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j])
                    continue outer;
            }
            return i;
        }
        return -1;
    }

    private FreeTypeSecurityCheck() {
    }
}
