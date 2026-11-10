package com.playercharacters.mixin;

import com.playercharacters.CharacterResolver;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Swaps the skin of players in the world. The player renderer, the first-person hand and the
 * inventory preview all read the skin through this method, while the tab list and menus use the
 * real skin, so you can still tell who is who.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
	private void playercharacters$useCharacterSkin(CallbackInfoReturnable<PlayerSkin> cir) {
		PlayerSkin skin = CharacterResolver.skinFor(((AbstractClientPlayer) (Object) this).getUUID());
		if (skin != null) {
			cir.setReturnValue(skin);
		}
	}
}
