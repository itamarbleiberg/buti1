package com.playercharacters.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.playercharacters.character.CharacterModel;
import com.playercharacters.character.CharacterModel.Bone;
import com.playercharacters.character.PlayerCharacter;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.Deadmau5EarsLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.StuckInBodyLayer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Draws a 3D character in place of the player model. Each part copies the pose of the matching part of
 * Minecraft's player model, so walking, looking around, swinging, sneaking and riding all still animate.
 */
public final class CharacterModelRenderer {
	private CharacterModelRenderer() {
	}

	public static @Nullable PlayerCharacter characterOf(Object renderState) {
		return renderState instanceof CharacterRenderState state ? state.playercharacters$character() : null;
	}

	/** Replaces the player model with the character's model. Called with the same arguments the player model would get. */
	public static void submit(CharacterModel model, PlayerModel playerModel, AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
		int light, int overlay, int tint) {
		// Drawing is deferred, so the player model hasn't been posed for this player yet; pose it now to read its angles.
		playerModel.setupAnim(state);
		RenderType type = ARGB.alpha(tint) < 255 ? RenderTypes.itemEntityTranslucentCull(model.texture()) : RenderTypes.entityCutout(model.texture());
		float jawAngle = ((CharacterRenderState) state).playercharacters$jaw() * model.jawOpenRadians();

		submitPart(model, Bone.BODY, playerModel.body, null, 0, poseStack, collector, type, light, overlay, tint);
		submitPart(model, Bone.HEAD, playerModel.head, null, 0, poseStack, collector, type, light, overlay, tint);
		submitPart(model, Bone.JAW, playerModel.head, model.part(Bone.HEAD), jawAngle, poseStack, collector, type, light, overlay, tint);
		submitPart(model, Bone.RIGHT_ARM, playerModel.rightArm, null, 0, poseStack, collector, type, light, overlay, tint);
		submitPart(model, Bone.LEFT_ARM, playerModel.leftArm, null, 0, poseStack, collector, type, light, overlay, tint);
		submitPart(model, Bone.RIGHT_LEG, playerModel.rightLeg, null, 0, poseStack, collector, type, light, overlay, tint);
		submitPart(model, Bone.LEFT_LEG, playerModel.leftLeg, null, 0, poseStack, collector, type, light, overlay, tint);
	}

	/**
	 * @param follows   the player model part whose pose this part copies
	 * @param jawParent for the jaw: the head part (the jaw turns with the head first, then opens around its own hinge)
	 */
	private static void submitPart(CharacterModel model, Bone bone, ModelPart follows, CharacterModel.@Nullable Part jawParent, float jawAngle,
		PoseStack poseStack, SubmitNodeCollector collector, RenderType type, int light, int overlay, int tint) {
		CharacterModel.Part part = model.part(bone);
		// Parts without polygons only provide a joint (like the burger's head joint, which its bun turns with).
		if (part == null || part.vertexCount() == 0 || !follows.visible) {
			return;
		}
		poseStack.pushPose();
		if (bone == Bone.JAW) {
			applyPose(poseStack, jawParent != null ? jawParent.pivot() : part.pivot(), follows);
			if (jawAngle != 0.0F) {
				Vector3f hinge = part.pivot();
				poseStack.translate(hinge.x, hinge.y, hinge.z);
				poseStack.mulPose(new Quaternionf().rotationX(jawAngle));
				poseStack.translate(-hinge.x, -hinge.y, -hinge.z);
			}
		} else {
			applyPose(poseStack, part.pivot(), follows);
		}
		collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> emit(part, pose, buffer, light, overlay, tint));
		poseStack.popPose();
	}

	/** Moves and turns around {@code pivot} the same way {@code follows} moved and turned away from its rest pose. */
	private static void applyPose(PoseStack poseStack, Vector3f pivot, ModelPart follows) {
		PartPose rest = follows.getInitialPose();
		poseStack.translate(pivot.x + (follows.x - rest.x()) / 16.0F, pivot.y + (follows.y - rest.y()) / 16.0F, pivot.z + (follows.z - rest.z()) / 16.0F);
		if (follows.xRot != 0.0F || follows.yRot != 0.0F || follows.zRot != 0.0F) {
			poseStack.mulPose(new Quaternionf().rotationZYX(follows.zRot, follows.yRot, follows.xRot));
		}
		poseStack.translate(-pivot.x, -pivot.y, -pivot.z);
	}

	private static void emit(CharacterModel.Part part, PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int tint) {
		Matrix4f matrix = pose.pose();
		Vector3f p = new Vector3f();
		Vector3f n = new Vector3f();
		float[] v = part.vertices();
		int[] colors = part.colors();
		boolean tinted = tint != -1;
		for (int i = 0, count = colors.length; i < count; i++) {
			int o = i * 8;
			matrix.transformPosition(v[o], v[o + 1], v[o + 2], p);
			pose.transformNormal(v[o + 3], v[o + 4], v[o + 5], n); // only normalizes when the pose was scaled unevenly
			int color = tinted ? ARGB.multiply(colors[i], tint) : colors[i];
			buffer.addVertex(p.x, p.y, p.z, color, v[o + 6], v[o + 7], overlay, light, n.x, n.y, n.z);
		}
	}

	// ------------------------------------------------------------------ layers

	/** Worn armor-slot items (armor, elytra, pumpkins and heads). Hidden for every character. */
	public static boolean isWornEquipment(RenderLayer<?, ?> layer) {
		return layer instanceof HumanoidArmorLayer<?, ?, ?> || layer instanceof WingsLayer<?, ?> || layer instanceof CustomHeadLayer<?, ?>;
	}

	/** Things glued to the shape of the normal player body. Hidden for 3D characters, where they would float. */
	public static boolean isAttachedToPlayerBody(RenderLayer<?, ?> layer) {
		return layer instanceof CapeLayer || layer instanceof Deadmau5EarsLayer || layer instanceof StuckInBodyLayer<?, ?>;
	}

	/**
	 * Runs the held-item layer with the player model's arms moved so that its hands line up with the character's
	 * hands. Minecraft's own item code then does the rest (bows, shields, spyglasses, spears...).
	 */
	public static void withHandsAtCharacter(CharacterModel model, PlayerModel playerModel, Runnable submitItems) {
		ModelPart right = playerModel.rightArm;
		ModelPart left = playerModel.leftArm;
		float rx = right.x;
		float ry = right.y;
		float rz = right.z;
		float lx = left.x;
		float ly = left.y;
		float lz = left.z;
		moveHand(model.part(Bone.RIGHT_ARM), right, 1);
		moveHand(model.part(Bone.LEFT_ARM), left, -1);
		try {
			submitItems.run();
		} finally {
			right.x = rx;
			right.y = ry;
			right.z = rz;
			left.x = lx;
			left.y = ly;
			left.z = lz;
		}
	}

	/**
	 * The item layer puts items at (-side/16, 0.625, -0.125) in the arm's own space. Places the arm so that this
	 * point lands on the character's hand, given that both arms are turned the same way.
	 */
	private static void moveHand(CharacterModel.@Nullable Part part, ModelPart arm, int side) {
		if (part == null || part.hand() == null) {
			return;
		}
		PartPose rest = arm.getInitialPose();
		Vector3f shoulder = new Vector3f(part.pivot()).add((arm.x - rest.x()) / 16.0F, (arm.y - rest.y()) / 16.0F, (arm.z - rest.z()) / 16.0F);
		Vector3f offset = new Vector3f(part.hand()).sub(part.pivot()).sub(-side / 16.0F, 0.625F, -0.125F);
		new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot).transform(offset);
		Vector3f target = shoulder.add(offset);
		arm.x = target.x * 16.0F;
		arm.y = target.y * 16.0F;
		arm.z = target.z * 16.0F;
	}
}
