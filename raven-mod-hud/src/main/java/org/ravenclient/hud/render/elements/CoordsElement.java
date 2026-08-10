package org.ravenclient.hud.render.elements;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.ravenclient.hud.render.HudElement;

public class CoordsElement extends HudElement {
	public CoordsElement() { super("coords", "Coordinates"); }

	@Override
	public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, double sw, double sh, boolean editMode) {
		if (!cfg.visible && !editMode) return;
		double x = px(sw), y = py(sh), w = pw(sw), h = ph(sh);
		drawBackground(guiGraphics, x, y, w, h);
		String text = editMode ? "X: 128  Y: 64  Z: -256" : getCoords();
		drawText(guiGraphics, text, x + 4, y + h - 6);
	}

	private String getCoords() {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			double x = client.player.getX();
			double y = client.player.getY();
			double z = client.player.getZ();
			return String.format("X: %.0f  Y: %.0f  Z: %.0f", x, y, z);
		}
		return "X: 0  Y: 64  Z: 0";
	}
}
