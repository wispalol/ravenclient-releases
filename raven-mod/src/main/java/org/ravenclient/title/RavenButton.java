package org.ravenclient.title;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Clean rounded pill used for the three main menu buttons. Dark ink base that
 * lightens and gains a soft halo on hover.
 */
public class RavenButton extends Button {

    private float hover;

    public RavenButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        float target = isHovered() ? 1.0F : 0.0F;
        this.hover = Mth.lerp(Math.min(1.0F, partialTick * 14.0F), this.hover, target);

        int w = getWidth();
        int h = getHeight();
        int cx = getX() + w / 2;
        int cy = getY() + h / 2;

        if (hover > 0.01F) {
            int halo = Math.max(w, h) + 16;
            int alpha = (int) (36 * hover);
            RavenTextures.drawTinted(guiGraphics, RavenTextures.rounded(),
                    cx - halo / 2, cy - halo / 2, halo, halo, (alpha << 24) | 0x93A2B0);
        }

        float scale = 1.0F + 0.03F * hover;
        int sw = Math.round(w * scale);
        int sh = Math.round(h * scale);
        int col = blend(0xE63B4554, 0xFF4C5869, hover);
        RavenTextures.drawTinted(guiGraphics, RavenTextures.rounded(),
                cx - sw / 2, cy - sh / 2, sw, sh, col);

        int textCol = blend(0xFFDDE2EA, 0xFFFFFFFF, hover);
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), cx, cy - 4, textCol);
    }

    private static int blend(int from, int to, float t) {
        float k = Mth.clamp(t, 0.0F, 1.0F);
        int a = lerp((from >> 24) & 0xFF, (to >> 24) & 0xFF, k);
        int r = lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, k);
        int g = lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, k);
        int b = lerp(from & 0xFF, to & 0xFF, k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerp(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }
}
