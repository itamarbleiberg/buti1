package com.playercharacters.speech;

import com.playercharacters.PlayerCharactersClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.jspecify.annotations.Nullable;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;

/**
 * The offline speech recognizer (Vosk) and its small English model, which ship inside the mod jar. Vosk can only
 * read a model from a normal folder, so the model is unpacked to {@code .minecraft/playercharacters/speech-model}
 * the first time it is needed.
 */
public final class SpeechModel {
	static final String NAME = "vosk-model-small-en-us-0.15";
	private static final String RESOURCE = "assets/" + PlayerCharactersClient.MOD_ID + "/speech/" + NAME;
	private static final Path FOLDER = FabricLoader.getInstance().getGameDir().resolve(PlayerCharactersClient.MOD_ID).resolve("speech-model");
	private static final String DONE_FILE = ".unpacked";

	public enum Status {
		/** Not loaded (yet): nobody talked since the game started, or voice emotes are off. */
		OFF,
		LOADING,
		READY,
		/** Can't work on this computer, see {@link #problem()}. */
		UNAVAILABLE
	}

	private static volatile Status status = Status.OFF;
	private static volatile @Nullable String problem;
	/** Speech thread only. */
	private static @Nullable Model model;

	private SpeechModel() {
	}

	public static Status status() {
		return status;
	}

	/** Why speech recognition is unavailable, or null. */
	public static @Nullable String problem() {
		return problem;
	}

	/** Loads the model the first time it is needed. Speech thread only. Null if speech recognition can't work here. */
	static @Nullable Model get() {
		if (model != null) {
			return model;
		}
		if (status == Status.UNAVAILABLE) {
			return null;
		}
		status = Status.LOADING;
		long start = System.nanoTime();
		try {
			Path folder = unpack();
			LibVosk.setLogLevel(LogLevel.WARNINGS);
			PlayerCharactersClient.LOGGER.info("Loading the speech model for voice emotes. A \"Can't open file\" message about Gr.fst may follow: "
				+ "that file was left out on purpose, it is only needed to recognize every English word.");
			model = new Model(pathForNativeCode(folder));
			status = Status.READY;
			PlayerCharactersClient.LOGGER.info("Voice emotes are ready ({} ms)", (System.nanoTime() - start) / 1_000_000);
			return model;
		} catch (Throwable e) { // also UnsatisfiedLinkError and friends when Vosk has no library for this computer
			status = Status.UNAVAILABLE;
			problem = e.getClass().getSimpleName() + ": " + e.getMessage();
			PlayerCharactersClient.LOGGER.warn("Voice emotes are unavailable: speech recognition couldn't start", e);
			return null;
		}
	}

	/** Frees the model's memory. Speech thread only. */
	static void close() {
		if (model != null) {
			model.close();
			model = null;
			status = Status.OFF;
		}
	}

	/** Copies the model out of the mod jar, unless this version of the mod already did. */
	private static Path unpack() throws IOException {
		ModContainer mod = FabricLoader.getInstance().getModContainer(PlayerCharactersClient.MOD_ID)
			.orElseThrow(() -> new IOException("mod not found"));
		Path source = mod.findPath(RESOURCE).orElseThrow(() -> new IOException("the speech model is missing from the mod jar"));
		Path target = FOLDER.resolve(NAME);
		String version = NAME + " for " + mod.getMetadata().getVersion().getFriendlyString();
		Path done = target.resolve(DONE_FILE);
		if (Files.isRegularFile(done) && Files.readString(done, StandardCharsets.UTF_8).strip().equals(version)) {
			return target;
		}

		PlayerCharactersClient.LOGGER.info("Unpacking the speech model to {}", target);
		List<Path> files;
		try (Stream<Path> walk = Files.walk(source)) {
			files = walk.filter(Files::isRegularFile).toList();
		}
		for (Path file : files) {
			// The source is inside the jar, so build the target path name by name.
			Path out = target;
			for (Path name : source.relativize(file)) {
				out = out.resolve(name.toString());
			}
			Files.createDirectories(out.getParent());
			Files.copy(file, out, StandardCopyOption.REPLACE_EXISTING);
		}
		Files.writeString(done, version, StandardCharsets.UTF_8);
		return target;
	}

	/**
	 * Vosk's native code opens files with plain 8-bit paths, which on Windows can't hold every character (for example
	 * some user names). Minecraft runs inside its game folder, so a path relative to it avoids the problem.
	 */
	private static String pathForNativeCode(Path folder) {
		Path absolute = folder.toAbsolutePath().normalize();
		Path workingDir = Path.of("").toAbsolutePath().normalize();
		if (absolute.startsWith(workingDir) && !absolute.equals(workingDir)) {
			String relative = workingDir.relativize(absolute).toString();
			if (relative.chars().allMatch(c -> c < 128)) {
				return relative;
			}
		}
		return absolute.toString();
	}
}
