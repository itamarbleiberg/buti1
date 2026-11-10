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
 * The fart pose: the upper body leans forward around the hips, which pushes the butt out. The legs stay where
 * they are. Works on Minecraft's player model, and on 3D characters through their own joints.
 */
public final class FartPose {
	/** How far the upper body leans forward when fully bent over, in radians (about 17 degrees). */
	private static final float MAX_LEAN = 0.3F;
	/** Where the legs of Minecraft's player model meet its body, in model space (blocks, Y down). */
	private static final Vector3f PLAYER_HIP = new Vector3f(0.0F, 0.75F, 0.0F);

	private FartPose() {
	}

	/** Call after the model was posed for this frame. {@code bend} goes from 0 (standing) to about 1. */
	public static void bend(PlayerModel model, @Nullable CharacterModel character, float bend) {
		float lean = bend * MAX_LEAN;
		// In model space the front is -Z and Y points down, so turning around +X leans forward.
		Quaternionf turn = new Quaternionf().rotationX(lean);
		Vector3f hip = character != null ? new Vector3f(character.hip()) : PLAYER_HIP;
		bendPart(model.body, character, Bone.BODY, hip, turn, lean);
		bendPart(model.head, character, Bone.HEAD, hip, turn, lean);
		bendPart(model.rightArm, character, Bone.RIGHT_ARM, hip, turn, lean);
		bendPart(model.leftArm, character, Bone.LEFT_ARM, hip, turn, lean);
	}

	/** Turns the part around the hip: its joint moves along the circle, and it tilts by the same angle. */
	private static void bendPart(ModelPart part, @Nullable CharacterModel character, Bone bone, Vector3f hip, Quaternionf turn, float lean) {
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

		Vector3f move = new Vector3f(joint).sub(hip);
		turn.transform(move);
		move.add(hip).sub(joint);
		part.x += move.x * 16.0F;
		part.y += move.y * 16.0F;
		part.z += move.z * 16.0F;
		part.xRot += lean;
	}
}
