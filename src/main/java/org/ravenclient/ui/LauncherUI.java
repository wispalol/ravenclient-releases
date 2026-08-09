package org.ravenclient.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.util.Duration;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import org.ravenclient.auth.Account;
import org.ravenclient.auth.AuthException;
import org.ravenclient.auth.DeviceCodeSession;
import org.ravenclient.auth.MicrosoftAuthenticator;
import org.ravenclient.config.AccountStore;
import org.ravenclient.config.LauncherConfig;
import org.ravenclient.config.ProfileStore;
import org.ravenclient.config.ProfileStore.Profile;
import org.ravenclient.game.GameLauncher;
import org.ravenclient.game.Loader;
import org.ravenclient.game.LoaderMeta;
import org.ravenclient.updater.AppUpdater;
import org.ravenclient.updater.ClientVersion;
import org.ravenclient.updater.UpdateManifest;

import java.awt.Desktop;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.scene.layout.Priority;

public class LauncherUI extends Application {

    /**
     * The only Minecraft versions this launcher exposes. Modrinth-compatible
     * releases plus "Latest release" as a convenience.
     */
    static final List<String> SUPPORTED_VERSIONS = List.of("1.21.11", "26.1.1", "26.2");

    private final ExecutorService pool = Executors.newCachedThreadPool();

    private LauncherConfig config;
    private Stage stage;

    private Label accountLabel = new Label("Not signed in");
    private ComboBox<String> versionBox;
    private final List<ToggleButton> loaderPills = new ArrayList<>();
    private ToggleGroup loaderGroup;
    private Slider memorySlider;
    private Label memoryLabel;
    private Button launchButton;
    private ProgressBar progressBar;
    private Label statusLabel;
    private TextArea console;

    private VBox homeRoot;
    private final StackPane pageContainer = new StackPane();
    private Account account;
    private Process gameProcess;
    private Dialog<Void> deviceDialog;
    private boolean busy;
    private GameLauncher.LaunchData currentLaunchData;

    // --- nav buttons -------------------------------------------------------
    private final ToggleGroup navGroup = new ToggleGroup();
    private final ToggleButton homeBtn = navButton("Home", true);
    private final ToggleButton modsBtn = navButton("Mods", false);
    private final ToggleButton versionsBtn = navButton("Versions", false);
    private final ToggleButton profilesBtn = navButton("Profiles", false);
    private final ToggleButton cosmeticsBtn = navButton("Cosmetics", false);
    private final ToggleButton settingsBtn = navButton("Settings", false);

    private static ToggleButton navButton(String text, boolean selected) {
        ToggleButton b = new ToggleButton(text);
        b.getStyleClass().add("nav-button");
        b.setPrefHeight(42);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setGraphic(navIcon(navGlyph(text)));
        b.setToggleGroup(null); // Will be set manually
        if (selected) b.setSelected(true);
        return b;
    }

    private static String navGlyph(String text) {
        switch (text) {
            case "Home": return "\u2302";          // ⌂
            case "Mods": return "\u25C8";          // ◈
            case "Versions": return "\u2B67";       // ⭧
            case "Profiles": return "\u2691";       // ⚑
            case "Cosmetics": return "\u2726";      // ✦
            case "Settings": return "\u2699";       // ⚙
            default: return "\u2022";
        }
    }

    private static Label navIcon(String glyph) {
        Label l = new Label(glyph);
        l.getStyleClass().add("nav-icon");
        l.setMinWidth(26);
        return l;
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        String appdata = System.getenv("APPDATA");
        Path dir = Path.of(appdata != null ? appdata : System.getProperty("user.home"), ".ravenclient");
        this.config = LauncherConfig.load(dir);

        BorderPane root = new BorderPane();
        root.setLeft(buildSidebar());
        root.setCenter(pageContainer);
        root.setBottom(buildFooter());

        StackPane sceneRoot = new StackPane(root);
        Node bootOverlay = buildBootOverlay();
        sceneRoot.getChildren().add(bootOverlay);

        Scene scene = new Scene(sceneRoot, 960, 640);
        scene.getStylesheets().add(getClass().getResource("/raven.css").toExternalForm());

        stage.setTitle("RavenClient");
        stage.setMinWidth(780);
        stage.setMinHeight(580);
        stage.setScene(scene);
        stage.setResizable(true);
        stage.show();

        selectPage(homeBtn);
        loadAccount();
        loadVersions();
        animateBoot(bootOverlay, sceneRoot);
        log("RavenClient ready. Sign in to launch Minecraft. 👋 Welcome to the flock. Auto-update verified. Self-update working!");
        checkForUpdatesBackground();
    }

    // --- updates -----------------------------------------------------------

    private void checkForUpdatesBackground() {
        if (!AppUpdater.isPackaged()) return; // dev run: no packaged app to swap
        pool.execute(() -> {
            try {
                UpdateManifest m = AppUpdater.check();
                if (m != null) Platform.runLater(() -> promptUpdate(m));
            } catch (Exception e) {
                log("Update check failed: " + e.getMessage());
            }
        });
    }

    private void checkForUpdatesManual() {
        setStatus("Checking for updates...");
        pool.execute(() -> {
            try {
                UpdateManifest m = AppUpdater.check();
                Platform.runLater(() -> {
                    if (m == null) setStatus("You are up to date.");
                    else promptUpdate(m);
                });
            } catch (Exception e) {
                Platform.runLater(() -> setStatus("Update check failed: " + e.getMessage()));
            }
        });
    }

    private void promptUpdate(UpdateManifest m) {
        if (gameProcess != null && gameProcess.isAlive()) {
            setStatus("Close Minecraft before installing an update.");
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Update available");
        alert.setHeaderText("RavenClient " + m.version() + " is available");
        String notes = m.notes() == null || m.notes().isBlank() ? "" : "\n\n" + m.notes();
        alert.setContentText("Update to version " + m.version()
                + "? Your settings, accounts and profiles will be kept." + notes);
        alert.initOwner(stage);
        alert.getDialogPane().getStylesheets().add(getClass().getResource("/raven.css").toExternalForm());
        ButtonType install = new ButtonType("Update now");
        ButtonType later = new ButtonType("Later", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(install, later);
        alert.showAndWait().ifPresent(bt -> {
            if (bt == install) installUpdate(m);
        });
    }

    private void installUpdate(UpdateManifest m) {
        setBusy(true);
        setUpStatus("Downloading update " + m.version() + "...");
        pool.execute(() -> {
            try {
                AppUpdater.apply(m, new AppUpdater.UpdateListener() {
                    @Override public void status(String text) { Platform.runLater(() -> setStatus(text)); }
                    @Override public void progress(double fraction) { Platform.runLater(() -> progressBar.setProgress(fraction)); }
                });
                Platform.runLater(() -> {
                    log("Update installed. Restarting RavenClient...");
                    Platform.exit();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setStatus("Update failed: " + e.getMessage());
                    log(stackTrace(e));
                    setBusy(false);
                });
            }
        });
    }

    // --- boot splash -------------------------------------------------------

    private static final List<String> BOOT_LINES = List.of(
            "Unfurling dark wings",
            "Calibrating the flight deck",
            "Syncing profile mods",
            "Waking the grid",
            "Polishing the hunt");

    private Node buildBootOverlay() {
        VBox box = new VBox(16);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("boot-overlay");

        // Animated background gradient
        StackPane background = new StackPane();
        background.getStyleClass().add("boot-background");
        
        StackPane markBox = new StackPane();
        Label mark = new Label("R");
        mark.getStyleClass().add("boot-mark");
        markBox.getChildren().add(mark);

        Label title = new Label("RAVENCLIENT");
        title.getStyleClass().add("boot-title");
        Label tagline = new Label("rise above");
        tagline.getStyleClass().add("boot-tagline");

        ProgressBar bar = new ProgressBar(0);
        bar.getStyleClass().add("boot-progress");
        bar.setPrefWidth(340);

        Label line = new Label(BOOT_LINES.get(0));
        line.getStyleClass().add("boot-line");

        // Add floating particles effect
        VBox particlesContainer = new VBox();
        particlesContainer.getStyleClass().add("boot-particles");
        
        box.getChildren().addAll(background, markBox, title, tagline, bar, line, particlesContainer);
        return box;
    }

    private void animateBoot(Node overlay, StackPane sceneRoot) {
        VBox box = (VBox) overlay;
        Label mark = (Label) ((StackPane) box.getChildren().get(1)).getChildren().get(0);
        Label title = (Label) box.getChildren().get(2);
        ProgressBar bar = (ProgressBar) box.getChildren().get(4);
        Label line = (Label) box.getChildren().get(5);

        // Enhanced progress animation with multiple phases
        Timeline progress = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(bar.progressProperty(), 0)),
                new KeyFrame(Duration.millis(800), new KeyValue(bar.progressProperty(), 0.3, Interpolator.EASE_IN)),
                new KeyFrame(Duration.millis(1600), new KeyValue(bar.progressProperty(), 0.7, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(2600), new KeyValue(bar.progressProperty(), 1, Interpolator.EASE_OUT)));
        progress.setOnFinished(e -> fadeOutBoot(overlay, sceneRoot));
        progress.play();

        // Enhanced status line cycling with fade effect
        Timeline messages = new Timeline();
        double t = 0;
        for (int i = 1; i < BOOT_LINES.size(); i++) {
            final int idx = i;
            messages.getKeyFrames().add(new KeyFrame(
                    Duration.millis(t += 520), e -> {
                        FadeTransition fade = new FadeTransition(Duration.millis(300), line);
                        fade.setFromValue(1.0);
                        fade.setToValue(0.3);
                        fade.setOnFinished(f -> {
                            line.setText(BOOT_LINES.get(idx));
                            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), line);
                            fadeIn.setFromValue(0.3);
                            fadeIn.setToValue(1.0);
                            fadeIn.play();
                        });
                        fade.play();
                    }));
        }
        messages.play();

        // Enhanced logo pulse with rotation
        Timeline pulse = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(mark.scaleXProperty(), 1),
                        new KeyValue(mark.scaleYProperty(), 1),
                        new KeyValue(mark.rotateProperty(), 0)),
                new KeyFrame(Duration.millis(650),
                        new KeyValue(mark.scaleXProperty(), 1.08, Interpolator.EASE_BOTH),
                        new KeyValue(mark.scaleYProperty(), 1.08, Interpolator.EASE_BOTH),
                        new KeyValue(mark.rotateProperty(), 2, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(1300),
                        new KeyValue(mark.scaleXProperty(), 1, Interpolator.EASE_BOTH),
                        new KeyValue(mark.scaleYProperty(), 1, Interpolator.EASE_BOTH),
                        new KeyValue(mark.rotateProperty(), 0, Interpolator.EASE_BOTH)));
        pulse.setCycleCount(Timeline.INDEFINITE);
        pulse.play();

        // Enhanced title shimmer with color shift
        Timeline glow = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(title.opacityProperty(), 0.65)),
                new KeyFrame(Duration.millis(800), new KeyValue(title.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(1600), new KeyValue(title.opacityProperty(), 0.65)));
        glow.setCycleCount(Timeline.INDEFINITE);
        glow.play();
        
        // Add subtle floating animation to background
        Timeline backgroundFloat = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(box.translateYProperty(), 0)),
                new KeyFrame(Duration.millis(4000), new KeyValue(box.translateYProperty(), -5, Interpolator.SPLINE(0.5, 0.5, 0.5, 0.5))),
                new KeyFrame(Duration.millis(8000), new KeyValue(box.translateYProperty(), 0, Interpolator.SPLINE(0.5, 0.5, 0.5, 0.5))));
        backgroundFloat.setCycleCount(Timeline.INDEFINITE);
        backgroundFloat.play();
    }

    private void fadeOutBoot(Node overlay, StackPane sceneRoot) {
        FadeTransition fade = new FadeTransition(Duration.millis(450), overlay);
        fade.setFromValue(1);
        fade.setToValue(0);
        fade.setOnFinished(e -> sceneRoot.getChildren().remove(overlay));
        fade.play();
    }

    // --- layout ------------------------------------------------------------

    private VBox buildSidebar() {
        VBox logoBox = new VBox(2);
        logoBox.setAlignment(Pos.CENTER);
        logoBox.setPadding(new Insets(0, 0, 16, 0));
        Label logo = new Label("RAVEN");
        logo.getStyleClass().add("sidebar-logo");
        Label logoSub = new Label("client");
        logoSub.getStyleClass().add("sidebar-logo-sub");
        logoBox.getChildren().addAll(logo, logoSub);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        VBox nav = new VBox(4, logoBox,
                homeBtn, modsBtn, versionsBtn, profilesBtn, cosmeticsBtn, spacer, settingsBtn);
        nav.setPadding(new Insets(20, 10, 16, 10));
        nav.getStyleClass().add("sidebar");

        for (ToggleButton b : new ToggleButton[]{homeBtn, modsBtn, versionsBtn, profilesBtn, cosmeticsBtn, settingsBtn}) {
            b.setOnAction(e -> selectPage(b));
        }
        return nav;
    }

    private void selectPage(ToggleButton b) {
        b.setSelected(true);
        for (ToggleButton other : new ToggleButton[]{homeBtn, modsBtn, versionsBtn, profilesBtn, cosmeticsBtn, settingsBtn}) {
            if (other != b) other.setSelected(false);
        }
        Node page;
        if (b == homeBtn) page = homeRoot != null ? homeRoot : buildHome();
        else if (b == modsBtn) page = buildMods();
        else if (b == versionsBtn) page = buildVersions();
        else if (b == profilesBtn) page = buildProfiles();
        else if (b == cosmeticsBtn) page = buildCosmetics();
        else page = buildSettings();
        
        animatePageTransition(page);
    }

    private void animatePageTransition(Node newPage) {
        if (!pageContainer.getChildren().isEmpty()) {
            Node oldPage = pageContainer.getChildren().get(0);
            oldPage.setOpacity(1);
            newPage.setOpacity(0);
            pageContainer.getChildren().add(newPage);

            FadeTransition fadeOut = new FadeTransition(Duration.millis(150), oldPage);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(150), newPage);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);

            fadeOut.setOnFinished(e -> {
                pageContainer.getChildren().remove(oldPage);
                fadeIn.play();
            });
            fadeOut.play();
        } else {
            pageContainer.getChildren().setAll(newPage);
        }
    }

    private Node buildHome() {
        Profile active = activeProfile();

        versionBox = new ComboBox<>();
        versionBox.setPrefWidth(180);
        versionBox.getStyleClass().add("modern-combobox");
        versionBox.getItems().addAll(SUPPORTED_VERSIONS);
        String initialVersion = active != null ? active.version() : config.selectedVersion;
        versionBox.setValue(initialVersion != null && SUPPORTED_VERSIONS.contains(initialVersion)
                ? initialVersion : SUPPORTED_VERSIONS.get(0));

        loaderGroup = new ToggleGroup();
        loaderPills.clear();
        for (String loaderName : List.of("Vanilla", "Fabric", "Quilt", "Forge", "NeoForge")) {
            ToggleButton pill = new ToggleButton(loaderName);
            pill.setToggleGroup(loaderGroup);
            pill.getStyleClass().add("loader-pill");
            pill.setPrefWidth(84);
            pill.setOnAction(e -> {
                if (modsBtn.isSelected()) selectPage(modsBtn);
            });
            loaderPills.add(pill);
        }
        selectLoader(active != null ? active.loader() : "Vanilla");

        memorySlider = new Slider(1, 16, config.memoryMb / 1024.0);
        memorySlider.setShowTickLabels(true);
        memorySlider.setMajorTickUnit(4);
        memorySlider.setBlockIncrement(1);
        final Label memLabel = new Label(((int) memorySlider.getValue()) + " GB");
        memLabel.getStyleClass().add("memory-label");
        memoryLabel = memLabel;
        memorySlider.valueProperty().addListener((o, a, b) -> memLabel.setText(((int) b.doubleValue()) + " GB"));
        // Top bar
        HBox topBar = new HBox(14);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(14, 24, 14, 24));

        Label logoMark = new Label("R");
        logoMark.getStyleClass().add("logo-mark");
        VBox logoBox = new VBox(0);
        Label logoTitle = new Label("RAVENCLIENT");
        logoTitle.getStyleClass().add("logo-text");
        Label logoSub = new Label("rise above");
        logoSub.getStyleClass().add("logo-sub");
        logoBox.getChildren().addAll(logoTitle, logoSub);

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);

        Label avatar = new Label(account != null ? initial(accountLabel.getText()) : "\u263A");
        avatar.getStyleClass().add("avatar");
        Label accountName = new Label(account != null ? accountLabel.getText() : "Not signed in");
        accountName.getStyleClass().add("account-name");
        Button accountButton = new Button(account != null ? "Sign out" : "Sign in");
        accountButton.getStyleClass().add("small-button");
        accountButton.setOnAction(e -> onAccountAction());

        HBox accountInfo = new HBox(10);
        accountInfo.setAlignment(Pos.CENTER_RIGHT);
        accountInfo.getChildren().addAll(avatar, accountName, accountButton);
        topBar.getChildren().addAll(logoMark, logoBox, topSpacer, accountInfo);

        // Hero card
        VBox hero = new VBox(12);
        hero.getStyleClass().add("hero-card");
        HBox.setHgrow(hero, Priority.ALWAYS);

        Label heroKicker = new Label(active != null ? "PROFILE \u00B7 " + active.name().toUpperCase() : "QUICK PLAY");
        heroKicker.getStyleClass().add("hero-kicker");
        Label heroTitle = new Label("Spread your wings");
        heroTitle.getStyleClass().add("hero-title");

        Label loaderCaption = new Label("Loader");
        loaderCaption.getStyleClass().add("hero-label");
        HBox pills = new HBox(8);
        pills.getChildren().addAll(loaderPills);

        Label versionCaption = new Label("Minecraft version");
        versionCaption.getStyleClass().add("hero-label");
        HBox versionRow = new HBox(10, versionBox);

        Label memoryCaption = new Label("Memory");
        memoryCaption.getStyleClass().add("hero-label");
        HBox memoryRow = new HBox(10, memorySlider, memLabel);
        HBox.setHgrow(memorySlider, Priority.ALWAYS);
        
        Button play = new Button("PLAY");
        play.getStyleClass().add("play-button");
        play.setMaxWidth(Double.MAX_VALUE);
        play.setPrefHeight(50);
        play.setOnAction(e -> onLaunch());

        hero.getChildren().addAll(heroKicker, heroTitle, loaderCaption, pills,
                versionCaption, versionRow, memoryCaption, memoryRow, play);

        // Right column
        VBox right = new VBox(14);
        right.setPrefWidth(280);

        VBox profilesCard = new VBox(10);
        profilesCard.getStyleClass().add("glass-panel");
        Label profilesTitle = new Label("Profiles");
        profilesTitle.getStyleClass().add("side-card-title");
        VBox profilesList = new VBox(6);
        refreshProfilesList(profilesList);
        Button newProfileBtn = new Button("+  New profile");
        newProfileBtn.getStyleClass().add("ghost-button");
        newProfileBtn.setMaxWidth(Double.MAX_VALUE);
        newProfileBtn.setOnAction(e -> createProfileDialog());
        profilesCard.getChildren().addAll(profilesTitle, profilesList, newProfileBtn);

        VBox instanceCard = new VBox(10);
        instanceCard.getStyleClass().add("glass-panel");
        Label instanceTitle = new Label("Instance");
        instanceTitle.getStyleClass().add("side-card-title");
        String loaderForMods = activeLoaderName();
        String modsValue = ("Vanilla".equals(loaderForMods) ? "" : loaderForMods + " \u00B7 ") + activeVersion();
        Label modsRowLabel = new Label("Mods set");
        modsRowLabel.getStyleClass().add("side-row-key");
        Label modsRowValue = new Label(modsValue);
        modsRowValue.getStyleClass().add("side-row-value");
        Region instanceSpacer = new Region();
        HBox.setHgrow(instanceSpacer, Priority.ALWAYS);
        HBox modsRow = new HBox(8, modsRowLabel, instanceSpacer, modsRowValue);
        instanceCard.getChildren().addAll(instanceTitle, modsRow);

        right.getChildren().addAll(profilesCard, instanceCard);

        // Player display (skin + name) in the center
        Node playerDisplay = buildPlayerDisplay();

        // Main layout with hero, player display, and right column
        HBox main = new HBox(18);
        main.setPadding(new Insets(22, 26, 22, 26));
        main.getChildren().addAll(hero, playerDisplay, right);
        HBox.setHgrow(hero, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.NEVER);

        // News section at the bottom
        Node newsSection = buildNewsSection();

        VBox root = new VBox(0, topBar, main, newsSection);
        root.getStyleClass().add("home-container");
        homeRoot = root;
        return root;
    }

    private Node buildNewsSection() {
        Label title = new Label("Latest News");
        title.getStyleClass().add("section-label");

        VBox newsItems = new VBox(12);
        newsItems.setPadding(new Insets(16, 28, 16, 28));

        newsItems.getChildren().addAll(
            newsCard("RavenClient " + ClientVersion.VERSION + " Update", "New animated UI with enhanced visuals, smooth page transitions, and a central player skin display. Check it out!"),
            newsCard("Minecraft " + (SUPPORTED_VERSIONS.isEmpty() ? "" : SUPPORTED_VERSIONS.get(SUPPORTED_VERSIONS.size() - 1)) + " Release", "The latest Minecraft update is now supported. Download the new version from the Versions tab.")
        );

        HBox container = new HBox();
        container.setPadding(new Insets(0, 28, 20, 28));
        container.getChildren().add(newsItems);
        HBox.setHgrow(newsItems, Priority.ALWAYS);

        return new VBox(0, title, container);
    }

    private Node newsCard(String title, String desc) {
        VBox card = new VBox(8);
        card.getStyleClass().add("news-card");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label time = new Label("\u23F0");
        time.getStyleClass().add("news-time");
        Label newsTitle = new Label(title);
        newsTitle.getStyleClass().add("news-title");
        header.getChildren().addAll(time, newsTitle);

        Label newsDesc = new Label(desc);
        newsDesc.setWrapText(true);
        newsDesc.getStyleClass().add("news-desc");

        card.getChildren().addAll(header, newsDesc);
        return card;
    }

    private static record CardInfo(String title, String desc) { }

    private Node card(String title, String desc, double spacing) {
        Label t = new Label(title);
        t.getStyleClass().add("card-title");
        Label d = new Label(desc);
        d.setWrapText(true);
        d.getStyleClass().add("card-desc");
        VBox c = new VBox(spacing, t, d);
        c.getStyleClass().add("card");
        return c;
    }

    private Node buildMods() {
        String selectedVersion = activeVersion();
        Loader loader = Loader.fromDisplayName(activeLoaderName());

        Path modsDir = modsDirFor(loader, selectedVersion);
        try {
            Files.createDirectories(modsDir);
        } catch (IOException e) {
            log("Could not create mods directory: " + e);
        }

        ModBrowser browser = new ModBrowser(config, SUPPORTED_VERSIONS, loader, pool, this::appendLog, modsDir);
        Node content = browser.build();
        VBox wrap = new VBox(content);
        wrap.getStyleClass().add("glass-panel");
        Label title = new Label("Mods");
        title.getStyleClass().add("page-title");
        BorderPane.setMargin(title, new Insets(0, 0, 0, 36));
        BorderPane pane = new BorderPane(wrap);
        pane.setTop(title);
        return pane;
    }

    /** The mods directory a loader profile uses: version-scoped for loaders, root for vanilla. */
    private Path modsDirFor(Loader loader, String version) {
        if (loader == null || !loader.isModded()) return config.gameDir.resolve("mods");
        return config.gameDir.resolve("versions").resolve(LoaderMeta.profileId(loader, version)).resolve("mods");
    }

    /** The mods directory for a saved profile, using the same convention as modsDirFor. */
    private Path profileModsDir(Profile p) {
        return modsDirFor(Loader.fromDisplayName(p.loader()), p.version());
    }

    private Node buildVersions() {
        Label title = new Label("Versions");
        title.getStyleClass().add("page-title");

        ComboBox<String> createCombo = new ComboBox<>();
        createCombo.getItems().addAll(SUPPORTED_VERSIONS);
        createCombo.setPromptText("Minecraft version");
        createCombo.getStyleClass().add("modern-combobox");
        createCombo.setPrefWidth(150);

        ComboBox<String> createLoader = new ComboBox<>();
        createLoader.getItems().addAll(
                java.util.Arrays.stream(Loader.moddedLoaders()).map(Loader::displayName).toList());
        createLoader.setValue("Fabric");
        createLoader.setPrefWidth(130);
        createLoader.getStyleClass().add("modern-combobox");
        Button createProfile = new Button("Install profile");
        createProfile.getStyleClass().add("secondary-button");
        createProfile.setOnAction(e -> installLoader(createCombo, createLoader));
        HBox createRow = new HBox(12, createCombo, createLoader, createProfile);
        createRow.setAlignment(Pos.CENTER_LEFT);

        VBox installedBox = new VBox(10);
        installedBox.setPadding(new Insets(14, 32, 24, 32));

        refreshVersions(installedBox);

        Label note = new Label("Mods installed from the Mods tab go into the profile's own mods folder and are loaded automatically when that profile is launched.");
        note.setWrapText(true);
        note.getStyleClass().add("muted");

        VBox list = new VBox(14, title, createRow, note, new Label("Installed profiles:"), installedBox);
        list.setPadding(new Insets(18, 32, 18, 32));
        return new StackPane(new ScrollPane(list));
    }

    private void refreshVersions(VBox box) {
        box.getChildren().clear();
        // Vanilla supported + latest
        List<String> rows = new ArrayList<>(SUPPORTED_VERSIONS);
        rows.add("Latest release");
        for (String v : rows) {
            box.getChildren().add(profileRow("Minecraft " + v, "Vanilla", v, null));
        }
        // Loader profiles
        for (String pid : LoaderMeta.installed(config)) {
            Loader loader = LoaderMeta.loaderOf(pid);
            if (loader == null) continue;
            String mc = pid.substring(loader.prefix().length());
            box.getChildren().add(profileRow("Minecraft " + mc, loader.displayName(), pid,
                    () -> deleteProfile(pid, loader.displayName())));
        }
    }

    private Node profileRow(String name, String type, String id, Runnable onDelete) {
        final String finalId = id;
        final String finalType = type;
        final Runnable finalOnDelete = onDelete;
        
        VBox card = new VBox(10);
        card.getStyleClass().add("profile-row");
        
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        
        Label nameLbl = new Label(name);
        nameLbl.getStyleClass().add("profile-name");
        nameLbl.setMaxWidth(260);
        nameLbl.setTextOverrun(OverrunStyle.ELLIPSIS);
        
        Label typeBadge = new Label(type);
        typeBadge.getStyleClass().add(getBadgeStyle(type));
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button play = new Button("Play");
        play.getStyleClass().add("mini-play");
        play.setOnAction(e -> {
            if (gameProcess != null && gameProcess.isAlive()) {
                setStatus("Minecraft is already running.");
                return;
            }
            launch(finalId, finalType);
        });
        if (finalOnDelete != null) {
            Button del = new Button("Delete");
            del.getStyleClass().add("mini-delete");
            del.setOnAction(e -> finalOnDelete.run());
            row.getChildren().addAll(nameLbl, typeBadge, spacer, play, del);
        } else {
            row.getChildren().addAll(nameLbl, typeBadge, spacer, play);
        }
        card.getChildren().add(row);
        return card;
    }

    private String getBadgeStyle(String type) {
        switch (type) {
            case "Fabric": return "badge-fabric";
            case "NeoForge": return "badge-neoforge";
            case "Forge": return "badge-forge";
            case "Quilt": return "badge-quilt";
            default: return "badge-vanilla";
        }
    }

    // --- profiles ----------------------------------------------------------

    private Profile activeProfile() {
        if (config.selectedProfile == null) return null;
        return ProfileStore.find(config.launcherDir, config.selectedProfile);
    }

    private String activeVersion() {
        Profile p = activeProfile();
        if (p != null) return p.version();
        return versionBox != null ? versionBox.getValue() : SUPPORTED_VERSIONS.get(0);
    }

    private String activeLoaderName() {
        Profile p = activeProfile();
        if (p != null) return p.loader();
        return getLoaderName();
    }

    private void selectLoader(String name) {
        if (loaderPills == null) return;
        for (ToggleButton pill : loaderPills) {
            pill.setSelected(pill.getText().equalsIgnoreCase(name));
        }
    }

    private String getLoaderName() {
        if (loaderPills == null || loaderPills.isEmpty()) return "Vanilla";
        for (ToggleButton pill : loaderPills) {
            if (pill.isSelected()) return pill.getText();
        }
        return "Vanilla";
    }

    private Profile findProfile(String version, String loader) {
        for (Profile p : ProfileStore.load(config.launcherDir)) {
            if (p.version().equals(version) && p.loader().equalsIgnoreCase(loader)) return p;
        }
        return null;
    }

    private void saveConfigQuiet() {
        try {
            config.save();
        } catch (IOException e) {
            log("Could not save config: " + e.getMessage());
        }
    }

    private void refreshHome() {
        buildHome();
        if (!pageContainer.getChildren().isEmpty() && pageContainer.getChildren().get(0) == homeRoot) {
            pageContainer.getChildren().setAll(homeRoot);
        }
    }

    private void playProfile(Profile p) {
        if (account == null) {
            setStatus("Sign in with your Microsoft account to launch Minecraft.");
            return;
        }
        selectLoader(p.loader());
        if (versionBox != null) versionBox.setValue(p.version());
        config.selectedProfile = p.id();
        config.selectedVersion = p.version();
        saveConfigQuiet();
        refreshHome();
        launch(p.version(), p.loader());
    }

    private void deleteSavedProfile(Profile p) {
        try {
            ProfileStore.delete(config.launcherDir, p.id());
            if (p.id().equals(config.selectedProfile)) config.selectedProfile = null;
            saveConfigQuiet();
            if (profilesBtn.isSelected()) {
                selectPage(profilesBtn);
            } else {
                refreshHome();
            }
            setStatus("Deleted profile \"" + p.name() + "\".");
        } catch (Exception ex) {
            setStatus("Could not delete profile: " + ex.getMessage());
        }
    }

    private void refreshProfilesList(VBox box) {
        box.getChildren().clear();
        List<Profile> profiles = ProfileStore.load(config.launcherDir);
        if (profiles.isEmpty()) {
            Label empty = new Label("No profiles yet. Create one to keep a separate mod setup per version.");
            empty.getStyleClass().add("muted");
            empty.setWrapText(true);
            box.getChildren().add(empty);
            return;
        }
        for (Profile p : profiles) {
            box.getChildren().add(compactProfileRow(p));
        }
    }

    private Node compactProfileRow(Profile p) {
        HBox row = new HBox(10);
        row.getStyleClass().add("profile-row");
        row.setAlignment(Pos.CENTER_LEFT);

        Label name = new Label(p.name());
        name.getStyleClass().add("profile-name");
        name.setTextOverrun(OverrunStyle.ELLIPSIS);
        name.setMaxWidth(120);

        Label loaderBadge = new Label(p.loader());
        loaderBadge.getStyleClass().addAll("badge", getBadgeStyle(p.loader()));
        Label versionBadge = new Label(p.version());
        versionBadge.getStyleClass().add("badge-version");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button play = new Button("\u25B6");
        play.getStyleClass().add("mini-play");
        play.setOnAction(e -> playProfile(p));
        Button del = new Button("\u2715");
        del.getStyleClass().add("mini-delete");
        del.setOnAction(e -> deleteSavedProfile(p));

        row.getChildren().addAll(name, loaderBadge, versionBadge, spacer, play, del);
        return row;
    }

    private void createProfileDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("New profile");
        dialog.initOwner(stage);

        TextField name = new TextField();
        name.setPromptText("e.g. My survival world");
        name.setPrefWidth(240);

        ComboBox<String> version = new ComboBox<>();
        version.getItems().addAll(SUPPORTED_VERSIONS);
        version.setValue(activeVersion());
        version.getStyleClass().add("modern-combobox");

        ComboBox<String> loader = new ComboBox<>();
        loader.getItems().addAll("Vanilla", "Fabric", "Quilt", "Forge", "NeoForge");
        loader.setValue(getLoaderName());
        loader.getStyleClass().add("modern-combobox");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.add(new Label("Name"), 0, 0);
        grid.add(name, 1, 0);
        grid.add(new Label("Version"), 0, 1);
        grid.add(version, 1, 1);
        grid.add(new Label("Loader"), 0, 2);
        grid.add(loader, 1, 2);

        DialogPane pane = dialog.getDialogPane();
        pane.setContent(grid);
        pane.getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        pane.getStylesheets().add(getClass().getResource("/raven.css").toExternalForm());

        dialog.setResultConverter(bt -> bt == ButtonType.OK ? ButtonType.OK : null);
        if (dialog.showAndWait().orElse(null) != ButtonType.OK) return;
        if (name.getText() == null || name.getText().isBlank()) {
            setStatus("Profile name cannot be empty.");
            return;
        }
        try {
            Profile p = ProfileStore.create(config.launcherDir, name.getText().trim(), version.getValue(), loader.getValue());
            config.selectedProfile = p.id();
            config.selectedVersion = p.version();
            saveConfigQuiet();
            selectLoader(p.loader());
            if (versionBox != null) versionBox.setValue(p.version());
            refreshHome();
            setStatus("Created profile \"" + p.name() + "\".");
        } catch (Exception ex) {
            setStatus("Could not create profile: " + ex.getMessage());
        }
    }

    private Node buildProfiles() {
        Label title = new Label("Profiles");
        title.getStyleClass().add("page-title");

        TextField nameField = new TextField();
        nameField.setPromptText("Profile name");
        nameField.setPrefWidth(260);
        nameField.getStyleClass().add("search-box");

        ComboBox<String> versionCombo = new ComboBox<>();
        versionCombo.getItems().addAll(SUPPORTED_VERSIONS);
        versionCombo.setValue(activeVersion());
        versionCombo.getStyleClass().add("modern-combobox");
        versionCombo.setPrefWidth(140);

        ComboBox<String> loaderComboP = new ComboBox<>();
        loaderComboP.getItems().addAll("Vanilla", "Fabric", "Quilt", "Forge", "NeoForge");
        loaderComboP.setValue(getLoaderName());
        loaderComboP.getStyleClass().add("modern-combobox");
        loaderComboP.setPrefWidth(140);

        Button createBtn = new Button("Create profile");
        createBtn.getStyleClass().add("primary-button");
        createBtn.setOnAction(e -> {
            if (nameField.getText() == null || nameField.getText().isBlank()) {
                setStatus("Profile name cannot be empty.");
                return;
            }
            try {
                Profile p = ProfileStore.create(config.launcherDir, nameField.getText().trim(), versionCombo.getValue(), loaderComboP.getValue());
                config.selectedProfile = p.id();
                config.selectedVersion = p.version();
                saveConfigQuiet();
                nameField.clear();
                selectPage(profilesBtn);
                setStatus("Created profile \"" + p.name() + "\".");
            } catch (Exception ex) {
                setStatus("Could not create profile: " + ex.getMessage());
            }
        });

        HBox createRow = new HBox(12, nameField, versionCombo, loaderComboP, createBtn);
        createRow.setAlignment(Pos.CENTER_LEFT);

        VBox list = new VBox(14);
        List<Profile> profiles = ProfileStore.load(config.launcherDir);
        if (profiles.isEmpty()) {
            Label empty = new Label("No profiles yet. Create one to keep separate mod sets per Minecraft version.");
            empty.getStyleClass().add("muted");
            empty.setWrapText(true);
            list.getChildren().add(empty);
        } else {
            for (Profile p : profiles) {
                list.getChildren().add(profileCard(p));
            }
        }

        VBox body = new VBox(18, title, createRow, list);
        body.setPadding(new Insets(22, 32, 22, 32));
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        return new StackPane(scroll);
    }

    private Node profileCard(Profile p) {
        VBox card = new VBox(12);
        card.getStyleClass().add("mod-card");

        HBox head = new HBox(14);
        head.setAlignment(Pos.CENTER_LEFT);

        StackPane iconBox = new StackPane();
        Label mark = new Label(initial(p.name()));
        mark.getStyleClass().add("mod-icon-placeholder");
        mark.setMinSize(46, 46);
        mark.setPrefSize(46, 46);
        mark.setMaxSize(46, 46);
        mark.setAlignment(Pos.CENTER);
        mark.setStyle("-fx-background-color: " + iconColor(p.name()) + ";");
        iconBox.getChildren().add(mark);

        VBox info = new VBox(4);
        Label nameLbl = new Label(p.name());
        nameLbl.getStyleClass().add("mod-title");
        HBox badges = new HBox(6);
        Label loaderBadge = new Label(p.loader());
        loaderBadge.getStyleClass().addAll("badge", getBadgeStyle(p.loader()));
        Label versionBadge = new Label(p.version());
        versionBadge.getStyleClass().add("badge-version");
        badges.getChildren().addAll(loaderBadge, versionBadge);
        info.getChildren().addAll(nameLbl, badges);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        boolean active = p.id().equals(config.selectedProfile);
        if (active) {
            Label tag = new Label("ACTIVE");
            tag.getStyleClass().add("badge-active");
            head.getChildren().add(tag);
        }
        Button playBtn = new Button("PLAY");
        playBtn.getStyleClass().add("play-button");
        playBtn.setOnAction(e -> playProfile(p));
        Button deleteBtn = new Button("Delete");
        deleteBtn.getStyleClass().add("mod-remove-button");
        deleteBtn.setOnAction(e -> deleteSavedProfile(p));

        head.getChildren().addAll(iconBox, info, spacer, playBtn, deleteBtn);

        Path modsDir = profileModsDir(p);
        List<Path> jars = new ArrayList<>();
        if (Files.isDirectory(modsDir)) {
            try (java.util.stream.Stream<Path> stream = Files.list(modsDir)) {
                jars = stream.filter(f -> f.getFileName().toString().endsWith(".jar")).sorted().toList();
            } catch (IOException ignored) { }
        }

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);
        Label modsCountLbl = new Label(jars.size() + " mod" + (jars.size() == 1 ? "" : "s"));
        modsCountLbl.getStyleClass().add("side-row-value");
        Button addModsBtn = new Button("+  Add mods");
        addModsBtn.getStyleClass().add("secondary-button");
        addModsBtn.setOnAction(e -> openModrinthForProfile(p));
        Button openFolderBtn = new Button("Open mods folder");
        openFolderBtn.getStyleClass().add("secondary-button");
        openFolderBtn.setOnAction(e -> openFolder(modsDir));
        Button setJdkBtn = new Button("Set JDK");
        setJdkBtn.getStyleClass().add("secondary-button");
        setJdkBtn.setOnAction(e -> setProfileJdk(p));
        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);
        actions.getChildren().addAll(modsCountLbl, actionSpacer, addModsBtn, openFolderBtn, setJdkBtn);

        card.getChildren().add(head);
        card.getChildren().add(actions);

        if (!jars.isEmpty()) {
            VBox modList = new VBox(6);
            Label modListTitle = new Label("Installed mods");
            modListTitle.getStyleClass().add("side-card-title");
            modList.getChildren().add(modListTitle);
            for (Path jar : jars) {
                modList.getChildren().add(installedModRow(p, jar));
            }
            card.getChildren().add(modList);
        }
        return card;
    }

    private Node installedModRow(Profile p, Path jar) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("profile-row");
        Label name = new Label(jar.getFileName().toString());
        name.getStyleClass().add("mod-author");
        name.setTextOverrun(OverrunStyle.ELLIPSIS);
        name.setMaxWidth(440);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button remove = new Button("Remove");
        remove.getStyleClass().add("mod-remove-button");
        remove.setOnAction(e -> {
            try {
                Files.deleteIfExists(jar);
                refreshProfilesPage();
                refreshHome();
                setStatus("Removed " + jar.getFileName() + " from \"" + p.name() + "\".");
            } catch (IOException ex) {
                setStatus("Could not remove mod: " + ex.getMessage());
            }
        });
        row.getChildren().addAll(name, spacer, remove);
        return row;
    }

    /** Opens a Modrinth mod browser scoped to this profile's version + loader. */
    private void openModrinthForProfile(Profile p) {
        Loader loader = Loader.fromDisplayName(p.loader());
        Path modsDir = profileModsDir(p);
        ModBrowser browser = new ModBrowser(config, List.of(p.version()), loader, pool, this::appendLog, modsDir);
        browser.showDialog(stage, () -> {
            refreshProfilesPage();
            refreshHome();
        });
    }

    private void refreshProfilesPage() {
        if (profilesBtn.isSelected()) selectPage(profilesBtn);
    }

    private static String initial(String s) {
        if (s == null || s.isBlank()) return "?";
        return s.substring(0, 1).toUpperCase();
    }

    private static String iconColor(String s) {
        int h = s == null ? 7 : Math.abs(s.hashCode() % 360);
        return "hsb(" + h + ", 65%, 55%)";
    }

    private Node buildPlayerDisplay() {
        VBox container = new VBox(12);
        container.setAlignment(Pos.CENTER);
        container.setPrefWidth(180);
        container.setPadding(new Insets(12, 0, 12, 0));

        StackPane avatarBox = new StackPane();
        avatarBox.setMinSize(80, 80);
        avatarBox.setPrefSize(80, 80);
        avatarBox.setMaxSize(80, 80);
        avatarBox.getStyleClass().add("player-avatar-box");

        if (account != null && account.uuid() != null && !account.uuid().isBlank()) {
            ImageView skin = new ImageView();
            skin.setFitWidth(80);
            skin.setFitHeight(80);
            skin.setPreserveRatio(true);
            String uuid = account.uuid().replace("-", "");
            String avatarUrl = "https://crafatar.com/avatars/" + uuid;
            pool.execute(() -> {
                try {
                    Image img = new Image(avatarUrl, 80, 80, true, true);
                    Platform.runLater(() -> {
                        if (img.getWidth() > 0) {
                            skin.setImage(img);
                        }
                    });
                } catch (Exception ignored) {
                    // Fall back to the initial label
                }
            });
            avatarBox.getChildren().add(skin);
        } else {
            Label placeholder = new Label(account != null ? initial(accountLabel.getText()) : "\u263A");
            placeholder.getStyleClass().add("player-avatar-placeholder");
            avatarBox.getChildren().add(placeholder);
        }

        Label playerName = new Label(account != null ? accountLabel.getText() : "Not signed in");
        playerName.getStyleClass().add("player-name");
        playerName.setMinWidth(160);
        playerName.setAlignment(Pos.CENTER);

        Label playerStatus = new Label(account != null ? "Online" : "Offline");
        playerStatus.getStyleClass().add(account != null ? "player-online" : "player-offline");

        container.getChildren().addAll(avatarBox, playerName, playerStatus);
        return container;
    }

    private void deleteProfile(String profileId, String loaderType) {
        try {
            Path dir = config.gameDir.resolve("versions").resolve(profileId);
            if (Files.exists(dir)) deleteRecursively(dir);
            setStatus("Deleted " + profileId + " (" + loaderType + ").");
        } catch (Exception ex) {
            setStatus("Could not delete " + profileId + ": " + ex.getMessage());
        }
        refreshSidebarVersions();
    }

    private void setProfileJdk(Profile p) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select JDK java.exe for " + p.name());
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Java executable (java.exe)", "java.exe"));
        if (p.javaPath() != null && !p.javaPath().isBlank()) {
            chooser.setInitialDirectory(Path.of(p.javaPath()).getParent().toFile());
        }
        java.io.File picked = chooser.showOpenDialog(null);
        if (picked == null) return;
        try {
            List<ProfileStore.Profile> profiles = ProfileStore.load(config.launcherDir);
            String targetPath = picked.getAbsolutePath();
            List<ProfileStore.Profile> updated = new ArrayList<>();
            for (ProfileStore.Profile prof : profiles) {
                if (prof.id().equals(p.id())) {
                    updated.add(new ProfileStore.Profile(prof.id(), prof.name(), prof.version(), prof.loader(), prof.createdAt(), targetPath));
                } else {
                    updated.add(prof);
                }
            }
            ProfileStore.save(config.launcherDir, updated);
            setStatus("JDK set for " + p.name() + ": " + targetPath);
            refreshProfilesPage();
        } catch (Exception ex) {
            setStatus("Could not set JDK: " + ex.getMessage());
        }
    }

    private void installLoader(ComboBox<String> versionCombo, ComboBox<String> loaderCombo) {
        if (busy) return;
        String mc = versionCombo.getValue();
        Loader loader = loaderCombo.getValue() == null ? null : Loader.fromDisplayName(loaderCombo.getValue());
        if (mc == null || loader == null || !loader.isModded()) {
            setStatus("Pick a version and loader to install a profile for.");
            return;
        }
        final String targetVersion = mc;
        final Loader targetLoader = loader;
        setBusy(true);
        setStatus("Installing " + targetLoader.displayName() + " for " + targetVersion + "...");
        progressBar.setProgress(0);
        progressBar.setVisible(true);
        pool.execute(() -> {
            try {
                LoaderMeta.install(config, targetLoader, targetVersion, uiListener());
                Platform.runLater(() -> {
                    setStatus(targetLoader.displayName() + " installed for " + targetVersion + ". Refresh the Versions tab.");
                    refreshSidebarVersions();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    setStatus(targetLoader.displayName() + " install failed: " + ex.getMessage());
                    log(stackTrace(ex));
                });
            } finally {
                Platform.runLater(() -> setBusy(false));
            }
        });
    }

    private void refreshSidebarVersions() {
        // Trigger Versions page rebuild if visible
        buildVersions();
    }

    private static void deleteRecursively(Path p) throws IOException {
        if (Files.isDirectory(p)) {
            try (var s = Files.list(p)) {
                s.forEach(ch -> { try { deleteRecursively(ch); } catch (IOException ignored) {} });
            }
        }
        Files.deleteIfExists(p);
    }

    private Node buildCosmetics() {
        Label title = new Label("Cosmetics");
        title.getStyleClass().add("page-title");
        Label info = new Label("Capes, particles and emotes are coming in a future update. They work alongside Fabric mods like OptiFine or Entity Model Features once installed from the Mods tab.");
        info.setWrapText(true);
        info.getStyleClass().add("muted");
        
        Label hudInfo = new Label("RavenClient HUD: In-game overlay (Right Shift to open settings) with FPS counter, ping, coordinates, and customizable positioning. Available in v1.0.14+.");
        hudInfo.setWrapText(true);
        hudInfo.getStyleClass().add("muted");
        
        VBox body = new VBox(14, title, info, hudInfo);
        body.setPadding(new Insets(22, 32, 22, 32));
        return new StackPane(new ScrollPane(body));
    }

    private Node buildSettings() {
        Label title = new Label("Settings");
        title.getStyleClass().add("page-title");

        VBox form = new VBox(16);
        form.setPadding(new Insets(22, 32, 22, 32));

        // Memory panel
        VBox memCard = new VBox(10);
        memCard.getStyleClass().add("panel");
        Label memTitle = new Label("Memory");
        memTitle.getStyleClass().add("panel-title");
        HBox memRow = new HBox(12, new Label("RAM:"), memorySlider, memoryLabel);
        memRow.setAlignment(Pos.CENTER_LEFT);
        memCard.getChildren().addAll(memTitle, memRow);

        // Window panel
        VBox winCard = new VBox(10);
        winCard.getStyleClass().add("panel");
        Label winTitle = new Label("Window");
        winTitle.getStyleClass().add("panel-title");
        TextField widthField = new TextField(String.valueOf(config.windowWidth));
        widthField.setPrefWidth(90);
        widthField.getStyleClass().add("modern-combobox");
        TextField heightField = new TextField(String.valueOf(config.windowHeight));
        heightField.setPrefWidth(90);
        heightField.getStyleClass().add("modern-combobox");
        ToggleButton fullscreenBtn = new ToggleButton("Fullscreen");
        fullscreenBtn.getStyleClass().add("toggle-switch");
        fullscreenBtn.setSelected(config.fullscreen);
        HBox winRow = new HBox(10, new Label("Size:"), widthField, new Label("×"), heightField, fullscreenBtn);
        winRow.setAlignment(Pos.CENTER_LEFT);
        winCard.getChildren().addAll(winTitle, winRow);

        // Behavior panel
        VBox behaviorCard = new VBox(10);
        behaviorCard.getStyleClass().add("panel");
        Label behaviorTitle = new Label("Behavior");
        behaviorTitle.getStyleClass().add("panel-title");
        ToggleButton discordBtn = new ToggleButton("Discord Rich Presence");
        discordBtn.getStyleClass().add("toggle-switch");
        discordBtn.setSelected(config.discordRpc);
        ToggleButton autoUpdateBtn = new ToggleButton("Auto-update on startup");
        autoUpdateBtn.getStyleClass().add("toggle-switch");
        autoUpdateBtn.setSelected(config.autoUpdate);
        ToggleButton launchOnStartupBtn = new ToggleButton("Launch on system startup");
        launchOnStartupBtn.getStyleClass().add("toggle-switch");
        launchOnStartupBtn.setSelected(config.launchOnStartup);
        VBox toggles = new VBox(8, discordBtn, autoUpdateBtn, launchOnStartupBtn);
        behaviorCard.getChildren().addAll(behaviorTitle, toggles);

        // Advanced panel
        VBox advancedCard = new VBox(10);
        advancedCard.getStyleClass().add("panel");
        Label advancedTitle = new Label("Advanced");
        advancedTitle.getStyleClass().add("panel-title");
        Label jvmLabel = new Label("JVM arguments:");
        jvmLabel.getStyleClass().add("field-label");
        TextField jvmField = new TextField(config.jvmArgs);
        jvmField.setPromptText("e.g. -XX:+UnlockExperimentalVMOptions");
        jvmField.getStyleClass().add("search-box");
        Label gameLabel = new Label("Game arguments:");
        gameLabel.getStyleClass().add("field-label");
        TextField gameField = new TextField(config.gameArgs);
        gameField.setPromptText("e.g. --fullscreen");
        gameField.getStyleClass().add("search-box");
        advancedCard.getChildren().addAll(advancedTitle, jvmLabel, jvmField, gameLabel, gameField);

        // Actions panel
        VBox actionsCard = new VBox(10);
        actionsCard.getStyleClass().add("panel");
        Label actionsTitle = new Label("Actions");
        actionsTitle.getStyleClass().add("panel-title");
        Button openGame = new Button("Open game folder");
        openGame.getStyleClass().add("secondary-button");
        openGame.setOnAction(e -> openGameFolder());
        Button openLogs = new Button("Open logs folder");
        openLogs.getStyleClass().add("secondary-button");
        openLogs.setOnAction(e -> openFolder(config.launcherDir));
        actionsCard.getChildren().addAll(actionsTitle, openGame, openLogs);

        // Updates panel
        VBox updateCard = new VBox(10);
        updateCard.getStyleClass().add("panel");
        Label updateTitle = new Label("Updates");
        updateTitle.getStyleClass().add("panel-title");
        Label versionLabel = new Label("Version " + ClientVersion.VERSION);
        versionLabel.getStyleClass().add("side-row-value");
        Button checkUpdates = new Button("Check for updates");
        checkUpdates.getStyleClass().add("secondary-button");
        checkUpdates.setOnAction(e -> checkForUpdatesManual());
        HBox updateRow = new HBox(12, versionLabel, checkUpdates);
        updateRow.setAlignment(Pos.CENTER_LEFT);
        updateCard.getChildren().addAll(updateTitle, updateRow);

        Button saveBtn = new Button("Save settings");
        saveBtn.getStyleClass().add("primary-button");
        saveBtn.setOnAction(e -> {
            config.memoryMb = (int) memorySlider.getValue() * 1024;
            try {
                config.windowWidth = Integer.parseInt(widthField.getText());
                config.windowHeight = Integer.parseInt(heightField.getText());
            } catch (Exception ignored) {}
            config.fullscreen = fullscreenBtn.isSelected();
            config.discordRpc = discordBtn.isSelected();
            config.autoUpdate = autoUpdateBtn.isSelected();
            config.launchOnStartup = launchOnStartupBtn.isSelected();
            config.jvmArgs = jvmField.getText();
            config.gameArgs = gameField.getText();
            saveSettings();
        });

        form.getChildren().addAll(title, memCard, winCard, behaviorCard, advancedCard, actionsCard, updateCard, saveBtn);
        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        return new StackPane(scroll);
    }

    private void saveSettings() {
        config.memoryMb = (int) memorySlider.getValue() * 1024;
        try {
            config.save();
            setStatus("Settings saved.");
        } catch (IOException e) {
            setStatus("Could not save settings: " + e.getMessage());
        }
    }

    private void openFolder(Path dir) {
        try {
            Files.createDirectories(dir);
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(dir.toFile());
        } catch (Exception e) {
            setStatus("Could not open folder: " + e.getMessage());
        }
    }

    private Node buildFooter() {
        HBox bar = new HBox();
        bar.getStyleClass().add("status-bar");
        statusLabel = new Label("Idle.");
        statusLabel.getStyleClass().add("status");
        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(5);
        progressBar.getStyleClass().add("thin");
        HBox.setHgrow(progressBar, Priority.ALWAYS);
        bar.setPadding(new Insets(8, 18, 8, 18));

        Region spinnerRegion = new Region();
        spinnerRegion.setMinWidth(14);
        spinnerRegion.setMinHeight(14);
        spinnerRegion.setStyle("-fx-background-color: transparent; -fx-background-radius: 8;");
        spinnerRegion.getStyleClass().add("spinner");
        Timeline spin = new Timeline(
            new KeyFrame(Duration.millis(0), new KeyValue(spinnerRegion.rotateProperty(), 0)),
            new KeyFrame(Duration.millis(700), new KeyValue(spinnerRegion.rotateProperty(), 360))
        );
        spin.setCycleCount(Timeline.INDEFINITE);
        spin.play();
        spinnerRegion.visibleProperty().bind(progressBar.visibleProperty());
        bar.getChildren().add(spinnerRegion);

        console = new TextArea();
        console.setEditable(false);
        console.setWrapText(true);
        console.getStyleClass().add("console");
        Label consoleTitle = new Label("CONSOLE");
        consoleTitle.getStyleClass().add("section-label");
        TitledPane consolePane = new TitledPane("Console", console);
        consolePane.setCollapsible(true);
        consolePane.setExpanded(false);
        VBox footer = new VBox(bar, consolePane);
        return footer;
    }

    private static Label avatar(String name) {
        String init = "R";
        if (name != null && !name.isBlank() && !"Not signed in".equals(name)) {
            String[] parts = name.trim().split("\\s+");
            init = parts.length > 1 ? ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase()
                    : name.substring(0, Math.min(2, name.length())).toUpperCase();
        }
        Label l = new Label(init);
        l.getStyleClass().add("avatar");
        return l;
    }

    // --- behaviour ---------------------------------------------------------

    private void onAccountAction() {
        if (account != null) signOut(false);
        else startLogin();
    }

    private void startLogin() {
        if (busy) return;
        setBusy(true);
        setStatus("Starting Microsoft login...");
        MicrosoftAuthenticator auth = new MicrosoftAuthenticator();
        pool.execute(() -> {
            try {
                DeviceCodeSession session = auth.startDeviceFlow();
                Platform.runLater(() -> showDeviceDialog(session));
                Account acc = auth.waitForDeviceCode(session);
                Platform.runLater(() -> applyAccount(acc));
            } catch (AuthException ex) {
                Platform.runLater(() -> {
                    setStatus(ex.getMessage());
                    closeDeviceDialog();
                    setBusy(false);
                });
            }
        });
    }

    private void showDeviceDialog(DeviceCodeSession session) {
        deviceDialog = new Dialog<>();
        deviceDialog.setTitle("Sign in with Microsoft");
        deviceDialog.initOwner(stage);

        Label msg = new Label("Go to the Microsoft device login page and enter this code:");
        msg.getStyleClass().add("device-code");
        Label code = new Label(session.userCode());
        code.getStyleClass().add("device-code-value");
        Button openBrowser = new Button("Open " + session.verificationUri());
        openBrowser.getStyleClass().add("secondary-button");
        openBrowser.setMaxWidth(Double.MAX_VALUE);
        openBrowser.setOnAction(e -> openBrowser(session.verificationUri()));

        VBox content = new VBox(14, msg, code, openBrowser);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER);

        deviceDialog.getDialogPane().setContent(content);
        dialogClose(deviceDialog);
        deviceDialog.show();
        openBrowser(session.verificationUri());
    }

    private static void dialogClose(Dialog<?> d) {
        d.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        d.getDialogPane().getStylesheets().add(LauncherUI.class.getResource("/raven.css").toExternalForm());
        d.setResultConverter(bt -> null);
    }

    private void applyAccount(Account acc) {
        this.account = acc;
        try {
            AccountStore.save(config.launcherDir, acc);
        } catch (IOException e) {
            log("Could not save account: " + e.getMessage());
        }
        accountLabel.setText(acc.username());
        setStatus("Signed in as " + acc.username());
        log("Signed in as " + acc.username() + " (" + acc.uuid() + ")");
        closeDeviceDialog();
        setBusy(false);
        rebuildHome();
    }

    private void rebuildHome() {
        Node newHome = buildHome();
        animatePageTransition(newHome);
    }

    private void loadAccount() {
        try {
            Account saved = AccountStore.load(config.launcherDir);
            if (saved == null) {
                accountLabel.setText("Not signed in");
                return;
            }
            accountLabel.setText(saved.username());
            setStatus("Restoring session...");
            pool.execute(() -> {
                try {
                    Account fresh = new MicrosoftAuthenticator().refresh(saved);
                    Platform.runLater(() -> applyAccount(fresh));
                } catch (AuthException ex) {
                    Platform.runLater(() -> {
                        setStatus("Session expired - please sign in again.");
                        signOut(true);
                    });
                }
            });
        } catch (IOException e) {
            log("Could not load saved account: " + e.getMessage());
        }
    }

    private void loadVersions() {
        if (versionBox != null) versionBox.setDisable(true);
        pool.execute(() -> {
            try {
                GameLauncher gl = new GameLauncher(config, uiListener());
                List<String> ids = gl.availableVersions();
                List<String> supported = new ArrayList<>(SUPPORTED_VERSIONS);
                supported.retainAll(ids);
                Platform.runLater(() -> {
                    if (versionBox != null) {
                        versionBox.getItems().clear();
                        versionBox.getItems().add("Latest release");
                        versionBox.getItems().addAll(supported);
                        String saved = config.selectedVersion;
                        if (saved != null && !saved.isBlank() && versionBox.getItems().contains(saved)) {
                            versionBox.setValue(saved);
                        } else {
                            versionBox.setValue("Latest release");
                        }
                        versionBox.setDisable(false);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> setStatus("Could not fetch versions: " + e.getMessage()));
            }
        });
    }

    private void onLaunch() {
        String selected = versionBox != null ? versionBox.getValue() : null;
        if (selected == null || selected.isEmpty()) {
            setStatus("Please select a Minecraft version.");
            return;
        }
        String loader = getLoaderName();
        if (loader == null || loader.isEmpty()) {
            loader = "Vanilla";
        }
        Profile match = findProfile(selected, loader);
        config.selectedProfile = match != null ? match.id() : null;
        config.selectedVersion = "Latest release".equals(selected) ? null : selected;
        launch(selected, loader);
    }

    private void launch(String version, String loader) {
        if (account == null) {
            setStatus("Sign in with your Microsoft account to launch Minecraft.");
            return;
        }
        if (gameProcess != null && gameProcess.isAlive()) {
            setStatus("Minecraft is already running.");
            return;
        }
        String savedVersion = version.equals("Latest release") ? version : versionBox != null ? versionBox.getValue() : version;
        config.selectedVersion = savedVersion;
        config.memoryMb = (int) memorySlider.getValue() * 1024;
        try {
            config.save();
        } catch (IOException e) {
            log("Could not save config: " + e.getMessage());
        }

        setBusy(true);
        final String launchVersion = version;
        final String launchLoaderName = loader;
        setUpStatus("Preparing " + version + "...");

        GameLauncher gl = new GameLauncher(config, uiListener());
        pool.execute(() -> {
            try {
                String actualVersion = launchVersion;

                // Handle loader installation
                Loader launchLoader = Loader.fromDisplayName(launchLoaderName);
                if (launchLoader != Loader.VANILLA) {
                    String mc = gl.resolveId(launchVersion);
                    if (!LoaderMeta.hasProfile(config, launchLoader, mc)) {
                        String status = launchLoader.displayName() + " not installed; installing for " + mc + "...";
                        Platform.runLater(() -> setStatus(status));
                        LoaderMeta.install(config, launchLoader, mc, uiListener());
                    }
                    actualVersion = LoaderMeta.profileId(launchLoader, mc);
                }
                
                GameLauncher.LaunchData data = gl.prepare(actualVersion);
                currentLaunchData = data;
                Process process = gl.launch(data, account);
                gameProcess = process;
                Platform.runLater(() -> {
                    setStatus("Minecraft is starting...");
                    log("Minecraft launched (PID " + process.pid() + ").");
                    progressBar.setVisible(false);
                });
            } catch (Exception e) {
                final String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                Platform.runLater(() -> {
                    setStatus("Launch failed: " + errorMessage);
                    log(stackTrace(e));
                    progressBar.setVisible(false);
                });
            } finally {
                Platform.runLater(() -> setBusy(false));
            }
        });
    }

    private void openGameFolder() {
        openFolder(config.gameDir);
    }

    private void openBrowser(String uri) {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(uri));
        } catch (Exception e) {
            log("Could not open browser: " + e.getMessage());
        }
    }

    private void signOut(boolean quiet) {
        this.account = null;
        try {
            AccountStore.delete(config.launcherDir);
        } catch (IOException ignored) { }
        accountLabel.setText("Not signed in");
        rebuildHome();
        if (!quiet) {
            setStatus("Signed out.");
            log("Signed out.");
        }
    }

    private GameLauncher.Listener uiListener() {
        return new GameLauncher.Listener() {
            @Override public void log(String line) { Platform.runLater(() -> appendLog(line)); }
            @Override public void status(String text) { Platform.runLater(() -> setStatus(text)); }
            @Override public void progress(double fraction) { Platform.runLater(() -> progressBar.setProgress(fraction)); }
        };
    }

    private void appendLog(String line) {
        console.appendText(line + "\n");
        if (console.getLength() > 200_000) {
            console.deleteText(0, console.getLength() - 150_000);
        }
        console.positionCaret(console.getLength());
    }

    private void log(String line) { appendLog(line); }

    private void setStatus(String text) { statusLabel.setText(text); }

    private void setUpStatus(String text) {
        setStatus(text);
        statusLabel.setText(text);
        progressBar.setProgress(0);
        progressBar.setVisible(true);
    }

    private void setBusy(boolean value) {
        this.busy = value;
        if (launchButton != null) launchButton.setDisable(value);
        if (versionBox != null) versionBox.setDisable(value);
        if (loaderPills != null) {
            for (ToggleButton pill : loaderPills) pill.setDisable(value);
        }
    }

    private void closeDeviceDialog() {
        if (deviceDialog != null && deviceDialog.isShowing()) deviceDialog.close();
        deviceDialog = null;
    }

    private static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    @Override
    public void stop() {
        pool.shutdownNow();
    }

    static class ModCount {
        static String labelFor(LauncherConfig config) {
            try {
                return String.valueOf(LoaderMeta.installed(config).size());
            } catch (Exception e) {
                return "0";
            }
        }
    }
}
