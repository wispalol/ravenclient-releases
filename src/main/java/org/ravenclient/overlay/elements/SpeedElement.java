package org.ravenclient.overlay.elements;

import javafx.scene.canvas.GraphicsContext;
import org.ravenclient.overlay.HudElement;
import org.ravenclient.overlay.GameDataBridge;

public class SpeedElement extends HudElement {
    public SpeedElement() { super("speed", "Speed"); }

    @Override
    public void render(GraphicsContext gc, double sw, double sh, boolean editMode) {
        if (!cfg.visible && !editMode) return;
        double x = px(sw), y = py(sh), w = pw(sw), h = ph(sh);
        gc.save();
        gc.setGlobalAlpha(cfg.opacity);
        drawBackground(gc, x, y, w, h);
        String text = editMode ? "Speed: 5.6 b/s" : "Speed: " + GameDataBridge.getInstance().getSpeed();
        drawText(gc, text, x + 4, y + h - 4);
        gc.restore();
    }
}
