package com.playercharacters.render;

import com.playercharacters.character.CharacterModel;
import com.playercharacters.character.CharacterModel.Bone;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.playercharacters.emote.EmotePose;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Changes to the player model's pose after Minecraft posed it: emotes, bending over to fart, the middle finger, and
 * the per-character head turning and waddling. 3D characters copy the player model's pose, and held items and armor
 * follow it, so changing the player model moves everything together.
 */
public final class CharacterPoses {
	/** How far the upper body leans forward when fully bent over, in radians (about 17 degrees). */
	private static final float MAX_LEAN = 0.3F;
	/** The arm pointing forward at whoever is in front, slightly toward the middle. */
	private static final float FINGER_ARM_PITCH = -1.47F;
	private static final float FINGER_ARM_YAW = -0.12F;
	/** Where the legs of Minecraft's player model meet its body, in model space (blocks, Y down). */
	private static final Vector3f PLAYER_HIP = new Vector3f(0.0F, 0.75F, 0.0F);
	/** Between the feet, in model space. */
	private static final Vector3f FEET = new Vector3f(0.0F, 1.501F, 0.0F);
	/** The player renderer draws its model at 15/16 size. */
	private static final float PLAYER_SCALE = 0.9375F;
	private static final float[] NO_TURN = new float[3];

	private CharacterPoses() {
	}

	/** Some characters only turn their head part of the way (SpongeBob's whole sponge is his head). */
	public static void headTurn(PlayerModel model, CharacterModel character) {
		float amount = character.extras().headTurn();
		if (amount < 1.0F) {
			model.head.xRot *= amount;
			model.head.yRot *= amount;
			model.head.zRot *= amount;
		}
	}

	/** Raises the right arm toward the front. {@code raise} goes from 0 (down) to about 1. */
	public static void middleFinger(PlayerModel model, float raise, float ageInTicks) {
		ModelPart arm = model.rightArm;
		float shake = 0.035F * (float) Math.sin(ageInTicks * 1.7F);
		arm.xRot += (FINGER_ARM_PITCH + shake - arm.xRot) * raise;
		arm.yRot += (FINGER_ARM_YAW - arm.yRot) * raise;
		arm.zRot -= arm.zRot * raise;
	}

	/** Bends the upper body forward around the hips, which pushes the butt out. {@code bend} goes from 0 to about 1. */
	public static void fartBend(PlayerModel model, @Nullable CharacterModel character, float bend) {
		float lean = bend * MAX_LEAN;
		// In model space the front is -Z and Y points down, so turning around +X leans forward.
		Quaternionf turn = new Quaternionf().rotationX(lean);
		Vector3f hip = character != null ? new Vector3f(character.hip()) : PLAYER_HIP;
		turnPart(model.body, character, Bone.BODY, hip, turn);
		turnPart(model.head, character, Bone.HEAD, hip, turn);
		turnPart(model.rightArm, character, Bone.RIGHT_ARM, hip, turn);
		turnPart(model.leftArm, character, Bone.LEFT_ARM, hip, turn);
		model.body.xRot += lean;
		model.head.xRot += lean;
		model.rightArm.xRot += lean;
		model.leftArm.xRot += lean;
	}

	/** Rocks the whole character from side to side in step with its walking, like a penguin. */
	public static void waddle(PlayerModel model, CharacterModel character, float walkPosition, float walkSpeed) {
		float amount = Math.min(1.0F, walkSpeed);
		if (character.extras().waddleRadians() <= 0.0F || amount < 0.01F) {
			return;
		}
		float roll = character.extras().waddleRadians() * (float) Math.sin(walkPosition * 0.6662F) * amount;
		Quaternionf turn = new Quaternionf().rotationZ(roll);
		ModelPart[] parts = {model.body, model.head, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
		Bone[] bones = {Bone.BODY, Bone.HEAD, Bone.RIGHT_ARM, Bone.LEFT_ARM, Bone.RIGHT_LEG, Bone.LEFT_LEG};
		for (int i = 0; i < parts.length; i++) {
			turnPart(parts[i], character, bones[i], FEET, turn);
			parts[i].zRot += roll; // the outermost rotation, so adding to it turns the part as a whole
		}
	}

	/**
	 * Poses the player model for an emote, blended with Minecraft's own pose by the emote's weight. The body turns around
	 * the hips and takes the head and arms along.
	 */
	public static void emote(PlayerModel model, @Nullable CharacterModel character, EmotePose pose) {
		float w = pose.weight;
		float headTurn = character != null ? character.extras().headTurn() : 1.0F;
		blend(model.head, pose.head, headTurn, w);
		blend(model.rightArm, pose.rightArm, 1.0F, w);
		blend(model.leftArm, pose.leftArm, 1.0F, w);
		blend(model.rightLeg, pose.rightLeg, 1.0F, w);
		blend(model.leftLeg, pose.leftLeg, 1.0F, w);
		blend(model.body, NO_TURN, 1.0F, w);
		model.rightArm.y -= pose.shoulderLift * w;
		model.leftArm.y -= pose.shoulderLift * w;
		model.head.z -= pose.headForward * w;
		float[] body = pose.body;
		if (body[0] != 0.0F || body[1] != 0.0F || body[2] != 0.0F) {
			Quaternionf turn = new Quaternionf().rotationZYX((float) Math.toRadians(body[2] * w), (float) Math.toRadians(body[1] * w),
				(float) Math.toRadians(body[0] * w));
			Vector3f hip = character != null ? new Vector3f(character.hip()) : PLAYER_HIP;
			rotatePart(model.body, character, Bone.BODY, hip, turn);
			rotatePart(model.head, character, Bone.HEAD, hip, turn);
			rotatePart(model.rightArm, character, Bone.RIGHT_ARM, hip, turn);
			rotatePart(model.leftArm, character, Bone.LEFT_ARM, hip, turn);
		}
	}

	/**
	 * Moves and turns the whole character for an emote (jumping, spinning, lying down, sitting). Called where the entity
	 * renderer has turned to face the body's direction: x points to the character's right, y up, -z forward.
	 */
	public static void moveWhole(PoseStack poseStack, EmotePose pose, @Nullable CharacterModel character) {
		float w = pose.weight;
		float hipHeight = character != null ? (FEET.y - character.hip().y()) * PLAYER_SCALE : (FEET.y - PLAYER_HIP.y) * PLAYER_SCALE;
		poseStack.translate(-pose.x * w, (pose.y - pose.hipDrop * hipHeight) * w, -pose.z * w);
		if (pose.yaw != 0.0F || pose.pitch != 0.0F || pose.roll != 0.0F) {
			poseStack.translate(0.0F, pose.pivotY, 0.0F);
			poseStack.mulPose(Axis.YP.rotationDegrees(pose.yaw * w));
			poseStack.mulPose(Axis.XP.rotationDegrees(-pose.pitch * w));
			poseStack.mulPose(Axis.ZP.rotationDegrees(-pose.roll * w));
			poseStack.translate(0.0F, -pose.pivotY, 0.0F);
		}
	}

	/** Bobs a 3D character up with each step and leans it forward a little when it runs. */
	public static void walkBounce(PoseStack poseStack, CharacterModel character, float walkPosition, float walkSpeed) {
		float amount = Math.min(1.0F, walkSpeed);
		float bounce = character.extras().bounce();
		if (amount < 0.01F || bounce <= 0.0F) {
			return;
		}
		// Highest when the legs pass each other, lowest when they are furthest apart.
		float step = 1.0F - Math.abs((float) Math.cos(walkPosition * 0.6662F));
		poseStack.translate(0.0F, bounce * step * amount, 0.0F);
		poseStack.mulPose(Axis.XP.rotationDegrees(-4.0F * amount * amount));
	}

	/**
	 * Small movements that make 3D characters look alive: breathing (the shoulders and head rise and fall) and nodding
	 * a little while talking.
	 */
	public static void alive(PlayerModel model, float ageInTicks, float jaw, int seed) {
		float breath = (float) Math.sin((ageInTicks + (seed & 63)) * 0.1F);
		model.rightArm.y -= 0.3F * breath;
		model.leftArm.y -= 0.3F * breath;
		model.head.y -= 0.2F * breath;
		model.head.xRot += 0.07F * jaw;
	}

	/** Every few seconds, each player's character blinks: a different rhythm per player. */
	public static boolean isBlinking(java.util.UUID player, float ageInTicks) {
		int seed = player.hashCode();
		float period = 70 + (seed & 31);
		float t = (ageInTicks + ((seed >>> 8) & 255)) % period;
		return t < 2.5F || (seed & 64) != 0 && t > 9.0F && t < 11.0F; // some blink twice
	}

	/** Blends a part's rotation toward an emote's angles (degrees). */
	private static void blend(ModelPart part, float[] degrees, float scale, float weight) {
		part.xRot += ((float) Math.toRadians(degrees[0] * scale) - part.xRot) * weight;
		part.yRot += ((float) Math.toRadians(degrees[1] * scale) - part.yRot) * weight;
		part.zRot += ((float) Math.toRadians(degrees[2] * scale) - part.zRot) * weight;
	}

	/** Turns a part around {@code center} by {@code turn}: its joint moves along, and the turn is added on top of its own rotation. */
	private static void rotatePart(ModelPart part, @Nullable CharacterModel character, Bone bone, Vector3f center, Quaternionf turn) {
		turnPart(part, character, bone, center, turn);
		Quaternionf rotation = new Quaternionf(turn).mul(new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot));
		Vector3f angles = rotation.getEulerAnglesZYX(new Vector3f());
		part.xRot = angles.x;
		part.yRot = angles.y;
		part.zRot = angles.z;
	}

	/** Moves the part's joint along a circle around {@code center}. The caller turns the part itself by the same angle. */
	private static void turnPart(ModelPart part, @Nullable CharacterModel character, Bone bone, Vector3f center, Quaternionf turn) {
		CharacterModel.Part characterPart = null;
		if (character != null) {
			characterPart = character.part(bone);
			if (characterPart == null && bone == Bone.HEAD) {
				characterPart = character.part(Bone.JAW); // a jaw without a head turns around its own joint
			}
		}

		// The joint where it is drawn this frame: a 3D character's own joint, moved as much as the player model's part moved.
		Vector3f joint;
		if (characterPart != null) {
			PartPose rest = part.getInitialPose();
			joint = new Vector3f(characterPart.pivot()).add((part.x - rest.x()) / 16.0F, (part.y - rest.y()) / 16.0F, (part.z - rest.z()) / 16.0F);
		} else {
			joint = new Vector3f(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F);
		}

		Vector3f move = new Vector3f(joint).sub(center);
		turn.transform(move);
		move.add(center).sub(joint);
		part.x += move.x * 16.0F;
		part.y += move.y * 16.0F;
		part.z += move.z * 16.0F;
	}
}
