package org.ravenclient.hud.render.elements;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.ravenclient.hud.render.HudElement;

import java.util.ArrayList;
import java.util.List;

public class PotionEffectsElement extends HudElement {
	public PotionEffectsElement() { super("potions", "Potion Effects"); }

	@Override
	public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, double sw, double sh, boolean editMode) {
		if (!cfg.visible && !editMode) return;
		double x = px(sw), y = py(sh), w = pw(sw), h = ph(sh);
		drawBackground(guiGraphics, x, y, w, h);

		List<String> effects = new ArrayList<>();
		effects.add("Speed I");
		effects.add("Strength I");
		effects.add("Night Vision");

		double lineY = y + h - 5;
		for (int i = effects.size() - 1; i >= 0; i--) {
			drawText(guiGraphics, effects.get(i), x + 4, lineY);
			lineY -= cfg.fontSize + 2;
		}
	}
}
