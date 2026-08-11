package org.ravenclient.hud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import org.ravenclient.hud.config.HudConfig;
import org.ravenclient.hud.render.HudRenderer;

public class RavenHudMod implements ClientModInitializer {

	public static final String MOD_ID = "ravenclient-hud";
	public static HudConfig CONFIG;
	public static HudRenderer RENDERER;
	private static KeyMapping nestKey;

	@Override
	public void onInitializeClient() {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		CONFIG = HudConfig.load(mc.gameDirectory.toPath());
		CONFIG.ensureDefaults();

		RENDERER = new HudRenderer(CONFIG);

		HudRenderCallback.EVENT.register((graphics, tickDelta) -> RENDERER.render(graphics, 0));

		nestKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.ravenclient-hud.nest",
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				"key.categories.misc"
		));

		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (nestKey.consumeClick()) {
				if (client.player != null) {
					client.setScreen(new org.ravenclient.hud.gui.RavenNestScreen(CONFIG));
				}
			}
		});
	}
}
