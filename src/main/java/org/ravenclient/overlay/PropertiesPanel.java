package org.ravenclient.overlay;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

public class PropertiesPanel extends VBox {

    private HudElement current;
    private Runnable onChanged;

    public PropertiesPanel() {
        setSpacing(8);
        setPadding(new Insets(12));
        setPrefWidth(220);
        setStyle("-fx-background-color: #1a1a2eee; -fx-background-radius: 8;");
        Label empty = new Label("Select an element");
        empty.setStyle("-fx-text-fill: #888; -fx-font-size: 12;");
        getChildren().add(empty);
    }

    public void setOnChanged(Runnable r) { this.onChanged = r; }

    public void show(HudElement el) {
        this.current = el;
        getChildren().clear();

        Label title = new Label(el.label);
        title.setStyle("-fx-text-fill: #7c6af7; -fx-font-weight: bold; -fx-font-size: 13;");
        getChildren().add(title);
        getChildren().add(separator());

        addToggle("Visible", el.cfg.visible, v -> { el.cfg.visible = v; changed(); });
        addToggle("Background", el.cfg.background, v -> { el.cfg.background = v; changed(); });
        addToggle("Shadow", el.cfg.shadow, v -> { el.cfg.shadow = v; changed(); });
        addToggle("Rounded", el.cfg.rounded, v -> { el.cfg.rounded = v; changed(); });

        addSlider("Opacity", el.cfg.opacity, 0, 1, v -> { el.cfg.opacity = v; changed(); });
        addSlider("Scale", el.cfg.scale, 0.5, 3.0, v -> { el.cfg.scale = v; changed(); });
        addSlider("Font Size", el.cfg.fontSize, 8, 24, v -> { el.cfg.fontSize = (int) Math.round(v); changed(); });
        addSlider("Width", el.cfg.width, 0.02, 0.5, v -> { el.cfg.width = v; changed(); });
        addSlider("Height", el.cfg.height, 0.01, 0.2, v -> { el.cfg.height = v; changed(); });

        addColorPicker("Text Color", intToColor(el.cfg.color), c -> { el.cfg.color = colorToInt(c); changed(); });
        addColorPicker("BG Color", intToColor(el.cfg.bgColor), c -> { el.cfg.bgColor = colorToInt(c); changed(); });

        addSlider("X (normalized)", el.cfg.x, 0, 1, v -> { el.cfg.x = v; changed(); });
        addSlider("Y (normalized)", el.cfg.y, 0, 1, v -> { el.cfg.y = v; changed(); });

        Button reset = new Button("Reset Position");
        reset.setStyle("-fx-background-color: #333355; -fx-text-fill: white; -fx-background-radius: 4;");
        reset.setMaxWidth(Double.MAX_VALUE);
        reset.setOnAction(e -> { el.cfg.x = 0.01; el.cfg.y = 0.01; changed(); });
        getChildren().add(reset);
    }

    public void clear() {
        current = null;
        getChildren().clear();
        Label empty = new Label("Select an element");
        empty.setStyle("-fx-text-fill: #888; -fx-font-size: 12;");
        getChildren().add(empty);
    }

    private void addToggle(String label, boolean value, java.util.function.Consumer<Boolean> onChange) {
        HBox row = new HBox(8);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #ccc; -fx-font-size: 11;");
        lbl.setPrefWidth(90);
        CheckBox cb = new CheckBox();
        cb.setSelected(value);
        cb.setStyle("-fx-text-fill: white;");
        cb.setOnAction(e -> onChange.accept(cb.isSelected()));
        row.getChildren().addAll(lbl, cb);
        getChildren().add(row);
    }

    private void addSlider(String label, double value, double min, double max, java.util.function.Consumer<Double> onChange) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #ccc; -fx-font-size: 11;");
        Slider slider = new Slider(min, max, value);
        slider.setMaxWidth(Double.MAX_VALUE);
        slider.setStyle("-fx-control-inner-background: #333355;");
        Label val = new Label(String.format("%.2f", value));
        val.setStyle("-fx-text-fill: #aaa; -fx-font-size: 10;");
        slider.valueProperty().addListener((o, a, b) -> {
            val.setText(String.format("%.2f", b.doubleValue()));
            onChange.accept(b.doubleValue());
        });
        HBox row = new HBox(6, slider, val);
        HBox.setHgrow(slider, Priority.ALWAYS);
        getChildren().addAll(lbl, row);
    }

    private void addColorPicker(String label, Color value, java.util.function.Consumer<Color> onChange) {
        HBox row = new HBox(8);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #ccc; -fx-font-size: 11;");
        lbl.setPrefWidth(90);
        ColorPicker cp = new ColorPicker(value);
        cp.setPrefWidth(110);
        cp.setOnAction(e -> onChange.accept(cp.getValue()));
        row.getChildren().addAll(lbl, cp);
        getChildren().add(row);
    }

    private Separator separator() {
        Separator s = new Separator();
        s.setStyle("-fx-background-color: #333355;");
        return s;
    }

    private void changed() { if (onChanged != null) onChanged.run(); }

    private static Color intToColor(int c) {
        return Color.rgb((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, ((c >> 24) & 0xFF) / 255.0);
    }

    private static int colorToInt(Color c) {
        return ((int)(c.getOpacity() * 255) << 24)
                | ((int)(c.getRed() * 255) << 16)
                | ((int)(c.getGreen() * 255) << 8)
                | (int)(c.getBlue() * 255);
    }
}
