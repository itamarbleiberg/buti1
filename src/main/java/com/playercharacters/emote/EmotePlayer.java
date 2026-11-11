package com.playercharacters.emote;

import com.playercharacters.CharacterResolver;
import com.playercharacters.character.PlayerCharacter;
import com.playercharacters.config.CharacterSettings;
import com.playercharacters.effects.FartEffects;
import com.playercharacters.effects.MiddleFingerEmote;
import com.playercharacters.emote.Emote.Music;
import com.playercharacters.emote.Emote.Track;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Plays emotes: which player is doing which one and for how long, their music and particles, and stopping them when
 * the player walks away. Runs on the client thread; timing is in ticks. Every client plays emotes on its own, started
 * by the emote wheel or by {@link EmoteChat}.
 */
public final class EmotePlayer {
	private static final float FADE_IN = 0.25F;
	private static final float FADE_OUT = 0.3F;
	private static final int STOP_TICKS = 6;
	/** Emotes that loop stop by themselves after this long. */
	private static final int MAX_LOOP_TICKS = 20 * 30;
	/** Moving farther than this from where the emote started stops it, like walking away. */
	private static final double MOVE_LIMIT_SQR = 0.3 * 0.3;
	private static final double HEARING_SQR = 48.0 * 48.0;
	private static final RandomSource RANDOM = RandomSource.create();
	private static final Map<UUID, Playing> ACTIVE = new HashMap<>();
	private static int started;
	private static int notes;
	/** The camera before an emote switched the local player to the front view, to switch back afterwards. */
	private static @Nullable CameraType cameraBefore;
	private static @Nullable Preview preview;

	private EmotePlayer() {
	}

	private static final class Playing {
		final Emote emote;
		final Vec3 start;
		int age;
		int stopAge = -1;

		Playing(Emote emote, Vec3 start) {
			this.emote = emote;
			this.start = start;
		}

		boolean stopping() {
			return this.stopAge >= 0;
		}
	}

	/** While the emote wheel draws its preview: the emote to show on one player, and since when. */
	private record Preview(UUID player, Emote emote, long startMillis) {
	}

	/** Starts a pose emote for a player, replacing the one they were doing. */
	public static void start(Player player, Emote emote) {
		if (!emote.isPose()) {
			return;
		}
		ACTIVE.put(player.getUUID(), new Playing(emote, player.position()));
		started++;
	}

	/** Plays an emote as yourself, from the emote wheel: also shows it to other players who have the mod. */
	public static void playLocal(Emote emote) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		switch (emote.kind()) {
			case FART -> FartEffects.start(player.getUUID());
			case MIDDLE_FINGER -> MiddleFingerEmote.start(player.getUUID());
			case POSE -> {
				start(player, emote);
				// Like in Fortnite: watch yourself do it. Back to first person when it ends.
				if (client.options.getCameraType().isFirstPerson()) {
					cameraBefore = client.options.getCameraType();
					client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				}
			}
		}
		EmoteChat.announce(emote);
	}

	/** Stops a player's emote (it blends back to normal over a few ticks). */
	public static void stop(UUID player) {
		Playing playing = ACTIVE.get(player);
		if (playing != null && !playing.stopping()) {
			playing.stopAge = playing.age;
		}
	}

	/** The emote this player is doing, or null. */
	public static @Nullable Emote playing(UUID player) {
		Playing playing = ACTIVE.get(player);
		return playing == null || playing.stopping() ? null : playing.emote;
	}

	/** How many emotes were started since the game started. */
	public static int startedCount() {
		return started;
	}

	/** How many music notes emotes played since the game started. */
	public static int notesPlayed() {
		return notes;
	}

	public static void clear() {
		ACTIVE.clear();
	}

	/** This frame's pose for a player, or null when they aren't doing an emote. */
	public static @Nullable EmotePose pose(UUID player, float partialTick) {
		Preview shown = preview;
		if (shown != null && shown.player.equals(player)) {
			return previewPose(shown);
		}
		Playing playing = ACTIVE.get(player);
		if (playing == null) {
			return null;
		}
		float t = (playing.age + partialTick) / 20.0F;
		float weight = smooth(t / FADE_IN);
		if (playing.stopping()) {
			weight *= 1 - smooth((playing.age - playing.stopAge + partialTick) / STOP_TICKS);
		} else if (!playing.emote.loops()) {
			weight *= 1 - smooth((t - (playing.emote.seconds() - FADE_OUT)) / FADE_OUT);
		}
		EmotePose pose = new EmotePose();
		playing.emote.animation().pose(pose, t);
		pose.weight = weight;
		return pose;
	}

	// ------------------------------------------------------------------ preview

	/** Runs {@code draw} with {@code player} doing {@code emote}, started at {@code startMillis}. Render thread only. */
	public static void withPreview(UUID player, Emote emote, long startMillis, Runnable draw) {
		Preview previous = preview;
		preview = new Preview(player, emote, startMillis);
		try {
			draw.run();
		} finally {
			preview = previous;
		}
	}

	/** The emote being previewed on this player (also the fart and the middle finger), or null. */
	public static @Nullable Emote previewEmote(UUID player) {
		Preview shown = preview;
		return shown != null && shown.player.equals(player) ? shown.emote : null;
	}

	/** Seconds into the previewed emote, repeating. */
	public static float previewSeconds() {
		Preview shown = preview;
		if (shown == null) {
			return 0.0F;
		}
		float t = (Util.getMillis() - shown.startMillis) / 1000.0F;
		float round = shown.emote.loops() ? Math.max(shown.emote.seconds(), 3.0F) : shown.emote.seconds() + 0.6F;
		return t % round;
	}

	private static @Nullable EmotePose previewPose(Preview shown) {
		if (!shown.emote.isPose()) {
			return null;
		}
		float t = previewSeconds();
		EmotePose pose = new EmotePose();
		shown.emote.animation().pose(pose, t);
		float weight = smooth(t / FADE_IN);
		if (!shown.emote.loops()) {
			weight *= 1 - smooth((t - (shown.emote.seconds() - FADE_OUT)) / FADE_OUT);
		}
		pose.weight = weight;
		return pose;
	}

	// ------------------------------------------------------------------ ticking

	/** Called every client tick: ages emotes, stops finished ones, and plays their music and particles. */
	public static void tick(Minecraft client) {
		ClientLevel level = client.level;
		if (level == null) {
			ACTIVE.clear();
			cameraBefore = null;
			return;
		}
		if (client.isPaused()) {
			return;
		}
		Iterator<Map.Entry<UUID, Playing>> iterator = ACTIVE.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Playing> entry = iterator.next();
			Playing playing = entry.getValue();
			Player player = level.getPlayerByUUID(entry.getKey());
			if (player == null) {
				iterator.remove();
				continue;
			}
			playing.age++;
			if (!playing.stopping() && (shouldStop(player, playing) || playing.age >= MAX_LOOP_TICKS)) {
				playing.stopAge = playing.age;
			}
			boolean finished = playing.stopping() ? playing.age - playing.stopAge >= STOP_TICKS
				: !playing.emote.loops() && playing.age >= playing.emote.seconds() * 20;
			if (finished) {
				iterator.remove();
				continue;
			}
			if (playing.stopping() || client.player == null || client.player.distanceToSqr(player) > HEARING_SQR) {
				continue;
			}
			float height = heightOf(player);
			if (playing.emote.music() != null && CharacterSettings.get().emoteMusic()) {
				playMusic(client, player, playing.emote.music(), playing.age, height);
			}
			if (playing.emote.effects() != null) {
				playing.emote.effects().tick(player, playing.age, height);
			}
		}
		restoreCamera(client);
	}

	private static boolean shouldStop(Player player, Playing playing) {
		Vec3 now = player.position();
		double dx = now.x - playing.start.x;
		double dz = now.z - playing.start.z;
		return dx * dx + dz * dz > MOVE_LIMIT_SQR || Math.abs(now.y - playing.start.y) > 0.6 || player.getPose() != Pose.STANDING
			|| player.isPassenger() || player.isDeadOrDying() || player.swinging || player.isUsingItem();
	}

	private static void restoreCamera(Minecraft client) {
		if (cameraBefore == null || client.player == null || ACTIVE.containsKey(client.player.getUUID())) {
			return;
		}
		// Only if it is still the view the emote switched to: the player may have changed it themselves.
		if (client.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
			client.options.setCameraType(cameraBefore);
		}
		cameraBefore = null;
	}

	private static float heightOf(Player player) {
		PlayerCharacter character = CharacterResolver.characterFor(player.getUUID());
		return character != null && character.model() != null ? character.model().height() : 1.8F;
	}

	private static void playMusic(Minecraft client, Player player, Music music, int age, float height) {
		int tick = age - 1; // the first tick plays the first beat
		if (tick % music.ticksPerStep() != 0) {
			return;
		}
		int step = tick / music.ticksPerStep();
		for (Track track : music.tracks()) {
			int note = track.notes()[step % track.notes().length];
			if (note >= 0) {
				play(client, player, track.sound(), track.volume(), note);
			}
		}
		if (step % 4 == 0) {
			double x = player.getX() + (RANDOM.nextDouble() - 0.5) * 0.6;
			double z = player.getZ() + (RANDOM.nextDouble() - 0.5) * 0.6;
			// For note particles the x speed picks the color.
			player.level().addParticle(ParticleTypes.NOTE, x, player.getY() + (height + 0.35) * player.getScale(), z, RANDOM.nextInt(25) / 24.0, 0.0, 0.0);
		}
	}

	private static void play(Minecraft client, Player player, Holder<SoundEvent> sound, float volume, int note) {
		notes++;
		float pitch = (float) Math.pow(2.0, (note - 12) / 12.0);
		client.getSoundManager().play(new SimpleSoundInstance(sound.value().location(), SoundSource.RECORDS, volume, pitch, RandomSource.create(), false, 0,
			SoundInstance.Attenuation.LINEAR, player.getX(), player.getY() + 1.0, player.getZ(), false));
	}

	private static float smooth(float x) {
		float t = Mth.clamp(x, 0.0F, 1.0F);
		return t * t * (3 - 2 * t);
	}
}
