/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.account;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXSpinner;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.OAuth;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.upgrade.IntegrityChecker;
import org.jackhuang.hmcl.util.StringUtils;

import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class MicrosoftAccountLoginPane extends JFXDialogLayout implements DialogAware {
    private final Account accountToRelogin;
    private final Consumer<AuthInfo> loginCallback;
    private final Runnable cancelCallback;

    @SuppressWarnings("FieldCanBeLocal")
    private final WeakListenerHolder holder = new WeakListenerHolder();

    private final ObjectProperty<Step> step = new SimpleObjectProperty<>();

    private TaskExecutor browserTaskExecutor;

    private final JFXButton btnLogin;
    private final SpinnerPane loginButtonSpinner;

    public MicrosoftAccountLoginPane() {
        this(false);
    }

    public MicrosoftAccountLoginPane(boolean bodyonly) {
        this(null, null, null, bodyonly);
    }

    public MicrosoftAccountLoginPane(Account account, Consumer<AuthInfo> callback, Runnable onCancel, boolean bodyonly) {
        this.accountToRelogin = account;
        this.loginCallback = callback;
        this.cancelCallback = onCancel;

        getStyleClass().add("microsoft-login-dialog");
        if (bodyonly) {
            this.pseudoClassStateChanged(PseudoClass.getPseudoClass("bodyonly"), true);
        } else {
            Label heading = new Label(accountToRelogin != null ? i18n("account.login.refresh") : i18n("account.create.microsoft"));
            heading.getStyleClass().add("header-label");
            setHeading(heading);
        }

        this.setMaxWidth(650);

        onEscPressed(this, this::onCancel);

        btnLogin = new JFXButton(i18n("account.login"));
        btnLogin.getStyleClass().add("dialog-accept");

        loginButtonSpinner = new SpinnerPane();
        loginButtonSpinner.getStyleClass().add("small-spinner-pane");
        loginButtonSpinner.setContent(btnLogin);

        JFXButton btnCancel = new JFXButton(i18n("button.cancel"));
        btnCancel.getStyleClass().add("dialog-cancel");
        btnCancel.setOnAction(e -> onCancel());

        setActions(loginButtonSpinner, btnCancel);

        holder.registerWeak(Accounts.OAUTH_CALLBACK.onOpenBrowserAuthorizationCode, event -> Platform.runLater(() -> {
            if (step.get() instanceof Step.StartAuthorizationCodeLogin)
                step.set(new Step.WaitForOpenBrowser(event.getUrl()));
        }));

        this.step.set(Accounts.OAUTH_CALLBACK.getClientId().isEmpty()
                ? new Step.Init()
                : new Step.StartAuthorizationCodeLogin());
        FXUtils.onChangeAndOperate(step, this::onStep);
    }

    private void onStep(Step currentStep) {
        VBox rootContainer = new VBox(10);
        setBody(rootContainer);
        rootContainer.setAlignment(Pos.TOP_CENTER);

        if (Accounts.OAUTH_CALLBACK.getClientId().isEmpty()) {
            var snapshotHint = new HintPane(MessageDialogPane.MessageType.WARNING);
            snapshotHint.setSegment(i18n("account.methods.microsoft.snapshot"));
            rootContainer.getChildren().add(snapshotHint);
            btnLogin.setDisable(true);
            loginButtonSpinner.setLoading(false);
            return;
        }

        if (!IntegrityChecker.isOfficial()) {
            var unofficialHintPane = new HintPane(MessageDialogPane.MessageType.WARNING);
            unofficialHintPane.setSegment(i18n("unofficial.hint"));
            rootContainer.getChildren().add(unofficialHintPane);
        }

        if (currentStep instanceof Step.Init) {
            btnLogin.setOnAction(e -> this.step.set(new Step.StartAuthorizationCodeLogin()));
            loginButtonSpinner.setLoading(false);

            var hintPane = new HintPane(MessageDialogPane.MessageType.INFO);
            hintPane.setText(i18n("account.methods.microsoft.hint"));
            rootContainer.getChildren().add(hintPane);
        } else if (currentStep instanceof Step.StartAuthorizationCodeLogin) {
            loginButtonSpinner.setLoading(false);
            cancelAllTasks();

            rootContainer.getChildren().add(new JFXSpinner());

            browserTaskExecutor = Task.supplyAsync(() -> Accounts.FACTORY_MICROSOFT.create(null, null, null, null, OAuth.GrantFlow.AUTHORIZATION_CODE))
                    .whenComplete(Schedulers.javafx(), this::onLoginCompleted)
                    .executor(true);
        } else if (currentStep instanceof Step.WaitForOpenBrowser wait) {
            btnLogin.setOnAction(e -> {
                FXUtils.openLink(wait.url());
                loginButtonSpinner.setLoading(false);
            });
            loginButtonSpinner.setLoading(false);

            HintPane hintPane = new HintPane(MessageDialogPane.MessageType.INFO);
            hintPane.setSegment(
                    i18n("account.methods.microsoft.methods.browser.hint", StringUtils.escapeXmlAttribute(wait.url()), wait.url()),
                    FXUtils::copyText
            );
            rootContainer.getChildren().add(hintPane);

            HintPane pasteHint = new HintPane(MessageDialogPane.MessageType.INFO);
            pasteHint.setSegment(i18n("account.methods.microsoft.methods.browser.paste_hint"));
            rootContainer.getChildren().add(pasteHint);

            TextField urlField = new TextField();
            urlField.setPromptText(i18n("account.methods.microsoft.methods.browser.paste_placeholder"));
            rootContainer.getChildren().add(urlField);

            JFXButton btnSubmit = new JFXButton(i18n("account.methods.microsoft.methods.browser.submit"));
            btnSubmit.getStyleClass().add("dialog-accept");
            btnSubmit.setOnAction(e -> {
                String url = urlField.getText();
                if (StringUtils.isBlank(url)) {
                    return;
                }

                try {
                    Accounts.OAUTH_CALLBACK.submitManualCode(url.trim());
                    loginButtonSpinner.setLoading(true);
                } catch (IllegalArgumentException ex) {
                    var errHint = new HintPane(MessageDialogPane.MessageType.ERROR);
                    errHint.setSegment(i18n("account.methods.microsoft.methods.browser.paste_invalid"));
                    rootContainer.getChildren().add(errHint);
                }
            });
            rootContainer.getChildren().add(btnSubmit);
        } else if (currentStep instanceof Step.LoginFailed failed) {
            btnLogin.setOnAction(e -> this.step.set(new Step.StartAuthorizationCodeLogin()));
            loginButtonSpinner.setLoading(false);
            cancelAllTasks();

            HintPane errHintPane = new HintPane(MessageDialogPane.MessageType.ERROR);
            errHintPane.setSegment(failed.message());
            rootContainer.getChildren().add(errHintPane);
        }

        var linkBox = new FlowPane(8, 8);
        linkBox.setAlignment(Pos.CENTER_LEFT);
        linkBox.setPrefWrapLength(500);

        JFXHyperlink profileLink = new JFXHyperlink(i18n("account.methods.microsoft.profile"));
        profileLink.setExternalLink("https://account.live.com/editprof.aspx");
        JFXHyperlink purchaseLink = new JFXHyperlink(i18n("account.methods.microsoft.purchase"));
        purchaseLink.setExternalLink(YggdrasilService.PURCHASE_URL);

        linkBox.getChildren().addAll(profileLink, purchaseLink);
        rootContainer.getChildren().add(linkBox);

        setBody(rootContainer);
    }

    private void cancelAllTasks() {
        if (browserTaskExecutor != null) browserTaskExecutor.cancel();
    }

    private void onCancel() {
        cancelAllTasks();
        if (cancelCallback != null) cancelCallback.run();
        fireEvent(new DialogCloseEvent());
    }

    private void onLoginCompleted(MicrosoftAccount account, Exception exception) {
        if (exception == null) {
            boolean storageReadOnly = accountToRelogin != null
                    ? Accounts.isAccountFilesReadOnly(accountToRelogin)
                    : Accounts.isAccountFilesReadOnly(account);
            if (storageReadOnly) {
                Controllers.confirmBackupAndOverwrite(i18n("account.storage.read_only"), () -> {
                    Accounts.forceOverwriteAccountFiles(accountToRelogin != null ? accountToRelogin : account);
                    completeLogin(account);
                });
                return;
            }

            completeLogin(account);
        } else if (!(exception instanceof CancellationException)) {
            this.step.set(new Step.LoginFailed(Accounts.localizeErrorMessage(exception)));
        }
    }

    /// Adds the logged-in account, selects it, and completes the login callback.
    private void completeLogin(MicrosoftAccount account) {
        if (accountToRelogin != null) Accounts.getAccounts().remove(accountToRelogin);

        int oldIndex = Accounts.getAccounts().indexOf(account);
        if (oldIndex == -1) {
            Accounts.getAccounts().add(account);
        } else {
            Accounts.getAccounts().remove(oldIndex);
            Accounts.getAccounts().add(oldIndex, account);
        }

        Accounts.setSelectedAccount(account);

        if (loginCallback != null) {
            try {
                loginCallback.accept(account.logIn());
            } catch (AuthenticationException e) {
                this.step.set(new Step.LoginFailed(Accounts.localizeErrorMessage(e)));
                return;
            }
        }
        fireEvent(new DialogCloseEvent());
    }

    private sealed interface Step {
        final class Init implements Step {
        }

        final class StartAuthorizationCodeLogin implements Step {
        }

        record WaitForOpenBrowser(String url) implements Step {

        }

        record LoginFailed(String message) implements Step {
        }
    }

}
