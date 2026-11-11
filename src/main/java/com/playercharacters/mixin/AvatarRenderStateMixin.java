package com.playercharacters.mixin;

import com.playercharacters.character.PlayerCharacter;
import com.playercharacters.emote.EmotePose;
import com.playercharacters.render.ArmPose;
import com.playercharacters.render.CharacterRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Lets each player's render state remember which character it is drawn as, how its jaw, arms and face are posed, and its emote. */
@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements CharacterRenderState {
	@Unique
	private @Nullable PlayerCharacter playercharacters$character;
	@Unique
	private float playercharacters$jaw;
	@Unique
	private float playercharacters$fart;
	@Unique
	private float playercharacters$middleFinger;
	@Unique
	private boolean playercharacters$mad;
	@Unique
	private @Nullable EmotePose playercharacters$emote;
	@Unique
	private boolean playercharacters$blink;
	@Unique
	private final ArmPose playercharacters$rightArm = new ArmPose();
	@Unique
	private final ArmPose playercharacters$leftArm = new ArmPose();

	@Override
	public ArmPose playercharacters$arm(boolean right) {
		return right ? this.playercharacters$rightArm : this.playercharacters$leftArm;
	}

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

	@Override
	public float playercharacters$middleFinger() {
		return this.playercharacters$middleFinger;
	}

	@Override
	public void playercharacters$setMiddleFinger(float raise) {
		this.playercharacters$middleFinger = raise;
	}

	@Override
	public boolean playercharacters$mad() {
		return this.playercharacters$mad;
	}

	@Override
	public void playercharacters$setMad(boolean mad) {
		this.playercharacters$mad = mad;
	}

	@Override
	public boolean playercharacters$blink() {
		return this.playercharacters$blink;
	}

	@Override
	public void playercharacters$setBlink(boolean blink) {
		this.playercharacters$blink = blink;
	}

	@Override
	public @Nullable EmotePose playercharacters$emote() {
		return this.playercharacters$emote;
	}

	@Override
	public void playercharacters$setEmote(@Nullable EmotePose pose) {
		this.playercharacters$emote = pose;
	}
}
