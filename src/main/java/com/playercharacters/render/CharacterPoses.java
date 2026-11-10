package com.playercharacters.render;

import com.playercharacters.character.CharacterModel;
import com.playercharacters.character.CharacterModel.Bone;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Changes to the player model's pose after Minecraft posed it: bending over to fart, the middle finger, and the
 * per-character head turning and waddling. 3D characters copy the player model's pose, and held items and armor
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
