package org.ravenclient.overlay.elements;

import javafx.scene.canvas.GraphicsContext;
import org.ravenclient.overlay.HudElement;
import org.ravenclient.overlay.GameDataBridge;

public class ComboElement extends HudElement {
    public ComboElement() { super("combo", "Combo Counter"); }

    @Override
    public void render(GraphicsContext gc, double sw, double sh, boolean editMode) {
        if (!cfg.visible && !editMode) return;
        double x = px(sw), y = py(sh), w = pw(sw), h = ph(sh);
        gc.save();
        gc.setGlobalAlpha(cfg.opacity);
        drawBackground(gc, x, y, w, h);
        int combo = editMode ? 7 : GameDataBridge.getInstance().getCombo();
        drawText(gc, "Combo: " + combo, x + 4, y + h - 4);
        gc.restore();
    }
}
