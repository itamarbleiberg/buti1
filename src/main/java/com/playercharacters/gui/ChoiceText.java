package com.playercharacters.gui;

import com.playercharacters.CharacterResolver;
import com.playercharacters.character.CharacterLibrary;
import com.playercharacters.character.PlayerCharacter;
import com.playercharacters.config.CharacterSettings;
import com.playercharacters.config.Choice;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Button labels describing a {@link Choice}. */
final class ChoiceText {
	private ChoiceText() {
	}

	/** Label for a player's row, e.g. "Robot", "Own skin", "Random: Frog" or "Default: Robot". */
	static Component forPlayer(Choice choice, UUID player) {
		return switch (choice.kind()) {
			case DEFAULT -> Component.translatable("playercharacters.choice.default", result(choice, player));
			case OWN_SKIN -> Component.translatable("playercharacters.choice.own");
			case RANDOM -> Component.translatable("playercharacters.choice.random_is", result(choice, player));
			case CHARACTER -> character(choice.characterId());
		};
	}

	/** Label for the "Default" button, e.g. "Default: Random". */
	static Component forDefault() {
		Choice choice = CharacterSettings.get().defaultChoice();
		Component value = switch (choice.kind()) {
			case DEFAULT, OWN_SKIN -> Component.translatable("playercharacters.choice.own");
			case RANDOM -> Component.translatable("playercharacters.choice.random");
			case CHARACTER -> character(choice.characterId());
		};
		return Component.translatable("playercharacters.menu.default", value);
	}

	/** What the player ends up looking like: a character name or "own skin". */
	static Component result(Choice choice, UUID player) {
		PlayerCharacter character = CharacterResolver.resolve(choice, player);
		return character != null
			? Component.literal(character.displayName())
			: Component.translatable("playercharacters.choice.own_lower");
	}

	/** A character's name, or a warning if its file was removed. */
	static Component character(String id) {
		PlayerCharacter character = CharacterLibrary.get(id);
		return character != null
			? Component.literal(character.displayName())
			: Component.translatable("playercharacters.choice.missing", id).withStyle(ChatFormatting.YELLOW);
	}
}
