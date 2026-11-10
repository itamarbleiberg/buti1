package com.playercharacters.character;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

/**
 * A character a player can be shown as. Either a 3D model ({@link #model} set) or a Minecraft skin
 * ({@link #idleSkin} and {@link #talkingSkin} set).
 *
 * @param id                 file or folder name, used in the config to remember choices
 * @param displayName        name shown in menus
 * @param model              the 3D model, or null for skin characters
 * @param idleSkin           skin characters: the normal skin
 * @param talkingSkin        skin characters: the same skin with the mouth open
 * @param customTalkingFrame skin characters: the mouth-open frame came from a "_talking.png" file
 * @param icon               64x64 picture for menus, or null to draw the skin's face (or a letter)
 */
public record PlayerCharacter(
	String id,
	String displayName,
	@Nullable CharacterModel model,
	@Nullable PlayerSkin idleSkin,
	@Nullable PlayerSkin talkingSkin,
	boolean customTalkingFrame,
	@Nullable Identifier icon
) {
	public static PlayerCharacter ofSkin(String id, String displayName, PlayerSkin idle, PlayerSkin talking, boolean customTalkingFrame) {
		return new PlayerCharacter(id, displayName, null, idle, talking, customTalkingFrame, null);
	}

	public static PlayerCharacter ofModel(String id, String displayName, CharacterModel model, @Nullable Identifier icon) {
		return new PlayerCharacter(id, displayName, model, null, null, false, icon);
	}

	public boolean isModel() {
		return this.model != null;
	}

	/** Skin characters only: the skin to draw, depending on whether the mouth is open. */
	public @Nullable PlayerSkin skin(boolean mouthOpen) {
		return mouthOpen ? this.talkingSkin : this.idleSkin;
	}
}
