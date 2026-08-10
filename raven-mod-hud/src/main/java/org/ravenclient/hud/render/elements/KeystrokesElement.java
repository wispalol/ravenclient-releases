package org.ravenclient.hud.render.elements;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.ravenclient.hud.render.HudElement;

public class KeystrokesElement extends HudElement {
	public KeystrokesElement() { super("keystrokes", "Keystrokes"); }

	@Override
	public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, double sw, double sh, boolean editMode) {
		if (!cfg.visible && !editMode) return;
		double x = px(sw), y = py(sh), w = pw(sw), h = ph(sh);
		drawBackground(guiGraphics, x, y, w, h);

		double ks = Math.min(w / 3.2, h / 3.2);
		double gap = 3;
		double startX = x + (w - ks * 3 - gap * 2) / 2;
		double row1Y = y + (h - ks * 2 - gap) / 2;
		double row2Y = row1Y + ks + gap;

		drawKey(guiGraphics, startX + ks + gap, row1Y, ks, ks, "W", false);
		drawKey(guiGraphics, startX, row2Y, ks, ks, "A", false);
		drawKey(guiGraphics, startX + ks + gap, row2Y, ks, ks, "S", false);
		drawKey(guiGraphics, startX + (ks + gap) * 2, row2Y, ks, ks, "D", false);
	}

	private void drawKey(net.minecraft.client.gui.GuiGraphics guiGraphics, double x, double y, double w, double h, String key, boolean pressed) {
		int bgColor = pressed ? 0x7c6af7aa : 0x22ffffff;
		guiGraphics.fill((int)x, (int)y, (int)w, (int)h, bgColor);
		Minecraft client = Minecraft.getInstance();
		int textWidth = client.font.width(key);
		guiGraphics.drawString(client.font, key, (int)(x + w/2 - textWidth/2), (int)(y + h/2 - 4), 0xFFFFFFFF);
	}
}
