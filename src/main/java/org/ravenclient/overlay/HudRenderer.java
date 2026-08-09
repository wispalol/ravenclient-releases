package org.ravenclient.overlay;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;

import java.util.List;

public class HudRenderer extends AnimationTimer {

    private final Canvas canvas;
    private final List<HudElement> elements;
    private boolean editMode = false;

    public HudRenderer(Canvas canvas, List<HudElement> elements) {
        this.canvas = canvas;
        this.elements = elements;
    }

    public void setEditMode(boolean editMode) { this.editMode = editMode; }

    @Override
    public void handle(long now) {
        double sw = canvas.getWidth();
        double sh = canvas.getHeight();
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, sw, sh);

        for (HudElement el : elements) {
            if (el.cfg == null) continue;
            el.render(gc, sw, sh, editMode);
        }

        if (editMode) {
            for (HudElement el : elements) {
                if (el.cfg == null) continue;
                el.renderEditOverlay(gc, sw, sh);
            }
        }
    }
}
