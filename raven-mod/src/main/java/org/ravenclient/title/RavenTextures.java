package org.ravenclient.title;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

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

    private static ResourceLocation circle;
    private static ResourceLocation triangle;
    private static ResourceLocation rounded;

    private RavenTextures() {
    }

    public static ResourceLocation circle() {
        if (circle == null) {
            circle = register("raven_circle", makeCircle());
        }
        return circle;
    }

    public static ResourceLocation triangle() {
        if (triangle == null) {
            triangle = register("raven_triangle", makeTriangle());
        }
        return triangle;
    }

    public static ResourceLocation rounded() {
        if (rounded == null) {
            rounded = register("raven_rounded", makeRounded());
        }
        return rounded;
    }

    /** Draws a white shape texture tinted with the given ARGB color, scaled to w/h. */
    public static void drawTinted(GuiGraphics guiGraphics, ResourceLocation texture,
                                  int x, int y, int width, int height, int argb) {
        float a = ((argb >> 24) & 0xFF) / 255.0F;
        float r = ((argb >> 16) & 0xFF) / 255.0F;
        float g = ((argb >> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, g, b, a);
        guiGraphics.blit(texture, x, y, width, height, 0.0F, 0.0F, SIZE, SIZE, SIZE, SIZE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private static ResourceLocation register(String name, BufferedImage image) {
        try {
            NativeImage nativeImage = toNativeImage(image);
            return Minecraft.getInstance().getTextureManager().register("raven/" + name, new DynamicTexture(nativeImage));
        } catch (IOException e) {
            return ResourceLocation.fromNamespaceAndPath("raven", "missing");
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
