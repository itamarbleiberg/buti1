package com.playercharacters.mixin;

import com.playercharacters.character.PlayerCharacter;
import com.playercharacters.render.CharacterRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Lets each player's render state remember which character it is drawn as, how open its jaw is and how far it bends over. */
@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements CharacterRenderState {
	@Unique
	private @Nullable PlayerCharacter playercharacters$character;
	@Unique
	private float playercharacters$jaw;
	@Unique
	private float playercharacters$fart;

	@Override
	public @Nullable PlayerCharacter playercharacters$character() {
		return this.playercharacters$character;
	}

	@Override
	public void playercharacters$setCharacter(@Nullable PlayerCharacter character) {
		this.playercharacters$character = character;
	}

	@Override
	public float playercharacters$jaw() {
		return this.playercharacters$jaw;
	}

	@Override
	public void playercharacters$setJaw(float jaw) {
		this.playercharacters$jaw = jaw;
	}

	@Override
	public float playercharacters$fart() {
		return this.playercharacters$fart;
	}

	@Override
	public void playercharacters$setFart(float fart) {
		this.playercharacters$fart = fart;
	}
}
