/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl;

import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Stores metadata about this application.
 */
public final class Metadata {
    private Metadata() {
    }

    public static final String NAME = "HMCL-No-Trackers";
    public static final String FULL_NAME = "Hello Minecraft! Launcher (No-Trackers)";
    public static final String VERSION = System.getProperty("hmcl.version.override", JarUtils.getAttribute("hmcl.version", "@develop@"));

    /// Explicit Application User Model ID used for Windows taskbar grouping and pinning.
    public static final String WINDOWS_APP_USER_MODEL_ID = "org.jackhuang.hmcl";

    public static final String TITLE = NAME + " " + VERSION;
    public static final String FULL_TITLE = FULL_NAME + " v" + VERSION;

    public static final int MINIMUM_REQUIRED_JAVA_VERSION = 17;
    public static final int MINIMUM_SUPPORTED_JAVA_VERSION = 17;
    public static final int RECOMMENDED_JAVA_VERSION = 21;

    public static final String BUILD_CHANNEL = JarUtils.getAttribute("hmcl.version.type", "nightly");
    public static final String GITHUB_SHA = JarUtils.getAttribute("hmcl.version.hash", null);

    public static final Path CURRENT_DIRECTORY = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    public static final Path MINECRAFT_DIRECTORY = OperatingSystem.getWorkingDirectory("minecraft");
    public static final Path HMCL_USER_HOME;
    public static final Path HMCL_LOCAL_HOME;
    public static final Path DEPENDENCIES_DIRECTORY;

    static {
        String hmclHome = System.getProperty("hmcl.home", System.getenv("HMCL_USER_HOME"));
        if (StringUtils.isBlank(hmclHome)) {
            if (OperatingSystem.CURRENT_OS.isLinuxOrBSD()) {
                String xdgData = System.getenv("XDG_DATA_HOME");
                if (StringUtils.isNotBlank(xdgData)) {
                    HMCL_USER_HOME = Path.of(xdgData, "hmcl").toAbsolutePath().normalize();
                } else {
                    HMCL_USER_HOME = Path.of(System.getProperty("user.home"), ".local", "share", "hmcl").toAbsolutePath().normalize();
                }
            } else {
                HMCL_USER_HOME = OperatingSystem.getWorkingDirectory("hmcl");
            }
        } else {
            HMCL_USER_HOME = Path.of(hmclHome).toAbsolutePath().normalize();
        }

        String hmclCurrentDir = System.getProperty("hmcl.dir", System.getenv("HMCL_LOCAL_HOME"));
        HMCL_LOCAL_HOME = StringUtils.isNotBlank(hmclCurrentDir)
                ? Path.of(hmclCurrentDir).toAbsolutePath().normalize()
                : CURRENT_DIRECTORY.resolve(".hmcl");

        String hmclDependencies = System.getProperty("hmcl.dependencies.dir", System.getenv("HMCL_DEPENDENCIES_DIR"));
        DEPENDENCIES_DIRECTORY = StringUtils.isNotBlank(hmclDependencies)
                ? Path.of(hmclDependencies).toAbsolutePath().normalize()
                : HMCL_LOCAL_HOME.resolve("dependencies");
    }

    public static boolean isStable() {
        return "stable".equals(BUILD_CHANNEL);
    }

    public static boolean isDev() {
        return "dev".equals(BUILD_CHANNEL);
    }

    public static boolean isNightly() {
        return !isStable() && !isDev();
    }

    public static @Nullable String getSuggestedJavaDownloadLink() {
        return null;
    }
}
