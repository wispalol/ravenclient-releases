package org.ravenclient.hud.render.elements;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.ravenclient.hud.render.HudElement;

public class FpsElement extends HudElement {
	private int fps = 0;
	private long lastTime = System.nanoTime();
	private int frames = 0;

	public FpsElement() { super("fps", "FPS"); }

	@Override
	public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, double sw, double sh, boolean editMode) {
		if (!cfg.visible && !editMode) return;
		frames++;
		long now = System.nanoTime();
		if (now - lastTime >= 1_000_000_000L) {
			fps = frames;
			frames = 0;
			lastTime = now;
		}
		double x = px(sw), y = py(sh), w = pw(sw), h = ph(sh);
		drawBackground(guiGraphics, x, y, w, h);
		String text = editMode ? "FPS: 120" : "FPS: " + fps;
		drawText(guiGraphics, text, x + 4, y + h - 6);
	}
}
