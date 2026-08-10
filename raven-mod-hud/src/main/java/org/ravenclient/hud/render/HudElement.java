package org.ravenclient.hud.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public abstract class HudElement {

	public final String id;
	public final String label;
	public org.ravenclient.hud.config.HudConfig.ElementConfig cfg;

	protected HudElement(String id, String label) {
		this.id = id;
		this.label = label;
	}

	public void bind(org.ravenclient.hud.config.HudConfig config) {
		this.cfg = config.element(id);
	}

	public double px(double sw) { return cfg.x * sw; }
	public double py(double sh) { return cfg.y * sh; }
	public double pw(double sw) { return cfg.width * sw; }
	public double ph(double sh) { return cfg.height * sh; }

	public abstract void render(net.minecraft.client.gui.GuiGraphics guiGraphics, double sw, double sh, boolean editMode);

	protected void drawBackground(net.minecraft.client.gui.GuiGraphics guiGraphics, double x, double y, double w, double h) {
		if (!cfg.background) return;
		int bg = cfg.bgColor;
		guiGraphics.fill((int)x, (int)y, (int)w, (int)h, bg);
	}

	protected int elementColor() {
		return cfg.color;
	}

	protected void drawText(net.minecraft.client.gui.GuiGraphics guiGraphics, String text, double x, double y) {
		Minecraft client = Minecraft.getInstance();
		int color = elementColor();
		if (cfg.shadow) {
			guiGraphics.drawString(client.font, text, (int)x + 1, (int)y + 1, 0xFF000000);
		}
		guiGraphics.drawString(client.font, text, (int)x, (int)y, color);
	}
}
