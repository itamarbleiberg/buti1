package com.playercharacters.emote;

import com.playercharacters.emote.Emote.Kind;
import com.playercharacters.emote.Emote.Music;
import com.playercharacters.emote.Emote.Track;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Every emote in the emote wheel, in wheel order (8 per page). The animations are written as math: smooth waves and
 * blends between poses, timed in seconds.
 */
public final class Emotes {
	private static final float PI = (float) Math.PI;
	private static final RandomSource RANDOM = RandomSource.create();
	private static final List<Emote> ALL = new ArrayList<>();
	private static final Map<String, Emote> BY_ID = new LinkedHashMap<>();
	private static final Map<String, Emote> BY_CHAT = new LinkedHashMap<>();

	// ------------------------------------------------------------------ page 1

	public static final Emote DEFAULT_DANCE = add(pose("default_dance", "does the Default Dance", 4.0F, true, Emotes::defaultDance, music(5,
		Track.hits(SoundEvents.NOTE_BLOCK_BASEDRUM, 0.75F, 6, "x.x.x.x.x.x.x.x."),
		Track.hits(SoundEvents.NOTE_BLOCK_SNARE, 0.5F, 12, "..x...x...x...x."),
		Track.hits(SoundEvents.NOTE_BLOCK_HAT, 0.3F, 18, ".x.x.x.x.x.x.x.x"),
		Track.notes(SoundEvents.NOTE_BLOCK_BASS, 0.7F, "6 . 6 . 9 . 9 . 11 . 11 . 9 . 13 .")), null, false, 0.3F));
	public static final Emote SIX_SEVEN = add(pose("six_seven", "does the 67", 1.6F, true, Emotes::sixSeven, music(6,
		Track.hits(SoundEvents.NOTE_BLOCK_BASEDRUM, 0.75F, 6, "x..x..x.x..x..x."),
		Track.hits(SoundEvents.NOTE_BLOCK_SNARE, 0.45F, 12, "....x.......x..."),
		Track.notes(SoundEvents.NOTE_BLOCK_PLING, 0.55F, "6 . . 7 . . . . 6 . . 7 . . . .")), null, false, 0.4F));
	public static final Emote FLOSS = add(pose("floss", "flosses", 1.2F, true, Emotes::floss, music(4,
		Track.hits(SoundEvents.NOTE_BLOCK_BASEDRUM, 0.75F, 6, "x...x...x...x..."),
		Track.hits(SoundEvents.NOTE_BLOCK_SNARE, 0.5F, 12, "....x.......x..."),
		Track.hits(SoundEvents.NOTE_BLOCK_HAT, 0.22F, 20, "xxxxxxxxxxxxxxxx"),
		Track.notes(SoundEvents.NOTE_BLOCK_BIT, 0.45F, "13 . 13 . 16 . 13 . 11 . 11 . 9 . 8 .")), null, false, 0.45F));
	public static final Emote WAVE = add(pose("wave", "waves", 2.5F, false, Emotes::wave, null, null, false, 0.9F));
	public static final Emote DAB = add(pose("dab", "dabs", 2.0F, false, Emotes::dab, null, null, false, 1.0F));
	public static final Emote GRIDDY = add(pose("griddy", "hits the Griddy", 3.2F, true, Emotes::griddy, music(5,
		Track.hits(SoundEvents.NOTE_BLOCK_BASEDRUM, 0.8F, 6, "x..x..x.x..x..x."),
		Track.hits(SoundEvents.NOTE_BLOCK_SNARE, 0.5F, 12, "....x.......x..."),
		Track.hits(SoundEvents.NOTE_BLOCK_HAT, 0.3F, 18, ".x.x.x.x.x.x.x.x"),
		Track.notes(SoundEvents.NOTE_BLOCK_BASS, 0.7F, "6 . . 6 . . 9 . 11 . . 11 . . 9 .")), null, true, 2.8F));
	public static final Emote FART = add(new Emote("fart", "farts", Kind.FART, 1.4F, false, (pose, t) -> {
	}, null, null, false, 0.45F));
	public static final Emote MIDDLE_FINGER = add(new Emote("middle_finger", "flips you off", Kind.MIDDLE_FINGER, 3.0F, false, (pose, t) -> {
	}, null, null, false, 1.0F));

	// ------------------------------------------------------------------ page 2

	public static final Emote CLAP = add(pose("clap", "claps", 3.0F, false, Emotes::clap, null, Emotes::clapSound, false, 0.36F));
	public static final Emote SALUTE = add(pose("salute", "salutes", 2.5F, false, Emotes::salute, null, null, false, 1.2F));
	public static final Emote FACEPALM = add(pose("facepalm", "facepalms", 2.6F, false, Emotes::facepalm, null, null, false, 1.3F));
	public static final Emote LAUGH = add(pose("laugh", "laughs", 3.0F, false, Emotes::laugh, null, null, false, 1.0F));
	public static final Emote CRY = add(pose("cry", "cries", 4.0F, false, Emotes::cry, null, Emotes::tears, false, 1.5F));
	public static final Emote BOW = add(pose("bow", "bows", 2.6F, false, Emotes::bow, null, null, false, 1.2F));
	public static final Emote SHRUG = add(pose("shrug", "shrugs", 2.2F, false, Emotes::shrug, null, null, false, 1.0F));
	public static final Emote THINK = add(pose("think", "thinks", 3.2F, false, Emotes::think, null, null, false, 1.5F));

	// ------------------------------------------------------------------ page 3

	public static final Emote JUMPING_JACKS = add(pose("jumping_jacks", "does jumping jacks", 1.0F, true, Emotes::jumpingJacks, music(5,
		Track.hits(SoundEvents.NOTE_BLOCK_BASEDRUM, 0.75F, 6, "x.x."),
		Track.hits(SoundEvents.NOTE_BLOCK_HAT, 0.35F, 18, ".x.x")), null, false, 0.5F));
	public static final Emote SPIN = add(pose("spin", "spins around", 2.0F, false, Emotes::spin, null, Emotes::sparkles, false, 0.75F));
	public static final Emote CHICKEN = add(pose("chicken", "does the Chicken Dance", 1.6F, true, Emotes::chicken, music(5,
		Track.hits(SoundEvents.NOTE_BLOCK_BASEDRUM, 0.75F, 6, "x...x...x...x..."),
		Track.hits(SoundEvents.NOTE_BLOCK_SNARE, 0.45F, 12, "..x...x...x...x."),
		Track.hits(SoundEvents.NOTE_BLOCK_COW_BELL, 0.45F, 12, "x.x.x.x.x.x.x.x."),
		Track.notes(SoundEvents.NOTE_BLOCK_XYLOPHONE, 0.5F, "18 18 . 18 18 . 16 . 18 18 . 18 18 . 20 .")), null, false, 0.3F));
	public static final Emote ROBOT = add(pose("robot", "does the Robot", 4.0F, true, Emotes::robot, music(5,
		Track.hits(SoundEvents.NOTE_BLOCK_BASEDRUM, 0.75F, 6, "x...x...x...x..."),
		Track.hits(SoundEvents.NOTE_BLOCK_HAT, 0.3F, 20, "..x...x...x...x."),
		Track.notes(SoundEvents.NOTE_BLOCK_BIT, 0.5F, "6 . 18 . 6 . 13 . 6 . 18 . 11 . 13 .")), null, false, 1.3F));
	public static final Emote DISCO = add(pose("disco", "does the Disco", 1.0F, true, Emotes::disco, music(5,
		Track.hits(SoundEvents.NOTE_BLOCK_BASEDRUM, 0.75F, 6, "x.x.x.x."),
		Track.hits(SoundEvents.NOTE_BLOCK_HAT, 0.35F, 20, ".x.x.x.x"),
		Track.hits(SoundEvents.NOTE_BLOCK_SNARE, 0.4F, 12, "..x...x."),
		Track.notes(SoundEvents.NOTE_BLOCK_BASS, 0.7F, "6 18 6 18 9 21 9 21")), null, false, 0.3F));
	public static final Emote AIR_GUITAR = add(pose("air_guitar", "plays air guitar", 1.0F, true, Emotes::airGuitar, music(4,
		Track.hits(SoundEvents.NOTE_BLOCK_BASEDRUM, 0.75F, 6, "x...x...x...x..."),
		Track.hits(SoundEvents.NOTE_BLOCK_SNARE, 0.5F, 12, "....x.......x..."),
		Track.notes(SoundEvents.NOTE_BLOCK_GUITAR, 0.6F, "6 . 6 9 . 6 11 9 6 . 6 9 . 13 11 9")), null, false, 0.3F));
	public static final Emote HEADBANG = add(pose("headbang", "headbangs", 0.84F, true, Emotes::headbang, music(4,
		Track.hits(SoundEvents.NOTE_BLOCK_BASEDRUM, 0.8F, 6, "x.x.x.x."),
		Track.hits(SoundEvents.NOTE_BLOCK_SNARE, 0.55F, 12, "..x...x."),
		Track.notes(SoundEvents.NOTE_BLOCK_GUITAR, 0.6F, "6 6 6 . 6 6 9 .")), null, false, 0.42F));
	public static final Emote HYPE = add(pose("hype", "gets hyped", 1.2F, true, Emotes::hype, music(5,
		Track.hits(SoundEvents.NOTE_BLOCK_BASEDRUM, 0.8F, 6, "x.x.x.x."),
		Track.hits(SoundEvents.NOTE_BLOCK_SNARE, 0.5F, 12, "..x...x."),
		Track.notes(SoundEvents.NOTE_BLOCK_PLING, 0.5F, "18 . 18 . 20 . 18 .")), null, false, 0.3F));

	// ------------------------------------------------------------------ page 4

	public static final Emote FLEX = add(pose("flex", "flexes", 3.2F, false, Emotes::flex, null, null, false, 1.2F));
	public static final Emote PUSH_UPS = add(pose("push_ups", "does push-ups", 1.2F, true, Emotes::pushUps, null, null, true, 1.2F));
	public static final Emote SIT = add(pose("sit", "sits down", 6.0F, true, Emotes::sit, null, null, true, 1.0F));
	public static final Emote ZOMBIE = add(pose("zombie", "acts like a zombie", 2.4F, true, Emotes::zombie, null, null, false, 0.6F));
	public static final Emote L_DANCE = add(pose("l_dance", "does the L Dance", 1.2F, true, Emotes::lDance, music(6,
		Track.hits(SoundEvents.NOTE_BLOCK_BASEDRUM, 0.75F, 6, "x.x.x.x."),
		Track.hits(SoundEvents.NOTE_BLOCK_SNARE, 0.45F, 12, "..x...x."),
		Track.notes(SoundEvents.NOTE_BLOCK_XYLOPHONE, 0.5F, "13 . 15 . 18 . 15 .")), null, false, 0.3F));
	public static final Emote HEART = add(pose("heart", "makes a heart", 3.0F, false, Emotes::heart, null, Emotes::hearts, false, 1.3F));
	public static final Emote KISS = add(pose("kiss", "blows a kiss", 2.2F, false, Emotes::kiss, null, Emotes::kissHeart, false, 1.15F));
	public static final Emote CHEER = add(pose("cheer", "cheers", 3.0F, false, Emotes::cheer, null, Emotes::confetti, false, 0.37F));

	private Emotes() {
	}

	private static Emote add(Emote emote) {
		ALL.add(emote);
		BY_ID.put(emote.id(), emote);
		BY_CHAT.put(emote.chat().toLowerCase(Locale.ROOT), emote);
		return emote;
	}

	private static Emote pose(String id, String chat, float seconds, boolean loops, Emote.Animation animation, @Nullable Music music,
		Emote.@Nullable Effects effects, boolean ownLegs, float iconTime) {
		return new Emote(id, chat, Kind.POSE, seconds, loops, animation, music, effects, ownLegs, iconTime);
	}

	private static Music music(int ticksPerStep, Track... tracks) {
		return new Music(ticksPerStep, List.of(tracks));
	}

	/** All emotes, in wheel order. */
	public static List<Emote> all() {
		return Collections.unmodifiableList(ALL);
	}

	public static @Nullable Emote byId(String id) {
		return BY_ID.get(id);
	}

	/** The emote a {@code /me} message stands for, like "waves", or null. */
	public static @Nullable Emote byChat(String text) {
		return BY_CHAT.get(text.strip().toLowerCase(Locale.ROOT));
	}

	// ------------------------------------------------------------------ helpers

	/** A wave between -1 and 1 that repeats every {@code period} seconds. */
	private static float wave(float t, float period) {
		return Mth.sin(2 * PI * t / period);
	}

	private static float wave(float t, float period, float phase) {
		return Mth.sin(2 * PI * (t / period + phase));
	}

	/** Bumps between 0 and 1, one every {@code period} seconds, like hops. */
	private static float hop(float t, float period) {
		return Math.abs(Mth.sin(PI * t / period));
	}

	private static float smooth(float x) {
		float t = Mth.clamp(x, 0.0F, 1.0F);
		return t * t * (3 - 2 * t);
	}

	/** Goes smoothly from 0 to 1 between {@code from} and {@code to} seconds. */
	private static float ramp(float t, float from, float to) {
		return smooth((t - from) / (to - from));
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	/** Half the gap between each pose of a sequence is spent moving to the next one. */
	private static EmotePose sequence(float t, float each, float move, EmotePose... poses) {
		int count = poses.length;
		float total = each * count;
		float local = ((t % total) + total) % total;
		int index = (int) (local / each);
		float into = ramp(local - index * each, 0.0F, move);
		EmotePose pose = new EmotePose();
		pose.copyFrom(poses[(index + count - 1) % count]);
		pose.mix(poses[index], into);
		return pose;
	}

	// ------------------------------------------------------------------ the dances

	/**
	 * Fortnite's Default Dance, which comes from the "Turk" dance: stepping in place with the knees coming up while the
	 * fists pump back and forth with bent elbows, then both arms thrown up and slammed down, a point forward, and arms
	 * crossed to finish.
	 */
	private static void defaultDance(EmotePose p, float t) {
		float c = t % 4.0F;
		float s = Mth.sin(2 * PI * t / 1.0F);
		EmotePose steps = new EmotePose();
		steps.rightLeg(-44 * Math.max(0, s), 0, 4).leftLeg(-44 * Math.max(0, -s), 0, -4);
		// The arm on the side of the foot that is down swings forward, like when running.
		steps.rightArm(-16 + 42 * s, 10, 8).leftArm(-16 - 42 * s, -10, -8).rightElbow(92 - 22 * s).leftElbow(92 + 22 * s).hands(HandShape.FIST);
		steps.body(7, 12 * s, 0).head(-4, -9 * s, 3 * s).move(0, 0.07F * hop(t, 0.5F), 0);
		EmotePose up = new EmotePose().rightArm(-168, 0, 18).leftArm(-168, 0, -18).elbows(14).hands(HandShape.OPEN).head(-18, 0, 0).body(-4, 0, 0)
			.move(0, 0.13F, 0);
		EmotePose slam = new EmotePose().rightArm(-14, 0, 28).leftArm(-14, 0, -28).elbows(24).hands(HandShape.FIST).body(20, 0, 0).head(14, 0, 0)
			.rightLeg(-28, 0, 7).leftLeg(-28, 0, -7);
		slam.hipDrop = 0.13F;
		EmotePose point = new EmotePose().rightArmTo(0.12F, 0.12F, 1.0F).leftArmTo(-0.12F, 0.12F, 1.0F).elbows(8).hands(HandShape.POINT).body(10, 0, 0)
			.head(-6, 0, 0).rightLeg(-10, 0, 3).leftLeg(4, 0, -3);
		// Arms folded in front of the chest, each hand at the other arm.
		EmotePose cross = new EmotePose().rightHandAt(0.8F, -0.02F, 0.3F, -0.3F, -1, 0.4F).leftHandAt(-0.8F, 0.06F, 0.36F, 0.3F, -1, 0.4F)
			.hands(HandShape.FIST).body(-7, -8, 0).head(-7, 6, 7).leftLeg(-6, 0, 10).rightLeg(2, 0, 2);
		cross.shoulderLift = 0.6F;
		p.copyFrom(keyframes(c, new float[] {0.0F, 1.95F, 2.25F, 2.45F, 2.75F, 2.95F, 3.3F, 3.5F, 3.85F, 4.0F},
			steps, steps, up, slam, slam, point, point, cross, cross, steps));
		p.jaw = 0.25F * ramp(c, 2.25F, 2.4F) * (1 - ramp(c, 2.6F, 2.8F));
	}

	/** Goes through poses at the given times, moving smoothly from each one to the next. */
	private static EmotePose keyframes(float c, float[] times, EmotePose... poses) {
		int i = times.length - 1;
		for (int k = 0; k < times.length - 1; k++) {
			if (c < times[k + 1]) {
				i = k;
				break;
			}
		}
		EmotePose out = new EmotePose();
		out.copyFrom(poses[i]);
		if (i < times.length - 1) {
			out.mix(poses[i + 1], smooth((c - times[i]) / (times[i + 1] - times[i])));
		}
		return out;
	}

	/**
	 * "Six, seven": the forearms held out in front, palms up, going up and down in turns like scales, while the head
	 * bobs.
	 */
	private static void sixSeven(EmotePose p, float t) {
		float s = wave(t, 0.8F);
		p.rightArm(-20 + 12 * s, 0, 12).leftArm(-20 - 12 * s, 0, -12);
		p.rightElbow(84 + 32 * s, 90).leftElbow(84 - 32 * s, 90).hands(HandShape.OPEN);
		p.head(4, 14 * wave(t, 1.6F), 10 * wave(t, 1.6F, 0.25F)).body(3, 6 * s, 0);
		p.move(0, 0.03F * hop(t, 0.4F), 0);
		p.jaw = Math.max(0, wave(t, 0.8F, 0.1F)) * 0.7F;
	}

	/**
	 * Straight arms swinging together from side to side around the hips, which go the other way: at each side one arm
	 * is in front of the body and the other behind it, and they swap on the way across.
	 */
	private static void floss(EmotePose p, float t) {
		float s = wave(t, 0.6F);
		p.rightArmTo(0.9F * s, -1.0F, 0.5F * s).leftArmTo(0.9F * s, -1.0F, -0.5F * s).elbows(10).hands(HandShape.FIST);
		p.body(0, 6 * s, -8 * s).head(0, -4 * s, 5 * s).rightLeg(0, 0, -4 * s).leftLeg(0, 0, -4 * s);
		p.move(-0.08F * s, 0.02F * hop(t, 0.3F), 0);
	}

	/**
	 * The Griddy: leaning forward, heels tapping out in front in turns with a little skip, arms swinging hard the other
	 * way, then both hands around the eyes like goggles. It travels: walk while doing it.
	 */
	private static void griddy(EmotePose p, float t) {
		float s = wave(t, 0.7F);
		EmotePose steps = new EmotePose();
		steps.body(18, 6 * s, 0).head(-14, -4 * s, 0);
		steps.rightLeg(10 - 48 * Math.max(0, s), 0, 3).leftLeg(10 - 48 * Math.max(0, -s), 0, -3);
		steps.rightArm(55 * s, 0, 14).leftArm(-55 * s, 0, -14).rightElbow(50 + 30 * Math.max(0, -s)).leftElbow(50 + 30 * Math.max(0, s));
		steps.hands(HandShape.FIST).move(0, 0.05F * hop(t, 0.35F), 0.035F * s);
		EmotePose goggles = new EmotePose();
		goggles.copyFrom(steps);
		goggles.rightHandAt(0.36F, 0.6F, 0.42F, -1, -0.35F, 0.1F).leftHandAt(-0.36F, 0.6F, 0.42F, 1, -0.35F, 0.1F).hands(HandShape.CURVED);
		goggles.rightTwist = -40;
		goggles.leftTwist = -40;
		goggles.head(-18, 0, 0).body(8, 0, 0);
		float cycle = t % 3.2F;
		p.copyFrom(steps);
		p.mix(goggles, ramp(cycle, 2.3F, 2.5F) * (1 - ramp(cycle, 3.0F, 3.2F)));
	}

	private static void jumpingJacks(EmotePose p, float t) {
		float u = (1 - Mth.cos(2 * PI * t / 1.0F)) / 2;
		p.rightArm(0, 0, 8 + 160 * u).leftArm(0, 0, -8 - 160 * u).elbows(12 + 14 * u).hands(HandShape.OPEN);
		p.rightLeg(0, 0, 4 + 15 * u).leftLeg(0, 0, -4 - 15 * u);
		p.head(-6 * u, 0, 0).move(0, 0.14F * hop(t, 0.5F), 0);
	}

	/** Fists tucked into the armpits, elbows flapping like wings, pecking with the head, knees bobbing. */
	private static void chicken(EmotePose p, float t) {
		float flap = hop(t, 0.2F);
		float peck = hop(t, 0.4F);
		p.rightHandAt(0.14F, -0.1F, 0.14F, -1, -0.7F + 1.1F * flap, -0.2F).leftHandAt(-0.14F, -0.1F, 0.14F, 1, -0.7F + 1.1F * flap, -0.2F);
		p.hands(HandShape.FIST);
		p.body(15, 0, 8 * wave(t, 1.6F)).head(10 + 15 * peck, 0, 0);
		p.headForward = 2.5F * peck;
		p.rightLeg(0, 0, 6).leftLeg(0, 0, -6);
		p.move(0, -0.06F * peck, 0);
		p.jaw = 0.5F * peck;
	}

	/** Stiff poses with the elbows at right angles, snapping from one to the next. */
	private static void robot(EmotePose p, float t) {
		p.copyFrom(sequence(t, 0.5F, 0.09F,
			new EmotePose().rightArm(-80, 0, 0).rightElbow(90).leftArm(0, 0, -4).leftElbow(8).hands(HandShape.OPEN),
			new EmotePose().rightArm(-80, 0, 0).rightElbow(90).leftArm(-80, 0, 0).leftElbow(90).hands(HandShape.OPEN).head(0, -45, 0),
			new EmotePose().rightArmTo(-1, 0, 0, 90).rightElbow(90).leftArmTo(1, 0, 0, -90).leftElbow(90).hands(HandShape.OPEN).head(0, 45, 0),
			new EmotePose().rightArm(0, 0, 6).rightElbow(90).leftArm(0, 0, -6).leftElbow(90).hands(HandShape.FIST),
			new EmotePose().rightArm(-172, 0, 0).rightElbow(4).leftArm(0, 0, -4).leftElbow(90).hands(HandShape.POINT).head(-20, 0, 0),
			new EmotePose().rightArm(-80, 0, 0).rightElbow(90).leftArm(-80, 0, 0).leftElbow(90).hands(HandShape.OPEN).body(0, 30, 0),
			new EmotePose().rightArm(-80, 0, 0).rightElbow(90).leftArm(-80, 0, 0).leftElbow(90).hands(HandShape.OPEN).body(0, -30, 0),
			new EmotePose().rightArm(0, 0, 4).rightElbow(6).leftArm(0, 0, -4).leftElbow(6).hands(HandShape.OPEN).head(15, 0, 0)));
		p.move(0, 0.015F * (1 - ramp(t % 0.5F, 0.0F, 0.12F)), 0);
	}

	/** Pointing up across the body, then down to the side, with the other hand on the hip. */
	private static void disco(EmotePose p, float t) {
		float local = t % 1.0F;
		float up = ramp(local, 0.0F, 0.18F) * (1 - ramp(local, 0.5F, 0.68F));
		p.rightArmTo(lerp(-0.6F, 0.5F, up), lerp(-0.6F, 1.0F, up), lerp(0.35F, 0.3F, up)).rightElbow(6).rightHand(HandShape.POINT);
		p.leftHandAt(0.1F, -0.62F, 0.06F, 1, 0.15F, -0.3F);
		p.head(-15 * up, -18 * up, 0).body(0, 0, 6 * wave(t, 1.0F)).rightLeg(-8 * up, 0, 0);
		p.move(-0.03F * wave(t, 1.0F), 0, 0);
	}

	/** The left hand on the guitar's neck out to the side, the right one strumming at the hip. */
	private static void airGuitar(EmotePose p, float t) {
		float bob = hop(t, 0.5F);
		p.leftHandAt(0.6F, 0.08F, 0.62F, 0.4F, -1, 0).leftHand(HandShape.GRIP);
		p.rightHandAt(0.4F, -0.42F + 0.12F * wave(t, 0.125F), 0.4F, -1, -0.3F, 0).rightHand(HandShape.CURVED);
		p.head(20 * bob - 5, 0, 0).body(10, -15, 0);
		p.rightLeg(-15 * bob, 0, 8).leftLeg(0, 0, -8);
		p.jaw = 0.3F * bob;
	}

	/** Nodding hard with both hands up making rock horns. */
	private static void headbang(EmotePose p, float t) {
		float nod = (1 - Mth.cos(2 * PI * t / 0.42F)) / 2;
		p.head(40 * nod - 10, 0, 0).body(12 * nod, 0, 0);
		float y = 0.82F - 0.22F * nod;
		p.rightHandAt(-0.12F, y, 0.45F, -1, -0.3F, 0).leftHandAt(0.12F, y, 0.45F, 1, -0.3F, 0).hands(HandShape.HORNS);
		p.rightTwist = -70;
		p.leftTwist = -70;
		p.move(0, -0.03F * nod, 0);
		p.jaw = 0.4F * nod;
	}

	/** Fortnite's Hype: bouncing from foot to foot with the fists pumping up in turns, shouting. */
	private static void hype(EmotePose p, float t) {
		float s = wave(t, 0.6F);
		float right = Math.max(0, s);
		float left = Math.max(0, -s);
		p.rightHandAt(-0.14F, lerp(-0.22F, 0.95F, right), lerp(0.45F, 0.22F, right), -1, -0.2F, 0.1F)
			.leftHandAt(0.14F, lerp(-0.22F, 0.95F, left), lerp(0.45F, 0.22F, left), 1, -0.2F, 0.1F).hands(HandShape.FIST);
		p.rightLeg(-30 * left, 0, 6).leftLeg(-30 * right, 0, -6);
		p.head(-12 * hop(t, 0.3F), 10 * s, 0).body(4, 10 * s, -5 * s).move(0.07F * s, 0.1F * hop(t, 0.3F), 0);
		p.jaw = 0.75F * hop(t, 0.3F);
	}

	/** A hand making an L on the forehead, kicking out to the sides in turns. */
	private static void lDance(EmotePose p, float t) {
		float k = wave(t, 0.6F);
		p.rightHandAt(0.4F, 0.78F, 0.32F, -1, 0.15F, 0.25F).rightHand(HandShape.L);
		p.rightTwist = -90;
		p.leftArm(-30 * k, 0, -10).leftElbow(35 + 25 * k).leftHand(HandShape.FIST);
		p.rightLeg(-10, 0, 35 * Math.max(0, k)).leftLeg(-10, 0, -35 * Math.max(0, -k));
		p.head(-8, 0, 10 * k).body(0, 10 * k, 0).move(0, 0.07F * hop(t, 0.3F), 0);
	}

	private static void zombie(EmotePose p, float t) {
		float s = wave(t, 1.2F);
		p.rightArm(-88 + 5 * s, 5, 0).leftArm(-88 - 5 * s, -5, 0).elbows(14 + 6 * s).hands(HandShape.CURVED);
		p.rightTwist = -60;
		p.leftTwist = -60;
		p.head(12, 0, 18 * wave(t, 2.4F, 0.1F)).body(8, 0, 4 * s);
		p.rightLeg(-10 * s, 0, 0).leftLeg(10 * s, 0, 0);
		p.jaw = 0.4F + 0.3F * wave(t, 0.9F);
	}

	// ------------------------------------------------------------------ gestures

	/** The forearm up and the open hand rocking from side to side at the elbow. */
	private static void wave(EmotePose p, float t) {
		float w = wave(t, 0.5F);
		p.rightHandAt(-0.5F + 0.24F * w, 0.92F, 0.22F, -1, -0.45F, 0).rightHand(HandShape.OPEN);
		p.rightTwist = 60;
		p.leftArmTo(0.08F, -1, 0).leftElbow(10);
		p.head(-4, -8, -6).body(0, -6, 0);
		p.jaw = 0.15F;
	}

	/** One arm stretched up to the side, the other bent in front of the face, the head down into that elbow. */
	private static void dab(EmotePose p, float t) {
		p.rightArmTo(-0.85F, 0.55F, 0.15F).rightElbow(4).rightHand(HandShape.OPEN);
		p.leftHandAt(-0.85F, 0.6F, 0.42F, 0.5F, 0.2F, 1).leftHand(HandShape.FIST);
		p.head(32, -25, -10).body(5, -10, -8);
	}

	private static void clap(EmotePose p, float t) {
		float open = hop(t, 0.36F);
		p.rightHandAt(0.5F - 0.24F * open, -0.05F, 0.55F, -1, -0.6F, 0).leftHandAt(-0.5F + 0.24F * open, -0.05F, 0.55F, 1, -0.6F, 0)
			.hands(HandShape.OPEN);
		p.body(-3, 0, 0).head(-5, 0, 0);
		p.jaw = 0.2F;
	}

	private static void clapSound(Player player, int age, float height) {
		// The hands meet every 0.36 seconds.
		float t = age / 20.0F;
		float before = (age - 1) / 20.0F;
		if (age > 4 && Math.floor(t / 0.36F) != Math.floor(before / 0.36F)) {
			player.level().playLocalSound(player.getX(), player.getY() + height * 0.7, player.getZ(), SoundEvents.NOTE_BLOCK_HAT.value(),
				player.getSoundSource(), 0.6F, 0.75F + RANDOM.nextFloat() * 0.15F, false);
		}
	}

	private static void salute(EmotePose p, float t) {
		p.rightHandAt(0.28F, 0.7F, 0.3F, -1, 0.1F, 0.2F).rightHand(HandShape.OPEN);
		p.rightTwist = -35;
		p.leftArm(0, 0, -2).leftElbow(5);
		p.head(-6, 0, 0).body(-3, 0, 0).rightLeg(0, 0, -2).leftLeg(0, 0, 2);
	}

	private static void facepalm(EmotePose p, float t) {
		p.rightHandAt(0.48F, 0.5F, 0.46F, -0.4F, -1, 0.3F).rightHand(HandShape.OPEN);
		p.rightTwist = -30;
		p.leftArmTo(-0.1F, -1, 0.1F).leftElbow(10);
		p.head(28, 8 * wave(t, 0.5F), 0).body(10, 0, 0);
	}

	/** Holding the belly, shaking with laughter. */
	private static void laugh(EmotePose p, float t) {
		float shake = wave(t, 0.22F);
		p.body(-14 + 4 * shake, 0, 0).head(-22, 0, 0);
		p.rightHandAt(0.38F, -0.5F, 0.28F, -1, -0.2F, -0.2F).leftHandAt(-0.38F, -0.5F, 0.28F, 1, -0.2F, -0.2F).hands(HandShape.CURVED);
		p.shoulderLift = 0.6F * shake;
		p.jaw = 0.55F + 0.45F * shake;
	}

	/** Rubbing the eyes with both fists, sobbing. */
	private static void cry(EmotePose p, float t) {
		float rub = wave(t, 0.3F);
		p.rightHandAt(0.4F + 0.03F * rub, 0.6F, 0.42F, -0.6F, -1, 0.2F).leftHandAt(-0.4F + 0.03F * rub, 0.6F, 0.42F, 0.6F, -1, 0.2F).hands(HandShape.FIST);
		p.head(28, 4 * wave(t, 0.6F), 0).body(14, 0, 0);
		p.shoulderLift = 0.7F * Math.max(0, wave(t, 0.3F));
	}

	private static void tears(Player player, int age, float height) {
		if (age > 8 && age % 3 == 0) {
			particle(player, ParticleTypes.FALLING_WATER, height * 0.86F, 0.12F, (RANDOM.nextFloat() - 0.5F) * 0.25F, Vec3.ZERO);
		}
	}

	/** A deep bow, one hand on the chest and the other behind the back. */
	private static void bow(EmotePose p, float t) {
		float b = ramp(t, 0.2F, 0.7F) * (1 - ramp(t, 1.9F, 2.4F));
		EmotePose bowed = new EmotePose().body(55, 0, 0).head(10, 0, 0);
		bowed.rightHandAt(0.5F, -0.12F, 0.26F, -1, -0.6F, 0.2F).rightHand(HandShape.OPEN);
		bowed.leftHandAt(-0.42F, -0.78F, -0.28F, 1, -0.4F, -0.4F).leftHand(HandShape.RELAXED);
		p.mix(bowed, b);
	}

	/** The shoulders up, forearms out to the sides and palms up. */
	private static void shrug(EmotePose p, float t) {
		p.shoulderLift = 2.0F;
		p.rightHandAt(-0.55F, -0.25F, 0.45F, -0.3F, -1, -0.3F).leftHandAt(0.55F, -0.25F, 0.45F, 0.3F, -1, -0.3F).hands(HandShape.OPEN);
		p.rightTwist = 90;
		p.leftTwist = 90;
		p.head(0, 0, 14);
		p.jaw = 0.15F;
	}

	/** A hand on the chin, the other arm across the belly holding up its elbow, looking up as if wondering. */
	private static void think(EmotePose p, float t) {
		p.rightHandAt(0.46F, 0.3F, 0.42F, -0.2F, -1, 0.2F).rightHand(HandShape.CURVED);
		p.leftHandAt(-0.52F, -0.32F, 0.3F, 0.6F, -0.8F, 0.2F).leftHand(HandShape.RELAXED);
		p.head(-12, 14, -10).body(0, 8, 0);
		p.shoulderLift = 0.4F * Math.max(0, wave(t, 0.8F));
	}

	/** Two whole turns on one foot with the arms out. */
	private static void spin(EmotePose p, float t) {
		float s = ramp(t, 0.1F, 1.6F);
		float up = (float) Math.sin(PI * Mth.clamp((t - 0.1F) / 1.5F, 0.0F, 1.0F));
		p.turn(720 * s, 0, 0, 0);
		p.rightArm(0, 0, 80).leftArm(0, 0, -80).elbows(12).hands(HandShape.OPEN).rightLeg(-25 * up, 0, 0);
		p.head(-10, 0, 0).move(0, 0.05F * up, 0);
	}

	private static void sparkles(Player player, int age, float height) {
		if (age > 2 && age < 34 && age % 2 == 0) {
			particle(player, ParticleTypes.CRIT, height * 0.5F, (RANDOM.nextFloat() - 0.5F) * 0.8F, (RANDOM.nextFloat() - 0.5F) * 0.8F, Vec3.ZERO);
		}
	}

	/** Both biceps: the upper arms out to the sides, the forearms up and the fists by the head. */
	private static void flex(EmotePose p, float t) {
		float squeeze = hop(t, 0.8F);
		p.rightHandAt(-0.42F, 0.5F - 0.06F * squeeze, 0.08F, -1, -0.2F, 0).leftHandAt(0.42F, 0.5F - 0.06F * squeeze, 0.08F, 1, -0.2F, 0)
			.hands(HandShape.FIST);
		p.shoulderLift = squeeze;
		p.head(-5, 25 * wave(t, 1.6F), 0).body(-6, 0, 0).rightLeg(0, 0, 8).leftLeg(0, 0, -8);
	}

	/**
	 * Lying face down and pushing up and down. The whole body turns around the feet; the hands stay on the ground under
	 * the shoulders, so the elbows bend when the body goes down.
	 */
	private static void pushUps(EmotePose p, float t) {
		float lie = ramp(t, 0.0F, 0.6F);
		float up = (1 - Mth.cos(2 * PI * t / 1.2F)) / 2;
		float pitch = lerp(66, 52, up) * lie;
		p.turn(0, pitch, 0, 0);
		double theta = Math.toRadians(pitch);
		// How high the shoulders are above the ground, in arm lengths, and which way the ground is from them.
		float reach = (float) (1.3 * Math.cos(theta) / 0.7);
		float down = (float) -Math.cos(theta);
		float ahead = (float) Math.sin(theta);
		p.rightHandAt(-0.14F, down * reach, ahead * reach, -0.6F, -1, 0).leftHandAt(0.14F, down * reach, ahead * reach, 0.6F, -1, 0).hands(HandShape.OPEN);
		p.head(-35 * lie, 0, 0);
	}

	/** Sits down on the ground, legs out in front, leaning back on the hands and looking around. */
	private static void sit(EmotePose p, float t) {
		float sit = ramp(t, 0.0F, 0.5F);
		p.hipDrop = sit;
		p.rightLeg(-88 * sit, 6 * sit, 0).leftLeg(-88 * sit, -6 * sit, 0);
		p.rightArmTo(-0.2F * sit, -1, -0.75F * sit).leftArmTo(0.2F * sit, -1, -0.75F * sit).elbows(8 * sit).hands(HandShape.OPEN);
		p.body(-12 * sit, 0, 0).head(-5, 25 * wave(t, 6.0F) * sit, 0);
	}

	/** Both hands in front of the chest, fingers curved into the shape of a heart. */
	private static void heart(EmotePose p, float t) {
		p.rightHandAt(0.46F, 0.02F, 0.55F, -1, -0.6F, 0).leftHandAt(-0.46F, 0.02F, 0.55F, 1, -0.6F, 0).hands(HandShape.CURVED);
		p.rightTwist = -60;
		p.leftTwist = -60;
		p.head(-12, 0, 8 * wave(t, 1.5F)).body(-4, 0, 4 * wave(t, 1.5F));
	}

	private static void hearts(Player player, int age, float height) {
		if (age % 9 == 4 && age < 54) {
			particle(player, ParticleTypes.HEART, height + 0.35F, 0.0F, (RANDOM.nextFloat() - 0.5F) * 0.3F, Vec3.ZERO);
		}
	}

	/** Fingertips to the mouth, then the hand opens out toward whoever is in front. */
	private static void kiss(EmotePose p, float t) {
		float k = ramp(t, 0.15F, 0.5F);
		float out = ramp(t, 0.8F, 1.1F);
		EmotePose mouth = new EmotePose().rightHandAt(0.48F, 0.4F, 0.42F, -0.5F, -1, 0.3F).rightHand(HandShape.CURVED);
		mouth.rightTwist = -40;
		mouth.leftArm(0, 0, -3).head(2, 0, 12);
		mouth.jaw = 0.2F;
		EmotePose blow = new EmotePose().rightArmTo(-0.1F, 0.35F, 1.0F).rightElbow(14, 90).rightHand(HandShape.OPEN).leftArm(0, 0, -3).head(-4, 0, 0);
		p.mix(mouth, k);
		p.mix(blow, out);
	}

	private static void kissHeart(Player player, int age, float height) {
		if (age == 19) {
			particle(player, ParticleTypes.HEART, height * 0.8F, 0.45F, -0.15F, new Vec3(0, 0.02, 0.12));
		}
	}

	private static void cheer(EmotePose p, float t) {
		float v = hop(t, 0.75F);
		p.rightArmTo(-0.45F, 1, 0.12F + 0.2F * v).leftArmTo(0.45F, 1, 0.12F + 0.2F * v).elbows(38 - 24 * v).hands(HandShape.FIST);
		p.head(-15, 0, 0).move(0, 0.2F * v, 0);
		p.jaw = 0.7F;
	}

	private static void confetti(Player player, int age, float height) {
		if (age % 5 == 0 && age < 56) {
			particle(player, ParticleTypes.HAPPY_VILLAGER, height + 0.2F, (RANDOM.nextFloat() - 0.5F) * 0.6F, (RANDOM.nextFloat() - 0.5F) * 0.6F,
				Vec3.ZERO);
		}
	}

	/**
	 * A particle near the player: {@code up} blocks above the feet, {@code forward} blocks in front of them and
	 * {@code side} blocks to their left, moving with {@code velocity} (x left, y up, z forward).
	 */
	private static void particle(Player player, ParticleOptions type, float up, float forward, float side, Vec3 velocity) {
		double yaw = Math.toRadians(player.yBodyRot);
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		double scale = player.getScale();
		double x = player.getX() + (side * cos - forward * sin) * scale;
		double z = player.getZ() + (side * sin + forward * cos) * scale;
		double vx = velocity.x * cos - velocity.z * sin;
		double vz = velocity.x * sin + velocity.z * cos;
		player.level().addParticle(type, x, player.getY() + up * scale, z, vx, velocity.y, vz);
	}
}
