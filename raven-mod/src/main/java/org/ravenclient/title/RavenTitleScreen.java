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
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

/**
 * Clean Japanese-inspired main menu: warm paper gradient, a soft vermilion sun,
 * drifting sakura petals, mist bands and distant hills around a centered stack
 * of three buttons. The account name sits in the top-right corner and the
 * RavenClient wordmark in the footer.
 */
public class RavenTitleScreen extends Screen {

    /** Set right before opening the vanilla TitleScreen so the mixin lets it through. */
    public static volatile boolean suppressVanilla = false;

    private static final int INK = 0xFF33363C;

    private final Petal[] petals;
    private final Cloud[] clouds;
    private float age;

    public RavenTitleScreen() {
        super(Component.literal("RavenClient"));
        this.petals = new Petal[14];
        this.clouds = new Cloud[3];
    }

    @Override
    protected void init() {
        clearWidgets();

        Random random = new Random();
        for (int i = 0; i < petals.length; i++) {
            petals[i] = new Petal(random, width, height);
        }
        for (int i = 0; i < clouds.length; i++) {
            clouds[i] = new Cloud(random, width, height);
        }

        int w = 210;
        int h = 26;
        int gap = 16;
        int blockH = h * 3 + gap * 2;
        int cx = (width - w) / 2;
        int startY = height / 2 - blockH / 2;

        addRenderableWidget(new RavenButton(cx, startY, w, h,
                Component.literal("Singleplayer"), btn ->
                        Minecraft.getInstance().setScreen(new SelectWorldScreen(this))));
        addRenderableWidget(new RavenButton(cx, startY + h + gap, w, h,
                Component.literal("Multiplayer"), btn ->
                        Minecraft.getInstance().setScreen(new JoinMultiplayerScreen(this))));
        addRenderableWidget(new RavenButton(cx, startY + 2 * (h + gap), w, h,
                Component.literal("Settings"), btn ->
                        Minecraft.getInstance().setScreen(new OptionsScreen(this, Minecraft.getInstance().options))));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        age += partialTick;
        float t = Mth.clamp(age / 0.55F, 0.0F, 1.0F);
        float ease = 1.0F - (float) Math.pow(1.0F - t, 3.0);

        renderBackdrop(guiGraphics);
        drawSun(guiGraphics, ease);
        drawClouds(guiGraphics, ease);
        drawHills(guiGraphics, ease);
        drawPetals(guiGraphics, ease);
        drawAccount(guiGraphics, ease);
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

    private void renderBackdrop(GuiGraphics guiGraphics) {
        guiGraphics.fillGradient(0, 0, width, height, 0xFFFCF7EE, 0xFFF0DFC8);
    }

    private void drawSun(GuiGraphics guiGraphics, float ease) {
        int cx = (int) (width * 0.40);
        int cy = (int) (height * 0.24);
        int size = (int) (height * 0.82);
        int alpha = (int) (0x96 * ease);
        RavenTextures.drawTinted(guiGraphics, RavenTextures.sun(),
                cx - size / 2, cy - size / 2, size, size, (alpha << 24) | 0xC84B31);
    }

    private void drawClouds(GuiGraphics guiGraphics, float ease) {
        for (Cloud cloud : clouds) {
            float f = (age * cloud.speed + cloud.offset) % 1.6F;
            if (f < 0) f += 1.6F;
            int x = (int) (f * (width + cloud.width)) - (int) cloud.width;
            int alpha = (int) (cloud.alpha * ease);
            RavenTextures.drawTinted(guiGraphics, RavenTextures.cloud(),
                    x, cloud.y, (int) cloud.width, (int) (cloud.width * 0.22),
                    (alpha << 24) | 0xFFFFFF);
        }
    }

    private void drawHills(GuiGraphics guiGraphics, float ease) {
        int w = (int) (width * 1.15);
        int farH = (int) (height * 0.20);
        int nearH = (int) (height * 0.14);
        int farAlpha = (int) (0x59 * ease);
        int nearAlpha = (int) (0x7D * ease);
        RavenTextures.drawTinted(guiGraphics, RavenTextures.hills(),
                (width - w) / 2, height - farH, w, farH, (farAlpha << 24) | 0xA7B4C0);
        RavenTextures.drawTinted(guiGraphics, RavenTextures.hills(),
                (width - w) / 2, height - nearH + 10, w, nearH, (nearAlpha << 24) | 0x93A2B0);
    }

    private void drawPetals(GuiGraphics guiGraphics, float ease) {
        for (Petal petal : petals) {
            float drift = (age * petal.fall + petal.phase) % 1.0F;
            float x = petal.x + (float) Math.sin((age * petal.swayFreq + petal.phase) * 6.2831853F) * petal.sway;
            float y = drift * (height + 40) - 20;
            float rot = (float) Math.sin(age * petal.rotFreq + petal.phase * 2.0F) * 0.9F;
            int alpha = (int) (0xC8 * ease);

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(x, y, 0.0D);
            guiGraphics.pose().rotateAround(new Quaternionf().rotationZ(rot), 0.0F, 0.0F, 0.0F);
            RavenTextures.drawTinted(guiGraphics, RavenTextures.petal(),
                    -petal.size / 2, -petal.size / 2, petal.size, petal.size,
                    (alpha << 24) | petal.color);
            guiGraphics.pose().popPose();
        }
    }

    private void drawAccount(GuiGraphics guiGraphics, float ease) {
        Minecraft mc = Minecraft.getInstance();
        String name = mc.getUser().getName();
        int slide = (int) ((1.0F - ease) * 14);

        int panelW = Math.max(104, mc.font.width(Component.literal(name)) + 60);
        int panelH = 34;
        int px = width - panelW - 24;
        int py = 20 + slide;

        RavenTextures.drawTinted(guiGraphics, RavenTextures.rounded(),
                px, py, panelW, panelH, 0xE6FDF8EE);
        int dot = 9;
        RavenTextures.drawTinted(guiGraphics, RavenTextures.sun(),
                px + 16, py + panelH / 2 - dot / 2, dot, dot, 0xFF4CAF7D);
        guiGraphics.drawString(mc.font, Component.literal(name),
                px + 32, py + panelH / 2 - 4, INK);
    }

    private void drawFooter(GuiGraphics guiGraphics, float ease) {
        Minecraft mc = Minecraft.getInstance();
        int slide = (int) ((1.0F - ease) * 14);
        guiGraphics.drawCenteredString(mc.font, Component.literal("RavenClient"),
                width / 2, height - 16 + slide, 0xFF9B9184);
    }

    // --- ambient elements ---------------------------------------------------

    private static final class Petal {
        final float x;
        final float phase;
        final float fall;
        final float sway;
        final float swayFreq;
        final float rotFreq;
        final int size;
        final int color;

        Petal(Random random, int screenW, int screenH) {
            x = random.nextFloat() * screenW;
            phase = random.nextFloat();
            fall = 0.020F + random.nextFloat() * 0.018F;
            sway = 18 + random.nextFloat() * 40;
            swayFreq = 0.06F + random.nextFloat() * 0.10F;
            rotFreq = 0.10F + random.nextFloat() * 0.15F;
            size = 7 + random.nextInt(7);
            color = random.nextBoolean() ? 0xE89AA8 : 0xE6A08F;
        }
    }

    private static final class Cloud {
        final int y;
        final float width;
        final float speed;
        final float alpha;
        final float offset;

        Cloud(Random random, int screenW, int screenH) {
            y = (int) (screenH * (0.16F + random.nextFloat() * 0.26F));
            width = screenW * (0.50F + random.nextFloat() * 0.35F);
            speed = 0.010F + random.nextFloat() * 0.014F;
            alpha = 0x32 + random.nextInt(0x20);
            offset = random.nextFloat() * 1.6F;
        }
    }
}
