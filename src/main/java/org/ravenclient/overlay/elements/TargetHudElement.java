package org.ravenclient.overlay.elements;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import org.ravenclient.overlay.HudElement;
import org.ravenclient.overlay.GameDataBridge;

public class TargetHudElement extends HudElement {
    public TargetHudElement() { super("targethud", "Target HUD"); }

    @Override
    public void render(GraphicsContext gc, double sw, double sh, boolean editMode) {
        if (!cfg.visible && !editMode) return;
        GameDataBridge.TargetInfo target = editMode
                ? new GameDataBridge.TargetInfo("Player123", 14, 20)
                : GameDataBridge.getInstance().getTarget();
        if (target == null && !editMode) return;
        if (target == null) target = new GameDataBridge.TargetInfo("Player123", 14, 20);

        double x = px(sw), y = py(sh), w = pw(sw), h = ph(sh);
        gc.save();
        gc.setGlobalAlpha(cfg.opacity);

        // Background panel
        gc.setFill(Color.web("#1a1a2ecc"));
        gc.fillRoundRect(x, y, w, h, 8, 8);
        gc.setStroke(Color.web("#7c6af755"));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x, y, w, h, 8, 8);

        // Name
        gc.setFont(Font.font("System", cfg.fontSize));
        gc.setFill(Color.WHITE);
        gc.fillText(target.name(), x + 8, y + h * 0.38);

        // Health bar
        double barX = x + 8, barY = y + h * 0.55, barW = w - 16, barH = h * 0.2;
        double hpFrac = Math.max(0, Math.min(1, target.hp() / (double) target.maxHp()));
        gc.setFill(Color.web("#333344"));
        gc.fillRoundRect(barX, barY, barW, barH, 3, 3);
        Color hpColor = hpFrac > 0.5 ? Color.web("#4caf50") : hpFrac > 0.25 ? Color.web("#ff9800") : Color.web("#f44336");
        gc.setFill(hpColor);
        gc.fillRoundRect(barX, barY, barW * hpFrac, barH, 3, 3);

        // HP text
        gc.setFont(Font.font("Monospaced", cfg.fontSize - 2));
        gc.setFill(Color.web("#cccccc"));
        gc.fillText(target.hp() + "/" + target.maxHp(), barX + barW + 4, barY + barH);

        gc.restore();
    }
}
