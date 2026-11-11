package com.playercharacters.character;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * A 3D character, already converted to the coordinate space of Minecraft's player model:
 * 1 unit = 1 block, Y pointing down, origin at the neck (feet at y = 1.501), facing -Z,
 * and pre-scaled so it appears at its real size after the player renderer's 15/16 scale.
 */
public final class CharacterModel {
	/** The bones of Minecraft's player model that a model part can follow. */
	public enum Bone {
		HEAD,
		JAW,
		BODY,
		RIGHT_ARM,
		LEFT_ARM,
		RIGHT_LEG,
		LEFT_LEG
	}

	/** How the character stands and walks. */
	public enum Stance {
		/** Like a person: arms swing as it walks. */
		TWO_LEGS,
		/** Like a dog: the arms are front legs and trot with the back legs. For emotes it stands up on its back legs. */
		FOUR_LEGS
	}

	/** What the arms do when the character isn't doing anything else with them. */
	public enum Arms {
		/** Minecraft's own arm poses: swinging while walking, holding items, aiming bows. */
		NORMAL,
		/** Both arms up in front, holding a rifle that is part of the right arm, aimed wherever the player looks. */
		RIFLE
	}

	/**
	 * One rigid piece of the model. A part can have no vertices at all: then it only provides a joint
	 * (for example a head joint that the jaw turns around).
	 *
	 * @param vertices 8 floats per vertex (x, y, z, normal x, y, z, u, v), 4 vertices per quad
	 * @param colors   one ARGB color per vertex
	 * @param pivot    the joint it rotates around
	 * @param hand     arms only: where held items go
	 */
	public record Part(float[] vertices, int[] colors, Vector3f pivot, @Nullable Vector3f hand) {
		public int vertexCount() {
			return this.colors.length;
		}

		/** The same polygons around another joint. */
		public Part withPivot(Vector3f newPivot) {
			return new Part(this.vertices, this.colors, newPivot, this.hand);
		}
	}

	/**
	 * A tail that sways on its own. It moves with the body, and bends more the further a point is from its base.
	 *
	 * @param part   the tail
	 * @param glow   glowing bits of it (like a flame at the tip), bending the same way; or null
	 * @param base   model space: where it grows out of the body
	 * @param length how far its furthest point is from the base
	 * @param tip    model space: its furthest point (where particles can come out)
	 * @param index  0 for the first tail, 1 for the next...; each sways a little differently
	 */
	public record Tail(Part part, @Nullable Part glow, Vector3f base, float length, Vector3f tip, int index) {
	}

	/**
	 * Particles that keep coming out of a character, like flames on a tail or mist at the feet.
	 *
	 * @param particle  a particle id such as "minecraft:soul_fire_flame", or "minecraft:dust" with {@code color}
	 * @param color     RGB for dust particles
	 * @param size      scale for dust particles
	 * @param attachedTo what it moves with: a bone, or a tail ({@code tail} set)
	 * @param tail      the tail it comes out of (its tip), or -1
	 * @param at        model space: where it comes out
	 * @param every     ticks between particles
	 * @param when      when it shows
	 * @param spread    blocks of random offset
	 * @param velocity  blocks per tick: x toward the character's left, y up, z forward
	 */
	public record Emitter(String particle, int color, float size, Bone attachedTo, int tail, Vector3f at, int every, When when, float spread,
		Vector3f velocity) {
		public enum When {
			ALWAYS,
			MOVING,
			SPRINTING,
			STANDING_STILL
		}
	}

	/** Detailed arms for first person, where they are seen up close. Any of them can be null. */
	public record FirstPerson(@Nullable Part rightArm, @Nullable Part leftArm, @Nullable Part middleFinger) {
		public static final FirstPerson NONE = new FirstPerson(null, null, null);
	}

	/** Which finger a finger is. Hands with fewer fingers leave some out (a cartoon hand has no ring finger). */
	public enum FingerRole {
		THUMB,
		INDEX,
		MIDDLE,
		RING,
		LITTLE
	}

	/**
	 * A finger: its own polygons, which curl toward the palm at three joints (two for a thumb). Everything in model space.
	 *
	 * @param role        which finger it is
	 * @param part        its polygons in third person
	 * @param firstPerson its more detailed polygons in first person, or null
	 * @param base        the knuckle it grows from
	 * @param axis        unit direction from the knuckle to the tip
	 * @param length      from the knuckle to the tip
	 * @param bend        unit axis it curls around: turning it by a positive angle moves the tip toward the palm
	 * @param joints      where it bends, as fractions of its length from the knuckle (the first is 0, the knuckle)
	 * @param angles      how far each joint bends when the finger is fully curled, in radians
	 * @param rigid       true for mechanical fingers, which bend sharply at their joints instead of smoothly
	 */
	public record Finger(FingerRole role, Part part, @Nullable Part firstPerson, Vector3f base, Vector3f axis, float length, Vector3f bend, float[] joints,
		float[] angles, boolean rigid) {
		public Finger withParts(Part newPart, @Nullable Part newFirstPerson) {
			return new Finger(this.role, newPart, newFirstPerson, this.base, this.axis, this.length, this.bend, this.joints, this.angles, this.rigid);
		}
	}

	/**
	 * How an arm bends: at its elbow, and with fingers that curl. Every 3D character has one for each arm; without
	 * settings in character.json the elbow is guessed halfway down the arm. Everything in model space.
	 *
	 * @param shoulder the arm's joint (its part's pivot)
	 * @param elbow    the elbow joint
	 * @param wrist    where the forearm meets the hand (the forearm twists between the elbow and here)
	 * @param hinge    unit axis of the elbow: turning the forearm around it by a positive angle bends the arm forward
	 * @param blend    blocks on each side of the elbow over which the arm's surface bends smoothly
	 * @param fingers  fingers that curl, maybe none
	 * @param held     something held in the hand that moves with it as a whole (the queen's rifle), or null
	 * @param heldGlow glowing bits of it (the rifle scope's lens), or null
	 * @param heldFirstPerson its more detailed version for first person, or null
	 */
	public record ArmRig(Vector3f shoulder, Vector3f elbow, Vector3f wrist, Vector3f hinge, float blend, List<Finger> fingers, @Nullable Part held,
		@Nullable Part heldGlow, @Nullable Part heldFirstPerson) {
		public ArmRig {
			fingers = List.copyOf(fingers);
		}

		public @Nullable Finger finger(FingerRole role) {
			for (Finger finger : this.fingers) {
				if (finger.role() == role) {
					return finger;
				}
			}
			return null;
		}

		/** The same joints with these fingers and held thing (for the simpler copies drawn far away). */
		public ArmRig withParts(List<Finger> newFingers, @Nullable Part newHeld, @Nullable Part newHeldGlow) {
			return new ArmRig(this.shoulder, this.elbow, this.wrist, this.hinge, this.blend, newFingers, newHeld, newHeldGlow, null);
		}
	}

	/**
	 * Extras for the middle finger emote and other options, all optional.
	 *
	 * @param madParts       drawn only while the character is mad (an angry face), each following its bone
	 * @param calmParts      drawn only while it is not mad (things the angry face replaces)
	 * @param middleFinger   a right arm making the gesture, drawn instead of the right arm during the emote
	 * @param waddleRadians  how far the whole character rocks from side to side while walking
	 * @param headTurn       how much of the player's head turning the head copies, from 0 to 1
	 * @param smoke          model space: where smoke rises from (like the tip of a cigarette), moving with the head
	 * @param blinkParts     closed eyelids, drawn for a moment every few seconds
	 * @param bounce         how far (blocks) the character bobs up and down with each step
	 * @param glowParts      parts that glow in the dark (eyes, markings), each following its bone
	 * @param tails          tails that sway by themselves
	 * @param stance         two legs or four
	 * @param arms           what the arms do by default
	 * @param hideHeldItems  whether items in the hands are left out (the hands are already holding something)
	 * @param firstPerson    detailed arms for first person
	 * @param particles      particles that keep coming out of it
	 * @param rightRig       how the right arm bends (elbow and fingers), or null for a stiff arm
	 * @param leftRig        how the left arm bends, or null
	 */
	public record Extras(Map<Bone, Part> madParts, Map<Bone, Part> calmParts, @Nullable Part middleFinger, float waddleRadians, float headTurn,
		@Nullable Vector3f smoke, Map<Bone, Part> blinkParts, float bounce, Map<Bone, Part> glowParts, List<Tail> tails, Stance stance, Arms arms,
		boolean hideHeldItems, FirstPerson firstPerson, List<Emitter> particles, @Nullable ArmRig rightRig, @Nullable ArmRig leftRig) {
		public static final Extras NONE = new Extras(Map.of(), Map.of(), null, 0.0F, 1.0F, null, Map.of(), 0.0F, Map.of(), List.of(), Stance.TWO_LEGS,
			Arms.NORMAL, false, FirstPerson.NONE, List.of(), null, null);

		public Extras {
			madParts = madParts.isEmpty() ? Map.of() : new EnumMap<>(madParts);
			calmParts = calmParts.isEmpty() ? Map.of() : new EnumMap<>(calmParts);
			blinkParts = blinkParts.isEmpty() ? Map.of() : new EnumMap<>(blinkParts);
			glowParts = glowParts.isEmpty() ? Map.of() : new EnumMap<>(glowParts);
			tails = List.copyOf(tails);
			particles = List.copyOf(particles);
		}

		public boolean fourLegs() {
			return this.stance == Stance.FOUR_LEGS;
		}

		public @Nullable ArmRig rig(Bone arm) {
			return arm == Bone.RIGHT_ARM ? this.rightRig : arm == Bone.LEFT_ARM ? this.leftRig : null;
		}

		/** Whether a hand has fingers that curl. */
		public boolean hasFingers(Bone arm) {
			ArmRig rig = this.rig(arm);
			return rig != null && !rig.fingers().isEmpty();
		}

		/**
		 * Whether the middle finger is made with the right hand's own fingers. A model that has an arm made for the
		 * gesture ("middle_finger") uses that instead.
		 */
		public boolean fingerGesture() {
			return this.middleFinger == null && this.hasFingers(Bone.RIGHT_ARM);
		}
	}

	private final Map<Bone, Part> parts;
	private final Identifier texture;
	private final float jawOpenRadians;
	private final Vector3f hip;
	private final Vector3f butt;
	private final float height;
	private final Extras extras;
	/** Simpler copies for drawing far away (see {@link #forDistance}). */
	private CharacterModel[] simpler = new CharacterModel[0];
	/** Beyond these distances (squared, in blocks) the simpler copies are drawn. */
	private static final double[] SIMPLER_FROM = {12.0 * 12.0, 28.0 * 28.0};

	/**
	 * @param jawOpenRadians how far the jaw turns when fully open; negative opens it upward (like a lid)
	 * @param hip            model space: the point the upper body bends around
	 * @param butt           where fart clouds come out, in blocks relative to the feet: x toward the character's
	 *                       left, y up, z forward. Already at the character's real size.
	 * @param height         how tall it is, in blocks
	 */
	public CharacterModel(Map<Bone, Part> parts, Identifier texture, float jawOpenRadians, Vector3f hip, Vector3f butt, float height, Extras extras) {
		this.parts = new EnumMap<>(parts);
		this.texture = texture;
		this.jawOpenRadians = jawOpenRadians;
		this.hip = hip;
		this.butt = butt;
		this.height = height;
		this.extras = extras;
	}

	public @Nullable Part part(Bone bone) {
		return this.parts.get(bone);
	}

	/** The copy of this model to draw at this (squared) distance: simpler further away, where details are too small to see. */
	public CharacterModel forDistance(double distanceSquared) {
		for (int i = this.simpler.length - 1; i >= 0; i--) {
			if (distanceSquared > SIMPLER_FROM[i]) {
				return this.simpler[i];
			}
		}
		return this;
	}

	/** The simplest copy, for small pictures like the emote wheel's. */
	public CharacterModel simplest() {
		return this.simpler.length == 0 ? this : this.simpler[this.simpler.length - 1];
	}

	/** Makes the simpler copies used far away. Called once, when the character is loaded. */
	void makeSimplerCopies() {
		CharacterModel[] copies = new CharacterModel[ModelDetail.CELLS.length];
		for (int i = 0; i < copies.length; i++) {
			copies[i] = this.simplified(ModelDetail.CELLS[i]);
		}
		this.simpler = copies;
	}

	private CharacterModel simplified(float cell) {
		Map<Bone, Part> parts = new EnumMap<>(Bone.class);
		this.parts.forEach((bone, part) -> parts.put(bone, ModelDetail.simplify(part, cell)));
		Extras e = this.extras;
		List<Tail> tails = new ArrayList<>();
		for (Tail tail : e.tails()) {
			tails.add(new Tail(ModelDetail.simplify(tail.part(), cell), tail.glow() == null ? null : ModelDetail.simplify(tail.glow(), cell), tail.base(),
				tail.length(), tail.tip(), tail.index()));
		}
		// Far away nobody sees first-person arms, so the simpler copies don't keep them.
		Extras extras = new Extras(simplify(e.madParts(), cell), simplify(e.calmParts(), cell),
			e.middleFinger() == null ? null : ModelDetail.simplify(e.middleFinger(), cell), e.waddleRadians(), e.headTurn(), e.smoke(),
			simplify(e.blinkParts(), cell), e.bounce(), simplify(e.glowParts(), cell), tails, e.stance(), e.arms(), e.hideHeldItems(), FirstPerson.NONE,
			e.particles(), simplify(e.rightRig(), cell), simplify(e.leftRig(), cell));
		return new CharacterModel(parts, this.texture, this.jawOpenRadians, this.hip, this.butt, this.height, extras);
	}

	private static @Nullable ArmRig simplify(@Nullable ArmRig rig, float cell) {
		if (rig == null) {
			return null;
		}
		List<Finger> fingers = new ArrayList<>();
		for (Finger finger : rig.fingers()) {
			fingers.add(finger.withParts(ModelDetail.simplify(finger.part(), cell), null));
		}
		return rig.withParts(fingers, rig.held() == null ? null : ModelDetail.simplify(rig.held(), cell),
			rig.heldGlow() == null ? null : ModelDetail.simplify(rig.heldGlow(), cell));
	}

	private static Map<Bone, Part> simplify(Map<Bone, Part> parts, float cell) {
		Map<Bone, Part> simpler = new EnumMap<>(Bone.class);
		parts.forEach((bone, part) -> simpler.put(bone, ModelDetail.simplify(part, cell)));
		return simpler;
	}

	public Identifier texture() {
		return this.texture;
	}

	public float jawOpenRadians() {
		return this.jawOpenRadians;
	}

	public Vector3fc hip() {
		return this.hip;
	}

	public Vector3fc butt() {
		return this.butt;
	}

	public float height() {
		return this.height;
	}

	public Extras extras() {
		return this.extras;
	}

	/** Every polygon drawn in third person (first-person arms not included). */
	public int quadCount() {
		int quads = 0;
		for (Part part : this.parts.values()) {
			quads += part.vertexCount() / 4;
		}
		for (Map<Bone, Part> parts : List.of(this.extras.madParts(), this.extras.calmParts(), this.extras.blinkParts(), this.extras.glowParts())) {
			for (Part part : parts.values()) {
				quads += part.vertexCount() / 4;
			}
		}
		for (Tail tail : this.extras.tails()) {
			quads += tail.part().vertexCount() / 4 + (tail.glow() == null ? 0 : tail.glow().vertexCount() / 4);
		}
		if (this.extras.middleFinger() != null) {
			quads += this.extras.middleFinger().vertexCount() / 4;
		}
		for (ArmRig rig : new ArmRig[] {this.extras.rightRig(), this.extras.leftRig()}) {
			if (rig != null) {
				for (Finger finger : rig.fingers()) {
					quads += finger.part().vertexCount() / 4;
				}
				if (rig.held() != null) {
					quads += rig.held().vertexCount() / 4;
				}
				if (rig.heldGlow() != null) {
					quads += rig.heldGlow().vertexCount() / 4;
				}
			}
		}
		return quads;
	}
}
