package org.ravenclient.overlay.elements;

import javafx.scene.canvas.GraphicsContext;
import org.ravenclient.overlay.HudElement;
import org.ravenclient.overlay.GameDataBridge;

public class SessionStatsElement extends HudElement {
    public SessionStatsElement() { super("session", "Session Stats"); }

    @Override
    public void render(GraphicsContext gc, double sw, double sh, boolean editMode) {
        if (!cfg.visible && !editMode) return;
        double x = px(sw), y = py(sh), w = pw(sw), h = ph(sh);
        gc.save();
        gc.setGlobalAlpha(cfg.opacity);
        drawBackground(gc, x, y, w, h);
        GameDataBridge db = GameDataBridge.getInstance();
        double lineH = h / 3.0;
        drawText(gc, editMode ? "Kills: 12" : "Kills: " + db.getKills(), x + 4, y + lineH);
        drawText(gc, editMode ? "Deaths: 2" : "Deaths: " + db.getDeaths(), x + 4, y + lineH * 2);
        drawText(gc, editMode ? "KDR: 6.0" : "KDR: " + String.format("%.1f", db.getKdr()), x + 4, y + lineH * 3 - 4);
        gc.restore();
    }
}
