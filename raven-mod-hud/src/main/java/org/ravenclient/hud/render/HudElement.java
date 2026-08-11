package org.ravenclient.hud.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.ravenclient.hud.config.HudConfig;

public abstract class HudElement {

	public String id;
	public String label;
	public HudConfig.ElementConfig cfg;

	protected HudElement(String id, String label) {
		this.id = id;
		this.label = label;
	}

	public void bind(HudConfig config) {
		this.cfg = config.element(id);
	}

	public abstract void render(GuiGraphics g, int sw, int sh, boolean edit);

	protected void drawBg(GuiGraphics g, int x, int y, int w, int h) {
		if (!cfg.background) return;
		g.fill(x, y, x + w, y + h, cfg.bgColor);
	}

	protected void drawText(GuiGraphics g, String text, int x, int y) {
		try {
			int color = cfg.color;
			if (cfg.shadow) {
				g.drawString(Minecraft.getInstance().font, text, x + 1, y + 1, 0xFF000000);
			}
			g.drawString(Minecraft.getInstance().font, text, x, y, color);
		} catch (Throwable ignored) {}
	}
}
