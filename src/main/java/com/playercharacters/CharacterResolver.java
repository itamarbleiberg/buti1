package com.playercharacters;

import com.playercharacters.character.CharacterLibrary;
import com.playercharacters.character.CharacterSkin;
import com.playercharacters.config.CharacterSettings;
import com.playercharacters.config.Choice;
import com.playercharacters.voice.TalkTracker;
import java.util.UUID;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

/** Decides which character (if any) a player is shown as. */
public final class CharacterResolver {
	private CharacterResolver() {
	}

	/** The character this player currently appears as, or null for their own skin. */
	public static @Nullable CharacterSkin characterFor(UUID player) {
		return resolve(CharacterSettings.get().choiceFor(player), player);
	}

	/** What {@code choice} means for {@code player}. {@link Choice#DEFAULT} follows the default setting. */
	public static @Nullable CharacterSkin resolve(Choice choice, UUID player) {
		if (choice.kind() == Choice.Kind.DEFAULT) {
			choice = CharacterSettings.get().defaultChoice();
		}
		return switch (choice.kind()) {
			case DEFAULT, OWN_SKIN -> null;
			case RANDOM -> CharacterLibrary.randomFor(player);
			case CHARACTER -> CharacterLibrary.get(choice.characterId());
		};
	}

	/** The skin to render this player with, or null to keep their real skin. Called every frame. */
	public static @Nullable PlayerSkin skinFor(UUID player) {
		CharacterSkin character = characterFor(player);
		if (character == null) {
			return null;
		}
		boolean mouthOpen = CharacterSettings.get().talkingMouths() && TalkTracker.isMouthOpen(player);
		return character.skin(mouthOpen);
	}
}
