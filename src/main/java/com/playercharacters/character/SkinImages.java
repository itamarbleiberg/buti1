package com.playercharacters.character;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/** Loading skin PNGs the same way vanilla treats downloaded skins, and drawing open mouths. */
final class SkinImages {
	/** Inside of the mouth. */
	private static final int MOUTH_COLOR = 0xFF2B1010;
	/** Bottom row of a mouth that is at least two pixels tall. */
	private static final int TONGUE_COLOR = 0xFF9C3F3F;

	// Front of the head on the skin texture, and the hat layer on top of it.
	private static final int FACE_U = 8;
	private static final int FACE_V = 8;
	private static final int HAT_U = 40;

	private SkinImages() {
	}

	record LoadedSkin(NativeImage image, boolean slimArms) {
	}

	static LoadedSkin read(Path file) throws IOException {
		NativeImage image;
		try (InputStream in = Files.newInputStream(file)) {
			image = NativeImage.read(in);
		}

		int width = image.getWidth();
		int height = image.getHeight();
		if (width != 64 || (height != 64 && height != 32)) {
			image.close();
			throw new IOException("must be a 64x64 Minecraft skin, but it is " + width + "x" + height);
		}

		// Must happen before the alpha fix below, which fills the unused slim-arm pixels.
		boolean slim = height == 64 && hasSlimArms(image);
		return new LoadedSkin(normalize(image), slim);
	}

	/** Slim skins leave the outer 2 columns of the right arm's back face (x 54-55, y 20-31) empty. */
	private static boolean hasSlimArms(NativeImage image) {
		for (int y = 20; y < 32; y++) {
			for (int x = 54; x < 56; x++) {
				if (ARGB.alpha(image.getPixel(x, y)) != 0) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Same processing vanilla applies to downloaded skins: converts old 64x32 skins to 64x64 and makes the
	 * inner layer opaque, so characters look exactly like they would as a real skin.
	 */
	private static NativeImage normalize(NativeImage image) {
		boolean legacy = image.getHeight() == 32;
		if (legacy) {
			NativeImage converted = new NativeImage(64, 64, true);
			converted.copyFrom(image);
			image.close();
			image = converted;
			image.fillRect(0, 32, 64, 32, 0);
			image.copyRect(4, 16, 16, 32, 4, 4, true, false);
			image.copyRect(8, 16, 16, 32, 4, 4, true, false);
			image.copyRect(0, 20, 24, 32, 4, 12, true, false);
			image.copyRect(4, 20, 16, 32, 4, 12, true, false);
			image.copyRect(8, 20, 8, 32, 4, 12, true, false);
			image.copyRect(12, 20, 16, 32, 4, 12, true, false);
			image.copyRect(44, 16, -8, 32, 4, 4, true, false);
			image.copyRect(48, 16, -8, 32, 4, 4, true, false);
			image.copyRect(40, 20, 0, 32, 4, 12, true, false);
			image.copyRect(44, 20, -8, 32, 4, 12, true, false);
			image.copyRect(48, 20, -16, 32, 4, 12, true, false);
			image.copyRect(52, 20, -8, 32, 4, 12, true, false);
		}

		setNoAlpha(image, 0, 0, 32, 16);
		if (legacy) {
			clearHatIfFullyOpaque(image, 32, 0, 64, 32);
		}
		setNoAlpha(image, 0, 16, 64, 32);
		setNoAlpha(image, 16, 48, 48, 64);
		return image;
	}

	/** Copy of {@code idle} with an open mouth painted on the face (and the hat layer cleared above it). */
	static NativeImage withOpenMouth(NativeImage idle, CharacterOptions.Mouth mouth) {
		NativeImage talking = new NativeImage(idle.getWidth(), idle.getHeight(), false);
		talking.copyFrom(idle);

		int x0 = Mth.clamp(mouth.x, 0, 7);
		int y0 = Mth.clamp(mouth.y, 0, 7);
		int width = Mth.clamp(mouth.width, 1, 8 - x0);
		int height = Mth.clamp(mouth.height, 1, 8 - y0);
		for (int dy = 0; dy < height; dy++) {
			boolean bottomRow = height > 1 && dy == height - 1;
			for (int dx = 0; dx < width; dx++) {
				// Leave the bottom corners alone on wide mouths so the shape looks rounded.
				if (bottomRow && width > 2 && (dx == 0 || dx == width - 1)) {
					continue;
				}
				int x = x0 + dx;
				int y = FACE_V + y0 + dy;
				talking.setPixel(FACE_U + x, y, bottomRow ? TONGUE_COLOR : MOUTH_COLOR);
				talking.setPixel(HAT_U + x, y, 0);
			}
		}
		return talking;
	}

	private static void setNoAlpha(NativeImage image, int x0, int y0, int x1, int y1) {
		for (int x = x0; x < x1; x++) {
			for (int y = y0; y < y1; y++) {
				image.setPixel(x, y, ARGB.opaque(image.getPixel(x, y)));
			}
		}
	}

	/** Old skins without transparency support filled the hat area with a solid color; vanilla hides it. */
	private static void clearHatIfFullyOpaque(NativeImage image, int x0, int y0, int x1, int y1) {
		for (int x = x0; x < x1; x++) {
			for (int y = y0; y < y1; y++) {
				if (ARGB.alpha(image.getPixel(x, y)) < 128) {
					return;
				}
			}
		}
		for (int x = x0; x < x1; x++) {
			for (int y = y0; y < y1; y++) {
				image.setPixel(x, y, image.getPixel(x, y) & 0xFFFFFF);
			}
		}
	}
}
