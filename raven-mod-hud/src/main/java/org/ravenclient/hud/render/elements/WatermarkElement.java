package org.ravenclient.hud.render.elements;

import net.minecraft.client.gui.GuiGraphics;
import org.ravenclient.hud.render.HudElement;

public class WatermarkElement extends HudElement {
	public WatermarkElement() { super("watermark", "Watermark"); }

	@Override
	public void render(GuiGraphics g, int sw, int sh, boolean edit) {
		if (!cfg.visible) return;
		int x = (int)(cfg.x * sw);
		int y = (int)(cfg.y * sh);
		int w = (int)(cfg.width * sw);
		int h = (int)(cfg.height * sh);
		drawBg(g, x, y, w, h);
		drawText(g, "RavenClient", x + 4, y + h - 6);
	}
}
