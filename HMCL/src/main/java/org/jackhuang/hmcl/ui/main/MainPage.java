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
package org.jackhuang.hmcl.ui.main;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXPopup;
import javafx.animation.RotateTransition;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.GameDirectory;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.instances.GameListPopupMenu;
import org.jackhuang.hmcl.ui.instances.Instances;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.download.RemoteVersion.Type.RELEASE;
import static org.jackhuang.hmcl.setting.SettingsManager.state;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class MainPage extends StackPane implements DecoratorPage {
    private static final String ANNOUNCEMENT = "announcement";

    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();

    private final ObjectProperty<@Nullable GameInstanceID> currentGame = new SimpleObjectProperty<>(this, "currentGame");
    private final ObservableList<GameInstanceManifest> versions = FXCollections.observableArrayList();
    private HMCLGameRepository repository;

    private TransitionPane announcementPane;
    private final JFXButton menuButton;

    {
        HBox titleNode = new HBox(8);
        titleNode.setPadding(new Insets(0, 0, 0, 2));
        titleNode.setAlignment(Pos.CENTER_LEFT);

        ImageView titleIcon = new ImageView(FXUtils.newBuiltinImage("/assets/img/icon-title.png"));
        Label titleLabel = new Label(Metadata.FULL_TITLE);
        if (I18n.isUpsideDown()) {
            titleIcon.setRotate(180);
            titleLabel.setRotate(180);
        }
        titleLabel.getStyleClass().add("jfx-decorator-title");
        titleLabel.textFillProperty().bind(Themes.titleFillProperty());
        titleNode.getChildren().setAll(titleIcon, titleLabel);

        state.setValue(new State(null, titleNode, false, false, true));

        setPadding(new Insets(20));

        if (Metadata.isNightly() || (Metadata.isDev() && !Objects.equals(Metadata.VERSION, state().getShownTips().get(ANNOUNCEMENT)))) {
            String title;
            String content;
            if (Metadata.isNightly()) {
                title = i18n("update.channel.nightly.title");
                content = i18n("update.channel.nightly.hint");
            } else {
                title = i18n("update.channel.dev.title");
                content = i18n("update.channel.dev.hint");
            }

            VBox announcementCard = new VBox();

            BorderPane titleBar = new BorderPane();
            titleBar.getStyleClass().add("title");
            titleBar.setLeft(new Label(title));

            JFXButton btnHide = new JFXButton();
            btnHide.setOnAction(e -> {
                announcementPane.setContent(new StackPane(), ContainerAnimations.FADE);
                if (Metadata.isDev()) {
                    state().getShownTips().put(ANNOUNCEMENT, Metadata.VERSION);
                }
            });
            btnHide.getStyleClass().add("announcement-close-button");
            btnHide.setGraphic(SVG.CLOSE.createIcon(20));
            titleBar.setRight(btnHide);

            TextFlow body = FXUtils.segmentToTextFlow(content, Controllers::onHyperlinkAction);
            body.setLineSpacing(4);

            announcementCard.getChildren().setAll(titleBar, body);
            announcementCard.setSpacing(16);
            announcementCard.getStyleClass().addAll("card", "announcement");

            VBox announcementBox = new VBox(16);
            announcementBox.setPadding(new Insets(15));
            announcementBox.getChildren().add(announcementCard);

            announcementPane = new TransitionPane();
            announcementPane.setContent(announcementBox, ContainerAnimations.NONE);

            StackPane.setMargin(announcementPane, new Insets(-15));
            getChildren().add(announcementPane);
        }

        HBox launchPane = new HBox();
        launchPane.getStyleClass().add("launch-pane");
        FXUtils.onScroll(launchPane, versions, list -> {
            GameInstanceID currentId = getCurrentGame();
            return Lang.indexWhere(list, instance -> instance.id().equals(currentId));
        }, it -> repository.setSelectedInstance(it.id()));

        StackPane.setAlignment(launchPane, Pos.BOTTOM_RIGHT);
        {
            JFXButton launchButton = new JFXButton();
            launchButton.getStyleClass().add("launch-button");
            launchButton.setDefaultButton(true);
            {
                VBox graphic = new VBox();
                graphic.setAlignment(Pos.CENTER);
                Label launchLabel = new Label();
                launchLabel.setStyle("-fx-font-size: 16px;");
                Label currentLabel = new Label();
                currentLabel.setStyle("-fx-font-size: 12px;");

                FXUtils.onChangeAndOperate(currentGameProperty(), new Consumer<>() {
                    private Tooltip tooltip;

                    @Override
                    public void accept(@Nullable GameInstanceID currentGame) {
                        if (currentGame == null) {
                            launchLabel.setText(i18n("instance.launch.empty"));
                            currentLabel.setText(null);
                            graphic.getChildren().setAll(launchLabel);
                            FXUtils.setOnActionWithCooldown(launchButton, MainPage.this::launchNoGame);
                            if (tooltip == null)
                                tooltip = new Tooltip(i18n("instance.launch.empty.tooltip"));
                            FXUtils.installFastTooltip(launchButton, tooltip);
                        } else {
                            launchLabel.setText(i18n("instance.launch"));
                            currentLabel.setText(currentGame.toString());
                            graphic.getChildren().setAll(launchLabel, currentLabel);
                            FXUtils.setOnActionWithCooldown(launchButton, MainPage.this::launch);
                            if (tooltip != null)
                                Tooltip.uninstall(launchButton, tooltip);
                        }
                    }
                });

                launchButton.setGraphic(graphic);
            }

            menuButton = new JFXButton();
            menuButton.getStyleClass().add("menu-button");
            menuButton.setOnAction(e -> {
                if (GameListPopupMenu.hideShowing(menuButton)) {
                    return;
                }

                JFXPopup popup = GameListPopupMenu.showAndGetPopup(
                        menuButton,
                        JFXPopup.PopupVPosition.BOTTOM,
                        JFXPopup.PopupHPosition.RIGHT,
                        0,
                        -menuButton.getHeight(),
                        repository, versions
                );

                Node graphic = menuButton.getGraphic();
                if (graphic != null) {
                    if (AnimationUtils.isAnimationEnabled()) {
                        Duration duration = Duration.millis(200);
                        RotateTransition rotateOpen = new RotateTransition(duration, graphic);
                        rotateOpen.setToAngle(-180);
                        FXUtils.playAnimation(graphic, "arrow-rotation", rotateOpen);

                        popup.setOnHidden(windowEvent -> {
                            RotateTransition rotateClose = new RotateTransition(duration, graphic);
                            rotateClose.setToAngle(0);
                            FXUtils.playAnimation(graphic, "arrow-rotation", rotateClose);
                        });
                    } else {
                        graphic.setRotate(-180);
                        popup.setOnHidden(windowEvent -> graphic.setRotate(0));
                    }
                }
            });
            FXUtils.installFastTooltip(menuButton, i18n("instance.switch"));
            menuButton.setGraphic(SVG.ARROW_DROP_UP.createIcon(30));

            EventHandler<MouseEvent> secondaryClickHandle = event -> {
                if (event.getButton() == MouseButton.SECONDARY && event.getClickCount() == 1) {
                    menuButton.fire();
                    event.consume();
                }
            };
            launchButton.addEventHandler(MouseEvent.MOUSE_CLICKED, secondaryClickHandle);
            menuButton.addEventHandler(MouseEvent.MOUSE_CLICKED, secondaryClickHandle);

            launchPane.getChildren().setAll(launchButton, menuButton);
        }

        getChildren().addAll(launchPane);

    }

    private void launch() {
        HMCLGameRepository repository = GameDirectoryManager.getSelectedRepository();
        Instances.launch(repository, repository.getSelectedInstance());
    }

    private void launchNoGame() {
        DownloadProvider downloadProvider = DownloadProviders.getDownloadProvider();
        VersionList<?> versionList = downloadProvider.getVersionListById("game");

        Holder<GameInstanceID> instanceHolder = new Holder<>();
        Task<?> task = versionList.refreshAsync("")
                .thenSupplyAsync(() -> versionList.getVersions("").stream()
                        .filter(it -> it.getVersionType() == RELEASE)
                        .filter(it -> NativePatcher.checkSupportedStatus(GameVersionNumber.asGameVersion(it.getGameVersion()), Platform.SYSTEM_PLATFORM, OperatingSystem.SYSTEM_VERSION) != NativePatcher.SupportStatus.UNSUPPORTED)
                        .sorted()
                        .findFirst()
                        .orElseThrow(() -> new IOException("No versions found")))
                .thenComposeAsync(version -> {
                    HMCLGameRepository repository = GameDirectoryManager.getSelectedRepository();
                    DefaultDependencyManager dependency = repository.getDependency();

                    String gameVersion = version.getGameVersion();
                    GameInstanceID instanceId = new GameInstanceID(gameVersion);

                    instanceHolder.value = instanceId;

                    return dependency.newGameBuilder()
                            .name(instanceId)
                            .gameVersion(gameVersion)
                            .buildAsync();
                })
                .whenComplete(any -> GameDirectoryManager.getSelectedRepository().refresh())
                .whenComplete(Schedulers.javafx(), (result, exception) -> {
                    if (exception == null) {
                        GameDirectoryManager.getSelectedRepository().setSelectedInstance(instanceHolder.value);
                        launch();
                    } else if (!(exception instanceof CancellationException)) {
                        LOG.warning("Failed to install game", exception);
                        Controllers.dialog(StringUtils.getStackTrace(exception),
                                i18n("install.failed"),
                                MessageDialogPane.MessageType.WARNING);
                    }
                });
        Controllers.taskDialog(task, i18n("instance.launch.empty.installing"), TaskCancellationAction.NORMAL);
    }

    @Override
    public ReadOnlyObjectWrapper<State> stateProperty() {
        return state;
    }

    public GameDirectory getGameDirectory() {
        return repository.getGameDirectory();
    }

    public HMCLGameRepository getRepository() {
        return repository;
    }

    public GameInstanceID getCurrentGame() {
        return currentGame.get();
    }

    public ObjectProperty<@Nullable GameInstanceID> currentGameProperty() {
        return currentGame;
    }

    public void setCurrentGame(@Nullable GameInstanceID currentGame) {
        this.currentGame.set(currentGame);
    }

    public ObservableList<GameInstanceManifest> getVersions() {
        return versions;
    }

    public void initVersions(HMCLGameRepository repository, List<GameInstanceManifest> versions) {
        FXUtils.checkFxUserThread();
        this.repository = repository;
        this.versions.setAll(versions);
    }
}
