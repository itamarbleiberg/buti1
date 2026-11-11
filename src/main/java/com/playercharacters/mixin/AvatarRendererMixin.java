package com.playercharacters.mixin;

import com.playercharacters.CharacterResolver;
import com.playercharacters.character.PlayerCharacter;
import com.playercharacters.effects.FartEffects;
import com.playercharacters.effects.MiddleFingerEmote;
import com.mojang.blaze3d.vertex.PoseStack;
import com.playercharacters.emote.Emote;
import com.playercharacters.emote.EmotePlayer;
import com.playercharacters.emote.EmotePose;
import com.playercharacters.emote.Emotes;
import com.playercharacters.render.CharacterPoses;
import com.playercharacters.render.CharacterRenderState;
import com.playercharacters.render.FirstPersonHands;
import com.playercharacters.render.JawAnimator;
import java.util.UUID;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Each frame, records on the player's render state which character to draw, how far its jaw is open, how far it
 * bends over and whether it is showing the middle finger. Also swaps in a 3D character's own arm in first person.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL")
	)
	private void playercharacters$rememberCharacter(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		CharacterRenderState characterState = (CharacterRenderState) state;
		if (avatar instanceof AbstractClientPlayer player) {
			UUID id = player.getUUID();
			PlayerCharacter character = CharacterResolver.characterFor(id);
			characterState.playercharacters$setCharacter(character);
			EmotePose emote = EmotePlayer.pose(id, partialTick);
			characterState.playercharacters$setEmote(emote);
			float jaw = character != null && character.isModel() ? JawAnimator.jawFor(id) : 0.0F;
			if (emote != null && character != null && character.isModel()) {
				jaw = Math.max(jaw, emote.jaw * emote.weight); // laughing, shouting, "six seven"...
			}
			characterState.playercharacters$setJaw(jaw);
			float fart = FartEffects.bend(id, partialTick);
			float raise = MiddleFingerEmote.raise(id, partialTick);
			boolean mad = MiddleFingerEmote.isMad(id) || emote != null && emote.mad && emote.weight > 0.5F;
			// The emote wheel shows the fart and the middle finger too.
			Emote previewed = EmotePlayer.previewEmote(id);
			if (previewed == Emotes.FART) {
				fart = FartEffects.bendAt(EmotePlayer.previewSeconds() % 1.8F);
			} else if (previewed == Emotes.MIDDLE_FINGER) {
				float t = EmotePlayer.previewSeconds();
				raise = Math.min(1.0F, t / 0.25F) * (1.0F - Math.max(0.0F, Math.min(1.0F, (t - 2.5F) / 0.4F)));
				mad = true;
			}
			characterState.playercharacters$setFart(fart);
			characterState.playercharacters$setMiddleFinger(raise);
			characterState.playercharacters$setMad(mad);
			characterState.playercharacters$setBlink(character != null && character.isModel() && CharacterPoses.isBlinking(id, player.tickCount + partialTick));
			if (raise > 0.0F) {
				state.rightHandItemState.clear(); // the hand is busy
			}
			if (emote != null && emote.weight > 0.3F) {
				state.rightHandItemState.clear(); // dancing needs both hands
				state.leftHandItemState.clear();
			}
		} else {
			characterState.playercharacters$setCharacter(null);
			characterState.playercharacters$setJaw(0.0F);
			characterState.playercharacters$setFart(0.0F);
			characterState.playercharacters$setMiddleFinger(0.0F);
			characterState.playercharacters$setMad(false);
			characterState.playercharacters$setEmote(null);
			characterState.playercharacters$setBlink(false);
		}
	}

	/** Emotes can move the whole character: jumping, spinning, lying down, sitting. 3D characters also bob as they walk. */
	@Inject(
		method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
		at = @At("TAIL")
	)
	private void playercharacters$moveForEmote(AvatarRenderState state, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
		CharacterRenderState characterState = (CharacterRenderState) state;
		PlayerCharacter character = characterState.playercharacters$character();
		if (character != null && character.model() != null && !state.isFallFlying && state.swimAmount <= 0.0F) {
			CharacterPoses.walkBounce(poseStack, character.model(), state.walkAnimationPos, state.walkAnimationSpeed);
		}
		EmotePose emote = characterState.playercharacters$emote();
		if (emote != null && emote.weight > 0.0F) {
			CharacterPoses.moveWhole(poseStack, emote, character == null ? null : character.model());
		}
	}

	/** In first person, a 3D character shows its own arm instead of Minecraft's. */
	@Inject(
		method = "renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void playercharacters$characterRightHand(PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier skin, boolean sleeve,
		CallbackInfo ci) {
		if (FirstPersonHands.renderCharacterArm(poseStack, collector, light, HumanoidArm.RIGHT)) {
			ci.cancel();
		}
	}

	@Inject(
		method = "renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void playercharacters$characterLeftHand(PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier skin, boolean sleeve,
		CallbackInfo ci) {
		if (FirstPersonHands.renderCharacterArm(poseStack, collector, light, HumanoidArm.LEFT)) {
			ci.cancel();
		}
	}
}
