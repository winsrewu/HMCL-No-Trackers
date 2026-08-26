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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.OAuth;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.event.EventManager;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * @author huangyuhui
 */
public final class OAuthServer {
    private OAuthServer() {
    }

    /// The Minecraft Launcher client id, which works without an officially injected client id.
    public static final String MICROSOFT_CLIENT_ID = "00000000402b5328";

    /// An OAuth session that waits for the authorization URL pasted by the user.
    ///
    /// The Minecraft Launcher client id only accepts `https://login.live.com/oauth20_desktop.srf`
    /// as the redirect URI, so the authorization code never comes back to a local server.
    /// Instead, the user copies the final URL from the browser address bar and pastes it into HMCL.
    public static final class ManualCodeSession implements OAuth.Session {
        private final CompletableFuture<String> future = new CompletableFuture<>();

        @Override
        public String getState() {
            return "";
        }

        @Override
        public String getCodeVerifier() {
            return "";
        }

        @Override
        public String getRedirectURI() {
            return OAuth.REDIRECT_URI;
        }

        @Override
        public String waitFor() throws InterruptedException, ExecutionException {
            return future.get();
        }

        @Override
        public void close() {
            if (!future.isDone()) {
                future.completeExceptionally(new AuthenticationException("OAuth authorization is cancelled"));
            }
        }

        void complete(String code) {
            future.complete(code);
        }
    }

    public static class Factory implements OAuth.Callback {
        public final EventManager<OpenBrowserEvent> onOpenBrowserAuthorizationCode = new EventManager<>();

        private volatile ManualCodeSession pendingSession;

        @Override
        public OAuth.Session startServer() throws IOException, AuthenticationException {
            if (StringUtils.isBlank(getClientId())) {
                throw new MicrosoftAuthenticationNotSupportedException();
            }

            ManualCodeSession session = new ManualCodeSession();
            pendingSession = session;
            return session;
        }

        @Override
        public void grantDeviceCode(String userCode, String verificationURI) {
            throw new UnsupportedOperationException("Device code flow is not supported by the Minecraft Launcher client id");
        }

        @Override
        public void loginCompletedDeviceCode() {
            throw new UnsupportedOperationException("Device code flow is not supported");
        }

        @Override
        public void openBrowser(OAuth.GrantFlow grantFlow, String url) throws IOException {
            switch (grantFlow) {
                case AUTHORIZATION_CODE -> onOpenBrowserAuthorizationCode.fireEvent(new OpenBrowserEvent(this, url));
                case DEVICE -> throw new UnsupportedOperationException("Device code flow is not supported");
            }
        }

        @Override
        public String getClientId() {
            return MICROSOFT_CLIENT_ID;
        }

        /// Submits the authorization URL pasted by the user and extracts the authorization code.
        ///
        /// @param url the full URL copied from the browser address bar, e.g. `https://login.live.com/oauth20_desktop.srf?code=...`
        /// @throws IllegalArgumentException if the URL does not contain an authorization code.
        public void submitManualCode(String url) throws IllegalArgumentException {
            ManualCodeSession session = pendingSession;
            if (session == null) {
                throw new IllegalStateException("No pending OAuth session");
            }

            String code = extractCode(url);
            if (code == null || code.isEmpty()) {
                throw new IllegalArgumentException("No authorization code found in URL: " + url);
            }

            session.complete(code);
        }

        private static String extractCode(String url) {
            try {
                String query = URI.create(url).getRawQuery();
                if (query == null) {
                    return null;
                }

                for (Pair<String, String> entry : NetworkUtils.parseQuery(query)) {
                    if ("code".equals(entry.getKey())) {
                        return entry.getValue();
                    }
                }
                return null;
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    public static class OpenBrowserEvent extends Event {
        private final String url;

        public OpenBrowserEvent(Object source, String url) {
            super(source);
            this.url = url;
        }

        public String getUrl() {
            return url;
        }
    }

    public static class MicrosoftAuthenticationNotSupportedException extends AuthenticationException {
    }
}
