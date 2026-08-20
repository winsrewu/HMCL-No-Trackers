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
package org.jackhuang.hmcl.ui.account;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.ClassTitle;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.i18n.LocaleUtils;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;

import java.util.Locale;

import static org.jackhuang.hmcl.setting.SettingsManager.userSettings;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.createSelectedItemPropertyFor;

public final class AccountListPage extends DecoratorAnimatedPage implements DecoratorPage {
    static final BooleanProperty RESTRICTED = new SimpleBooleanProperty(true);

    static {
        String property = System.getProperty("hmcl.offline.auth.restricted", "auto");

        if ("false".equals(property)
                || "auto".equals(property) && LocaleUtils.IS_CHINA_MAINLAND
                || SettingsManager.userSettings().enableOfflineAccountProperty().get())
            RESTRICTED.set(false);
        else
            userSettings().enableOfflineAccountProperty().addListener(new ChangeListener<Boolean>() {
                @Override
                public void changed(ObservableValue<? extends Boolean> o, Boolean oldValue, Boolean newValue) {
                    if (newValue) {
                        userSettings().enableOfflineAccountProperty().removeListener(this);
                        RESTRICTED.set(false);
                    }
                }
            });
    }

    private final ObservableList<AccountListItem> items;
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("account.manage")));
    private final ListProperty<Account> accounts = new SimpleListProperty<>(this, "accounts", FXCollections.observableArrayList());
    private final ObjectProperty<Account> selectedAccount;

    public AccountListPage() {
        items = MappedObservableList.create(accounts, AccountListItem::new);
        selectedAccount = createSelectedItemPropertyFor(items, Account.class);
    }

    public ObjectProperty<Account> selectedAccountProperty() {
        return selectedAccount;
    }

    public ListProperty<Account> accountsProperty() {
        return accounts;
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new AccountListPageSkin(this);
    }

    private static class AccountListPageSkin extends DecoratorAnimatedPageSkin<AccountListPage> {

        private ChangeListener<Boolean> holder;

        public AccountListPageSkin(AccountListPage skinnable) {
            super(skinnable);

            {
                VBox boxMethods = new VBox();
                {
                    boxMethods.getStyleClass().add("advanced-list-box-content");
                    FXUtils.setLimitWidth(boxMethods, 200);

                    AdvancedListItem microsoftItem = new AdvancedListItem();
                    microsoftItem.getStyleClass().add("navigation-drawer-item");
                    microsoftItem.setTitle(i18n("account.methods.microsoft"));
                    microsoftItem.setLeftIcon(SVG.MICROSOFT);
                    microsoftItem.setOnAction(e -> {
                        if (SettingsManager.isUserGameAccountsReadOnly()) {
                            confirmOverwriteUserAccounts(() -> Controllers.dialog(new MicrosoftAccountLoginPane()));
                        } else {
                            Controllers.dialog(new MicrosoftAccountLoginPane());
                        }
                    });

                    AdvancedListItem offlineItem = new AdvancedListItem();
                    offlineItem.getStyleClass().add("navigation-drawer-item");
                    offlineItem.setTitle(i18n("account.methods.offline"));
                    offlineItem.setLeftIcon(SVG.PERSON);
                    offlineItem.setOnAction(e -> {
                        if (SettingsManager.isUserGameAccountsReadOnly()) {
                            confirmOverwriteUserAccounts(() -> Controllers.dialog(new CreateAccountPane(Accounts.FACTORY_OFFLINE)));
                        } else {
                            Controllers.dialog(new CreateAccountPane(Accounts.FACTORY_OFFLINE));
                        }
                    });

                    ClassTitle title = new ClassTitle(i18n("account.create").toUpperCase(Locale.ROOT));
                    if (RESTRICTED.get()) {
                        VBox wrapper = new VBox(offlineItem);
                        wrapper.setPadding(Insets.EMPTY);
                        FXUtils.installFastTooltip(wrapper, i18n("account.login.restricted"));

                        offlineItem.setDisable(true);

                        boxMethods.getChildren().setAll(title, microsoftItem, wrapper);

                        holder = FXUtils.onWeakChange(RESTRICTED, value -> {
                            if (!value) {
                                holder = null;
                                offlineItem.setDisable(false);
                                boxMethods.getChildren().setAll(title, microsoftItem, offlineItem);
                            }
                        });
                    } else {
                        boxMethods.getChildren().setAll(title, microsoftItem, offlineItem);
                    }
                }

                ScrollPane scrollPane = new ScrollPane(boxMethods);
                VBox.setVgrow(scrollPane, Priority.ALWAYS);
                setLeft(scrollPane);
            }

            ScrollPane scrollPane = new ScrollPane();
            VBox list = new VBox();
            {
                scrollPane.setFitToWidth(true);

                list.maxWidthProperty().bind(scrollPane.widthProperty());
                list.setSpacing(10);
                list.getStyleClass().add("card-list");

                Bindings.bindContent(list.getChildren(), skinnable.items);

                scrollPane.setContent(list);
                FXUtils.smoothScrolling(scrollPane);

                setCenter(scrollPane);
            }
        }

        /// Confirms overwriting the user account files before continuing the account operation.
        private static void confirmOverwriteUserAccounts(Runnable action) {
            Controllers.confirmBackupAndOverwrite(i18n("account.storage.read_only"), () -> {
                SettingsManager.forceOverwriteUserGameAccounts();
                action.run();
            });
        }
    }
}
