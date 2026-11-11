import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Draws the emote wheel's textures: the wheel itself and its slots.
 * Run: {@code java EmoteWheelTextures.java <textures/gui folder>}
 */
public class EmoteWheelTextures {
	public static void main(String[] args) throws IOException {
		File out = new File(args[0]);
		out.mkdirs();

		BufferedImage wheel = image(256);
		Graphics2D g = graphics(wheel);
		g.setColor(new Color(16, 18, 30, 165));
		g.fill(new Ellipse2D.Double(4, 4, 248, 248));
		g.setColor(new Color(10, 12, 20, 120));
		g.fill(new Ellipse2D.Double(70, 70, 116, 116));
		g.setStroke(new BasicStroke(3f));
		g.setColor(new Color(255, 255, 255, 110));
		g.draw(new Ellipse2D.Double(5.5, 5.5, 245, 245));
		g.setStroke(new BasicStroke(2f));
		g.setColor(new Color(255, 255, 255, 70));
		g.draw(new Ellipse2D.Double(71, 71, 114, 114));
		g.dispose();
		ImageIO.write(wheel, "png", new File(out, "emote_wheel.png"));

		ImageIO.write(slot(new Color(70, 76, 98, 215), new Color(40, 44, 60, 215), new Color(255, 255, 255, 150), 2.5f),
			"png", new File(out, "emote_slot.png"));
		ImageIO.write(slot(new Color(255, 214, 92, 240), new Color(232, 150, 40, 240), new Color(255, 255, 255, 255), 4f),
			"png", new File(out, "emote_slot_selected.png"));
	}

	static BufferedImage slot(Color top, Color bottom, Color border, float width) {
		BufferedImage image = image(64);
		Graphics2D g = graphics(image);
		g.setPaint(new GradientPaint(0, 4, top, 0, 60, bottom));
		g.fill(new Ellipse2D.Double(3, 3, 58, 58));
		g.setPaint(null);
		g.setStroke(new BasicStroke(width));
		g.setColor(border);
		g.draw(new Ellipse2D.Double(3 + width / 2, 3 + width / 2, 58 - width, 58 - width));
		g.dispose();
		return image;
	}

	static BufferedImage image(int size) {
		return new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
	}

	static Graphics2D graphics(BufferedImage image) {
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		return g;
	}
}
