package com.playercharacters.config;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * What a player should look like.
 *
 * @param kind        follow the default, keep their own skin, a random character, or one specific character
 * @param characterId the character's id (its file name without ".png"), only set for {@link Kind#CHARACTER}
 */
public record Choice(Kind kind, @Nullable String characterId) {
	public enum Kind {
		/** Use whatever the "Default" setting says. Only used for individual players. */
		DEFAULT,
		OWN_SKIN,
		RANDOM,
		CHARACTER
	}

	public static final Choice DEFAULT = new Choice(Kind.DEFAULT, null);
	public static final Choice OWN_SKIN = new Choice(Kind.OWN_SKIN, null);
	public static final Choice RANDOM = new Choice(Kind.RANDOM, null);

	private static final String CHARACTER_PREFIX = "character:";

	public static Choice character(String characterId) {
		return new Choice(Kind.CHARACTER, characterId);
	}

	/** Text form used in the config file. */
	public String encode() {
		return switch (this.kind) {
			case DEFAULT -> "default";
			case OWN_SKIN -> "own";
			case RANDOM -> "random";
			case CHARACTER -> CHARACTER_PREFIX + this.characterId;
		};
	}

	public static Choice decode(@Nullable String value, Choice fallback) {
		if (value == null) {
			return fallback;
		}
		if (value.startsWith(CHARACTER_PREFIX) && value.length() > CHARACTER_PREFIX.length()) {
			return character(value.substring(CHARACTER_PREFIX.length()));
		}
		return switch (value.toLowerCase(Locale.ROOT)) {
			case "default" -> DEFAULT;
			case "own" -> OWN_SKIN;
			case "random" -> RANDOM;
			default -> fallback;
		};
	}
}
