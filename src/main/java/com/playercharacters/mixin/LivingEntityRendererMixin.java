package com.playercharacters.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.playercharacters.character.PlayerCharacter;
import com.playercharacters.render.CharacterModelRenderer;
import com.playercharacters.render.VanillaEmoteParts;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * For players shown as characters: draws the 3D model instead of the player model, and leaves out their armor.
 * The name tag is drawn later by the entity renderer and is not affected. Held items stay, moved to the
 * character's hands. Players drawn with the normal model get a finger and eyebrows for the middle finger emote.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	private static final String SUBMIT =
		"submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V";

	@Shadow
	protected EntityModel<?> model;

	@WrapOperation(
		method = SUBMIT,
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;"
				+ "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;"
				+ "ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"
		)
	)
	private void playercharacters$drawCharacterModel(SubmitNodeCollector collector, Model<?> model, Object state, PoseStack poseStack, RenderType renderType,
		int light, int overlay, int tint, TextureAtlasSprite sprite, int outlineColor, ModelFeatureRenderer.CrumblingOverlay crumbling, Operation<Void> original) {
		PlayerCharacter character = CharacterModelRenderer.characterOf(state);
		if (character != null && character.model() != null && this.model instanceof PlayerModel playerModel && state instanceof AvatarRenderState avatarState) {
			CharacterModelRenderer.submit(character.model(), playerModel, avatarState, poseStack, collector, light, overlay, tint);
		} else {
			original.call(collector, model, state, poseStack, renderType, light, overlay, tint, sprite, outlineColor, crumbling);
			if (this.model instanceof PlayerModel playerModel && state instanceof AvatarRenderState avatarState) {
				VanillaEmoteParts.submit(playerModel, avatarState, poseStack, collector, light, overlay);
			}
		}
	}

	@WrapOperation(
		method = SUBMIT,
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;submit(Lcom/mojang/blaze3d/vertex/PoseStack;"
				+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/EntityRenderState;FF)V"
		)
	)
	private void playercharacters$filterLayers(RenderLayer<?, ?> layer, PoseStack poseStack, SubmitNodeCollector collector, int light,
		EntityRenderState state, float yRot, float xRot, Operation<Void> original) {
		PlayerCharacter character = CharacterModelRenderer.characterOf(state);
		if (character == null) {
			original.call(layer, poseStack, collector, light, state, yRot, xRot);
			return;
		}
		if (CharacterModelRenderer.isWornEquipment(layer)) {
			return;
		}
		if (character.model() != null) {
			if (CharacterModelRenderer.isAttachedToPlayerBody(layer)) {
				return;
			}
			if (layer instanceof ItemInHandLayer<?, ?> && character.model().extras().hideHeldItems()) {
				return; // the hands are already holding something (like a rifle), or they are paws
			}
			if (layer instanceof ItemInHandLayer<?, ?> && this.model instanceof PlayerModel playerModel) {
				CharacterModelRenderer.withHandsAtCharacter(character.model(), playerModel,
					() -> original.call(layer, poseStack, collector, light, state, yRot, xRot));
				return;
			}
		}
		original.call(layer, poseStack, collector, light, state, yRot, xRot);
	}
}
