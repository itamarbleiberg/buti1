package com.playercharacters.mixin;

import com.playercharacters.CharacterResolver;
import com.playercharacters.character.PlayerCharacter;
import com.playercharacters.effects.FartEffects;
import com.playercharacters.effects.MiddleFingerEmote;
import com.playercharacters.render.CharacterRenderState;
import com.playercharacters.render.JawAnimator;
import java.util.UUID;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Each frame, records on the player's render state which character to draw, how far its jaw is open, how far it
 * bends over and whether it is showing the middle finger.
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
			characterState.playercharacters$setJaw(character != null && character.isModel() ? JawAnimator.jawFor(id) : 0.0F);
			characterState.playercharacters$setFart(FartEffects.bend(id, partialTick));
			float raise = MiddleFingerEmote.raise(id, partialTick);
			characterState.playercharacters$setMiddleFinger(raise);
			characterState.playercharacters$setMad(MiddleFingerEmote.isMad(id));
			if (raise > 0.0F) {
				state.rightHandItemState.clear(); // the hand is busy
			}
		} else {
			characterState.playercharacters$setCharacter(null);
			characterState.playercharacters$setJaw(0.0F);
			characterState.playercharacters$setFart(0.0F);
			characterState.playercharacters$setMiddleFinger(0.0F);
			characterState.playercharacters$setMad(false);
		}
	}
}
