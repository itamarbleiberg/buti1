package com.playercharacters.mixin;

import com.playercharacters.character.CharacterModel;
import com.playercharacters.character.PlayerCharacter;
import com.playercharacters.emote.EmotePose;
import com.playercharacters.render.CharacterPoses;
import com.playercharacters.render.CharacterRenderState;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds this mod's poses after Minecraft posed the player model: four-legged walking, rifle aiming, head turning and
 * waddling for some characters, emotes, the middle finger, and bending over to fart. 3D characters copy the player
 * model's pose, and held items and armor follow it too, so these changes move everything together.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void playercharacters$addPoses(AvatarRenderState state, CallbackInfo ci) {
		PlayerModel model = (PlayerModel) (Object) this;
		CharacterRenderState characterState = (CharacterRenderState) state;
		PlayerCharacter character = characterState.playercharacters$character();
		CharacterModel characterModel = character == null ? null : character.model();
		if (characterModel != null) {
			if (characterModel.extras().fourLegs()) {
				CharacterPoses.fourLegs(model, state);
			} else if (characterModel.extras().arms() == CharacterModel.Arms.RIFLE && !state.isFallFlying && state.swimAmount <= 0.0F) {
				CharacterPoses.rifle(model);
			}
			CharacterPoses.headTurn(model, characterModel);
			CharacterPoses.alive(model, state.ageInTicks, characterState.playercharacters$jaw(), state.id);
		}
		EmotePose emote = characterState.playercharacters$emote();
		if (emote != null && emote.weight > 0.0F) {
			CharacterPoses.emote(model, characterModel, emote, state.walkAnimationSpeed);
		}
		float raise = characterState.playercharacters$middleFinger();
		if (raise > 0.0F) {
			CharacterPoses.middleFinger(model, raise, state.ageInTicks, characterModel != null && characterModel.extras().fingerGesture());
		}
		float bend = characterState.playercharacters$fart();
		if (bend > 0.0F) {
			CharacterPoses.fartBend(model, characterModel, bend);
		}
		if (characterModel != null) {
			CharacterPoses.waddle(model, characterModel, state.walkAnimationPos, state.walkAnimationSpeed);
		}
		CharacterPoses.arms(model, state, characterModel);
	}
}
