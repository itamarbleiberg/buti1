import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.imageio.ImageIO;

/**
 * Draws the emote wheel's textures: the eight glassy slices around the wheel (normal, highlighted and their glow), the
 * round middle, page dots, arrows and the dark edges behind it all. Everything is drawn from distance fields, so edges
 * are smooth and glows fade softly.
 * <p>
 * A slice is drawn pointing up, its wheel's middle below the picture. In wheel units (the outer radius is 1) the
 * picture covers x from -SLICE_HALF_WIDTH to +SLICE_HALF_WIDTH and y from -1 - PAD (top) to SLICE_BOTTOM (bottom,
 * still above the middle). EmoteWheelScreen uses the same numbers.
 * <p>
 * Run: {@code java EmoteWheelTextures.java <textures/gui folder>}
 */
public class EmoteWheelTextures {
	static final double INNER = 0.44;
	static final double HALF_ANGLE = Math.toRadians(22.5 - 1.1);
	static final double PAD = 0.09;
	static final double CORNER = 0.05;
	static final double SLICE_HALF_WIDTH = Math.sin(Math.toRadians(22.5)) + PAD;
	static final double SLICE_TOP = -1 - PAD;
	static final double SLICE_BOTTOM = -INNER * Math.cos(Math.toRadians(22.5)) + PAD;
	static final int PIXELS_PER_UNIT = 300;

	public static void main(String[] args) throws IOException {
		File out = new File(args[0]);
		out.mkdirs();
		System.out.printf("slice: half width %.4f, top %.4f, bottom %.4f%n", SLICE_HALF_WIDTH, SLICE_TOP, SLICE_BOTTOM);

		write(out, "wheel_slice", slice(false));
		write(out, "wheel_slice_hover", slice(true));
		write(out, "wheel_slice_glow", sliceGlow());
		write(out, "wheel_hub", hub());
		write(out, "wheel_vignette", vignette());
		write(out, "wheel_dot", dot());
		write(out, "wheel_arrow", arrow());
		write(out, "wheel_ring", ring());
	}

	/** Saves the picture, with a .mcmeta that turns on smooth scaling (the wheel is drawn at many sizes and angles). */
	static void write(File folder, String name, BufferedImage image) throws IOException {
		ImageIO.write(image, "png", new File(folder, name + ".png"));
		Files.writeString(new File(folder, name + ".png.mcmeta").toPath(), "{\n  \"texture\": {\n    \"blur\": true,\n    \"clamp\": true\n  }\n}\n",
			StandardCharsets.UTF_8);
	}

	// ------------------------------------------------------------------ slices

	interface Shader {
		/** Color (straight alpha, 0..1 per channel) at a point of the wheel: x right, y down, r and angle from straight up. */
		double[] at(double x, double y, double r, double angle, double distance);
	}

	static BufferedImage slice(boolean hover) {
		return drawSlice((x, y, r, angle, d) -> {
			double inside = coverage(d, 0.004);
			if (inside <= 0) {
				return null;
			}
			double t = (r - INNER) / (1 - INNER); // 0 at the inner edge, 1 at the outer
			double[] inner = hover ? rgb(0x2A6BFF) : rgb(0x10163A);
			double[] outer = hover ? rgb(0xB24DFF) : rgb(0x232E6E);
			double[] c = mix(inner, outer, smooth(t));
			// A soft sheen across the glass.
			double sheen = Math.exp(-sq((x * 0.8 - y * 0.6 + 0.62) / 0.11)) * (hover ? 0.22 : 0.10);
			c = add(c, sheen);
			// Light along the edges, fading inward.
			double edgeGlow = Math.exp(d / (hover ? 0.05 : 0.035)) * (hover ? 0.55 : 0.4);
			double[] glowColor = hover ? rgb(0xE6FBFF) : rgb(0x47DFFF);
			c = mix(c, glowColor, edgeGlow);
			// A thin bright rim.
			double rim = coverage(Math.abs(d + 0.006) - (hover ? 0.007 : 0.005), 0.003);
			c = mix(c, hover ? rgb(0xFFFFFF) : rgb(0x6FE9FF), rim * (hover ? 1.0 : 0.85));
			double alpha = (hover ? 0.93 : 0.82) * inside;
			alpha = Math.max(alpha, rim * inside);
			return new double[] {c[0], c[1], c[2], alpha};
		});
	}

	static BufferedImage sliceGlow() {
		return drawSlice((x, y, r, angle, d) -> {
			if (d < -0.01) {
				return null;
			}
			double glow = Math.exp(-Math.max(0, d) / 0.03);
			double[] c = mix(rgb(0x9AF4FF), rgb(0xFF7BEA), smooth((r - INNER) / (1 - INNER)));
			return new double[] {c[0], c[1], c[2], 0.75 * glow * coverage(-d - 0.002, 0.004 + 0 * angle)};
		});
	}

	static BufferedImage drawSlice(Shader shader) {
		int w = (int) Math.round(2 * SLICE_HALF_WIDTH * PIXELS_PER_UNIT);
		int h = (int) Math.round((SLICE_BOTTOM - SLICE_TOP) * PIXELS_PER_UNIT);
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int py = 0; py < h; py++) {
			for (int px = 0; px < w; px++) {
				double[] sum = new double[4];
				for (int s = 0; s < 4; s++) { // 4 samples per pixel
					double x = -SLICE_HALF_WIDTH + (px + 0.25 + 0.5 * (s & 1)) / PIXELS_PER_UNIT;
					double y = SLICE_TOP + (py + 0.25 + 0.5 * (s >> 1)) / PIXELS_PER_UNIT;
					double r = Math.sqrt(x * x + y * y);
					double angle = Math.atan2(x, -y);
					double[] c = shader.at(x, y, r, angle, sliceDistance(r, angle));
					if (c != null) {
						sum[0] += c[0] * c[3];
						sum[1] += c[1] * c[3];
						sum[2] += c[2] * c[3];
						sum[3] += c[3];
					}
				}
				image.setRGB(px, py, argb(sum));
			}
		}
		return image;
	}

	/** Signed distance (wheel units) to a slice with rounded corners: negative inside. */
	static double sliceDistance(double r, double angle) {
		double radial = Math.max(INNER - r, r - 1) + CORNER;
		double around = (Math.abs(angle) - HALF_ANGLE) * r + CORNER;
		double outside = Math.sqrt(sq(Math.max(radial, 0)) + sq(Math.max(around, 0)));
		return outside + Math.min(Math.max(radial, around), 0) - CORNER;
	}

	// ------------------------------------------------------------------ the rest

	/** The middle: a dark glass disc with a glowing ring. The disc has radius 0.86 of the picture's half size. */
	static BufferedImage hub() {
		int size = 256;
		return draw(size, (x, y) -> {
			double r = Math.sqrt(x * x + y * y) / 0.86;
			double d = r - 1;
			double inside = coverage(d * 0.86, 0.006);
			double[] c = mix(rgb(0x1B2558), rgb(0x080C20), smooth(r));
			double ringLight = Math.exp(-sq((r - 0.97) / 0.03));
			c = mix(c, rgb(0x5FE6FF), ringLight * 0.9);
			double innerRing = Math.exp(-sq((r - 0.78) / 0.008)) * 0.35;
			c = mix(c, rgb(0x5FE6FF), innerRing);
			double glow = d > 0 ? Math.exp(-d / 0.07) * 0.55 : 0;
			double alpha = Math.max(0.88 * inside, glow);
			if (d > 0) {
				c = rgb(0x5FE6FF);
			}
			return new double[] {c[0], c[1], c[2], alpha};
		});
	}

	/** A thin glowing circle around the whole wheel. The circle has radius 0.92 of the picture's half size. */
	static BufferedImage ring() {
		return draw(512, (x, y) -> {
			double r = Math.sqrt(x * x + y * y);
			double line = Math.exp(-sq((r - 0.92) / 0.006));
			double glow = Math.exp(-sq((r - 0.92) / 0.04)) * 0.35;
			double[] c = mix(rgb(0x4FDFFF), rgb(0xD9FBFF), line);
			return new double[] {c[0], c[1], c[2], Math.min(1, line * 0.8 + glow)};
		});
	}

	static BufferedImage vignette() {
		return draw(256, (x, y) -> {
			double r = Math.sqrt(x * x + y * y) / Math.sqrt(2);
			double a = 0.35 + 0.55 * smooth((r - 0.25) / 0.75);
			double[] c = mix(rgb(0x060A1C), rgb(0x000000), r);
			return new double[] {c[0], c[1], c[2], a};
		});
	}

	static BufferedImage dot() {
		return draw(32, (x, y) -> {
			double r = Math.sqrt(x * x + y * y);
			double core = coverage(r - 0.45, 0.06);
			double glow = Math.exp(-Math.max(0, r - 0.45) / 0.2) * 0.6;
			return new double[] {1, 1, 1, Math.max(core, glow * (r < 1 ? 1 : 0))};
		});
	}

	/** A chevron pointing right. */
	static BufferedImage arrow() {
		return draw(64, (x, y) -> {
			// Two thick strokes meeting at the point.
			double d = Math.min(segment(x, y, -0.25, -0.55, 0.3, 0.0), segment(x, y, -0.25, 0.55, 0.3, 0.0)) - 0.13;
			double core = coverage(d, 0.03);
			double glow = Math.exp(-Math.max(0, d) / 0.12) * 0.5;
			double[] c = mix(rgb(0xBFF6FF), rgb(0xFFFFFF), core);
			return new double[] {c[0], c[1], c[2], Math.max(core, glow)};
		});
	}

	interface Pixel {
		double[] at(double x, double y);
	}

	/** A square picture; x and y go from -1 to 1. */
	static BufferedImage draw(int size, Pixel pixel) {
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		for (int py = 0; py < size; py++) {
			for (int px = 0; px < size; px++) {
				double[] sum = new double[4];
				for (int s = 0; s < 4; s++) {
					double x = (px + 0.25 + 0.5 * (s & 1)) / size * 2 - 1;
					double y = (py + 0.25 + 0.5 * (s >> 1)) / size * 2 - 1;
					double[] c = pixel.at(x, y);
					sum[0] += c[0] * c[3];
					sum[1] += c[1] * c[3];
					sum[2] += c[2] * c[3];
					sum[3] += c[3];
				}
				image.setRGB(px, py, argb(sum));
			}
		}
		return image;
	}

	static double segment(double px, double py, double ax, double ay, double bx, double by) {
		double dx = bx - ax;
		double dy = by - ay;
		double t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)));
		return Math.hypot(px - ax - t * dx, py - ay - t * dy);
	}

	/** Sums of 4 samples (color premultiplied by alpha) to a straight-alpha ARGB pixel. */
	static int argb(double[] sum) {
		double a = sum[3] / 4;
		if (a <= 0.0005) {
			return 0;
		}
		int r = (int) Math.round(Math.min(1, sum[0] / sum[3]) * 255);
		int g = (int) Math.round(Math.min(1, sum[1] / sum[3]) * 255);
		int b = (int) Math.round(Math.min(1, sum[2] / sum[3]) * 255);
		return (int) Math.round(Math.min(1, a) * 255) << 24 | r << 16 | g << 8 | b;
	}

	/** 1 well inside a shape (negative distance), 0 outside, smooth across {@code width}. */
	static double coverage(double d, double width) {
		return smooth(0.5 - d / width);
	}

	static double smooth(double x) {
		double t = Math.max(0, Math.min(1, x));
		return t * t * (3 - 2 * t);
	}

	static double sq(double v) {
		return v * v;
	}

	static double[] rgb(int color) {
		return new double[] {(color >> 16 & 255) / 255.0, (color >> 8 & 255) / 255.0, (color & 255) / 255.0};
	}

	static double[] mix(double[] a, double[] b, double t) {
		t = Math.max(0, Math.min(1, t));
		return new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
	}

	static double[] add(double[] c, double amount) {
		return new double[] {Math.min(1, c[0] + amount), Math.min(1, c[1] + amount), Math.min(1, c[2] + amount)};
	}
}
