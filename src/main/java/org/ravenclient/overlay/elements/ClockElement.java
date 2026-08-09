package org.ravenclient.overlay.elements;

import javafx.scene.canvas.GraphicsContext;
import org.ravenclient.overlay.HudElement;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ClockElement extends HudElement {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public ClockElement() { super("clock", "Clock"); }

    @Override
    public void render(GraphicsContext gc, double sw, double sh, boolean editMode) {
        if (!cfg.visible && !editMode) return;
        double x = px(sw), y = py(sh), w = pw(sw), h = ph(sh);
        gc.save();
        gc.setGlobalAlpha(cfg.opacity);
        drawBackground(gc, x, y, w, h);
        drawText(gc, LocalTime.now().format(FMT), x + 4, y + h - 4);
        gc.restore();
    }
}
