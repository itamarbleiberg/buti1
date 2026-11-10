package com.playercharacters.voice;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * Turns voice chat audio into "mouth open / mouth closed" per player.
 * <p>
 * Simple Voice Chat delivers audio in 20 ms frames. The mouth opens on loud parts of speech (syllables)
 * and closes on the quieter gaps in between, measured relative to how loud that person recently was,
 * so quiet and loud microphones both flap naturally. Audio arrives on voice chat threads and is read
 * on the render thread, hence the concurrent map and volatile fields.
 */
public final class TalkTracker {
	/** Mouth closes if no audio arrived for this long (the speaker stopped or packets were lost). */
	private static final long STALE_AFTER_NANOS = TimeUnit.MILLISECONDS.toNanos(160);
	/** Anything quieter than this is treated as silence. */
	private static final float SILENCE_DB = -50.0F;
	/** Lowest value the "recent peak" can fall to, so background noise doesn't count as speech. */
	private static final float PEAK_FLOOR_DB = -42.0F;
	/** How fast the recent peak falls, per 20 ms frame (12 dB per second). */
	private static final float PEAK_DECAY_DB = 0.24F;
	/** Open when the frame is at most this far below the recent peak. */
	private static final float OPEN_WITHIN_DB = 9.0F;
	/** Close when the frame drops further than this below the recent peak. */
	private static final float CLOSE_BELOW_DB = 13.0F;
	/** Minimum frames to stay open / closed, so the mouth doesn't flicker. */
	private static final int MIN_OPEN_FRAMES = 3;
	private static final int MIN_CLOSED_FRAMES = 2;

	private static final Map<UUID, Voice> VOICES = new ConcurrentHashMap<>();

	private TalkTracker() {
	}

	/**
	 * Feeds one audio frame from a player.
	 *
	 * @param speaker the player's UUID
	 * @param pcm     16 bit mono PCM; empty means the player stopped talking
	 */
	public static void onAudioFrame(@Nullable UUID speaker, short @Nullable [] pcm) {
		if (speaker == null) {
			return;
		}
		if (pcm == null || pcm.length == 0) {
			Voice voice = VOICES.get(speaker);
			if (voice != null) {
				voice.stop();
			}
			return;
		}
		VOICES.computeIfAbsent(speaker, id -> new Voice()).update(levelDb(pcm), System.nanoTime());
	}

	public static boolean isMouthOpen(UUID player) {
		Voice voice = VOICES.get(player);
		return voice != null && voice.open && System.nanoTime() - voice.lastFrameNanos < STALE_AFTER_NANOS;
	}

	/** How wide the mouth should be: 0 when closed, otherwise 0.45 (quiet syllable) up to 1 (loudest). */
	public static float mouthOpenness(UUID player) {
		Voice voice = VOICES.get(player);
		return voice != null && voice.open && System.nanoTime() - voice.lastFrameNanos < STALE_AFTER_NANOS ? voice.openness : 0.0F;
	}

	public static void clear() {
		VOICES.clear();
	}

	/** Loudness of a frame in dB relative to full scale (0 dB = loudest possible, about -100 dB = silence). */
	static float levelDb(short[] pcm) {
		double sumOfSquares = 0.0;
		for (short sample : pcm) {
			sumOfSquares += (double) sample * sample;
		}
		double rms = Math.sqrt(sumOfSquares / pcm.length) / 32768.0;
		return rms < 1.0E-5 ? -100.0F : (float) (20.0 * Math.log10(rms));
	}

	private static final class Voice {
		private float peakDb = PEAK_FLOOR_DB;
		private int framesInState = MIN_CLOSED_FRAMES;
		private volatile boolean open;
		private volatile float openness;
		private volatile long lastFrameNanos;

		synchronized void update(float levelDb, long now) {
			if (this.lastFrameNanos == 0L || now - this.lastFrameNanos > STALE_AFTER_NANOS) {
				// First audio after a pause: start fresh instead of comparing against old loudness.
				this.peakDb = PEAK_FLOOR_DB;
				this.open = false;
				this.framesInState = MIN_CLOSED_FRAMES;
			}

			this.peakDb = Math.max(PEAK_FLOOR_DB, Math.max(levelDb, this.peakDb - PEAK_DECAY_DB));
			float belowPeak = this.peakDb - levelDb;
			boolean loudEnough = levelDb > SILENCE_DB;
			this.framesInState++;

			if (this.open) {
				if (this.framesInState >= MIN_OPEN_FRAMES && (!loudEnough || belowPeak > CLOSE_BELOW_DB)) {
					this.open = false;
					this.framesInState = 0;
				}
			} else if (this.framesInState >= MIN_CLOSED_FRAMES && loudEnough && belowPeak <= OPEN_WITHIN_DB) {
				this.open = true;
				this.framesInState = 0;
			}
			float loudness = Math.max(0.0F, Math.min(1.0F, 1.0F - belowPeak / CLOSE_BELOW_DB));
			this.openness = 0.45F + 0.55F * loudness;
			this.lastFrameNanos = now;
		}

		synchronized void stop() {
			this.open = false;
			this.framesInState = MIN_CLOSED_FRAMES;
		}
	}
}
