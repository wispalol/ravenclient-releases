package org.ravenclient.overlay.elements;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import org.ravenclient.overlay.HudElement;

public class KeystrokesElement extends HudElement {
    public KeystrokesElement() {
        super("keystrokes", "Keystrokes");
    }

    @Override
    public void render(GraphicsContext gc, double sw, double sh, boolean editMode) {
        if (!cfg.visible && !editMode) return;
        double x = px(sw), y = py(sh), w = pw(sw), h = ph(sh);
        gc.save();
        gc.setGlobalAlpha(cfg.opacity);
        drawBackground(gc, x, y, w, h);

        double ks = Math.min(w / 3.2, h / 3.2);
        double gap = 3;
        double startX = x + (w - ks * 3 - gap * 2) / 2;
        double row1Y = y + (h - ks * 2 - gap) / 2;
        double row2Y = row1Y + ks + gap;

        // W A S D + Space
        boolean w_ = !editMode && isKeyDown(87);
        boolean a = !editMode && isKeyDown(65);
        boolean s = !editMode && isKeyDown(83);
        boolean d = !editMode && isKeyDown(68);

        drawKey(gc, startX + ks + gap, row1Y, ks, ks, "W", w_);
        drawKey(gc, startX, row2Y, ks, ks, "A", a);
        drawKey(gc, startX + ks + gap, row2Y, ks, ks, "S", s);
        drawKey(gc, startX + (ks + gap) * 2, row2Y, ks, ks, "D", d);

        gc.restore();
    }

    private void drawKey(GraphicsContext gc, double x, double y, double w, double h, String key, boolean pressed) {
        gc.setFill(pressed ? Color.web("#7c6af7aa") : Color.web("#ffffff22"));
        gc.fillRoundRect(x, y, w, h, 4, 4);
        gc.setStroke(Color.web("#ffffff44"));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x, y, w, h, 4, 4);
        gc.setFont(Font.font("Monospaced", Math.max(8, h * 0.45)));
        gc.setFill(Color.WHITE);
        double tw = key.length() * h * 0.28;
        gc.fillText(key, x + (w - tw) / 2, y + h * 0.68);
    }

    private boolean isKeyDown(int keyCode) {
        // Real key state would come from a native hook; placeholder returns false
        return false;
    }
}
