package com.playercharacters.mixin;

import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the cigarette smoke start see-through, like real smoke. */
@Mixin(SingleQuadParticle.class)
public interface SingleQuadParticleAccessor {
	@Invoker("setAlpha")
	void playercharacters$setAlpha(float alpha);
}
