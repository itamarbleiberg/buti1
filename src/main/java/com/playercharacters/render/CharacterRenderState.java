package com.playercharacters.render;

import com.playercharacters.character.PlayerCharacter;
import com.playercharacters.emote.EmotePose;
import org.jspecify.annotations.Nullable;

/** Extra data added to the player render state (see {@code AvatarRenderStateMixin}). */
public interface CharacterRenderState {
	/** The character this player is drawn as, or null for the normal player. */
	@Nullable PlayerCharacter playercharacters$character();

	void playercharacters$setCharacter(@Nullable PlayerCharacter character);

	/** How far the jaw is open, from 0 (closed) to 1 (fully open). */
	float playercharacters$jaw();

	void playercharacters$setJaw(float jaw);

	/** How far the player is bent over while farting, from 0 (standing up straight) to about 1. */
	float playercharacters$fart();

	void playercharacters$setFart(float fart);

	/** How far the right arm is raised for the middle finger, from 0 to 1. */
	float playercharacters$middleFinger();

	void playercharacters$setMiddleFinger(float raise);

	/** Whether the character shows its angry face. */
	boolean playercharacters$mad();

	void playercharacters$setMad(boolean mad);

	/** Whether the eyes are closed for a blink this frame. */
	boolean playercharacters$blink();

	void playercharacters$setBlink(boolean blink);

	/** This frame's emote pose, or null when the player isn't doing an emote. */
	@Nullable EmotePose playercharacters$emote();

	void playercharacters$setEmote(@Nullable EmotePose pose);

	/** How the right (or left) arm's elbow and fingers are bent this frame. Set when the player model is posed. */
	ArmPose playercharacters$arm(boolean right);
}
