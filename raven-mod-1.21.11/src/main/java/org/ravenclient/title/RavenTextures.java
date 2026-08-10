package org.ravenclient.title;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Lazily generates the white shapes the title screen is drawn with: a feathered
 * disc for glows/dots, and rounded-rectangle pills generated at their exact
 * pixel size so the corners stay crisp instead of being squished by scaling.
 * Shapes are tinted at draw time via {@link #draw}.
 */
public final class RavenTextures {

    public static final int DISC_SIZE = 512;

    private static Identifier softDisc;
    private static final Map<String, Identifier> roundedCache = new HashMap<>();

    private RavenTextures() {
    }

    public static Identifier softDisc() {
        if (softDisc == null) {
            softDisc = register("raven_soft_disc", makeSoftDisc());
        }
        return softDisc;
    }

    /** Rounded-rectangle of exactly {@code width x height} pixels, cached per size. */
    public static Identifier rounded(int width, int height) {
        String key = width + "x" + height;
        Identifier id = roundedCache.get(key);
        if (id == null) {
            id = register("raven_rounded_" + key, makeRounded(width, height));
            roundedCache.put(key, id);
        }
        return id;
    }

    /** Draws a white shape texture (of texW x texH) tinted with ARGB, scaled to w x h. */
    public static void draw(GuiGraphics guiGraphics, Identifier texture, int texW, int texH,
                            int x, int y, int width, int height, int argb) {
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                x, y, 0, 0, width, height, texW, texH, texW, texH, argb);
    }

    /** Draws the feathered soft disc tinted with ARGB, scaled to w x h. */
    public static void drawSoftDisc(GuiGraphics guiGraphics, int x, int y, int width, int height, int argb) {
        draw(guiGraphics, softDisc(), DISC_SIZE, DISC_SIZE, x, y, width, height, argb);
    }

    private static Identifier register(String name, BufferedImage image) {
        try {
            NativeImage nativeImage = toNativeImage(image);
            Identifier id = Identifier.fromNamespaceAndPath("raven", name);
            Minecraft.getInstance().getTextureManager().register(id,
                    new DynamicTexture(() -> "raven/" + name, nativeImage));
            return id;
        } catch (IOException e) {
            return Identifier.fromNamespaceAndPath("raven", "missing");
        }
    }

    private static NativeImage toNativeImage(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        try (ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray())) {
            return NativeImage.read(in);
        }
    }

    /** Feathered disc: solid core with a smooth falloff to a transparent edge. */
    private static BufferedImage makeSoftDisc() {
        BufferedImage image = new BufferedImage(DISC_SIZE, DISC_SIZE, BufferedImage.TYPE_INT_ARGB);
        float r = DISC_SIZE / 2.0F - 4.0F;
        for (int y = 0; y < DISC_SIZE; y++) {
            for (int x = 0; x < DISC_SIZE; x++) {
                float dx = (x - DISC_SIZE / 2.0F) / r;
                float dy = (y - DISC_SIZE / 2.0F) / r;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float a;
                if (d <= 0.72F) {
                    a = 1.0F;
                } else if (d >= 1.0F) {
                    a = 0.0F;
                } else {
                    float t = (d - 0.72F) / 0.28F;
                    a = 1.0F - t * t;
                }
                int alpha = (int) (255.0F * a);
                image.setRGB(x, y, (alpha << 24) | 0xFFFFFF);
            }
        }
        return image;
    }

    /** Fully rounded pill at exactly width x height. */
    private static BufferedImage makeRounded(int width, int height) {
        BufferedImage image = new BufferedImage(Math.max(1, width), Math.max(1, height),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        int radius = Math.max(1, height / 2);
        g.fill(new RoundRectangle2D.Double(0, 0, width, height, radius * 2, radius * 2));
        g.dispose();
        return image;
    }
}
