package com.playercharacters.mixin;

import com.playercharacters.character.PlayerCharacter;
import com.playercharacters.render.CharacterRenderState;
import com.playercharacters.render.FartPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bends the player model over while that player farts. 3D characters copy the player model's pose, held items
 * and armor follow it too, so this one change moves everything together.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void playercharacters$bendOverForFart(AvatarRenderState state, CallbackInfo ci) {
		CharacterRenderState characterState = (CharacterRenderState) state;
		float bend = characterState.playercharacters$fart();
		if (bend > 0.0F) {
			PlayerCharacter character = characterState.playercharacters$character();
			FartPose.bend((PlayerModel) (Object) this, character == null ? null : character.model(), bend);
		}
	}
}
