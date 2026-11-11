package com.playercharacters.render;

import com.playercharacters.character.CharacterModel;
import com.playercharacters.character.CharacterModel.Bone;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.playercharacters.emote.EmotePose;
import com.playercharacters.emote.HandShape;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Changes to the player model's pose after Minecraft posed it: emotes, bending over to fart, the middle finger, and
 * the per-character head turning, waddling, four-legged walking and rifle aiming. 3D characters copy the player
 * model's pose, and held items and armor follow it, so changing the player model moves everything together.
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
	/** Four-legged characters stand up on their back legs this far (radians) for emotes. */
	public static final float REAR_UP = 1.2F;
	/** How far the legs of a four-legged character swing as it trots, at full speed. */
	private static final float TROT_STRIDE = 1.05F;
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

	/**
	 * Four legs: Minecraft posed a person, and this turns it into a trotting animal. The arms are the front legs and step
	 * together with the opposite back leg. The body stays level when sneaking: the legs bend and the head goes down
	 * instead, like an animal about to pounce. An attack is a swipe of a front paw.
	 */
	public static void fourLegs(PlayerModel model, AvatarRenderState state) {
		for (ModelPart part : new ModelPart[] {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg}) {
			PartPose rest = part.getInitialPose();
			part.x = rest.x();
			part.y = rest.y();
			part.z = rest.z();
		}
		model.body.setRotation(0.0F, 0.0F, 0.0F);
		float position = state.walkAnimationPos;
		float speed = state.walkAnimationSpeed / Math.max(0.01F, state.speedValue);
		float swing = Mth.cos(position * 0.6662F) * TROT_STRIDE * speed;
		model.rightArm.setRotation(-swing, 0.0F, 0.0F);
		model.leftArm.setRotation(swing, 0.0F, 0.0F);
		model.rightLeg.setRotation(swing, 0.0F, 0.0F);
		model.leftLeg.setRotation(-swing, 0.0F, 0.0F);
		if (state.isPassenger) {
			// Sitting on its haunches.
			model.rightLeg.xRot = -1.1F;
			model.leftLeg.xRot = -1.1F;
		}
		if (state.attackTime > 0.0F) {
			float swipe = Mth.sin(state.attackTime * Mth.PI);
			ModelPart paw = state.attackArm == HumanoidArm.LEFT ? model.leftArm : model.rightArm;
			paw.xRot -= swipe * 1.25F;
			paw.zRot += (paw == model.rightArm ? 0.25F : -0.25F) * swipe;
		}
		if (state.isCrouching) {
			model.rightArm.xRot -= 0.55F;
			model.leftArm.xRot -= 0.55F;
			model.rightLeg.xRot += 0.6F;
			model.leftLeg.xRot += 0.6F;
			model.head.xRot += 0.3F;
		}
	}

	/** Both arms up in front, the right one holding the rifle, aimed wherever the player looks. */
	public static void rifle(PlayerModel model) {
		float pitch = Mth.clamp(model.head.xRot, -1.2F, 1.2F);
		float yaw = model.head.yRot;
		model.rightArm.setRotation(-Mth.HALF_PI + pitch, yaw, 0.0F);
		model.leftArm.setRotation(-Mth.HALF_PI + pitch + 0.08F, yaw + 0.62F, 0.0F);
	}

	/**
	 * Raises the right arm toward the front. {@code raise} goes from 0 (down) to about 1. A hand with fingers makes the
	 * gesture itself: the upper arm goes forward and the forearm up, the back of the hand toward whoever is in front (see
	 * {@link #arms}). Other arms point straight forward with a finger modeled pointing up.
	 */
	public static void middleFinger(PlayerModel model, float raise, float ageInTicks, boolean ownHand) {
		ModelPart arm = model.rightArm;
		float shake = 0.035F * (float) Math.sin(ageInTicks * 1.7F);
		arm.xRot += ((ownHand ? GESTURE_ARM_PITCH : FINGER_ARM_PITCH) + shake - arm.xRot) * raise;
		// A hand held up from a bent elbow goes a little out to the side, so it is next to the face rather than in front of it.
		arm.yRot += ((ownHand ? GESTURE_ARM_YAW : FINGER_ARM_YAW) - arm.yRot) * raise;
		arm.zRot -= arm.zRot * raise;
	}

	/**
	 * The middle finger made with a hand's own fingers: the upper arm forward a little below the shoulder, the forearm
	 * standing up from the elbow (bend, degrees), turned (degrees) so the back of the hand faces whoever is in front.
	 */
	private static final float GESTURE_ARM_PITCH = -1.4F;
	private static final float GESTURE_ARM_YAW = 0.3F;
	private static final float GESTURE_ELBOW = 92.0F;
	private static final float GESTURE_TWIST = 90.0F;

	/**
	 * Works out this frame's elbows and fingers: holding an item closes the hand around it, emotes bend the arms and shape
	 * the hands, and the middle finger is made with the hand's own fingers where it has them. Arms that can't bend (the
	 * normal player model's) point to where the bent arm would have its hand instead.
	 */
	public static void arms(PlayerModel model, AvatarRenderState state, @Nullable CharacterModel character) {
		CharacterRenderState characterState = (CharacterRenderState) state;
		EmotePose emote = characterState.playercharacters$emote();
		float raise = characterState.playercharacters$middleFinger();
		for (int side = 0; side < 2; side++) {
			boolean right = side == 0;
			Bone bone = right ? Bone.RIGHT_ARM : Bone.LEFT_ARM;
			ArmPose pose = characterState.playercharacters$arm(right);
			pose.reset();
			if (!(right ? state.rightHandItemState : state.leftHandItemState).isEmpty()) {
				pose.shape(HandShape.GRIP, 1.0F);
			}
			if (emote != null && emote.weight > 0.0F) {
				pose.blend(right ? emote.rightElbow : emote.leftElbow, right ? emote.rightTwist : emote.leftTwist, right ? emote.rightHand : emote.leftHand,
					emote.weight);
			}
			if (right && raise > 0.0F && character != null && character.extras().fingerGesture()) {
				pose.blend(GESTURE_ELBOW, GESTURE_TWIST, HandShape.MIDDLE_FINGER, raise);
			}
			if (character == null && pose.bend != 0.0F && VanillaArms.canBend(state, right)) {
				// Minecraft's own model: the arm is drawn again bent at the elbow (see VanillaArms), without the forearm's twist.
				VanillaArms.hide(right ? model.rightArm : model.leftArm);
				pose.twist = 0.0F;
			} else if ((character == null || character.extras().rig(bone) == null) && pose.bend != 0.0F) {
				(right ? model.rightArm : model.leftArm).xRot -= straightened(pose.bend);
				pose.bend = 0.0F;
				pose.twist = 0.0F;
			}
		}
	}

	/** How much further forward a straight arm turns to point where an arm bent this far (radians) has its hand. */
	public static float straightened(float bend) {
		return (float) Math.atan2(EmotePose.FOREARM * Math.sin(bend), EmotePose.UPPER_ARM + EmotePose.FOREARM * Math.cos(bend));
	}

	/**
	 * Bends the upper body forward around the hips, which pushes the butt out. {@code bend} goes from 0 to about 1.
	 * Four-legged characters squat instead: the back legs step under the body and the rump drops.
	 */
	public static void fartBend(PlayerModel model, @Nullable CharacterModel character, float bend) {
		if (character != null && character.extras().fourLegs()) {
			Quaternionf drop = new Quaternionf().rotationX(-0.14F * bend);
			Vector3f shoulders = shoulders(character);
			rotatePart(model.body, character, Bone.BODY, shoulders, drop);
			turnPart(model.rightLeg, character, Bone.RIGHT_LEG, shoulders, drop);
			turnPart(model.leftLeg, character, Bone.LEFT_LEG, shoulders, drop);
			model.rightLeg.xRot -= 0.5F * bend;
			model.leftLeg.xRot -= 0.5F * bend;
			return;
		}
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

	/** Model space: between the front legs' joints of a four-legged character. */
	private static Vector3f shoulders(CharacterModel character) {
		CharacterModel.Part right = character.part(Bone.RIGHT_ARM);
		CharacterModel.Part left = character.part(Bone.LEFT_ARM);
		if (right != null && left != null) {
			return new Vector3f(right.pivot()).add(left.pivot()).mul(0.5F);
		}
		return new Vector3f(character.hip());
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

	/** How much an emote leaves the legs to walking: 0 standing still, 1 walking or running. */
	public static float walking(float walkSpeed) {
		return Mth.clamp((walkSpeed - 0.05F) * 2.5F, 0.0F, 1.0F);
	}

	/**
	 * Poses the player model for an emote, blended with Minecraft's own pose by the emote's weight. The body turns around
	 * the hips and takes the head and arms along. While the player walks, the legs keep walking (unless the emote moves
	 * them itself, like the Griddy). Four-legged characters stand up on their back legs first, and use their front legs
	 * as arms.
	 */
	public static void emote(PlayerModel model, @Nullable CharacterModel character, EmotePose pose, float walkSpeed) {
		float w = pose.weight;
		float headTurn = character != null ? character.extras().headTurn() : 1.0F;
		float legs = pose.ownLegs ? w : w * (1.0F - walking(walkSpeed));
		blend(model.head, pose.head, headTurn, w);
		blend(model.rightArm, pose.rightArm, 1.0F, w);
		blend(model.leftArm, pose.leftArm, 1.0F, w);
		blend(model.rightLeg, pose.rightLeg, 1.0F, legs);
		blend(model.leftLeg, pose.leftLeg, 1.0F, legs);
		blend(model.body, NO_TURN, 1.0F, w);
		model.rightArm.y -= pose.shoulderLift * w;
		model.leftArm.y -= pose.shoulderLift * w;
		model.head.z -= pose.headForward * w;
		Vector3f hip = character != null ? new Vector3f(character.hip()) : PLAYER_HIP;
		if (character != null && character.extras().fourLegs()) {
			// Up on the back legs: the body turns up around the hips, the head and front legs ride along without turning,
			// so the head still looks ahead and the front legs hang down like arms.
			Quaternionf up = new Quaternionf().rotationX(-REAR_UP * w);
			rotatePart(model.body, character, Bone.BODY, hip, up);
			turnPart(model.head, character, Bone.HEAD, hip, up);
			turnPart(model.rightArm, character, Bone.RIGHT_ARM, hip, up);
			turnPart(model.leftArm, character, Bone.LEFT_ARM, hip, up);
		}
		float[] body = pose.body;
		if (body[0] != 0.0F || body[1] != 0.0F || body[2] != 0.0F) {
			Quaternionf turn = new Quaternionf().rotationZYX((float) Math.toRadians(body[2] * w), (float) Math.toRadians(body[1] * w),
				(float) Math.toRadians(body[0] * w));
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

	/**
	 * Four-legged characters are already lying flat, so swimming and gliding shouldn't tip them over like a person: this
	 * takes back the player renderer's turn and leaves only a tilt toward where the player looks.
	 */
	public static void levelFourLegs(PoseStack poseStack, AvatarRenderState state) {
		if (state.isFallFlying) {
			float amount = state.fallFlyingScale();
			if (!state.isAutoSpinAttack) {
				poseStack.mulPose(Axis.XP.rotationDegrees(amount * 90.0F));
			}
		} else if (state.swimAmount > 0.0F) {
			if (state.isVisuallySwimming) {
				poseStack.translate(0.0F, 1.0F, -0.3F);
			}
			float turned = Mth.lerp(state.swimAmount, 0.0F, state.isInWater ? -90.0F - state.xRot : -90.0F);
			poseStack.mulPose(Axis.XP.rotationDegrees(-turned));
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
