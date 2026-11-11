package com.playercharacters.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.playercharacters.CharacterResolver;
import com.playercharacters.character.CharacterModel;
import com.playercharacters.character.CharacterModel.Bone;
import com.playercharacters.character.CharacterModel.Part;
import com.playercharacters.character.PlayerCharacter;
import com.playercharacters.emote.HandShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Your own hands in first person: a 3D character's own arm instead of Minecraft's, and the middle finger emote
 * held up in front of you, pointing at whoever you look at.
 */
public final class FirstPersonHands {
	/** Where Minecraft's first-person arm has its hand, in model space (pixels): the bottom of the arm. */
	private static final Vector3f PLAYER_RIGHT_SHOULDER = new Vector3f(-5.0F, 2.0F, 0.0F);
	private static final Vector3f PLAYER_HAND_FROM_SHOULDER = new Vector3f(-1.0F, 9.0F, 0.0F);
	/**
	 * Turns a right arm from model space (hanging down, finger pointing forward) to the view: the arm points forward
	 * and a little to the left, at the middle of the screen, and the finger points up.
	 */
	private static final Quaternionf ARM_TO_VIEW = new Quaternionf().setFromNormalized(new Matrix3f(
		-0.917F, 0.0F, 0.399F,
		-0.399F, 0.0F, -0.917F,
		0.0F, -1.0F, 0.0F));

	/** For a middle finger made with the hand's own fingers: how far the elbow bends and the forearm turns (degrees). */
	private static final float GESTURE_ELBOW = 88.0F;
	private static final float GESTURE_TWIST = 90.0F;
	/**
	 * Turns a right arm from model space to the view for that: the upper arm goes into the screen (a little toward the
	 * middle), so the forearm, bent toward the arm's front, stands up.
	 */
	private static final Quaternionf GESTURE_TO_VIEW = gestureToView();

	private static Quaternionf gestureToView() {
		Vector3f upper = new Vector3f(-0.25F, 0.0F, -1.0F).normalize();
		Vector3f back = new Vector3f(0.0F, -1.0F, 0.0F); // the arm's +Z (its back) points down, so its front points up
		Vector3f side = new Vector3f(upper).cross(back);
		return new Quaternionf().setFromNormalized(new Matrix3f(side.x, side.y, side.z, upper.x, upper.y, upper.z, back.x, back.y, back.z));
	}

	/** 3D characters' arms are drawn this much bigger in first person than they are on the character. */
	public static final float ARM_SCALE = 1.6F;
	/** And pushed this far (blocks) out along the arm, so more of the hand shows. */
	private static final float ARM_REACH = 0.07F;

	private static int armsDrawn;
	private static int fingersDrawn;
	private static int lastArmVertices;

	private FirstPersonHands() {
	}

	/** How many times a 3D character's own arm was drawn in first person since the game started. */
	public static int armsDrawn() {
		return armsDrawn;
	}

	/** How many times the middle finger was drawn in first person since the game started. */
	public static int fingersDrawn() {
		return fingersDrawn;
	}

	/**
	 * Draws the local player's 3D character arm where Minecraft would draw its own first-person arm.
	 *
	 * @return false when the player isn't a 3D character, so Minecraft draws its normal arm (skin characters already
	 *         get their skin on it)
	 */
	public static boolean renderCharacterArm(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidArm side) {
		AbstractClientPlayer player = Minecraft.getInstance().player;
		CharacterModel model = modelOf(player);
		if (model == null) {
			return false;
		}
		Part arm = firstPersonArm(model, side);
		if (arm == null || arm.vertexCount() == 0) {
			return true; // a character without arms has no arm to show
		}
		boolean right = side == HumanoidArm.RIGHT;
		float mirror = right ? 1.0F : -1.0F;
		Vector3f hand = arm.hand() != null ? arm.hand() : arm.pivot();
		poseStack.pushPose();
		// Like Minecraft's arm: from the shoulder, turned out a little, then to the hand at the bottom of the arm.
		poseStack.translate(mirror * PLAYER_RIGHT_SHOULDER.x / 16.0F, PLAYER_RIGHT_SHOULDER.y / 16.0F, 0.0F);
		poseStack.mulPose(new Quaternionf().rotationZ(mirror * 0.1F));
		poseStack.translate(mirror * PLAYER_HAND_FROM_SHOULDER.x / 16.0F, PLAYER_HAND_FROM_SHOULDER.y / 16.0F + ARM_REACH, 0.0F);
		poseStack.scale(ARM_SCALE, ARM_SCALE, ARM_SCALE); // bigger than in third person, so the hand shows well
		poseStack.translate(-hand.x, -hand.y, -hand.z);
		ArmPose pose = handPose(player, right);
		lastHandPose.set(pose);
		CharacterModelRenderer.submitArm(model, arm, model.extras().rig(right ? Bone.RIGHT_ARM : Bone.LEFT_ARM), pose, right, true, poseStack, collector, light);
		poseStack.popPose();
		armsDrawn++;
		lastArmVertices = arm.vertexCount();
		return true;
	}

	/** How your own hand looks this frame: relaxed, the fingers moving a little, and a fist while you punch. */
	static ArmPose handPose(AbstractClientPlayer player, boolean right) {
		ArmPose pose = new ArmPose();
		float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float time = (player.tickCount + partialTick) / 20.0F;
		for (int finger = 0; finger < 5; finger++) {
			// A relaxed hand: each finger a little more curled than the one before it, and slowly moving.
			pose.curl[finger] = RELAXED[finger] + 0.05F * (float) Math.sin(time * 1.3F + finger * 0.9F);
		}
		HumanoidArm attacking = player.swingingArm == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		float swing = player.getAttackAnim(partialTick);
		if (swing > 0.0F && attacking == (right ? HumanoidArm.RIGHT : HumanoidArm.LEFT)) {
			pose.shape(HandShape.FIST, Math.min(1.0F, (float) Math.sin(swing * Math.PI) * 2.5F));
		}
		return pose;
	}

	/** Each finger's curl in a relaxed hand, thumb first. */
	private static final float[] RELAXED = {0.08F, 0.12F, 0.17F, 0.22F, 0.28F};
	private static final ArmPose lastHandPose = new ArmPose();

	/** The last first-person hand's pose (for tests). */
	public static ArmPose lastHandPose() {
		return lastHandPose;
	}

	/** The arm to show in first person: the detailed first-person one if the character has it. */
	public static @Nullable Part firstPersonArm(CharacterModel model, HumanoidArm side) {
		CharacterModel.FirstPerson firstPerson = model.extras().firstPerson();
		Part detailed = side == HumanoidArm.RIGHT ? firstPerson.rightArm() : firstPerson.leftArm();
		return detailed != null ? detailed : model.part(side == HumanoidArm.RIGHT ? Bone.RIGHT_ARM : Bone.LEFT_ARM);
	}

	/** How many vertices the last first-person arm had (the detailed arms have more). */
	public static int lastArmVertices() {
		return lastArmVertices;
	}

	/**
	 * Draws the right arm raised in front of you with the middle finger up. {@code raise} goes from 0 (below the
	 * screen) to 1; while it is up, the fist keeps jabbing toward whoever you are looking at.
	 */
	public static void renderMiddleFinger(PoseStack poseStack, SubmitNodeCollector collector, int light, AbstractClientPlayer player, float raise,
		float ageInTicks) {
		fingersDrawn++;
		float jab = raise >= 1.0F ? 0.05F * Math.max(0.0F, (float) Math.sin(ageInTicks * 0.9F)) : 0.0F;
		poseStack.pushPose();
		poseStack.translate(0.27F, -0.95F + 0.7F * raise, -0.5F - 0.17F * raise - jab);
		CharacterModel model = modelOf(player);
		boolean ownFingers = model != null && model.extras().fingerGesture();
		poseStack.mulPose(ownFingers ? GESTURE_TO_VIEW : ARM_TO_VIEW);

		if (ownFingers) {
			// The hand's own fingers: the forearm standing up in front of you from below the screen, the palm toward
			// you, only the middle finger out. The bent hand goes where the hand would be; the arm reaches down from it.
			Part arm = firstPersonArm(model, HumanoidArm.RIGHT);
			if (arm != null) {
				ArmPose pose = new ArmPose();
				pose.blend(GESTURE_ELBOW, GESTURE_TWIST, HandShape.MIDDLE_FINGER, 1.0F);
				lastHandPose.set(pose);
				Vector3f hand = arm.hand() != null ? arm.hand() : arm.pivot();
				ArmBender bender = ArmBender.of(model.extras().rightRig(), pose, true);
				Vector3f bentHand = bender == null ? new Vector3f(hand) : bender.handPoint(hand, new Vector3f());
				poseStack.translate(0.0F, 0.1F, 0.0F); // the finger reaches up out of the hand, so the hand sits a little higher
				poseStack.scale(ARM_SCALE, ARM_SCALE, ARM_SCALE);
				poseStack.translate(-bentHand.x, -bentHand.y, -bentHand.z);
				CharacterModelRenderer.submitArm(model, arm, model.extras().rightRig(), pose, true, true, poseStack, collector, light);
			}
		} else if (model != null) {
			Part arm = model.extras().firstPerson().middleFinger() != null ? model.extras().firstPerson().middleFinger()
				: model.extras().middleFinger() != null ? model.extras().middleFinger() : firstPersonArm(model, HumanoidArm.RIGHT);
			if (arm != null) {
				Vector3f hand = arm.hand() != null ? arm.hand() : arm.pivot();
				poseStack.scale(ARM_SCALE, ARM_SCALE, ARM_SCALE);
				poseStack.translate(-hand.x, -hand.y, -hand.z);
				CharacterModelRenderer.submitAlone(model, arm, poseStack, collector, light);
			}
		} else {
			// Minecraft's own arm, in the player's (or skin character's) skin, with a finger added.
			PlayerModel playerModel = Minecraft.getInstance().getEntityRenderDispatcher().getPlayerRenderer(player).getModel();
			Vector3f hand = new Vector3f(PLAYER_RIGHT_SHOULDER).add(PLAYER_HAND_FROM_SHOULDER).div(16.0F);
			poseStack.translate(-hand.x, -hand.y, -hand.z);
			playerModel.rightArm.resetPose();
			playerModel.rightArm.visible = true;
			playerModel.rightSleeve.visible = player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
			Identifier skin = player.getSkin().body().texturePath();
			collector.submitModelPart(playerModel.rightArm, poseStack, RenderTypes.entityTranslucent(skin), light, OverlayTexture.NO_OVERLAY, null);
			poseStack.pushPose();
			playerModel.rightArm.translateAndRotate(poseStack);
			VanillaEmoteParts.submitFinger(poseStack, collector, skin, player.getSkin().model() == PlayerModelType.SLIM, light, OverlayTexture.NO_OVERLAY);
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	private static @Nullable CharacterModel modelOf(@Nullable AbstractClientPlayer player) {
		if (player == null) {
			return null;
		}
		PlayerCharacter character = CharacterResolver.characterFor(player.getUUID());
		return character == null ? null : character.model();
	}
}
