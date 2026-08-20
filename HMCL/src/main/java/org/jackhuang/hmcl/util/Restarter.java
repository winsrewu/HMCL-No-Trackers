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
package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.util.io.JarUtils;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Restarts the current application in a new Java process.
///
/// @author Glavo
public final class Restarter {

    /// Restarts the current application.
    public static void restartSelf() throws IOException {
        LOG.info("Restarting HMCL");

        Path thisJar = JarUtils.thisJarPath();
        if (thisJar == null) {
            throw new IOException("Failed to find current HMCL location");
        }

        startJava(thisJar);
    }

    /// Launches the given jar in a new Java process, inheriting the current JVM arguments.
    private static void startJava(Path jar) throws IOException {
        List<String> commandline = new ArrayList<>();
        commandline.add(JavaRuntime.getDefault().getBinary().toString());

        try {
            for (String inputArgument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if (inputArgument.startsWith("-D") || inputArgument.startsWith("-X")) {
                    commandline.add(inputArgument);
                }
            }
        } catch (Throwable ignored) {
            // ManagementFactory not available
            for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
                if (entry.getKey() instanceof String key && key.startsWith("hmcl.")) {
                    commandline.add("-D" + key + "=" + entry.getValue());
                }
            }
        }

        commandline.add("-jar");
        commandline.add(jar.toAbsolutePath().toString());
        LOG.info("Starting process: " + maskCommandline(commandline));
        new ProcessBuilder(commandline)
                .directory(Paths.get("").toAbsolutePath().toFile())
                .inheritIO()
                .start();
    }

    private static String maskCommandline(List<String> commandline) {
        return String.join(" ", commandline.stream().map(str -> {
            if (str.startsWith("-D")) {
                int eqIdx = str.indexOf('=');
                if (eqIdx != -1) {
                    String key = str.substring(2, eqIdx);
                    String value = str.substring(eqIdx + 1);
                    if (key.contains("http.proxy") ||
                            key.startsWith("https.proxy") ||
                            key.startsWith("socksProxy") ||
                            key.equals("hmcl.microsoft.auth.id") ||
                            key.equals("hmcl.curseforge.apikey")
                    ) {
                        return "-D" + key + "=" + (value.isEmpty() ? "" : value.charAt(0) + "*".repeat(value.length() - 1));
                    }
                }
            }

            return str;
        }).toList());
    }

    private Restarter() {
    }
}
