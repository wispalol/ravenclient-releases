package org.ravenclient.hud.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.ravenclient.hud.config.HudConfig;
import org.ravenclient.hud.render.elements.CoordsElement;
import org.ravenclient.hud.render.elements.FpsElement;
import org.ravenclient.hud.render.elements.WatermarkElement;

import java.util.ArrayList;
import java.util.List;

public class HudRenderer {

	private final HudConfig config;
	private final List<HudElement> elements;
	private boolean running = false;

	public HudRenderer(HudConfig config) {
		this.config = config;
		this.elements = new ArrayList<>(List.of(
			new WatermarkElement(),
			new FpsElement(),
			new CoordsElement()
		));

		double y = 0.01;
		for (HudElement el : elements) {
			el.bind(config);
			if (!config.elements.containsKey(el.id)) {
				el.cfg.x = 0.01;
				el.cfg.y = y;
				y += 0.04;
			}
		}
	}

	public void start() { running = true; }
	public void stop() { running = false; }

	public void render(GuiGraphics guiGraphics) {
		if (!running) return;

		int width = Minecraft.getInstance().getWindow().getWidth();
		int height = Minecraft.getInstance().getWindow().getHeight();

		for (HudElement el : elements) {
			if (el.cfg == null || !el.cfg.visible) continue;
			el.render(guiGraphics, width, height, false);
		}
	}

	public List<HudElement> getElements() {
		return elements;
	}
}
