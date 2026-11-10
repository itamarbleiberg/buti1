package com.playercharacters.character;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.entity.player.PlayerModelType;
import org.jspecify.annotations.Nullable;

/**
 * Optional "<character>.json" file next to a character's PNG, for example:
 * <pre>
 * {
 *   "name": "Frog",
 *   "model": "slim",
 *   "mouth": { "x": 1, "y": 5, "width": 6, "height": 2 }
 * }
 * </pre>
 * Every field is optional.
 */
final class CharacterOptions {
	private static final Gson GSON = new Gson();

	@Nullable String name;
	/** "slim" (3 pixel arms) or "wide" (4 pixel arms). Detected from the skin when missing. */
	@Nullable String model;
	@Nullable Mouth mouth;

	/** Where the automatic open mouth is drawn, in pixels on the 8x8 face (0,0 = top left). */
	static final class Mouth {
		int x = 3;
		int y = 6;
		int width = 2;
		int height = 2;
	}

	Mouth mouthOrDefault() {
		return this.mouth == null ? new Mouth() : this.mouth;
	}

	@Nullable PlayerModelType modelType() {
		if (this.model == null) {
			return null;
		}
		return switch (this.model.toLowerCase(Locale.ROOT)) {
			case "slim", "alex" -> PlayerModelType.SLIM;
			case "wide", "classic", "default", "steve" -> PlayerModelType.WIDE;
			default -> null;
		};
	}

	/** Reads the options file if it exists. Problems are added to {@code problems} and defaults are used instead. */
	static CharacterOptions read(Path file, List<String> problems) {
		if (!Files.isRegularFile(file)) {
			return new CharacterOptions();
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			CharacterOptions options = GSON.fromJson(reader, CharacterOptions.class);
			return options == null ? new CharacterOptions() : options;
		} catch (IOException | JsonParseException e) {
			problems.add(file.getFileName() + ": couldn't read it (" + e.getMessage() + "), using default settings");
			return new CharacterOptions();
		}
	}
}
