package org.ravenclient.overlay.elements;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.ravenclient.overlay.HudElement;

public class WatermarkElement extends HudElement {
    public WatermarkElement() { super("watermark", "Watermark"); }

    @Override
    public void render(GraphicsContext gc, double sw, double sh, boolean editMode) {
        if (!cfg.visible && !editMode) return;
        double x = px(sw), y = py(sh), w = pw(sw), h = ph(sh);
        gc.save();
        gc.setGlobalAlpha(cfg.opacity);
        drawBackground(gc, x, y, w, h);
        gc.setFont(Font.font("System", FontWeight.BOLD, cfg.fontSize + 2));
        if (cfg.shadow) {
            gc.setFill(Color.color(0, 0, 0, 0.6));
            gc.fillText("RavenClient", x + 5, y + h - 5);
        }
        gc.setFill(Color.web("#7c6af7"));
        gc.fillText("Raven", x + 4, y + h - 6);
        gc.setFill(elementColor());
        double ravenWidth = 38;
        gc.fillText("Client", x + 4 + ravenWidth, y + h - 6);
        gc.restore();
    }
}
