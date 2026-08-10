package org.ravenclient.overlay;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * The in-game "bird nest" menu opened by the configurable menu key.
 * Sidebar: Browse / Layout / Keybinds / Profiles / Settings.
 */
public class NestGui extends StackPane {

    private final HudConfig config;
    private final List<HudElement> elements;
    private final Runnable onEditHud;
    private final Runnable onModulesChanged;
    private final Runnable onClose;

    private final VBox content = new VBox(0);
    private final TextField search = new TextField();
    private final VBox moduleList = new VBox(8);
    private final VBox keybindList = new VBox(8);
    private final VBox profileList = new VBox(8);
    private VBox navList;
    private Region dim;

    private String activeCategory = "All";
    private String activePage = "Browse";

    // Keybind capture state
    private boolean capturing = false;
    private Consumer<Integer> captureSink;
    private Button captureButton;

    public NestGui(HudConfig config, List<HudElement> elements, Runnable onEditHud,
                   Runnable onModulesChanged, Runnable onClose) {
        this.config = config;
        this.elements = elements;
        this.onEditHud = onEditHud;
        this.onModulesChanged = onModulesChanged;
        this.onClose = onClose;
        build();
    }

    private void build() {
        dim = new Region();
        dim.setStyle("-fx-background-color: #00000088;");
        dim.setOnMouseClicked(e -> onClose.run());

        HBox panel = new HBox(0);
        panel.setMaxWidth(880);
        panel.setMaxHeight(620);
        panel.setStyle("-fx-background-color: #12121eee; -fx-background-radius: 12; -fx-effect: dropshadow(gaussian, #00000099, 32, 0, 0, 8);");
        panel.setOnMouseClicked(e -> e.consume());

        panel.getChildren().addAll(buildSidebar(), buildContent());

        StackPane.setAlignment(panel, Pos.CENTER);

        FadeTransition ft = new FadeTransition(Duration.millis(180), panel);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();

        getChildren().addAll(dim, panel);

        addEventFilter(MouseEvent.MOUSE_PRESSED, this::onCaptureMouse);
        addEventFilter(KeyEvent.KEY_PRESSED, this::onCaptureKey);
    }

    // ---------------- Sidebar ----------------

    private VBox buildSidebar() {
        VBox sidebar = new VBox(4);
        sidebar.setPrefWidth(180);
        sidebar.setPadding(new Insets(20, 8, 20, 8));
        sidebar.setStyle("-fx-background-color: #0e0e1a; -fx-background-radius: 12 0 0 12;");

        Label logo = new Label("RAVEN NEST");
        logo.setStyle("-fx-text-fill: #7c6af7; -fx-font-weight: bold; -fx-font-size: 16;");
        logo.setPadding(new Insets(0, 0, 0, 8));
        Label bird = new Label("\uD83E\uDDBA  bird nest");
        bird.setStyle("-fx-text-fill: #666; -fx-font-size: 10;");
        bird.setPadding(new Insets(0, 0, 14, 10));

        navList = new VBox(4);
        for (String item : new String[]{"Browse", "Layout", "Keybinds", "Profiles", "Settings"}) {
            Button b = navBtn(item);
            b.setOnAction(e -> {
                if (item.equals("Layout")) {
                    onEditHud.run();
                } else {
                    showPage(item);
                }
            });
            navList.getChildren().add(b);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button close = navBtn("✕  Close");
        close.setOnAction(e -> onClose.run());

        sidebar.getChildren().addAll(logo, bird, navList, spacer, close);
        restyleNav();
        return sidebar;
    }

    private Button navBtn(String text) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setPadding(new Insets(9, 12, 9, 12));
        b.setStyle("-fx-background-color: transparent; -fx-text-fill: #aaa; -fx-background-radius: 6; -fx-font-size: 12; -fx-cursor: hand;");
        return b;
    }

    private void restyleNav() {
        if (navList == null) return;
        for (Node n : navList.getChildren()) {
            if (!(n instanceof Button b)) continue;
            boolean isClose = b.getText().startsWith("✕");
            boolean sel = !isClose && b.getText().equals(activePage);
            b.setStyle("-fx-background-color: " + (sel ? "#7c6af733" : "transparent")
                    + "; -fx-text-fill: " + (sel ? "#7c6af7" : "#aaa")
                    + "; -fx-background-radius: 6; -fx-font-size: 12; -fx-cursor: hand;");
        }
    }

    private VBox buildContent() {
        content.setPadding(new Insets(20));
        HBox.setHgrow(content, Priority.ALWAYS);
        showPage("Browse");
        return content;
    }

    private void showPage(String page) {
        activePage = page;
        cancelCapture();
        content.getChildren().clear();
        content.getChildren().add(switch (page) {
            case "Keybinds" -> buildKeybindsPage();
            case "Profiles" -> buildProfilesPage();
            case "Settings" -> buildSettingsPage();
            default -> buildBrowsePage();
        });
        restyleNav();
    }

    private Label pageTitle(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: white; -fx-font-size: 16; -fx-font-weight: bold;");
        return l;
    }

    private Label pageHint(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #888; -fx-font-size: 10;");
        return l;
    }

    private ScrollPane scroll(VBox list) {
        ScrollPane sp = new ScrollPane(list);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(sp, Priority.ALWAYS);
        return sp;
    }

    // ---------------- Browse ----------------

    private VBox buildBrowsePage() {
        VBox page = new VBox(10);

        search.setPromptText("Search mods...");
        search.setStyle("-fx-background-color: #1e1e30; -fx-text-fill: white; -fx-prompt-text-fill: #666; -fx-background-radius: 6; -fx-border-color: #333355; -fx-border-radius: 6;");
        search.textProperty().addListener((o, a, b) -> refreshModules());

        HBox chips = new HBox(6);
        for (String cat : ModuleRegistry.CATEGORIES) {
            ToggleButton chip = new ToggleButton(cat);
            chip.setSelected(cat.equals(activeCategory));
            chip.setStyle(chipStyle(cat.equals(activeCategory)));
            chip.setOnAction(e -> {
                activeCategory = cat;
                restyleChips(chips);
                refreshModules();
            });
            chips.getChildren().add(chip);
        }

        page.getChildren().addAll(pageTitle("Browse Mods"),
                pageHint("Built-in, fully legitimate mods. PvP mods are informational, visual or cosmetic only."),
                search, chips, scroll(moduleList));
        refreshModules();
        return page;
    }

    private String chipStyle(boolean active) {
        return "-fx-background-color: " + (active ? "#7c6af7" : "#1e1e30")
                + "; -fx-text-fill: " + (active ? "white" : "#aaa")
                + "; -fx-background-radius: 14; -fx-font-size: 11; -fx-padding: 4 12 4 12;";
    }

    private void restyleChips(HBox chips) {
        for (Node n : chips.getChildren()) {
            if (n instanceof ToggleButton t) t.setStyle(chipStyle(t.getText().equals(activeCategory)));
        }
    }

    private void refreshModules() {
        moduleList.getChildren().clear();
        String q = search.getText() == null ? "" : search.getText().toLowerCase();
        int count = 0;
        for (ModuleRegistry.ClientModule m : ModuleRegistry.MODULES) {
            if (!activeCategory.equals("All") && !m.category().equals(activeCategory)) continue;
            if (!q.isEmpty() && !m.name().toLowerCase().contains(q) && !m.desc().toLowerCase().contains(q)) continue;
            moduleList.getChildren().add(moduleCard(m));
            count++;
        }
        if (count == 0) {
            Label none = new Label("No mods match.");
            none.setStyle("-fx-text-fill: #666; -fx-font-size: 11;");
            moduleList.getChildren().add(none);
        }
    }

    private HBox moduleCard(ModuleRegistry.ClientModule m) {
        HudConfig.ModuleConfig mc = config.module(m.id());
        boolean locked = m.requiresMod();
        boolean on = !locked && mc.enabled;

        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(10, 14, 10, 14));
        card.setStyle("-fx-background-color: " + (on ? "#1e1e3a" : "#16161e") + "; -fx-background-radius: 8;");

        VBox info = new VBox(3);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label name = new Label(m.name());
        name.setStyle("-fx-text-fill: " + (on ? "#e0e0ff" : "#888") + "; -fx-font-weight: bold; -fx-font-size: 12;");
        Label desc = new Label(m.desc());
        desc.setStyle("-fx-text-fill: #555; -fx-font-size: 10;");
        info.getChildren().addAll(name, desc);
        if (locked) {
            Label note = new Label("Changes the game itself — needs an in-game mod (not shipped yet).");
            note.setStyle("-fx-text-fill: #b8860b; -fx-font-size: 9;");
            info.getChildren().add(note);
        } else if (mc.keybind != null && !mc.keybind.isBlank()) {
            Label key = new Label("Key: " + mc.keybind);
            key.setStyle("-fx-text-fill: #7c6af7; -fx-font-size: 9;");
            info.getChildren().add(key);
        }

        Label catBadge = new Label(m.category());
        catBadge.setStyle("-fx-background-color: #7c6af722; -fx-text-fill: #7c6af7; -fx-background-radius: 4; -fx-font-size: 9; -fx-padding: 2 6 2 6;");

        Label status = new Label(locked ? "Needs in-game mod" : "Overlay");
        status.setStyle("-fx-background-color: " + (locked ? "#b8860b33" : "#4caf5033")
                + "; -fx-text-fill: " + (locked ? "#d5a83c" : "#4caf50")
                + "; -fx-background-radius: 4; -fx-font-size: 9; -fx-padding: 2 6 2 6;");

        ToggleButton toggle = new ToggleButton(on ? "ON" : (locked ? "SOON" : "OFF"));
        toggle.setSelected(on);
        toggle.setDisable(locked);
        toggle.setStyle("-fx-background-color: " + (on ? "#7c6af7" : "#333355")
                + "; -fx-text-fill: white; -fx-background-radius: 4; -fx-font-size: 11; -fx-min-width: 52;");
        toggle.setOnAction(e -> {
            HudConfig.ModuleConfig live = config.module(m.id());
            live.enabled = toggle.isSelected();
            toggle.setText(live.enabled ? "ON" : "OFF");
            toggle.setStyle("-fx-background-color: " + (live.enabled ? "#7c6af7" : "#333355")
                    + "; -fx-text-fill: white; -fx-background-radius: 4; -fx-font-size: 11; -fx-min-width: 52;");
            name.setStyle("-fx-text-fill: " + (live.enabled ? "#e0e0ff" : "#888") + "; -fx-font-weight: bold; -fx-font-size: 12;");
            card.setStyle("-fx-background-color: " + (live.enabled ? "#1e1e3a" : "#16161e")
                    + "; -fx-background-radius: 8;");
            onModulesChanged.run();
        });

        card.getChildren().addAll(info, catBadge, status, toggle);
        return card;
    }

    // ---------------- Keybinds ----------------

    private VBox buildKeybindsPage() {
        keybindList.getChildren().clear();
        keybindList.getChildren().add(menuKeyRow());
        keybindList.getChildren().add(new Separator());
        for (ModuleRegistry.ClientModule m : ModuleRegistry.MODULES) {
            if (m.requiresMod()) continue;
            keybindList.getChildren().add(keybindRow(m));
        }

        VBox page = new VBox(10);
        page.getChildren().addAll(pageTitle("Keybinds"),
                pageHint("Click a button, then press a key or mouse button. Esc cancels, Delete clears."),
                scroll(keybindList));
        return page;
    }

    private HBox menuKeyRow() {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 12, 10, 12));
        row.setStyle("-fx-background-color: #1e1e3a; -fx-background-radius: 8;");

        Label name = new Label("Open Mods Menu");
        name.setStyle("-fx-text-fill: #e0e0ff; -fx-font-weight: bold; -fx-font-size: 12;");
        Label note = new Label("Press this to open the bird nest");
        note.setStyle("-fx-text-fill: #555; -fx-font-size: 9;");
        VBox info = new VBox(2, name, note);
        HBox.setHgrow(info, Priority.ALWAYS);

        Button reset = smallBtn("Reset");
        reset.setOnAction(e -> {
            config.menuKey = Keys.VK_RSHIFT;
            onModulesChanged.run();
            showPage("Keybinds");
        });

        Button btn = keyButton(Keys.name(config.menuKey));
        btn.setOnAction(e -> {
            if (capturing) {
                showPage(activePage);
                return;
            }
            startCapture(btn, vk -> {
                if (vk > 0) config.menuKey = vk;
                onModulesChanged.run();
            });
        });

        row.getChildren().addAll(info, reset, btn);
        return row;
    }

    private HBox keybindRow(ModuleRegistry.ClientModule m) {
        HudConfig.ModuleConfig mc = config.module(m.id());
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 12, 8, 12));
        row.setStyle("-fx-background-color: #16161e; -fx-background-radius: 8;");

        Label name = new Label(m.name());
        name.setStyle("-fx-text-fill: #ccc; -fx-font-size: 12;");
        HBox.setHgrow(name, Priority.ALWAYS);

        String conflict = findConflict(m.id(), mc.keybind);
        if (conflict != null) {
            Label c = new Label("⚠ conflicts with " + conflict);
            c.setStyle("-fx-text-fill: #e05a5a; -fx-font-size: 9;");
            row.getChildren().add(c);
        }

        Button clear = smallBtn("✕");
        clear.setDisable(mc.keybind == null || mc.keybind.isBlank());
        clear.setOnAction(e -> {
            config.module(m.id()).keybind = "";
            onModulesChanged.run();
            showPage("Keybinds");
        });

        Button btn = keyButton(display(mc.keybind));
        btn.setOnAction(e -> {
            if (capturing) {
                showPage(activePage);
                return;
            }
            startCapture(btn, vk -> {
                config.module(m.id()).keybind = vk > 0 ? Keys.name(vk) : "";
                onModulesChanged.run();
            });
        });

        row.getChildren().addAll(name, clear, btn);
        return row;
    }

    private String findConflict(String selfId, String key) {
        if (key == null || key.isBlank()) return null;
        for (ModuleRegistry.ClientModule m : ModuleRegistry.MODULES) {
            if (m.requiresMod() || m.id().equals(selfId)) continue;
            HudConfig.ModuleConfig mc = config.module(m.id());
            if (key.equals(mc.keybind)) return m.name();
        }
        return null;
    }

    private Button keyButton(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: #333355; -fx-text-fill: white; -fx-background-radius: 4; -fx-font-size: 11; -fx-min-width: 130;");
        return b;
    }

    private String display(String key) {
        return key == null || key.isBlank() ? "None" : key;
    }

    // ---------------- Keybind capture ----------------

    private void startCapture(Button btn, Consumer<Integer> sink) {
        cancelCapture();
        capturing = true;
        captureButton = btn;
        captureSink = sink;
        btn.setText("Press a key or click...");
        btn.setStyle("-fx-background-color: #d5a83c; -fx-text-fill: #1a1a2e; -fx-background-radius: 4; -fx-font-size: 11; -fx-min-width: 130;");
    }

    private void finishCapture(int vk) {
        Consumer<Integer> sink = captureSink;
        cancelCapture();
        if (sink != null) sink.accept(vk);
        showPage(activePage);
    }

    private void cancelCapture() {
        capturing = false;
        captureSink = null;
        captureButton = null;
    }

    private void onCaptureKey(KeyEvent e) {
        if (!capturing) return;
        e.consume();
        if (e.getCode() == KeyCode.ESCAPE) {
            showPage(activePage);
            return;
        }
        if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
            finishCapture(0);
            return;
        }
        int vk = Keys.vkForFx(e.getCode());
        if (vk > 0) finishCapture(vk);
    }

    private void onCaptureMouse(MouseEvent e) {
        if (!capturing) return;
        if (e.getTarget() == dim) {
            showPage(activePage);
            return;
        }
        int vk = switch (e.getButton()) {
            case PRIMARY -> 0x01;
            case SECONDARY -> 0x02;
            case MIDDLE -> 0x04;
            default -> -1;
        };
        if (vk > 0) {
            finishCapture(vk);
            e.consume();
        }
    }

    // ---------------- Profiles ----------------

    private VBox buildProfilesPage() {
        Label subtitle = pageHint("Active profile: " + config.activeProfile);

        TextField nameField = new TextField();
        nameField.setPromptText("Profile name");
        nameField.setStyle("-fx-background-color: #1e1e30; -fx-text-fill: white; -fx-prompt-text-fill: #666; -fx-background-radius: 6; -fx-border-color: #333355; -fx-border-radius: 6;");
        HBox.setHgrow(nameField, Priority.ALWAYS);

        Button create = smallBtn("Create");
        create.setOnAction(e -> {
            String n = nameField.getText();
            if (n == null || n.isBlank()) return;
            config.createProfile(n);
            onModulesChanged.run();
            showPage("Profiles");
        });
        Button importBtn = smallBtn("Import...");
        importBtn.setOnAction(e -> importProfile());

        HBox createRow = new HBox(8, nameField, importBtn, create);

        profileList.getChildren().clear();
        for (String name : config.profiles.keySet()) profileList.getChildren().add(profileRow(name));

        VBox page = new VBox(10);
        page.getChildren().addAll(pageTitle("Profiles"),
                subtitle, createRow, scroll(profileList));
        return page;
    }

    private HBox profileRow(String name) {
        boolean active = name.equals(config.activeProfile);
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 12, 10, 12));
        row.setStyle("-fx-background-color: " + (active ? "#1e1e3a" : "#16161e") + "; -fx-background-radius: 8;");

        Label lbl = new Label(name);
        lbl.setStyle("-fx-text-fill: " + (active ? "#e0e0ff" : "#ccc")
                + "; -fx-font-weight: " + (active ? "bold" : "normal") + "; -fx-font-size: 12;");
        HBox.setHgrow(lbl, Priority.ALWAYS);

        if (active) {
            Label badge = new Label("● Active");
            badge.setStyle("-fx-background-color: #4caf5033; -fx-text-fill: #4caf50; -fx-background-radius: 4; -fx-font-size: 9; -fx-padding: 2 6 2 6;");
            row.getChildren().add(badge);
        } else {
            Button use = smallBtn("Use");
            use.setOnAction(e -> {
                config.applyProfile(name);
                onModulesChanged.run();
                showPage("Profiles");
            });
            row.getChildren().add(use);
        }

        Button dup = smallBtn("Duplicate");
        dup.setOnAction(e -> {
            config.duplicateProfile(name, name + " copy");
            onModulesChanged.run();
            showPage("Profiles");
        });
        Button rename = smallBtn("Rename");
        rename.setOnAction(e -> renameProfile(row, name));
        Button export = smallBtn("Export");
        export.setOnAction(e -> exportProfile(name));
        Button del = smallBtn("Delete");
        del.setStyle("-fx-background-color: #5a2e2e; -fx-text-fill: #e05a5a; -fx-background-radius: 4; -fx-font-size: 10; -fx-padding: 4 8 4 8;");
        del.setDisable(config.profiles.size() <= 1);
        del.setOnAction(e -> {
            config.deleteProfile(name);
            onModulesChanged.run();
            showPage("Profiles");
        });

        row.getChildren().addAll(lbl, dup, rename, export, del);
        return row;
    }

    private void renameProfile(HBox row, String oldName) {
        row.getChildren().clear();
        TextField tf = new TextField(oldName);
        tf.setStyle("-fx-background-color: #1e1e30; -fx-text-fill: white; -fx-background-radius: 6; -fx-border-color: #7c6af7; -fx-border-radius: 6;");
        HBox.setHgrow(tf, Priority.ALWAYS);
        Button cancel = smallBtn("✕");
        cancel.setOnAction(e -> showPage("Profiles"));
        Button ok = smallBtn("✓");
        ok.setOnAction(e -> {
            config.renameProfile(oldName, tf.getText());
            onModulesChanged.run();
            showPage("Profiles");
        });
        row.getChildren().addAll(tf, cancel, ok);
        tf.requestFocus();
    }

    private void exportProfile(String name) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Profile");
        fc.setInitialFileName(name + ".json");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Raven profile", "*.json"));
        File f = fc.showSaveDialog(getScene().getWindow());
        if (f == null) return;
        try {
            config.exportProfile(name, f.toPath());
        } catch (Exception ex) {
            System.out.println("[RAVEN] Export failed: " + ex.getMessage());
        }
    }

    private void importProfile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Import Profile");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Raven profile", "*.json"));
        File f = fc.showOpenDialog(getScene().getWindow());
        if (f == null) return;
        try {
            config.importProfile(f.toPath());
            onModulesChanged.run();
            showPage("Profiles");
        } catch (Exception ex) {
            System.out.println("[RAVEN] Import failed: " + ex.getMessage());
        }
    }

    // ---------------- Settings ----------------

    private VBox buildSettingsPage() {
        long realMods = ModuleRegistry.MODULES.stream().filter(m -> !m.requiresMod()).count();

        Label version = new Label("RavenClient 1.0.35 — bird nest");
        version.setStyle("-fx-text-fill: #ccc; -fx-font-size: 12;");
        Label counts = new Label(realMods + " working overlay mods · "
                + ModuleRegistry.MODULES.size() + " mods in catalog");
        counts.setStyle("-fx-text-fill: #888; -fx-font-size: 10;");
        Label active = new Label("Active profile: " + config.activeProfile
                + "   ·   Menu key: " + Keys.name(config.menuKey));
        active.setStyle("-fx-text-fill: #888; -fx-font-size: 10;");

        VBox info = new VBox(6, version, counts, active);
        info.setPadding(new Insets(14));
        info.setStyle("-fx-background-color: #1e1e3a; -fx-background-radius: 8;");

        Button reset = new Button("Reset Mods & Layout to Defaults");
        reset.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-background-radius: 6; -fx-font-size: 12; -fx-padding: 10 16 10 16; -fx-cursor: hand;");
        reset.setOnAction(e -> {
            config.resetToDefaults();
            onModulesChanged.run();
            showPage("Settings");
        });

        Button close = smallBtn("Close Menu");
        close.setOnAction(e -> onClose.run());

        VBox page = new VBox(12);
        page.getChildren().addAll(pageTitle("Settings"), info, reset, close);
        return page;
    }

    private Button smallBtn(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: #333355; -fx-text-fill: #ccc; -fx-background-radius: 4; -fx-font-size: 10; -fx-padding: 4 8 4 8;");
        return b;
    }
}
