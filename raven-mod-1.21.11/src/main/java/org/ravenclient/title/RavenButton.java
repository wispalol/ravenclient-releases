package org.ravenclient.title;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Lunar-style button rendered from the generated shape textures:
 * PLAY = large white circle with a dark triangle that scales up on hover,
 * PILL = rounded pill, TEXT = bare label with a hover highlight.
 * On 1.21.11 {@code renderWidget} is final, so the drawing is done in
 * {@code renderContents}, which replaces the vanilla sprite entirely.
 */
public class RavenButton extends Button {

    public enum Kind {
        PILL,
        TEXT,
        PLAY
    }

    private final Kind kind;
    private float hover;

    public RavenButton(int x, int y, int width, int height, Kind kind, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.kind = kind;
    }

    @Override
    protected void renderContents(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        float target = isHovered() ? 1.0F : 0.0F;
        this.hover = Mth.lerp(Math.min(1.0F, partialTick * 14.0F), this.hover, target);

        switch (kind) {
            case PLAY -> renderPlay(guiGraphics);
            case PILL -> renderPill(guiGraphics);
            default -> renderText(guiGraphics);
        }
    }

    private void renderPlay(GuiGraphics guiGraphics) {
        int size = getWidth();
        int cx = getX() + size / 2;
        int cy = getY() + size / 2;
        float scale = 1.0F + 0.08F * hover;

        if (hover > 0.01F) {
            int glow = Math.round(size * 1.55F);
            int alpha = (int) (90 * hover);
            RavenTextures.drawTinted(guiGraphics, RavenTextures.circle(),
                    cx - glow / 2, cy - glow / 2, glow, glow, (alpha << 24) | 0x6A7CF6);
        }
        int s = Math.round(size * scale);
        RavenTextures.drawTinted(guiGraphics, RavenTextures.circle(), cx - s / 2, cy - s / 2, s, s, 0xFFF4F6FB);
        int tri = Math.round(size * 0.34F * scale);
        RavenTextures.drawTinted(guiGraphics, RavenTextures.triangle(),
                cx - tri / 2 + 2, cy - tri / 2, tri, tri, 0xFF12141C);
    }

    private void renderPill(GuiGraphics guiGraphics) {
        int w = getWidth();
        int h = getHeight();
        int cx = getX() + w / 2;
        int cy = getY() + h / 2;
        float scale = 1.0F + 0.03F * hover;
        int sw = Math.round(w * scale);
        int sh = Math.round(h * scale);

        int col = blend(0xB3222F44, 0xE83C4F77, hover);
        RavenTextures.drawTinted(guiGraphics, RavenTextures.rounded(), cx - sw / 2, cy - sh / 2, sw, sh, col);

        int textCol = blend(0xFFC8D1E0, 0xFFFFFFFF, hover);
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), cx, cy - 4, textCol);
    }

    private void renderText(GuiGraphics guiGraphics) {
        int col = blend(0xFF9AA4B8, 0xFFFFFFFF, hover);
        int x = getX();
        int y = getY() + getHeight() / 2 - 4;
        if (hover > 0.01F) {
            RavenTextures.drawTinted(guiGraphics, RavenTextures.rounded(),
                    x - 10, getY() - 2, getWidth() + 20, getHeight() + 4, 0x26223045);
        }
        guiGraphics.drawString(Minecraft.getInstance().font, getMessage(), x, y, col);
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
