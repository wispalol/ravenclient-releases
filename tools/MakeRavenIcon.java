import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dev tool - renders a raven silhouette and writes raven.png + raven.ico
 * into src/main/resources. The ico embeds the 256px PNG (Windows supports
 * PNG-compressed ICO entries). Run from the project root:
 *
 *   javac -d target/iconbuild tools/MakeRavenIcon.java
 *   java -cp target/iconbuild MakeRavenIcon
 */
public class MakeRavenIcon {

    public static void main(String[] args) throws Exception {
        int size = 256;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        Color ink = new Color(12, 12, 16);
        g.setColor(ink);

        // Body
        g.fill(new Ellipse2D.Double(100, 98, 122, 134));
        // Head
        g.fill(new Ellipse2D.Double(74, 56, 62, 54));
        // Crown feathers
        g.fill(new Polygon(new int[]{84, 92, 103}, new int[]{58, 26, 58}, 3));
        g.fill(new Polygon(new int[]{98, 109, 120}, new int[]{54, 22, 56}, 3));
        // Beak
        GeneralPath beak = new GeneralPath();
        beak.moveTo(88, 98);
        beak.lineTo(12, 116);
        beak.lineTo(88, 120);
        beak.closePath();
        g.fill(beak);
        // Tail
        GeneralPath tail = new GeneralPath();
        tail.moveTo(192, 168);
        tail.lineTo(255, 188);
        tail.lineTo(212, 238);
        tail.lineTo(168, 226);
        tail.closePath();
        g.fill(tail);
        // Eye
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(92, 74, 7, 9));

        g.dispose();

        Path png = Path.of("src/main/resources/raven.png");
        Path ico = Path.of("src/main/resources/raven.ico");
        ImageIO.write(img, "png", png.toFile());
        byte[] pngBytes = Files.readAllBytes(png);

        ByteBuffer buf = ByteBuffer.allocate(22 + pngBytes.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 0);
        buf.putShort((short) 1);
        buf.putShort((short) 1);
        buf.put((byte) 0);
        buf.put((byte) 0);
        buf.put((byte) 0);
        buf.put((byte) 0);
        buf.putShort((short) 1);
        buf.putShort((short) 32);
        buf.putInt(pngBytes.length);
        buf.putInt(22);
        buf.put(pngBytes);
        Files.write(ico, buf.array());

        System.out.println("Wrote " + new File(png.toString()).getAbsolutePath());
        System.out.println("Wrote " + new File(ico.toString()).getAbsolutePath());
    }
}
