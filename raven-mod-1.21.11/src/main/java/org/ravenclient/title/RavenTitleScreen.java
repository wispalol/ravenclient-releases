package org.ravenclient.title;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Clean main menu over the real rotating Minecraft panorama: three glassy pills
 * in the center, the account name top-right and the RavenClient wordmark in the
 * footer, all easing in with staggered slide/fade animations.
 */
public class RavenTitleScreen extends Screen {

    /** Set right before opening the vanilla TitleScreen so the mixin lets it through. */
    public static volatile boolean suppressVanilla = false;

    private final PanoramaRenderer panorama;
    private final RavenButton[] buttons = new RavenButton[3];
    private final int[] baseY = new int[3];
    private float age;

    public RavenTitleScreen() {
        super(Component.literal("RavenClient"));
        this.panorama = new PanoramaRenderer(new CubeMap(
                Identifier.fromNamespaceAndPath("minecraft", "textures/gui/title/background/panorama")));
    }

    @Override
    protected void init() {
        clearWidgets();

        int w = 210;
        int h = 26;
        int gap = 16;
        int blockH = h * 3 + gap * 2;
        int cx = (width - w) / 2;
        int startY = height / 2 - blockH / 2;

        buttons[0] = new RavenButton(cx, startY, w, h, Component.literal("Singleplayer"),
                btn -> Minecraft.getInstance().setScreen(new SelectWorldScreen(this)));
        buttons[1] = new RavenButton(cx, startY + h + gap, w, h, Component.literal("Multiplayer"),
                btn -> Minecraft.getInstance().setScreen(new JoinMultiplayerScreen(this)));
        buttons[2] = new RavenButton(cx, startY + 2 * (h + gap), w, h, Component.literal("Settings"),
                btn -> Minecraft.getInstance().setScreen(new OptionsScreen(this, Minecraft.getInstance().options)));

        for (int i = 0; i < buttons.length; i++) {
            baseY[i] = buttons[i].getY();
            addRenderableWidget(buttons[i]);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        age += partialTick;

        panorama.render(guiGraphics, width, height, true);
        guiGraphics.fillGradient(0, 0, width, height, 0x1F000000, 0x3D000000);

        float t = Mth.clamp(age / 0.9F, 0.0F, 1.0F);
        float ease = 1.0F - (float) Math.pow(1.0F - t, 3.0);

        for (int i = 0; i < buttons.length; i++) {
            float e = enter(age, 0.12F + i * 0.12F, 0.45F);
            buttons[i].setY(baseY[i] + (int) ((1.0F - e) * 26));
        }

        drawAccount(guiGraphics, ease);
        drawFooter(guiGraphics, ease);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (ease < 1.0F) {
            int alpha = (int) ((1.0F - ease) * 220.0F);
            guiGraphics.fill(0, 0, width, height, alpha << 24);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (keyEvent.isEscape()) {
            returnToVanilla();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    private void returnToVanilla() {
        suppressVanilla = true;
        Minecraft.getInstance().setScreen(new TitleScreen());
    }

    // --- drawing -----------------------------------------------------------

    private void drawAccount(GuiGraphics guiGraphics, float ease) {
        Minecraft mc = Minecraft.getInstance();
        String name = mc.getUser().getName();
        float e = enter(age, 0.05F, 0.5F);
        int slide = (int) ((1.0F - e) * 30);

        int panelW = Math.max(108, mc.font.width(Component.literal(name)) + 56);
        int panelH = 32;
        int px = width - panelW - 22 + slide;
        int py = 18;

        int panelAlpha = (int) (0xD9 * e);
        RavenTextures.draw(guiGraphics, RavenTextures.rounded(panelW, panelH), panelW, panelH,
                px, py, panelW, panelH, (panelAlpha << 24) | 0xFFFFFF);

        int dot = 9;
        int dotAlpha = (int) (0xFF * e);
        RavenTextures.drawSoftDisc(guiGraphics, px + 16, py + panelH / 2 - dot / 2, dot, dot,
                (dotAlpha << 24) | 0x4CAF7D);

        int textAlpha = (int) (0xFF * e);
        guiGraphics.drawString(mc.font, Component.literal(name),
                px + 32, py + panelH / 2 - 4, (textAlpha << 24) | 0x23262B);
    }

    private void drawFooter(GuiGraphics guiGraphics, float ease) {
        Minecraft mc = Minecraft.getInstance();
        float e = enter(age, 0.45F, 0.5F);
        int slide = (int) ((1.0F - e) * 16);
        int alpha = (int) (0xE6 * e);
        guiGraphics.drawCenteredString(mc.font, Component.literal("RavenClient"),
                width / 2, height - 18 + slide, (alpha << 24) | 0xFFFFFF);
    }

    private static float enter(float age, float delay, float duration) {
        float t = Mth.clamp((age - delay) / duration, 0.0F, 1.0F);
        return 1.0F - (float) Math.pow(1.0F - t, 3.0);
    }
}
