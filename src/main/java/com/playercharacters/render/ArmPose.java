package com.playercharacters.render;

import com.playercharacters.emote.HandShape;
import java.util.Arrays;

/**
 * How one arm is bent this frame, on top of where the player model's arm points: the elbow, the forearm's twist and
 * the fingers. A straight arm with the fingers as modeled needs no extra work to draw.
 */
public final class ArmPose {
	/** Radians the elbow is bent (0 is straight) and the forearm is turned around itself (positive turns the palm forward). */
	public float bend;
	public float twist;
	/** How far each finger is curled, by {@code FingerRole} order (thumb first): 0 as modeled, 1 a fist. */
	public final float[] curl = new float[5];

	public void reset() {
		this.bend = 0.0F;
		this.twist = 0.0F;
		Arrays.fill(this.curl, 0.0F);
	}

	public void set(ArmPose other) {
		this.bend = other.bend;
		this.twist = other.twist;
		System.arraycopy(other.curl, 0, this.curl, 0, 5);
	}

	/** Whether the arm is straight and the fingers are as modeled. */
	public boolean isStraight() {
		return this.bend == 0.0F && this.twist == 0.0F && !this.curls();
	}

	/** Whether any finger is curled or stretched. */
	public boolean curls() {
		for (float c : this.curl) {
			if (c != 0.0F) {
				return true;
			}
		}
		return false;
	}

	/** Moves toward an elbow bend and twist (degrees) and finger curls by {@code amount} (0 to 1). */
	public void blend(float bendDegrees, float twistDegrees, float[] curls, float amount) {
		if (amount <= 0.0F) {
			return;
		}
		this.bend += ((float) Math.toRadians(bendDegrees) - this.bend) * amount;
		this.twist += ((float) Math.toRadians(twistDegrees) - this.twist) * amount;
		for (int i = 0; i < 5; i++) {
			this.curl[i] += (curls[i] - this.curl[i]) * amount;
		}
	}

	/** Moves toward an elbow bend and twist (degrees) and a hand shape by {@code amount} (0 to 1). */
	public void blend(float bendDegrees, float twistDegrees, HandShape hand, float amount) {
		if (amount <= 0.0F) {
			return;
		}
		this.bend += ((float) Math.toRadians(bendDegrees) - this.bend) * amount;
		this.twist += ((float) Math.toRadians(twistDegrees) - this.twist) * amount;
		this.shape(hand, amount);
	}

	/** Moves the fingers toward a hand shape by {@code amount}. */
	public void shape(HandShape shape, float amount) {
		if (amount <= 0.0F) {
			return;
		}
		for (int i = 0; i < 5; i++) {
			this.curl[i] += (shape.curl(i) - this.curl[i]) * amount;
		}
	}
}
