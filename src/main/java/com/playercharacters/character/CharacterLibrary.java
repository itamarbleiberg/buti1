package com.playercharacters.character;

import com.mojang.blaze3d.platform.NativeImage;
import com.playercharacters.PlayerCharactersClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
 * Loads characters from {@code .minecraft/config/playercharacters/characters/}.
 * <ul>
 *   <li>{@code Name.png} - a normal 64x64 Minecraft skin. Every PNG is one character.</li>
 *   <li>{@code Name_talking.png} - optional: the same skin with the mouth open. Generated automatically when missing.</li>
 *   <li>{@code Name.json} - optional settings, see {@link CharacterOptions}.</li>
 * </ul>
 */
public final class CharacterLibrary {
	private static final String TALKING_SUFFIX = "_talking";
	private static final List<String> EXAMPLE_FILES = List.of("README.txt", "Robot.png", "Robot_talking.png", "Frog.png", "Frog.json");
	private static final Path FOLDER = FabricLoader.getInstance().getConfigDir().resolve(PlayerCharactersClient.MOD_ID).resolve("characters");

	private static volatile List<CharacterSkin> characters = List.of();
	private static volatile Map<String, CharacterSkin> byId = Map.of();
	private static volatile List<String> problems = List.of();
	private static final List<Identifier> registeredTextures = new ArrayList<>();
	private static int generation;

	private CharacterLibrary() {
	}

	public static Path folder() {
		return FOLDER;
	}

	/** All loaded characters, sorted by name. */
	public static List<CharacterSkin> all() {
		return characters;
	}

	public static @Nullable CharacterSkin get(@Nullable String id) {
		return id == null ? null : byId.get(id);
	}

	/** The same player always gets the same character, as long as the set of characters doesn't change. */
	public static @Nullable CharacterSkin randomFor(UUID player) {
		List<CharacterSkin> list = characters;
		return list.isEmpty() ? null : list.get(Math.floorMod(player.hashCode(), list.size()));
	}

	/** Files that couldn't be loaded, as human readable lines. */
	public static List<String> problems() {
		return problems;
	}

	/** (Re)loads every character. Must run on the render thread because it uploads textures. */
	public static void reload() {
		TextureManager textureManager = Minecraft.getInstance().getTextureManager();
		createFolderWithExamples();

		int gen = ++generation;
		List<CharacterSkin> loaded = new ArrayList<>();
		List<String> issues = new ArrayList<>();
		List<Identifier> newTextures = new ArrayList<>();
		List<Path> skinFiles = listSkinFiles(issues);
		for (int i = 0; i < skinFiles.size(); i++) {
			Path file = skinFiles.get(i);
			try {
				loaded.add(load(textureManager, file, "characters/" + gen + "/" + i, newTextures, issues));
			} catch (IOException | RuntimeException e) {
				issues.add(file.getFileName() + ": " + e.getMessage());
				PlayerCharactersClient.LOGGER.warn("Skipping character file {}", file, e);
			}
		}

		// The new textures use new ids, so the old ones can be freed safely now.
		registeredTextures.forEach(textureManager::release);
		registeredTextures.clear();
		registeredTextures.addAll(newTextures);

		loaded.sort(Comparator.comparing(c -> c.displayName().toLowerCase(Locale.ROOT)));
		Map<String, CharacterSkin> index = new HashMap<>();
		loaded.forEach(c -> index.put(c.id(), c));
		characters = List.copyOf(loaded);
		byId = Map.copyOf(index);
		problems = List.copyOf(issues);
		PlayerCharactersClient.LOGGER.info("Loaded {} character(s) from {}", loaded.size(), FOLDER);
	}

	private static CharacterSkin load(TextureManager textureManager, Path file, String texturePath, List<Identifier> newTextures, List<String> issues)
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
		return new CharacterSkin(id, name, skin(idleId, model), skin(talkingId, model), customTalkingFrame);
	}

	/** A character skin has no cape or elytra texture, so the player's real cape is hidden too. */
	private static PlayerSkin skin(Identifier texture, PlayerModelType model) {
		return new PlayerSkin(new ClientAsset.ResourceTexture(texture, texture), null, null, model, true);
	}

	/** Every PNG in the folder except "X_talking.png" files that belong to an "X.png". */
	private static List<Path> listSkinFiles(List<String> issues) {
		List<Path> pngs;
		try (Stream<Path> files = Files.list(FOLDER)) {
			pngs = files
				.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
				.sorted()
				.toList();
		} catch (IOException e) {
			issues.add("Couldn't open the characters folder: " + e.getMessage());
			PlayerCharactersClient.LOGGER.warn("Couldn't list {}", FOLDER, e);
			return List.of();
		}

		Set<String> names = new HashSet<>();
		pngs.forEach(path -> names.add(baseName(path)));
		return pngs.stream().filter(path -> {
			String name = baseName(path);
			return !(name.endsWith(TALKING_SUFFIX) && names.contains(name.substring(0, name.length() - TALKING_SUFFIX.length())));
		}).toList();
	}

	private static String baseName(Path file) {
		String name = file.getFileName().toString();
		return name.substring(0, name.length() - ".png".length());
	}

	/** On first start, creates the folder with a README and two example characters. */
	private static void createFolderWithExamples() {
		if (Files.isDirectory(FOLDER)) {
			return;
		}
		try {
			Files.createDirectories(FOLDER);
			Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(PlayerCharactersClient.MOD_ID);
			if (mod.isEmpty()) {
				return;
			}
			for (String name : EXAMPLE_FILES) {
				Optional<Path> example = mod.get().findPath("assets/" + PlayerCharactersClient.MOD_ID + "/examples/" + name);
				if (example.isPresent()) {
					Files.copy(example.get(), FOLDER.resolve(name));
				}
			}
		} catch (IOException e) {
			PlayerCharactersClient.LOGGER.warn("Couldn't create {}", FOLDER, e);
		}
	}
}
