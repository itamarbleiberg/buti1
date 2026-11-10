package com.playercharacters.speech;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.playercharacters.PlayerCharactersClient;
import com.playercharacters.config.CharacterSettings;
import com.playercharacters.effects.FartEffects;
import com.playercharacters.effects.MiddleFingerEmote;
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
 * Listens for "fart", "fuck you" and "fuck off" in voice chat: in the voices of everyone you can hear, and in your own
 * microphone.
 * <p>
 * Uses Vosk offline speech recognition on its own thread, so voice chat is never slowed down. Each recognizer only
 * knows a handful of words: the phrases, and words that sound like them ("far", "part", "thank you", "luck", "cough"...),
 * so that near misses are heard as those words instead. There is one recognizer for farts and one for the middle
 * finger, because adding "fuck off" to the fart recognizer made it miss more farts. Nothing is sent anywhere; it all
 * happens on this computer.
 * <p>
 * Tested with 372 computer-voiced sentences in six accents: "fart" was caught in 32 of 36, "fuck you" and "fuck off" in
 * 72 of 72. The other sentences, which include lookalikes such as "farm", "heart", "thank you", "back off",
 * "what the fuck" and "oh fuck, I died", started one fart (of 336) and no middle finger (of 300).
 */
public final class VoiceEmoteDetector {
	private static final String FART_WORDS = "\"fart\", \"far\", \"part\", \"start\", \"heart\", \"art\", \"farm\", \"fast\", \"car\", \"dart\"";
	private static final String FUCK_YOU_WORDS = "\"fuck you\", \"fuck\", \"you\", \"thank you\", \"for you\", \"to you\", \"luck\", \"duck\", "
		+ "\"stuck\", \"truck\", \"back\", \"look\", \"book\", \"hook\", \"fun\", \"fork\", \"folk\"";
	private static final String FUCK_OFF_WORDS = "\"fuck off\", \"off\", \"cough\", \"back off\", \"show off\", \"took off\", \"fall off\", "
		+ "\"kick off\", \"fork off\", \"i\", \"died\"";
	/** For farts: the phrases and similar-sounding words that catch near misses. "[unk]" stands for any other word. */
	private static final String FART_GRAMMAR = "[" + FART_WORDS + ", " + FUCK_YOU_WORDS + ", \"[unk]\"]";
	/** For the middle finger: the same, plus "fuck off" and its lookalikes. */
	private static final String MIDDLE_FINGER_GRAMMAR = "[" + FART_WORDS + ", " + FUCK_YOU_WORDS + ", " + FUCK_OFF_WORDS + ", \"[unk]\"]";
	private static final int FART = 0;
	private static final int MIDDLE_FINGER = 1;
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
					} catch (RuntimeException e) {
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

	/** How often each emote's phrase is in a recognized text: {farts, middle fingers ("fuck you" or "fuck off")}. */
	static int[] count(String text) {
		String[] words = text.strip().split("\\s+");
		int[] counts = new int[2];
		for (int i = 0; i < words.length; i++) {
			if (words[i].equals("fart")) {
				counts[FART]++;
			} else if (words[i].equals("fuck") && i + 1 < words.length && (words[i + 1].equals("you") || words[i + 1].equals("off"))) {
				counts[MIDDLE_FINGER]++;
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
		PlayerCharactersClient.LOGGER.debug("Heard {} from {}", emote == FART ? "\"fart\"" : "\"fuck you\" or \"fuck off\"", speaker);
		Minecraft.getInstance().execute(() -> {
			if (emote == FART) {
				FartEffects.start(speaker);
			} else {
				MiddleFingerEmote.start(speaker);
			}
		});
	}

	private static boolean isOn(int emote) {
		CharacterSettings settings = CharacterSettings.get();
		return emote == FART ? settings.fartDetection() : settings.middleFinger();
	}

	/** Speech recognition for one person: a recognizer for each emote, fed the same audio. */
	private static final class Listener {
		private final UUID speaker;
		private final Spotter[] spotters;
		private final Downsampler downsampler = new Downsampler();
		private final short[] chunk = new short[CHUNK];
		private final short[] frame = new short[1024];
		private int chunkLength;
		private boolean talking;
		long lastAudioNanos;

		Listener(UUID speaker, Model model) {
			this.speaker = speaker;
			Spotter fart = new Spotter(FART, new Recognizer(model, SAMPLE_RATE, FART_GRAMMAR));
			try {
				this.spotters = new Spotter[] {fart, new Spotter(MIDDLE_FINGER, new Recognizer(model, SAMPLE_RATE, MIDDLE_FINGER_GRAMMAR))};
			} catch (RuntimeException e) {
				fart.recognizer.close();
				throw e;
			}
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
			for (Spotter spotter : this.spotters) {
				if (isOn(spotter.emote)) {
					spotter.feed(this.speaker, this.chunk, this.chunkLength);
				}
			}
			this.chunkLength = 0;
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
			for (Spotter spotter : this.spotters) {
				spotter.finish(this.speaker);
			}
		}

		void close() {
			for (Spotter spotter : this.spotters) {
				spotter.recognizer.close();
			}
		}
	}

	/** One recognizer, listening for one emote's phrase. */
	private static final class Spotter {
		final int emote;
		final Recognizer recognizer;
		/** How many times the phrase was already acted on in the sentence being spoken. */
		private int handled;

		Spotter(int emote, Recognizer recognizer) {
			this.emote = emote;
			this.recognizer = recognizer;
		}

		void feed(UUID speaker, short[] chunk, int length) {
			if (this.recognizer.acceptWaveForm(chunk, length)) {
				this.check(speaker, text(this.recognizer.getResult(), "text"), true);
			} else {
				// The guess so far. Acting on it makes the emote come right after the words, not after the sentence.
				this.check(speaker, text(this.recognizer.getPartialResult(), "partial"), false);
			}
		}

		void finish(UUID speaker) {
			this.check(speaker, text(this.recognizer.getFinalResult(), "text"), true);
		}

		private void check(UUID speaker, String text, boolean sentenceEnded) {
			int count = count(text)[this.emote];
			if (count > this.handled) {
				heard(speaker, this.emote);
				this.handled = count;
			}
			if (sentenceEnded) {
				this.handled = 0;
			}
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
