package org.ravenclient.hud.render.elements;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.ravenclient.hud.render.HudElement;

public class CoordsElement extends HudElement {
	public CoordsElement() { super("coords", "Coordinates"); }

	@Override
	public void render(GuiGraphics g, int sw, int sh, boolean edit) {
		if (!cfg.visible) return;
		int x = (int)(cfg.x * sw);
		int y = (int)(cfg.y * sh);
		int w = (int)(cfg.width * sw);
		int h = (int)(cfg.height * sh);
		drawBg(g, x, y, w, h);
		String text = "XYZ: " + getCoords();
		drawText(g, text, x + 4, y + h - 6);
	}

	private String getCoords() {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			return String.format("%.0f / %.0f / %.0f", client.player.getX(), client.player.getY(), client.player.getZ());
		}
		return "0 / 64 / 0";
	}
}
