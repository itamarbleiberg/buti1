package com.playercharacters.character;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.mojang.blaze3d.platform.NativeImage;
import com.playercharacters.PlayerCharactersClient;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Loads a 3D character from a folder:
 * <ul>
 *   <li>{@code model.obj} - the model (required). Objects/groups are matched to body parts by name:
 *       head, jaw, body, right_arm, left_arm, right_leg, left_leg. Anything else moves with the body.
 *       For the middle finger emote: objects named "mad_&lt;part&gt;" are only drawn while the character is mad,
 *       "calm_&lt;part&gt;" only while it isn't, and "middle_finger" replaces the right arm during the gesture.
 *       "blink_&lt;part&gt;" (closed eyelids) is shown for a moment every few seconds. "glow_&lt;part&gt;" glows in
 *       the dark. Objects whose name starts with "tail" sway by themselves ("glow_tail..." glows and sways with them).
 *       "fp_right_arm", "fp_left_arm" and "fp_middle_finger" are more detailed arms, only drawn in first person.
 *       Fingers that curl are objects of their own: "right_thumb", "right_index", "right_middle", "right_ring" and
 *       "right_little" (and "left_..."), with "fp_right_index" and so on for first person. Arms bend at their elbow.</li>
 *   <li>{@code texture.png} - optional texture. Without it, the model uses its vertex colors.</li>
 *   <li>{@code character.json} - optional settings: name, size, height, turn, jaw_angle, butt, waddle, head_turn,
 *       smoke, bounce, stance, arms, hide_held_items, particles and pivots.</li>
 *   <li>{@code icon.png} - optional picture for the menus.</li>
 * </ul>
 * Models are expected in Blender's usual export orientation: Y up, facing +Z, 1 unit = 1 block.
 */
final class ModelCharacterLoader {
	static final String MODEL_FILE = "model.obj";
	private static final Gson GSON = new Gson();
	private static final Identifier WHITE = Identifier.fromNamespaceAndPath(PlayerCharactersClient.MOD_ID, "textures/white.png");
	/** The player renderer draws its model at 15/16 size; models are scaled up to make up for it. */
	private static final float PLAYER_SCALE = 0.9375F;
	/** Model space y of the feet. */
	private static final float FEET_Y = 1.501F;
	private static final int MAX_QUADS = 100_000;

	private ModelCharacterLoader() {
	}

	/** Settings from character.json. Field names match the JSON keys. */
	@SuppressWarnings("unused")
	private static final class Options {
		@Nullable String name;
		@Nullable Float size;
		@Nullable Float height;
		@Nullable Float turn;
		@Nullable Float jaw_angle;
		float @Nullable [] butt;
		@Nullable Float waddle;
		@Nullable Float head_turn;
		float @Nullable [] smoke;
		@Nullable Float bounce;
		/** "two_legs" (the default) or "four_legs". */
		@Nullable String stance;
		/** "normal" (the default) or "rifle". */
		@Nullable String arms;
		@Nullable Boolean hide_held_items;
		@Nullable List<ParticleOptions> particles;
		@Nullable Map<String, PartOptions> parts;
	}

	@SuppressWarnings("unused")
	private static final class ParticleOptions {
		@Nullable String particle;
		@Nullable String color;
		@Nullable Float size;
		@Nullable String part;
		float @Nullable [] at;
		@Nullable Integer every;
		@Nullable String when;
		@Nullable Float spread;
		float @Nullable [] velocity;
	}

	/** What an object in model.obj is used for. */
	private enum Kind {
		NORMAL,
		MAD,
		CALM,
		BLINK,
		GLOW,
		MIDDLE_FINGER,
		TAIL,
		TAIL_GLOW,
		FIRST_PERSON_RIGHT,
		FIRST_PERSON_LEFT,
		FIRST_PERSON_FINGER,
		/** A finger that can curl ("right_index"), and its first-person version ("fp_right_index"). */
		HAND_FINGER,
		FIRST_PERSON_HAND_FINGER,
		/** Something held in a hand ("right_hand", like the queen's rifle): it moves with the hand, past the elbow. */
		HAND,
		FIRST_PERSON_HAND
	}

	/** @param tail for tails: the tail's name (without "glow_"); for fingers: the finger's name ("right_index") */
	private record Slot(Kind kind, CharacterModel.Bone bone, String tail) {
		Slot(Kind kind, CharacterModel.Bone bone) {
			this(kind, bone, "");
		}
	}

	@SuppressWarnings("unused")
	private static final class PartOptions {
		float @Nullable [] pivot;
		float @Nullable [] hand;
		/** Arms: the elbow joint, and where the forearm ends (the wrist). */
		float @Nullable [] elbow;
		float @Nullable [] wrist;
		/** Fingers: where the finger ends, and which way its tip goes when it curls (toward the palm). */
		float @Nullable [] tip;
		float @Nullable [] curl;
		/** Fingers: where it bends (fractions of its length from the knuckle), and how far each joint bends in a fist (degrees). */
		float @Nullable [] joints;
		float @Nullable [] angles;
		/** Fingers: bends sharply at its joints, like a robot's. */
		@Nullable Boolean rigid;
	}

	/** Finger objects: "right_index", "left_thumb", "fp_right_middle" and so on. */
	private static final Pattern FINGER = Pattern.compile("^(fp_|first_person_)?(right|left)_(thumb|index|middle|ring|little|pinky)(_finger)?$");
	/** Objects held in a hand: "right_hand", "fp_left_hand". */
	private static final Pattern HAND_OBJECT = Pattern.compile("^(fp_|first_person_)?(right|left)_hand$");

	static boolean isModelFolder(Path folder) {
		return Files.isRegularFile(folder.resolve(MODEL_FILE));
	}

	static PlayerCharacter load(TextureManager textureManager, Path folder, String texturePath, List<Identifier> newTextures, List<String> problems)
		throws IOException {
		String id = folder.getFileName().toString();
		Options options = readOptions(folder.resolve("character.json"), problems, id);
		Obj obj = Obj.parse(folder.resolve(MODEL_FILE));
		if (obj.faces.isEmpty()) {
			throw new IOException("model.obj has no faces");
		}

		Identifier texture = WHITE;
		Path texturePng = folder.resolve("texture.png");
		if (Files.isRegularFile(texturePng)) {
			texture = Identifier.fromNamespaceAndPath(PlayerCharactersClient.MOD_ID, texturePath + "/texture");
			register(textureManager, texture, texturePng, "Player Characters: " + id);
			newTextures.add(texture);
		}

		Identifier icon = null;
		Path iconPng = folder.resolve("icon.png");
		if (Files.isRegularFile(iconPng)) {
			try {
				icon = Identifier.fromNamespaceAndPath(PlayerCharactersClient.MOD_ID, texturePath + "/icon");
				register(textureManager, icon, iconPng, "Player Characters: " + id + " (icon)");
				newTextures.add(icon);
			} catch (IOException e) {
				problems.add(id + "/icon.png: " + e.getMessage());
				icon = null;
			}
		}

		CharacterModel model = build(obj, options, texture, id, problems);
		model.makeSimplerCopies();
		if (model.part(CharacterModel.Bone.JAW) == null) {
			problems.add(id + "/model.obj: no part called \"jaw\", so the mouth won't move");
		}
		String name = options.name == null || options.name.isBlank() ? id : options.name.strip();
		PlayerCharactersClient.LOGGER.info("Loaded 3D character {} ({} quads)", id, model.quadCount());
		return PlayerCharacter.ofModel(id, name, model, icon);
	}

	private static void register(TextureManager textureManager, Identifier id, Path png, String label) throws IOException {
		NativeImage image;
		try (InputStream in = Files.newInputStream(png)) {
			image = NativeImage.read(in);
		}
		textureManager.register(id, new DynamicTexture(() -> label, image));
	}

	private static Options readOptions(Path file, List<String> problems, String id) {
		if (!Files.isRegularFile(file)) {
			return new Options();
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			Options options = GSON.fromJson(reader, Options.class);
			return options == null ? new Options() : options;
		} catch (IOException | JsonParseException e) {
			problems.add(id + "/character.json: couldn't read it (" + e.getMessage() + "), using default settings");
			return new Options();
		}
	}

	// ------------------------------------------------------------------ building

	private static CharacterModel build(Obj obj, Options options, Identifier texture, String id, List<String> problems) throws IOException {
		double turn = Math.toRadians(options.turn == null ? 0 : options.turn);
		float cos = (float) Math.cos(turn);
		float sin = (float) Math.sin(turn);

		// Turn the model, then find how tall it is and where its feet are. First-person arms don't count.
		Map<Slot, List<Obj.Face>> facesBySlot = new LinkedHashMap<>();
		int quads = 0;
		for (Obj.Face face : obj.faces) {
			facesBySlot.computeIfAbsent(slotFor(face.group), b -> new ArrayList<>()).add(face);
			quads += face.corners.length <= 4 ? 1 : face.corners.length - 2;
		}
		if (quads > MAX_QUADS) {
			throw new IOException("model.obj is too detailed (" + quads + " polygons, the limit is " + MAX_QUADS + ")");
		}
		float minY = Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		for (Map.Entry<Slot, List<Obj.Face>> entry : facesBySlot.entrySet()) {
			if (isFirstPerson(entry.getKey().kind())) {
				continue;
			}
			for (Obj.Face face : entry.getValue()) {
				for (Obj.Corner corner : face.corners) {
					float y = obj.positions.get(corner.position)[1];
					minY = Math.min(minY, y);
					maxY = Math.max(maxY, y);
				}
			}
		}
		if (minY > maxY) {
			throw new IOException("model.obj has only first-person arms");
		}
		float height = maxY - minY;
		float scale;
		if (options.height != null && options.height > 0) {
			scale = options.height / height;
		} else if (options.size != null && options.size > 0) {
			scale = options.size;
		} else if (height < 0.5F || height > 4.0F) {
			scale = 1.9F / height; // probably made in centimeters or tiny units: fit it to player height
		} else {
			scale = 1.0F;
		}
		Transform t = new Transform(cos, sin, minY, scale / PLAYER_SCALE);

		Map<CharacterModel.Bone, CharacterModel.Part> parts = new EnumMap<>(CharacterModel.Bone.class);
		for (Map.Entry<Slot, List<Obj.Face>> entry : facesBySlot.entrySet()) {
			CharacterModel.Bone bone = entry.getKey().bone();
			if (entry.getKey().kind() == Kind.NORMAL) {
				parts.put(bone, buildPart(obj, entry.getValue(), bone, partOptions(options, bone), t));
			}
		}
		// A joint without polygons, for example a head joint for a jaw that is the whole head (like the burger's bun).
		for (CharacterModel.Bone bone : CharacterModel.Bone.values()) {
			PartOptions partOptions = partOptions(options, bone);
			if (!parts.containsKey(bone) && partOptions != null && partOptions.pivot != null && partOptions.pivot.length == 3) {
				float[] pivot = partOptions.pivot;
				parts.put(bone, new CharacterModel.Part(new float[0], new int[0], t.point(pivot[0], pivot[1], pivot[2]), null));
			}
		}

		// Positive angles open the jaw downward, negative ones open it upward like a lid.
		float jawDegrees = options.jaw_angle == null ? 30.0F : Math.max(-80, Math.min(80, options.jaw_angle));
		Vector3f hip = hip(parts, t.scale * height);
		Vector3f butt;
		if (options.butt != null && options.butt.length == 3) {
			butt = t.bodySpace(t.point(options.butt[0], options.butt[1], options.butt[2]));
		} else {
			butt = t.bodySpace(defaultButt(parts, hip));
		}
		// Emote parts move exactly like the part they belong to, so they use its joint.
		Map<CharacterModel.Bone, CharacterModel.Part> mad = new EnumMap<>(CharacterModel.Bone.class);
		Map<CharacterModel.Bone, CharacterModel.Part> calm = new EnumMap<>(CharacterModel.Bone.class);
		Map<CharacterModel.Bone, CharacterModel.Part> blink = new EnumMap<>(CharacterModel.Bone.class);
		Map<CharacterModel.Bone, CharacterModel.Part> glow = new EnumMap<>(CharacterModel.Bone.class);
		Map<String, List<Obj.Face>> tailFaces = new LinkedHashMap<>();
		Map<String, List<Obj.Face>> tailGlowFaces = new LinkedHashMap<>();
		// Fingers and held things by name ("right_index", "right_hand"), in third person and in first person.
		Map<String, List<Obj.Face>> handFaces = new LinkedHashMap<>();
		Map<String, List<Obj.Face>> fpHandFaces = new LinkedHashMap<>();
		CharacterModel.Part middleFinger = null;
		CharacterModel.Part fpRight = null;
		CharacterModel.Part fpLeft = null;
		CharacterModel.Part fpFinger = null;
		for (Map.Entry<Slot, List<Obj.Face>> entry : facesBySlot.entrySet()) {
			Slot slot = entry.getKey();
			switch (slot.kind()) {
				case NORMAL -> {
					continue;
				}
				case TAIL -> {
					tailFaces.computeIfAbsent(slot.tail(), k -> new ArrayList<>()).addAll(entry.getValue());
					continue;
				}
				case TAIL_GLOW -> {
					tailGlowFaces.computeIfAbsent(slot.tail(), k -> new ArrayList<>()).addAll(entry.getValue());
					continue;
				}
				case HAND_FINGER, HAND -> {
					handFaces.computeIfAbsent(slot.tail(), k -> new ArrayList<>()).addAll(entry.getValue());
					continue;
				}
				case FIRST_PERSON_HAND_FINGER, FIRST_PERSON_HAND -> {
					fpHandFaces.computeIfAbsent(slot.tail(), k -> new ArrayList<>()).addAll(entry.getValue());
					continue;
				}
				default -> {
				}
			}
			CharacterModel.Part part = buildPart(obj, entry.getValue(), slot.bone(), partOptions(options, slot.bone()), t);
			CharacterModel.Part main = parts.get(slot.bone());
			if (main != null) {
				part = isFirstPerson(slot.kind()) ? new CharacterModel.Part(part.vertices(), part.colors(), main.pivot(), main.hand()) : part.withPivot(main.pivot());
			}
			switch (slot.kind()) {
				case MAD -> mad.put(slot.bone(), part);
				case CALM -> calm.put(slot.bone(), part);
				case BLINK -> blink.put(slot.bone(), part);
				case GLOW -> glow.put(slot.bone(), part);
				case FIRST_PERSON_RIGHT -> fpRight = part;
				case FIRST_PERSON_LEFT -> fpLeft = part;
				case FIRST_PERSON_FINGER -> fpFinger = part;
				default -> middleFinger = part;
			}
		}
		Map<String, CharacterModel.Tail> tails = buildTails(obj, tailFaces, tailGlowFaces, options, parts, t, id, problems);

		float waddle = options.waddle == null ? 0.0F : Math.max(0, Math.min(30, options.waddle));
		float headTurn = options.head_turn == null ? 1.0F : Math.max(0, Math.min(1, options.head_turn));
		Vector3f smoke = options.smoke != null && options.smoke.length == 3 ? t.point(options.smoke[0], options.smoke[1], options.smoke[2]) : null;
		float bounce = options.bounce == null ? 0.0F : Math.max(0, Math.min(0.3F, options.bounce)) * t.scale * PLAYER_SCALE;
		CharacterModel.Stance stance = "four_legs".equalsIgnoreCase(options.stance) || "four legs".equalsIgnoreCase(options.stance)
			? CharacterModel.Stance.FOUR_LEGS : CharacterModel.Stance.TWO_LEGS;
		CharacterModel.Arms arms = "rifle".equalsIgnoreCase(options.arms) ? CharacterModel.Arms.RIFLE : CharacterModel.Arms.NORMAL;
		List<CharacterModel.Emitter> emitters = emitters(options, tails, t, id, problems);
		CharacterModel.ArmRig rightRig = armRig(obj, options, parts, CharacterModel.Bone.RIGHT_ARM, handFaces, fpHandFaces, t);
		CharacterModel.ArmRig leftRig = armRig(obj, options, parts, CharacterModel.Bone.LEFT_ARM, handFaces, fpHandFaces, t);
		for (String orphan : handFaces.keySet()) {
			problems.add(id + "/model.obj: \"" + orphan + "\" has no arm to belong to");
		}
		CharacterModel.Extras extras = new CharacterModel.Extras(mad, calm, middleFinger, (float) Math.toRadians(waddle), headTurn, smoke, blink, bounce,
			glow, List.copyOf(tails.values()), stance, arms, Boolean.TRUE.equals(options.hide_held_items),
			new CharacterModel.FirstPerson(fpRight, fpLeft, fpFinger), emitters, rightRig, leftRig);
		return new CharacterModel(parts, texture, (float) Math.toRadians(jawDegrees), hip, butt, height * scale, extras);
	}

	private static boolean isFirstPerson(Kind kind) {
		return kind == Kind.FIRST_PERSON_RIGHT || kind == Kind.FIRST_PERSON_LEFT || kind == Kind.FIRST_PERSON_FINGER || kind == Kind.FIRST_PERSON_HAND_FINGER
			|| kind == Kind.FIRST_PERSON_HAND;
	}

	/**
	 * Tails by name, in the order they appear in model.obj. Each sways around its base, which is its joint in
	 * character.json or its point nearest the body.
	 */
	private static Map<String, CharacterModel.Tail> buildTails(Obj obj, Map<String, List<Obj.Face>> tailFaces, Map<String, List<Obj.Face>> glowFaces,
		Options options, Map<CharacterModel.Bone, CharacterModel.Part> parts, Transform t, String id, List<String> problems) {
		Map<String, CharacterModel.Tail> tails = new LinkedHashMap<>();
		CharacterModel.Part body = parts.get(CharacterModel.Bone.BODY);
		Vector3f bodyPivot = body != null ? body.pivot() : new Vector3f(0.0F, FEET_Y * 0.5F, 0.0F);
		for (Map.Entry<String, List<Obj.Face>> entry : tailFaces.entrySet()) {
			CharacterModel.Part part = buildPart(obj, entry.getValue(), CharacterModel.Bone.BODY, null, t);
			PartOptions partOptions = options.parts == null ? null : options.parts.get(entry.getKey());
			Vector3f base;
			if (partOptions != null && partOptions.pivot != null && partOptions.pivot.length == 3) {
				base = t.point(partOptions.pivot[0], partOptions.pivot[1], partOptions.pivot[2]);
			} else {
				base = nearest(part, bodyPivot);
			}
			Vector3f tip = new Vector3f(base);
			float length = 0.0F;
			float[] v = part.vertices();
			for (int o = 0; o < v.length; o += 8) {
				float d = base.distance(v[o], v[o + 1], v[o + 2]);
				if (d > length) {
					length = d;
					tip.set(v[o], v[o + 1], v[o + 2]);
				}
			}
			List<Obj.Face> glow = glowFaces.remove(entry.getKey());
			CharacterModel.Part glowPart = glow == null ? null : buildPart(obj, glow, CharacterModel.Bone.BODY, null, t);
			tails.put(entry.getKey(), new CharacterModel.Tail(part.withPivot(base), glowPart == null ? null : glowPart.withPivot(base), base,
				Math.max(0.01F, length), tip, tails.size()));
		}
		for (String orphan : glowFaces.keySet()) {
			problems.add(id + "/model.obj: \"glow_" + orphan + "\" has no tail called \"" + orphan + "\"");
		}
		return tails;
	}

	/**
	 * How an arm bends: at its elbow (from character.json, or about halfway down the arm), and with its fingers if it has
	 * finger objects. Null when the model has no such arm.
	 */
	private static CharacterModel.@Nullable ArmRig armRig(Obj obj, Options options, Map<CharacterModel.Bone, CharacterModel.Part> parts,
		CharacterModel.Bone arm, Map<String, List<Obj.Face>> handFaces, Map<String, List<Obj.Face>> fpHandFaces, Transform t) {
		CharacterModel.Part part = parts.get(arm);
		if (part == null || part.vertexCount() == 0) {
			return null;
		}
		String side = arm == CharacterModel.Bone.RIGHT_ARM ? "right" : "left";
		PartOptions armOptions = partOptions(options, arm);
		Vector3f shoulder = new Vector3f(part.pivot());
		Vector3f far = farthest(part, shoulder);
		Vector3f elbow = point(armOptions == null ? null : armOptions.elbow, t, new Vector3f(shoulder).lerp(far, 0.47F));
		Vector3f wrist = point(armOptions == null ? null : armOptions.wrist, t, new Vector3f(shoulder).lerp(far, 0.82F));
		Vector3f along = new Vector3f(wrist).sub(shoulder);
		along = along.lengthSquared() < 1.0E-8F ? new Vector3f(0.0F, 1.0F, 0.0F) : along.normalize();
		// Bending forward: the forearm turns toward the front (-Z) around an axis across the arm.
		Vector3f hinge = new Vector3f(along).cross(0.0F, 0.0F, -1.0F);
		if (hinge.lengthSquared() < 0.04F) {
			hinge.set(-1.0F, 0.0F, 0.0F).sub(new Vector3f(along).mul(-along.x));
		}
		hinge.normalize();

		List<CharacterModel.Finger> fingers = new ArrayList<>();
		for (CharacterModel.FingerRole role : CharacterModel.FingerRole.values()) {
			String name = side + "_" + role.name().toLowerCase(Locale.ROOT);
			List<Obj.Face> faces = handFaces.remove(name);
			List<Obj.Face> fpFaces = fpHandFaces.remove(name);
			if (faces == null && fpFaces == null) {
				continue;
			}
			CharacterModel.Part fingerPart = faces == null ? new CharacterModel.Part(new float[0], new int[0], shoulder, null)
				: buildPart(obj, faces, arm, null, t).withPivot(shoulder);
			CharacterModel.Part fpPart = fpFaces == null ? null : buildPart(obj, fpFaces, arm, null, t).withPivot(shoulder);
			CharacterModel.Part shape = faces != null ? fingerPart : fpPart;
			PartOptions fingerOptions = options.parts == null ? null
				: options.parts.getOrDefault(name, role == CharacterModel.FingerRole.LITTLE ? options.parts.get(side + "_pinky") : null);
			Vector3f base = point(fingerOptions == null ? null : fingerOptions.pivot, t, nearest(shape, wrist));
			Vector3f tip = point(fingerOptions == null ? null : fingerOptions.tip, t, farthest(shape, base));
			Vector3f axis = new Vector3f(tip).sub(base);
			float length = axis.length();
			axis = length < 1.0E-4F ? new Vector3f(along) : axis.div(length);
			boolean thumb = role == CharacterModel.FingerRole.THUMB;
			float inward = arm == CharacterModel.Bone.RIGHT_ARM ? 1.0F : -1.0F; // toward the body, where a hanging hand's palm faces
			Vector3f curl = fingerOptions != null && fingerOptions.curl != null && fingerOptions.curl.length == 3
				? t.direction(fingerOptions.curl[0], fingerOptions.curl[1], fingerOptions.curl[2])
				: new Vector3f(inward, 0.0F, thumb ? 1.0F : 0.0F).normalize();
			curl.sub(new Vector3f(axis).mul(curl.dot(axis)));
			if (curl.lengthSquared() < 1.0E-6F) {
				curl.set(hinge).cross(axis);
			}
			curl.normalize();
			Vector3f bend = new Vector3f(axis).cross(curl).normalize();
			float[] joints = thumb ? new float[] {0.0F, 0.5F} : new float[] {0.0F, 0.44F, 0.74F};
			float[] angles = thumb ? new float[] {40.0F, 55.0F} : new float[] {75.0F, 95.0F, 65.0F};
			if (fingerOptions != null && fingerOptions.joints != null && fingerOptions.joints.length >= 1 && fingerOptions.joints.length <= 6) {
				joints = fingerOptions.joints.clone();
				float[] given = fingerOptions.angles != null && fingerOptions.angles.length == joints.length ? fingerOptions.angles : null;
				angles = new float[joints.length];
				for (int i = 0; i < joints.length; i++) {
					joints[i] = Math.max(0.0F, Math.min(1.0F, joints[i]));
					angles[i] = given != null ? given[i] : 240.0F / joints.length;
				}
			} else if (fingerOptions != null && fingerOptions.angles != null && fingerOptions.angles.length == joints.length) {
				angles = fingerOptions.angles.clone();
			}
			for (int i = 0; i < angles.length; i++) {
				angles[i] = (float) Math.toRadians(Math.max(-90.0F, Math.min(180.0F, angles[i])));
			}
			fingers.add(new CharacterModel.Finger(role, fingerPart, fpPart, base, axis, Math.max(length, 1.0E-4F), bend, joints, angles,
				fingerOptions != null && Boolean.TRUE.equals(fingerOptions.rigid)));
		}
		List<Obj.Face> heldFaces = handFaces.remove(side + "_hand");
		List<Obj.Face> heldGlowFaces = handFaces.remove("glow_" + side + "_hand");
		List<Obj.Face> fpHeldFaces = fpHandFaces.remove(side + "_hand");
		CharacterModel.Part held = heldFaces == null ? null : buildPart(obj, heldFaces, arm, null, t).withPivot(shoulder);
		CharacterModel.Part heldGlow = heldGlowFaces == null ? null : buildPart(obj, heldGlowFaces, arm, null, t).withPivot(shoulder);
		CharacterModel.Part fpHeld = fpHeldFaces == null ? null : buildPart(obj, fpHeldFaces, arm, null, t).withPivot(shoulder);
		return new CharacterModel.ArmRig(shoulder, elbow, wrist, hinge, elbowBlend(part, elbow, along), fingers, held, heldGlow, fpHeld);
	}

	private static Vector3f point(float @Nullable [] given, Transform t, Vector3f otherwise) {
		return given != null && given.length == 3 ? t.point(given[0], given[1], given[2]) : otherwise;
	}

	/** How softly the arm bends: a bit more than half its thickness at the elbow, on each side. */
	private static float elbowBlend(CharacterModel.Part part, Vector3f elbow, Vector3f along) {
		float[] v = part.vertices();
		FloatArrayList radii = new FloatArrayList();
		Vector3f d = new Vector3f();
		for (int o = 0; o < v.length; o += 8) {
			d.set(v[o], v[o + 1], v[o + 2]).sub(elbow);
			float s = d.dot(along);
			if (Math.abs(s) < 0.03F) {
				radii.add(d.sub(along.x * s, along.y * s, along.z * s).length());
			}
		}
		if (radii.isEmpty()) {
			return 0.02F;
		}
		float[] sorted = radii.toFloatArray();
		Arrays.sort(sorted);
		return Math.max(0.006F, Math.min(0.08F, sorted[sorted.length / 2] * 0.6F));
	}

	private static Vector3f farthest(CharacterModel.Part part, Vector3f from) {
		Vector3f best = new Vector3f(from);
		float bestDistance = -1.0F;
		float[] v = part.vertices();
		for (int o = 0; o < v.length; o += 8) {
			float d = from.distanceSquared(v[o], v[o + 1], v[o + 2]);
			if (d > bestDistance) {
				bestDistance = d;
				best.set(v[o], v[o + 1], v[o + 2]);
			}
		}
		return best;
	}

	private static Vector3f nearest(CharacterModel.Part part, Vector3f to) {
		Vector3f best = new Vector3f(to);
		float bestDistance = Float.MAX_VALUE;
		float[] v = part.vertices();
		for (int o = 0; o < v.length; o += 8) {
			float d = to.distanceSquared(v[o], v[o + 1], v[o + 2]);
			if (d < bestDistance) {
				bestDistance = d;
				best.set(v[o], v[o + 1], v[o + 2]);
			}
		}
		return best;
	}

	private static List<CharacterModel.Emitter> emitters(Options options, Map<String, CharacterModel.Tail> tails, Transform t, String id,
		List<String> problems) {
		List<CharacterModel.Emitter> emitters = new ArrayList<>();
		if (options.particles == null) {
			return emitters;
		}
		for (ParticleOptions p : options.particles) {
			if (p == null || p.particle == null || p.particle.isBlank()) {
				problems.add(id + "/character.json: a particle without \"particle\" was skipped");
				continue;
			}
			String particle = p.particle.strip().toLowerCase(Locale.ROOT);
			if (!particle.contains(":")) {
				particle = "minecraft:" + particle;
			}
			int color = 0xFFFFFF;
			if (p.color != null) {
				try {
					color = Integer.parseInt(p.color.strip().replace("#", ""), 16) & 0xFFFFFF;
				} catch (NumberFormatException e) {
					problems.add(id + "/character.json: particle color \"" + p.color + "\" isn't like \"#55E0FF\"");
				}
			}
			String partName = p.part == null ? "body" : normalize(p.part);
			CharacterModel.Tail attachedTail = tails.get(partName);
			int tail = attachedTail == null ? -1 : attachedTail.index();
			CharacterModel.Bone bone = attachedTail == null ? boneFor(partName) : CharacterModel.Bone.BODY;
			Vector3f at;
			if (p.at != null && p.at.length == 3) {
				at = t.point(p.at[0], p.at[1], p.at[2]);
			} else if (attachedTail != null) {
				at = new Vector3f(attachedTail.tip());
			} else {
				at = new Vector3f(0.0F, FEET_Y * 0.5F, 0.0F);
			}
			CharacterModel.Emitter.When when = switch (p.when == null ? "always" : p.when.strip().toLowerCase(Locale.ROOT)) {
				case "moving", "walking" -> CharacterModel.Emitter.When.MOVING;
				case "sprinting", "running" -> CharacterModel.Emitter.When.SPRINTING;
				case "still", "standing_still", "standing" -> CharacterModel.Emitter.When.STANDING_STILL;
				default -> CharacterModel.Emitter.When.ALWAYS;
			};
			Vector3f velocity = new Vector3f();
			if (p.velocity != null && p.velocity.length == 3) {
				float x = p.velocity[0];
				float z = p.velocity[2];
				velocity.set(x * t.cos + z * t.sin, p.velocity[1], -x * t.sin + z * t.cos);
			}
			emitters.add(new CharacterModel.Emitter(particle, color, p.size == null ? 1.0F : Math.max(0.05F, Math.min(4.0F, p.size)), bone, tail, at,
				p.every == null ? 4 : Math.max(1, Math.min(200, p.every)), when, p.spread == null ? 0.0F : Math.max(0.0F, Math.min(2.0F, p.spread)), velocity));
		}
		return emitters;
	}

	static Slot slotFor(String group) {
		String name = normalize(group);
		Matcher finger = FINGER.matcher(name);
		if (finger.matches()) {
			String role = finger.group(3).equals("pinky") ? "little" : finger.group(3);
			String id = finger.group(2) + "_" + role;
			CharacterModel.Bone arm = finger.group(2).equals("right") ? CharacterModel.Bone.RIGHT_ARM : CharacterModel.Bone.LEFT_ARM;
			return new Slot(finger.group(1) != null ? Kind.FIRST_PERSON_HAND_FINGER : Kind.HAND_FINGER, arm, id);
		}
		Matcher hand = HAND_OBJECT.matcher(name.startsWith("glow_") ? name.substring(5) : name);
		if (hand.matches()) {
			CharacterModel.Bone arm = hand.group(2).equals("right") ? CharacterModel.Bone.RIGHT_ARM : CharacterModel.Bone.LEFT_ARM;
			if (name.startsWith("glow_")) {
				return new Slot(Kind.HAND, arm, "glow_" + hand.group(2) + "_hand");
			}
			return new Slot(hand.group(1) != null ? Kind.FIRST_PERSON_HAND : Kind.HAND, arm, hand.group(2) + "_hand");
		}
		if (name.startsWith("fp_") || name.startsWith("first_person_")) {
			String rest = name.substring(name.startsWith("fp_") ? 3 : 13);
			if (rest.startsWith("middle_finger")) {
				return new Slot(Kind.FIRST_PERSON_FINGER, CharacterModel.Bone.RIGHT_ARM);
			}
			CharacterModel.Bone bone = boneFor(rest);
			if (bone == CharacterModel.Bone.RIGHT_ARM) {
				return new Slot(Kind.FIRST_PERSON_RIGHT, bone);
			}
			if (bone == CharacterModel.Bone.LEFT_ARM) {
				return new Slot(Kind.FIRST_PERSON_LEFT, bone);
			}
		}
		if (name.startsWith("middle_finger")) {
			return new Slot(Kind.MIDDLE_FINGER, CharacterModel.Bone.RIGHT_ARM);
		}
		if (name.startsWith("glow_tail")) {
			return new Slot(Kind.TAIL_GLOW, CharacterModel.Bone.BODY, name.substring(5));
		}
		if (name.startsWith("tail")) {
			return new Slot(Kind.TAIL, CharacterModel.Bone.BODY, name);
		}
		if (name.startsWith("glow_")) {
			return new Slot(Kind.GLOW, boneFor(name.substring(5)));
		}
		if (name.startsWith("mad_")) {
			return new Slot(Kind.MAD, boneFor(name.substring(4)));
		}
		if (name.startsWith("calm_")) {
			return new Slot(Kind.CALM, boneFor(name.substring(5)));
		}
		if (name.startsWith("blink_")) {
			return new Slot(Kind.BLINK, boneFor(name.substring(6)));
		}
		return new Slot(Kind.NORMAL, boneFor(name));
	}

	private static String normalize(String group) {
		return group.strip().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_').replace('.', '_');
	}

	private static @Nullable PartOptions partOptions(Options options, CharacterModel.Bone bone) {
		return options.parts == null ? null : options.parts.get(bone.name().toLowerCase(Locale.ROOT));
	}

	/** Model space: where the legs meet the body, which is what the upper body bends around. */
	private static Vector3f hip(Map<CharacterModel.Bone, CharacterModel.Part> parts, float modelHeight) {
		CharacterModel.Part right = parts.get(CharacterModel.Bone.RIGHT_LEG);
		CharacterModel.Part left = parts.get(CharacterModel.Bone.LEFT_LEG);
		if (right != null && left != null) {
			return new Vector3f(right.pivot()).add(left.pivot()).mul(0.5F);
		}
		if (right != null || left != null) {
			Vector3f leg = new Vector3f((right != null ? right : left).pivot());
			return leg.set(0.0F, leg.y, leg.z);
		}
		return new Vector3f(0.0F, FEET_Y - modelHeight * 0.42F, 0.0F);
	}

	/** Model space: the middle of the body's back, a little above the hips. */
	private static Vector3f defaultButt(Map<CharacterModel.Bone, CharacterModel.Part> parts, Vector3f hip) {
		CharacterModel.Part body = parts.get(CharacterModel.Bone.BODY);
		Iterable<CharacterModel.Part> search = body != null && body.vertexCount() > 0 ? List.of(body) : parts.values();
		float back = hip.z;
		for (CharacterModel.Part part : search) {
			float[] v = part.vertices();
			for (int o = 0; o < v.length; o += 8) {
				back = Math.max(back, v[o + 2]); // the model faces -Z, so the back is the largest z
			}
		}
		return new Vector3f(hip.x, hip.y - 0.05F, back + 0.02F);
	}

	private static CharacterModel.Part buildPart(Obj obj, List<Obj.Face> faces, CharacterModel.Bone bone, @Nullable PartOptions options, Transform t) {
		List<Obj.Corner[]> quads = new ArrayList<>();
		for (Obj.Face face : faces) {
			Obj.Corner[] c = face.corners;
			if (c.length == 3) {
				quads.add(new Obj.Corner[] {c[0], c[1], c[2], c[2]});
			} else if (c.length == 4) {
				quads.add(c);
			} else {
				for (int i = 1; i < c.length - 1; i++) {
					quads.add(new Obj.Corner[] {c[0], c[i], c[i + 1], c[i + 1]});
				}
			}
		}

		float[] vertices = new float[quads.size() * 4 * 8];
		int[] colors = new int[quads.size() * 4];
		float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
		float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		int v = 0;
		for (Obj.Corner[] quad : quads) {
			float[] flatNormal = null;
			for (Obj.Corner corner : quad) {
				float[] p = obj.positions.get(corner.position);
				for (int axis = 0; axis < 3; axis++) {
					min[axis] = Math.min(min[axis], t.turnedAxis(p, axis));
					max[axis] = Math.max(max[axis], t.turnedAxis(p, axis));
				}
				Vector3f mp = t.point(p[0], p[1], p[2]);
				float[] n;
				if (corner.normal >= 0) {
					n = obj.normals.get(corner.normal);
				} else {
					if (flatNormal == null) {
						flatNormal = faceNormal(obj, quad);
					}
					n = flatNormal;
				}
				Vector3f mn = t.direction(n[0], n[1], n[2]);
				float u = 0;
				float tv = 0;
				if (corner.uv >= 0) {
					float[] uv = obj.uvs.get(corner.uv);
					u = uv[0];
					tv = 1 - uv[1];
				}
				int o = v * 8;
				vertices[o] = mp.x;
				vertices[o + 1] = mp.y;
				vertices[o + 2] = mp.z;
				vertices[o + 3] = mn.x;
				vertices[o + 4] = mn.y;
				vertices[o + 5] = mn.z;
				vertices[o + 6] = u;
				vertices[o + 7] = tv;
				colors[v] = obj.colors.get(corner.position);
				v++;
			}
		}

		// Default joints from the part's size: legs and arms hang from their top, the head sits on its bottom,
		// the jaw hinges at its top back.
		float cx = (min[0] + max[0]) / 2;
		float cz = (min[2] + max[2]) / 2;
		float[] defaultPivot = switch (bone) {
			case HEAD -> new float[] {cx, min[1], cz};
			case JAW -> new float[] {cx, max[1], min[2]};
			default -> new float[] {cx, max[1], cz};
		};
		float[] pivot = options != null && options.pivot != null && options.pivot.length == 3 ? options.pivot : null;
		Vector3f pivotModel = pivot != null ? t.point(pivot[0], pivot[1], pivot[2]) : t.turnedPoint(defaultPivot);

		Vector3f hand = null;
		if (bone == CharacterModel.Bone.RIGHT_ARM || bone == CharacterModel.Bone.LEFT_ARM) {
			float[] h = options != null && options.hand != null && options.hand.length == 3 ? options.hand : null;
			hand = h != null ? t.point(h[0], h[1], h[2]) : t.turnedPoint(new float[] {cx, min[1] + (max[1] - min[1]) * 0.12F, max[2]});
		}
		return new CharacterModel.Part(vertices, colors, pivotModel, hand);
	}

	private static float[] faceNormal(Obj obj, Obj.Corner[] quad) {
		float[] a = obj.positions.get(quad[0].position);
		float[] b = obj.positions.get(quad[1].position);
		float[] c = obj.positions.get(quad[2].position);
		Vector3f n = new Vector3f(b[0] - a[0], b[1] - a[1], b[2] - a[2]).cross(c[0] - a[0], c[1] - a[1], c[2] - a[2]);
		if (n.lengthSquared() < 1.0E-12F) {
			return new float[] {0, 1, 0};
		}
		n.normalize();
		return new float[] {n.x, n.y, n.z};
	}

	static CharacterModel.Bone boneFor(String group) {
		String name = normalize(group);
		for (CharacterModel.Bone bone : CharacterModel.Bone.values()) {
			for (String alias : aliases(bone)) {
				if (name.equals(alias) || name.startsWith(alias + "_")) {
					return bone;
				}
			}
		}
		return CharacterModel.Bone.BODY;
	}

	private static List<String> aliases(CharacterModel.Bone bone) {
		return switch (bone) {
			case HEAD -> List.of("head");
			case JAW -> List.of("jaw", "lower_jaw", "mouth", "chin");
			case BODY -> List.of("body", "torso", "chest");
			case RIGHT_ARM -> List.of("right_arm", "rightarm", "arm_right", "arm_r", "r_arm", "front_right_leg");
			case LEFT_ARM -> List.of("left_arm", "leftarm", "arm_left", "arm_l", "l_arm", "front_left_leg");
			case RIGHT_LEG -> List.of("right_leg", "rightleg", "leg_right", "leg_r", "r_leg", "back_right_leg");
			case LEFT_LEG -> List.of("left_leg", "leftleg", "leg_left", "leg_l", "l_leg", "back_left_leg");
		};
	}

	/** Turns the model, puts its feet on the ground, scales it and converts it to player model space. */
	private record Transform(float cos, float sin, float minY, float scale) {
		float turnedAxis(float[] p, int axis) {
			return switch (axis) {
				case 0 -> p[0] * this.cos + p[2] * this.sin;
				case 1 -> p[1];
				default -> -p[0] * this.sin + p[2] * this.cos;
			};
		}

		Vector3f point(float x, float y, float z) {
			return this.turnedPoint(new float[] {x * this.cos + z * this.sin, y, -x * this.sin + z * this.cos});
		}

		/** For points that are already turned. */
		Vector3f turnedPoint(float[] p) {
			return new Vector3f(p[0] * this.scale, FEET_Y - (p[1] - this.minY) * this.scale, -p[2] * this.scale);
		}

		/**
		 * From model space to blocks relative to the feet as the player renderer draws it (after its 15/16 scale):
		 * x toward the character's left, y up, z forward.
		 */
		Vector3f bodySpace(Vector3f model) {
			return new Vector3f(model.x * PLAYER_SCALE, (FEET_Y - model.y) * PLAYER_SCALE, -model.z * PLAYER_SCALE);
		}

		Vector3f direction(float x, float y, float z) {
			float tx = x * this.cos + z * this.sin;
			float tz = -x * this.sin + z * this.cos;
			Vector3f n = new Vector3f(tx, -y, -tz);
			return n.lengthSquared() < 1.0E-12F ? new Vector3f(0, -1, 0) : n.normalize();
		}
	}

	// ------------------------------------------------------------------ OBJ parsing

	/** Minimal Wavefront OBJ reader: positions (with optional vertex colors), UVs, normals, groups and polygons. */
	static final class Obj {
		final List<float[]> positions = new ArrayList<>();
		final List<Integer> colors = new ArrayList<>();
		final List<float[]> uvs = new ArrayList<>();
		final List<float[]> normals = new ArrayList<>();
		final List<Face> faces = new ArrayList<>();

		record Corner(int position, int uv, int normal) {
		}

		record Face(String group, Corner[] corners) {
		}

		static Obj parse(Path file) throws IOException {
			Obj obj = new Obj();
			String group = "body";
			int lineNumber = 0;
			for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
				lineNumber++;
				String line = raw.strip();
				if (line.isEmpty() || line.charAt(0) == '#') {
					continue;
				}
				String[] tokens = line.split("\\s+");
				try {
					switch (tokens[0]) {
						case "v" -> {
							obj.positions.add(new float[] {Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]), Float.parseFloat(tokens[3])});
							obj.colors.add(tokens.length >= 7 ? color(tokens[4], tokens[5], tokens[6]) : 0xFFFFFFFF);
						}
						case "vt" -> obj.uvs.add(new float[] {Float.parseFloat(tokens[1]), tokens.length > 2 ? Float.parseFloat(tokens[2]) : 0});
						case "vn" -> obj.normals.add(new float[] {Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]), Float.parseFloat(tokens[3])});
						case "o", "g" -> group = tokens.length > 1 ? line.substring(tokens[0].length()).strip() : "body";
						case "f" -> {
							if (tokens.length >= 4) {
								Corner[] corners = new Corner[tokens.length - 1];
								for (int i = 1; i < tokens.length; i++) {
									corners[i - 1] = obj.corner(tokens[i]);
								}
								obj.faces.add(new Face(group, corners));
							}
						}
						default -> {
							// mtllib, usemtl, s, l and other lines aren't needed
						}
					}
				} catch (RuntimeException e) {
					throw new IOException("model.obj line " + lineNumber + " couldn't be read: " + line, e);
				}
			}
			return obj;
		}

		private Corner corner(String token) {
			String[] parts = token.split("/", -1);
			int position = index(parts[0], this.positions.size());
			int uv = parts.length > 1 && !parts[1].isEmpty() ? index(parts[1], this.uvs.size()) : -1;
			int normal = parts.length > 2 && !parts[2].isEmpty() ? index(parts[2], this.normals.size()) : -1;
			if (position < 0 || position >= this.positions.size()) {
				throw new IllegalArgumentException("vertex " + parts[0] + " doesn't exist");
			}
			return new Corner(position, uv >= 0 && uv < this.uvs.size() ? uv : -1, normal >= 0 && normal < this.normals.size() ? normal : -1);
		}

		/** OBJ indices start at 1; negative ones count back from the end. */
		private static int index(String value, int count) {
			int i = Integer.parseInt(value);
			return i < 0 ? count + i : i - 1;
		}

		private static int color(String r, String g, String b) {
			int red = Math.round(Math.max(0, Math.min(1, Float.parseFloat(r))) * 255);
			int green = Math.round(Math.max(0, Math.min(1, Float.parseFloat(g))) * 255);
			int blue = Math.round(Math.max(0, Math.min(1, Float.parseFloat(b))) * 255);
			return 0xFF000000 | red << 16 | green << 8 | blue;
		}
	}
}
