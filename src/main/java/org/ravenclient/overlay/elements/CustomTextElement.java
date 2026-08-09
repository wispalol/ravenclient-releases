package org.ravenclient.overlay.elements;

import javafx.scene.canvas.GraphicsContext;
import org.ravenclient.overlay.HudElement;

public class CustomTextElement extends HudElement {
    public String text = "Custom Text";

    public CustomTextElement() { super("customtext", "Custom Text"); }

    @Override
    public void render(GraphicsContext gc, double sw, double sh, boolean editMode) {
        if (!cfg.visible && !editMode) return;
        double x = px(sw), y = py(sh), w = pw(sw), h = ph(sh);
        gc.save();
        gc.setGlobalAlpha(cfg.opacity);
        drawBackground(gc, x, y, w, h);
        drawText(gc, text, x + 4, y + h - 4);
        gc.restore();
    }
}
