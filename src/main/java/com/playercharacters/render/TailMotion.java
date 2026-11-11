package com.playercharacters.render;

import com.playercharacters.character.CharacterModel.Tail;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * How tails sway this frame. A tail bends around its base, more toward the tip, with a wave running along it: lazy
 * when standing, quicker while walking, and wagging during emotes. Several tails each move a little differently.
 * Everything is in model space (Y down, front -Z), so the tail moves with the body.
 *
 * @param time    seconds, keeps counting
 * @param speed   how fast the wave goes, in radians per second
 * @param side    how far the tips swing from side to side, in radians
 * @param upDown  how far the tips swing up and down, in radians
 * @param lift    how far the tips are raised, in radians (to keep tails up while standing up for an emote)
 */
public record TailMotion(float time, float speed, float side, float upDown, float lift) {
	public static final TailMotion STILL = new TailMotion(0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

	/**
	 * @param seconds   time, in seconds
	 * @param walking   0 standing to 1 running
	 * @param wagging   0 to 1 while doing an emote
	 * @param lift      radians to raise the tails by
	 */
	public static TailMotion of(float seconds, float walking, float wagging, float lift) {
		float walk = Mth.clamp(walking, 0.0F, 1.0F);
		float wag = Mth.clamp(wagging, 0.0F, 1.0F);
		float speed = Mth.lerp(wag, Mth.lerp(walk, 2.2F, 6.5F), 9.0F);
		float side = Mth.lerp(wag, Mth.lerp(walk, 0.26F, 0.36F), 0.48F);
		float upDown = Mth.lerp(walk, 0.1F, 0.2F);
		return new TailMotion(seconds, speed, side, upDown, lift + 0.12F * walk);
	}

	/** Steps along a tail at which its bend is worked out once per frame; points in between share the nearest two. */
	private static final int STEPS = 32;

	/**
	 * This frame's bend of one tail, worked out at {@value #STEPS} steps from base to tip: much quicker than working it out
	 * for every corner (a bushy tail has thousands).
	 */
	public Table table(Tail tail) {
		float[] turns = new float[(STEPS + 1) * 4];
		float phase = tail.index() * 2.1F;
		for (int k = 0; k <= STEPS; k++) {
			float t = (float) k / STEPS;
			float wave = this.time * this.speed + phase - 2.4F * t;
			float yaw = this.side * (float) Math.pow(t, 1.25) * Mth.sin(wave);
			float pitch = (this.upDown * Mth.sin(wave * 0.7F + 1.3F + phase) + this.lift) * t;
			turns[k * 4] = Mth.cos(pitch);
			turns[k * 4 + 1] = Mth.sin(pitch);
			turns[k * 4 + 2] = Mth.cos(yaw);
			turns[k * 4 + 3] = Mth.sin(yaw);
		}
		return new Table(tail.base(), 1.0F / tail.length(), turns);
	}

	/** One tail's bend this frame (see {@link #table}). */
	public static final class Table {
		private final Vector3f base;
		private final float inverseLength;
		/** Per step: cos and sin of the up-and-down turn, then of the side-to-side turn. */
		private final float[] turns;

		Table(Vector3f base, float inverseLength, float[] turns) {
			this.base = base;
			this.inverseLength = inverseLength;
			this.turns = turns;
		}

		/** Bends one point of the tail and its normal, in place. */
		public void bend(Vector3f point, Vector3f normal) {
			Vector3f base = this.base;
			float dx = point.x - base.x;
			float dy = point.y - base.y;
			float dz = point.z - base.z;
			float x = Math.min(1.0F, (float) Math.sqrt(dx * dx + dy * dy + dz * dz) * this.inverseLength) * STEPS;
			int k = Math.min(STEPS - 1, (int) x);
			float f = x - k;
			float[] r = this.turns;
			int o = k * 4;
			float cp = r[o] + (r[o + 4] - r[o]) * f;
			float sp = r[o + 1] + (r[o + 5] - r[o + 1]) * f;
			float cy = r[o + 2] + (r[o + 6] - r[o + 2]) * f;
			float sy = r[o + 3] + (r[o + 7] - r[o + 3]) * f;
			// Up and down first (around x), then from side to side (around the body's up axis).
			float y1 = dy * cp - dz * sp;
			float z1 = dy * sp + dz * cp;
			point.set(base.x + dx * cy + z1 * sy, base.y + y1, base.z - dx * sy + z1 * cy);
			float nx = normal.x;
			float ny = normal.y;
			float nz = normal.z;
			float ny1 = ny * cp - nz * sp;
			float nz1 = ny * sp + nz * cp;
			normal.set(nx * cy + nz1 * sy, ny1, -nx * sy + nz1 * cy);
		}
	}

	/** Bends one point of a tail. {@code normal} is turned the same way. Both are changed in place. */
	public void bend(Tail tail, Vector3f point, Vector3f normal) {
		Vector3f base = tail.base();
		float dx = point.x - base.x;
		float dy = point.y - base.y;
		float dz = point.z - base.z;
		float t = Math.min(1.0F, (float) Math.sqrt(dx * dx + dy * dy + dz * dz) / tail.length());
		float phase = tail.index() * 2.1F;
		float wave = this.time * this.speed + phase - 2.4F * t;
		float yaw = this.side * (float) Math.pow(t, 1.25) * Mth.sin(wave);
		float pitch = (this.upDown * Mth.sin(wave * 0.7F + 1.3F + phase) + this.lift) * t;
		// Up and down first (around x), then from side to side (around the body's up axis).
		float cp = Mth.cos(pitch);
		float sp = Mth.sin(pitch);
		float cy = Mth.cos(yaw);
		float sy = Mth.sin(yaw);
		point.set(base.x + rotateX(dx, dy, dz, cp, sp, cy, sy, 0), base.y + rotateX(dx, dy, dz, cp, sp, cy, sy, 1),
			base.z + rotateX(dx, dy, dz, cp, sp, cy, sy, 2));
		normal.set(rotateX(normal.x, normal.y, normal.z, cp, sp, cy, sy, 0), rotateX(normal.x, normal.y, normal.z, cp, sp, cy, sy, 1),
			rotateX(normal.x, normal.y, normal.z, cp, sp, cy, sy, 2));
	}

	/** One coordinate of a vector turned by {@code pitch} around x, then by {@code yaw} around y. */
	private static float rotateX(float x, float y, float z, float cp, float sp, float cy, float sy, int axis) {
		// Around x: a tail pointing back (+z) goes up (-y) for a positive angle.
		float y1 = y * cp - z * sp;
		float z1 = y * sp + z * cp;
		return switch (axis) {
			case 0 -> x * cy + z1 * sy;
			case 1 -> y1;
			default -> -x * sy + z1 * cy;
		};
	}
}
