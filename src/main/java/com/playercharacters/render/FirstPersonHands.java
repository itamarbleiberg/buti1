package com.playercharacters.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.playercharacters.CharacterResolver;
import com.playercharacters.character.CharacterModel;
import com.playercharacters.character.CharacterModel.Bone;
import com.playercharacters.character.CharacterModel.Part;
import com.playercharacters.character.PlayerCharacter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
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

	private static int armsDrawn;
	private static int fingersDrawn;

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
		Part arm = model.part(side == HumanoidArm.RIGHT ? Bone.RIGHT_ARM : Bone.LEFT_ARM);
		if (arm == null || arm.vertexCount() == 0) {
			return true; // a character without arms has no arm to show
		}
		float mirror = side == HumanoidArm.RIGHT ? 1.0F : -1.0F;
		Vector3f hand = arm.hand() != null ? arm.hand() : arm.pivot();
		poseStack.pushPose();
		// Like Minecraft's arm: from the shoulder, turned out a little, then to the hand at the bottom of the arm.
		poseStack.translate(mirror * PLAYER_RIGHT_SHOULDER.x / 16.0F, PLAYER_RIGHT_SHOULDER.y / 16.0F, 0.0F);
		poseStack.mulPose(new Quaternionf().rotationZ(mirror * 0.1F));
		poseStack.translate(mirror * PLAYER_HAND_FROM_SHOULDER.x / 16.0F, PLAYER_HAND_FROM_SHOULDER.y / 16.0F, 0.0F);
		poseStack.translate(-hand.x, -hand.y, -hand.z);
		CharacterModelRenderer.submitAlone(model, arm, poseStack, collector, light);
		poseStack.popPose();
		armsDrawn++;
		return true;
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
		poseStack.mulPose(ARM_TO_VIEW);

		CharacterModel model = modelOf(player);
		if (model != null) {
			Part arm = model.extras().middleFinger() != null ? model.extras().middleFinger() : model.part(Bone.RIGHT_ARM);
			if (arm != null) {
				Vector3f hand = arm.hand() != null ? arm.hand() : arm.pivot();
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
