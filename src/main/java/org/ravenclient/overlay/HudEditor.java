package org.ravenclient.overlay;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.List;

public class HudEditor extends StackPane {

    private static final double SNAP_THRESHOLD = 0.015; // normalized
    private static final double GRID = 0.02;

    private final List<HudElement> elements;
    private final HudConfig config;
    private final Canvas overlayCanvas;
    private final PropertiesPanel propsPanel;
    private final Runnable onSave;
    private final Runnable onClose;

    private HudElement dragging = null;
    private HudElement resizing = null;
    private HudElement selected = null;
    private double dragOffsetX, dragOffsetY;
    private double resizeStartW, resizeStartH, resizeStartX, resizeStartY;
    private boolean snapEnabled = true;

    public HudEditor(List<HudElement> elements, HudConfig config, Canvas hudCanvas, Runnable onSave, Runnable onClose) {
        this.elements = elements;
        this.config = config;
        this.onSave = onSave;
        this.onClose = onClose;

        // Dim background
        Region dim = new Region();
        dim.setStyle("-fx-background-color: #00000066;");
        dim.setMouseTransparent(true);

        overlayCanvas = new Canvas();
        overlayCanvas.widthProperty().bind(widthProperty());
        overlayCanvas.heightProperty().bind(heightProperty());
        overlayCanvas.setMouseTransparent(true);

        propsPanel = new PropertiesPanel();
        propsPanel.setOnChanged(this::saveConfig);
        StackPane.setAlignment(propsPanel, Pos.CENTER_RIGHT);
        StackPane.setMargin(propsPanel, new Insets(0, 16, 0, 0));

        VBox toolbar = buildToolbar();
        StackPane.setAlignment(toolbar, Pos.TOP_CENTER);
        StackPane.setMargin(toolbar, new Insets(16, 0, 0, 0));

        getChildren().addAll(dim, overlayCanvas, toolbar, propsPanel);

        setOnMousePressed(this::onMousePressed);
        setOnMouseDragged(this::onMouseDragged);
        setOnMouseReleased(this::onMouseReleased);
        setOnMouseMoved(this::onMouseMoved);
    }

    private VBox buildToolbar() {
        Label title = new Label("✦  Edit HUD  —  drag elements to reposition");
        title.setStyle("-fx-text-fill: #ccc; -fx-font-size: 12;");

        Button snapBtn = new Button("Snap: ON");
        snapBtn.setStyle("-fx-background-color: #7c6af7; -fx-text-fill: white; -fx-background-radius: 4; -fx-font-size: 11;");
        snapBtn.setOnAction(e -> {
            snapEnabled = !snapEnabled;
            snapBtn.setText("Snap: " + (snapEnabled ? "ON" : "OFF"));
            snapBtn.setStyle("-fx-background-color: " + (snapEnabled ? "#7c6af7" : "#444466")
                    + "; -fx-text-fill: white; -fx-background-radius: 4; -fx-font-size: 11;");
        });

        Button resetAll = new Button("Reset All");
        resetAll.setStyle("-fx-background-color: #333355; -fx-text-fill: #ccc; -fx-background-radius: 4; -fx-font-size: 11;");
        resetAll.setOnAction(e -> resetAllPositions());

        Button done = new Button("✓  Done");
        done.setStyle("-fx-background-color: #4caf50; -fx-text-fill: white; -fx-background-radius: 4; -fx-font-size: 11;");
        done.setOnAction(e -> { saveConfig(); onClose.run(); });

        HBox bar = new HBox(12, title, snapBtn, resetAll, done);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(10, 20, 10, 20));
        bar.setStyle("-fx-background-color: #1a1a2eee; -fx-background-radius: 8;");
        bar.setMaxWidth(Region.USE_PREF_SIZE);

        VBox wrap = new VBox(bar);
        wrap.setAlignment(Pos.TOP_CENTER);
        return wrap;
    }

    private void onMousePressed(MouseEvent e) {
        double sw = getWidth(), sh = getHeight();
        selected = null;
        dragging = null;
        resizing = null;

        for (int i = elements.size() - 1; i >= 0; i--) {
            HudElement el = elements.get(i);
            double x = el.px(sw), y = el.py(sh), w = el.pw(sw), h = el.ph(sh);

            if (isResizeHandle(e.getX(), e.getY(), x, y, w, h)) {
                selected = el;
                resizing = el;
                resizeStartW = el.cfg.width;
                resizeStartH = el.cfg.height;
                resizeStartX = e.getX();
                resizeStartY = e.getY();
                break;
            } else if (el.contains(e.getX(), e.getY(), sw, sh)) {
                selected = el;
                dragging = el;
                dragOffsetX = e.getX() - x;
                dragOffsetY = e.getY() - y;
                break;
            }
        }
        elements.forEach(el -> el.selected = el == selected);
        if (selected != null) propsPanel.show(selected);
        else propsPanel.clear();
    }

    private boolean isResizeHandle(double mx, double my, double x, double y, double w, double h) {
        double hs = 8;
        double right = x + w;
        double bottom = y + h;
        return mx >= right - hs && mx <= right + hs && my >= bottom - hs && my <= bottom + hs;
    }

    private void onMouseDragged(MouseEvent e) {
        double sw = getWidth(), sh = getHeight();

        if (resizing != null) {
            double dx = (e.getX() - resizeStartX) / sw;
            double dy = (e.getY() - resizeStartY) / sh;
            double newW = Math.max(0.02, Math.min(0.5, resizeStartW + dx));
            double newH = Math.max(0.01, Math.min(0.2, resizeStartH + dy));
            resizing.cfg.width = newW;
            resizing.cfg.height = newH;
            return;
        }

        if (dragging == null) return;
        double nx = (e.getX() - dragOffsetX) / sw;
        double ny = (e.getY() - dragOffsetY) / sh;
        if (snapEnabled) {
            nx = snap(nx, dragging.cfg.width);
            ny = snap(ny, dragging.cfg.height);
        }
        dragging.setPos(nx, ny);
        drawAlignmentGuides(sw, sh);
    }

    private void onMouseReleased(MouseEvent e) {
        dragging = null;
        resizing = null;
        clearGuides();
        saveConfig();
    }

    private void onMouseMoved(MouseEvent e) {
        double sw = getWidth(), sh = getHeight();
        elements.forEach(el -> el.hovered = el.contains(e.getX(), e.getY(), sw, sh));
    }

    private double snap(double pos, double size) {
        // Snap to edges and center
        double[] snaps = {0, 0.5 - size / 2, 1 - size};
        for (double s : snaps) {
            if (Math.abs(pos - s) < SNAP_THRESHOLD) return s;
        }
        // Grid snap
        return Math.round(pos / GRID) * GRID;
    }

    private void drawAlignmentGuides(double sw, double sh) {
        if (dragging == null) return;
        GraphicsContext gc = overlayCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, sw, sh);
        gc.setStroke(Color.web("#7c6af766"));
        gc.setLineWidth(1);
        gc.setLineDashes(4, 4);
        // Center guides
        gc.strokeLine(sw / 2, 0, sw / 2, sh);
        gc.strokeLine(0, sh / 2, sw, sh / 2);
        gc.setLineDashes();
    }

    private void clearGuides() {
        GraphicsContext gc = overlayCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, overlayCanvas.getWidth(), overlayCanvas.getHeight());
    }

    private void resetAllPositions() {
        double startX = 0.01;
        double y = 0.01;
        for (HudElement el : elements) {
            el.cfg.x = startX;
            el.cfg.y = y;
            y += el.cfg.height + 0.005;
        }
        saveConfig();
    }

    private void saveConfig() {
        try {
            config.save(OverlayManager.configDir());
        } catch (Exception ignored) {}
    }
}
