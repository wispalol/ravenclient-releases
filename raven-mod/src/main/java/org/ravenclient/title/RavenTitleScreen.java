package org.ravenclient.title;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * The Lunar-inspired main menu shown instead of the vanilla title screen.
 * Dark gradient backdrop with a soft glow, brand block, account card,
 * a big circular PLAY button, secondary pills, and a footer.
 */
public class RavenTitleScreen extends Screen {

    /** Set right before opening the vanilla TitleScreen so the mixin lets it through. */
    public static volatile boolean suppressVanilla = false;

    private static final int ACCENT = 0xFF6A7CF6;

    private float age;

    public RavenTitleScreen() {
        super(Component.literal("RavenClient"));
    }

    @Override
    protected void init() {
        clearWidgets();
        int cx = width / 2;
        int playY = (int) (height * 0.53);
        int pillY = playY + 64;

        int playSize = 92;
        addRenderableWidget(new RavenButton(cx - playSize / 2, playY - playSize / 2, playSize, playSize,
                RavenButton.Kind.PLAY, Component.empty(), btn ->
                        Minecraft.getInstance().setScreen(new SelectWorldScreen(this))));

        int mw = 170;
        int ow = 120;
        int gap = 14;
        int startX = cx - (mw + gap + ow) / 2;
        addRenderableWidget(new RavenButton(startX, pillY, mw, 44, RavenButton.Kind.PILL,
                Component.literal("Multiplayer"), btn ->
                        Minecraft.getInstance().setScreen(new JoinMultiplayerScreen(this))));
        addRenderableWidget(new RavenButton(startX + mw + gap, pillY, ow, 44, RavenButton.Kind.PILL,
                Component.literal("Options"), btn ->
                        Minecraft.getInstance().setScreen(new OptionsScreen(this, Minecraft.getInstance().options))));

        addRenderableWidget(new RavenButton(width - 92, height - 34, 66, 20, RavenButton.Kind.TEXT,
                Component.literal("Quit"), btn -> Minecraft.getInstance().stop()));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        age += partialTick;
        float t = Mth.clamp(age / 0.55F, 0.0F, 1.0F);
        float ease = 1.0F - (float) Math.pow(1.0F - t, 3.0);

        renderBackdrop(guiGraphics, ease);
        drawBrand(guiGraphics, ease);
        drawAccount(guiGraphics, ease);
        drawCenter(guiGraphics, ease);
        drawFooter(guiGraphics, ease);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (ease < 1.0F) {
            int alpha = (int) ((1.0F - ease) * 235.0F);
            guiGraphics.fill(0, 0, width, height, alpha << 24);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            returnToVanilla();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void returnToVanilla() {
        suppressVanilla = true;
        Minecraft.getInstance().setScreen(new TitleScreen());
    }

    // --- drawing -----------------------------------------------------------

    private void renderBackdrop(GuiGraphics guiGraphics, float ease) {
        guiGraphics.fillGradient(0, 0, width, height, 0xFF0C1019, 0xFF04060B);

        int cx = width / 2;
        int cy = (int) (height * 0.22);
        int size = Math.max(width, height);
        int alpha = (int) (55 * ease);
        RavenTextures.drawTinted(guiGraphics, RavenTextures.circle(),
                cx - size / 2, cy - size / 2, size, size, (alpha << 24) | ACCENT);
    }

    private void drawBrand(GuiGraphics guiGraphics, float ease) {
        int slide = (int) ((1.0F - ease) * 14);
        int x = 26;
        int y = 26 + slide;
        guiGraphics.fill(x, y + 3, x + 5, y + 21, ACCENT);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("RAVEN"), x + 15, y - 1, 0xFFFFFFFF);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("CLIENT"), x + 15, y + 11, 0xFF8A93A6);
    }

    private void drawAccount(GuiGraphics guiGraphics, float ease) {
        Minecraft mc = Minecraft.getInstance();
        String name = mc.getUser().getName();
        int slide = (int) ((1.0F - ease) * 14);

        int panelW = Math.max(120, mc.font.width(Component.literal(name)) + 78);
        int panelH = 40;
        int px = width - panelW - 26;
        int py = 22 + slide;

        RavenTextures.drawTinted(guiGraphics, RavenTextures.rounded(), px, py, panelW, panelH, 0xB3222F44);
        int dot = 10;
        RavenTextures.drawTinted(guiGraphics, RavenTextures.circle(),
                px + 20, py + panelH / 2 - dot / 2, dot, dot, 0xFF4ADE80);
        guiGraphics.drawString(mc.font, Component.literal(name), px + 40, py + panelH / 2 - 4, 0xFFFFFFFF);
    }

    private void drawCenter(GuiGraphics guiGraphics, float ease) {
        int cx = width / 2;
        int logoY = (int) (height * 0.20);
        int slide = (int) ((1.0F - ease) * 24);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(cx, logoY + slide, 0.0D);
        guiGraphics.pose().scale(3.1F, 3.1F, 1.0F);
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, Component.literal("RAVEN"), 2, 2, 0x66000000);
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, Component.literal("RAVEN"), 0, 0, 0xFFFFFFFF);
        guiGraphics.pose().popPose();

        int barY = logoY + 42 + slide;
        guiGraphics.fill(cx - 44, barY, cx + 44, barY + 3, ACCENT);
    }

    private void drawFooter(GuiGraphics guiGraphics, float ease) {
        Minecraft mc = Minecraft.getInstance();
        int slide = (int) ((1.0F - ease) * 14);
        guiGraphics.drawString(mc.font, Component.literal("Minecraft " + mc.getLaunchedVersion()),
                26, height - 30 + slide, 0xFF8A93A6);
        guiGraphics.drawString(mc.font, Component.literal("RavenClient - not affiliated with Mojang or Microsoft"),
                26, height - 18 + slide, 0xFF5A6375);
    }
}
