package org.ravenclient.overlay.elements;

import javafx.scene.canvas.GraphicsContext;
import org.ravenclient.overlay.GameDataBridge;
import org.ravenclient.overlay.HudElement;

public class ItemDurabilityElement extends HudElement {
    public ItemDurabilityElement() { super("durability", "Item Durability"); }

    @Override
    public void render(GraphicsContext gc, double sw, double sh, boolean editMode) {
        if (!cfg.visible && !editMode) return;
        double x = px(sw), y = py(sh), w = pw(sw), h = ph(sh);
        gc.save();
        gc.setGlobalAlpha(cfg.opacity);
        drawBackground(gc, x, y, w, h);
        GameDataBridge db = GameDataBridge.getInstance();
        String text = editMode ? "Diamond Sword  ·  1562/1562"
                : String.format("%s  ·  %d/%d", db.getHeldItem(), db.getDurability(), db.getMaxDurability());
        drawText(gc, text, x + 4, y + h - 4);
        gc.restore();
    }
}
