package org.ravenclient.hud.render.elements;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.ravenclient.hud.render.HudElement;

public class WatermarkElement extends HudElement {
	public WatermarkElement() { super("watermark", "Watermark"); }

	@Override
	public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, double sw, double sh, boolean editMode) {
		if (!cfg.visible && !editMode) return;
		double x = px(sw), y = py(sh), w = pw(sw), h = ph(sh);
		drawBackground(guiGraphics, x, y, w, h);
		drawText(guiGraphics, "RavenClient", x + 4, y + h - 6);
	}
}
