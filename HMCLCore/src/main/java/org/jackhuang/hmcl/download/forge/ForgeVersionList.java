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
package org.jackhuang.hmcl.download.forge;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author huangyuhui
 */
public final class ForgeVersionList extends VersionList<ForgeRemoteVersion> {
    private final DownloadProvider downloadProvider;

    public ForgeVersionList(DownloadProvider downloadProvider) {
        this.downloadProvider = downloadProvider;
    }

    @Override
    public boolean hasType() {
        return false;
    }

    /// Fetches the Forge version list from the official Maven metadata.
    /// Only Forge 1.13 and newer is supported, as older versions are not published on Maven.
    @Override
    public Task<?> refreshAsync() {
        return new GetTask(downloadProvider.injectURLWithCandidates(MAVEN_METADATA_URL))
                .thenApplyAsync(xml -> parseMavenMetadata(xml))
                .thenAcceptAsync(versions -> {
                    lock.writeLock().lock();

                    try {
                        this.versions.clear();
                        for (ForgeRemoteVersion version : versions) {
                            this.versions.put(version.getGameVersion(), version);
                        }
                    } finally {
                        lock.writeLock().unlock();
                    }
                });
    }

    /// Parses Forge's Maven metadata and maps each version to its installer URL.
    private static List<ForgeRemoteVersion> parseMavenMetadata(String xml) {
        Document document = Jsoup.parse(xml, "", Parser.xmlParser());
        List<ForgeRemoteVersion> result = new ArrayList<>();
        for (var element : document.select("version")) {
            String fullVersion = element.text();
            int separator = fullVersion.indexOf('-');
            if (separator <= 0) {
                continue;
            }

            String gameVersion = fullVersion.substring(0, separator);
            String selfVersion = fullVersion.substring(separator + 1);
            String installerUrl = MAVEN_BASE + fullVersion + "/forge-" + fullVersion + "-installer.jar";
            result.add(new ForgeRemoteVersion(gameVersion, selfVersion, null, Collections.singletonList(installerUrl)));
        }
        return result;
    }

    private static final String MAVEN_METADATA_URL = "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml";
    private static final String MAVEN_BASE = "https://maven.minecraftforge.net/net/minecraftforge/forge/";
}
