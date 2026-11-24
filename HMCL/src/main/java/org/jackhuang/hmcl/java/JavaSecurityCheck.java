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

import javafx.scene.control.Alert;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.setting.JavaVersionType;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Enforces [JavaSecurityPolicy] in the launcher.
///
/// Two gates are implemented:
///
/// 1. [#checkLauncherJvm()] runs while HMCL starts up and verifies the JVM HMCL itself runs
///    on. An unsafe or unrecognized runtime is reported and HMCL refuses to start.
/// 2. [#checkBeforeLaunch(GameSettings)] runs right before a game is launched and verifies
///    every Java runtime HMCL knows about, plus the runtime configured for the instance. Any
///    unsafe or unrecognized runtime aborts the launch.
///
/// Both gates follow the same rule: only [JavaSecurityPolicy.Status#SAFE] passes, everything
/// else is blocked. The checks are deliberately fail-closed, including when a version cannot
/// be parsed at all.
@NotNullByDefault
public final class JavaSecurityCheck {

    /// Verifies the JVM HMCL itself is running on.
    ///
    /// The result is shown to the user with a blocking error dialog; the caller is responsible
    /// for terminating the launcher afterwards.
    ///
    /// @return `true` when HMCL may continue starting up, `false` when the launcher must exit
    public static boolean checkLauncherJvm() {
        JavaInfo info = JavaInfo.CURRENT_ENVIRONMENT;
        JavaSecurityPolicy.Status status = JavaSecurityPolicy.check(info);
        if (status == JavaSecurityPolicy.Status.SAFE)
            return true;

        String description = JavaSecurityPolicy.describe(info);
        LOG.error("Refusing to run on " + description + ": " + describeStatus(status, info));
        showFatalAlert(i18n("java.security.launcher_blocked", description, JavaSecurityPolicy.safeVersionsText()));
        return false;
    }

    /// Verifies every Java runtime before a game is launched.
    ///
    /// The runtimes known to [JavaManager] are checked first; when the instance pins a custom
    /// executable, that executable is checked as well. The launch is aborted when at least one
    /// runtime is not [JavaSecurityPolicy.Status#SAFE].
    ///
    /// @param settings the effective settings of the instance that is about to be launched
    /// @return `true` when the launch may continue, `false` when it has been blocked
    public static boolean checkBeforeLaunch(@Nullable GameSettings.Effective settings) {
        List<JavaRuntime> runtimes;
        try {
            runtimes = collectKnownRuntimes(settings);
        } catch (InterruptedException e) {
            // HMCL is shutting down; do not block a process that is already going away.
            Thread.currentThread().interrupt();
            return true;
        }

        List<BlockedRuntime> blocked = new ArrayList<>();
        for (JavaRuntime runtime : runtimes) {
            JavaInfo info = runtime.getInfo();
            JavaSecurityPolicy.Status status = JavaSecurityPolicy.check(info);
            if (status != JavaSecurityPolicy.Status.SAFE)
                blocked.add(new BlockedRuntime(JavaSecurityPolicy.describe(info), info, status));
        }

        if (blocked.isEmpty())
            return true;

        List<String> lines = new ArrayList<>(blocked.size());
        for (BlockedRuntime runtime : blocked)
            lines.add("· " + runtime.description() + " — " + describeStatus(runtime.status(), runtime.info()));

        LOG.error("Blocked the launch, unsafe Java runtime(s): " + String.join("; ", lines));
        Controllers.dialog(
                i18n("java.security.launch_blocked", String.join("\n", lines), JavaSecurityPolicy.safeVersionsText()),
                i18n("java.security.title"),
                MessageType.ERROR
        );
        return false;
    }

    /// Collects the runtimes that have to be verified before a launch.
    ///
    /// @param settings the effective settings of the instance, may be `null`
    /// @return the runtimes to verify, without duplicates
    /// @throws InterruptedException when HMCL is shutting down while the Java list is loaded
    private static @NotNull List<JavaRuntime> collectKnownRuntimes(@Nullable GameSettings.Effective settings) throws InterruptedException {
        Map<Path, JavaRuntime> runtimes = new LinkedHashMap<>();

        Collection<JavaRuntime> detected = JavaManager.getAllJava();
        for (JavaRuntime runtime : detected)
            runtimes.put(runtime.getBinary(), runtime);

        if (settings != null
                && settings.getInheritable(GameSettings::javaTypeProperty) == JavaVersionType.CUSTOM) {
            String customPath = settings.getInheritable(GameSettings::customJavaPathProperty);
            if (StringUtils.isNotBlank(customPath)) {
                try {
                    JavaRuntime custom = JavaManager.getJava(Path.of(customPath));
                    runtimes.put(custom.getBinary(), custom);
                } catch (Exception e) {
                    // An unreadable custom runtime is reported by the regular launch checks; it
                    // must not weaken this gate, so it is simply not added here.
                    LOG.warning("Failed to inspect the custom Java runtime " + customPath, e);
                }
            }
        }

        return new ArrayList<>(runtimes.values());
    }

    /// One runtime that failed the policy and is reported to the user.
    ///
    /// @param description the human-readable runtime description
    /// @param info        the runtime information
    /// @param status      the blocking status, never [JavaSecurityPolicy.Status#SAFE]
    private record BlockedRuntime(String description, JavaInfo info, JavaSecurityPolicy.Status status) {
    }

    /// Builds the localized reason for a blocking status.
    ///
    /// @param status the policy status, never [JavaSecurityPolicy.Status#SAFE]
    /// @param info   the runtime information, may be `null`
    /// @return the localized reason
    private static @NotNull String describeStatus(JavaSecurityPolicy.Status status, @Nullable JavaInfo info) {
        if (status == JavaSecurityPolicy.Status.VULNERABLE) {
            String minimum = info != null ? JavaSecurityPolicy.minimumSafeVersion(info.getParsedVersion()) : null;
            return minimum != null
                    ? i18n("java.security.reason.vulnerable", minimum)
                    : i18n("java.security.reason.vulnerable_unknown_fix");
        }

        return i18n("java.security.reason.unrecognized");
    }

    /// Shows a blocking error dialog for a launcher that must exit.
    ///
    /// @param text the localized message body
    private static void showFatalAlert(String text) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(i18n("java.security.title"));
        alert.setHeaderText(i18n("java.security.title"));
        alert.setContentText(text);
        alert.getDialogPane().setPrefWidth(720);
        alert.showAndWait();
    }

    private JavaSecurityCheck() {
    }
}
