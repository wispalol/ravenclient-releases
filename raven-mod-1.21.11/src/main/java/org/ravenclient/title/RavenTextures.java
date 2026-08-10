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
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Lazily generates the white shape textures the title screen is drawn with
 * (soft sun disc, sakura petal, mist band, hill silhouette, rounded panel)
 * and tints them at draw time. Every texture is a square the sampler maps to
 * the requested size, so the UV math in {@link #drawTinted} stays uniform.
 */
public final class RavenTextures {

    public static final int SIZE = 512;

    private static Identifier sun;
    private static Identifier petal;
    private static Identifier cloud;
    private static Identifier hills;
    private static Identifier rounded;

    private RavenTextures() {
    }

    public static Identifier sun() {
        if (sun == null) {
            sun = register("raven_sun", makeSun());
        }
        return sun;
    }

    public static Identifier petal() {
        if (petal == null) {
            petal = register("raven_petal", makePetal());
        }
        return petal;
    }

    public static Identifier cloud() {
        if (cloud == null) {
            cloud = register("raven_cloud", makeCloud());
        }
        return cloud;
    }

    public static Identifier hills() {
        if (hills == null) {
            hills = register("raven_hills", makeHills());
        }
        return hills;
    }

    public static Identifier rounded() {
        if (rounded == null) {
            rounded = register("raven_rounded", makeRounded());
        }
        return rounded;
    }

    /** Draws a white shape texture tinted with the given ARGB color, scaled to w/h. */
    public static void drawTinted(GuiGraphics guiGraphics, Identifier texture,
                                  int x, int y, int width, int height, int argb) {
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                x, y, 0, 0, width, height, SIZE, SIZE, SIZE, SIZE, argb);
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
    private static BufferedImage makeSun() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        float r = SIZE / 2.0F - 4.0F;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float dx = (x - SIZE / 2.0F) / r;
                float dy = (y - SIZE / 2.0F) / r;
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

    /** A single sakura petal, drawn pointed at the bottom with a rounded top. */
    private static BufferedImage makePetal() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        Path2D path = new Path2D.Double();
        path.moveTo(SIZE * 0.5, SIZE * 0.16);
        path.curveTo(SIZE * 0.29, SIZE * 0.18, SIZE * 0.19, SIZE * 0.31, SIZE * 0.19, SIZE * 0.62);
        path.curveTo(SIZE * 0.19, SIZE * 0.82, SIZE * 0.33, SIZE * 0.88, SIZE * 0.5, SIZE * 0.88);
        path.curveTo(SIZE * 0.67, SIZE * 0.88, SIZE * 0.81, SIZE * 0.82, SIZE * 0.81, SIZE * 0.62);
        path.curveTo(SIZE * 0.81, SIZE * 0.31, SIZE * 0.71, SIZE * 0.18, SIZE * 0.5, SIZE * 0.16);
        path.closePath();
        g.fill(path);
        g.dispose();
        return image;
    }

    /** Soft horizontal mist band fading out on all sides. */
    private static BufferedImage makeCloud() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float fx = x / (SIZE - 1.0F);
                float fy = y / (SIZE - 1.0F);
                float a = softEdge(fx, 0.12F) * softEdge(fy, 0.30F);
                int alpha = (int) (255.0F * a);
                image.setRGB(x, y, (alpha << 24) | 0xFFFFFF);
            }
        }
        return image;
    }

    /** Rolling hill silhouette spanning the bottom of the texture. */
    private static BufferedImage makeHills() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        Path2D path = new Path2D.Double();
        path.moveTo(0, SIZE);
        path.lineTo(0, SIZE * 0.78);
        path.quadTo(SIZE * 0.18, SIZE * 0.49, SIZE * 0.41, SIZE * 0.64);
        path.quadTo(SIZE * 0.55, SIZE * 0.72, SIZE * 0.68, SIZE * 0.58);
        path.quadTo(SIZE * 0.86, SIZE * 0.41, SIZE, SIZE * 0.57);
        path.lineTo(SIZE, SIZE);
        path.closePath();
        g.fill(path);
        g.dispose();
        return image;
    }

    private static BufferedImage makeRounded() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Double(4, 4, SIZE - 8, SIZE - 8, 96, 96));
        g.dispose();
        return image;
    }

    private static float softEdge(float f, float edgeWidth) {
        float t = Math.min(f, 1.0F - f) / edgeWidth;
        t = Math.max(0.0F, Math.min(1.0F, t));
        return t * t * (3.0F - 2.0F * t);
    }
}
