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
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Lazily generates the white shape textures the Lunar-style UI is drawn with
 * (soft circle, play triangle, rounded panel) and tints them at draw time.
 * Shapes are rasterized with AWT at 2x and downscaled by the texture sampler,
 * which gives smooth anti-aliased edges.
 */
public final class RavenTextures {

    public static final int SIZE = 256;

    private static Identifier circle;
    private static Identifier triangle;
    private static Identifier rounded;

    private RavenTextures() {
    }

    public static Identifier circle() {
        if (circle == null) {
            circle = register("raven_circle", makeCircle());
        }
        return circle;
    }

    public static Identifier triangle() {
        if (triangle == null) {
            triangle = register("raven_triangle", makeTriangle());
        }
        return triangle;
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

    private static BufferedImage makeCircle() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(3, 3, SIZE - 6, SIZE - 6));
        g.dispose();
        return image;
    }

    private static BufferedImage makeTriangle() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        Path2D path = new Path2D.Double();
        path.moveTo(SIZE * 0.30, SIZE * 0.16);
        path.lineTo(SIZE * 0.30, SIZE * 0.84);
        path.lineTo(SIZE * 0.82, SIZE * 0.50);
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
        g.fill(new RoundRectangle2D.Double(1, 1, SIZE - 2, SIZE - 2, 44, 44));
        g.dispose();
        return image;
    }
}
