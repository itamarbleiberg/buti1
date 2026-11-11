package com.playercharacters.emote;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One frame of an emote. Part rotations are in degrees, using the directions of Minecraft's player model:
 * <ul>
 *   <li>arms and legs: x negative swings them forward (-90 points straight ahead, -180 straight up), y turns them around
 *       the vertical axis, z positive raises the right arm (or leg) out to the side, negative the left one;</li>
 *   <li>head: x positive looks down, y positive looks right, z positive tilts it to its left;</li>
 *   <li>body: x positive leans forward, y positive twists to the right, z positive tilts to the left. The body turns
 *       around the hips and takes the head and arms with it.</li>
 * </ul>
 * The whole character can also move (blocks: x toward its left, y up, z forward) and turn (degrees: yaw to the left,
 * pitch forward, roll to the right) around a point {@code pivotY} blocks above its feet.
 */
public final class EmotePose {
	public final float[] head = new float[3];
	public final float[] body = new float[3];
	public final float[] rightArm = new float[3];
	public final float[] leftArm = new float[3];
	public final float[] rightLeg = new float[3];
	public final float[] leftLeg = new float[3];
	/** Pixels the shoulders are pulled up (a shrug), and the head pushed forward (pecking). */
	public float shoulderLift;
	public float headForward;
	public float x;
	public float y;
	public float z;
	public float yaw;
	public float pitch;
	public float roll;
	public float pivotY;
	/** Lowers the whole character by this much of its hip height: 1 sits it on the ground. */
	public float hipDrop;
	/** How far the mouth is open, 0 to 1. */
	public float jaw;
	/** The angry face of characters that have one. */
	public boolean mad;
	/** How much of the emote is shown: 0 is Minecraft's own pose, 1 is the emote. */
	public float weight = 1.0F;

	public EmotePose head(float down, float right, float tiltLeft) {
		set(this.head, down, right, tiltLeft);
		return this;
	}

	public EmotePose body(float forward, float right, float tiltLeft) {
		set(this.body, forward, right, tiltLeft);
		return this;
	}

	public EmotePose rightArm(float x, float y, float z) {
		set(this.rightArm, x, y, z);
		return this;
	}

	public EmotePose leftArm(float x, float y, float z) {
		set(this.leftArm, x, y, z);
		return this;
	}

	/** Both arms, the left one mirrored. */
	public EmotePose arms(float x, float y, float z) {
		return this.rightArm(x, y, z).leftArm(x, -y, -z);
	}

	public EmotePose rightLeg(float x, float y, float z) {
		set(this.rightLeg, x, y, z);
		return this;
	}

	public EmotePose leftLeg(float x, float y, float z) {
		set(this.leftLeg, x, y, z);
		return this;
	}

	/** Points the right arm toward a direction: toward its left, up and forward (any length). */
	public EmotePose rightArmTo(float left, float up, float forward) {
		aim(this.rightArm, left, up, forward, 0.0F);
		return this;
	}

	public EmotePose leftArmTo(float left, float up, float forward) {
		aim(this.leftArm, left, up, forward, 0.0F);
		return this;
	}

	/** Like {@link #rightArmTo}, also turning the arm around itself by {@code twist} degrees. */
	public EmotePose rightArmTo(float left, float up, float forward, float twist) {
		aim(this.rightArm, left, up, forward, twist);
		return this;
	}

	public EmotePose leftArmTo(float left, float up, float forward, float twist) {
		aim(this.leftArm, left, up, forward, twist);
		return this;
	}

	public EmotePose move(float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
		return this;
	}

	public EmotePose turn(float yaw, float pitch, float roll, float pivotY) {
		this.yaw = yaw;
		this.pitch = pitch;
		this.roll = roll;
		this.pivotY = pivotY;
		return this;
	}

	public void copyFrom(EmotePose other) {
		System.arraycopy(other.head, 0, this.head, 0, 3);
		System.arraycopy(other.body, 0, this.body, 0, 3);
		System.arraycopy(other.rightArm, 0, this.rightArm, 0, 3);
		System.arraycopy(other.leftArm, 0, this.leftArm, 0, 3);
		System.arraycopy(other.rightLeg, 0, this.rightLeg, 0, 3);
		System.arraycopy(other.leftLeg, 0, this.leftLeg, 0, 3);
		this.shoulderLift = other.shoulderLift;
		this.headForward = other.headForward;
		this.x = other.x;
		this.y = other.y;
		this.z = other.z;
		this.yaw = other.yaw;
		this.pitch = other.pitch;
		this.roll = other.roll;
		this.pivotY = other.pivotY;
		this.hipDrop = other.hipDrop;
		this.jaw = other.jaw;
		this.mad = other.mad;
		this.weight = other.weight;
	}

	/** Blends toward {@code other}: 0 keeps this pose, 1 takes the other one. Angles blend one by one. */
	public void mix(EmotePose other, float amount) {
		mix(this.head, other.head, amount);
		mix(this.body, other.body, amount);
		mix(this.rightArm, other.rightArm, amount);
		mix(this.leftArm, other.leftArm, amount);
		mix(this.rightLeg, other.rightLeg, amount);
		mix(this.leftLeg, other.leftLeg, amount);
		this.shoulderLift += (other.shoulderLift - this.shoulderLift) * amount;
		this.headForward += (other.headForward - this.headForward) * amount;
		this.x += (other.x - this.x) * amount;
		this.y += (other.y - this.y) * amount;
		this.z += (other.z - this.z) * amount;
		this.yaw += (other.yaw - this.yaw) * amount;
		this.pitch += (other.pitch - this.pitch) * amount;
		this.roll += (other.roll - this.roll) * amount;
		this.pivotY += (other.pivotY - this.pivotY) * amount;
		this.hipDrop += (other.hipDrop - this.hipDrop) * amount;
		this.jaw += (other.jaw - this.jaw) * amount;
		this.mad = amount < 0.5F ? this.mad : other.mad;
	}

	private static void mix(float[] a, float[] b, float amount) {
		for (int i = 0; i < 3; i++) {
			a[i] += (b[i] - a[i]) * amount;
		}
	}

	private static void set(float[] angles, float x, float y, float z) {
		angles[0] = x;
		angles[1] = y;
		angles[2] = z;
	}

	/**
	 * The rotation (in Minecraft's x, y, z order) that turns a hanging arm toward a direction given in the character's
	 * frame. In model space the arm hangs along +Y, the character's left is +X and its front is -Z.
	 */
	static void aim(float[] angles, float left, float up, float forward, float twist) {
		Vector3f direction = new Vector3f(left, -up, -forward);
		if (direction.lengthSquared() < 1.0E-8F) {
			set(angles, 0, 0, 0);
			return;
		}
		direction.normalize();
		Quaternionf rotation = new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, direction.x, direction.y, direction.z);
		if (twist != 0.0F) {
			rotation.rotateY((float) Math.toRadians(twist));
		}
		Vector3f euler = rotation.getEulerAnglesZYX(new Vector3f());
		set(angles, (float) Math.toDegrees(euler.x), (float) Math.toDegrees(euler.y), (float) Math.toDegrees(euler.z));
	}
}
