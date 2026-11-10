package com.playercharacters.character;

import java.util.EnumMap;
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
	 * Extras for the middle finger emote and other options, all optional.
	 *
	 * @param madParts       drawn only while the character is mad (an angry face), each following its bone
	 * @param calmParts      drawn only while it is not mad (things the angry face replaces)
	 * @param middleFinger   a right arm making the gesture, drawn instead of the right arm during the emote
	 * @param waddleRadians  how far the whole character rocks from side to side while walking
	 * @param headTurn       how much of the player's head turning the head copies, from 0 to 1
	 * @param smoke          model space: where smoke rises from (like the tip of a cigarette), moving with the head
	 */
	public record Extras(Map<Bone, Part> madParts, Map<Bone, Part> calmParts, @Nullable Part middleFinger, float waddleRadians, float headTurn,
		@Nullable Vector3f smoke) {
		public static final Extras NONE = new Extras(Map.of(), Map.of(), null, 0.0F, 1.0F, null);

		public Extras {
			madParts = madParts.isEmpty() ? Map.of() : new EnumMap<>(madParts);
			calmParts = calmParts.isEmpty() ? Map.of() : new EnumMap<>(calmParts);
		}
	}

	private final Map<Bone, Part> parts;
	private final Identifier texture;
	private final float jawOpenRadians;
	private final Vector3f hip;
	private final Vector3f butt;
	private final float height;
	private final Extras extras;

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

	public int quadCount() {
		int quads = 0;
		for (Part part : this.parts.values()) {
			quads += part.vertexCount() / 4;
		}
		for (Part part : this.extras.madParts().values()) {
			quads += part.vertexCount() / 4;
		}
		for (Part part : this.extras.calmParts().values()) {
			quads += part.vertexCount() / 4;
		}
		if (this.extras.middleFinger() != null) {
			quads += this.extras.middleFinger().vertexCount() / 4;
		}
		return quads;
	}
}
