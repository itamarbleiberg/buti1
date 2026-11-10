package com.playercharacters.speech;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.playercharacters.PlayerCharactersClient;
import com.playercharacters.config.CharacterSettings;
import com.playercharacters.effects.FartEffects;
import com.playercharacters.effects.MiddleFingerEmote;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;
import org.vosk.Model;
import org.vosk.Recognizer;

/**
 * Listens for "fart" and "fuck you" in voice chat: in the voices of everyone you can hear, and in your own microphone.
 * <p>
 * Uses Vosk offline speech recognition on its own thread, so voice chat is never slowed down. The recognizer only
 * knows a handful of words: the two phrases, and words that sound like them ("far", "part", "thank you", "luck"...),
 * so that near misses are heard as those words instead. Nothing is sent anywhere; it all happens on this computer.
 * <p>
 * Tested with 168 computer-voiced sentences in six accents: "fart" in 32 of 36, "fuck you" in 36 of 36, and one false
 * "fart" in the 168 sentences, which include lookalikes such as "farm", "heart", "thank you" and "what the fuck".
 */
public final class VoiceEmoteDetector {
	/** The phrases, and similar-sounding words that catch near misses. "[unk]" stands for any other word. */
	private static final String GRAMMAR = "[\"fart\", \"far\", \"part\", \"start\", \"heart\", \"art\", \"farm\", \"fast\", \"car\", \"dart\", "
		+ "\"fuck you\", \"fuck\", \"you\", \"thank you\", \"for you\", \"to you\", \"luck\", \"duck\", \"stuck\", \"truck\", \"back\", "
		+ "\"look\", \"book\", \"hook\", \"fun\", \"fork\", \"folk\", \"[unk]\"]";
	private static final int SAMPLE_RATE = 16_000;
	/** Audio goes to the recognizer in 100 ms pieces. */
	private static final int CHUNK = SAMPLE_RATE / 10;
	/** Someone counts as done talking after this long without audio. */
	private static final long DONE_AFTER_NANOS = TimeUnit.MILLISECONDS.toNanos(350);
	/** The recognizer of someone who stopped talking is freed after this long. */
	private static final long FORGET_AFTER_NANOS = TimeUnit.SECONDS.toNanos(30);
	/** 40 seconds of 20 ms voice chat frames. */
	private static final BlockingQueue<Job> QUEUE = new ArrayBlockingQueue<>(2000);

	private sealed interface Job permits Audio, Stopped, Clear {
	}

	private record Audio(UUID speaker, short[] pcm) implements Job {
	}

	private record Stopped(UUID speaker) implements Job {
	}

	private record Clear() implements Job {
	}

	private static volatile @Nullable Thread worker;
	private static volatile boolean warnedFull;

	private VoiceEmoteDetector() {
	}

	/**
	 * Feeds one voice chat frame. Called on voice chat threads; only copies the audio and returns.
	 *
	 * @param speaker the player talking
	 * @param pcm     20 ms of 48 kHz 16 bit mono audio; empty means they stopped talking
	 */
	public static void onAudio(@Nullable UUID speaker, short @Nullable [] pcm) {
		if (speaker == null || !anyEmoteOn() || SpeechModel.status() == SpeechModel.Status.UNAVAILABLE) {
			return;
		}
		startWorker();
		Job job = pcm == null || pcm.length == 0 ? new Stopped(speaker) : new Audio(speaker, pcm.clone());
		if (!QUEUE.offer(job) && !warnedFull) {
			warnedFull = true;
			PlayerCharactersClient.LOGGER.warn("Voice emotes can't keep up with voice chat, skipping some audio");
		}
	}

	/** Forgets everyone, for example after leaving a server. */
	public static void clear() {
		if (worker != null) {
			QUEUE.offer(new Clear());
		}
	}

	private static void startWorker() {
		if (worker != null) {
			return;
		}
		synchronized (VoiceEmoteDetector.class) {
			if (worker == null) {
				Thread thread = new Thread(VoiceEmoteDetector::run, "Player Characters voice emotes");
				thread.setDaemon(true);
				thread.setPriority(Thread.NORM_PRIORITY - 1);
				thread.start();
				worker = thread;
			}
		}
	}

	private static void run() {
		Map<UUID, Listener> listeners = new HashMap<>();
		while (true) {
			Job job;
			try {
				job = QUEUE.poll(100, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				return;
			}
			try {
				if (job != null) {
					handle(job, listeners);
				}
				sweep(listeners, System.nanoTime());
			} catch (RuntimeException e) {
				PlayerCharactersClient.LOGGER.warn("Voice emote detection failed, starting over", e);
				listeners.values().forEach(Listener::close);
				listeners.clear();
			}
		}
	}

	private static void handle(Job job, Map<UUID, Listener> listeners) {
		switch (job) {
			case Audio audio -> {
				Listener listener = listeners.get(audio.speaker());
				if (listener == null) {
					Model model = SpeechModel.get();
					if (model == null) {
						QUEUE.clear();
						return;
					}
					try {
						listener = new Listener(audio.speaker(), model);
					} catch (IOException e) {
						PlayerCharactersClient.LOGGER.warn("Couldn't start listening to {}", audio.speaker(), e);
						return;
					}
					listeners.put(audio.speaker(), listener);
				}
				listener.accept(audio.pcm(), System.nanoTime());
			}
			case Stopped stopped -> {
				Listener listener = listeners.get(stopped.speaker());
				if (listener != null) {
					listener.finish();
				}
			}
			case Clear ignored -> {
				listeners.values().forEach(Listener::close);
				listeners.clear();
			}
		}
	}

	/** Finishes sentences of people who went quiet, and frees what isn't needed anymore. */
	private static void sweep(Map<UUID, Listener> listeners, long now) {
		Iterator<Listener> iterator = listeners.values().iterator();
		while (iterator.hasNext()) {
			Listener listener = iterator.next();
			long quiet = now - listener.lastAudioNanos;
			if (quiet > DONE_AFTER_NANOS) {
				listener.finish();
			}
			if (quiet > FORGET_AFTER_NANOS || !anyEmoteOn()) {
				listener.close();
				iterator.remove();
			}
		}
		if (listeners.isEmpty() && !anyEmoteOn()) {
			SpeechModel.close(); // turned off in the menu: give the memory back
		}
	}

	private static boolean anyEmoteOn() {
		CharacterSettings settings = CharacterSettings.get();
		return settings.fartDetection() || settings.middleFinger();
	}

	/** How often each emote's phrase is in a recognized text: {farts, middle fingers}. */
	static int[] count(String text) {
		String[] words = text.strip().split("\\s+");
		int[] counts = new int[2];
		for (int i = 0; i < words.length; i++) {
			if (words[i].equals("fart")) {
				counts[0]++;
			} else if (words[i].equals("fuck") && i + 1 < words.length && words[i + 1].equals("you")) {
				counts[1]++;
			}
		}
		return counts;
	}

	/** The value of {@code key} in one of Vosk's JSON results. */
	static String text(String json, String key) {
		try {
			JsonObject object = JsonParser.parseString(json).getAsJsonObject();
			JsonElement value = object.get(key);
			return value == null ? "" : value.getAsString();
		} catch (RuntimeException e) {
			return "";
		}
	}

	private static void heard(UUID speaker, int emote) {
		PlayerCharactersClient.LOGGER.debug("Heard {} from {}", emote == 0 ? "\"fart\"" : "\"fuck you\"", speaker);
		Minecraft.getInstance().execute(() -> {
			if (emote == 0) {
				FartEffects.start(speaker);
			} else {
				MiddleFingerEmote.start(speaker);
			}
		});
	}

	/** Speech recognition for one person. */
	private static final class Listener {
		private final UUID speaker;
		private final Recognizer recognizer;
		private final Downsampler downsampler = new Downsampler();
		private final short[] chunk = new short[CHUNK];
		private final short[] frame = new short[1024];
		private int chunkLength;
		/** How many times each phrase was already acted on in the sentence being spoken. */
		private final int[] handled = new int[2];
		private boolean talking;
		long lastAudioNanos;

		Listener(UUID speaker, Model model) throws IOException {
			this.speaker = speaker;
			this.recognizer = new Recognizer(model, SAMPLE_RATE, GRAMMAR);
		}

		void accept(short[] pcm, long now) {
			this.lastAudioNanos = now;
			this.talking = true;
			for (int offset = 0; offset < pcm.length; offset += this.frame.length * 3) {
				int length = Math.min(this.frame.length * 3, pcm.length - offset);
				int samples = this.downsampler.process(pcm, offset, length, this.frame);
				for (int i = 0; i < samples; i++) {
					this.chunk[this.chunkLength++] = this.frame[i];
					if (this.chunkLength == CHUNK) {
						this.feed();
					}
				}
			}
		}

		private void feed() {
			boolean sentenceEnded = this.recognizer.acceptWaveForm(this.chunk, this.chunkLength);
			this.chunkLength = 0;
			if (sentenceEnded) {
				this.check(text(this.recognizer.getResult(), "text"), true);
			} else {
				// The guess so far. Acting on it makes the fart come right after the word, not after the sentence.
				this.check(text(this.recognizer.getPartialResult(), "partial"), false);
			}
		}

		/** They stopped talking: get the final result for what they said last. */
		void finish() {
			if (!this.talking) {
				return;
			}
			this.talking = false;
			if (this.chunkLength > 0) {
				this.feed();
			}
			this.check(text(this.recognizer.getFinalResult(), "text"), true);
		}

		private void check(String text, boolean sentenceEnded) {
			int[] counts = count(text);
			for (int emote = 0; emote < counts.length; emote++) {
				if (counts[emote] > this.handled[emote]) {
					heard(this.speaker, emote);
					this.handled[emote] = counts[emote];
				}
				if (sentenceEnded) {
					this.handled[emote] = 0;
				}
			}
		}

		void close() {
			this.recognizer.close();
		}
	}

	/** 48 kHz to 16 kHz: a low-pass filter (windowed sinc, cut off at 7.2 kHz), then every third sample. */
	static final class Downsampler {
		private static final float[] TAPS = taps(31, 7200.0 / 48000.0);
		/** The last samples, stored twice so the newest {@code TAPS.length} are always in one row. */
		private final float[] history = new float[TAPS.length * 2];
		private int position;
		private int phase;

		private static float[] taps(int count, double cutoff) {
			float[] taps = new float[count];
			double sum = 0.0;
			for (int i = 0; i < count; i++) {
				double x = i - (count - 1) / 2.0;
				double sinc = x == 0.0 ? 2.0 * cutoff : Math.sin(2.0 * Math.PI * cutoff * x) / (Math.PI * x);
				double window = 0.54 - 0.46 * Math.cos(2.0 * Math.PI * i / (count - 1));
				taps[i] = (float) (sinc * window);
				sum += taps[i];
			}
			for (int i = 0; i < count; i++) {
				taps[i] /= (float) sum;
			}
			return taps;
		}

		/** Returns how many samples were written to {@code out} (a third of {@code length}, rounded either way). */
		int process(short[] in, int offset, int length, short[] out) {
			int written = 0;
			int n = TAPS.length;
			for (int i = offset; i < offset + length; i++) {
				this.history[this.position] = in[i];
				this.history[this.position + n] = in[i];
				this.position = (this.position + 1) % n;
				if (this.phase == 0) {
					float sum = 0.0F;
					for (int k = 0; k < n; k++) {
						sum += TAPS[k] * this.history[this.position + k];
					}
					out[written++] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(sum)));
				}
				this.phase = (this.phase + 1) % 3;
			}
			return written;
		}
	}
}
