package org.ravenclient.overlay.elements;

import javafx.scene.canvas.GraphicsContext;
import org.ravenclient.overlay.HudElement;

public class ModuleStatusElement extends HudElement {
    public ModuleStatusElement() { super("modules", "Module Status"); }

    @Override
    public void render(GraphicsContext gc, double sw, double sh, boolean editMode) {
        if (!cfg.visible && !editMode) return;
        double x = px(sw), y = py(sh), w = pw(sw), h = ph(sh);
        gc.save();
        gc.setGlobalAlpha(cfg.opacity);
        drawBackground(gc, x, y, w, h);
        String text = editMode ? "KillAura  ✓\nSpeed  ✓\nESP  ✗" : "KillAura  ✓\nSpeed  ✓\nESP  ✗";
        drawText(gc, text, x + 4, y + h - 4);
        gc.restore();
    }
}
