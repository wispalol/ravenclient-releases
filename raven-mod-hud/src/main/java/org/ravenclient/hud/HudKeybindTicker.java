package org.ravenclient.hud;

import net.minecraft.client.Minecraft;
import org.ravenclient.hud.RavenHudMod;

public class HudKeybindTicker {

	private static boolean lastRightShift = false;

	public static void tick() {
		if (Minecraft.getInstance().player == null) return;

		boolean rightShift = false;
		if (RavenHudMod.menuKey != null) {
			try {
				java.lang.reflect.Method m = RavenHudMod.menuKey.getClass().getMethod("isDown");
				rightShift = (Boolean) m.invoke(RavenHudMod.menuKey);
			} catch (Exception e) {
				rightShift = false;
			}
		}

		if (rightShift && !lastRightShift) {
			RavenHudMod.openMenu();
		}
		lastRightShift = rightShift;
	}
}
