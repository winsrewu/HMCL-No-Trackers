/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.auth.offline;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import javafx.beans.binding.ObjectBinding;
import org.glavo.uuid.UUIDs;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountID;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.yggdrasil.Texture;
import org.jackhuang.hmcl.auth.yggdrasil.TextureType;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.ToStringBuilder;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 *
 * @author huang
 */
public class OfflineAccount extends Account {

    private final String profileName;
    private final UUID profileID;
    private Skin skin;

    protected OfflineAccount(
            AccountID accountID,
            String profileName,
            UUID profileID,
            Skin skin) {
        super(accountID);
        this.profileName = requireNonNull(profileName);
        this.profileID = requireNonNull(profileID);
        this.skin = skin;

        if (StringUtils.isBlank(profileName)) {
            throw new IllegalArgumentException("Profile name cannot be blank");
        }
    }

    @Override
    public UUID getProfileID() {
        return profileID;
    }

    @Override
    public String getProfileName() {
        return profileName;
    }

    public Skin getSkin() {
        return skin;
    }

    public void setSkin(Skin skin) {
        this.skin = skin;
        invalidate();
    }

    public AuthInfo logInWithoutSkin() throws AuthenticationException {
        // Using "legacy" user type here because "mojang" user type may cause "invalid session token" or "disconnected" when connecting to a game server.
        return new AuthInfo(profileName, profileID, UUIDs.toCompactString(UUID.randomUUID()), AuthInfo.USER_TYPE_MSA, "{}");
    }

    @Override
    public AuthInfo logIn() throws AuthenticationException {
        return logInWithoutSkin();
    }

    @Override
    public AuthInfo playOffline() throws AuthenticationException {
        return logIn();
    }

    @Override
    public void writeMetadata(JsonObject metadata) {
        super.writeMetadata(metadata);
        metadata.addProperty("profileID", profileID.toString());
        metadata.addProperty("profileName", profileName);
        if (skin == null) {
            metadata.add("skin", JsonNull.INSTANCE);
        } else {
            JsonObject skinStorage = new JsonObject();
            skin.writeStorage(skinStorage);
            metadata.add("skin", skinStorage);
        }
    }

    @Override
    public ObjectBinding<Optional<Map<TextureType, Texture>>> getTextures() {
        return super.getTextures();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("accountID", getAccountID())
                .append("profileName", profileName)
                .append("profileID", profileID)
                .toString();
    }
}
