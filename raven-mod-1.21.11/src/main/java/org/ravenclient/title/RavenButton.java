package org.ravenclient.title;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Clean glassy pill used for the three main menu buttons: translucent white base
 * with a soft drop shadow, brightening with a gentle glow on hover. On 1.21.11
 * {@code renderWidget} is final, so the drawing is done in {@code renderContents}.
 */
public class RavenButton extends Button {

    private float hover;

    public RavenButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderContents(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        float target = isHovered() ? 1.0F : 0.0F;
        this.hover = Mth.lerp(Math.min(1.0F, partialTick * 12.0F), this.hover, target);

        int w = getWidth();
        int h = getHeight();
        int cx = getX() + w / 2;
        int cy = getY() + h / 2;

        if (hover > 0.01F) {
            int glow = Math.max(w, h) + 28;
            int a = (int) (72 * hover);
            RavenTextures.drawSoftDisc(guiGraphics, cx - glow / 2, cy - glow / 2, glow, glow, (a << 24) | 0xFFFFFF);
        }

        float scale = 1.0F + 0.04F * hover;
        int sw = Math.round(w * scale);
        int sh = Math.round(h * scale);

        int shadow = 0x33000000;
        RavenTextures.draw(guiGraphics, RavenTextures.rounded(sw, sh), sw, sh,
                cx - sw / 2, cy - sh / 2 + 2, sw, sh, shadow);

        int bg = blend(0xCCFFFFFF, 0xF7FFFFFF, hover);
        RavenTextures.draw(guiGraphics, RavenTextures.rounded(sw, sh), sw, sh,
                cx - sw / 2, cy - sh / 2, sw, sh, bg);

        int text = blend(0xFF2A2E34, 0xFF13161B, hover);
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), cx, cy - 4, text);
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
