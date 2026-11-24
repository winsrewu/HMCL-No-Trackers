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
package org.jackhuang.hmcl.java;

import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Hardcoded Java4Shell (CVE-2026-114938) safety policy.
///
/// The vulnerability lives in HotSpot's AVX-512 Base64 decoder stub
/// (`StubGenerator::generate_base64_decodeBlock`): a padded tail shorter than one complete
/// four-character group underflows the computed output length, the `bzhiq` mask becomes all
/// ones and the final `evmovdqub` store writes 64 bytes, up to 62 bytes past the destination
/// array. Heap corruption on a JIT-warmed decoder is enough for remote code execution.
///
/// OpenJDK shipped the fix (a length check before the vector tail path) in
/// 17.0.21.1 / 21.0.13.1 / 25.0.5.1 / 27.0.1.1. Java 8 and Java 11 never contained the
/// AVX-512 decoder and are not affected.
///
/// The policy is intentionally strict and architecture independent: only the major releases
/// listed in [#check(int, VersionNumber)] are accepted, every other release is
/// [Status#UNRECOGNIZED] and is blocked by callers. HMCL cannot verify whether the host CPU
/// actually exposes AVX-512 VBMI, so it does not exempt non-x86-64 machines; a fixed build is
/// required everywhere.
@NotNullByDefault
public final class JavaSecurityPolicy {
    /// The identifier of the vulnerability this policy covers.
    public static final String CVE_ID = "CVE-2026-114938";

    /// Result of evaluating one Java runtime against the hardcoded policy.
    @NotNullByDefault
    public enum Status {
        /// The runtime contains the fix, or the vulnerable code never existed in it.
        SAFE,

        /// The runtime is affected by Java4Shell and a fixed build exists for its release line.
        VULNERABLE,

        /// The release line is not covered by this policy: no fixed build exists (EOL or
        /// unmaintained line), or the version could not be parsed.
        UNRECOGNIZED
    }

    /// Evaluates a runtime by its parsed major version and full version number.
    ///
    /// @param info the runtime information, may be `null`
    /// @return the policy status, never `null`
    public static @NotNull Status check(@Nullable JavaInfo info) {
        if (info == null)
            return Status.UNRECOGNIZED;

        return check(info.getParsedVersion(), info.getVersionNumber());
    }

    /// Evaluates a runtime by its major version and full version number.
    ///
    /// @param majorVersion the parsed major version, e.g. `21`
    /// @param version      the full version, e.g. `21.0.13.0.2`, may be `null`
    /// @return the policy status, never `null`
    public static @NotNull Status check(int majorVersion, @Nullable VersionNumber version) {
        return switch (majorVersion) {
            // Java 8 and Java 11 do not contain the AVX-512 Base64 decoder.
            case 8, 11 -> Status.SAFE;

            // Fixed builds of the maintained LTS lines.
            case 17 -> atLeast(version, "17.0.21.1");
            case 21 -> atLeast(version, "21.0.13.1");
            case 25 -> atLeast(version, "25.0.5.1");
            case 27 -> atLeast(version, "27.0.1.1");

            // Everything else (9-10, 12-16, 18-20, 22-24, 26, 28+, ...) has no fixed build we
            // could point the user at, so it is blocked instead of being guessed.
            default -> Status.UNRECOGNIZED;
        };
    }

    /// Compares a version with the minimum fixed build of its release line.
    ///
    /// @param version the runtime version, may be `null`
    /// @param minimum the minimum fixed build, e.g. `21.0.13.1`
    /// @return [Status#SAFE] when `version` is at least `minimum`, [Status#UNRECOGNIZED] when
    ///         `version` is `null`, [Status#VULNERABLE] otherwise
    private static @NotNull Status atLeast(@Nullable VersionNumber version, String minimum) {
        if (version == null)
            return Status.UNRECOGNIZED;

        return version.compareTo(VersionNumber.asVersion(minimum)) >= 0
                ? Status.SAFE
                : Status.VULNERABLE;
    }

    /// Returns the minimum fixed build of one release line.
    ///
    /// @param majorVersion the parsed major version
    /// @return the minimum fixed version, or `null` when this policy has no fixed build for it
    public static @Nullable String minimumSafeVersion(int majorVersion) {
        return switch (majorVersion) {
            case 17 -> "17.0.21.1";
            case 21 -> "21.0.13.1";
            case 25 -> "25.0.5.1";
            case 27 -> "27.0.1.1";
            default -> null;
        };
    }

    /// Returns the human-readable list of runtimes accepted by this policy.
    ///
    /// The text is deliberately not localized: it only contains version numbers, and keeping
    /// it in one place avoids the list drifting away from [#check(int, VersionNumber)].
    ///
    /// @return the accepted runtimes, e.g. `Java 8 / Java 11 / 17.0.21.1+ / ...`
    public static @NotNull String safeVersionsText() {
        return "Java 8 / Java 11 / 17.0.21.1+ / 21.0.13.1+ / 25.0.5.1+ / 27.0.1.1+";
    }

    /// Describes one runtime for a blocking message.
    ///
    /// @param info the runtime information, may be `null`
    /// @return a short human-readable description, e.g. `Java 21.0.13.0.2 (Microsoft)`
    public static @NotNull String describe(@Nullable JavaInfo info) {
        if (info == null)
            return "unknown Java runtime";

        String vendor = info.getVendor();
        String version = info.getVersion();
        return vendor == null || vendor.isBlank()
                ? "Java " + version
                : "Java " + version + " (" + vendor + ")";
    }

    private JavaSecurityPolicy() {
    }
}
