package com.playercharacters.render;

import com.playercharacters.character.PlayerCharacter;
import org.jspecify.annotations.Nullable;

/** Extra data added to the player render state (see {@code AvatarRenderStateMixin}). */
public interface CharacterRenderState {
	/** The character this player is drawn as, or null for the normal player. */
	@Nullable PlayerCharacter playercharacters$character();

	void playercharacters$setCharacter(@Nullable PlayerCharacter character);

	/** How far the jaw is open, from 0 (closed) to 1 (fully open). */
	float playercharacters$jaw();

	void playercharacters$setJaw(float jaw);
}
