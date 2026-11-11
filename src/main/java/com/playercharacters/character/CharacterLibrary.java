package com.playercharacters.character;

import com.mojang.blaze3d.platform.NativeImage;
import com.playercharacters.PlayerCharactersClient;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
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
	/**
	 * Files of older examples that a newer example takes the place of, with their SHA-256. They are deleted when the
	 * newer example is installed, but only if none of them were changed.
	 */
	private static final Map<String, Map<String, String>> REPLACES = new HashMap<>();
	/** SHA-256 of older versions of example files. Such files are updated, as long as nobody changed them. */
	private static final Map<String, Set<String>> OUTDATED = new HashMap<>();

	static {
		EXAMPLES.put("Alien", modelFiles("Alien"));
		EXAMPLES.put("Robot 3D", modelFiles("Robot"));
		EXAMPLES.put("Frog 3D", modelFiles("Frog"));
		EXAMPLES.put("Burger", modelFiles("Burger"));
		EXAMPLES.put("Penguin", modelFiles("Penguin"));
		EXAMPLES.put("SpongeBob", modelFiles("SpongeBob"));
		EXAMPLES.put("Skinny Alien", modelFiles("Skinny Alien"));
		EXAMPLES.put("Blue", modelFiles("Blue"));
		// Versions 1 and 2 had the robot and the frog as skins.
		REPLACES.put("Robot 3D", Map.of(
			"Robot.png", "c25c1b4982abf476bbd50ea787b432fda2d01fe0ee381a091ca265e12408e539",
			"Robot_talking.png", "d1d27376e6217477ab439feb77c356e1a3e8db9180fcc865bbef9a684e71dab4"));
		REPLACES.put("Frog 3D", Map.of(
			"Frog.png", "1c35f4608a6d53a3543d3697ea4e2ccb06341bca0cfbeaf2aa3db4fec9ef8979",
			"Frog.json", "8bb9b34a81b6e0ef6d0c99684c19e28f3f67d67069bed01c0f1d93bbcc3aaa35"));
		// Versions 2 and 3 (version 4 added the angry faces and middle fingers).
		outdated("Alien/model.obj", "766b2cf98270f83448615a0797d88517bb7c3f8da8b60b8b8598e9a06fad711d",
			"47cf348d5186e5aff86e8f73cc54829934f45617b326e8591de343501b466897");
		outdated("Alien/character.json", "3fea26c095af1f961822a9452acd2629e6a15f8901574d41e680f38d099fe879",
			"bb464f8eee7d1245229b222a61b6bae1491c5cece7dc4b4d5106c15751adfd1c");
		outdated("Alien/icon.png", "e32bbc58b8f7ec558d785b703e144add66e85e3f26ebda6fe8b7fc39a79bff6b",
			"23b0481b472af8e5d35b4af5a351c83dca0bcb099f9c414741913c6330182c32");
		outdated("Burger/model.obj", "a09753c907c2239b1c7d8f003dc6e8573e8e734391aaa995b346567ad55852ad");
		outdated("Burger/character.json", "1f4780eb0b6fd163ea909dbe6cee7e5ee254168d19e5011d14f74d56e3232994");
		outdated("Burger/icon.png", "a6a9e7b13ddaeacd432931aab27f489f6f971af0e70e7bf27d34d864c206f26f");
		outdated("Frog/model.obj", "76c7eb1cc19b849eac0c8526073f493e5a98d5d45a7aa31c40fc151e276b1d4a");
		outdated("Frog/character.json", "fee98f11449c4e4cf878302f911db60be3fca332c8387833fa39081908bfd234");
		outdated("Frog/icon.png", "62ab7cb7dc0b05b92519d7306bfbbd2d71808bd4c186c5c961e5a9dddda441d9");
		outdated("Robot/model.obj", "5a9c2c6bce5962d45836631cfab7843f0b66983407325c4352abba8758d37fe9");
		outdated("Robot/character.json", "cc78b6c6fc309b0b00fdbae8a28636bc652b7c309ff8e3836e73637968fb306a");
		outdated("Robot/icon.png", "4cc653f27f0fda1db3481422c5db3b5f27ebf5e4f398ddfb3911578eb4a66c62");
		// Version 4 (version 5 turned Blue into 67 and remade the penguin, SpongeBob and the skinny alien).
		outdated("Blue/model.obj", "e5ca568036e84bb2a75123c740b32a42409496745300f16308eb63097f864c16");
		outdated("Blue/character.json", "958ed595990e0bd011a5a0efef780c669694cd4e61788ecb8cde56d1f96a4bb3");
		outdated("Blue/icon.png", "6597777a9e1ac2f926b8b3be5b0e9ba715b0a775ffd19be12d66e42598c88034");
		outdated("Penguin/model.obj", "e70f0a2279b9c00a0d59f4a7a2f96cedb3948c184bb4773e352f98d60e5b4d87");
		outdated("Penguin/character.json", "30ca3ddc01601dfc7377d18e07fbf1922b787f9235a2203bec9f6b647463fefd");
		outdated("Penguin/icon.png", "b09bef1d6d1cb4bbeae00366ab62c55658d0b5107732ae171b9fbb0db70c8d04");
		outdated("SpongeBob/model.obj", "1122acd0920f402064e6fc85508c0a9bb9d3e14f6beadae915564e23876e1e16");
		outdated("SpongeBob/character.json", "bbbc39fd75cc99ab38f99da0aa2f68bde632200e2df0834e4d66eaf8ab2cdebf");
		outdated("SpongeBob/icon.png", "10ae4ab121fa2f604baaab591c216fd3343ab38f81879f4223bb5d65edbe10d0");
		outdated("Skinny Alien/model.obj", "6d2cca091e4c6a356ae54dd29af9d5e32ac73241326ce22d96d634ba377d55bd");
		outdated("Skinny Alien/character.json", "def4b367e54a432310b0646e8d92cc6cd84c71588b2812c11b9413f7c01b3e04");
		outdated("Skinny Alien/icon.png", "be0d58f969c9b1606511fee68c3a75f779a939d8551609cf30ac875ba12e0403");
		// Version 5 (version 6 made all of them more detailed, and added blinking).
		outdated("Alien/model.obj", "f63935eb7a0dbe3732b674207e0d95953255c01125d3e90d7f2104d616f8436a");
		outdated("Alien/character.json", "bb464f8eee7d1245229b222a61b6bae1491c5cece7dc4b4d5106c15751adfd1c");
		outdated("Alien/icon.png", "23b0481b472af8e5d35b4af5a351c83dca0bcb099f9c414741913c6330182c32");
		outdated("Robot/model.obj", "ecdf980d4c43e168e38814cdec215ad9b9aa1fd318149bed5682f699f57421b0");
		outdated("Robot/character.json", "cc78b6c6fc309b0b00fdbae8a28636bc652b7c309ff8e3836e73637968fb306a");
		outdated("Robot/icon.png", "4cc653f27f0fda1db3481422c5db3b5f27ebf5e4f398ddfb3911578eb4a66c62");
		outdated("Frog/model.obj", "5f4efadf41267c85f58daedfb5754987aebe01549f6e12fc32c8a546a956fb4c");
		outdated("Frog/character.json", "fee98f11449c4e4cf878302f911db60be3fca332c8387833fa39081908bfd234");
		outdated("Frog/icon.png", "c0e7431c17d44009765c5e2766eaf04e2ea89f986958d678db4d2890c895432c");
		outdated("Burger/model.obj", "f184e839ada2a9eba722e9785cfc1471d940f0048b0a5a0811171a0be2f3d058");
		outdated("Burger/character.json", "1f4780eb0b6fd163ea909dbe6cee7e5ee254168d19e5011d14f74d56e3232994");
		outdated("Burger/icon.png", "a6a9e7b13ddaeacd432931aab27f489f6f971af0e70e7bf27d34d864c206f26f");
		outdated("Penguin/model.obj", "4f6451a65341303effe3cef51bc0b1e89b9e104981cfddd88d2e2d7e1e397a07");
		outdated("Penguin/character.json", "d51ddb6f7db4ff4de8fcba929eafee68ce59c52ff0f863c0bd2ddcb68f3a16b1");
		outdated("Penguin/icon.png", "1f7b48f873d94292c5fa1ff950128c301cd9cf3ae9da4b402f892835f5367750");
		outdated("SpongeBob/model.obj", "11cb7611c98441ea24baa823a74e27667c7f919e2e48d165340d2864f3fe535b");
		outdated("SpongeBob/character.json", "bbbc39fd75cc99ab38f99da0aa2f68bde632200e2df0834e4d66eaf8ab2cdebf");
		outdated("SpongeBob/icon.png", "bfc7c831fb542532941b7e98d5d969f3b74c35527878f80aa7714a7948848d92");
		outdated("Skinny Alien/model.obj", "5ce1b55fdd45b0a0fb2b3e7f746c2cb4e94ba829d3e5d86092253ae335b68386");
		outdated("Skinny Alien/character.json", "def4b367e54a432310b0646e8d92cc6cd84c71588b2812c11b9413f7c01b3e04");
		outdated("Skinny Alien/icon.png", "d76025b59e1951247c0a9877330f49bd7794326e8dcd7190df16974330f4f97c");
		outdated("Blue/model.obj", "041d6f6535c053f316d74a505e5432a2cbb637cae7406be04361e3721c549ab8");
		outdated("Blue/character.json", "4540a938f01b735d301cb4f75435dca4aee82afa62276c5c0773b01ece7d5490");
		outdated("Blue/icon.png", "ccc95bae24efe84bd3bf053e03d6c8408bfd6ce6e6cfc44cd20777e6f7ddba78");
	}

	/** Remembers SHA-256 hashes of older versions of an example file. */
	private static void outdated(String file, String... hashes) {
		OUTDATED.computeIfAbsent(file, key -> new HashSet<>()).addAll(List.of(hashes));
	}

	private static List<String> modelFiles(String folder) {
		return List.of(folder + "/model.obj", folder + "/character.json", folder + "/icon.png");
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
		Set<String> modelFolders = new HashSet<>();
		all.stream().filter(ModelCharacterLoader::isModelFolder).forEach(path -> modelFolders.add(path.getFileName().toString()));

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
				if (talkingFrame) {
					continue;
				}
				if (modelFolders.contains(name)) {
					// Both would be called the same, and choices are saved by name. The 3D one wins.
					issues.add(path.getFileName() + ": skipped because the 3D character folder \"" + name + "\" has the same name. Rename one of them.");
				} else {
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
			boolean changed = false;
			for (Map.Entry<String, List<String>> example : EXAMPLES.entrySet()) {
				if (installed.add(example.getKey())) {
					deleteIfUnchanged(REPLACES.getOrDefault(example.getKey(), Map.of()));
					for (String file : example.getValue()) {
						copyExample(mod.get(), file, false);
					}
					changed = true;
				} else {
					// Installed by an older version of the mod: bring it up to date. If any of its files was changed, the
					// whole character is left alone, so a new model never ends up with old settings or the other way around.
					List<String> outdated = new ArrayList<>();
					boolean edited = false;
					for (String file : example.getValue()) {
						String current = sha256(FOLDER.resolve(file));
						if (current == null || current.equals(bundledSha256(mod.get(), file))) {
							continue; // deleted by the user, or already up to date
						}
						if (OUTDATED.getOrDefault(file, Set.of()).contains(current)) {
							outdated.add(file);
						} else {
							edited = true;
						}
					}
					if (edited && !outdated.isEmpty()) {
						PlayerCharactersClient.LOGGER.info("Not updating the example {} because some of its files were changed", example.getKey());
					} else {
						for (String file : outdated) {
							copyExample(mod.get(), file, true);
							changed = true;
						}
					}
				}
			}
			if (changed) {
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

	private static void copyExample(ModContainer mod, String file, boolean replace) throws IOException {
		Optional<Path> source = mod.findPath("assets/" + PlayerCharactersClient.MOD_ID + "/examples/" + file);
		Path target = FOLDER.resolve(file);
		if (source.isPresent() && (replace || !Files.exists(target))) {
			Files.createDirectories(target.getParent());
			Files.copy(source.get(), target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** Deletes these files (name to SHA-256), but only if every one that exists is exactly as the mod put it there. */
	private static void deleteIfUnchanged(Map<String, String> files) throws IOException {
		for (Map.Entry<String, String> file : files.entrySet()) {
			Path path = FOLDER.resolve(file.getKey());
			if (Files.exists(path) && !file.getValue().equals(sha256(path))) {
				PlayerCharactersClient.LOGGER.info("Keeping the old example {} because it was changed", file.getKey());
				return;
			}
		}
		for (String file : files.keySet()) {
			if (Files.deleteIfExists(FOLDER.resolve(file))) {
				PlayerCharactersClient.LOGGER.info("Removed the old example {}, it is now a 3D character", file);
			}
		}
	}

	private static @Nullable String bundledSha256(ModContainer mod, String file) {
		Optional<Path> source = mod.findPath("assets/" + PlayerCharactersClient.MOD_ID + "/examples/" + file);
		return source.isPresent() ? sha256(source.get()) : null;
	}

	/** SHA-256 of a file as lowercase hex, or null if it doesn't exist or can't be read. */
	private static @Nullable String sha256(Path file) {
		if (!Files.isRegularFile(file)) {
			return null;
		}
		try (InputStream in = Files.newInputStream(file)) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[65536];
			for (int n; (n = in.read(buffer)) > 0; ) {
				digest.update(buffer, 0, n);
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (IOException | NoSuchAlgorithmException e) {
			return null;
		}
	}
}
