package org.ravenclient.title;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Clean glassy pill used for the three main menu buttons: translucent white base
 * with a soft drop shadow, brightening with a gentle glow on hover.
 */
public class RavenButton extends Button {

    private float hover;

    public RavenButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int w = getWidth();
        int h = getHeight();
        int cx = getX() + w / 2;
        int cy = getY() + h / 2;

        if (isFocused()) {
            int focusPad = 5;
            int fw = w + focusPad * 2;
            int fh = h + focusPad * 2;
            RavenTextures.draw(guiGraphics, RavenTextures.rounded(fw, fh), fw, fh,
                    cx - fw / 2, cy - fh / 2, fw, fh, 0x66FFFFFF);
        }

        int shadow = 0x20000000;
        RavenTextures.draw(guiGraphics, RavenTextures.rounded(w, h), w, h,
                cx - w / 2, cy - h / 2 + 3, w, h, shadow);

        RavenTextures.draw(guiGraphics, RavenTextures.rounded(w, h), w, h,
                cx - w / 2, cy - h / 2, w, h, 0xE6FFFFFF);

        guiGraphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), cx, cy - 4, 0xFF1A1D23);
    }
}
