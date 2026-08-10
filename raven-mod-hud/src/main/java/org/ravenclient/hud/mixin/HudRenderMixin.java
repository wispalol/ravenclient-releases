package org.ravenclient.hud.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.ravenclient.hud.RavenHudMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class HudRenderMixin {

	@Inject(method = "render", at = @At("TAIL"))
	private void onRenderGui(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (RavenHudMod.RENDERER != null) {
			RavenHudMod.RENDERER.render(guiGraphics);
		}
	}
}
