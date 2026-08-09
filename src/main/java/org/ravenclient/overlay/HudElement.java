package org.ravenclient.overlay;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Base class for all draggable HUD elements.
 * Positions are stored as normalized [0..1] fractions of screen size.
 */
public abstract class HudElement {

    public final String id;
    public final String label;
    protected HudConfig.ElementConfig cfg;

    // Runtime state (not persisted)
    public boolean selected = false;
    public boolean hovered = false;

    protected HudElement(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public void bind(HudConfig config) {
        this.cfg = config.element(id);
    }

    /** Pixel bounds at the given screen size. */
    public double px(double sw) { return cfg.x * sw; }
    public double py(double sh) { return cfg.y * sh; }
    public double pw(double sw) { return cfg.width * sw; }
    public double ph(double sh) { return cfg.height * sh; }

    public boolean contains(double mx, double my, double sw, double sh) {
        return mx >= px(sw) && mx <= px(sw) + pw(sw) && my >= py(sh) && my <= py(sh) + ph(sh);
    }

    /** Move by normalized delta. */
    public void move(double dnx, double dny) {
        cfg.x = Math.max(0, Math.min(1 - cfg.width, cfg.x + dnx));
        cfg.y = Math.max(0, Math.min(1 - cfg.height, cfg.y + dny));
    }

    public void setPos(double nx, double ny) {
        cfg.x = Math.max(0, Math.min(1 - cfg.width, nx));
        cfg.y = Math.max(0, Math.min(1 - cfg.height, ny));
    }

    /** Render the element. Called every frame. */
    public abstract void render(GraphicsContext gc, double sw, double sh, boolean editMode);

    /** Render the editor outline/handles when selected. */
    public void renderEditOverlay(GraphicsContext gc, double sw, double sh) {
        double x = px(sw), y = py(sh), w = pw(sw), h = ph(sh);
        gc.save();
        gc.setGlobalAlpha(1.0);
        if (selected) {
            gc.setStroke(Color.web("#7c6af7"));
            gc.setLineWidth(2);
            gc.strokeRect(x, y, w, h);
            double hs = 6;
            gc.setFill(Color.web("#7c6af7"));
            gc.fillRect(x - hs / 2, y - hs / 2, hs, hs);
            gc.fillRect(x + w - hs / 2, y - hs / 2, hs, hs);
            gc.fillRect(x - hs / 2, y + h - hs / 2, hs, hs);
            gc.fillRect(x + w - hs / 2, y + h - hs / 2, hs, hs);
            double bs = 8;
            gc.setFill(Color.web("#ffffff"));
            gc.fillRect(x + w - bs / 2, y + h - bs / 2, bs, bs);
            gc.setStroke(Color.web("#7c6af7"));
            gc.setLineWidth(1);
            gc.strokeRect(x + w - bs / 2, y + h - bs / 2, bs, bs);
            gc.setFont(Font.font("System", FontWeight.BOLD, 10));
            gc.setFill(Color.web("#7c6af7"));
            gc.fillText(label, x + 2, y - 4);
        } else if (hovered) {
            gc.setStroke(Color.web("#ffffff44"));
            gc.setLineWidth(1);
            gc.strokeRect(x, y, w, h);
            gc.setFont(Font.font("System", 10));
            gc.setFill(Color.web("#ffffff88"));
            gc.fillText(label, x + 2, y - 4);
        }
        gc.restore();
    }

    protected void drawBackground(GraphicsContext gc, double x, double y, double w, double h) {
        if (!cfg.background) return;
        int bg = cfg.bgColor;
        double a = ((bg >> 24) & 0xFF) / 255.0;
        double r = ((bg >> 16) & 0xFF) / 255.0;
        double g = ((bg >> 8) & 0xFF) / 255.0;
        double b = (bg & 0xFF) / 255.0;
        gc.setFill(new Color(r, g, b, a));
        if (cfg.rounded) gc.fillRoundRect(x, y, w, h, 6, 6);
        else gc.fillRect(x, y, w, h);
    }

    protected Color elementColor() {
        int c = cfg.color;
        return Color.rgb((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, ((c >> 24) & 0xFF) / 255.0);
    }

    protected void drawText(GraphicsContext gc, String text, double x, double y) {
        gc.setFont(Font.font("Monospaced", cfg.fontSize));
        if (cfg.shadow) {
            gc.setFill(Color.color(0, 0, 0, 0.6));
            gc.fillText(text, x + 1, y + 1);
        }
        gc.setFill(elementColor());
        gc.fillText(text, x, y);
    }
}
