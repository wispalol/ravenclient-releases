package org.ravenclient.overlay.elements;

import javafx.scene.canvas.GraphicsContext;
import org.ravenclient.overlay.GameDataBridge;
import org.ravenclient.overlay.HudElement;

public class HitCounterElement extends HudElement {
    public HitCounterElement() { super("hits", "Hit Counter"); }

    @Override
    public void render(GraphicsContext gc, double sw, double sh, boolean editMode) {
        if (!cfg.visible && !editMode) return;
        double x = px(sw), y = py(sh), w = pw(sw), h = ph(sh);
        gc.save();
        gc.setGlobalAlpha(cfg.opacity);
        drawBackground(gc, x, y, w, h);
        GameDataBridge db = GameDataBridge.getInstance();
        String text = editMode ? "Hits: 42" : "Hits: " + db.getHits();
        drawText(gc, text, x + 4, y + h - 4);
        gc.restore();
    }
}
