package org.ravenclient.hud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import org.ravenclient.hud.config.HudConfig;
import org.ravenclient.hud.gui.RavenHudScreen;
import org.ravenclient.hud.render.HudRenderer;

public class RavenHudMod implements ClientModInitializer {

	public static final String MOD_ID = "ravenclient-hud";
	public static KeyMapping menuKey;
	public static HudConfig CONFIG;
	public static HudRenderer RENDERER;

	@Override
	public void onInitializeClient() {
		CONFIG = HudConfig.load();
		CONFIG.ensureDefaultProfile();

		menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.ravenclient-hud.menu",
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				"key.categories.misc"
		));

		RENDERER = new HudRenderer(CONFIG);
		RENDERER.start();

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			RENDERER.stop();
			try {
				CONFIG.save();
			} catch (Exception ignored) {}
		});
	}

	public static void openMenu() {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.setScreen(new RavenHudScreen(CONFIG, RENDERER));
		}
	}
}
