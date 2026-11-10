package com.playercharacters.character;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
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
	 * One rigid piece of the model.
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
	}

	private final Map<Bone, Part> parts;
	private final Identifier texture;
	private final float jawOpenRadians;

	public CharacterModel(Map<Bone, Part> parts, Identifier texture, float jawOpenRadians) {
		this.parts = new EnumMap<>(parts);
		this.texture = texture;
		this.jawOpenRadians = jawOpenRadians;
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

	public int quadCount() {
		int quads = 0;
		for (Part part : this.parts.values()) {
			quads += part.vertexCount() / 4;
		}
		return quads;
	}
}
