package org.ravenclient.overlay.elements;

import javafx.scene.canvas.GraphicsContext;
import org.ravenclient.overlay.HudElement;

import java.lang.management.ManagementFactory;

public class FpsElement extends HudElement {
    private int fps = 0;
    private long lastTime = System.nanoTime();
    private int frames = 0;

    public FpsElement() { super("fps", "FPS"); }

    @Override
    public void render(GraphicsContext gc, double sw, double sh, boolean editMode) {
        if (!cfg.visible && !editMode) return;
        frames++;
        long now = System.nanoTime();
        if (now - lastTime >= 1_000_000_000L) {
            fps = frames;
            frames = 0;
            lastTime = now;
        }
        double x = px(sw), y = py(sh), w = pw(sw), h = ph(sh);
        gc.save();
        gc.setGlobalAlpha(cfg.opacity);
        drawBackground(gc, x, y, w, h);
        drawText(gc, "FPS: " + (editMode ? "120" : fps), x + 4, y + h - 4);
        gc.restore();
    }
}
