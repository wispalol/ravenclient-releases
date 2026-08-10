package org.ravenclient.title.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.ravenclient.title.RavenConfig;
import org.ravenclient.title.RavenTitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla title screen with the RavenClient Lunar-style menu.
 * Skipped while a loading overlay is active, while SHIFT is held (so users can
 * always get the default screen), when disabled in config, or when
 * {@link RavenTitleScreen#suppressVanilla} was set right before opening a
 * vanilla TitleScreen on purpose.
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void raven$openCustomTitle(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!RavenConfig.showCustomTitle) {
            return;
        }
        if (RavenTitleScreen.suppressVanilla) {
            RavenTitleScreen.suppressVanilla = false;
            return;
        }
        if (mc.getOverlay() != null) {
            return;
        }
        if (mc.hasShiftDown()) {
            return;
        }
        mc.setScreen(new RavenTitleScreen());
        ci.cancel();
    }
}
