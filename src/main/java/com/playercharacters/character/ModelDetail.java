package com.playercharacters.character;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import org.joml.Vector3f;

/**
 * Simpler copies of model parts for drawing characters far away, where nobody can see the fine detail. Made once when a
 * character loads, by vertex clustering: all corners within the same small cube are merged into one, and polygons that
 * shrink to a line or a point are dropped.
 */
final class ModelDetail {
	/** Cube sizes in blocks: about 1 to 2 pixels on screen at the distances these are used from. */
	static final float[] CELLS = {0.032F, 0.07F};

	private ModelDetail() {
	}

	static CharacterModel.Part simplify(CharacterModel.Part part, float cell) {
		float[] vertices = part.vertices();
		int[] colors = part.colors();
		int count = colors.length;
		if (count == 0) {
			return part;
		}
		Long2IntOpenHashMap cellIndex = new Long2IntOpenHashMap();
		cellIndex.defaultReturnValue(-1);
		int[] cellOf = new int[count];
		float[] sums = new float[count * 8];
		float[] colorSums = new float[count * 4];
		int[] members = new int[count];
		int cells = 0;
		for (int i = 0; i < count; i++) {
			int o = i * 8;
			long key = key(vertices[o] / cell, vertices[o + 1] / cell, vertices[o + 2] / cell, vertices[o + 3], vertices[o + 4], vertices[o + 5], colors[i]);
			int c = cellIndex.get(key);
			if (c < 0) {
				c = cells++;
				cellIndex.put(key, c);
			}
			cellOf[i] = c;
			for (int k = 0; k < 8; k++) {
				sums[c * 8 + k] += vertices[o + k];
			}
			int color = colors[i];
			colorSums[c * 4] += color >>> 24 & 255;
			colorSums[c * 4 + 1] += color >> 16 & 255;
			colorSums[c * 4 + 2] += color >> 8 & 255;
			colorSums[c * 4 + 3] += color & 255;
			members[c]++;
		}

		// One merged corner per cube: the average position, direction, texture spot and color of its corners.
		float[] merged = new float[cells * 8];
		int[] mergedColors = new int[cells];
		Vector3f normal = new Vector3f();
		for (int c = 0; c < cells; c++) {
			float n = members[c];
			for (int k = 0; k < 8; k++) {
				merged[c * 8 + k] = sums[c * 8 + k] / n;
			}
			normal.set(sums[c * 8 + 3], sums[c * 8 + 4], sums[c * 8 + 5]);
			if (normal.lengthSquared() > 1.0E-8F) {
				normal.normalize();
			} else {
				normal.set(0.0F, -1.0F, 0.0F);
			}
			merged[c * 8 + 3] = normal.x;
			merged[c * 8 + 4] = normal.y;
			merged[c * 8 + 5] = normal.z;
			mergedColors[c] = Math.round(colorSums[c * 4] / n) << 24 | Math.round(colorSums[c * 4 + 1] / n) << 16
				| Math.round(colorSums[c * 4 + 2] / n) << 8 | Math.round(colorSums[c * 4 + 3] / n);
		}

		FloatArrayList out = new FloatArrayList(count * 2);
		IntArrayList outColors = new IntArrayList(count / 4);
		int[] ring = new int[4];
		for (int q = 0; q + 3 < count; q += 4) {
			int size = 0;
			for (int k = 0; k < 4; k++) {
				int c = cellOf[q + k];
				if (size == 0 || ring[size - 1] != c) {
					ring[size++] = c;
				}
			}
			if (size > 1 && ring[size - 1] == ring[0]) {
				size--;
			}
			if (size < 3 || size == 3 && (ring[0] == ring[2])) {
				continue; // shrank to a line or a point
			}
			for (int k = 0; k < 4; k++) {
				int c = ring[Math.min(k, size - 1)]; // a triangle repeats its last corner
				for (int j = 0; j < 8; j++) {
					out.add(merged[c * 8 + j]);
				}
				outColors.add(mergedColors[c]);
			}
		}
		return new CharacterModel.Part(out.toFloatArray(), outColors.toIntArray(), part.pivot(), part.hand());
	}

	/**
	 * The cube a corner is in, which way it faces (one of eight directions) and roughly its color. So the two sides of a
	 * thin part (a flipper, a finger) aren't merged into each other, and neither are small features of another color,
	 * like eyes, into the skin around them.
	 */
	private static long key(float x, float y, float z, float nx, float ny, float nz, int color) {
		long ix = (long) Math.floor(x) & 0x7FF;
		long iy = (long) Math.floor(y) & 0x7FF;
		long iz = (long) Math.floor(z) & 0x7FF;
		long facing = (nx > 0 ? 1 : 0) | (ny > 0 ? 2 : 0) | (nz > 0 ? 4 : 0);
		long tone = (color >> 22 & 3) << 4 | (color >> 14 & 3) << 2 | (color >> 6 & 3);
		return ix << 45 | iy << 34 | iz << 23 | facing << 20 | tone;
	}
}
