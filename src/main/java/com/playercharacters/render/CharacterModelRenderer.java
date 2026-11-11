package com.playercharacters.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.playercharacters.character.CharacterModel;
import com.playercharacters.character.CharacterModel.Bone;
import com.playercharacters.character.CharacterModel.Part;
import com.playercharacters.character.PlayerCharacter;
import com.playercharacters.effects.CigaretteSmoke;
import com.playercharacters.emote.EmotePose;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.LightTexture;
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
import net.minecraft.client.renderer.texture.OverlayTexture;
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
		submits++;
		// Drawing is deferred, so the player model hasn't been posed for this player yet; pose it now to read its angles.
		playerModel.setupAnim(state);
		model = model.forDistance(state.distanceToCameraSq); // fewer polygons far away, where details are too small to see
		RenderType type = ARGB.alpha(tint) < 255 ? RenderTypes.itemEntityTranslucentCull(model.texture()) : RenderTypes.entityCutout(model.texture());
		CharacterRenderState characterState = (CharacterRenderState) state;
		float jawAngle = characterState.playercharacters$jaw() * model.jawOpenRadians();
		CharacterModel.Extras extras = model.extras();
		// An arm modeled making the middle finger takes the right arm's place; otherwise the hand makes it with its fingers.
		boolean fingerUp = characterState.playercharacters$middleFinger() > 0.0F && extras.middleFinger() != null;
		Part rightArm = fingerUp ? extras.middleFinger() : model.part(Bone.RIGHT_ARM);
		Bends bends = new Bends(fingerUp ? null : ArmBender.of(extras.rightRig(), characterState.playercharacters$arm(true), true),
			ArmBender.of(extras.leftRig(), characterState.playercharacters$arm(false), false));

		for (Bone bone : Bone.values()) {
			submitPart(model, bone, bone == Bone.RIGHT_ARM ? rightArm : model.part(bone), playerModel, jawAngle, bends, poseStack, collector, type, light,
				overlay, tint);
		}
		// The angry face goes on while mad, and what it replaces comes off.
		Map<Bone, Part> faceParts = characterState.playercharacters$mad() ? extras.madParts() : extras.calmParts();
		for (Map.Entry<Bone, Part> entry : faceParts.entrySet()) {
			submitPart(model, entry.getKey(), entry.getValue(), playerModel, jawAngle, bends, poseStack, collector, type, light, overlay, tint);
		}
		if (characterState.playercharacters$blink()) {
			for (Map.Entry<Bone, Part> entry : extras.blinkParts().entrySet()) {
				submitPart(model, entry.getKey(), entry.getValue(), playerModel, jawAngle, bends, poseStack, collector, type, light, overlay, tint);
			}
		}
		// Glowing eyes and markings: full brightness, even at night.
		for (Map.Entry<Bone, Part> entry : extras.glowParts().entrySet()) {
			if (entry.getKey() != Bone.RIGHT_ARM || !fingerUp) {
				submitPart(model, entry.getKey(), entry.getValue(), playerModel, jawAngle, bends, poseStack, collector, type, LightTexture.FULL_BRIGHT, overlay,
					tint);
			}
		}
		// Fingers, and things held in the hands.
		for (Bone arm : new Bone[] {Bone.RIGHT_ARM, Bone.LEFT_ARM}) {
			CharacterModel.ArmRig rig = extras.rig(arm);
			ModelPart follows = follows(playerModel, arm);
			if (rig == null || !follows.visible || arm == Bone.RIGHT_ARM && fingerUp) {
				continue;
			}
			ArmBender bender = arm == Bone.RIGHT_ARM ? bends.right : bends.left;
			poseStack.pushPose();
			applyPose(poseStack, rig.shoulder(), follows);
			List<CharacterModel.Finger> fingers = rig.fingers();
			for (int i = 0; i < fingers.size(); i++) {
				Part finger = fingers.get(i).part();
				int index = i;
				if (finger.vertexCount() > 0) {
					collector.submitCustomGeometry(poseStack, type, bender == null ? (pose, buffer) -> emit(finger, pose, buffer, light, overlay, tint)
						: (pose, buffer) -> timed(finger, () -> bender.emitFinger(index, finger, pose, buffer, light, overlay, tint)));
				}
			}
			Part held = rig.held();
			if (held != null && held.vertexCount() > 0) {
				collector.submitCustomGeometry(poseStack, type, bender == null ? (pose, buffer) -> emit(held, pose, buffer, light, overlay, tint)
					: (pose, buffer) -> timed(held, () -> bender.emitHeld(held, pose, buffer, light, overlay, tint)));
			}
			Part heldGlow = rig.heldGlow();
			if (heldGlow != null && heldGlow.vertexCount() > 0) {
				int bright = LightTexture.FULL_BRIGHT;
				collector.submitCustomGeometry(poseStack, type, bender == null ? (pose, buffer) -> emit(heldGlow, pose, buffer, bright, overlay, tint)
					: (pose, buffer) -> timed(heldGlow, () -> bender.emitHeld(heldGlow, pose, buffer, bright, overlay, tint)));
			}
			poseStack.popPose();
		}
		if (!extras.tails().isEmpty() && playerModel.body.visible) {
			submitTails(model, playerModel, tailMotion(state, model), poseStack, collector, type, light, overlay, tint);
		}
		// Tell the cigarette smoke where the tip is drawn, so it rises from there whatever the character is doing.
		Vector3f smoke = extras.smoke();
		if (smoke != null && playerModel.head.visible) {
			Part head = model.part(Bone.HEAD) != null ? model.part(Bone.HEAD) : model.part(Bone.JAW);
			poseStack.pushPose();
			applyPose(poseStack, head != null ? head.pivot() : new Vector3f(), playerModel.head);
			Vector3f tip = poseStack.last().pose().transformPosition(smoke, new Vector3f());
			poseStack.popPose();
			CigaretteSmoke.drawn(state.id, state.x, state.y, state.z, tip.x, tip.y, tip.z);
		}
	}

	/** This frame's bending of each arm (null for a straight one). */
	private record Bends(@Nullable ArmBender right, @Nullable ArmBender left) {
		@Nullable ArmBender of(Bone bone) {
			return bone == Bone.RIGHT_ARM ? this.right : bone == Bone.LEFT_ARM ? this.left : null;
		}
	}

	/** Runs {@code draw} for a part, counting its corners and the time it takes (for tests). */
	private static void timed(Part part, Runnable draw) {
		long start = System.nanoTime();
		draw.run();
		emittedVertices += part.vertexCount();
		emitNanos += System.nanoTime() - start;
	}

	/** How the tails sway for this player this frame: lazily, quicker while walking, wagging during emotes. */
	public static TailMotion tailMotion(AvatarRenderState state, CharacterModel model) {
		EmotePose emote = ((CharacterRenderState) state).playercharacters$emote();
		float emoteWeight = emote == null ? 0.0F : emote.weight;
		float lift = model.extras().fourLegs() ? CharacterPoses.REAR_UP * 0.85F * emoteWeight : 0.0F;
		lift += 0.7F * ((CharacterRenderState) state).playercharacters$fart(); // out of the way
		float seconds = (state.ageInTicks + (state.id & 255)) / 20.0F;
		return TailMotion.of(seconds, state.walkAnimationSpeed, emoteWeight, lift);
	}

	/** Tails move with the body, then bend by themselves. */
	private static void submitTails(CharacterModel model, PlayerModel playerModel, TailMotion motion, PoseStack poseStack, SubmitNodeCollector collector,
		RenderType type, int light, int overlay, int tint) {
		Part body = model.part(Bone.BODY);
		poseStack.pushPose();
		applyPose(poseStack, body != null ? body.pivot() : new Vector3f(), playerModel.body);
		for (CharacterModel.Tail tail : model.extras().tails()) {
			TailMotion.Table bend = motion.table(tail);
			collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> emitBent(tail.part(), bend, pose, buffer, light, overlay, tint));
			if (tail.glow() != null) {
				collector.submitCustomGeometry(poseStack, type,
					(pose, buffer) -> emitBent(tail.glow(), bend, pose, buffer, LightTexture.FULL_BRIGHT, overlay, tint));
			}
		}
		poseStack.popPose();
	}

	private static void emitBent(Part part, TailMotion.Table bend, PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int tint) {
		long start = System.nanoTime();
		emittedVertices += part.vertexCount();
		Matrix4f matrix = pose.pose();
		Vector3f p = new Vector3f();
		Vector3f n = new Vector3f();
		float[] v = part.vertices();
		int[] colors = part.colors();
		boolean tinted = tint != -1;
		for (int i = 0, count = colors.length; i < count; i++) {
			int o = i * 8;
			p.set(v[o], v[o + 1], v[o + 2]);
			n.set(v[o + 3], v[o + 4], v[o + 5]);
			bend.bend(p, n);
			matrix.transformPosition(p);
			pose.transformNormal(n.x, n.y, n.z, n);
			int color = tinted ? ARGB.multiply(colors[i], tint) : colors[i];
			buffer.addVertex(p.x, p.y, p.z, color, v[o + 6], v[o + 7], overlay, light, n.x, n.y, n.z);
		}
		emitNanos += System.nanoTime() - start;
	}

	private static long emitNanos;
	private static long emittedVertices;
	private static long submits;

	/** How many times a 3D character was drawn in the world or in a menu since the game started (for tests). */
	public static long submitCount() {
		return submits;
	}

	/** Time spent writing 3D characters' corners for the graphics card since the game started, in nanoseconds (for tests). */
	public static long emitNanos() {
		return emitNanos;
	}

	/** How many corners of 3D characters were written for the graphics card since the game started (for tests). */
	public static long emittedVertices() {
		return emittedVertices;
	}

	private static ModelPart follows(PlayerModel playerModel, Bone bone) {
		return switch (bone) {
			case HEAD, JAW -> playerModel.head;
			case BODY -> playerModel.body;
			case RIGHT_ARM -> playerModel.rightArm;
			case LEFT_ARM -> playerModel.leftArm;
			case RIGHT_LEG -> playerModel.rightLeg;
			case LEFT_LEG -> playerModel.leftLeg;
		};
	}

	/**
	 * Draws one part where the player model's matching part is. The jaw turns with the head first (around the head's
	 * joint), then opens around its own hinge.
	 */
	private static void submitPart(CharacterModel model, Bone bone, @Nullable Part part, PlayerModel playerModel, float jawAngle, Bends bends,
		PoseStack poseStack, SubmitNodeCollector collector, RenderType type, int light, int overlay, int tint) {
		ModelPart follows = follows(playerModel, bone);
		// Parts without polygons only provide a joint (like the burger's head joint, which its bun turns with).
		if (part == null || part.vertexCount() == 0 || !follows.visible) {
			return;
		}
		ArmBender bender = bends.of(bone);
		if (bender != null) {
			poseStack.pushPose();
			applyPose(poseStack, part.pivot(), follows);
			collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> timed(part, () -> bender.emitArm(part, pose, buffer, light, overlay, tint)));
			poseStack.popPose();
			return;
		}
		Part jawParent = bone == Bone.JAW ? model.part(Bone.HEAD) : null;
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

	/** Draws one part as it is, with the current pose (for example an arm in first person). */
	public static void submitAlone(CharacterModel model, Part part, PoseStack poseStack, SubmitNodeCollector collector, int light) {
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(model.texture()),
			(pose, buffer) -> emit(part, pose, buffer, light, OverlayTexture.NO_OVERLAY, -1));
	}

	/**
	 * Draws an arm on its own with the current pose (your own arm in first person): bent as {@code armPose} says, with its
	 * fingers and whatever it holds, in their first-person versions where there are some.
	 */
	public static void submitArm(CharacterModel model, Part arm, CharacterModel.@Nullable ArmRig rig, ArmPose armPose, boolean right, boolean firstPerson,
		PoseStack poseStack, SubmitNodeCollector collector, int light) {
		RenderType type = RenderTypes.entityCutout(model.texture());
		int overlay = OverlayTexture.NO_OVERLAY;
		ArmBender bender = ArmBender.of(rig, armPose, right);
		collector.submitCustomGeometry(poseStack, type, bender == null ? (pose, buffer) -> emit(arm, pose, buffer, light, overlay, -1)
			: (pose, buffer) -> timed(arm, () -> bender.emitArm(arm, pose, buffer, light, overlay, -1)));
		if (rig == null) {
			return;
		}
		List<CharacterModel.Finger> fingers = rig.fingers();
		for (int i = 0; i < fingers.size(); i++) {
			CharacterModel.Finger finger = fingers.get(i);
			Part part = firstPerson && finger.firstPerson() != null ? finger.firstPerson() : finger.part();
			int index = i;
			if (part.vertexCount() > 0) {
				collector.submitCustomGeometry(poseStack, type, bender == null ? (pose, buffer) -> emit(part, pose, buffer, light, overlay, -1)
					: (pose, buffer) -> timed(part, () -> bender.emitFinger(index, part, pose, buffer, light, overlay, -1)));
			}
		}
		Part held = firstPerson && rig.heldFirstPerson() != null ? rig.heldFirstPerson() : rig.held();
		if (held != null && held.vertexCount() > 0) {
			collector.submitCustomGeometry(poseStack, type, bender == null ? (pose, buffer) -> emit(held, pose, buffer, light, overlay, -1)
				: (pose, buffer) -> timed(held, () -> bender.emitHeld(held, pose, buffer, light, overlay, -1)));
		}
	}

	private static void emit(CharacterModel.Part part, PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int tint) {
		long start = System.nanoTime();
		emittedVertices += part.vertexCount();
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
		emitNanos += System.nanoTime() - start;
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
	public static void withHandsAtCharacter(CharacterModel model, PlayerModel playerModel, AvatarRenderState state, Runnable submitItems) {
		ModelPart right = playerModel.rightArm;
		ModelPart left = playerModel.leftArm;
		PartPose rightPose = right.storePose();
		PartPose leftPose = left.storePose();
		CharacterRenderState characterState = (CharacterRenderState) state;
		moveHand(model.part(Bone.RIGHT_ARM), right, 1, ArmBender.of(model.extras().rightRig(), characterState.playercharacters$arm(true), true));
		moveHand(model.part(Bone.LEFT_ARM), left, -1, ArmBender.of(model.extras().leftRig(), characterState.playercharacters$arm(false), false));
		try {
			submitItems.run();
		} finally {
			right.loadPose(rightPose);
			left.loadPose(leftPose);
		}
	}

	/**
	 * The item layer puts items at (-side/16, 0.625, -0.125) in the arm's own space. Places (and, for a bent arm, turns)
	 * the arm so that this point lands on the character's hand.
	 */
	private static void moveHand(CharacterModel.@Nullable Part part, ModelPart arm, int side, @Nullable ArmBender bender) {
		if (part == null || part.hand() == null) {
			return;
		}
		PartPose rest = arm.getInitialPose();
		Vector3f shoulder = new Vector3f(part.pivot()).add((arm.x - rest.x()) / 16.0F, (arm.y - rest.y()) / 16.0F, (arm.z - rest.z()) / 16.0F);
		Quaternionf turn = new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot);
		Vector3f hand = bender == null ? new Vector3f(part.hand()) : bender.handPoint(part.hand(), new Vector3f());
		Quaternionf handTurn = new Quaternionf(turn);
		if (bender != null) {
			handTurn.mul(new Quaternionf().setFromNormalized(bender.handTurn()));
			Vector3f angles = handTurn.getEulerAnglesZYX(new Vector3f());
			arm.setRotation(angles.x, angles.y, angles.z);
		}
		Vector3f target = turn.transform(hand.sub(part.pivot())).add(shoulder).sub(handTurn.transform(new Vector3f(-side / 16.0F, 0.625F, -0.125F)));
		arm.x = target.x * 16.0F;
		arm.y = target.y * 16.0F;
		arm.z = target.z * 16.0F;
	}
}
