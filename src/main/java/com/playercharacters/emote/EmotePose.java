package com.playercharacters.emote;

import org.joml.Matrix3f;
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
	/** The emote moves the legs itself (the Griddy, sitting), so they don't switch to walking when the player moves. */
	public boolean ownLegs;
	/**
	 * How far each elbow is bent, in degrees: 0 is a straight arm, 90 a right angle. The forearm bends toward the front of
	 * the upper arm, so which way that is depends on how the upper arm is turned.
	 */
	public float rightElbow;
	public float leftElbow;
	/** How far each forearm is turned around itself, in degrees: positive turns the palm toward the front of the arm. */
	public float rightTwist;
	public float leftTwist;
	/** How far each finger is curled, thumb first (see {@link HandShape}): 0 as modeled, 1 a fist. */
	public final float[] rightHand = new float[5];
	public final float[] leftHand = new float[5];

	/** Arm lengths from the shoulder to the elbow and from the elbow to the hand, as parts of the whole arm. */
	public static final float UPPER_ARM = 0.45F;
	public static final float FOREARM = 0.55F;
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

	public EmotePose rightElbow(float bend) {
		this.rightElbow = bend;
		return this;
	}

	public EmotePose leftElbow(float bend) {
		this.leftElbow = bend;
		return this;
	}

	/** Bends the right elbow and turns the forearm ({@code twist} degrees, positive turns the palm forward). */
	public EmotePose rightElbow(float bend, float twist) {
		this.rightElbow = bend;
		this.rightTwist = twist;
		return this;
	}

	public EmotePose leftElbow(float bend, float twist) {
		this.leftElbow = bend;
		this.leftTwist = twist;
		return this;
	}

	/** Both elbows bent the same. */
	public EmotePose elbows(float bend) {
		this.rightElbow = bend;
		this.leftElbow = bend;
		return this;
	}

	public EmotePose rightHand(HandShape shape) {
		shape.copyTo(this.rightHand);
		return this;
	}

	public EmotePose leftHand(HandShape shape) {
		shape.copyTo(this.leftHand);
		return this;
	}

	public EmotePose hands(HandShape shape) {
		return this.rightHand(shape).leftHand(shape);
	}

	/**
	 * Puts the right hand at a point, bending the elbow as needed: {@code left}, {@code up} and {@code forward} from the
	 * shoulder, in arm lengths (1 is the whole arm stretched out). The elbow points toward ({@code elbowLeft},
	 * {@code elbowUp}, {@code elbowForward}), for example out to the side and down.
	 */
	public EmotePose rightHandAt(float left, float up, float forward, float elbowLeft, float elbowUp, float elbowForward) {
		this.rightElbow = reach(this.rightArm, left, up, forward, elbowLeft, elbowUp, elbowForward);
		return this;
	}

	/** Like {@link #rightHandAt}, for the left hand ({@code left} still points to the character's left). */
	public EmotePose leftHandAt(float left, float up, float forward, float elbowLeft, float elbowUp, float elbowForward) {
		this.leftElbow = reach(this.leftArm, left, up, forward, elbowLeft, elbowUp, elbowForward);
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
		this.ownLegs = other.ownLegs;
		this.jaw = other.jaw;
		this.mad = other.mad;
		this.weight = other.weight;
		this.rightElbow = other.rightElbow;
		this.leftElbow = other.leftElbow;
		this.rightTwist = other.rightTwist;
		this.leftTwist = other.leftTwist;
		System.arraycopy(other.rightHand, 0, this.rightHand, 0, 5);
		System.arraycopy(other.leftHand, 0, this.leftHand, 0, 5);
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
		this.rightElbow += (other.rightElbow - this.rightElbow) * amount;
		this.leftElbow += (other.leftElbow - this.leftElbow) * amount;
		this.rightTwist += (other.rightTwist - this.rightTwist) * amount;
		this.leftTwist += (other.leftTwist - this.leftTwist) * amount;
		for (int i = 0; i < 5; i++) {
			this.rightHand[i] += (other.rightHand[i] - this.rightHand[i]) * amount;
			this.leftHand[i] += (other.leftHand[i] - this.leftHand[i]) * amount;
		}
	}

	private static void mix(float[] a, float[] b, float amount) {
		for (int i = 0; i < 3; i++) {
			a[i] += (b[i] - a[i]) * amount;
		}
	}

	/**
	 * Two-joint reaching: turns the upper arm and returns the elbow bend (degrees) that put the hand at a point. In model
	 * space the arm hangs along +Y, the character's left is +X and its front is -Z; the forearm bends toward the upper
	 * arm's front (its -Z), so the arm is turned around itself until that faces the hand.
	 */
	static float reach(float[] angles, float left, float up, float forward, float elbowLeft, float elbowUp, float elbowForward) {
		Vector3f target = new Vector3f(left, -up, -forward);
		float distance = target.length();
		Vector3f dir = distance < 1.0E-5F ? new Vector3f(0.0F, 1.0F, 0.0F) : new Vector3f(target).div(distance);
		distance = Math.max(Math.abs(UPPER_ARM - FOREARM) + 0.02F, Math.min(UPPER_ARM + FOREARM - 0.002F, distance));
		Vector3f pole = new Vector3f(elbowLeft, -elbowUp, -elbowForward);
		pole.sub(new Vector3f(dir).mul(pole.dot(dir)));
		if (pole.lengthSquared() < 1.0E-6F) {
			pole.set(0.0F, 0.0F, 1.0F).sub(new Vector3f(dir).mul(dir.z)); // elbow back, like a person's
			if (pole.lengthSquared() < 1.0E-6F) {
				pole.set(1.0F, 0.0F, 0.0F);
			}
		}
		pole.normalize();
		// The angle between the upper arm and the line to the hand, from the triangle shoulder - elbow - hand.
		float cosShoulder = (UPPER_ARM * UPPER_ARM + distance * distance - FOREARM * FOREARM) / (2.0F * UPPER_ARM * distance);
		cosShoulder = Math.max(-1.0F, Math.min(1.0F, cosShoulder));
		float sinShoulder = (float) Math.sqrt(1.0F - cosShoulder * cosShoulder);
		Vector3f upper = new Vector3f(dir).mul(cosShoulder).add(new Vector3f(pole).mul(sinShoulder)).normalize();
		Vector3f fore = new Vector3f(dir).mul(distance).sub(new Vector3f(upper).mul(UPPER_ARM)).normalize();
		float bend = (float) Math.acos(Math.max(-1.0F, Math.min(1.0F, upper.dot(fore))));
		// Which way the forearm goes off the upper arm: that is where the arm's front has to face.
		Vector3f front = new Vector3f(fore).sub(new Vector3f(upper).mul(fore.dot(upper)));
		if (front.lengthSquared() < 1.0E-6F) {
			front.set(pole).mul(-1.0F);
		}
		front.normalize();
		Vector3f back = new Vector3f(front).mul(-1.0F); // the arm's +Z
		Vector3f side = new Vector3f(upper).cross(back); // the arm's +X, so that X x Y = Z
		Quaternionf rotation = new Quaternionf().setFromNormalized(new Matrix3f(side.x, side.y, side.z, upper.x, upper.y, upper.z, back.x, back.y, back.z));
		Vector3f euler = rotation.getEulerAnglesZYX(new Vector3f());
		set(angles, (float) Math.toDegrees(euler.x), (float) Math.toDegrees(euler.y), (float) Math.toDegrees(euler.z));
		return (float) Math.toDegrees(bend);
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
