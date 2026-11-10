package com.playercharacters.character;

import com.mojang.blaze3d.platform.NativeImage;
import com.playercharacters.PlayerCharactersClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

/**
 * Loads characters from {@code .minecraft/config/playercharacters/characters/}:
 * <ul>
 *   <li>a folder with a {@code model.obj} is a 3D character (see {@link ModelCharacterLoader})</li>
 *   <li>{@code Name.png} is a skin character, with optional {@code Name_talking.png} and {@code Name.json}</li>
 * </ul>
 */
public final class CharacterLibrary {
	private static final String TALKING_SUFFIX = "_talking";
	private static final Path FOLDER = FabricLoader.getInstance().getConfigDir().resolve(PlayerCharactersClient.MOD_ID).resolve("characters");
	/** Lists the examples that were already put in the folder once, so deleted ones don't come back. */
	private static final String EXAMPLES_MARKER = ".examples";
	private static final Map<String, List<String>> EXAMPLES = new LinkedHashMap<>();

	static {
		EXAMPLES.put("Robot", List.of("Robot.png", "Robot_talking.png"));
		EXAMPLES.put("Frog", List.of("Frog.png", "Frog.json"));
		EXAMPLES.put("Alien", List.of("Alien/model.obj", "Alien/character.json", "Alien/icon.png"));
	}

	private static volatile List<PlayerCharacter> characters = List.of();
	private static volatile Map<String, PlayerCharacter> byId = Map.of();
	private static volatile List<String> problems = List.of();
	private static final List<Identifier> registeredTextures = new ArrayList<>();
	private static int generation;

	private CharacterLibrary() {
	}

	public static Path folder() {
		return FOLDER;
	}

	/** All loaded characters, sorted by name. */
	public static List<PlayerCharacter> all() {
		return characters;
	}

	public static @Nullable PlayerCharacter get(@Nullable String id) {
		return id == null ? null : byId.get(id);
	}

	/** The same player always gets the same character, as long as the set of characters doesn't change. */
	public static @Nullable PlayerCharacter randomFor(UUID player) {
		List<PlayerCharacter> list = characters;
		return list.isEmpty() ? null : list.get(Math.floorMod(player.hashCode(), list.size()));
	}

	/** Files that couldn't be loaded, as human readable lines. */
	public static List<String> problems() {
		return problems;
	}

	/** (Re)loads every character. Must run on the render thread because it uploads textures. */
	public static void reload() {
		TextureManager textureManager = Minecraft.getInstance().getTextureManager();
		installExamples();

		int gen = ++generation;
		List<PlayerCharacter> loaded = new ArrayList<>();
		List<String> issues = new ArrayList<>();
		List<Identifier> newTextures = new ArrayList<>();
		List<Path> entries = listCharacterFiles(issues);
		for (int i = 0; i < entries.size(); i++) {
			Path entry = entries.get(i);
			String texturePath = "characters/" + gen + "/" + i;
			try {
				if (Files.isDirectory(entry)) {
					loaded.add(ModelCharacterLoader.load(textureManager, entry, texturePath, newTextures, issues));
				} else {
					loaded.add(loadSkin(textureManager, entry, texturePath, newTextures, issues));
				}
			} catch (IOException | RuntimeException e) {
				issues.add(entry.getFileName() + ": " + e.getMessage());
				PlayerCharactersClient.LOGGER.warn("Skipping character {}", entry, e);
			}
		}

		// The new textures use new ids, so the old ones can be freed safely now.
		registeredTextures.forEach(textureManager::release);
		registeredTextures.clear();
		registeredTextures.addAll(newTextures);

		loaded.sort(Comparator.comparing(c -> c.displayName().toLowerCase(Locale.ROOT)));
		Map<String, PlayerCharacter> index = new HashMap<>();
		loaded.forEach(c -> index.put(c.id(), c));
		characters = List.copyOf(loaded);
		byId = Map.copyOf(index);
		problems = List.copyOf(issues);
		PlayerCharactersClient.LOGGER.info("Loaded {} character(s) from {}", loaded.size(), FOLDER);
	}

	// ------------------------------------------------------------------ skin characters

	private static PlayerCharacter loadSkin(TextureManager textureManager, Path file, String texturePath, List<Identifier> newTextures, List<String> issues)
		throws IOException {
		String id = baseName(file);
		CharacterOptions options = CharacterOptions.read(file.resolveSibling(id + ".json"), issues);
		SkinImages.LoadedSkin idle = SkinImages.read(file);

		NativeImage talking = null;
		Path talkingFile = file.resolveSibling(id + TALKING_SUFFIX + ".png");
		if (Files.isRegularFile(talkingFile)) {
			try {
				talking = SkinImages.read(talkingFile).image();
			} catch (IOException | RuntimeException e) {
				issues.add(talkingFile.getFileName() + ": " + e.getMessage() + " (using an automatic mouth instead)");
			}
		}
		boolean customTalkingFrame = talking != null;
		if (talking == null) {
			talking = SkinImages.withOpenMouth(idle.image(), options.mouthOrDefault());
		}

		PlayerModelType model = options.modelType();
		if (model == null) {
			model = idle.slimArms() ? PlayerModelType.SLIM : PlayerModelType.WIDE;
		}

		Identifier idleId = Identifier.fromNamespaceAndPath(PlayerCharactersClient.MOD_ID, texturePath + "/idle");
		Identifier talkingId = Identifier.fromNamespaceAndPath(PlayerCharactersClient.MOD_ID, texturePath + "/talking");
		// DynamicTexture takes ownership of the images and frees them when the texture is released.
		textureManager.register(idleId, new DynamicTexture(() -> "Player Characters: " + id, idle.image()));
		newTextures.add(idleId);
		textureManager.register(talkingId, new DynamicTexture(() -> "Player Characters: " + id + " (talking)", talking));
		newTextures.add(talkingId);

		String name = options.name == null || options.name.isBlank() ? id : options.name.strip();
		return PlayerCharacter.ofSkin(id, name, skin(idleId, model), skin(talkingId, model), customTalkingFrame);
	}

	/** A character skin has no cape or elytra texture, so the player's real cape is hidden too. */
	private static PlayerSkin skin(Identifier texture, PlayerModelType model) {
		return new PlayerSkin(new ClientAsset.ResourceTexture(texture, texture), null, null, model, true);
	}

	// ------------------------------------------------------------------ files

	/** Model folders, and every PNG except "X_talking.png" files that belong to an "X.png". */
	private static List<Path> listCharacterFiles(List<String> issues) {
		List<Path> all;
		try (Stream<Path> files = Files.list(FOLDER)) {
			all = files.filter(path -> !path.getFileName().toString().startsWith(".")).sorted().toList();
		} catch (IOException e) {
			issues.add("Couldn't open the characters folder: " + e.getMessage());
			PlayerCharactersClient.LOGGER.warn("Couldn't list {}", FOLDER, e);
			return List.of();
		}

		List<Path> pngs = all.stream()
			.filter(Files::isRegularFile)
			.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
			.toList();
		Set<String> names = new HashSet<>();
		pngs.forEach(path -> names.add(baseName(path)));

		List<Path> result = new ArrayList<>();
		for (Path path : all) {
			if (Files.isDirectory(path)) {
				if (ModelCharacterLoader.isModelFolder(path)) {
					result.add(path);
				} else {
					issues.add(path.getFileName() + ": folder has no " + ModelCharacterLoader.MODEL_FILE);
				}
			} else if (pngs.contains(path)) {
				String name = baseName(path);
				boolean talkingFrame = name.endsWith(TALKING_SUFFIX) && names.contains(name.substring(0, name.length() - TALKING_SUFFIX.length()));
				if (!talkingFrame) {
					result.add(path);
				}
			}
		}
		return result;
	}

	private static String baseName(Path file) {
		String name = file.getFileName().toString();
		return name.substring(0, name.length() - ".png".length());
	}

	/**
	 * Copies the example characters into the folder. Each example is copied only once, so examples
	 * the user deleted stay deleted, while examples added in a newer version of the mod still show up.
	 */
	private static void installExamples() {
		try {
			boolean freshFolder = !Files.isDirectory(FOLDER);
			Files.createDirectories(FOLDER);
			Path marker = FOLDER.resolve(EXAMPLES_MARKER);
			Set<String> installed = new LinkedHashSet<>();
			if (Files.isRegularFile(marker)) {
				for (String line : Files.readAllLines(marker, StandardCharsets.UTF_8)) {
					if (!line.isBlank()) {
						installed.add(line.strip());
					}
				}
			} else if (!freshFolder) {
				installed.addAll(List.of("Robot", "Frog")); // put there by version 1.0, which had no marker
			}

			Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(PlayerCharactersClient.MOD_ID);
			if (mod.isEmpty()) {
				return;
			}
			boolean addedAny = false;
			for (Map.Entry<String, List<String>> example : EXAMPLES.entrySet()) {
				if (installed.add(example.getKey())) {
					for (String file : example.getValue()) {
						copyExample(mod.get(), file);
					}
					addedAny = true;
				}
			}
			if (addedAny) {
				Optional<Path> readme = mod.get().findPath("assets/" + PlayerCharactersClient.MOD_ID + "/examples/README.txt");
				if (readme.isPresent()) {
					Files.copy(readme.get(), FOLDER.resolve("README.txt"), StandardCopyOption.REPLACE_EXISTING);
				}
				Files.write(marker, installed, StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			PlayerCharactersClient.LOGGER.warn("Couldn't set up {}", FOLDER, e);
		}
	}

	private static void copyExample(ModContainer mod, String file) throws IOException {
		Optional<Path> source = mod.findPath("assets/" + PlayerCharactersClient.MOD_ID + "/examples/" + file);
		Path target = FOLDER.resolve(file);
		if (source.isPresent() && !Files.exists(target)) {
			Files.createDirectories(target.getParent());
			Files.copy(source.get(), target);
		}
	}
}
