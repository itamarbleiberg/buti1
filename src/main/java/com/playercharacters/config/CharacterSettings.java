package com.playercharacters.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.playercharacters.PlayerCharactersClient;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Who is which character, the default for everyone else, and whether talking mouths and the emotes are on.
 * Saved to {@code config/playercharacters.json} after every change.
 */
public final class CharacterSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final CharacterSettings INSTANCE = new CharacterSettings();

	private final Path file = FabricLoader.getInstance().getConfigDir().resolve(PlayerCharactersClient.MOD_ID + ".json");
	private final Map<UUID, Choice> choices = new ConcurrentHashMap<>();
	private final Map<UUID, String> playerNames = new ConcurrentHashMap<>();
	private volatile boolean talkingMouths = true;
	private volatile boolean fartDetection = true;
	private volatile boolean middleFinger = true;
	private volatile Choice defaultChoice = Choice.OWN_SKIN;

	private CharacterSettings() {
	}

	public static CharacterSettings get() {
		return INSTANCE;
	}

	public boolean talkingMouths() {
		return this.talkingMouths;
	}

	public void setTalkingMouths(boolean enabled) {
		this.talkingMouths = enabled;
		this.save();
	}

	/** Whether saying (or typing) "fart" makes that player's character fart. */
	public boolean fartDetection() {
		return this.fartDetection;
	}

	public void setFartDetection(boolean enabled) {
		this.fartDetection = enabled;
		this.save();
	}

	/** Whether saying (or typing) "fuck you" makes that player's character show the middle finger. */
	public boolean middleFinger() {
		return this.middleFinger;
	}

	public void setMiddleFinger(boolean enabled) {
		this.middleFinger = enabled;
		this.save();
	}

	/** What players without their own choice look like. Never {@link Choice.Kind#DEFAULT}. */
	public Choice defaultChoice() {
		return this.defaultChoice;
	}

	public void setDefaultChoice(Choice choice) {
		this.defaultChoice = choice.kind() == Choice.Kind.DEFAULT ? Choice.OWN_SKIN : choice;
		this.save();
	}

	/** The choice made for this player, or {@link Choice#DEFAULT} if none was made. */
	public Choice choiceFor(UUID player) {
		return this.choices.getOrDefault(player, Choice.DEFAULT);
	}

	public void setChoice(UUID player, String playerName, Choice choice) {
		if (choice.kind() == Choice.Kind.DEFAULT) {
			this.choices.remove(player);
			this.playerNames.remove(player);
		} else {
			this.choices.put(player, choice);
			this.playerNames.put(player, playerName);
		}
		this.save();
	}

	public void load() {
		if (!Files.exists(this.file)) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
			Data data = GSON.fromJson(reader, Data.class);
			if (data == null) {
				return;
			}
			this.talkingMouths = data.talkingMouths;
			this.fartDetection = data.fartDetection;
			this.middleFinger = data.middleFinger;
			this.defaultChoice = Choice.decode(data.defaultCharacter, Choice.OWN_SKIN);
			if (this.defaultChoice.kind() == Choice.Kind.DEFAULT) {
				this.defaultChoice = Choice.OWN_SKIN;
			}

			this.choices.clear();
			this.playerNames.clear();
			if (data.players != null) {
				data.players.forEach((key, player) -> {
					UUID id = parseUuid(key);
					Choice choice = player == null ? Choice.DEFAULT : Choice.decode(player.character, Choice.DEFAULT);
					if (id != null && choice.kind() != Choice.Kind.DEFAULT) {
						this.choices.put(id, choice);
						this.playerNames.put(id, player.name == null ? "" : player.name);
					}
				});
			}
		} catch (IOException | JsonParseException e) {
			PlayerCharactersClient.LOGGER.warn("Could not read {}, starting with default settings", this.file, e);
			this.keepBrokenFile();
		}
	}

	public void save() {
		Data data = new Data();
		data.talkingMouths = this.talkingMouths;
		data.fartDetection = this.fartDetection;
		data.middleFinger = this.middleFinger;
		data.defaultCharacter = this.defaultChoice.encode();
		this.choices.entrySet().stream()
			.sorted(Comparator.comparing(entry -> this.playerNames.getOrDefault(entry.getKey(), ""), String.CASE_INSENSITIVE_ORDER))
			.forEach(entry -> data.players.put(
				entry.getKey().toString(),
				new PlayerData(this.playerNames.getOrDefault(entry.getKey(), ""), entry.getValue().encode())));

		try {
			Files.createDirectories(this.file.getParent());
			Path temp = this.file.resolveSibling(this.file.getFileName() + ".tmp");
			Files.writeString(temp, GSON.toJson(data), StandardCharsets.UTF_8);
			Files.move(temp, this.file, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			PlayerCharactersClient.LOGGER.warn("Could not save {}", this.file, e);
		}
	}

	/** Keeps an unreadable config around (instead of overwriting it on the next save) so hand edits aren't lost. */
	private void keepBrokenFile() {
		try {
			Files.move(this.file, this.file.resolveSibling(this.file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			PlayerCharactersClient.LOGGER.warn("Could not rename broken config {}", this.file, e);
		}
	}

	private static UUID parseUuid(String value) {
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** File format of playercharacters.json. */
	private static final class Data {
		boolean talkingMouths = true;
		boolean fartDetection = true;
		boolean middleFinger = true;
		String defaultCharacter = "own";
		Map<String, PlayerData> players = new LinkedHashMap<>();
	}

	private static final class PlayerData {
		String name;
		String character;

		PlayerData() {
		}

		PlayerData(String name, String character) {
			this.name = name;
			this.character = character;
		}
	}
}
