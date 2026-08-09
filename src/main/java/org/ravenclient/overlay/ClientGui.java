package org.ravenclient.overlay;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClientGui extends StackPane {

    public record Module(String id, String name, String desc, String category) {}

    private static final List<Module> MODULES = List.of(
        new Module("killaura", "KillAura", "Auto-attack nearby entities", "Combat"),
        new Module("reach", "Reach", "Extended attack reach", "Combat"),
        new Module("velocity", "Velocity", "Reduce knockback taken", "Combat"),
        new Module("criticals", "Criticals", "Always land critical hits", "Combat"),
        new Module("sprint", "Sprint", "Auto sprint", "Movement"),
        new Module("speed", "Speed", "Move faster", "Movement"),
        new Module("nofall", "NoFall", "Prevent fall damage", "Movement"),
        new Module("fly", "Fly", "Creative-style flight", "Movement"),
        new Module("norotate", "NoRotate", "Prevent server rotations", "Player"),
        new Module("antiafk", "AntiAFK", "Prevent AFK kick", "Player"),
        new Module("fullbright", "FullBright", "Maximum brightness", "Render"),
        new Module("esp", "ESP", "Entity outlines through walls", "Render"),
        new Module("tracers", "Tracers", "Lines to nearby entities", "Render"),
        new Module("chams", "Chams", "Colored entity models", "Render"),
        new Module("xray", "X-Ray", "See ores through blocks", "World"),
        new Module("nuker", "Nuker", "Break blocks around you", "World"),
        new Module("scaffold", "Scaffold", "Auto-place blocks below", "World"),
        new Module("autofish", "AutoFish", "Auto-reel fishing rod", "Misc"),
        new Module("autoclicker", "AutoClicker", "Auto left/right click", "Misc"),
        new Module("hud", "HUD", "In-game overlay elements", "HUD"),
        new Module("fps", "FPS Display", "Show FPS counter", "HUD"),
        new Module("coords", "Coordinates", "Show XYZ position", "HUD"),
        new Module("keystrokes", "Keystrokes", "Show WASD keystrokes", "HUD"),
        new Module("cps", "CPS Counter", "Show clicks per second", "HUD"),
        new Module("ping", "Ping", "Show server ping", "HUD"),
        new Module("targethud", "Target HUD", "Show target health bar", "HUD"),
        new Module("watermark", "Watermark", "Show client watermark", "HUD")
    );

    private static final List<String> CATEGORIES = List.of(
            "All", "Combat", "Movement", "Player", "Render", "World", "Misc", "HUD");

    private final HudConfig config;
    private final Runnable onEditHud;
    private final Runnable onClose;
    private String activeCategory = "All";
    private String searchQuery = "";
    private VBox moduleList;

    public ClientGui(HudConfig config, Runnable onEditHud, Runnable onClose) {
        this.config = config;
        this.onEditHud = onEditHud;
        this.onClose = onClose;
        build();
    }

    private void build() {
        // Dim overlay
        Region dim = new Region();
        dim.setStyle("-fx-background-color: #00000088;");
        dim.setOnMouseClicked(e -> onClose.run());

        // Main panel
        HBox panel = new HBox(0);
        panel.setMaxWidth(820);
        panel.setMaxHeight(560);
        panel.setStyle("-fx-background-color: #12121eee; -fx-background-radius: 12; -fx-effect: dropshadow(gaussian, #00000099, 32, 0, 0, 8);");
        panel.setOnMouseClicked(e -> e.consume());

        panel.getChildren().addAll(buildSidebar(), buildContent());

        StackPane.setAlignment(panel, Pos.CENTER);

        FadeTransition ft = new FadeTransition(Duration.millis(180), panel);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();

        getChildren().addAll(dim, panel);
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(4);
        sidebar.setPrefWidth(160);
        sidebar.setPadding(new Insets(20, 8, 20, 8));
        sidebar.setStyle("-fx-background-color: #0e0e1a; -fx-background-radius: 12 0 0 12;");

        Label logo = new Label("RAVEN");
        logo.setStyle("-fx-text-fill: #7c6af7; -fx-font-weight: bold; -fx-font-size: 18;");
        logo.setPadding(new Insets(0, 0, 16, 8));
        sidebar.getChildren().add(logo);

        for (String cat : CATEGORIES) {
            Button btn = new Button(cat);
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setAlignment(Pos.CENTER_LEFT);
            btn.setPadding(new Insets(8, 12, 8, 12));
            boolean active = cat.equals(activeCategory);
            btn.setStyle("-fx-background-color: " + (active ? "#7c6af733" : "transparent")
                    + "; -fx-text-fill: " + (active ? "#7c6af7" : "#aaa")
                    + "; -fx-background-radius: 6; -fx-font-size: 12; -fx-cursor: hand;");
            btn.setOnAction(e -> {
                activeCategory = cat;
                refreshModules();
                // Re-style all buttons
                sidebar.getChildren().stream()
                        .filter(n -> n instanceof Button)
                        .forEach(n -> {
                            Button b = (Button) n;
                            boolean sel = b.getText().equals(activeCategory);
                            b.setStyle("-fx-background-color: " + (sel ? "#7c6af733" : "transparent")
                                    + "; -fx-text-fill: " + (sel ? "#7c6af7" : "#aaa")
                                    + "; -fx-background-radius: 6; -fx-font-size: 12; -fx-cursor: hand;");
                        });
            });
            sidebar.getChildren().add(btn);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button editHud = new Button("✦  Edit HUD");
        editHud.setMaxWidth(Double.MAX_VALUE);
        editHud.setStyle("-fx-background-color: #7c6af7; -fx-text-fill: white; -fx-background-radius: 6; -fx-font-size: 12; -fx-cursor: hand;");
        editHud.setOnAction(e -> { onEditHud.run(); onClose.run(); });

        Button close = new Button("✕  Close");
        close.setMaxWidth(Double.MAX_VALUE);
        close.setStyle("-fx-background-color: #333355; -fx-text-fill: #ccc; -fx-background-radius: 6; -fx-font-size: 12; -fx-cursor: hand;");
        close.setOnAction(e -> onClose.run());

        sidebar.getChildren().addAll(spacer, editHud, close);
        return sidebar;
    }

    private VBox buildContent() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        HBox.setHgrow(content, Priority.ALWAYS);

        TextField search = new TextField();
        search.setPromptText("Search modules...");
        search.setStyle("-fx-background-color: #1e1e30; -fx-text-fill: white; -fx-prompt-text-fill: #666; -fx-background-radius: 6; -fx-border-color: #333355; -fx-border-radius: 6;");
        search.textProperty().addListener((o, a, b) -> { searchQuery = b.toLowerCase(); refreshModules(); });

        moduleList = new VBox(8);
        ScrollPane scroll = new ScrollPane(moduleList);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        content.getChildren().addAll(search, scroll);
        refreshModules();
        return content;
    }

    private void refreshModules() {
        moduleList.getChildren().clear();
        for (Module m : MODULES) {
            if (!activeCategory.equals("All") && !m.category().equals(activeCategory)) continue;
            if (!searchQuery.isEmpty() && !m.name().toLowerCase().contains(searchQuery)
                    && !m.desc().toLowerCase().contains(searchQuery)) continue;
            moduleList.getChildren().add(moduleCard(m));
        }
    }

    private HBox moduleCard(Module m) {
        HudConfig.ModuleConfig mc = config.module(m.id());

        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(10, 14, 10, 14));
        card.setStyle("-fx-background-color: " + (mc.enabled ? "#1e1e3a" : "#16161e")
                + "; -fx-background-radius: 8; -fx-cursor: hand;");

        VBox info = new VBox(2);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label name = new Label(m.name());
        name.setStyle("-fx-text-fill: " + (mc.enabled ? "#e0e0ff" : "#888") + "; -fx-font-weight: bold; -fx-font-size: 12;");
        Label desc = new Label(m.desc());
        desc.setStyle("-fx-text-fill: #555; -fx-font-size: 10;");
        info.getChildren().addAll(name, desc);

        Label catBadge = new Label(m.category());
        catBadge.setStyle("-fx-background-color: #7c6af722; -fx-text-fill: #7c6af7; -fx-background-radius: 4; -fx-font-size: 9; -fx-padding: 2 6 2 6;");

        ToggleButton toggle = new ToggleButton(mc.enabled ? "ON" : "OFF");
        toggle.setSelected(mc.enabled);
        toggle.setStyle("-fx-background-color: " + (mc.enabled ? "#7c6af7" : "#333355")
                + "; -fx-text-fill: white; -fx-background-radius: 4; -fx-font-size: 11; -fx-min-width: 44;");
        toggle.setOnAction(e -> {
            mc.enabled = toggle.isSelected();
            toggle.setText(mc.enabled ? "ON" : "OFF");
            toggle.setStyle("-fx-background-color: " + (mc.enabled ? "#7c6af7" : "#333355")
                    + "; -fx-text-fill: white; -fx-background-radius: 4; -fx-font-size: 11; -fx-min-width: 44;");
            name.setStyle("-fx-text-fill: " + (mc.enabled ? "#e0e0ff" : "#888") + "; -fx-font-weight: bold; -fx-font-size: 12;");
            card.setStyle("-fx-background-color: " + (mc.enabled ? "#1e1e3a" : "#16161e")
                    + "; -fx-background-radius: 8; -fx-cursor: hand;");
            saveConfig();
        });

        card.getChildren().addAll(info, catBadge, toggle);
        card.setOnMouseClicked(e -> { if (e.getTarget() != toggle) toggle.fire(); });
        return card;
    }

    private void saveConfig() {
        try { config.save(OverlayManager.configDir()); } catch (Exception ignored) {}
    }
}
