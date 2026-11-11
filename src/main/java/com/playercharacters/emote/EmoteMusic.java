package com.playercharacters.emote;

import com.playercharacters.PlayerCharactersClient;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Your own music for emotes: an .ogg file in {@code config/playercharacters/music} named like the emote ("Default
 * Dance.ogg" or "default_dance.ogg") plays instead of the note block tune, for anyone you see doing that emote.
 * Nothing is sent to other players; they hear the files in their own music folder.
 */
public final class EmoteMusic {
	private static final Path FOLDER = FabricLoader.getInstance().getConfigDir().resolve(PlayerCharactersClient.MOD_ID).resolve("music");
	private static final SoundEvent SOUND = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(PlayerCharactersClient.MOD_ID, "emote_music"));
	/** The folder is looked at again at most this often. */
	private static final long RESCAN_MILLIS = 2000;
	private static Map<String, Path> files = Map.of();
	private static long scannedAt;
	private static boolean stale = true;
	private static final Set<String> WARNED = new HashSet<>();
	private static boolean readmeWritten;
	private static int started;
	private static int opened;

	private EmoteMusic() {
	}

	public static Path folder() {
		return FOLDER;
	}

	/** How many times your own music was started, and opened by the sound engine, since the game started. */
	public static int startedCount() {
		return started;
	}

	public static int openedCount() {
		return opened;
	}

	/** Makes the folder, and explains it in a README.txt once the game knows the emotes' names. */
	public static void setUp() {
		try {
			Files.createDirectories(FOLDER);
		} catch (IOException e) {
			PlayerCharactersClient.LOGGER.warn("Couldn't set up {}", FOLDER, e);
		}
		stale = true;
		readmeWritten = false;
		tick();
	}

	/** Called every client tick: writes the README.txt as soon as the emotes' names are loaded (that happens after start-up). */
	public static void tick() {
		if (readmeWritten || !Language.getInstance().has(Emotes.DEFAULT_DANCE.translationKey())) {
			return;
		}
		readmeWritten = true;
		try {
			Files.createDirectories(FOLDER);
			StringBuilder text = new StringBuilder("""
				Your own music for emotes
				=========================

				Put an .ogg file here with the name of an emote, and that emote plays it instead of its note block tune.
				Only .ogg (Ogg Vorbis) works; convert MP3 or WAV files first, for example with Audacity.
				Dances that repeat play the file in a loop. The volume is "Jukebox/Note Blocks" in the sound settings.
				Other players with the mod hear the files in their own music folder, not yours.

				File names (either way works, upper or lower case):

				""");
			for (Emote emote : Emotes.all()) {
				text.append("  ").append(Component.translatable(emote.translationKey()).getString()).append(".ogg   or   ").append(emote.id()).append(".ogg\n");
			}
			Path readme = FOLDER.resolve("README.txt");
			String wanted = text.toString().replace("\n", System.lineSeparator());
			if (!Files.isRegularFile(readme) || !Files.readString(readme, StandardCharsets.UTF_8).equals(wanted)) {
				Files.writeString(readme, wanted, StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			PlayerCharactersClient.LOGGER.warn("Couldn't write the README.txt in {}", FOLDER, e);
		}
	}

	/** The music file for this emote, or null for the built-in tune. */
	public static @Nullable Path fileFor(Emote emote) {
		long now = Util.getMillis();
		if (stale || now - scannedAt > RESCAN_MILLIS) {
			stale = false;
			scannedAt = now;
			files = scan();
		}
		Path file = files.get(normalize(emote.id()));
		return file != null ? file : files.get(normalize(Component.translatable(emote.translationKey()).getString()));
	}

	/** Forgets what was in the folder, so the next emote looks again. */
	public static void rescan() {
		stale = true;
	}

	private static Map<String, Path> scan() {
		Map<String, Path> found = new HashMap<>();
		if (!Files.isDirectory(FOLDER)) {
			return found;
		}
		try (Stream<Path> list = Files.list(FOLDER)) {
			list.filter(Files::isRegularFile).forEach(file -> {
				String name = file.getFileName().toString();
				int dot = name.lastIndexOf('.');
				if (dot <= 0) {
					return;
				}
				String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
				if (extension.equals("ogg")) {
					found.put(normalize(name.substring(0, dot)), file);
				} else if (!extension.equals("txt") && WARNED.add(name)) {
					PlayerCharactersClient.LOGGER.warn("Emote music {} isn't an .ogg file, so it can't be played", file);
				}
			});
		} catch (IOException e) {
			PlayerCharactersClient.LOGGER.warn("Couldn't look in {}", FOLDER, e);
		}
		return found;
	}

	/** "Default Dance", "default_dance" and "DEFAULT-DANCE" are all the same. */
	static String normalize(String name) {
		StringBuilder out = new StringBuilder();
		name.toLowerCase(Locale.ROOT).codePoints().filter(Character::isLetterOrDigit).forEach(out::appendCodePoint);
		return out.toString();
	}

	/** Starts playing a file at a player, following them around. */
	static SoundInstance start(Player player, Path file, boolean loop) {
		started++;
		return new Sound(player, file, loop);
	}

	/** A sound that plays a music file from disk instead of from a resource pack, and moves with the player doing the emote. */
	private static final class Sound extends AbstractTickableSoundInstance {
		private final Player player;
		private final Path file;

		Sound(Player player, Path file, boolean loop) {
			super(SOUND, SoundSource.RECORDS, RandomSource.create());
			this.player = player;
			this.file = file;
			this.looping = loop;
			this.delay = 0;
			this.volume = 1.0F;
			this.attenuation = SoundInstance.Attenuation.LINEAR;
			this.x = player.getX();
			this.y = player.getY() + 1.0;
			this.z = player.getZ();
		}

		@Override
		public void tick() {
			if (this.player.isRemoved()) {
				this.stop();
				return;
			}
			this.x = this.player.getX();
			this.y = this.player.getY() + 1.0;
			this.z = this.player.getZ();
		}

		@Override
		public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary loader, Identifier id, boolean repeatInstantly) {
			Path path = this.file;
			return CompletableFuture.supplyAsync(() -> {
				try {
					InputStream in = new BufferedInputStream(Files.newInputStream(path));
					opened++;
					return repeatInstantly ? new LoopingAudioStream(JOrbisAudioStream::new, in) : new JOrbisAudioStream(in);
				} catch (IOException e) {
					PlayerCharactersClient.LOGGER.warn("Couldn't play emote music {}", path, e);
					throw new CompletionException(e);
				}
			}, Util.nonCriticalIoPool());
		}
	}
}
