package org.ravenclient.overlay.elements;

import javafx.scene.canvas.GraphicsContext;
import org.ravenclient.overlay.GameDataBridge;
import org.ravenclient.overlay.HudElement;

public class ArmorStatusElement extends HudElement {
    public ArmorStatusElement() { super("armor", "Armor Status"); }

    @Override
    public void render(GraphicsContext gc, double sw, double sh, boolean editMode) {
        if (!cfg.visible && !editMode) return;
        double x = px(sw), y = py(sh), w = pw(sw), h = ph(sh);
        gc.save();
        gc.setGlobalAlpha(cfg.opacity);
        drawBackground(gc, x, y, w, h);
        GameDataBridge db = GameDataBridge.getInstance();
        String text = editMode ? "Diamond Armor  ·  80%" : String.format("Armor: %.0f%%", db.getArmorPercent());
        drawText(gc, text, x + 4, y + h - 4);
        gc.restore();
    }
}
