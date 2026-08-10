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
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
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
        int r = size / 2;
        int cx = getX() + r;
        int cy = getY() + r;
        float scale = 1.0F + 0.08F * hover;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(cx, cy, 0.0D);
        if (hover > 0.01F) {
            int glow = (int) (size * 1.55F);
            int alpha = (int) (90 * hover);
            RavenTextures.drawTinted(guiGraphics, RavenTextures.circle(),
                    -glow / 2, -glow / 2, glow, glow, (alpha << 24) | 0x6A7CF6);
        }
        guiGraphics.pose().scale(scale, scale, 1.0F);
        RavenTextures.drawTinted(guiGraphics, RavenTextures.circle(), -r, -r, size, size, 0xFFF4F6FB);
        int tri = (int) (size * 0.34);
        RavenTextures.drawTinted(guiGraphics, RavenTextures.triangle(), -tri / 2 + 2, -tri / 2, tri, tri, 0xFF12141C);
        guiGraphics.pose().popPose();
    }

    private void renderPill(GuiGraphics guiGraphics) {
        int w = getWidth();
        int h = getHeight();
        int cx = getX() + w / 2;
        int cy = getY() + h / 2;
        float scale = 1.0F + 0.03F * hover;

        int col = blend(0xB3222F44, 0xE83C4F77, hover);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(cx, cy, 0.0D);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        RavenTextures.drawTinted(guiGraphics, RavenTextures.rounded(), -w / 2, -h / 2, w, h, col);
        guiGraphics.pose().popPose();

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
