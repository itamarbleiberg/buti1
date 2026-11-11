package com.playercharacters.emote;

/**
 * Shapes a hand can make, as how far each finger is curled: thumb, index, middle, ring and little finger. 0 is the
 * finger as the model has it (relaxed), 1 is curled into a fist, and a little below 0 stretches it straight. Hands with
 * fewer fingers just leave some out.
 */
public enum HandShape {
	RELAXED(0.0F, 0.0F, 0.0F, 0.0F, 0.0F),
	/** Fingers stretched out flat. */
	OPEN(-0.15F, -0.18F, -0.18F, -0.18F, -0.18F),
	FIST(0.9F, 1.0F, 1.0F, 1.0F, 1.0F),
	/** The index finger pointing. */
	POINT(0.9F, -0.15F, 1.0F, 1.0F, 1.0F),
	MIDDLE_FINGER(0.9F, 1.0F, -0.15F, 1.0F, 1.0F),
	PEACE(0.9F, -0.15F, -0.15F, 1.0F, 1.0F),
	THUMBS_UP(-0.2F, 1.0F, 1.0F, 1.0F, 1.0F),
	/** Index and little finger up, like at a rock concert. */
	HORNS(0.9F, -0.15F, 1.0F, 1.0F, -0.15F),
	/** Thumb and index finger making an L. */
	L(-0.25F, -0.15F, 1.0F, 1.0F, 1.0F),
	/** Fingers bent into a curve, like half of a heart. */
	CURVED(0.35F, 0.5F, 0.5F, 0.5F, 0.5F),
	/** Holding something. */
	GRIP(0.55F, 0.72F, 0.72F, 0.72F, 0.72F);

	private final float[] curl;

	HandShape(float... curl) {
		this.curl = curl;
	}

	/** How far a finger is curled in this shape, by its {@code FingerRole} ordinal. */
	public float curl(int finger) {
		return this.curl[finger];
	}

	/** Copies the curls into {@code into} (five values). */
	public void copyTo(float[] into) {
		System.arraycopy(this.curl, 0, into, 0, 5);
	}
}
