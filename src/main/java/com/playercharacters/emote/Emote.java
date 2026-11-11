package com.playercharacters.emote;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * An emote from the emote wheel: an animation of the whole character, with optional music and particles.
 * The fart and the middle finger are emotes too, played by their own code.
 *
 * @param id       stable name, used for translations and music file names
 * @param chat     what is sent as {@code /me <chat>} so that other players with the mod see the emote too
 * @param seconds  how long it lasts, or how long one round takes for emotes that loop
 * @param loops    whether it repeats until the player sneaks (or it has gone on for a minute)
 * @param ownLegs  the emote moves the legs itself (the Griddy, sitting): walking doesn't take them over
 * @param iconTime seconds into the emote that its picture in the wheel shows
 */
public record Emote(String id, String chat, Kind kind, float seconds, boolean loops, Animation animation, @Nullable Music music,
	@Nullable Effects effects, boolean ownLegs, float iconTime) {
	public enum Kind {
		POSE,
		FART,
		MIDDLE_FINGER
	}

	/** Fills in the pose {@code t} seconds after the emote started. */
	@FunctionalInterface
	public interface Animation {
		void pose(EmotePose pose, float t);
	}

	/** Particles and sounds, once per tick while the emote plays. {@code age} counts ticks from the start. */
	@FunctionalInterface
	public interface Effects {
		void tick(Player player, int age, float height);
	}

	/**
	 * A short loop of note block music. Every track has one entry per step: a note from 0 to 24 (like a note block's
	 * pitch), or -1 for silence.
	 */
	public record Music(int ticksPerStep, List<Track> tracks) {
		public int steps() {
			int steps = 1;
			for (Track track : this.tracks) {
				steps = Math.max(steps, track.notes().length);
			}
			return steps;
		}
	}

	public record Track(Holder<SoundEvent> sound, float volume, int[] notes) {
		/** "x" plays {@code note}, anything else is silence: "x...x..." */
		public static Track hits(Holder<SoundEvent> sound, float volume, int note, String pattern) {
			int[] notes = new int[pattern.length()];
			for (int i = 0; i < notes.length; i++) {
				notes[i] = pattern.charAt(i) == 'x' ? note : -1;
			}
			return new Track(sound, volume, notes);
		}

		/** Notes separated by spaces, "." for silence: "0 . 5 . 7 ." */
		public static Track notes(Holder<SoundEvent> sound, float volume, String pattern) {
			String[] tokens = pattern.trim().split("\\s+");
			int[] notes = new int[tokens.length];
			for (int i = 0; i < tokens.length; i++) {
				notes[i] = tokens[i].equals(".") ? -1 : Integer.parseInt(tokens[i]);
			}
			return new Track(sound, volume, notes);
		}
	}

	public boolean isPose() {
		return this.kind == Kind.POSE;
	}

	public String translationKey() {
		return "playercharacters.emote." + this.id;
	}
}
