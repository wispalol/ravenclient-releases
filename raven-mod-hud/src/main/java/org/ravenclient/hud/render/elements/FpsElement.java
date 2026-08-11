package org.ravenclient.hud.render.elements;

import net.minecraft.client.gui.GuiGraphics;
import org.ravenclient.hud.render.HudElement;

public class FpsElement extends HudElement {
	private int fps = 0;
	private long lastTime = System.nanoTime();
	private int frames = 0;

	public FpsElement() { super("fps", "FPS"); }

	@Override
	public void render(GuiGraphics g, int sw, int sh, boolean edit) {
		if (!cfg.visible) return;
		frames++;
		long now = System.nanoTime();
		if (now - lastTime >= 1_000_000_000L) {
			fps = frames;
			frames = 0;
			lastTime = now;
		}
		int x = (int)(cfg.x * sw);
		int y = (int)(cfg.y * sh);
		int w = (int)(cfg.width * sw);
		int h = (int)(cfg.height * sh);
		drawBg(g, x, y, w, h);
		drawText(g, "FPS: " + fps, x + 4, y + h - 6);
	}
}
