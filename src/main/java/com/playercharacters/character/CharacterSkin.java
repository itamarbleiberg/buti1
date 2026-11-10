package com.playercharacters.character;

import net.minecraft.world.entity.player.PlayerSkin;

/**
 * A loaded character.
 *
 * @param id                 file name without ".png", used in the config to remember choices
 * @param displayName        name shown in menus
 * @param idleSkin           the normal skin
 * @param talkingSkin        the same skin with the mouth open
 * @param customTalkingFrame true if the mouth-open frame came from a "_talking.png" file instead of being generated
 */
public record CharacterSkin(String id, String displayName, PlayerSkin idleSkin, PlayerSkin talkingSkin, boolean customTalkingFrame) {
	public PlayerSkin skin(boolean mouthOpen) {
		return mouthOpen ? this.talkingSkin : this.idleSkin;
	}
}
