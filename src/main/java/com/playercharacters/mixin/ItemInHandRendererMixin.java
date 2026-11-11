package com.playercharacters.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.playercharacters.effects.MiddleFingerEmote;
import com.playercharacters.render.FirstPersonHands;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In first person, the middle finger emote takes over the right hand: the fist comes up in front of you. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Inject(
		method = "renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;"
			+ "FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void playercharacters$middleFingerInFirstPerson(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swing,
		ItemStack stack, float equip, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		if (arm != HumanoidArm.RIGHT || player.isScoping()) {
			return;
		}
		float raise = MiddleFingerEmote.raise(player.getUUID(), partialTick);
		if (raise > 0.0F) {
			FirstPersonHands.renderMiddleFinger(poseStack, collector, light, player, raise, player.tickCount + partialTick);
			ci.cancel();
		}
	}
}
