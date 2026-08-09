package org.ravenclient.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.ravenclient.config.LauncherConfig;
import org.ravenclient.game.Loader;
import org.ravenclient.mod.ModManager;
import org.ravenclient.mod.ModSearch;
import org.ravenclient.mod.ModVersion;
import org.ravenclient.mod.Modrinth;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * A Modrinth catalog browser, aligned with OneLauncher's package store:
 *
 * <ul>
 *   <li>search is faceted on {@code project_type}, the selected Minecraft version and the
 *       loader, so filtering happens server-side;</li>
 *   <li>installing resolves the newest version for the game version + loader and pulls in
 *       required dependencies automatically;</li>
 *   <li>installed mods can be updated or removed, with update detection done by comparing
 *       the newest published build against the recorded one.</li>
 * </ul>
 */
public class ModBrowser {

    static final int PAGE_SIZE = 20;

    private final LauncherConfig config;
    private final Loader loader;
    private final ExecutorService pool;
    private final Consumer<String> log;
    private final Path modsDir;

    private final List<String> versionOptions;
    private final ComboBox<String> versionCombo;
    private final TextField searchBox;
    private final Button goButton;
    private final Button addFileButton;
    private final Label statusLabel;
    private final FlowPane resultsBox;
    private final Label pageLabel;
    private final Button prevButton;
    private final Button nextButton;

    private int currentOffset = 0;
    private int total = 0;
    private boolean built = false;

    public ModBrowser(LauncherConfig config, List<String> versionOptions, Loader loader,
                      ExecutorService pool, Consumer<String> log, Path modsDir) {
        this.config = config;
        this.versionOptions = versionOptions;
        this.loader = loader;
        this.pool = pool;
        this.log = log;
        this.modsDir = modsDir != null ? modsDir : config.gameDir.resolve("mods");

        versionCombo = new ComboBox<>();
        versionCombo.getItems().addAll(versionOptions);
        if (!versionOptions.isEmpty()) versionCombo.getSelectionModel().selectFirst();
        versionCombo.getStyleClass().add("modern-combobox");
        searchBox = new TextField();
        searchBox.getStyleClass().add("search-box");
        searchBox.setPromptText("Search " + (loader == null ? "all" : loader.displayName().toLowerCase()) + " mods on Modrinth...");
        goButton = new Button("Search");
        goButton.getStyleClass().add("primary-button");
        addFileButton = new Button("Add mod file");
        addFileButton.getStyleClass().add("secondary-button");
        addFileButton.setTooltip(new Tooltip("Import a local .jar mod into this profile's mods folder"));
        statusLabel = new Label("Enter a search term or clear it for the full catalog, then press Search.");
        statusLabel.getStyleClass().add("muted");
        statusLabel.setWrapText(true);
        resultsBox = new FlowPane();
        resultsBox.setHgap(14);
        resultsBox.setVgap(14);
        resultsBox.setPadding(new Insets(4));
        pageLabel = new Label();
        pageLabel.getStyleClass().add("mod-stats");
        prevButton = new Button("<");
        prevButton.getStyleClass().add("secondary-button");
        nextButton = new Button(">");
        nextButton.getStyleClass().add("secondary-button");
    }

    /** Builds and returns the browser content node. Idempotent: safe to call once per instance. */
    public Node build() {
        if (built) return resultsBox;
        built = true;

        Label loaderLabel = new Label(loader == null ? "Vanilla" : loader.displayName());
        loaderLabel.getStyleClass().add("mod-loader-label");

        HBox filterBar = new HBox(10,
                new Label("Minecraft:"), versionCombo,
                new Label("Loader:"), loaderLabel,
                searchBox, goButton, addFileButton);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchBox, Priority.ALWAYS);

        HBox nav = new HBox(10, prevButton, pageLabel, nextButton);
        nav.setAlignment(Pos.CENTER);
        Label powered = new Label("Powered by Modrinth");
        powered.getStyleClass().add("mod-powered");
        HBox footer = new HBox(14, nav, new Region(), powered);
        footer.setAlignment(Pos.CENTER_LEFT);

        ScrollPane scroll = new ScrollPane(resultsBox);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.getStyleClass().add("mod-scroll");

        VBox root = new VBox(14, filterBar, statusLabel, scroll, footer);
        root.setPadding(new Insets(4, 4, 4, 4));

        goButton.setOnAction(e -> search(0));
        prevButton.setOnAction(e -> search(currentOffset - PAGE_SIZE));
        nextButton.setOnAction(e -> search(currentOffset + PAGE_SIZE));
        addFileButton.setOnAction(e -> importModFiles());
        goButton.setDefaultButton(true);

        search(0);
        return root;
    }

    /** Imports local .jar mod file(s) into this profile's mods folder. */
    private void importModFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Add mod to " + modsDir.getFileName());
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Minecraft mods (*.jar)", "*.jar"));
        List<File> picked = chooser.showOpenMultipleDialog(null);
        if (picked == null || picked.isEmpty()) return;

        int imported = 0;
        List<String> skipped = new ArrayList<>();
        try {
            Files.createDirectories(modsDir);
            for (File f : picked) {
                if (Files.exists(modsDir.resolve(f.getName()))) {
                    skipped.add(f.getName());
                    continue;
                }
                Files.copy(f.toPath(), modsDir.resolve(f.getName()));
                imported++;
            }
        } catch (Exception ex) {
            log.accept("Could not import mod: " + ex);
            statusLabel.setText("Import failed: " + ex.getMessage());
            return;
        }

        StringBuilder msg = new StringBuilder();
        if (imported > 0) {
            msg.append("Imported ").append(imported).append(" mod file(s) to ")
                    .append(modsDir.getFileName());
        }
        if (!skipped.isEmpty()) {
            if (msg.length() > 0) msg.append(" · ");
            msg.append("Skipped (already present): ").append(String.join(", ", skipped));
        }
        statusLabel.setText(msg.toString());
        log.accept(msg.toString());
        search(currentOffset);
    }

    /** Shows the browser standalone in a modal dialog. */
    public void showDialog(Window owner) {
        showDialog(owner, null);
    }

    /** Shows the browser standalone; {@code afterClose} runs once the dialog is dismissed. */
    public void showDialog(Window owner, Runnable afterClose) {
        DialogPane pane = new DialogPane();
        pane.setContent(build());
        pane.setPrefWidth(800);
        pane.setPrefHeight(560);

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Add mods to " + modsDir.getFileName() + " (Modrinth)");
        dialog.setDialogPane(pane);
        if (owner != null) dialog.initOwner(owner);
        pane.getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().getStylesheets()
                .add(getClass().getResource("/raven.css").toExternalForm());
        if (afterClose != null) dialog.setOnHiding(e -> afterClose.run());
        dialog.show();
    }

    private void search(int offset) {
        String query = searchBox.getText();
        String version = versionCombo.getValue();
        if (version == null) return;
        currentOffset = offset;
        statusLabel.setText("Searching Modrinth...");
        goButton.setDisable(true);
        prevButton.setDisable(true);
        nextButton.setDisable(true);
        resultsBox.getChildren().clear();

        pool.execute(() -> {
            List<ModSearch.Hit> hits = List.of();
            try {
                ModSearch page = Modrinth.search(query, version, loader, offset, PAGE_SIZE);
                hits = page.hits() == null ? List.of() : page.hits();
                total = page.total();
            } catch (Exception ex) {
                log.accept("Modrinth search error: " + ex);
            }
            final List<ModSearch.Hit> rendered = hits;
            Platform.runLater(() -> {
                if (rendered.isEmpty()) {
                    statusLabel.setText("No mods found" + (query.isBlank() ? "" : " for \"" + query + "\"")
                            + " on " + version + " (" + loaderName() + ").");
                } else {
                    statusLabel.setText("Showing " + rendered.size() + " of " + total + " mods.");
                }
                resultsBox.getChildren().clear();
                for (ModSearch.Hit hit : rendered) resultsBox.getChildren().add(gridCard(hit));
                goButton.setDisable(false);
                prevButton.setDisable(offset <= 0);
                nextButton.setDisable(offset + PAGE_SIZE >= total || rendered.isEmpty());
                pageLabel.setText("Page " + (offset / PAGE_SIZE + 1) + " / " + (total / PAGE_SIZE + 1));
            });
        });
    }

    private String loaderName() {
        return loader == null ? "Vanilla" : loader.displayName();
    }

    private Node row(ModSearch.Hit hit) {
        VBox card = new VBox(10);
        card.getStyleClass().add("mod-card");

        HBox body = new HBox(14);
        body.setAlignment(Pos.CENTER_LEFT);

        StackPane iconBox = new StackPane();
        iconBox.setMinSize(56, 56);
        iconBox.setPrefSize(56, 56);
        iconBox.setMaxSize(56, 56);
        iconBox.getStyleClass().add("mod-icon-box");

        Label placeholder = new Label(initial(hit.title()));
        placeholder.getStyleClass().add("mod-icon-placeholder");
        placeholder.setStyle("-fx-background-color: " + iconColor(hit.title()) + ";");
        placeholder.setMinSize(56, 56);
        placeholder.setPrefSize(56, 56);
        placeholder.setMaxSize(56, 56);
        placeholder.setAlignment(Pos.CENTER);

        ImageView icon = new ImageView();
        icon.setFitWidth(56);
        icon.setFitHeight(56);
        icon.setPreserveRatio(true);
        Rectangle rounded = new Rectangle(56, 56);
        rounded.setArcWidth(16);
        rounded.setArcHeight(16);
        icon.setClip(rounded);

        iconBox.getChildren().addAll(placeholder, icon);
        loadIcon(hit, icon);

        VBox info = new VBox(5);
        Label title = new Label(hit.title());
        title.getStyleClass().add("mod-title");
        title.setMaxWidth(360);
        title.setTextOverrun(OverrunStyle.ELLIPSIS);

        Label author = new Label();
        author.getStyleClass().add("mod-author");
        String meta = (hit.author() == null || hit.author().isBlank() ? "" : hit.author())
                + "  ·  " + formatNumber(hit.downloads()) + " downloads"
                + (hit.follows() > 0 ? "  ·  " + formatNumber(hit.follows()) + " follows" : "");
        author.setText(meta);
        author.setMaxWidth(360);
        author.setTextOverrun(OverrunStyle.ELLIPSIS);

        Label description = new Label(hit.description() == null ? "" : hit.description().strip());
        description.getStyleClass().add("mod-description");
        description.setMaxWidth(360);
        description.setTextOverrun(OverrunStyle.ELLIPSIS);

        info.getChildren().addAll(title, author, description);

        if (hit.categories() != null && !hit.categories().isEmpty()) {
            HBox badges = new HBox(4);
            int shown = 0;
            for (String cat : hit.categories()) {
                if (shown >= 3) break;
                Label badge = new Label(cat);
                badge.getStyleClass().add("mod-category");
                badges.getChildren().add(badge);
                shown++;
            }
            info.getChildren().add(badges);
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actions = buildActions(hit);

        body.getChildren().addAll(iconBox, info, spacer, actions);
        card.getChildren().add(body);
        return card;
    }

    private Node gridCard(ModSearch.Hit hit) {
        VBox card = new VBox(8);
        card.getStyleClass().add("mod-card-grid");
        card.setPrefWidth(220);
        card.setMaxWidth(220);

        StackPane iconBox = new StackPane();
        iconBox.setMinSize(64, 64);
        iconBox.setPrefSize(64, 64);
        iconBox.setMaxSize(64, 64);
        iconBox.setAlignment(Pos.CENTER);
        iconBox.getStyleClass().add("mod-icon-box");

        Label placeholder = new Label(initial(hit.title()));
        placeholder.getStyleClass().add("mod-icon-placeholder");
        placeholder.setStyle("-fx-background-color: " + iconColor(hit.title()) + ";");
        placeholder.setMinSize(64, 64);
        placeholder.setPrefSize(64, 64);
        placeholder.setMaxSize(64, 64);
        placeholder.setAlignment(Pos.CENTER);

        ImageView icon = new ImageView();
        icon.setFitWidth(64);
        icon.setFitHeight(64);
        icon.setPreserveRatio(true);
        Rectangle rounded = new Rectangle(64, 64);
        rounded.setArcWidth(18);
        rounded.setArcHeight(18);
        icon.setClip(rounded);

        iconBox.getChildren().addAll(placeholder, icon);
        loadIcon(hit, icon, 64);

        Label title = new Label(hit.title());
        title.getStyleClass().add("mod-title");
        title.setWrapText(true);
        title.setMaxHeight(40);
        title.setTextOverrun(OverrunStyle.ELLIPSIS);

        Label author = new Label();
        author.getStyleClass().add("mod-author");
        String meta = (hit.author() == null || hit.author().isBlank() ? "" : hit.author())
                + "  ·  " + formatNumber(hit.downloads()) + " downloads";
        author.setText(meta);
        author.setWrapText(true);
        author.setMaxHeight(28);
        author.setTextOverrun(OverrunStyle.ELLIPSIS);

        HBox actions = buildActions(hit);
        actions.setMaxWidth(Double.MAX_VALUE);

        card.getChildren().addAll(iconBox, title, author, actions);
        return card;
    }

    private void loadIcon(ModSearch.Hit hit, ImageView icon, int size) {
        if (hit.icon_url() == null || hit.icon_url().isBlank()) return;
        pool.execute(() -> {
            try {
                Image img = new Image(hit.icon_url(), size, size, true, true, true);
                Platform.runLater(() -> icon.setImage(img));
            } catch (Exception ignored) {
                // icon fails to load -> the colored placeholder stays visible
            }
        });
    }

    private void loadIcon(ModSearch.Hit hit, ImageView icon) {
        loadIcon(hit, icon, 56);
    }

    private HBox buildActions(ModSearch.Hit hit) {
        HBox box = new HBox(8);
        box.setAlignment(Pos.CENTER_RIGHT);

        ModManager.InstalledMod installedMod = ModManager.find(modsDir, hit.project_id());
        if (installedMod != null) {
            Button remove = new Button("Remove");
            remove.getStyleClass().add("mod-remove-button");
            remove.setOnAction(e -> remove(hit, box));

            Button update = new Button("Checking...");
            update.getStyleClass().add("mod-update-button");
            update.setDisable(true);
            box.getChildren().addAll(update, remove);

            pool.execute(() -> {
                boolean upd;
                try {
                    upd = ModManager.hasUpdate(modsDir, installedMod, versionCombo.getValue(), loader);
                } catch (Exception ex) {
                    upd = false;
                }
                final boolean hasUpdate = upd;
                Platform.runLater(() -> {
                    if (hasUpdate) {
                        update.setText("Update");
                        update.setDisable(false);
                        update.setOnAction(e -> install(hit, versionCombo.getValue(), update));
                    } else {
                        update.setText("Up to date");
                    }
                });
            });
            return box;
        }

        Button install = new Button("Install");
        install.getStyleClass().add("mod-install-button");
        install.setMaxWidth(Double.MAX_VALUE);
        install.setOnAction(e -> install(hit, versionCombo.getValue(), install));
        box.getChildren().add(install);
        return box;
    }

    private void install(ModSearch.Hit hit, String version, Button button) {
        button.setDisable(true);
        button.setText("Resolving...");
        pool.execute(() -> {
            try {
                List<ModVersion> versions = Modrinth.versionsFor(hit.slug(), version, loader);
                if (versions.isEmpty()) {
                    Platform.runLater(() -> {
                        button.setText("N/A (" + version + ")");
                        button.setDisable(false);
                    });
                    return;
                }
                ModVersion target = versions.get(0);
                Platform.runLater(() -> button.setText("Downloading..."));
                List<ModManager.InstallResult> results = ModManager.install(modsDir, target, version, loader);
                Platform.runLater(() -> {
                    button.setText("Installed");
                    button.setDisable(true);
                    for (ModManager.InstallResult r : results) {
                        log.accept("Installed " + r.mod().name() + " -> " + r.file());
                    }
                    if (results.size() > 1) {
                        log.accept("+ " + (results.size() - 1) + " required dependenc"
                                + (results.size() - 1 == 1 ? "y" : "ies") + " installed");
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    button.setText("Failed");
                    button.setDisable(false);
                    log.accept("Modrinth install error for " + hit.slug() + ": " + ex);
                });
            }
        });
    }

    private void remove(ModSearch.Hit hit, HBox box) {
        box.setDisable(true);
        pool.execute(() -> {
            try {
                ModManager.remove(modsDir, hit.project_id());
                Platform.runLater(() -> {
                    log.accept("Removed " + hit.title());
                    Button install = new Button("Install");
                    install.getStyleClass().add("mod-install-button");
                    install.setPrefWidth(92);
                    install.setOnAction(e -> install(hit, versionCombo.getValue(), install));
                    box.setDisable(false);
                    box.getChildren().setAll(install);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    box.setDisable(false);
                    log.accept("Remove failed for " + hit.slug() + ": " + ex);
                });
            }
        });
    }

    private static String initial(String title) {
        if (title == null || title.isBlank()) return "?";
        return title.trim().substring(0, 1).toUpperCase();
    }

    private static String iconColor(String title) {
        int hue = Math.abs((title == null ? 0 : title.hashCode()) % 360);
        Color c = Color.hsb(hue, 0.55, 0.92);
        return String.format("#%02x%02x%02x",
                (int) (c.getRed() * 255), (int) (c.getGreen() * 255), (int) (c.getBlue() * 255));
    }

    private static String formatNumber(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fK", n / 1000.0);
        return String.valueOf(n);
    }
}
