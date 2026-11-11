import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the example 3D characters of the Player Characters mod (Alien, Robot, Frog, Burger). Each one is written
 * as {@code <Name>/model.obj} (with vertex colors), {@code character.json} and {@code icon.png}.
 * <p>
 * Smooth body shapes are sculpted as signed distance fields and turned into quads with surface nets. Thin parts
 * (fingers, legs, teeth, eyes) are built directly as tubes and ellipsoids, which needs far fewer polygons.
 * <p>
 * Coordinates: 1 unit = 1 block, Y up, feet at y = 0, facing +Z, the character's right side at -X.
 * <p>
 * Run: {@code java CharacterModels.java <outputFolder> [previewFolder] [onlyThisCharacter]}
 */
public class CharacterModels {
	public static void main(String[] args) throws IOException {
		Path out = Path.of(args[0]);
		Path previews = args.length > 1 ? Path.of(args[1]) : null;
		String only = args.length > 2 ? args[2] : null;
		for (Design design : List.of(new Alien(), new Robot(), new Frog(), new Burger(), new Penguin(), new SpongeBob(), new SkinnyAlien(), new SixSeven(),
			new WhiteQueen(), new BlackWolf())) {
			if (only != null && !only.equalsIgnoreCase(design.name())) {
				continue;
			}
			Map<String, Mesh> parts = design.build();
			if (!parts.containsKey("fp_right_arm")) {
				// First-person arms: the same arms (and fingers) built again with smaller polygons, and finer skin.
				detailScale = 0.6;
				Map<String, Mesh> detailed = design.build();
				detailScale = 1.0;
				for (Map.Entry<String, Mesh> entry : detailed.entrySet()) {
					String arm = entry.getKey();
					if (arm.equals("right_arm") || arm.equals("left_arm") || arm.equals("middle_finger") || isHandPart(arm)) {
						addFineDetail(entry.getValue());
						parts.put("fp_" + arm, entry.getValue());
					}
				}
			}
			int quads = 0;
			for (Mesh m : parts.values()) {
				quads += m.quads.size();
			}
			System.out.printf(Locale.ROOT, "%-7s %6d quads%n", design.name(), quads);
			Path dir = out.resolve(design.name());
			Files.createDirectories(dir);
			writeObj(dir.resolve("model.obj"), design, parts);
			writeJson(dir.resolve("character.json"), design);
			ImageIO.write(renderIcon(design, parts), "png", dir.resolve("icon.png").toFile());
			if (previews != null) {
				Files.createDirectories(previews);
				writePreviews(design, parts, previews);
			}
		}
	}

	/** Below 1 while building first-person arms: everything is made of smaller polygons. */
	static double detailScale = 1.0;

	/** Fine grain on a first-person arm's colors (skin pores, brushed metal), seen up close. */
	static void addFineDetail(Mesh m) {
		for (int i = 0; i < m.pos.size(); i++) {
			V p = m.pos.get(i);
			double k = 1 + 0.045 * noise(p.mul(170)) + 0.03 * noise(p.mul(430));
			double[] c = m.color.get(i);
			m.color.set(i, rgb(c[0] * k, c[1] * k, c[2] * k));
		}
	}

	/** A segment count for round shapes, more of them while building detailed parts. */
	static int segments(int seg) {
		return detailScale >= 1 ? seg : (int) Math.ceil(seg / detailScale);
	}

	// ================================================================== designs

	/** Joints and special points in the final pose, written to character.json. */
	record Rig(double jawAngle, V head, V jaw, V body, V rightArm, V rightHand, V leftArm, V leftHand, V rightLeg, V leftLeg, V butt) {
	}

	/**
	 * A finger that curls in the mod: its knuckle, its tip, and which way the tip goes when it curls (toward the palm).
	 * {@code rigid} fingers (a robot's) bend sharply at their joints. {@code joints} (fractions of the length) and
	 * {@code angles} (degrees, for a fist) can be null for the mod's defaults.
	 */
	record FingerSpec(V base, V tip, V curl, boolean rigid, double[] joints, double[] angles) {
		FingerSpec(V base, V tip, V curl, boolean rigid) {
			this(base, tip, curl, rigid, null, null);
		}
	}

	/** Names of the parts that belong to a hand: fingers ("right_index") and things held ("right_hand"). */
	static boolean isHandPart(String name) {
		return name.matches("(right|left)_(thumb|index|middle|ring|little|hand)");
	}

	abstract static class Design {
		/** Fingers that curl, by part name ("right_index"), filled in while building. */
		final Map<String, FingerSpec> fingerSpecs = new LinkedHashMap<>();
		/** Elbows and wrists ("right_elbow", "left_wrist"), filled in while building. */
		final Map<String, V> armJoints = new LinkedHashMap<>();

		/** Adds a finger: its mesh as a part of its own, and its joint for character.json. */
		void addFinger(Map<String, Mesh> parts, String name, Mesh mesh, FingerSpec spec) {
			parts.put(name, mesh);
			this.fingerSpecs.put(name, spec);
		}

		/** Where an arm bends (side -1 is the right arm): the elbow, and the wrist where the forearm's twist ends. */
		void elbow(double side, V elbow, V wrist) {
			String prefix = side < 0 ? "right_" : "left_";
			this.armJoints.put(prefix + "elbow", elbow);
			this.armJoints.put(prefix + "wrist", wrist);
		}

		/** The folder name, which is also the character's id in the mod. */
		abstract String name();

		/** The name shown in the menus. */
		String displayName() {
			return name();
		}

		/** Where smoke rises from (moving with the head), or null. */
		V smoke() {
			return null;
		}

		/** How far the character bobs up and down with each step, in blocks. */
		double bounce() {
			return 0.03;
		}

		/** All parts in their final pose, with colors. */
		abstract Map<String, Mesh> build();

		abstract Rig rig();

		/** Camera for the menu icon: where it looks from and at (roughly the face). */
		abstract V iconEye();

		abstract V iconTarget();

		double iconFov() {
			return 0.55;
		}

		/** Height used to frame the full-body previews. */
		double height() {
			return 1.9;
		}

		/** Degrees the whole character rocks from side to side while walking (0 = off). */
		double waddle() {
			return 0;
		}

		/** How much of the player's head turning the head part copies (1 = all of it). */
		double headTurn() {
			return 1;
		}

		/** "four_legs" for animals that walk on all fours, or null. */
		String stance() {
			return null;
		}

		/** "rifle" for characters holding a rifle in the right hand, aimed where the player looks; or null. */
		String arms() {
			return null;
		}

		/** Whether held items are left out (the hands already hold something, or they are paws). */
		boolean hideHeldItems() {
			return false;
		}

		/** More joints for character.json's "parts", like the bases of tails. */
		Map<String, V> extraJoints() {
			return Map.of();
		}

		/** Entries for character.json's "particles", as JSON objects. */
		List<String> particles() {
			return List.of();
		}
	}

	/** A finger shape as a mesh of its own, built around the line from its knuckle to its tip ({@code reach} around it). */
	static Mesh sdfFinger(Sdf shape, V base, V tip, double reach, double cell, int material) {
		V lo = v(Math.min(base.x, tip.x) - reach, Math.min(base.y, tip.y) - reach, Math.min(base.z, tip.z) - reach);
		V hi = v(Math.max(base.x, tip.x) + reach, Math.max(base.y, tip.y) + reach, Math.max(base.z, tip.z) + reach);
		Mesh m = new Mesh();
		m.append(surfaceNets(shape, lo, hi, cell), material);
		return m;
	}

	/** Parts that are only drawn during the middle finger emote (an angry face and a right arm making the gesture), or a blink. */
	static boolean isEmotePart(String name) {
		return name.startsWith("mad_") || name.equals("middle_finger") || name.startsWith("blink_");
	}

	/**
	 * Closed eyelids over ellipsoid eyes, for blinking: shells covering each whole eye, a little bigger than it.
	 *
	 * @param eyes centers of the eyes
	 */
	static Mesh blinkLids(V[] eyes, V radius, double thickness) {
		Mesh m = new Mesh();
		for (V eye : eyes) {
			m.append(eyelid(eye, radius, Math.signum(eye.x), 1.02, 0, thickness), 0);
		}
		return m;
	}

	/** Parts only drawn while the character is not mad (things the angry face replaces). */
	static boolean isCalmPart(String name) {
		return name.startsWith("calm_");
	}

	/** How far the emote raises the right arm forward, in the preview (the mod does the same in game). */
	static final double EMOTE_RAISE = Math.toRadians(-84);

	interface Painter {
		double[] paint(V p, V n, int material, double extra);
	}

	static void paint(Mesh m, Painter painter) {
		m.color.clear();
		for (int i = 0; i < m.pos.size(); i++) {
			m.color.add(painter.paint(m.pos.get(i), m.nrm.get(i), m.material.get(i), m.extra.get(i)));
		}
	}

	/**
	 * Gives the vertices that lie on {@code surface} that surface's normals. Parts cut out of one smooth shape (like a head
	 * and its jaw) then shade smoothly across the cut instead of showing a line there.
	 */
	static Mesh surfaceNormals(Mesh m, Sdf surface, double tolerance) {
		for (int i = 0; i < m.pos.size(); i++) {
			V p = m.pos.get(i);
			if (Math.abs(surface.at(p)) < tolerance) {
				m.nrm.set(i, gradient(surface, p, tolerance * 0.5).norm());
			}
		}
		return m;
	}

	/** Finishes a part: winds quads outward and colors every vertex. */
	static Mesh finish(Mesh m, Painter painter) {
		m.orientQuads();
		paint(m, painter);
		return m;
	}

	// ================================================================== Alien

	static final class Alien extends Design {
		static final double MOUTH_Y = 1.666;
		static final double MOUTH_FRONT_Z = 0.20;
		static final double CUT_SLOPE = 0.24;
		static final double JAW_BACK_Z = -0.03;
		static final V JAW_PIVOT = v(0, 1.705, 0.0);
		static final double JAW_OPEN_DEGREES = 32;
		static final V CAVITY_C = v(0, 1.675, 0.092);
		static final V CAVITY_R = v(0.082, 0.039, 0.088);
		static final double HUNCH = Math.toRadians(17);
		static final V HUNCH_CENTER = v(0, 1.02, 0);
		static final double HEAD_TILT = Math.toRadians(5);
		static final V HEAD_PIVOT = v(0, 1.56, -0.01);
		static final V BODY_PIVOT = v(0, 1.50, 0);
		static final V RIGHT_SHOULDER = v(-0.185, 1.415, -0.01);
		static final V LEFT_SHOULDER = v(0.185, 1.415, -0.01);
		static final V RIGHT_HAND = v(-0.232, 0.715, 0.088);
		static final V LEFT_HAND = v(0.232, 0.715, 0.088);
		static final V RIGHT_HIP = v(-0.072, 0.93, 0);
		static final V LEFT_HIP = v(0.072, 0.93, 0);
		static final V EYE_C = v(0.094, 1.805, 0.150);
		static final V EYE_R = v(0.074, 0.040, 0.040);
		static final double EYE_ROLL = Math.toRadians(24);
		static final double EYE_YAW = Math.toRadians(24);
		static final int SKIN = 0, EYE = 1, TEETH = 2, MOUTH = 3, CLAW = 4, LIP = 5, GLINT = 6, NAIL = 7;

		@Override
		String name() {
			return "Alien";
		}

		@Override
		double bounce() {
			return 0.03;
		}

		@Override
		V iconEye() {
			return headPose(v(0.0, 1.76, 1.05));
		}

		@Override
		V iconTarget() {
			return headPose(v(0, 1.76, 0));
		}

		@Override
		Rig rig() {
			V rightShift = hunched(RIGHT_SHOULDER).sub(RIGHT_SHOULDER);
			V leftShift = hunched(LEFT_SHOULDER).sub(LEFT_SHOULDER);
			return new Rig(JAW_OPEN_DEGREES, headPose(HEAD_PIVOT), headPose(JAW_PIVOT), hunched(BODY_PIVOT),
				RIGHT_SHOULDER.add(rightShift), RIGHT_HAND.add(rightShift), LEFT_SHOULDER.add(leftShift), LEFT_HAND.add(leftShift),
				RIGHT_HIP, LEFT_HIP, hunched(v(0, 0.95, -0.09)));
		}

		@Override
		Map<String, Mesh> build() {
			Map<String, Mesh> parts = new LinkedHashMap<>();
			parts.put("head", buildHead());
			parts.put("jaw", buildJaw());
			parts.put("body", surfaceNets(Alien::bodySdf, v(-0.24, 0.84, -0.14), v(0.24, 1.70, 0.14), 0.0135));
			for (int side = -1; side <= 1; side += 2) {
				parts.put(side < 0 ? "right_arm" : "left_arm", buildArm(side));
				this.addHand(parts, side);
			}
			parts.put("right_leg", buildLeg(-1));
			parts.put("left_leg", buildLeg(1));
			parts.put("mad_head", buildMadHead());
			parts.put("blink_head", buildBlink());
			for (Map.Entry<String, Mesh> part : parts.entrySet()) {
				String name = part.getKey();
				Mesh m = part.getValue();
				m.orientQuads();
				for (int i = 0; i < m.pos.size(); i++) {
					V p = m.pos.get(i);
					int material = m.material.get(i);
					boolean onCut = Math.abs(jawRegion(p)) < 0.009 && skull(p) < -0.002;
					if ((name.equals("head") || name.equals("jaw")) && material == SKIN && (skull(p) < -0.009 || cavity(p) < 0.008 || onCut)) {
						material = skull(p) > -0.016 && cavity(p) > 0.004 ? LIP : MOUTH;
					}
					m.color.add(shade(material, name, p, m.nrm.get(i), m.extra.get(i)));
				}
				hunch(name, m);
			}
			return parts;
		}

		static double skull(V p) {
			double d = ellipsoid(p, v(0, 1.835, -0.035), v(0.205, 0.165, 0.235));
			d = smin(d, ellipsoid(p, v(0, 1.735, 0.055), v(0.146, 0.150, 0.148)), 0.08);
			d = smin(d, ellipsoid(p, v(0, 1.622, 0.118), v(0.068, 0.062, 0.074)), 0.07);
			for (int s = -1; s <= 1; s += 2) {
				d = smin(d, ellipsoid(p, v(s * 0.112, 1.742, 0.088), v(0.05, 0.032, 0.05)), 0.035);
				d = smax(d, -eyeSdf(p.withX(p.x * s), 1.10, 0.012), 0.035);
			}
			return d;
		}

		static double cutPlane(V p) {
			double cutY = MOUTH_Y + (MOUTH_FRONT_Z - p.z) * CUT_SLOPE;
			return (p.y - cutY) / Math.sqrt(1 + CUT_SLOPE * CUT_SLOPE);
		}

		static double jawRegion(V p) {
			return Math.max(cutPlane(p), JAW_BACK_Z - p.z);
		}

		static double cavity(V p) {
			return ellipsoid(p, CAVITY_C, CAVITY_R);
		}

		static Mesh buildHead() {
			Mesh m = surfaceNormals(surfaceNets(p -> smax(smax(skull(p), -jawRegion(p), 0.012), -cavity(p), 0.012), v(-0.24, 1.55, -0.30),
				v(0.24, 2.03, 0.24), 0.0115), Alien::skull, 0.004);
			for (int s = -1; s <= 1; s += 2) {
				addEye(m, s);
				// two highlights on each big black eye
				V front = eyePoint(s, 0.0, 0.62, 1.0);
				addEllipsoid(m, front, v(0.011, 0.007, 0.004), GLINT);
				addEllipsoid(m, eyePoint(s, 0.42, -0.35, 1.0), v(0.004, 0.003, 0.002), GLINT);
			}
			addTeeth(m, true);
			return m;
		}

		static Mesh buildJaw() {
			Mesh m = surfaceNormals(surfaceNets(p -> smax(smax(skull(p), jawRegion(p), 0.012), -cavity(p), 0.012), v(-0.20, 1.53, -0.06), v(0.20, 1.76, 0.23),
				0.0105), Alien::skull, 0.004);
			addTeeth(m, false);
			return m;
		}

		static double eyeSdf(V p, double grow, double pushBack) {
			V q = rotZ(rotY(p.sub(EYE_C.add(v(0, 0, -pushBack))), -EYE_YAW), -EYE_ROLL);
			return ellipsoid(q, v(0, 0, 0), EYE_R.mul(grow));
		}

		/**
		 * A point on the surface of an eye: {@code u} along its long side (toward the outer corner), {@code w} up, as
		 * fractions of its size, pushed out by {@code out}.
		 */
		static V eyePoint(int side, double u, double w, double out) {
			double z = Math.sqrt(Math.max(0, 1 - u * u - w * w));
			V local = v(u * EYE_R.x * out, w * EYE_R.y * out, z * EYE_R.z * out + 0.002);
			V world = rotY(rotZ(local, EYE_ROLL), EYE_YAW).add(EYE_C).add(v(0, 0, -0.006));
			return side < 0 ? world.withX(-world.x) : world;
		}

		/** Closed lids over the big eyes: skin shells shaped and tilted like the eyes, a little bigger. */
		static Mesh buildBlink() {
			Mesh m = new Mesh();
			for (int side = -1; side <= 1; side += 2) {
				Mesh lid = eyelid(v(0, 0, 0), EYE_R.mul(1.0), 1, 1.02, 0, 0.16);
				for (int i = 0; i < lid.pos.size(); i++) {
					V world = rotY(rotZ(lid.pos.get(i), EYE_ROLL), EYE_YAW).add(EYE_C).add(v(0, 0, -0.006));
					V n = rotY(rotZ(lid.nrm.get(i), EYE_ROLL), EYE_YAW);
					if (side < 0) {
						world = world.withX(-world.x);
						n = n.withX(-n.x);
					}
					lid.pos.set(i, world);
					lid.nrm.set(i, n);
				}
				m.append(lid, SKIN);
			}
			return m;
		}

		static void addEye(Mesh m, int side) {
			int seg = 14;
			int rings = 8;
			int base = m.pos.size();
			for (int r = 0; r <= rings; r++) {
				double theta = Math.PI * r / rings;
				for (int s = 0; s <= seg; s++) {
					double phi = 2 * Math.PI * s / seg;
					V unit = v(Math.sin(theta) * Math.cos(phi), Math.cos(theta), Math.sin(theta) * Math.sin(phi));
					V local = v(unit.x * EYE_R.x * 0.98, unit.y * EYE_R.y * 0.98, unit.z * EYE_R.z * 0.98);
					V normal = v(unit.x / EYE_R.x, unit.y / EYE_R.y, unit.z / EYE_R.z);
					V world = rotY(rotZ(local, EYE_ROLL), EYE_YAW).add(EYE_C).add(v(0, 0, -0.006));
					V n = rotY(rotZ(normal, EYE_ROLL), EYE_YAW).norm();
					if (side < 0) {
						world = world.withX(-world.x);
						n = n.withX(-n.x);
					}
					m.add(world, n, EYE, 0);
				}
			}
			gridQuads(m, base, rings, seg);
		}

		static void addTeeth(Mesh m, boolean upper) {
			int count = upper ? 10 : 9;
			for (int i = 0; i < count; i++) {
				double t = -0.86 + 1.72 * i / (count - 1);
				double x = t * CAVITY_R.x * 0.92;
				double z = CAVITY_C.z + CAVITY_R.z * Math.sqrt(Math.max(0, 1 - t * t)) * 0.86 - (upper ? 0.0 : 0.010);
				double k = 1 - sq(x / CAVITY_R.x) - sq((z - CAVITY_C.z) / CAVITY_R.z);
				double wall = CAVITY_C.y + (upper ? 1 : -1) * CAVITY_R.y * Math.sqrt(Math.max(0, k));
				double length = (upper ? 0.044 : 0.032) * (1.0 - 0.38 * t * t);
				double radius = 0.0082 * (1.0 - 0.25 * t * t);
				V root = v(x, wall + (upper ? 0.006 : -0.006), z);
				V tip = v(x * 0.97, wall + (upper ? -length : length), z - 0.004);
				addCone(m, root, tip, radius, 5, TEETH);
			}
		}

		static double bodySdf(V p) {
			double d = ellipsoid(p, v(0, 1.275, 0.004), v(0.134, 0.165, 0.094));
			d = smin(d, roundCone(p, v(0, 1.16, 0.004), v(0, 0.995, -0.002), 0.094, 0.066), 0.05);
			d = smin(d, ellipsoid(p, v(0, 0.948, -0.006), v(0.108, 0.075, 0.077)), 0.05);
			d = smin(d, roundCone(p, v(0, 1.43, -0.016), v(0, 1.585, -0.01), 0.046, 0.038), 0.03);
			d = smin(d, roundCone(p, v(0, 1.30, -0.075), v(0, 0.99, -0.06), 0.032, 0.03), 0.04);
			for (int s = -1; s <= 1; s += 2) {
				d = smin(d, roundCone(p, v(0, 1.432, -0.012), v(s * 0.172, 1.414, -0.012), 0.05, 0.038), 0.04);
			}
			return d;
		}

		/** Angry brow ridges slanting down to a furrow between the eyes. */
		static Mesh buildMadHead() {
			Mesh m = new Mesh();
			for (int s = -1; s <= 1; s += 2) {
				// Lies on the eyes as well as the skull, so it covers their top inner corner.
				addSurfaceBrow(m, Alien::wholeBody, new double[][] {{s * 0.172, 1.858}, {s * 0.125, 1.85}, {s * 0.08, 1.832}, {s * 0.042, 1.806}, {s * 0.02, 1.79}},
					0.5, 0.024, 0.016, 0.004, SKIN);
			}
			return m;
		}

		static double armShape(V p, double s) {
			V shoulder = v(s * 0.19, 1.40, -0.01);
			V elbow = v(s * 0.215, 1.085, 0.0);
			V wrist = v(s * 0.226, 0.785, 0.045);
			double d = SkinnyAlien.sphere(p, v(s * 0.185, 1.415, -0.010), 0.042);
			d = smin(d, roundCone(p, shoulder, elbow, 0.035, 0.025), 0.03);
			d = smin(d, ellipsoid(p, SkinnyAlien.between(shoulder, elbow, 0.4).add(v(0, 0, 0.008)), v(0.03, 0.09, 0.03)), 0.03);
			d = smin(d, SkinnyAlien.sphere(p, elbow.add(v(0, 0.003, -0.012)), 0.027), 0.015); // the point of the elbow
			d = smin(d, SkinnyAlien.sphere(p, elbow.add(v(s * 0.013, 0, 0.003)), 0.018), 0.012);
			d = smin(d, roundCone(p, elbow, wrist, 0.026, 0.017), 0.02);
			d = smin(d, ellipsoid(p, SkinnyAlien.between(elbow, wrist, 0.25).add(v(0, 0, 0.004)), v(0.025, 0.075, 0.025)), 0.03);
			d = smin(d, SkinnyAlien.sphere(p, wrist.add(v(s * 0.011, 0, -0.004)), 0.011), 0.008); // wrist bones
			return smax(d, wrist.y - 0.025 - p.y, 0.01);
		}

		/** A narrow palm, with knuckles where the three long fingers and the thumb grow out (they are parts of their own). */
		static double palmShape(V p, double s) {
			V palm = v(s * 0.228, 0.745, 0.056);
			double d = ellipsoid(p, palm, v(0.016, 0.042, 0.029));
			d = smin(d, SkinnyAlien.sphere(p, v(s * 0.226, 0.785, 0.045), 0.017), 0.02);
			for (int f = -1; f <= 1; f++) {
				d = smin(d, SkinnyAlien.sphere(p, v(s * 0.228, 0.712, 0.056 + f * 0.017), 0.0085), 0.006);
			}
			return smin(d, SkinnyAlien.sphere(p, v(s * 0.222, 0.74, 0.078), 0.0078), 0.008);
		}

		static Mesh buildArm(int side) {
			double s = side;
			Mesh m = surfaceNets(p -> armShape(p, s), v(s * 0.2 - 0.075, 0.74, -0.08), v(s * 0.2 + 0.085, 1.48, 0.1), 0.0095);
			m.append(surfaceNets(p -> palmShape(p, s), v(s * 0.228 - 0.04, 0.68, 0.01), v(s * 0.228 + 0.04, 0.81, 0.11), 0.0044), SKIN);
			return m;
		}

		/**
		 * Three long knuckled fingers and a thumb, each a part of its own that curls toward the palm (which faces the body),
		 * with a dark nail on the tip. Also tells where the elbow is.
		 */
		void addHand(Map<String, Mesh> parts, int side) {
			double s = side;
			String prefix = side < 0 ? "right_" : "left_";
			V shift = hunched(side < 0 ? RIGHT_SHOULDER : LEFT_SHOULDER).sub(side < 0 ? RIGHT_SHOULDER : LEFT_SHOULDER);
			this.elbow(s, v(s * 0.215, 1.085, 0.0).add(shift), v(s * 0.226, 0.785, 0.045).add(shift));
			String[] roles = {"little", "middle", "index"};
			for (int f = -1; f <= 1; f++) {
				V base = v(s * 0.228, 0.712, 0.056 + f * 0.017);
				double length = f == 0 ? 0.165 : 0.15;
				this.addSdfFinger(parts, prefix + roles[f + 1], base, v(s * 0.03, -1, 0.1), v(0, 0, 0.12), length, 0.0075, v(-s, 0, 0), shift);
			}
			this.addSdfFinger(parts, prefix + "thumb", v(s * 0.222, 0.74, 0.078), v(-s * 0.15, -0.8, 0.55), v(0, 0.02, 0.05), 0.075, 0.0072,
				v(-s * 0.6, 0, -0.8), shift);
		}

		void addSdfFinger(Map<String, Mesh> parts, String name, V base, V dir, V curve, double length, double radius, V curl, V shift) {
			V[] tip = new V[2];
			SkinnyAlien.finger(v(0, 0, 0), base, dir, curve, length, radius, tip);
			Mesh m = sdfFinger(p -> SkinnyAlien.finger(p, base, dir, curve, length, radius, null), base, tip[0], radius + 0.012, 0.0044, SKIN);
			addCone(m, tip[0].sub(tip[1].mul(0.004)), tip[0].add(tip[1].mul(0.018)), 0.0055, 6, NAIL);
			this.addFinger(parts, name, m, new FingerSpec(base.add(shift), tip[0].add(shift), curl, false));
		}

		static double legShape(V p, double s) {
			V hip = v(s * 0.073, 0.93, 0.0);
			V knee = v(s * 0.083, 0.525, 0.028);
			V ankle = v(s * 0.086, 0.09, -0.008);
			double d = SkinnyAlien.sphere(p, hip, 0.056);
			d = smin(d, roundCone(p, hip, knee, 0.054, 0.035), 0.04);
			d = smin(d, ellipsoid(p, SkinnyAlien.between(hip, knee, 0.35).add(v(0, 0, 0.01)), v(0.045, 0.13, 0.046)), 0.04);
			d = smin(d, ellipsoid(p, knee.add(v(0, 0.004, 0.022)), v(0.027, 0.03, 0.02)), 0.014); // kneecap
			d = smin(d, SkinnyAlien.sphere(p, knee, 0.036), 0.02);
			d = smin(d, roundCone(p, knee, ankle, 0.033, 0.021), 0.03);
			d = smin(d, ellipsoid(p, SkinnyAlien.between(knee, ankle, 0.25).add(v(0, 0, -0.014)), v(0.03, 0.1, 0.03)), 0.03); // calf
			d = smin(d, SkinnyAlien.sphere(p, ankle.add(v(s * 0.017, 0.004, 0)), 0.013), 0.008); // ankle bones
			d = smin(d, SkinnyAlien.sphere(p, ankle.add(v(-s * 0.015, 0.01, 0)), 0.012), 0.008);
			return smax(d, 0.06 - p.y, 0.01);
		}

		static double footShape(V p, double s, List<V[]> tips) {
			V ankle = v(s * 0.086, 0.09, -0.008);
			double d = SkinnyAlien.sphere(p, ankle.add(v(0, -0.02, 0)), 0.024);
			d = smin(d, ellipsoid(p, v(s * 0.086, 0.042, -0.010), v(0.033, 0.040, 0.03)), 0.02);
			for (int t = -1; t <= 1; t++) {
				double dx = t * 0.015;
				V base = v(s * (0.086 + dx), 0.03, 0.03);
				d = smin(d, SkinnyAlien.sphere(p, base, 0.013), 0.01);
				V[] tip = new V[2];
				d = smin(d, SkinnyAlien.finger(p, base, v(s * dx * 3, -0.15, 1), v(0, -0.06, 0), 0.1 - Math.abs(t) * 0.012, 0.012, tip), 0.008);
				if (tips != null) {
					tips.add(tip);
				}
			}
			return smax(d, -p.y + 0.002, 0.004);
		}

		static Mesh buildLeg(int side) {
			double s = side;
			Mesh m = surfaceNets(p -> legShape(p, s), v(s * 0.08 - 0.08, 0.05, -0.08), v(s * 0.08 + 0.08, 0.99, 0.09), 0.012);
			m.append(surfaceNets(p -> footShape(p, s, null), v(s * 0.086 - 0.07, -0.002, -0.06), v(s * 0.086 + 0.07, 0.12, 0.17), 0.0056), SKIN);
			List<V[]> tips = new ArrayList<>();
			footShape(v(0, 0, 0), s, tips);
			for (V[] tip : tips) {
				addCone(m, tip[0].sub(tip[1].mul(0.005)), tip[0].add(tip[1].mul(0.02)), 0.0075, 6, NAIL);
			}
			return m;
		}

		static void addClawFinger(Mesh m, List<V> points, double baseRadius) {
			addTube(m, points, radii(baseRadius, baseRadius * 0.8, baseRadius * 0.35), 5, true, SKIN, CLAW);
		}

		static double hunchAngle(double y) {
			return HUNCH * smoothstep(HUNCH_CENTER.y, 1.52, y);
		}

		static V hunched(V p) {
			return rotX(p.sub(HUNCH_CENTER), hunchAngle(p.y)).add(HUNCH_CENTER);
		}

		static V headPose(V p) {
			V shift = hunched(HEAD_PIVOT).sub(HEAD_PIVOT);
			return rotX(p.sub(HEAD_PIVOT), HEAD_TILT).add(HEAD_PIVOT).add(shift);
		}

		/** Leans the upper body forward; the head rides along but stays looking ahead, arms (and their fingers) hang straight. */
		static void hunch(String part, Mesh m) {
			if (part.endsWith("_leg")) {
				return;
			}
			String arm = part.startsWith("right_") || part.equals("middle_finger") ? "right" : part.startsWith("left_") ? "left" : part;
			for (int i = 0; i < m.pos.size(); i++) {
				V p = m.pos.get(i);
				switch (arm) {
					case "head", "jaw", "mad_head", "blink_head" -> {
						m.pos.set(i, headPose(p));
						m.nrm.set(i, rotX(m.nrm.get(i), HEAD_TILT));
					}
					case "right" -> m.pos.set(i, p.add(hunched(RIGHT_SHOULDER).sub(RIGHT_SHOULDER)));
					case "left" -> m.pos.set(i, p.add(hunched(LEFT_SHOULDER).sub(LEFT_SHOULDER)));
					default -> {
						double angle = hunchAngle(p.y);
						m.pos.set(i, rotX(p.sub(HUNCH_CENTER), angle).add(HUNCH_CENTER));
						m.nrm.set(i, rotX(m.nrm.get(i), angle));
					}
				}
			}
		}

		static double[] shade(int material, String part, V p, V n, double extra) {
			switch (material) {
				case EYE:
					return glossyBlack(n);
				case GLINT:
					return rgb(0.95, 0.97, 1.0);
				case NAIL:
					return rgb(0.16 + 0.12 * extra, 0.15 + 0.11 * extra, 0.12 + 0.1 * extra);
				case TEETH: {
					double root = clamp01(extra);
					return rgb(0.93 - root * 0.12, 0.91 - root * 0.16, 0.82 - root * 0.22);
				}
				case LIP:
					return rgb(0.25, 0.13, 0.12);
				case MOUTH: {
					double back = clamp01((0.17 - p.z) / 0.20);
					double gum = clamp01(1 - Math.abs(cutPlane(p)) / 0.03);
					return rgb(0.62 - 0.40 * back + 0.08 * gum, 0.07 - 0.04 * back + 0.05 * gum, 0.08 - 0.04 * back + 0.05 * gum);
				}
				default: {
					double ao = occlusion(Alien::wholeBody, p, n);
					double mottle = noise(p.mul(9)) * 0.07 + noise(p.mul(37)) * 0.035;
					double r = 0.60 + mottle;
					double g = 0.585 + mottle;
					double b = 0.50 + mottle * 0.8;
					boolean face = part.equals("head") || part.equals("jaw");
					double shadeK = face ? 0.62 + 0.38 * ao : 0.42 + 0.58 * ao;
					if (face && Math.abs(cutPlane(p)) < 0.014 && p.z > 0.02) {
						double lip = Math.abs(cutPlane(p)) / 0.014;
						r = lerp(0.36, r, lip);
						g = lerp(0.20, g, lip);
						b = lerp(0.20, b, lip);
					}
					if (part.equals("body")) {
						double ribs = 0.5 + 0.5 * Math.cos((p.y - 1.135) / 0.046 * 2 * Math.PI);
						double ribMask = smoothstep(1.11, 1.15, p.y) * (1 - smoothstep(1.38, 1.42, p.y)) * smoothstep(-0.02, 0.05, p.z + Math.abs(p.x) * 0.4);
						shadeK *= 1 - 0.22 * ribs * ribMask;
					}
					if (part.equals("head")) {
						for (int s = -1; s <= 1; s += 2) {
							double dn = p.sub(v(s * 0.016, 1.716, 0.194)).len();
							if (dn < 0.012) {
								shadeK *= 0.45 + 0.55 * dn / 0.012;
							}
						}
					}
					if (material == CLAW) {
						double t = clamp01(extra);
						r = lerp(r, 0.30, t);
						g = lerp(g, 0.28, t);
						b = lerp(b, 0.24, t);
					}
					return rgb(r * shadeK, g * shadeK, b * shadeK);
				}
			}
		}

		static double wholeBody(V p) {
			double d = Math.min(skull(p), bodySdf(p));
			d = Math.min(d, Math.min(eyeSdf(p, 0.98, 0.006), eyeSdf(p.withX(-p.x), 0.98, 0.006)));
			for (int s = -1; s <= 1; s += 2) {
				d = Math.min(d, roundCone(p, v(s * 0.19, 1.40, -0.01), v(s * 0.215, 1.085, 0.0), 0.035, 0.025));
				d = Math.min(d, roundCone(p, v(s * 0.215, 1.085, 0.0), v(s * 0.226, 0.785, 0.045), 0.027, 0.017));
				d = Math.min(d, roundCone(p, v(s * 0.073, 0.93, 0.0), v(s * 0.083, 0.525, 0.028), 0.054, 0.035));
				d = Math.min(d, roundCone(p, v(s * 0.083, 0.525, 0.024), v(s * 0.086, 0.09, -0.008), 0.034, 0.021));
			}
			return d;
		}
	}

	// ================================================================== Robot

	/**
	 * A friendly metal robot: boxy head with a visor and glowing eyes, a jaw that drops open, panel seams and rivets,
	 * shoulder pads, a glowing power core and chest lights, pistons on the forearms and shins, vents on the back, jointed
	 * gripper hands and treaded feet.
	 */
	static final class Robot extends Design {
		static final V HEAD_C = v(0, 1.70, 0.0);
		static final V HEAD_H = v(0.17, 0.145, 0.15);
		static final double MOUTH_Y = 1.615;
		static final double JAW_BACK_Z = -0.07;
		static final V JAW_PIVOT = v(0, 1.66, -0.06);
		static final V CAVITY_C = v(0, 1.617, 0.045);
		static final V CAVITY_H = v(0.118, 0.032, 0.085);
		static final V EYE = v(0.058, 1.738, 0.138);
		static final V EYE_R = v(0.012, 0.026, 0.034);
		static final int METAL = 0, DARK = 1, GLOW = 2, VISOR = 3, RED = 4, YELLOW = 5, GREEN = 6, TEETH = 7, MOUTH = 8, CHROME = 9;

		@Override
		String name() {
			return "Robot";
		}

		@Override
		double bounce() {
			return 0.015;
		}

		@Override
		V iconEye() {
			return v(0.0, 1.72, 1.05);
		}

		@Override
		V iconTarget() {
			return v(0, 1.71, 0);
		}

		@Override
		Rig rig() {
			return new Rig(26, v(0, 1.52, 0), JAW_PIVOT, v(0, 1.50, 0),
				v(-0.262, 1.40, 0), v(-0.272, 0.665, 0.085), v(0.262, 1.40, 0), v(0.272, 0.665, 0.085),
				v(-0.105, 0.93, 0), v(0.105, 0.93, 0), v(0, 0.94, -0.13));
		}

		/** Cuts a seam into a surface: {@code band} is how far a point is from the seam's middle line. */
		static double groove(double d, double band, double width, double depth) {
			return smax(d, -Math.max(Math.abs(band) - width, -d - depth), 0.004);
		}

		static double head(V p) {
			double d = roundBox(p, HEAD_C, HEAD_H, 0.06);
			d = smax(d, -roundBox(p, v(0, 1.735, 0.155), v(0.135, 0.045, 0.03), 0.025), 0.01); // visor recess
			d = groove(d, p.y - 1.805, 0.006, 0.006); // seam around the top
			d = groove(d, p.x, 0.005, 0.005 * smoothstep(0.0, -0.05, p.z)); // and down the back of the head
			for (int s = -1; s <= 1; s += 2) { // ear bolts, also the jaw hinge
				d = smin(d, roundCylinderX(p, v(s * 0.175, 1.66, -0.06), 0.045, 0.02, 0.008), 0.01);
				d = smin(d, roundCylinderX(p, v(s * 0.197, 1.66, -0.06), 0.022, 0.012, 0.004), 0.006);
				d = smax(d, -roundBox(p, v(s * 0.172, 1.76, 0.03), v(0.01, 0.022, 0.05), 0.004), 0.004); // side vents
			}
			return d;
		}

		static double jawRegion(V p) {
			return Math.max(p.y - MOUTH_Y - (0.155 - p.z) * 0.10, JAW_BACK_Z - p.z);
		}

		static double cavity(V p) {
			return roundBox(p, CAVITY_C, CAVITY_H, 0.02);
		}

		static double body(V p) {
			double d = roundBox(p, v(0, 1.27, 0.0), v(0.205, 0.17, 0.125), 0.055);                 // chest
			d = smin(d, roundBox(p, v(0, 1.40, -0.01), v(0.235, 0.05, 0.11), 0.04), 0.04);         // shoulder bar
			d = smax(d, -roundBox(p, v(0, 1.28, 0.128), v(0.105, 0.075, 0.02), 0.012), 0.008);    // chest panel recess
			d = groove(d, p.y - 1.15, 0.005, 0.006);                                              // seam under the chest
			for (int s = -1; s <= 1; s += 2) {
				d = groove(d, Math.abs(p.x) - 0.15 + 0.0 * s, 0.004, 0.005 * smoothstep(1.17, 1.2, p.y)); // side panel seams
			}
			for (int k = 0; k < 4; k++) { // vents on the back
				d = smax(d, -roundBox(p, v(0, 1.33 - k * 0.04, -0.126), v(0.09, 0.008, 0.02), 0.004), 0.003);
			}
			d = Math.min(d, roundCylinderY(p, v(0, 1.07, 0), 0.10, 0.05, 0.02));                   // waist joint
			d = groove(d, p.y - 1.07, 0.006, 0.008);
			d = smin(d, roundBox(p, v(0, 0.955, 0), v(0.17, 0.07, 0.11), 0.035), 0.02);             // hips
			d = Math.min(d, roundCylinderY(p, v(0, 1.50, -0.005), 0.055, 0.055, 0.015));           // neck
			for (int k = 0; k < 3; k++) {
				d = groove(d, p.y - (1.47 + k * 0.025), 0.004, 0.006 * smoothstep(1.44, 1.46, p.y));
			}
			return d;
		}

		static double leg(V p, double s) {
			double d = roundCylinderY(p, v(s * 0.105, 0.725, 0), 0.052, 0.17, 0.02);                // thigh
			d = groove(d, p.y - 0.80, 0.005, 0.006);
			d = Math.min(d, ellipsoid(p, v(s * 0.105, 0.535, 0.012), v(0.068, 0.062, 0.068)));     // knee
			d = Math.min(d, roundBox(p, v(s * 0.105, 0.30, 0.0), v(0.063, 0.205, 0.068), 0.03));   // shin
			d = smax(d, -roundBox(p, v(s * 0.105, 0.33, 0.07), v(0.038, 0.11, 0.012), 0.008), 0.005); // shin panel
			d = Math.min(d, roundBox(p, v(s * 0.105, 0.047, 0.03), v(0.078, 0.047, 0.11), 0.03));  // foot
			d = smin(d, roundBox(p, v(s * 0.105, 0.04, 0.14), v(0.07, 0.038, 0.035), 0.025), 0.01); // toe plate
			d = groove(d, p.z - 0.105, 0.004, 0.006 * smoothstep(0.1, 0.07, p.y));
			return d;
		}

		static double arm(V p, double s) {
			V shoulder = v(s * 0.262, 1.40, 0);
			double d = ellipsoid(p, shoulder, v(0.075, 0.075, 0.075));                            // shoulder
			double pad = smax(ellipsoid(p, shoulder.add(v(s * 0.008, 0.03, 0)), v(0.095, 0.065, 0.095)), shoulder.y - 0.02 - p.y, 0.02);
			d = Math.min(d, groove(pad, p.y - (shoulder.y + 0.03), 0.004, 0.006));               // shoulder pad
			d = Math.min(d, roundCylinderY(p, v(s * 0.272, 1.215, 0), 0.042, 0.13, 0.015));         // upper arm
			d = groove(d, p.y - 1.26, 0.004, 0.005);
			d = Math.min(d, ellipsoid(p, v(s * 0.272, 1.065, 0.005), v(0.052, 0.052, 0.052)));     // elbow
			d = Math.min(d, roundBox(p, v(s * 0.272, 0.90, 0.02), v(0.055, 0.13, 0.058), 0.028)); // forearm
			d = smax(d, -roundBox(p, v(s * 0.272, 0.92, 0.078), v(0.032, 0.08, 0.01), 0.006), 0.004); // forearm panel
			d = Math.min(d, roundBox(p, v(s * 0.272, 0.725, 0.035), v(0.03, 0.045, 0.046), 0.015)); // palm
			return d;
		}

		/** A rounded bolt head on a surface. */
		static void addRivet(Mesh m, Sdf surface, V from, V direction) {
			V hit = hitSurface(surface, from, direction, 0.3);
			if (hit != null) {
				V n = gradient(surface, hit, 0.002).norm();
				addOrientedEllipsoid(m, hit, n, 0, v(0.0085, 0.0045, 0.0085), CHROME);
			}
		}

		/** A piston: a dark cylinder with a chrome rod sliding out of it. */
		static void addPiston(Mesh m, V from, V to, double radius) {
			V mid = from.add(to).mul(0.5);
			addTube(m, path(from, from.add(mid).mul(0.5), mid), radii(radius, radius, radius), 8, false, DARK, DARK);
			addTube(m, path(mid, mid.add(to).mul(0.5), to), radii(radius * 0.55, radius * 0.55, radius * 0.55), 8, false, CHROME, CHROME);
		}

		/** A gripper finger with two segments. */
		static void addGripper(Mesh m, V base, V knuckle, V tip, double radius) {
			addTube(m, path(base, base.add(knuckle).mul(0.5), knuckle), radii(radius, radius, radius * 0.95), 7, false, DARK, DARK);
			addSphere(m, knuckle, radius * 1.15, CHROME);
			addTube(m, path(knuckle, knuckle.add(tip).mul(0.5), tip), radii(radius * 0.9, radius * 0.85, radius * 0.7), 7, false, DARK, DARK);
		}

		static Mesh armMesh(double s) {
			Mesh arm = surfaceNets(p -> arm(p, s), v(s * 0.272 - 0.11, 0.66, -0.11), v(s * 0.272 + 0.11, 1.5, 0.11), 0.013);
			addPiston(arm, v(s * 0.272, 1.03, -0.05), v(s * 0.272, 0.80, -0.07), 0.013);
			for (int k = -1; k <= 1; k += 2) {
				addRivet(arm, p -> arm(p, s), v(s * (0.262 + k * 0.05), 1.55, 0.0), v(0, -1, 0));
				addRivet(arm, p -> arm(p, s), v(s * 0.262, 1.55, k * 0.05), v(0, -1, 0));
			}
			for (int f = -1; f <= 1; f++) { // the sockets the grippers turn in
				addSphere(arm, v(s * (0.272 + f * 0.019), 0.688, 0.052), 0.0118, CHROME);
			}
			addSphere(arm, v(s * 0.256, 0.708, 0.074), 0.011, CHROME);
			return arm;
		}

		/**
		 * Three jointed gripper fingers and a thumb, each a part of its own that bends sharply at its joint. The palm faces
		 * forward, so the fingers close toward the front, and the thumb toward them.
		 */
		void addHand(Map<String, Mesh> parts, double s) {
			String prefix = s < 0 ? "right_" : "left_";
			this.elbow(s, v(s * 0.272, 1.065, 0.005), v(s * 0.272, 0.775, 0.03));
			String[] roles = {"index", "middle", "little"};
			for (int f = -1; f <= 1; f++) {
				double x = s * (0.272 + f * 0.019);
				double length = f == 0 ? 1.08 : 1.0;
				V base = v(x, 0.688, 0.052);
				V knuckle = v(x + s * f * 0.002, 0.688 - 0.055 * length, 0.077);
				V tip = v(x - s * f * 0.004, 0.688 - 0.093 * length, 0.09);
				Mesh m = new Mesh();
				addGripper(m, base, knuckle, tip, 0.0098);
				this.addFinger(parts, prefix + roles[f + 1], finish(m, (p, n, mm, e) -> paintLimb(p, n, mm)),
					new FingerSpec(base, tip, v(0, 0.25, 1), true, new double[] {0, 0.58}, new double[] {60, 75}));
			}
			V base = v(s * 0.256, 0.708, 0.074);
			V tip = v(s * 0.258, 0.645, 0.118);
			Mesh thumb = new Mesh();
			addGripper(thumb, base, v(s * 0.25, 0.672, 0.107), tip, 0.0095);
			this.addFinger(parts, prefix + "thumb", finish(thumb, (p, n, mm, e) -> paintLimb(p, n, mm)),
				new FingerSpec(base, tip, v(s * 0.3, 0.2, -1), true, new double[] {0, 0.55}, new double[] {40, 55}));
		}

		static Mesh legMesh(double s) {
			Mesh leg = surfaceNets(p -> leg(p, s), v(s * 0.105 - 0.10, -0.01, -0.10), v(s * 0.105 + 0.10, 0.92, 0.2), 0.0135);
			addPiston(leg, v(s * 0.105, 0.47, -0.062), v(s * 0.105, 0.17, -0.072), 0.012);
			for (int k = 0; k < 4; k++) { // treads under the foot
				addBox(leg, v(s * 0.105, 0.004, -0.05 + k * 0.055), v(0.07, 0.006, 0.012), DARK);
			}
			for (int k = -1; k <= 1; k += 2) {
				addRivet(leg, p -> leg(p, s), v(s * (0.105 + k * 0.035), 0.42, 0.3), v(0, 0, -1));
			}
			return leg;
		}

		@Override
		Map<String, Mesh> build() {
			Map<String, Mesh> parts = new LinkedHashMap<>();
			Sdf headShape = p -> smax(smax(head(p), -jawRegion(p), 0.006), -cavity(p), 0.006);
			Mesh headMesh = surfaceNormals(surfaceNets(headShape, v(-0.24, 1.53, -0.20), v(0.24, 1.88, 0.22), 0.0115), Robot::head, 0.004);
			addEyes(headMesh);
			addAntenna(headMesh);
			addTeethRow(headMesh, true);
			for (int sx = -1; sx <= 1; sx += 2) { // rivets in the corners of the face
				for (int sy = 0; sy <= 1; sy++) {
					addRivet(headMesh, Robot::head, v(sx * 0.135, 1.665 + sy * 0.13, 0.3), v(0, 0, -1));
				}
			}
			parts.put("head", finish(headMesh, (p, n, m, e) -> paintHead(p, n, m, true)));

			Mesh jawMesh = surfaceNormals(surfaceNets(p -> smax(smax(head(p), jawRegion(p), 0.006), -cavity(p), 0.006), v(-0.20, 1.53, -0.09),
				v(0.20, 1.66, 0.19), 0.0105), Robot::head, 0.004);
			addTeethRow(jawMesh, false);
			parts.put("jaw", finish(jawMesh, (p, n, m, e) -> paintHead(p, n, m, false)));

			Mesh bodyMesh = surfaceNets(Robot::body, v(-0.27, 0.87, -0.16), v(0.27, 1.57, 0.17), 0.0135);
			int[] lights = {RED, YELLOW, GREEN, GLOW};
			for (int i = 0; i < 4; i++) {
				addEllipsoid(bodyMesh, v(-0.075 + i * 0.05, 1.325, 0.118), v(0.009, 0.013, 0.012), lights[i]);
			}
			// the power core: a glowing disc in a dark ring
			addCylinder(bodyMesh, v(0, 1.258, 0.108), v(0, 0, 1), 0.034, 0.01, 18, GLOW);
			addTorus(bodyMesh, v(0, 1.258, 0.118), v(0, 0, 1), 0.037, 0.007, 22, 6, DARK);
			for (int i = 0; i < 4; i++) { // speaker grille slots
				addBox(bodyMesh, v(0.0, 1.208 - i * 0.0, 0.0).add(v(-0.045 + i * 0.03, 0, 0.112)), v(0.006, 0.012, 0.006), DARK);
			}
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int sy = -1; sy <= 1; sy += 2) {
					addRivet(bodyMesh, Robot::body, v(sx * 0.12, 1.28 + sy * 0.088, 0.3), v(0, 0, -1));
					addRivet(bodyMesh, Robot::body, v(sx * 0.17, 1.27 + sy * 0.07, 0.3), v(0, 0, -1));
				}
			}
			parts.put("body", finish(bodyMesh, (p, n, m, e) -> paintBody(p, n, m)));

			for (int side = -1; side <= 1; side += 2) {
				double s = side;
				parts.put(side < 0 ? "right_arm" : "left_arm", finish(armMesh(s), (p, n, m, e) -> paintLimb(p, n, m)));
				this.addHand(parts, s);
				parts.put(side < 0 ? "right_leg" : "left_leg", finish(legMesh(s), (p, n, m, e) -> paintLimb(p, n, m)));
			}

			// Angry: the eyes glow red under slanted shutters.
			Mesh mad = new Mesh();
			for (int s = -1; s <= 1; s += 2) {
				addEllipsoid(mad, v(s * EYE.x, EYE.y, EYE.z + 0.0015), EYE_R.mul(1.06), RED);
				addTube(mad, path(v(s * 0.104, 1.771, 0.153), v(s * 0.06, 1.762, 0.154), v(s * 0.018, 1.745, 0.153)), radii(0.012, 0.012, 0.011), 6, false, DARK, DARK);
			}
			parts.put("mad_head", finish(mad, (p, n, m, e) -> paintMetal(p, n, m)));

			// Blinking: dark shutters slide over the eyes.
			Mesh blink = new Mesh();
			for (int s = -1; s <= 1; s += 2) {
				addEllipsoid(blink, v(s * EYE.x, EYE.y, EYE.z + 0.003), EYE_R.mul(1.12), DARK);
			}
			parts.put("blink_head", finish(blink, (p, n, m, e) -> paintMetal(p, n, m)));
			return parts;
		}

		static void addEyes(Mesh m) {
			for (int s = -1; s <= 1; s += 2) {
				V eye = EYE.withX(s * EYE.x);
				addEllipsoid(m, eye, EYE_R, GLOW);
				addTorus(m, eye.add(v(0, 0, EYE_R.z * 0.55)), v(0, 0, 1), 0.019, 0.004, 16, 5, DARK); // eye socket ring
			}
		}

		static void addAntenna(Mesh m) {
			addCylinder(m, v(0, 1.835, -0.02), v(0, 1, 0), 0.022, 0.012, 14, DARK);
			addTube(m, path(v(0, 1.83, -0.02), v(0, 1.90, -0.025), v(0, 1.97, -0.03)), radii(0.011, 0.009, 0.008), 7, false, CHROME, CHROME);
			addSphere(m, v(0, 1.985, -0.03), 0.026, RED);
		}

		/** Square metal teeth along the mouth edge. */
		static void addTeethRow(Mesh m, boolean upper) {
			for (int i = 0; i < 7; i++) {
				double x = -0.09 + i * 0.03;
				double y = upper ? CAVITY_C.y + CAVITY_H.y - 0.006 : CAVITY_C.y - CAVITY_H.y + 0.006;
				addBevelBox(m, v(x, y + (upper ? -0.013 : 0.013), 0.112), v(1, 0, 0), v(0, 1, 0), v(0, 0, 1), v(0.011, 0.014, 0.008), 0.003, TEETH, TEETH);
			}
		}

		double[] paintHead(V p, V n, int material, boolean upper) {
			if (material == METAL) {
				if (cavity(p) < 0.006 || Math.abs(jawRegion(p)) < 0.006 && head(p) < -0.003) {
					material = MOUTH;
				} else if (p.z > 0.12 && Math.abs(p.x) < 0.15 && p.y > 1.68 && p.y < 1.79) {
					material = VISOR;
				} else if (Math.abs(Math.abs(p.x) - 0.175) < 0.03 && Math.abs(p.y - 1.66) < 0.05 && Math.abs(p.z + 0.06) < 0.05) {
					material = DARK;
				}
			}
			if (material == MOUTH) {
				double back = clamp01((CAVITY_C.z - p.z) / CAVITY_H.z);
				return rgb(0.04 + 0.25 * back, 0.05 + 0.75 * back, 0.07 + 0.85 * back); // dark, glowing cyan at the back
			}
			if (material == VISOR) {
				double scan = Math.abs(p.y - 1.705) < 0.004 ? 0.25 : 0; // a faint scan line
				double shine = Math.pow(Math.max(0, n.dot(v(0.3, 0.8, 0.5).norm())), 10) * 0.35;
				return rgb(0.05 + shine, 0.07 + shine + scan, 0.10 + shine + scan);
			}
			return paintMetal(p, n, material);
		}

		double[] paintBody(V p, V n, int material) {
			if (material == METAL && p.z > 0.11 && Math.abs(p.x) < 0.12 && Math.abs(p.y - 1.28) < 0.09) {
				material = DARK;
			}
			if (material == METAL && (Math.abs(p.y - 1.07) < 0.06 || p.y > 1.44)) {
				material = DARK; // waist and neck joints
			}
			if (material == METAL && p.z < -0.11 && Math.abs(p.x) < 0.1 && p.y > 1.19 && p.y < 1.35) {
				material = DARK; // vents
			}
			return paintMetal(p, n, material);
		}

		double[] paintLimb(V p, V n, int material) {
			if (material == METAL) {
				double y = p.y;
				boolean joint = Math.abs(y - 1.065) < 0.05 || Math.abs(y - 0.535) < 0.05 || Math.abs(y - 1.40) < 0.06 && Math.abs(p.x) > 0.2 && y < 1.41;
				if (joint) {
					material = DARK;
				}
			}
			return paintMetal(p, n, material);
		}

		static double[] paintMetal(V p, V n, int material) {
			double brushed = noise(v(p.x * 60, p.y * 6, p.z * 60)) * 0.035 + noise(p.mul(14)) * 0.03;
			double rim = Math.pow(1 - Math.abs(n.y), 2) * 0.05;
			double spec = Math.pow(Math.max(0, n.dot(v(0.35, 0.8, 0.5).norm())), 18) * 0.25;
			return switch (material) {
				case DARK -> rgb(0.26 + brushed + spec * 0.5, 0.28 + brushed + spec * 0.5, 0.31 + brushed + spec * 0.5);
				case CHROME -> rgb(0.78 + spec, 0.8 + spec, 0.84 + spec);
				case GLOW -> rgb(0.45, 1.0, 1.0);
				case VISOR -> rgb(0.05, 0.07, 0.10);
				case RED -> rgb(1.0, 0.25, 0.22);
				case YELLOW -> rgb(1.0, 0.85, 0.25);
				case GREEN -> rgb(0.35, 1.0, 0.35);
				case TEETH -> rgb(0.85, 0.88, 0.9);
				default -> rgb(0.64 + brushed - rim + spec, 0.68 + brushed - rim + spec, 0.72 + brushed - rim + spec);
			};
		}
	}

	// ================================================================== Frog

	/**
	 * A cheerful upright frog with big eyes on top, a wide mouth with a tongue, warty spotted skin, ear discs behind the
	 * eyes, a pale belly, muscular legs and webbed hands and feet with round toe pads.
	 */
	static final class Frog extends Design {
		static final double MOUTH_Y = 1.235;
		static final double MOUTH_FRONT_Z = 0.22;
		static final double CUT_SLOPE = 0.20;
		static final double JAW_BACK_Z = -0.10;
		static final V JAW_PIVOT = v(0, 1.275, -0.07);
		static final V CAVITY_C = v(0, 1.238, 0.065);
		static final V CAVITY_R = v(0.185, 0.034, 0.13);
		static final V EYE_L = v(0.125, 1.405, 0.085);
		static final int SKIN = 0, WHITE = 1, PUPIL = 2, MOUTH = 3, TONGUE = 4, PAD = 5, IRIS = 6, WEB = 7, LINE = 8;

		/** The mouth: a clean line where the cut between head and jaw meets the skin, from corner to corner. */
		static void addMouthLine(Mesh m) {
			List<V> line = new ArrayList<>();
			for (int i = 0; i <= 20; i++) {
				double x = -0.2 + 0.4 * i / 20;
				double z = 0.25;
				V hit = null;
				for (int k = 0; k < 3; k++) { // the cut slopes with z, so settle the height and depth together
					double y = MOUTH_Y + (MOUTH_FRONT_Z - z) * CUT_SLOPE + 0.05 * sq(x / 0.22) + 0.003;
					hit = hitSurface(Frog::skull, v(x, y, 0.4), v(0, 0, -1), 0.6);
					if (hit == null) {
						break;
					}
					z = hit.z;
				}
				if (hit != null) {
					line.add(hit.add(v(0, 0, 0.002)));
				}
			}
			double[] r = new double[line.size()];
			for (int i = 0; i < r.length; i++) {
				r[i] = 0.0055 * (0.6 + 0.4 * Math.sin(Math.PI * i / (r.length - 1)));
			}
			addTube(m, line, r, 6, false, LINE, LINE);
		}

		/** Thin webbing in the gap between two fingers or toes, from {@code base}, reaching {@code reach} of the way to the tips. */
		static void addWeb(Mesh m, V base, V tipA, V tipB, double reach) {
			V a = base.add(tipA.sub(base).mul(reach));
			V b = base.add(tipB.sub(base).mul(reach));
			V mid = a.add(b).mul(0.5);
			V normal = a.sub(base).cross(b.sub(base)).norm();
			V center = base.add(mid).mul(0.5).add(mid.sub(base).mul(0.15));
			double length = mid.sub(base).len() * 0.62;
			double width = a.sub(b).len() * 0.5;
			V along = mid.sub(base).norm();
			// The same in-plane axes addOrientedEllipsoid uses, so the long side can point from the base toward the tips.
			V a0 = (Math.abs(normal.y) < 0.9 ? v(0, 1, 0) : v(0, 0, 1)).cross(normal).norm();
			V b0 = normal.cross(a0);
			double yaw = Math.atan2(along.dot(b0), along.dot(a0));
			addOrientedEllipsoid(m, center, normal, yaw, v(length, 0.0035, width), WEB);
		}

		@Override
		String name() {
			return "Frog";
		}

		@Override
		double bounce() {
			return 0.06;
		}

		/** Warts on the back and the top of the head (not on the belly or the face). */
		static double warts(V p) {
			double bumps = Math.max(0, noise(p.mul(38)) - 0.35) / 0.65;
			return 0.0055 * bumps * smoothstep(0.08, -0.02, p.z + 0.25 * Math.max(0, p.y - 1.2));
		}

		@Override
		V iconEye() {
			return v(0.0, 1.33, 1.0);
		}

		@Override
		V iconTarget() {
			return v(0, 1.31, 0);
		}

		@Override
		double iconFov() {
			return 0.62;
		}

		@Override
		Rig rig() {
			return new Rig(30, v(0, 1.14, 0), JAW_PIVOT, v(0, 1.14, 0),
				v(-0.17, 1.03, 0.0), v(-0.215, 0.70, 0.13), v(0.17, 1.03, 0.0), v(0.215, 0.70, 0.13),
				v(-0.10, 0.66, -0.02), v(0.10, 0.66, -0.02), v(0, 0.66, -0.16));
		}

		static double skull(V p) {
			double d = ellipsoid(p, v(0, 1.255, 0.03), v(0.235, 0.115, 0.20));
			d = smin(d, ellipsoid(p, v(0, 1.315, -0.01), v(0.215, 0.10, 0.18)), 0.06);
			for (int s = -1; s <= 1; s += 2) {
				d = smin(d, ellipsoid(p, EYE_L.withX(s * EYE_L.x).add(v(0, -0.01, -0.012)), v(0.078, 0.07, 0.072)), 0.05);
				d = smax(d, -ellipsoid(p, v(s * 0.212, 1.3, -0.04), v(0.012, 0.03, 0.03)), 0.01); // ear discs, a little sunken
				d = smax(d, -ellipsoid(p, v(s * 0.034, 1.322, 0.205), v(0.007, 0.005, 0.008)), 0.004); // nostrils
			}
			return d - warts(p);
		}

		static double cutPlane(V p) {
			double curve = 0.05 * sq(p.x / 0.22); // corners of the mouth curl up into a smile
			double cutY = MOUTH_Y + (MOUTH_FRONT_Z - p.z) * CUT_SLOPE + curve;
			return (p.y - cutY) / Math.sqrt(1 + CUT_SLOPE * CUT_SLOPE);
		}

		static double jawRegion(V p) {
			return Math.max(cutPlane(p), JAW_BACK_Z - p.z);
		}

		static double cavity(V p) {
			return ellipsoid(p, CAVITY_C, CAVITY_R);
		}

		static double body(V p) {
			double d = ellipsoid(p, v(0, 0.82, 0.02), v(0.205, 0.235, 0.17));
			d = smin(d, ellipsoid(p, v(0, 1.05, 0.0), v(0.165, 0.15, 0.135)), 0.08);
			d = smin(d, ellipsoid(p, v(0, 0.69, -0.025), v(0.18, 0.11, 0.15)), 0.06);
			// Top under the head. It slopes down toward the front, so the chest doesn't poke into the mouth when the
			// jaw opens.
			double top = 1.14 - 0.45 * Math.max(0, p.z);
			return smax(d, p.y - top, 0.04) - warts(p);
		}

		/** A muscular frog leg: big thigh, knee, shin and ankle, bent like a frog's. */
		static double legShape(V p, double s) {
			V hip = v(s * 0.115, 0.62, 0.0);
			V knee = v(s * 0.165, 0.40, 0.15);
			V ankle = v(s * 0.13, 0.08, -0.02);
			double d = ellipsoid(p, hip, v(0.10, 0.09, 0.085));
			d = smin(d, roundCone(p, hip.add(v(0, 0, 0.02)), knee, 0.088, 0.056), 0.04);
			d = smin(d, ellipsoid(p, SkinnyAlien.between(hip, knee, 0.45).add(v(s * 0.01, 0, -0.01)), v(0.08, 0.1, 0.075)), 0.05); // thigh muscle
			d = smin(d, ellipsoid(p, knee.add(v(0, 0.005, 0.02)), v(0.05, 0.05, 0.045)), 0.03); // knee
			d = smin(d, roundCone(p, knee, ankle, 0.054, 0.032), 0.035);
			d = smin(d, ellipsoid(p, SkinnyAlien.between(knee, ankle, 0.35).add(v(0, 0, -0.02)), v(0.045, 0.085, 0.045)), 0.04); // calf
			d = smin(d, SkinnyAlien.sphere(p, ankle, 0.034), 0.02);
			return d - warts(p) * 0.6;
		}


		@Override
		Map<String, Mesh> build() {
			Map<String, Mesh> parts = new LinkedHashMap<>();
			Mesh head = surfaceNormals(surfaceNets(p -> smax(smax(skull(p), -jawRegion(p), 0.01), -cavity(p), 0.01), v(-0.27, 1.12, -0.22),
				v(0.27, 1.50, 0.25), 0.0115), Frog::skull, 0.004);
			V[] eyes = new V[2];
			for (int s = -1; s <= 1; s += 2) {
				V eye = EYE_L.withX(s * EYE_L.x);
				V ball = eye.add(v(0, 0.004, 0.01));
				eyes[(s + 1) / 2] = ball;
				addEllipsoid(head, ball, v(0.058, 0.056, 0.058), WHITE);
				V look = v(s * 0.08, 0.05, 1);
				addEllipsoidCap(head, ball, v(0.058, 0.056, 0.058), look, 30, 1.004, IRIS);
				addEllipsoidCap(head, ball, v(0.058, 0.056, 0.058), look, 15, 1.008, PUPIL);
				addEllipsoid(head, ball.add(v(s * 0.004 - 0.012, 0.022, 0.05)), v(0.007, 0.008, 0.004), WHITE);
			}
			addMouthLine(head);
			parts.put("head", finish(head, this::paintHead));

			Mesh jaw = surfaceNormals(surfaceNets(p -> smax(smax(skull(p), jawRegion(p), 0.01), -cavity(p), 0.01), v(-0.25, 1.12, -0.13), v(0.25, 1.30, 0.25),
				0.0118), Frog::skull, 0.004);
			addEllipsoid(jaw, v(0, 1.212, 0.07), v(0.085, 0.016, 0.075), TONGUE);
			parts.put("jaw", finish(jaw, this::paintHead));

			parts.put("body", finish(surfaceNets(Frog::body, v(-0.24, 0.56, -0.20), v(0.24, 1.25, 0.21), 0.0135), this::paintBody));

			for (int side = -1; side <= 1; side += 2) {
				double s = side;
				Mesh arm = new Mesh();
				addSphere(arm, v(s * 0.17, 1.03, 0.0), 0.045, SKIN);
				addTube(arm, path(v(s * 0.17, 1.03, 0.0), v(s * 0.205, 0.93, 0.03), v(s * 0.222, 0.86, 0.05)), radii(0.038, 0.033, 0.03), 9, false, SKIN, SKIN);
				addSphere(arm, v(s * 0.222, 0.86, 0.05), 0.03, SKIN);
				addTube(arm, path(v(s * 0.222, 0.86, 0.05), v(s * 0.218, 0.78, 0.08), v(s * 0.212, 0.72, 0.105)), radii(0.03, 0.027, 0.024), 9, false, SKIN, SKIN);
				addEllipsoid(arm, v(s * 0.212, 0.705, 0.115), v(0.035, 0.03, 0.03), SKIN);
				parts.put(side < 0 ? "right_arm" : "left_arm", finish(arm, this::paintLimb));
				// Four fingers with round pads, each a part of its own (the web next to it goes with it), curling back under the hand.
				this.elbow(s, v(s * 0.222, 0.86, 0.05), v(s * 0.212, 0.72, 0.105));
				String[] roles = {"thumb", "index", "middle", "little"};
				V[] tips = new V[4];
				for (int f = 0; f < 4; f++) {
					double a = Math.toRadians(-50 + f * 33);
					tips[f] = v(s * (0.212 + Math.sin(a) * 0.045), 0.645, 0.13 + Math.cos(a) * 0.035);
				}
				V root = v(s * 0.212, 0.70, 0.12);
				for (int f = 0; f < 4; f++) {
					double a = Math.toRadians(-50 + f * 33);
					V knuckle = v(s * (0.212 + Math.sin(a) * 0.028), 0.672, 0.125 + Math.cos(a) * 0.02);
					Mesh finger = new Mesh();
					addTube(finger, path(root, knuckle, tips[f]), radii(0.011, 0.009, 0.008), 7, false, SKIN, SKIN);
					addSphere(finger, knuckle, 0.0105, SKIN);
					addSphere(finger, tips[f], 0.014, PAD);
					if (f > 0) {
						addWeb(finger, v(s * 0.212, 0.69, 0.122), tips[f - 1], tips[f], 0.6);
					}
					this.addFinger(parts, (side < 0 ? "right_" : "left_") + roles[f], finish(finger, this::paintLimb),
						new FingerSpec(root, tips[f], v(0, 0.25, -1), false, new double[] {0, 0.5}, new double[] {70, 80}));
				}

				Mesh leg = surfaceNets(p -> legShape(p, s), v(s * 0.14 - 0.13, 0.03, -0.11), v(s * 0.14 + 0.13, 0.72, 0.22), 0.012);
				addEllipsoid(leg, v(s * 0.135, 0.028, 0.06), v(0.075, 0.024, 0.065), SKIN);
				V[] toes = new V[4];
				for (int t = 0; t < 4; t++) {
					double a = Math.toRadians(-36 + t * 24);
					V tip = v(s * (0.135 + Math.sin(a) * 0.1), 0.016, 0.08 + Math.cos(a) * 0.13);
					V knuckle = v(s * (0.135 + Math.sin(a) * 0.055), 0.022, 0.09 + Math.cos(a) * 0.075);
					addTube(leg, path(v(s * 0.135, 0.03, 0.09), knuckle, tip), radii(0.015, 0.012, 0.01), 7, false, SKIN, SKIN);
					addSphere(leg, knuckle, 0.0135, SKIN);
					addSphere(leg, tip, 0.019, PAD);
					toes[t] = tip;
				}
				for (int t = 0; t < 3; t++) { // webbing between the toes
					addWeb(leg, v(s * 0.135, 0.026, 0.09), toes[t], toes[t + 1], 0.75);
				}
				parts.put(side < 0 ? "right_leg" : "left_leg", finish(leg, this::paintLimb));
			}

			// Angry: heavy lids slanting down over the eyes.
			Mesh mad = new Mesh();
			for (int s = -1; s <= 1; s += 2) {
				V eye = EYE_L.withX(s * EYE_L.x).add(v(0, 0.004, 0.01));
				mad.append(eyelid(eye, v(0.058, 0.056, 0.058), s, 0.12, 30, 0.16), SKIN);
			}
			parts.put("mad_head", finish(mad, this::paintHead));
			parts.put("blink_head", finish(blinkLids(eyes, v(0.058, 0.056, 0.058), 0.12), (p, n, m, e) -> green(p, n, 1)));
			return parts;
		}

		static double[] green(V p, V n, double ao) {
			double spots = smoothstep(0.35, 0.5, noise(p.mul(11)) * 0.5 + 0.5) * smoothstep(-0.05, 0.05, -p.z + 0.02);
			double mottle = noise(p.mul(40)) * 0.03;
			double r = lerp(0.34, 0.20, spots) + mottle;
			double g = lerp(0.66, 0.45, spots) + mottle;
			double b = lerp(0.22, 0.14, spots) + mottle;
			double k = 0.6 + 0.4 * ao;
			return rgb(r * k, g * k, b * k);
		}

		double[] paintHead(V p, V n, int material, double extra) {
			switch (material) {
				case WHITE:
					return rgb(0.96, 0.96, 0.93);
				case IRIS:
					return rgb(0.85, 0.66, 0.16);
				case PUPIL:
					return glossyBlack(n);
				case TONGUE:
					return rgb(0.95, 0.45, 0.55);
				default:
					break;
			}
			if (material == LINE) {
				return rgb(0.12, 0.24, 0.08);
			}
			boolean inside = skull(p) < -0.008 || cavity(p) < 0.008 || Math.abs(jawRegion(p)) < 0.008 && skull(p) < -0.006;
			if (inside) {
				double back = clamp01((0.18 - p.z) / 0.25);
				return rgb(0.78 - 0.35 * back, 0.30 - 0.15 * back, 0.36 - 0.15 * back);
			}
			double[] c = green(p, n, 1);
			for (int s = -1; s <= 1; s += 2) {
				double ear = p.sub(v(s * 0.212, 1.3, -0.04)).len();
				if (ear < 0.034) { // the ear disc: a darker ring around a lighter middle
					c = mix(c, ear < 0.024 ? rgb(0.52, 0.62, 0.3) : rgb(0.18, 0.32, 0.1), 0.85);
				}
				double blush = 1 - smoothstep(0.0, 0.05, p.sub(v(s * 0.175, 1.255, 0.14)).len());
				c = mix(c, rgb(0.92, 0.55, 0.58), blush * 0.85);
				double nostril = p.sub(v(s * 0.034, 1.322, 0.205)).len();
				if (nostril < 0.008) {
					c = mix(c, rgb(0.1, 0.18, 0.06), 1 - nostril / 0.008);
				}
			}
			if (p.y < MOUTH_Y + 0.01 && p.z > 0.05) { // light chin
				c = mix(c, rgb(0.86, 0.90, 0.62), smoothstep(0.0, 0.08, p.z - 0.05) * 0.7);
			}
			return c;
		}

		double[] paintBody(V p, V n, int material, double extra) {
			double[] c = green(p, n, 1);
			double belly = smoothstep(0.0, 0.06, p.z - 0.02) * (1 - smoothstep(0.10, 0.16, Math.abs(p.x))) * (1 - smoothstep(1.06, 1.14, p.y));
			return mix(c, rgb(0.88, 0.92, 0.64), belly);
		}

		double[] paintLimb(V p, V n, int material, double extra) {
			if (material == PAD) {
				return rgb(0.80, 0.86, 0.55);
			}
			if (material == WEB) {
				return rgb(0.45, 0.7, 0.32);
			}
			return green(p, n, 1);
		}
	}

	// ================================================================== Burger

	/**
	 * A burger with arms and legs: bottom bun, a charred patty, dripping cheese, pickles, frilly lettuce, tomato slices and
	 * a shiny sesame top bun with big eyes. The top bun is the "jaw": it lifts open (upward) while he talks, showing teeth
	 * and a tongue.
	 */
	static final class Burger extends Design {
		static final double TOP_BASE_Y = 0.905;
		static final V HINGE = v(0, TOP_BASE_Y, -0.36);
		static final int BUN = 0, BUN_INSIDE = 1, PATTY = 2, CHEESE = 3, LETTUCE = 4, TOMATO = 5, SEED = 6, WHITE = 7, PUPIL = 8,
			BROW = 9, TEETH = 10, TONGUE = 11, LIMB = 12, GLOVE = 13, SHOE = 14, SOLE = 15, PICKLE = 16, LACE = 17;
		static final double[][] TOMATOES = {{-0.22, 0.17}, {0.21, 0.19}, {0.02, -0.25}};
		static final double[][] PICKLES = {{-0.31, -0.12}, {0.3, -0.08}, {0.0, 0.33}};

		@Override
		String name() {
			return "Burger";
		}

		@Override
		double height() {
			return 1.35;
		}

		@Override
		double bounce() {
			return 0.05;
		}

		@Override
		V iconEye() {
			return v(0.0, 1.0, 1.35);
		}

		@Override
		V iconTarget() {
			return v(0, 0.97, 0);
		}

		@Override
		double iconFov() {
			return 1.0;
		}

		@Override
		Rig rig() {
			return new Rig(-38, v(0, TOP_BASE_Y, 0), HINGE, v(0, 0.86, 0),
				v(-0.42, 0.78, 0), v(-0.53, 0.43, 0.10), v(0.42, 0.78, 0), v(0.53, 0.43, 0.10),
				v(-0.12, 0.62, 0), v(0.12, 0.62, 0), v(0, 0.64, -0.38));
		}

		static double bottomBun(V p) {
			return roundCylinderY(p, v(0, 0.665, 0), 0.40, 0.065, 0.06);
		}

		static double patty(V p) {
			double bumps = noise(p.mul(22)) * 0.006 + noise(p.mul(55)) * 0.003;
			return roundCylinderY(p, v(0, 0.775, 0), 0.425, 0.05, 0.04) + bumps;
		}

		static double cheese(V p) {
			V q = rotY(p, Math.toRadians(45));
			double r = Math.max(Math.abs(q.x), Math.abs(q.z));
			double droop = 0.6 * sq(Math.max(0, r - 0.30));
			double d = roundBox(q.add(v(0, droop, 0)), v(0, 0.835, 0), v(0.40, 0.013, 0.40), 0.008);
			for (int k = 0; k < 4; k++) { // melted drips hanging from the corners
				double a = Math.toRadians(90 * k);
				V corner = v(Math.sin(a) * 0.53, 0.795, Math.cos(a) * 0.53);
				d = smin(d, roundCone(p, corner, corner.add(v(0, -0.05 - 0.012 * k, 0)), 0.024, 0.013), 0.02);
			}
			return d;
		}

		static double pickles(V p) {
			double d = 1e9;
			for (double[] c : PICKLES) {
				d = Math.min(d, roundCylinderY(p, v(c[0], 0.849, c[1]), 0.075, 0.006, 0.005));
			}
			return d;
		}

		static double lettuce(V p) {
			double angle = Math.atan2(p.z, p.x);
			double radial = Math.sqrt(p.x * p.x + p.z * p.z);
			double wave = (Math.sin(angle * 19) * 0.012 + Math.sin(angle * 7 + 2) * 0.006) * smoothstep(0.3, 0.45, radial);
			double edge = 0.45 + Math.sin(angle * 9 + 1) * 0.016 + Math.sin(angle * 23) * 0.006;
			V q = v(p.x, p.y - wave, p.z);
			double flat = Math.max(Math.abs(q.y - 0.860) - 0.012, radial - edge);
			return flat - 0.004;
		}

		static double tomato(V p) {
			double d = 1e9;
			for (double[] s : TOMATOES) {
				d = Math.min(d, roundCylinderY(p, v(s[0], 0.876, s[1]), 0.15, 0.012, 0.008));
			}
			return d;
		}

		static double stack(V p) {
			return Math.min(Math.min(Math.min(bottomBun(p), patty(p)), Math.min(cheese(p), lettuce(p))), Math.min(tomato(p), pickles(p)));
		}

		/** Which layer of the stack a point belongs to. */
		static int layerAt(V p) {
			double[] d = {bottomBun(p), patty(p), cheese(p), lettuce(p), tomato(p), pickles(p)};
			int[] layers = {BUN, PATTY, CHEESE, LETTUCE, TOMATO, PICKLE};
			int best = 0;
			for (int i = 1; i < d.length; i++) {
				if (d[i] < d[best]) {
					best = i;
				}
			}
			return layers[best];
		}

		static double topBun(V p) {
			double dome = ellipsoid(p, v(0, TOP_BASE_Y - 0.02, 0), v(0.415, 0.345, 0.415));
			return smax(dome, TOP_BASE_Y - p.y, 0.05);
		}

		@Override
		Map<String, Mesh> build() {
			Map<String, Mesh> parts = new LinkedHashMap<>();

			// Body: everything below the top bun, as one surface (no hidden faces between the layers).
			Mesh body = surfaceNets(Burger::stack, v(-0.58, 0.58, -0.58), v(0.58, 0.91, 0.58), 0.0158);
			addEllipsoid(body, v(0, 0.884, 0.20), v(0.11, 0.012, 0.09), TONGUE);
			parts.put("body", finish(body, (p, n, m, e) -> paint(p, n, m == TONGUE ? TONGUE : layerAt(p), e)));

			// Jaw: the top bun with eyes, eyebrows, seeds and teeth.
			Mesh top = new Mesh();
			top.append(surfaceNets(Burger::topBun, v(-0.44, TOP_BASE_Y - 0.03, -0.44), v(0.44, 1.26, 0.44), 0.0185), BUN);
			Mesh calm = new Mesh();
			Mesh mad = new Mesh();
			for (int s = -1; s <= 1; s += 2) {
				V eye = v(s * 0.115, 1.07, 0.325);
				addEllipsoid(top, eye, v(0.05, 0.088, 0.075), WHITE);
				addEllipsoid(top, eye.add(v(s * -0.01, -0.012, 0.042)), v(0.015, 0.04, 0.034), PUPIL);
				addEllipsoid(top, eye.add(v(s * -0.018, 0.002, 0.052)), v(0.005, 0.011, 0.009), WHITE);
				addTube(calm, path(eye.add(v(s * 0.065, 0.10, -0.03)), eye.add(v(s * 0.0, 0.125, 0.0)), eye.add(v(s * -0.065, 0.11, -0.02))),
					radii(0.009, 0.012, 0.009), 6, false, BROW, BROW);
				// Angry: bun-colored lids over the eyes, and thick brows sloping down to the middle.
				mad.append(eyelid(eye, v(0.05, 0.088, 0.075), s, 0.05, 28, 0.13), BUN);
				addTube(mad, path(eye.add(v(s * 0.075, 0.105, 0.0)), eye.add(v(s * 0.02, 0.075, 0.035)), eye.add(v(s * -0.06, 0.035, 0.03))),
					radii(0.016, 0.018, 0.014), 7, false, BROW, BROW);
			}
			addSeeds(top);
			for (int i = 0; i < 9; i++) {
				double a = Math.toRadians(-48 + i * 12);
				V root = v(Math.sin(a) * 0.36, TOP_BASE_Y + 0.004, Math.cos(a) * 0.36);
				addCone(top, root, root.add(v(0, -0.035, 0.004)), 0.022, 6, TEETH);
			}
			parts.put("jaw", finish(top, this::paint));
			parts.put("calm_jaw", finish(calm, this::paint));
			V[] eyes = {v(-0.115, 1.07, 0.325), v(0.115, 1.07, 0.325)};
			parts.put("blink_jaw", finish(blinkLids(eyes, v(0.05, 0.088, 0.075), 0.1), (p, n, m, e) -> lidColor(n)));
			parts.put("mad_jaw", finish(mad, (p, n, m, e) -> m == BUN ? lidColor(n) : paint(p, n, m, e)));

			for (int side = -1; side <= 1; side += 2) {
				double s = side;
				Mesh arm = new Mesh();
				addTube(arm, path(v(s * 0.40, 0.785, 0), v(s * 0.49, 0.66, 0.03), v(s * 0.525, 0.50, 0.075)), radii(0.026, 0.024, 0.022), 9, false, LIMB, LIMB);
				addSphere(arm, v(s * 0.474, 0.672, 0.025), 0.029, LIMB); // elbow
				addTorusY(arm, v(s * 0.527, 0.50, 0.078), 0.034, 0.012, GLOVE); // cuff
				addTorusY(arm, v(s * 0.527, 0.485, 0.08), 0.031, 0.008, GLOVE);
				V palm = v(s * 0.53, 0.445, 0.095);
				addEllipsoid(arm, palm, v(0.035, 0.05, 0.045), GLOVE);
				parts.put(side < 0 ? "right_arm" : "left_arm", finish(arm, this::paint));
				// A cartoon glove: three fat fingers and a thumb, each a part of its own, curling toward the palm (the body side).
				String prefix = side < 0 ? "right_" : "left_";
				this.elbow(s, v(s * 0.474, 0.672, 0.025), v(s * 0.527, 0.495, 0.079));
				String[] roles = {"little", "middle", "index"};
				for (int f = 0; f < 3; f++) {
					double z = 0.095 + (f - 1) * 0.024;
					V base = v(s * 0.53, 0.425, z);
					V tip = v(s * 0.53, 0.362, z + 0.013);
					Mesh finger = new Mesh();
					addTube(finger, path(base, v(s * 0.535, 0.39, z + 0.008), v(s * 0.53, 0.37, z + 0.012)), radii(0.015, 0.014, 0.013), 8, false, GLOVE, GLOVE);
					addSphere(finger, v(s * 0.535, 0.393, z + 0.008), 0.0155, GLOVE); // knuckle
					this.addFinger(parts, prefix + roles[f], finish(finger, this::paint), new FingerSpec(base, tip, v(-s, 0, 0), false));
				}
				V thumbBase = v(s * 0.495, 0.452, 0.112);
				Mesh thumb = new Mesh();
				addTube(thumb, path(thumbBase, v(s * 0.475, 0.43, 0.13), v(s * 0.465, 0.415, 0.135)), radii(0.014, 0.013, 0.012), 7, false, GLOVE, GLOVE);
				this.addFinger(parts, prefix + "thumb", finish(thumb, this::paint), new FingerSpec(thumbBase, v(s * 0.462, 0.41, 0.137), v(-s * 0.5, 0, -1), false));

				Mesh leg = new Mesh();
				addTube(leg, path(v(s * 0.12, 0.62, 0), v(s * 0.13, 0.36, 0.02), v(s * 0.125, 0.11, 0.0)), radii(0.028, 0.026, 0.024), 8, false, LIMB, LIMB);
				leg.append(surfaceNets(p -> shoe(p, s), v(s * 0.125 - 0.10, -0.01, -0.12), v(s * 0.125 + 0.10, 0.17, 0.22), 0.0125), SHOE);
				addSphere(leg, v(s * 0.13, 0.36, 0.02), 0.03, LIMB); // knee
				for (int k = 0; k < 3; k++) { // laces
					double z = 0.07 + k * 0.03;
					double y = 0.118 - k * 0.012;
					addTube(leg, path(v(s * 0.125 - 0.032, y, z), v(s * 0.125, y + 0.006, z + 0.004), v(s * 0.125 + 0.032, y, z)), radii(0.004, 0.004, 0.004), 5, false,
						LACE, LACE);
				}
				parts.put(side < 0 ? "right_leg" : "left_leg", finish(leg, this::paint));
			}
			return parts;
		}

		/** The lids are the same toasted bun as around them. */
		static double[] lidColor(V n) {
			double toast = smoothstep(0.0, 1.0, Math.max(0, n.y)) * 0.12;
			return rgb(0.86 - toast, 0.52 - toast * 1.2, 0.18 - toast * 0.6);
		}

		static double shoe(V p, double s) {
			double d = ellipsoid(p, v(s * 0.125, 0.065, 0.05), v(0.085, 0.07, 0.14));
			d = smax(d, -p.y + 0.005, 0.02);
			d = smin(d, ellipsoid(p, v(s * 0.125, 0.11, -0.005), v(0.05, 0.05, 0.06)), 0.03);
			return d;
		}

		static void addSeeds(Mesh m) {
			java.util.Random rnd = new java.util.Random(5);
			int placed = 0;
			while (placed < 58) {
				double a = rnd.nextDouble() * Math.PI * 2;
				double r = Math.sqrt(rnd.nextDouble()) * 0.36;
				double x = Math.cos(a) * r;
				double z = Math.sin(a) * r;
				if (z > 0.17 && Math.abs(x) < 0.25) {
					continue; // keep the face clear
				}
				double k = 1 - sq(x / 0.415) - sq(z / 0.415);
				if (k <= 0.05) {
					continue;
				}
				double y = TOP_BASE_Y - 0.02 + 0.345 * Math.sqrt(k);
				V normal = v(x / sq(0.415), (y - TOP_BASE_Y + 0.02) / sq(0.345), z / sq(0.415)).norm();
				V c = v(x, y, z).add(normal.mul(0.004));
				double yaw = rnd.nextDouble() * Math.PI;
				addOrientedEllipsoid(m, c, normal, yaw, v(0.017, 0.007, 0.009), SEED);
				placed++;
			}
		}

		double[] paint(V p, V n, int material, double extra) {
			double grain = noise(p.mul(30)) * 0.03;
			switch (material) {
				case BUN: {
					if (p.y < TOP_BASE_Y + 0.012 && p.y > 0.86 || p.y > 0.72 && p.y < 0.75 && Math.abs(n.y) > 0.5) {
						return rgb(0.97 + grain, 0.86 + grain, 0.62 + grain); // cut side
					}
					double toast = smoothstep(0.0, 1.0, Math.max(0, n.y)) * 0.12;
					double shine = Math.pow(Math.max(0, n.dot(v(0.3, 0.85, 0.45).norm())), 16) * 0.3;
					return rgb(0.86 - toast + grain + shine, 0.52 - toast * 1.2 + grain + shine, 0.18 - toast * 0.6 + grain * 0.5 + shine * 0.7);
				}
				case PATTY: {
					double grill = 0.5 + 0.5 * Math.sin((p.x + p.z) * 60);
					double k = Math.abs(n.y) > 0.6 ? 0.85 + 0.15 * grill : 1.0;
					return rgb((0.40 + grain) * k, (0.22 + grain) * k, (0.12 + grain * 0.5) * k);
				}
				case CHEESE:
					return rgb(0.99, 0.74 + grain, 0.16);
				case LETTUCE: {
					double vein = Math.pow(Math.abs(Math.sin(Math.atan2(p.z, p.x) * 13)), 8) * 0.12;
					return rgb(0.40 + vein, 0.76 + grain + vein, 0.24 + vein);
				}
				case TOMATO: {
					// slices: a darker skin edge, lighter flesh, and seeds in a ring
					double ring = 1e9;
					for (double[] c : TOMATOES) {
						ring = Math.min(ring, Math.sqrt(sq(p.x - c[0]) + sq(p.z - c[1])));
					}
					if (ring > 0.135) {
						return rgb(0.72, 0.1, 0.08);
					}
					if (Math.abs(ring - 0.08) < 0.012 && noise(p.mul(160)) > 0.1) {
						return rgb(0.98, 0.82, 0.45);
					}
					return rgb(0.9 + grain, 0.2 + grain, 0.14);
				}
				case PICKLE: {
					double ridge = 0.5 + 0.5 * Math.sin(Math.atan2(p.z, p.x) * 40);
					return rgb(0.42 + 0.08 * ridge, 0.58 + 0.08 * ridge, 0.16);
				}
				case LACE:
					return rgb(0.97, 0.97, 0.97);
				case SEED:
					return rgb(0.99, 0.95, 0.80);
				case WHITE:
					return rgb(0.98, 0.98, 0.97);
				case PUPIL:
					return glossyBlack(n);
				case BROW:
					return rgb(0.30, 0.16, 0.07);
				case TEETH:
					return rgb(0.97, 0.96, 0.92);
				case TONGUE:
					return rgb(0.94, 0.42, 0.50);
				case LIMB:
					return rgb(0.93 + grain, 0.74 + grain, 0.48 + grain);
				case GLOVE:
					return rgb(0.97, 0.97, 0.97);
				case SHOE:
					return p.y < 0.03 ? rgb(0.97, 0.97, 0.95) : rgb(0.85 + grain, 0.13, 0.12);
				default:
					return rgb(1, 0, 1);
			}
		}
	}



	// ================================================================== Penguin

	/**
	 * A tall, sleek cartoon penguin like the ones in the Madagascar movies, smoking: glossy black back and head, white
	 * front up to the eyes, heavy-lidded eyes under a black brow, a long salmon-orange beak with a cigarette in its
	 * corner (smoke rises from it in game), long thin flippers and orange webbed feet. Its lower beak opens when it
	 * talks, and it waddles from side to side while it walks.
	 */
	static final class Penguin extends Design {
		static final double NECK_Y = 1.12;
		static final V HEAD_PIVOT = v(0, NECK_Y, 0.0);
		static final V EYE = v(0.074, 1.345, 0.157);
		static final V EYE_R = v(0.047, 0.056, 0.032);
		static final double MOUTH_Y = 1.262;
		static final V JAW_PIVOT = v(0, MOUTH_Y, 0.15);
		static final V CIGARETTE = v(0.03, 1.259, 0.208);
		static final V CIGARETTE_DIR = v(0.5, -0.3, 0.81).norm();
		static final double CIGARETTE_LENGTH = 0.15;
		static final int FEATHER = 0, WHITE = 1, BEAK = 2, BEAK_LOW = 3, EYE_WHITE = 4, PUPIL = 5, IRIS = 6, FOOT = 7, CLAW = 8, TONGUE = 9, BLACK = 10,
			FILTER = 11, PAPER = 12, EMBER = 13, ASH = 14, EMBER_CORE = 15;

		@Override
		String name() {
			return "Penguin";
		}

		@Override
		double height() {
			return 1.6;
		}

		@Override
		double waddle() {
			return 8;
		}

		@Override
		double bounce() {
			return 0.02;
		}

		@Override
		double headTurn() {
			return 0.8;
		}

		@Override
		V smoke() {
			return CIGARETTE.add(CIGARETTE_DIR.mul(CIGARETTE_LENGTH + 0.008));
		}

		@Override
		V iconEye() {
			return v(0.25, 1.36, 1.0);
		}

		@Override
		V iconTarget() {
			return v(0.02, 1.3, 0);
		}

		@Override
		double iconFov() {
			return 0.85;
		}

		@Override
		Rig rig() {
			return new Rig(24, HEAD_PIVOT, JAW_PIVOT, v(0, 1.0, 0),
				v(-0.235, 1.03, -0.01), v(-0.3, 0.52, 0.04), v(0.235, 1.03, -0.01), v(0.3, 0.52, 0.04),
				v(-0.1, 0.17, 0.0), v(0.1, 0.17, 0.0), v(0, 0.3, -0.29));
		}

		/** Head and body in one smooth shape, without a neck. */
		static double full(V p) {
			double d = ellipsoid(p, v(0, 0.52, -0.012), v(0.30, 0.44, 0.275));
			d = smin(d, ellipsoid(p, v(0, 0.93, -0.004), v(0.245, 0.36, 0.228)), 0.2);
			d = smin(d, ellipsoid(p, v(0, 1.30, 0.012), v(0.198, 0.205, 0.192)), 0.16);
			d = smax(d, p.y - 1.475, 0.09); // flat-topped head
			d = smin(d, ellipsoid(p, v(0, 1.402, 0.118), v(0.168, 0.04, 0.08)), 0.05); // heavy brow over the eyes
			for (int s = -1; s <= 1; s += 2) {
				d = smin(d, ellipsoid(p, v(s * 0.086, 1.272, 0.116), v(0.074, 0.058, 0.07)), 0.05); // cheeks
				d = smax(d, -ellipsoid(p, EYE.withX(s * EYE.x), EYE_R.mul(1.06)), 0.012); // eye sockets
			}
			d = smin(d, roundCone(p, v(0, 0.24, -0.21), v(0, 0.1, -0.355), 0.075, 0.02), 0.06); // tail
			return d + noise(p.mul(28)) * 0.0009;
		}

		static double head(V p) {
			return smax(full(p), NECK_Y - p.y, 0.004);
		}

		/** The body reaches a little into the head, slightly smaller, so turning the head never opens a gap. */
		static double body(V p) {
			return smax(full(p) + 0.004 * smoothstep(NECK_Y - 0.04, NECK_Y, p.y), p.y - (NECK_Y + 0.04), 0.012);
		}

		/** A shape squashed along one direction (a flattened cone); returns a conservative distance. */
		static double squashed(Sdf shape, V p, V center, V axis, double factor) {
			V q = p.sub(center);
			V a = axis.norm();
			V squeezed = q.add(a.mul(q.dot(a) * (factor - 1)));
			return shape.at(squeezed.add(center)) / factor;
		}

		/** Where the beaks meet: a line sloping down toward the tip. */
		static double mouthLine(double z) {
			return MOUTH_Y - 0.06 * (z - 0.15);
		}

		static double upperBeak(V p) {
			V base = v(0, 1.276, 0.14);
			double d = squashed(q -> roundCone(q, base, v(0, 1.252, 0.356), 0.052, 0.0065), p, base, v(0, 1, 0), 1.65);
			d = smax(d, mouthLine(p.z) - p.y, 0.004); // flat underside
			for (int s = -1; s <= 1; s += 2) { // nostrils
				d = smax(d, -ellipsoid(p, v(s * 0.019, 1.289, 0.215), v(0.006, 0.004, 0.009)), 0.003);
			}
			return d;
		}

		static double lowerBeak(V p) {
			V base = v(0, 1.254, 0.145);
			double d = squashed(q -> roundCone(q, base, v(0, 1.243, 0.318), 0.043, 0.008), p, base, v(0, 1, 0), 2.0);
			return smax(d, p.y - mouthLine(p.z), 0.004); // flat top
		}

		/** The flat side of the flippers: they are squashed along this. */
		static V flipperThin(double s) {
			return v(s * 0.305, 0.5, 0.042).sub(v(s * 0.232, 1.035, -0.012)).cross(v(0, 0, 1)).norm();
		}

		/** A flipper down to where its three feathers begin. */
		static double flipper(V p, double s) {
			V root = v(s * 0.232, 1.035, -0.012);
			V mid = v(s * 0.29, 0.78, 0.02);
			V end = v(s * 0.3, 0.625, 0.036);
			V thin = flipperThin(s);
			double d = squashed(q -> roundCone(q, root, mid, 0.07, 0.054), p, root, thin, 3.4);
			return smin(d, squashed(q -> roundCone(q, mid, end, 0.054, 0.04), p, mid, thin, 3.4), 0.03);
		}

		/** Where feather {@code f} (-1 at the back, 1 at the front) of a flipper starts and ends. */
		static V featherBase(double s, int f) {
			return v(s * 0.299, 0.645, 0.036 + f * 0.021);
		}

		static V featherTip(double s, int f) {
			return v(s * 0.306, f == 0 ? 0.5 : 0.52, 0.042 + f * 0.034);
		}

		/** One of the three rounded feathers at the end of a flipper, flat like it; they curl like fingers. */
		static double feather(V p, double s, int f) {
			V base = featherBase(s, f);
			return squashed(q -> roundCone(q, base, featherTip(s, f), 0.021, 0.011), p, base, flipperThin(s), 3.4);
		}

		@Override
		Map<String, Mesh> build() {
			Map<String, Mesh> parts = new LinkedHashMap<>();
			Mesh headMesh = surfaceNormals(surfaceNets(Penguin::head, v(-0.23, 1.08, -0.22), v(0.23, 1.5, 0.25), 0.0125), Penguin::full, 0.0125);
			headMesh.append(surfaceNets(Penguin::upperBeak, v(-0.07, 1.21, 0.12), v(0.07, 1.33, 0.38), 0.0055), BEAK);
			for (int s = -1; s <= 1; s += 2) {
				V eye = EYE.withX(s * EYE.x);
				addEllipsoid(headMesh, eye, EYE_R, EYE_WHITE);
				V look = v(s * -0.05, -0.06, 1);
				addEllipsoidCap(headMesh, eye, EYE_R, look, 36, 1.004, IRIS);
				addEllipsoidCap(headMesh, eye, EYE_R, look, 19, 1.008, PUPIL);
				addEllipsoid(headMesh, eye.add(v(s * -0.008 - 0.01, -0.006, EYE_R.z * 0.98)), v(0.0065, 0.007, 0.003), EYE_WHITE);
			}
			Mesh ember = new Mesh();
			addCigarette(headMesh, ember);
			parts.put("head", finish(headMesh, this::paint));
			parts.put("glow_head", finish(ember, this::paint));

			Mesh jaw = surfaceNets(Penguin::lowerBeak, v(-0.06, 1.2, 0.13), v(0.06, 1.27, 0.34), 0.005);
			addEllipsoid(jaw, v(0, mouthLine(0.23) - 0.002, 0.225), v(0.017, 0.004, 0.035), TONGUE);
			parts.put("jaw", finish(jaw, (p, n, m, e) -> paint(p, n, m == TONGUE ? TONGUE : BEAK_LOW, e)));

			parts.put("body", finish(surfaceNormals(surfaceNets(Penguin::body, v(-0.33, 0.06, -0.38), v(0.33, 1.19, 0.31), 0.019), Penguin::full, 0.019),
				this::paint));

			for (int side = -1; side <= 1; side += 2) {
				double s = side;
				Mesh arm = surfaceNets(p -> flipper(p, s), v(s * 0.27 - 0.12, 0.56, -0.12), v(s * 0.27 + 0.12, 1.11, 0.13), 0.0085);
				parts.put(side < 0 ? "right_arm" : "left_arm", finish(arm, (p, n, m, e) -> paintFlipper(p, n, s)));
				// The flipper bends in the middle, and its three feathers curl toward the body like fingers.
				this.elbow(s, v(s * 0.29, 0.78, 0.02), v(s * 0.298, 0.66, 0.032));
				String[] roles = {"little", "middle", "index"};
				for (int f = -1; f <= 1; f++) {
					int k = f;
					V base = featherBase(s, f);
					V tip = featherTip(s, f);
					Mesh feather = sdfFinger(p -> feather(p, s, k), base, tip, 0.03, 0.0052, BLACK);
					this.addFinger(parts, (side < 0 ? "right_" : "left_") + roles[f + 1], finish(feather, (p, n, m, e) -> paintFlipper(p, n, s)),
						new FingerSpec(base, tip, v(-s, 0, 0), false, new double[] {0, 0.45}, new double[] {65, 70}));
				}

				Mesh leg = new Mesh();
				addTube(leg, path(v(s * 0.1, 0.2, 0.0), v(s * 0.105, 0.12, 0.01), v(s * 0.11, 0.06, 0.02)), radii(0.05, 0.046, 0.04), 10, false, FOOT, FOOT);
				addFoot(leg, s);
				parts.put(side < 0 ? "right_leg" : "left_leg", finish(leg, this::paint));
			}

			// Normally its eyes are half closed: cool, it smokes. Angry: the lids slant down hard, under a scowl.
			Mesh calm = new Mesh();
			Mesh mad = new Mesh();
			for (int s = -1; s <= 1; s += 2) {
				V eye = EYE.withX(s * EYE.x);
				calm.append(eyelid(eye, EYE_R, s, -0.22, 5, 0.1), BLACK);
				mad.append(eyelid(eye, EYE_R, s, 0.1, 30, 0.12), BLACK);
				addTube(mad, path(eye.add(v(s * 0.055, 0.07, -0.016)), eye.add(v(s * 0.012, 0.05, 0.02)), eye.add(v(s * -0.045, 0.018, 0.022))),
					radii(0.013, 0.016, 0.012), 7, false, BLACK, BLACK);
			}
			parts.put("calm_head", finish(calm, this::paint));
			parts.put("mad_head", finish(mad, this::paint));
			parts.put("blink_head", finish(blinkLids(new V[] {EYE.withX(-EYE.x), EYE}, EYE_R, 0.13), (p, n, m, e) -> paint(p, n, BLACK, e)));

			return parts;
		}

		/** A cigarette in the corner of the beak: orange filter, white paper and a glowing tip with a little ash. */
		static void addCigarette(Mesh m, Mesh glow) {
			double r = 0.0105;
			V filterEnd = CIGARETTE.add(CIGARETTE_DIR.mul(0.052));
			V paperEnd = CIGARETTE.add(CIGARETTE_DIR.mul(CIGARETTE_LENGTH - 0.016));
			V end = CIGARETTE.add(CIGARETTE_DIR.mul(CIGARETTE_LENGTH));
			addTube(m, path(CIGARETTE, CIGARETTE.add(CIGARETTE_DIR.mul(0.026)), filterEnd), radii(r, r, r), 10, false, FILTER, FILTER);
			addTube(m, path(filterEnd, filterEnd.add(paperEnd).mul(0.5), paperEnd), radii(r * 0.99, r * 0.99, r * 0.99), 10, false, PAPER, PAPER);
			// The burning tip glows (it is drawn at full brightness, even at night), with gray ash at its very end.
			addTube(glow, path(paperEnd, paperEnd.add(CIGARETTE_DIR.mul(0.007)), end), radii(r * 0.98, r * 0.96, r * 0.86), 10, false, EMBER, EMBER);
			addEllipsoid(glow, end.sub(CIGARETTE_DIR.mul(0.002)), v(r * 0.8, r * 0.8, r * 0.8), EMBER_CORE);
			addEllipsoid(m, end.add(CIGARETTE_DIR.mul(0.004)), v(r * 0.62, r * 0.62, r * 0.5), ASH);
		}

		static void addFoot(Mesh m, double s) {
			V heel = v(s * 0.112, 0.03, 0.035);
			addEllipsoid(m, heel, v(0.068, 0.032, 0.075), FOOT);
			for (int t = -1; t <= 1; t++) {
				double a = Math.toRadians(t * 24 + s * 6);
				V tip = heel.add(v(Math.sin(a) * 0.125, -0.012, Math.cos(a) * 0.145));
				addTube(m, path(heel.add(v(Math.sin(a) * 0.03, 0.004, Math.cos(a) * 0.03)), heel.add(v(Math.sin(a) * 0.078, -0.004, Math.cos(a) * 0.092)), tip),
					radii(0.021, 0.017, 0.012), 8, false, FOOT, FOOT);
				addSphere(m, heel.add(v(Math.sin(a) * 0.078, -0.002, Math.cos(a) * 0.092)), 0.0185, FOOT); // knuckle
				addCone(m, tip.add(v(0, 0, -0.004)), tip.add(v(Math.sin(a) * 0.024, -0.006, Math.cos(a) * 0.024)), 0.0095, 6, CLAW);
			}
			for (int t = -1; t <= 0; t++) { // webbing between the toes
				double a0 = Math.toRadians(t * 24 + s * 6);
				double a1 = Math.toRadians((t + 1) * 24 + s * 6);
				V mid = heel.add(v((Math.sin(a0) + Math.sin(a1)) * 0.047, -0.01, (Math.cos(a0) + Math.cos(a1)) * 0.052));
				addOrientedEllipsoid(m, mid, v(0, 1, 0), (a0 + a1) / 2, v(0.032, 0.006, 0.054), FOOT);
			}
		}

		/** How white the feathers are here: the front from the feet up to the eyes, black everywhere else. */
		static double whiteness(V p, V n) {
			double front = smoothstep(-0.04, 0.07, p.z) * smoothstep(-0.5, -0.1, n.z);
			double width = lerp(0.215, 0.138, smoothstep(0.62, 1.3, p.y));
			double side = 1 - smoothstep(width - 0.016, width + 0.016, Math.abs(p.x));
			double cap = EYE.y + 0.052 + 0.6 * sq(p.x) - 0.016 * Math.exp(-sq(p.x / 0.03));
			double top = 1 - smoothstep(cap - 0.012, cap + 0.012, p.y);
			return front * side * top;
		}

		static double[] glossy(V n, double[] base, double strength) {
			double spec = Math.pow(Math.max(0, n.dot(v(0.3, 0.8, 0.5).norm())), 14) * strength;
			double rim = Math.pow(Math.max(0, n.dot(v(-0.6, 0.35, -0.7).norm())), 10) * strength * 0.5;
			return rgb(base[0] + spec + rim, base[1] + spec + rim, base[2] + (spec + rim) * 1.15);
		}

		double[] paint(V p, V n, int material, double extra) {
			double feather = noise(p.mul(70)) * 0.012 + noise(p.mul(16)) * 0.01;
			switch (material) {
				case BLACK:
					return glossy(n, rgb(0.04 + feather, 0.045 + feather, 0.065 + feather), 0.3);
				case EYE_WHITE:
					return rgb(0.97, 0.97, 0.95);
				case IRIS:
					return rgb(0.22, 0.30, 0.42);
				case PUPIL:
					return glossyBlack(n);
				case BEAK:
				case BEAK_LOW: {
					boolean inside = material == BEAK ? n.y < -0.7 && p.z > 0.2 : n.y > 0.7 && p.z > 0.19 && p.z < 0.3;
					if (inside) {
						return rgb(0.55, 0.16, 0.2);
					}
					double tipDark = smoothstep(0.28, 0.37, p.z) * 0.1;
					double k = material == BEAK ? 1.0 : 0.88;
					double nostril = material == BEAK && p.y > 1.28 && p.z > 0.205 && p.z < 0.225 && Math.abs(Math.abs(p.x) - 0.019) < 0.006 ? 0.45 : 1;
					return glossy(n, rgb((0.98 - tipDark) * k * nostril, (0.54 - tipDark) * k * nostril, 0.32 * k * nostril), 0.1);
				}
				case TONGUE:
					return rgb(0.9, 0.42, 0.48);
				case FOOT:
					return rgb(0.97 + feather, 0.55 + feather, 0.2);
				case CLAW:
					return rgb(0.2, 0.15, 0.1);
				case FILTER: {
					double speck = noise(p.mul(900)) > 0.55 ? 0.82 : 1;
					return rgb(0.9 * speck, 0.56 * speck, 0.22 * speck);
				}
				case PAPER:
					return rgb(0.97, 0.97, 0.95);
				case EMBER: {
					double flicker = noise(p.mul(600)) * 0.12;
					return rgb(1.0, 0.36 + flicker, 0.06);
				}
				case EMBER_CORE:
					return rgb(1.0, 0.82, 0.42);
				case ASH:
					return rgb(0.56 + feather, 0.54 + feather, 0.52 + feather);
				default:
					break;
			}
			double shade = 0.9 + 0.1 * smoothstep(-0.2, 0.8, n.z);
			double[] white = rgb((0.95 + feather) * shade, (0.95 + feather) * shade, (0.93 + feather) * shade);
			double[] black = glossy(n, rgb(0.04 + feather, 0.045 + feather, 0.065 + feather), 0.3);
			return mix(black, white, whiteness(p, n));
		}

		/** Flippers: black outside, white on the side that faces the body. */
		double[] paintFlipper(V p, V n, double side) {
			double feather = noise(p.mul(70)) * 0.012;
			if (n.x * -side > 0.35) {
				return rgb(0.93 + feather, 0.93 + feather, 0.91 + feather);
			}
			return glossy(n, rgb(0.04 + feather, 0.045 + feather, 0.065 + feather), 0.3);
		}
	}

	// ================================================================== SpongeBob

	/**
	 * SpongeBob: a bright yellow sponge with holes, big round eyes with blue irises and lashes, a long nose, rosy
	 * freckled cheeks, a wide smile with dimples and two big buck teeth, a white shirt with a red tie, brown square
	 * pants with a black belt, striped socks and shiny black shoes. The whole sponge is his head: it turns a little when
	 * he looks around, and the part below his smile drops open when he talks.
	 */
	static final class SpongeBob extends Design {
		static final V SPONGE_C = v(0, 0.93, 0.0);
		static final V SPONGE_H = v(0.33, 0.395, 0.14);
		static final double FRONT = SPONGE_C.z + SPONGE_H.z;
		static final V HEAD_PIVOT = v(0, 0.56, 0.0);
		/** Far behind the mouth, so that opening the jaw a few degrees drops it almost straight down. */
		static final V JAW_PIVOT = v(0, 0.86, -0.32);
		static final V EYE = v(0.11, 1.1, 0.1);
		static final V EYE_R = v(0.104, 0.11, 0.09);
		static final int SPONGE = 0, PORE = 1, EYE_WHITE = 2, IRIS = 3, PUPIL = 4, LASH = 5, LINE = 6, TOOTH = 7, TONGUE = 8, SHIRT = 9, TIE = 10,
			PANTS = 11, BELT = 12, SKIN = 13, SOCK = 14, SHOE = 15, BROW = 16, CHEEK = 17, FRECKLE = 18, RED = 19, BLUE = 20, LACE = 21;
		static final List<V> PORES = pores();

		@Override
		String name() {
			return "SpongeBob";
		}

		@Override
		double height() {
			return 1.45;
		}

		@Override
		double headTurn() {
			return 0.3;
		}

		@Override
		double bounce() {
			return 0.05;
		}

		@Override
		V iconEye() {
			return v(0.0, 0.98, 1.4);
		}

		@Override
		V iconTarget() {
			return v(0, 0.96, 0);
		}

		@Override
		double iconFov() {
			return 1.05;
		}

		@Override
		Rig rig() {
			return new Rig(9, HEAD_PIVOT, JAW_PIVOT, v(0, 0.56, 0),
				v(-0.33, 0.535, 0.0), v(-0.43, 0.25, 0.07), v(0.33, 0.535, 0.0), v(0.43, 0.25, 0.07),
				v(-0.10, 0.37, 0.0), v(0.10, 0.37, 0.0), v(0, 0.41, -0.135));
		}

		/** Holes all over the sponge, but not on his face. */
		static List<V> pores() {
			java.util.Random rnd = new java.util.Random(11);
			List<V> list = new ArrayList<>();
			int tries = 0;
			while (list.size() < 64 && tries++ < 8000) {
				int face = rnd.nextInt(6);
				double u = rnd.nextDouble() * 2 - 1;
				double w = rnd.nextDouble() * 2 - 1;
				V q = switch (face) {
					case 0 -> v(u * 0.29, SPONGE_C.y + w * 0.35, FRONT);
					case 1 -> v(u * 0.29, SPONGE_C.y + w * 0.35, -FRONT);
					case 2, 3 -> v(face == 2 ? SPONGE_H.x : -SPONGE_H.x, SPONGE_C.y + w * 0.35, u * 0.1);
					default -> v(u * 0.29, SPONGE_C.y + SPONGE_H.y, w * 0.1);
				};
				if (face == 0 && (q.y > 0.7 && Math.abs(q.x) < 0.27)) {
					continue; // keep eyes, nose, cheeks and mouth clear
				}
				double r = 0.012 + rnd.nextDouble() * rnd.nextDouble() * 0.03;
				boolean clear = true;
				for (int i = 0; i < list.size(); i += 2) {
					if (list.get(i).sub(q).len() < r + list.get(i + 1).x + 0.03) {
						clear = false;
						break;
					}
				}
				if (clear) {
					list.add(v(q.x, q.y, q.z));
					list.add(v(r, face, 0)); // size and which side, stored right after the position
				}
			}
			return list;
		}

		static double poreDistance(V p) {
			double d = 1e9;
			for (int i = 0; i < PORES.size(); i += 2) {
				V c = PORES.get(i);
				double r = PORES.get(i + 1).x;
				d = Math.min(d, p.sub(c).len() - r);
			}
			return d;
		}

		static double sponge(V p) {
			// Gently wavy sides, like a real kitchen sponge, but no lumps on his face.
			double wave = noise(p.mul(5)) * 0.006 + noise(p.mul(13)) * 0.002;
			double d = roundBox(p, SPONGE_C, SPONGE_H, 0.04) + wave;
			return smax(d, -poreDistance(p), 0.008);
		}

		static double smileY(double x) {
			return 0.905 - 0.072 * (1 - sq(x / 0.235));
		}

		/** The part of the face below the smile, which drops when he talks. Negative inside. */
		static double jawRegion(V p) {
			double belowSmile = p.y - smileY(p.x);
			double aboveChin = 0.735 + 0.17 * Math.pow(Math.abs(p.x) / 0.235, 4) - p.y; // a U that meets the smile at the corners
			double front = FRONT - 0.07 - p.z;
			double sides = Math.abs(p.x) - 0.25;
			return Math.max(Math.max(belowSmile, aboveChin), Math.max(front, sides));
		}

		static double cavity(V p) {
			return ellipsoid(p, v(0, 0.82, 0.045), v(0.22, 0.085, 0.065)); // stays behind the face
		}

		static double head(V p) {
			return smax(smax(sponge(p), -jawRegion(p), 0.004), -cavity(p), 0.006);
		}

		static double jaw(V p) {
			return smax(smax(sponge(p), jawRegion(p), 0.004), -cavity(p), 0.006);
		}

		static double body(V p) {
			double shirt = roundBox(p, v(0, 0.515, 0), v(0.305, 0.045, 0.122), 0.02);
			double pants = roundBox(p, v(0, 0.405, 0), v(0.285, 0.075, 0.118), 0.025);
			double belt = Math.max(roundBox(p, v(0, 0.405, 0), v(0.29, 0.08, 0.123), 0.027), Math.abs(p.y - 0.452) - 0.012);
			double d = Math.min(Math.min(shirt, pants), belt);
			for (int s = -1; s <= 1; s += 2) { // collar
				V q = p.sub(v(s * 0.045, 0.548, 0.125));
				double flap = Math.max(Math.max(Math.abs(q.z) - 0.006, -q.y - 0.035 + Math.abs(q.x) * 0.9), Math.max(q.y - 0.01, Math.abs(q.x) - 0.045));
				d = Math.min(d, flap - 0.002);
				for (int k = -1; k <= 1; k += 2) { // belt loops
					d = Math.min(d, roundBox(p, v(s * 0.2 + k * 0.0, 0.452, k * 0.124), v(0.008, 0.016, 0.004), 0.003));
				}
			}
			return d;
		}

		static double tie(V p) {
			double knot = roundBox(p, v(0, 0.535, 0.132), v(0.022, 0.018, 0.012), 0.008);
			double t = clamp01((0.52 - p.y) / 0.15);
			double width = lerp(0.018, 0.034, t);
			double blade = Math.max(Math.max(Math.abs(p.x) - width + Math.max(0, p.y - 0.52) * 0.3, Math.abs(p.z - 0.132) - 0.007),
				Math.max(p.y - 0.522, 0.37 - p.y + Math.abs(p.x) * 0.8));
			return Math.min(knot, blade - 0.002);
		}

		/** A point on the front of his face, lifted off it a little. */
		static V onFace(double x, double y, double lift) {
			V hit = hitSurface(SpongeBob::sponge, v(x, y, 0.4), v(0, 0, -1), 0.6);
			if (hit == null) {
				throw new IllegalStateException("not on the face: " + x + ", " + y);
			}
			return hit.add(v(0, 0, lift));
		}

		@Override
		Map<String, Mesh> build() {
			Map<String, Mesh> parts = new LinkedHashMap<>();
			Mesh headMesh = surfaceNormals(surfaceNets(SpongeBob::head, v(-0.37, 0.50, -0.19), v(0.37, 1.36, 0.19), 0.015), SpongeBob::sponge, 0.008);
			for (int s = -1; s <= 1; s += 2) {
				V eye = EYE.withX(s * EYE.x);
				addEllipsoid(headMesh, eye, EYE_R, EYE_WHITE);
				V look = v(0, -0.02, 1);
				addEllipsoidCap(headMesh, eye, EYE_R, look, 34, 1.004, IRIS);
				addEllipsoidCap(headMesh, eye, EYE_R, look, 17, 1.008, PUPIL);
				V front = eye.add(v(0, 0, EYE_R.z));
				addEllipsoid(headMesh, front.add(v(-0.021, 0.022, 0.0)), v(0.011, 0.012, 0.004), EYE_WHITE);
				addEllipsoid(headMesh, front.add(v(0.015, -0.014, -0.001)), v(0.0055, 0.0055, 0.003), EYE_WHITE);
				for (int l = -1; l <= 1; l++) { // three lashes, curling up and out
					double a = Math.toRadians(l * 30 + s * 12);
					V on = v(Math.sin(a) * 0.55, 0.78, 0.5).norm(); // on the top front of the eyeball, in front of the sponge
					V root = eye.add(v(on.x * EYE_R.x, on.y * EYE_R.y, on.z * EYE_R.z).mul(0.97));
					V tip = root.add(v(Math.sin(a) * 0.04 + s * 0.008, 0.058, 0.026));
					addTube(headMesh, path(root, root.add(v(Math.sin(a) * 0.01, 0.032, 0.016)), tip), radii(0.0085, 0.007, 0.004), 7, false, LASH, LASH);
				}
				// rosy cheeks with three freckles each
				V cheek = onFace(s * 0.208, 0.952, 0.002);
				addOrientedEllipsoid(headMesh, cheek, v(0, 0, 1), 0, v(0.043, 0.004, 0.043), CHEEK);
				for (int f = 0; f < 3; f++) {
					V freckle = onFace(s * 0.208 + (f - 1) * 0.019, 0.952 + (f == 1 ? 0.013 : -0.005), 0.005);
					addOrientedEllipsoid(headMesh, freckle, v(0, 0, 1), 0, v(0.0065, 0.0025, 0.0065), FRECKLE);
				}
				// dimples: little curves at the ends of the smile
				List<V> dimple = new ArrayList<>();
				for (double a : new double[] {75, 30, -15, -60}) {
					double r = Math.toRadians(a);
					dimple.add(onFace(s * (0.236 + 0.024 * Math.cos(r)), smileY(0.236) + 0.024 * Math.sin(r), 0.002));
				}
				addTube(headMesh, dimple, radii(0.004, 0.0055, 0.0055, 0.004), 6, false, LINE, LINE);
			}
			// the nose, long and a little upturned
			addTube(headMesh, path(v(0, 1.0, FRONT - 0.03), v(0, 1.008, FRONT + 0.05), v(0, 1.028, FRONT + 0.11)), radii(0.029, 0.028, 0.028), 12, false, SPONGE,
				SPONGE);
			addSphere(headMesh, v(0, 1.03, FRONT + 0.112), 0.032, SPONGE);
			// the smile: a clean line from cheek to cheek, with the two buck teeth hanging from it
			List<V> smile = new ArrayList<>();
			for (int i = 0; i <= 16; i++) {
				double x = -0.236 + 0.472 * i / 16;
				smile.add(onFace(x, smileY(x) + 0.002, 0.0015));
			}
			double[] smileR = new double[smile.size()];
			for (int i = 0; i < smileR.length; i++) {
				double t = (double) i / (smileR.length - 1);
				smileR[i] = 0.0045 + 0.0025 * Math.sin(Math.PI * t);
			}
			addTube(headMesh, smile, smileR, 6, false, LINE, LINE);
			for (int s = -1; s <= 1; s += 2) {
				V top = onFace(s * 0.029, smileY(s * 0.029), 0.0);
				addBevelBox(headMesh, v(s * 0.029, top.y - 0.031, top.z + 0.004), v(1, 0, 0), v(0, 1, 0), v(0, 0, 1), v(0.026, 0.034, 0.007), 0.006, TOOTH, TOOTH);
			}
			parts.put("head", finish(headMesh, this::paintHead));

			Mesh jawMesh = surfaceNormals(surfaceNets(SpongeBob::jaw, v(-0.27, 0.68, FRONT - 0.11), v(0.27, 0.93, FRONT + 0.05), 0.0095), SpongeBob::sponge, 0.0035);
			addEllipsoid(jawMesh, v(0, 0.742, FRONT - 0.045), v(0.115, 0.013, 0.045), TONGUE);
			parts.put("jaw", finish(jawMesh, this::paintHead));

			Mesh bodyMesh = surfaceNets(SpongeBob::body, v(-0.33, 0.30, -0.15), v(0.33, 0.59, 0.16), 0.013);
			bodyMesh.append(surfaceNets(SpongeBob::tie, v(-0.05, 0.35, 0.11), v(0.05, 0.56, 0.155), 0.0045), TIE);
			parts.put("body", finish(bodyMesh, this::paintBody));

			for (int side = -1; side <= 1; side += 2) {
				double s = side;
				parts.put(side < 0 ? "right_arm" : "left_arm", finish(arm(s), this::paintBody));
				this.addHand(parts, s);
				parts.put(side < 0 ? "right_leg" : "left_leg", finish(leg(s), this::paintBody));
			}

			// Angry: heavy lids slanting down over the eyes, and thick brows.
			Mesh mad = new Mesh();
			for (int s = -1; s <= 1; s += 2) {
				V eye = EYE.withX(s * EYE.x);
				mad.append(eyelid(eye, EYE_R, s, 0.02, 26, 0.09), SPONGE);
				addTube(mad, path(eye.add(v(s * 0.1, 0.11, 0.0)), eye.add(v(s * 0.03, 0.085, 0.048)), eye.add(v(s * -0.075, 0.035, 0.052))),
					radii(0.014, 0.017, 0.013), 8, false, BROW, BROW);
			}
			parts.put("mad_head", finish(mad, this::paintHead));
			parts.put("blink_head", finish(blinkLids(new V[] {EYE.withX(-EYE.x), EYE}, EYE_R, 0.08), (p, n, m, e) -> paintHead(p, n, SPONGE, e)));
			return parts;
		}

		static Mesh arm(double s) {
			Mesh m = new Mesh();
			addTube(m, path(v(s * 0.29, 0.53, 0.0), v(s * 0.33, 0.528, 0.0), v(s * 0.372, 0.52, 0.0)), radii(0.05, 0.053, 0.056), 14, false, SHIRT, SHIRT);
			addTorus(m, v(s * 0.372, 0.52, 0.0), v(1, -0.15 * s, 0).mul(s), 0.054, 0.006, 16, 6, SHIRT); // sleeve hem
			addTube(m, path(v(s * 0.37, 0.52, 0.0), v(s * 0.41, 0.42, 0.02), v(s * 0.425, 0.31, 0.05)), radii(0.022, 0.021, 0.02), 9, false, SKIN, SKIN);
			addEllipsoid(m, palm(s), v(0.03, 0.038, 0.026), SKIN);
			return m;
		}

		static V palm(double s) {
			return v(s * 0.428, 0.275, 0.062);
		}

		/** Three fingers and a thumb, each a part of its own, curling toward the palm (the body side). The arm bends halfway down. */
		void addHand(Map<String, Mesh> parts, double s) {
			String prefix = s < 0 ? "right_" : "left_";
			this.elbow(s, v(s * 0.405, 0.43, 0.018), v(s * 0.425, 0.305, 0.05));
			V palm = palm(s);
			String[] roles = {"little", "middle", "index"};
			for (int f = 0; f < 3; f++) {
				double z = palm.z + (f - 1) * 0.019;
				V base = v(palm.x, palm.y - 0.022, z);
				V tip = v(palm.x, palm.y - 0.08, z + 0.009);
				Mesh finger = new Mesh();
				addTube(finger, path(base, v(palm.x + s * 0.004, palm.y - 0.05, z + 0.004), v(palm.x, palm.y - 0.072, z + 0.008)),
					radii(0.0108, 0.0102, 0.0094), 8, false, SKIN, SKIN);
				this.addFinger(parts, prefix + roles[f], finish(finger, this::paintBody), new FingerSpec(base, tip, v(-s, 0, 0), false));
			}
			V thumbBase = v(palm.x - s * 0.02, palm.y - 0.005, palm.z + 0.02);
			Mesh thumb = new Mesh();
			addTube(thumb, path(thumbBase, v(palm.x - s * 0.03, palm.y - 0.025, palm.z + 0.03), v(palm.x - s * 0.034, palm.y - 0.04, palm.z + 0.032)),
				radii(0.0102, 0.0097, 0.009), 8, false, SKIN, SKIN);
			this.addFinger(parts, prefix + "thumb", finish(thumb, this::paintBody),
				new FingerSpec(thumbBase, v(palm.x - s * 0.035, palm.y - 0.047, palm.z + 0.033), v(-s * 0.4, 0, -1), false));
		}

		static Mesh leg(double s) {
			Mesh m = new Mesh();
			addTube(m, path(v(s * 0.10, 0.37, 0.0), v(s * 0.102, 0.33, 0.0), v(s * 0.104, 0.30, 0.0)), radii(0.068, 0.07, 0.07), 14, false, PANTS, PANTS);
			addTube(m, path(v(s * 0.104, 0.31, 0.0), v(s * 0.105, 0.2, 0.0), v(s * 0.105, 0.08, 0.0)), radii(0.023, 0.022, 0.022), 9, false, SKIN, SKIN);
			addTube(m, path(v(s * 0.105, 0.215, 0.0), v(s * 0.105, 0.15, 0.0), v(s * 0.105, 0.07, 0.0)), radii(0.031, 0.03, 0.03), 10, false, SOCK, SOCK);
			addTorusY(m, v(s * 0.105, 0.19, 0.0), 0.031, 0.0045, RED);
			addTorusY(m, v(s * 0.105, 0.17, 0.0), 0.031, 0.0045, BLUE);
			m.append(surfaceNets(q -> shoe(q, s), v(s * 0.105 - 0.085, -0.005, -0.08), v(s * 0.105 + 0.085, 0.11, 0.18), 0.009), SHOE);
			for (int k = 0; k < 2; k++) { // laces
				addTube(m, path(v(s * 0.105 - 0.025, 0.098 - k * 0.012, 0.035 + k * 0.025), v(s * 0.105, 0.102 - k * 0.012, 0.04 + k * 0.025),
					v(s * 0.105 + 0.025, 0.098 - k * 0.012, 0.035 + k * 0.025)), radii(0.0035, 0.0035, 0.0035), 5, false, LACE, LACE);
			}
			return m;
		}

		static double shoe(V p, double s) {
			double d = ellipsoid(p, v(s * 0.105, 0.05, 0.045), v(0.07, 0.052, 0.115));
			d = smin(d, ellipsoid(p, v(s * 0.105, 0.075, -0.005), v(0.045, 0.04, 0.05)), 0.03);
			return smax(d, -p.y + 0.004, 0.012);
		}

		double[] paintHead(V p, V n, int material, double extra) {
			double grain = noise(p.mul(40)) * 0.012;
			switch (material) {
				case EYE_WHITE:
					return rgb(0.99, 0.99, 0.98);
				case IRIS: {
					double ring = smoothstep(0.6, 1.0, extra);
					return rgb(0.3, 0.66 - 0.1 * ring, 0.98);
				}
				case PUPIL:
				case LASH:
					return glossyBlack(n);
				case TOOTH:
					return rgb(0.99, 0.99, 0.97);
				case TONGUE:
					return rgb(0.97, 0.55, 0.6);
				case BROW:
					return rgb(0.45, 0.28, 0.06);
				case LINE:
					return rgb(0.42, 0.16, 0.08);
				case CHEEK:
					return rgb(0.99, 0.62, 0.52);
				case FRECKLE:
					return rgb(0.82, 0.32, 0.24);
				default:
					break;
			}
			if (cavity(p) < 0.008 || Math.abs(jawRegion(p)) < 0.008 && sponge(p) < -0.007) {
				double back = clamp01((FRONT - p.z) / 0.14);
				return rgb(0.5 - 0.3 * back, 0.07, 0.1);
			}
			double pore = poreDistance(p);
			double[] yellow = rgb(1.0 + grain, 0.94 + grain, 0.27 + grain);
			if (pore < 0.004) {
				double depth = clamp01(-pore / 0.012);
				return mix(rgb(0.78, 0.74, 0.12), rgb(0.62, 0.58, 0.06), depth);
			}
			return yellow;
		}

		double[] paintBody(V p, V n, int material, double extra) {
			double grain = noise(p.mul(40)) * 0.02;
			switch (material) {
				case SHIRT:
					return rgb(0.97, 0.97, 0.97);
				case TIE:
					return rgb(0.88 + grain, 0.13, 0.12);
				case SKIN:
					return rgb(1.0 + grain, 0.94 + grain, 0.27);
				case SOCK:
					return rgb(0.97, 0.97, 0.97);
				case RED:
					return rgb(0.86, 0.14, 0.14);
				case BLUE:
					return rgb(0.18, 0.4, 0.86);
				case SHOE:
					return glossyBlack(n);
				case LACE:
					return rgb(0.12, 0.12, 0.12);
				case PANTS:
					return rgb(0.62 + grain, 0.40 + grain, 0.16);
				default:
					break;
			}
			if (p.y > 0.47) {
				return rgb(0.97, 0.97, 0.97); // shirt and collar
			}
			if (p.y > 0.438 && p.y < 0.466 && roundBox(p, v(0, 0.405, 0), v(0.285, 0.075, 0.118), 0.025) > 0.002) {
				return glossyBlack(n); // belt and its loops
			}
			return rgb(0.62 + grain, 0.40 + grain, 0.16);
		}
	}

	// ================================================================== Skinny Alien

	/**
	 * A very tall, starved-looking creature: mottled golden tan skin over visible ribs and spine, a long
	 * neck with tendons, very long bony arms and legs with knobby elbows, knees and ankles, long knuckled fingers and toes
	 * with dark claws, and a long ridged skull with small sunken eyes, slit nostrils and a gaping mouth that opens wider
	 * when it talks.
	 */
	static final class SkinnyAlien extends Design {
		static final V HEAD_PIVOT = v(0, 1.60, 0.0);
		static final V JAW_PIVOT = v(0, 2.0, -0.025);
		/** The jaw already hangs open this far when quiet. */
		static final double GAPE = Math.toRadians(16);
		static final V EYE = v(0.04, 2.052, 0.083);
		static final double MOUTH_Y = 1.985;
		static final double CUT_SLOPE = 0.15;
		static final double JAW_BACK_Z = -0.035;
		static final V CAVITY_C = v(0, 1.975, 0.04);
		static final V CAVITY_R = v(0.058, 0.032, 0.07);
		static final int SKIN = 0, MOUTH = 1, TEETH = 2, EYE_M = 3, NAIL = 4, GLOW = 5;

		@Override
		String name() {
			return "Skinny Alien";
		}

		@Override
		double height() {
			return 2.2;
		}

		@Override
		double headTurn() {
			return 0.75;
		}

		@Override
		double bounce() {
			return 0.025;
		}

		@Override
		V iconEye() {
			return v(0.0, 2.03, 0.85);
		}

		@Override
		V iconTarget() {
			return v(0, 2.02, 0);
		}

		@Override
		double iconFov() {
			return 0.55;
		}

		@Override
		Rig rig() {
			return new Rig(22, HEAD_PIVOT, JAW_PIVOT, v(0, 1.58, 0),
				v(-0.19, 1.555, -0.015), v(-0.236, 0.64, 0.06), v(0.19, 1.555, -0.015), v(0.236, 0.64, 0.06),
				v(-0.085, 1.04, 0.0), v(0.085, 1.04, 0.0), v(0, 1.02, -0.105));
		}

		static V shoulder(double s) {
			return v(s * 0.19, 1.555, -0.015);
		}

		static V elbow(double s) {
			return v(s * 0.214, 1.17, -0.03);
		}

		static V wrist(double s) {
			return v(s * 0.225, 0.80, 0.008);
		}

		static V palm(double s) {
			return v(s * 0.228, 0.752, 0.018);
		}

		static V hip(double s) {
			return v(s * 0.085, 1.04, 0.0);
		}

		static V knee(double s) {
			return v(s * 0.09, 0.57, 0.015);
		}

		static V ankle(double s) {
			return v(s * 0.092, 0.075, -0.008);
		}

		static double ribs(V p) {
			if (p.y < 1.2 || p.y > 1.56) {
				return 0;
			}
			double r = 0;
			for (int k = 0; k < 7; k++) {
				double yk = 1.5 - k * 0.04;
				// ribs run down toward the front, and stop at the breastbone
				double y = yk - 0.06 * smoothstep(-0.02, 0.09, p.z) * (1 - Math.min(1, Math.abs(p.x) / 0.13));
				r = Math.max(r, Math.exp(-sq((p.y - y) / 0.0105)));
			}
			return r * smoothstep(0.012, 0.03, Math.abs(p.x)) * smoothstep(-0.09, -0.02, p.z);
		}

		static double torso(V p) {
			double d = ellipsoid(p, v(0, 1.405, -0.005), v(0.138, 0.185, 0.097));
			d = smin(d, roundCone(p, v(0, 1.235, -0.01), v(0, 1.11, -0.012), 0.066, 0.074), 0.05);
			d = smin(d, ellipsoid(p, v(0, 1.055, -0.012), v(0.11, 0.07, 0.076)), 0.05);
			for (int s = -1; s <= 1; s += 2) {
				d = smin(d, roundCone(p, v(s * 0.03, 1.56, -0.004), v(s * 0.19, 1.562, -0.016), 0.036, 0.032), 0.03);
				d = smin(d, ellipsoid(p, v(s * 0.096, 1.085, 0.04), v(0.024, 0.03, 0.024)), 0.02); // hip bones
				d = smin(d, roundCone(p, v(s * 0.06, 1.12, 0.05), v(s * 0.1, 1.08, 0.035), 0.011, 0.012), 0.02); // hip crests
				d = smin(d, roundCone(p, v(s * 0.018, 1.538, 0.064), v(s * 0.125, 1.552, 0.042), 0.012, 0.01), 0.014); // collarbones
				// the bony points of the shoulders
				d = smin(d, sphere(p, v(s * 0.172, 1.575, -0.012), 0.022), 0.02);
			}
			// the spine down the back
			for (int k = 0; k < 14; k++) {
				double y = 1.555 - k * 0.034;
				double z = -0.094 + 0.03 * smoothstep(1.2, 1.05, y) - 0.012 * Math.exp(-sq((y - 1.42) / 0.08));
				d = smin(d, ellipsoid(p, v(0, y, z), v(0.0095, 0.0115, 0.0085)), 0.017);
			}
			double sternum = Math.exp(-sq(p.x / 0.011)) * smoothstep(1.3, 1.34, p.y) * (1 - smoothstep(1.5, 1.54, p.y)) * smoothstep(0.02, 0.07, p.z);
			d -= 0.0095 * ribs(p) + 0.004 * sternum;
			d = smax(d, -ellipsoid(p, v(0, 1.185, 0.09), v(0.06, 0.075, 0.035)), 0.03); // sunken belly
			for (int s = -1; s <= 1; s += 2) { // hollows above the collarbones
				d = smax(d, -ellipsoid(p, v(s * 0.06, 1.565, 0.05), v(0.03, 0.012, 0.02)), 0.012);
			}
			return d + noise(p.mul(60)) * 0.0008;
		}

		static double sphere(V p, V c, double r) {
			return p.sub(c).len() - r;
		}

		static V between(V a, V b, double t) {
			return a.add(b.sub(a).mul(t));
		}

		static double cutPlane(V p) {
			double cutY = MOUTH_Y + (0.13 - p.z) * CUT_SLOPE;
			return (p.y - cutY) / Math.sqrt(1 + CUT_SLOPE * CUT_SLOPE);
		}

		static double jawRegion(V p) {
			return Math.max(cutPlane(p), JAW_BACK_Z - p.z);
		}

		static double cavity(V p) {
			return ellipsoid(p, CAVITY_C, CAVITY_R);
		}

		static double skull(V p) {
			double d = ellipsoid(p, v(0, 2.07, -0.045), v(0.094, 0.098, 0.122));
			d = smin(d, ellipsoid(p, v(0, 1.995, 0.03), v(0.08, 0.095, 0.092)), 0.05);
			d = smin(d, roundCone(p, v(0, 2.135, 0.03), v(0, 2.155, -0.11), 0.012, 0.017), 0.045); // a ridge over the top
			d = smin(d, ellipsoid(p, v(0, 2.04, -0.155), v(0.06, 0.05, 0.035)), 0.05); // bump at the back
			for (int s = -1; s <= 1; s += 2) {
				d = smin(d, ellipsoid(p, v(s * 0.062, 2.005, 0.07), v(0.028, 0.02, 0.028)), 0.022); // cheekbones
				d = smin(d, roundCone(p, v(s * 0.012, 2.08, 0.1), v(s * 0.068, 2.076, 0.074), 0.011, 0.009), 0.018); // brow ridges
				d = smax(d, -ellipsoid(p, v(s * 0.094, 2.07, 0.0), v(0.02, 0.032, 0.04)), 0.03); // hollow temples
				d = smax(d, -ellipsoid(p, v(s * 0.07, 1.975, 0.06), v(0.018, 0.03, 0.03)), 0.025); // hollow cheeks
				d = smax(d, -ellipsoid(p, EYE.withX(s * EYE.x).add(v(0, 0, 0.012)), v(0.026, 0.019, 0.024)), 0.012); // sockets
				d = smax(d, -ellipsoid(p, v(s * 0.011, 2.022, 0.116), v(0.0035, 0.0095, 0.008)), 0.003); // slit nostrils
				// creases from the nostrils down past the mouth
				d = smax(d, -(roundCone(p, v(s * 0.025, 2.025, 0.116), v(s * 0.052, 1.975, 0.102), 0.0048, 0.004)), 0.004);
			}
			return d;
		}

		static double neck(V p) {
			double d = roundCone(p, v(0, 1.575, -0.006), v(0, 1.905, 0.004), 0.046, 0.036);
			for (int s = -1; s <= 1; s += 2) {
				d = smin(d, roundCone(p, v(s * 0.021, 1.6, 0.022), v(s * 0.012, 1.88, 0.042), 0.011, 0.008), 0.012); // tendons
			}
			d = smin(d, ellipsoid(p, v(0, 1.79, 0.04), v(0.012, 0.016, 0.01)), 0.012); // throat bump
			for (int k = 0; k < 7; k++) { // the spine at the back of the neck
				double t = (0.045 + k * 0.042) / 0.33;
				d = smin(d, ellipsoid(p, v(0, 1.62 + k * 0.042, -0.049 + 0.02 * t), v(0.01, 0.012, 0.009)), 0.012);
			}
			return smax(d, 1.585 - p.y, 0.02);
		}

		static double head(V p) {
			// The neck is added after cutting out the jaw, which would otherwise take it.
			return smin(smax(smax(skull(p), -jawRegion(p), 0.008), -cavity(p), 0.008), neck(p), 0.03);
		}

		static double jawShape(V p) {
			return smax(smax(skull(p), jawRegion(p), 0.008), -cavity(p), 0.008);
		}

		/** Shoulder to wrist: thin, with knobby elbows and the two bones of the forearm showing. */
		static double arm(V p, double s) {
			V sh = shoulder(s);
			V el = elbow(s);
			V wr = wrist(s);
			double d = sphere(p, sh, 0.04);
			d = smin(d, roundCone(p, sh, el, 0.034, 0.023), 0.03);
			d = smin(d, ellipsoid(p, between(sh, el, 0.38).add(v(0, 0, 0.011)), v(0.026, 0.1, 0.027)), 0.035); // biceps
			d = smin(d, sphere(p, el.add(v(0, 0.004, -0.014)), 0.026), 0.016); // the point of the elbow
			d = smin(d, sphere(p, el.add(v(s * 0.015, 0.002, 0.002)), 0.019), 0.012);
			d = smin(d, sphere(p, el.add(v(-s * 0.013, 0.004, 0.004)), 0.017), 0.012);
			d = smin(d, roundCone(p, el, wr.add(v(s * 0.008, 0, -0.004)), 0.021, 0.0135), 0.02);
			d = smin(d, roundCone(p, el.add(v(-s * 0.01, -0.02, 0.01)), wr.add(v(-s * 0.006, 0, 0.008)), 0.016, 0.0115), 0.012);
			d = smin(d, ellipsoid(p, between(el, wr, 0.22).add(v(0, 0, 0.004)), v(0.023, 0.085, 0.023)), 0.03); // forearm muscle
			d = smin(d, sphere(p, wr.add(v(s * 0.013, 0.002, -0.006)), 0.0115), 0.008); // wrist bones
			d = smin(d, sphere(p, wr.add(v(-s * 0.011, 0.004, 0.008)), 0.01), 0.008);
			return smax(d, wr.y - 0.02 - p.y, 0.01) + noise(p.mul(70)) * 0.0006;
		}

		/** One finger: segments with knuckles, curling forward a little. Returns {distance, and the tip in out}. */
		static double finger(V p, V base, V dir, V curl, double length, double radius, V[] tipOut) {
			double d = 1e9;
			V a = base;
			double[] parts = {0.42, 0.33, 0.25};
			V forward = dir.norm();
			for (int i = 0; i < 3; i++) {
				V b = a.add(forward.mul(length * parts[i]));
				double r0 = radius * (1 - 0.14 * i);
				d = smin(d, roundCone(p, a, b, r0, r0 * 0.86), 0.004);
				if (i > 0) {
					d = smin(d, sphere(p, a, r0 * 1.2), 0.004); // knuckle
				}
				a = b;
				forward = forward.add(curl).norm();
			}
			if (tipOut != null) {
				tipOut[0] = a;
				tipOut[1] = forward;
			}
			return d;
		}

		static final double[] FINGER_LENGTH = {0.125, 0.15, 0.15, 0.125};

		/** The palm: narrow, with knuckles where the fingers grow out (they are parts of their own) and tendons over its back. */
		static double palmShape(V p, double s) {
			V palm = palm(s);
			double d = ellipsoid(p, palm, v(0.0145, 0.042, 0.029));
			d = smin(d, sphere(p, wrist(s).add(v(0, -0.012, 0.004)), 0.016), 0.02);
			for (int f = 0; f < 4; f++) {
				double z = palm.z - 0.022 + f * 0.0145;
				V base = v(palm.x, palm.y - 0.035, z);
				d = smin(d, sphere(p, base, 0.0088), 0.006); // knuckle
				d = smin(d, roundCone(p, palm.add(v(s * -0.008, 0.02, (f - 1.5) * 0.009)), base.add(v(s * -0.006, 0.004, 0)), 0.0028, 0.0035), 0.005); // tendons
			}
			return smin(d, sphere(p, v(palm.x - s * 0.006, palm.y - 0.008, palm.z + 0.026), 0.0085), 0.008);
		}

		/** A fist with the middle finger pointing forward (+Z), which is up once the arm is raised. */
		static double fist(V p, double s, List<V[]> tips) {
			V palm = palm(s);
			double d = ellipsoid(p, palm, v(0.016, 0.04, 0.03));
			d = smin(d, sphere(p, wrist(s).add(v(0, -0.012, 0.004)), 0.016), 0.02);
			for (int f = 0; f < 4; f++) {
				double x = palm.x + s * (f - 1.5) * 0.0105;
				V knuckle = v(x, palm.y - 0.03, palm.z + 0.022);
				d = smin(d, sphere(p, knuckle, 0.0092), 0.005);
				if (f == 2) {
					V[] tip = new V[2];
					d = smin(d, finger(p, knuckle, v(0, 0.08, 1), v(0, 0.02, 0), FINGER_LENGTH[2], 0.0074, tip), 0.006);
					if (tips != null) {
						tips.add(tip);
					}
				} else { // curled back into the palm
					d = smin(d, roundCone(p, knuckle, knuckle.add(v(0, -0.02, 0.012)), 0.0078, 0.007), 0.004);
					d = smin(d, roundCone(p, knuckle.add(v(0, -0.02, 0.012)), knuckle.add(v(0, -0.032, -0.012)), 0.007, 0.0062), 0.004);
				}
			}
			d = smin(d, roundCone(p, v(palm.x - s * 0.012, palm.y - 0.005, palm.z + 0.02), v(palm.x + s * 0.004, palm.y - 0.036, palm.z + 0.034), 0.008, 0.007),
				0.006); // thumb over the curled fingers
			return d;
		}

		static double leg(V p, double s) {
			V hip = hip(s);
			V knee = knee(s);
			V ankle = ankle(s);
			double d = sphere(p, hip, 0.052);
			d = smin(d, roundCone(p, hip, knee, 0.05, 0.029), 0.04);
			d = smin(d, ellipsoid(p, between(hip, knee, 0.3).add(v(0, 0, 0.012)), v(0.04, 0.14, 0.043)), 0.04); // thigh
			d = smin(d, ellipsoid(p, knee.add(v(0, 0.006, 0.025)), v(0.025, 0.03, 0.018)), 0.014); // kneecap
			d = smin(d, sphere(p, knee.add(v(s * 0.022, -0.008, 0.0)), 0.024), 0.014);
			d = smin(d, sphere(p, knee.add(v(-s * 0.02, -0.008, 0.0)), 0.022), 0.014);
			d = smin(d, roundCone(p, knee.add(v(0, -0.02, 0.004)), ankle, 0.029, 0.017), 0.03);
			d = smin(d, roundCone(p, knee.add(v(0, -0.035, 0.024)), ankle.add(v(0, 0.035, 0.013)), 0.011, 0.0075), 0.012); // shin bone
			d = smin(d, ellipsoid(p, between(knee, ankle, 0.25).add(v(0, 0, -0.016)), v(0.026, 0.11, 0.028)), 0.03); // calf
			d = smin(d, sphere(p, ankle.add(v(s * 0.017, 0.004, 0)), 0.013), 0.008); // ankle bones
			d = smin(d, sphere(p, ankle.add(v(-s * 0.015, 0.012, 0)), 0.012), 0.008);
			return smax(d, 0.05 - p.y, 0.01) + noise(p.mul(60)) * 0.0006;
		}

		/** A long bony foot: heel, tendons over the top and four long toes. */
		static double foot(V p, double s, List<V[]> tips) {
			V ankle = ankle(s);
			double d = sphere(p, ankle.add(v(0, -0.012, -0.004)), 0.02);
			d = smin(d, ellipsoid(p, v(s * 0.093, 0.028, -0.012), v(0.025, 0.028, 0.032)), 0.02); // heel
			d = smin(d, squashedY(q -> roundCone(q, v(s * 0.093, 0.03, 0.0), v(s * 0.093, 0.022, 0.075), 0.026, 0.028), p, 0.026, 1.5), 0.02);
			for (int t = 0; t < 4; t++) {
				double dx = (t - 1.5) * 0.0145;
				V base = v(s * (0.093 + dx), 0.02, 0.082);
				d = smin(d, roundCone(p, ankle.add(v(s * dx * 0.4, -0.03, 0.02)), base.add(v(0, 0.006, 0)), 0.004, 0.0055), 0.006); // tendons
				d = smin(d, sphere(p, base, 0.0098), 0.006);
				V[] tip = new V[2];
				double length = 0.075 - Math.abs(t - 1.5) * 0.012;
				d = smin(d, finger(p, base, v(s * dx * 1.2, -0.12, 1), v(0, -0.07, 0), length, 0.0088, tip), 0.006);
				if (tips != null) {
					tips.add(tip);
				}
			}
			return smax(d, -p.y + 0.002, 0.004);
		}

		/** A shape squashed to {@code 1 / factor} of its height around {@code centerY}; returns a conservative distance. */
		static double squashedY(Sdf shape, V p, double centerY, double factor) {
			return shape.at(v(p.x, centerY + (p.y - centerY) * factor, p.z)) / factor;
		}

		static double wholeBody(V p) {
			double d = Math.min(smin(skull(p), neck(p), 0.04), torso(p));
			for (int s = -1; s <= 1; s += 2) {
				d = Math.min(d, Math.min(arm(p, s), leg(p, s)));
			}
			return d;
		}

		@Override
		Map<String, Mesh> build() {
			Map<String, Mesh> parts = new LinkedHashMap<>();
			Mesh headMesh = surfaceNormals(surfaceNets(SkinnyAlien::head, v(-0.12, 1.56, -0.2), v(0.12, 2.19, 0.15), 0.009),
				p -> smin(skull(p), neck(p), 0.03), 0.003);
			for (int s = -1; s <= 1; s += 2) {
				V eye = EYE.withX(s * EYE.x);
				addEllipsoid(headMesh, eye, v(0.017, 0.013, 0.012), EYE_M);
				addEllipsoid(headMesh, eye.add(v(s * -0.004, 0.004, 0.011)), v(0.003, 0.003, 0.0015), GLOW);
			}
			addTeeth(headMesh, true);
			parts.put("head", finish(headMesh, (p, n, m, e) -> paint(p, n, m, e, "head")));

			Mesh jaw = surfaceNormals(surfaceNets(SkinnyAlien::jawShape, v(-0.1, 1.87, -0.06), v(0.1, 2.03, 0.14), 0.0082), SkinnyAlien::skull, 0.003);
			addTeeth(jaw, false);
			CharacterModels.paint(jaw, (p, n, m, e) -> paint(p, n, m, e, "jaw"));
			jaw.orientQuads();
			gape(jaw);
			parts.put("jaw", jaw);

			parts.put("body", finish(surfaceNets(SkinnyAlien::torso, v(-0.24, 0.96, -0.14), v(0.24, 1.62, 0.13), 0.0112),
				(p, n, m, e) -> paint(p, n, m, e, "body")));
			for (int side = -1; side <= 1; side += 2) {
				double s = side;
				parts.put(side < 0 ? "right_arm" : "left_arm", finish(armMesh(s), (p, n, m, e) -> paint(p, n, m, e, "arm")));
				this.addHand(parts, s);
				parts.put(side < 0 ? "right_leg" : "left_leg", finish(legMesh(s), (p, n, m, e) -> paint(p, n, m, e, "leg")));
			}

			// Angry: brows pulled down hard over the eyes, which glow red.
			Mesh mad = new Mesh();
			for (int s = -1; s <= 1; s += 2) {
				addSurfaceBrow(mad, SkinnyAlien::skull, new double[][] {{s * 0.085, 2.088}, {s * 0.058, 2.08}, {s * 0.032, 2.068}, {s * 0.012, 2.05}},
					0.4, 0.015, 0.011, 0.003, SKIN);
				addEllipsoid(mad, EYE.withX(s * EYE.x).add(v(0, 0, 0.004)), v(0.0175, 0.0135, 0.0125), GLOW);
			}
			parts.put("mad_head", finish(mad, (p, n, m, e) -> m == GLOW ? rgb(1.0, 0.18, 0.08) : paint(p, n, m, e, "head")));
			parts.put("blink_head", finish(blinkLids(new V[] {EYE.withX(-EYE.x), EYE}, v(0.018, 0.0145, 0.013), 0.12),
				(p, n, m, e) -> paint(p, n, SKIN, e, "blink")));
			return parts;
		}

		static Mesh armMesh(double s) {
			V sh = shoulder(s);
			Mesh m = surfaceNets(p -> arm(p, s), v(sh.x - 0.07, 0.76, -0.09), v(sh.x + 0.09, 1.61, 0.06), 0.009);
			V palm = palm(s);
			m.append(surfaceNets(p -> palmShape(p, s), v(palm.x - 0.035, 0.66, palm.z - 0.045), v(palm.x + 0.035, 0.81, palm.z + 0.06), 0.0041), SKIN);
			return m;
		}

		/** Four very long knuckled fingers and a thumb, each a part of its own, curling toward the palm (the body side). */
		void addHand(Map<String, Mesh> parts, double s) {
			String prefix = s < 0 ? "right_" : "left_";
			this.elbow(s, elbow(s), wrist(s));
			V palm = palm(s);
			String[] roles = {"little", "ring", "middle", "index"};
			for (int f = 0; f < 4; f++) {
				double z = palm.z - 0.022 + f * 0.0145;
				this.addLongFinger(parts, prefix + roles[f], v(palm.x, palm.y - 0.035, z), v(s * 0.02, -1, 0.07), v(0, 0, 0.16), FINGER_LENGTH[f], 0.0074,
					v(-s, 0, 0));
			}
			this.addLongFinger(parts, prefix + "thumb", v(palm.x - s * 0.006, palm.y - 0.008, palm.z + 0.026), v(-s * 0.08, -0.85, 0.42), v(0, 0.02, 0.06),
				0.085, 0.0078, v(-s * 0.6, 0, -0.8));
		}

		void addLongFinger(Map<String, Mesh> parts, String name, V base, V dir, V curve, double length, double radius, V curl) {
			V[] tip = new V[2];
			finger(v(0, 0, 0), base, dir, curve, length, radius, tip);
			Mesh m = sdfFinger(p -> finger(p, base, dir, curve, length, radius, null), base, tip[0], radius + 0.012, 0.0041, SKIN);
			addCone(m, tip[0].sub(tip[1].mul(0.004)), tip[0].add(tip[1].mul(0.017)), 0.0052, 6, NAIL);
			this.addFinger(parts, name, finish(m, (p, n, mm, e) -> paint(p, n, mm, e, "arm")), new FingerSpec(base, tip[0], curl, false));
		}

		static Mesh legMesh(double s) {
			V hip = hip(s);
			Mesh m = surfaceNets(p -> leg(p, s), v(hip.x - 0.075, 0.04, -0.07), v(hip.x + 0.075, 1.1, 0.07), 0.011);
			List<V[]> tips = new ArrayList<>();
			m.append(surfaceNets(p -> foot(p, s, null), v(hip.x - 0.05, -0.002, -0.05), v(hip.x + 0.06, 0.1, 0.18), 0.0047), SKIN);
			foot(v(0, 0, 0), s, tips);
			for (V[] tip : tips) {
				addCone(m, tip[0].sub(tip[1].mul(0.004)), tip[0].add(tip[1].mul(0.016)), 0.0058, 6, NAIL);
			}
			return m;
		}

		/** The jaw hangs open a little even when quiet. */
		static void gape(Mesh m) {
			for (int i = 0; i < m.pos.size(); i++) {
				m.pos.set(i, rotX(m.pos.get(i).sub(JAW_PIVOT), GAPE).add(JAW_PIVOT));
				m.nrm.set(i, rotX(m.nrm.get(i), GAPE));
			}
		}

		static void addTeeth(Mesh m, boolean upper) {
			java.util.Random rnd = new java.util.Random(upper ? 3 : 4);
			int count = upper ? 12 : 11;
			for (int i = 0; i < count; i++) {
				double t = -0.88 + 1.76 * i / (count - 1);
				double x = t * CAVITY_R.x * 0.95;
				double z = CAVITY_C.z + CAVITY_R.z * Math.sqrt(Math.max(0, 1 - t * t)) * 0.9;
				double k = Math.max(0, 1 - sq(x / CAVITY_R.x) - sq((z - CAVITY_C.z) / CAVITY_R.z));
				double wall = CAVITY_C.y + (upper ? 1 : -1) * CAVITY_R.y * Math.sqrt(k);
				double length = (0.012 + rnd.nextDouble() * 0.014) * (1 - 0.3 * t * t);
				V root = v(x, wall + (upper ? 0.004 : -0.004), z);
				V tip = v(x + (rnd.nextDouble() - 0.5) * 0.004, wall + (upper ? -length : length), z - 0.002);
				addCone(m, root, tip, 0.0055, 6, TEETH);
			}
		}

		static double[] paint(V p, V n, int material, double extra, String part) {
			switch (material) {
				case EYE_M:
					return glossyBlack(n);
				case GLOW:
					return rgb(1.0, 0.92, 0.55);
				case TEETH:
					return rgb(0.86 - 0.2 * extra, 0.8 - 0.22 * extra, 0.58 - 0.2 * extra);
				default:
					break;
			}
			boolean face = part.equals("head") || part.equals("jaw");
			if (face && (cavity(p) < 0.006 || Math.abs(jawRegion(p)) < 0.006 && skull(p) < -0.0055)) {
				double back = clamp01((CAVITY_C.z + CAVITY_R.z - p.z) / 0.11);
				return rgb(0.30 - 0.22 * back, 0.07 - 0.04 * back, 0.05 - 0.03 * back);
			}
			if (material == NAIL) {
				return rgb(0.17 + 0.15 * extra, 0.12 + 0.12 * extra, 0.07 + 0.08 * extra); // dark tips
			}
			double ao = occlusion(SkinnyAlien::wholeBody, p, n);
			// mottled skin: big soft patches, smaller brown spots, fine grain
			double blotch = noise(p.mul(7)) * 0.5 + 0.5;
			double spots = smoothstep(0.35, 0.75, noise(p.mul(26).add(v(3.1, 0, 0))));
			double fine = noise(p.mul(55)) * 0.035;
			double r = lerp(0.86, 0.62, blotch * 0.65) + fine - 0.1 * spots;
			double g = lerp(0.7, 0.46, blotch * 0.65) + fine - 0.1 * spots;
			double b = lerp(0.4, 0.24, blotch * 0.65) + fine * 0.6 - 0.06 * spots;
			double k = 0.4 + 0.6 * ao;
			if (part.equals("body")) {
				k *= 1 - 0.32 * (1 - ribs(p)) * smoothstep(1.22, 1.27, p.y) * (1 - smoothstep(1.5, 1.54, p.y)) * smoothstep(-0.04, 0.04, p.z);
			}
			return rgb(r * k, g * k, b * k);
		}
	}

	// ================================================================== 67

	/**
	 * "67": the digits 6 and 7 built from blue toy bricks with studs, each with one big googly eye, blocky arms with
	 * white gloves and one leg under each digit. There is no mouth: the top bar of the 7 lifts like a lid when it talks.
	 * It is stored as "Blue", so players who picked the old blue mascot get this one.
	 */
	static final class SixSeven extends Design {
		/** The size of one brick. */
		static final double C = 0.06;
		/** Where the bottom of the digits is. */
		static final double BOTTOM = 0.5;
		/** Half the depth of the digits. */
		static final double DEPTH = C;
		/** Half the gap between the digits. */
		static final double GAP = 0.3 * C;
		static final String[] SIX = {
			"..####",
			".#####",
			"###...",
			"##....",
			"#####.",
			"######",
			"##..##",
			"##..##",
			"######",
			".####.",
		};
		static final String[] SEVEN = {
			"######",
			"######",
			"....##",
			"....##",
			"...##.",
			"...##.",
			"..##..",
			"..##..",
			"..##..",
			"..##..",
		};
		static final double SIX_LEFT = -GAP - 6 * C;
		static final double SEVEN_LEFT = GAP;
		/** The eyes, in bricks from the digit's bottom left corner. */
		static final V SIX_EYE = eyeAt(SIX_LEFT, 2.45, 7.35);
		static final V SEVEN_EYE = eyeAt(SEVEN_LEFT, 4.2, 6.55);
		static final V EYE_R = v(1.45 * C, 1.45 * C, 0.55 * C);
		/** The 7's top bar lifts around its back bottom edge. */
		static final V JAW_PIVOT = v(SEVEN_LEFT + 3 * C, BOTTOM + 8 * C, -DEPTH);
		static final V RIGHT_SHOULDER = v(SIX_LEFT - 0.012, BOTTOM + 6.5 * C, 0);
		static final V LEFT_SHOULDER = v(SEVEN_LEFT + 6 * C + 0.012, BOTTOM + 6.5 * C, 0);
		static final int BRICK = 0, SEAM = 1, STUD = 2, EYE_WHITE = 3, PUPIL = 4, RIM = 5, UPPER_ARM = 6, CUFF = 7, GLOVE = 8, SHOE = 9, SOLE = 10,
			BAND = 11, LACE = 12;

		static V eyeAt(double left, double i, double j) {
			return v(left + i * C, BOTTOM + j * C, DEPTH + 0.15 * C);
		}

		@Override
		String name() {
			return "Blue";
		}

		@Override
		String displayName() {
			return "67";
		}

		@Override
		double height() {
			return 1.2;
		}

		@Override
		double headTurn() {
			return 0;
		}

		@Override
		double bounce() {
			return 0.05;
		}

		@Override
		V iconEye() {
			return v(0.0, 0.88, 1.35);
		}

		@Override
		V iconTarget() {
			return v(0, 0.82, 0);
		}

		@Override
		double iconFov() {
			return 1.0;
		}

		@Override
		Rig rig() {
			return new Rig(-30, v(0, BOTTOM, 0), JAW_PIVOT, v(0, BOTTOM, 0),
				RIGHT_SHOULDER, v(RIGHT_SHOULDER.x - 0.06, BOTTOM - 0.06, 0.02), LEFT_SHOULDER, v(LEFT_SHOULDER.x + 0.06, BOTTOM - 0.06, 0.02),
				v(SIX_LEFT + 3 * C, BOTTOM, 0), v(SEVEN_LEFT + 3 * C, BOTTOM, 0), v(0, BOTTOM + 2 * C, -DEPTH - 0.01));
		}

		@Override
		Map<String, Mesh> build() {
			Map<String, Mesh> parts = new LinkedHashMap<>();
			// The digits are the head: it doesn't turn, so the 7's lid stays on the 7 when crouching or bending.
			Mesh head = new Mesh();
			addDigit(head, SIX, SIX_LEFT, 0, SIX.length, SIX_EYE);
			addDigit(head, SEVEN, SEVEN_LEFT, 2, SEVEN.length, SEVEN_EYE);
			addEye(head, SIX_EYE, 0.06);
			addEye(head, SEVEN_EYE, -0.06);
			parts.put("head", finish(head, this::paint));

			Mesh jaw = new Mesh();
			addDigit(jaw, SEVEN, SEVEN_LEFT, 0, 2, SEVEN_EYE);
			parts.put("jaw", finish(jaw, this::paint));

			for (int side = -1; side <= 1; side += 2) {
				double s = side;
				parts.put(side < 0 ? "right_arm" : "left_arm", finish(arm(s), this::paint));
				this.addHand(parts, s);
				parts.put(side < 0 ? "right_leg" : "left_leg", finish(leg(s), this::paint));
			}

			// Angry: blue lids slide down over the eyes, under thick dark brows.
			Mesh mad = new Mesh();
			for (int k = 0; k < 2; k++) {
				V eye = k == 0 ? SIX_EYE : SEVEN_EYE;
				double s = k == 0 ? -1 : 1;
				mad.append(eyelid(eye, EYE_R, s, 0.1, 24, 0.1), BRICK);
				double tilt = Math.toRadians(s * 24); // the inner end lower
				V along = v(Math.cos(tilt), Math.sin(tilt), 0);
				addBevelBox(mad, eye.add(v(s * -0.004, EYE_R.y + 0.032, 0.012)), along, v(-along.y, along.x, 0), v(0, 0, 1), v(EYE_R.x * 1.05, 0.018, 0.02),
					0.005, RIM, RIM);
			}
			parts.put("mad_head", finish(mad, this::paint));
			parts.put("blink_head", finish(blinkLids(new V[] {SIX_EYE, SEVEN_EYE}, EYE_R, 0.2), (p, n, m, e) -> paint(p, n, BRICK, e)));
			return parts;
		}

		static boolean filled(String[] rows, int i, int r) {
			return r >= 0 && r < rows.length && i >= 0 && i < rows[r].length() && rows[r].charAt(i) == '#';
		}

		/**
		 * Rows {@code from} to {@code to} (counted from the top) of a digit: a beveled brick per filled cell, a stud on its
		 * front unless the eye covers it, and studs on top where nothing is above.
		 */
		static void addDigit(Mesh m, String[] rows, double left, int from, int to, V eye) {
			for (int r = from; r < to; r++) {
				int j = rows.length - 1 - r;
				for (int i = 0; i < rows[r].length(); i++) {
					if (!filled(rows, i, r)) {
						continue;
					}
					V c = v(left + (i + 0.5) * C, BOTTOM + (j + 0.5) * C, 0);
					addBevelBox(m, c, v(1, 0, 0), v(0, 1, 0), v(0, 0, 1), v(C / 2, C / 2, DEPTH), C * 0.09, BRICK, SEAM);
					double toEye = v((c.x - eye.x) / (EYE_R.x + 0.2 * C), (c.y - eye.y) / (EYE_R.y + 0.2 * C), 0).len();
					if (toEye > 1.0) {
						addStud(m, c.add(v(0, 0, DEPTH)), v(0, 0, 1), STUD);
					}
					if (!filled(rows, i, r - 1)) {
						for (int k = -1; k <= 1; k += 2) {
							addStud(m, c.add(v(0, C / 2, k * DEPTH / 2)), v(0, 1, 0), STUD);
						}
					}
				}
			}
		}

		static void addStud(Mesh m, V base, V axis, int material) {
			addCylinder(m, base.sub(axis.mul(0.004)), axis, 0.3 * C, 0.19 * C + 0.004, 12, material);
		}

		/** A googly eye stuck on the front: white, with a dark blue rim and a black pupil looking a little to one side. */
		static void addEye(Mesh m, V eye, double lookX) {
			addEllipsoid(m, eye, EYE_R, EYE_WHITE);
			addTorus(m, eye, v(0, 0, 1), EYE_R.x * 1.02, 0.2 * C, 28, 8, RIM);
			V look = v(lookX, -0.08, 1);
			addEllipsoidCap(m, eye, EYE_R, look, 30, 1.006, PUPIL);
			V d = v(look.x / EYE_R.x, look.y / EYE_R.y, look.z / EYE_R.z).norm();
			V center = eye.add(v(d.x * EYE_R.x, d.y * EYE_R.y, d.z * EYE_R.z).mul(1.01));
			addEllipsoid(m, center.add(v(-0.018, 0.019, 0.004)), v(0.011, 0.012, 0.004), EYE_WHITE);
			addEllipsoid(m, center.add(v(0.014, -0.012, 0.003)), v(0.005, 0.005, 0.002), EYE_WHITE);
		}

		/** A blocky arm hanging from the side of its digit: dark blue upper arm, blue forearm, a light cuff and a white glove. */
		static Mesh arm(double s) {
			Mesh m = new Mesh();
			V x = v(1, 0, 0);
			V y = v(0, 1, 0);
			V z = v(0, 0, 1);
			V shoulder = s < 0 ? RIGHT_SHOULDER : LEFT_SHOULDER;
			double ax = shoulder.x + s * 0.045;
			addBevelBox(m, v(ax, shoulder.y - 0.005, 0), x, y, z, v(0.048, 0.045, 0.045), 0.008, UPPER_ARM, UPPER_ARM);
			addBevelBox(m, v(ax + s * 0.004, shoulder.y - 0.12, 0), x, y, z, v(0.036, 0.085, 0.036), 0.007, UPPER_ARM, UPPER_ARM);
			addBevelBox(m, v(ax + s * 0.008, shoulder.y - 0.215, 0.004), x, y, z, v(0.04, 0.016, 0.04), 0.006, BRICK, SEAM); // elbow
			addBevelBox(m, v(ax + s * 0.01, shoulder.y - 0.3, 0.008), x, y, z, v(0.034, 0.075, 0.034), 0.007, BRICK, SEAM);
			addBevelBox(m, v(ax + s * 0.012, shoulder.y - 0.385, 0.01), x, y, z, v(0.046, 0.024, 0.046), 0.008, CUFF, CUFF);
			addBevelBox(m, palm(s), x, y, z, v(0.034, 0.045, 0.046), 0.009, GLOVE, GLOVE);
			return m;
		}

		static V palm(double s) {
			V shoulder = s < 0 ? RIGHT_SHOULDER : LEFT_SHOULDER;
			return v(shoulder.x + s * 0.057, shoulder.y - 0.45, 0.014);
		}

		/**
		 * The glove's four square fingers and its thumb, each made of two blocks that bend sharply at the knuckle like a
		 * toy's, and curl toward the palm (the body side).
		 */
		void addHand(Map<String, Mesh> parts, double s) {
			V x = v(1, 0, 0);
			V y = v(0, 1, 0);
			V z = v(0, 0, 1);
			V shoulder = s < 0 ? RIGHT_SHOULDER : LEFT_SHOULDER;
			double ax = shoulder.x + s * 0.045;
			this.elbow(s, v(ax + s * 0.008, shoulder.y - 0.215, 0.004), v(ax + s * 0.012, shoulder.y - 0.385, 0.01));
			String prefix = s < 0 ? "right_" : "left_";
			V palm = palm(s);
			String[] roles = {"little", "ring", "middle", "index"};
			for (int f = 0; f < 4; f++) {
				double fz = palm.z + (f - 1.5) * 0.023;
				double fx = palm.x + s * 0.004;
				Mesh finger = new Mesh();
				addBevelBox(finger, v(fx, palm.y - 0.0555, fz), x, y, z, v(0.026, 0.0145, 0.0098), 0.004, GLOVE, GLOVE);
				addBevelBox(finger, v(fx, palm.y - 0.0805, fz), x, y, z, v(0.026, 0.0135, 0.0098), 0.004, GLOVE, GLOVE);
				this.addFinger(parts, prefix + roles[f], finish(finger, this::paint), new FingerSpec(v(fx, palm.y - 0.042, fz), v(fx, palm.y - 0.094, fz),
					v(-s, 0, 0), true, new double[] {0, 0.5}, new double[] {85, 85}));
			}
			Mesh thumb = new Mesh();
			double tx = palm.x - s * 0.03;
			double tz = palm.z + 0.034;
			addBevelBox(thumb, v(tx, palm.y + 0.009, tz), x, y, z, v(0.012, 0.016, 0.012), 0.004, GLOVE, GLOVE);
			addBevelBox(thumb, v(tx, palm.y - 0.02, tz), x, y, z, v(0.012, 0.015, 0.012), 0.004, GLOVE, GLOVE);
			this.addFinger(parts, prefix + "thumb", finish(thumb, this::paint), new FingerSpec(v(tx, palm.y + 0.024, tz), v(tx, palm.y - 0.036, tz),
				v(-s * 0.5, 0, -1), true, new double[] {0, 0.5}, new double[] {35, 60}));
		}

		/** A blocky blue leg with a dark knee band, and a light gray-blue shoe on a white sole. */
		static Mesh leg(double s) {
			Mesh m = new Mesh();
			V x = v(1, 0, 0);
			V y = v(0, 1, 0);
			V z = v(0, 0, 1);
			double lx = s < 0 ? SIX_LEFT + 3 * C : SEVEN_LEFT + 3 * C;
			addBevelBox(m, v(lx, 0.43, 0), x, y, z, v(0.06, 0.07, 0.055), 0.008, BRICK, SEAM); // hip block under the digit
			addBevelBox(m, v(lx, 0.25, 0), x, y, z, v(0.048, 0.13, 0.048), 0.008, BRICK, SEAM);
			addBevelBox(m, v(lx, 0.27, 0), x, y, z, v(0.054, 0.024, 0.054), 0.007, BAND, BAND); // knee band
			addBevelBox(m, v(lx, 0.082, 0.036), x, y, z, v(0.07, 0.048, 0.112), 0.012, SHOE, SHOE);
			addBevelBox(m, v(lx, 0.126, -0.01), x, y, z, v(0.062, 0.03, 0.062), 0.01, SHOE, SHOE); // ankle
			addBevelBox(m, v(lx, 0.017, 0.038), x, y, z, v(0.076, 0.017, 0.12), 0.006, SOLE, SOLE);
			for (int k = 0; k < 3; k++) { // laces
				addBevelBox(m, v(lx, 0.131, 0.075 + k * 0.026), x, y, z, v(0.04, 0.005, 0.006), 0.002, LACE, LACE);
			}
			return m;
		}

		double[] paint(V p, V n, int material, double extra) {
			double grain = noise(p.mul(45)) * 0.012;
			switch (material) {
				case BRICK:
					return Math.abs(n.z) > 0.7 || n.y > 0.7 ? rgb(0.12 + grain, 0.44 + grain, 0.95) : rgb(0.06 + grain, 0.27 + grain, 0.74);
				case STUD: {
					double top = Math.max(Math.abs(n.z), n.y);
					return top > 0.7 ? rgb(0.17 + grain, 0.51 + grain, 0.99) : rgb(0.09 + grain, 0.36 + grain, 0.86);
				}
				case SEAM:
				case RIM:
					return rgb(0.04, 0.15, 0.47);
				case UPPER_ARM:
				case BAND:
					return rgb(0.06 + grain, 0.22 + grain, 0.62);
				case EYE_WHITE:
					return rgb(0.98, 0.98, 0.98);
				case PUPIL:
					return glossyBlack(n);
				case CUFF:
				case SHOE:
					return rgb(0.72 + grain, 0.78 + grain, 0.86 + grain);
				case GLOVE:
					return rgb(0.99, 0.94 + grain, 0.93 + grain);
				case SOLE:
					return rgb(0.97, 0.97, 0.96);
				case LACE:
					return rgb(0.96, 0.96, 0.98);
				default:
					return rgb(1, 0, 1);
			}
		}
	}

	// ================================================================== White Queen

	/**
	 * The white queen of a chess set: a polished ivory Staunton piece standing on a green felt pad, with a ringed base, a
	 * fluted stem, a collar, a flared cup with an elegant face (lashes, blue eyes, red lips), a crown of eight balled
	 * points and a ball on top. Slender ivory arms hold a black sniper rifle with an olive stock, a big scope with a glowing
	 * lens, a muzzle brake and a folded bipod, aimed wherever she looks. She has no legs: she hops along like a piece
	 * being moved. The cup and crown are her head, and her chin drops open when she talks.
	 */
	static final class WhiteQueen extends Design {
		static final V HEAD_PIVOT = v(0, 0.862, 0);
		static final double MOUTH_Y = 1.137;
		static final V JAW_PIVOT = v(0, MOUTH_Y, 0.03);
		static final V CAVITY_C = v(0, 1.128, 0.09);
		static final V CAVITY_R = v(0.05, 0.026, 0.055);
		static final V EYE = v(0.061, 1.292, 0.138);
		static final V EYE_R = v(0.034, 0.022, 0.021);
		static final double EYE_TILT = Math.toRadians(9);
		static final V RIGHT_SHOULDER = v(-0.152, 1.03, 0.0);
		static final V LEFT_SHOULDER = v(0.152, 1.03, 0.0);
		static final V RIGHT_HAND = v(-0.17, 0.484, 0.056);
		static final V LEFT_HAND = v(0.197, 0.47, 0.05);
		/**
		 * The rifle is held by its pistol grip, along the arm: the mod's rifle pose points the arm where the player looks,
		 * so the rifle does too. u runs from the stock to the muzzle, w toward the scope, x to the character's left; the
		 * grip is at u = U_GRIP, w = W_GRIP.
		 */
		static final double RIFLE_TILT = 0;
		static final V GRIP_POINT = v(-0.152, 0.482, 0.058);
		static final double U_GRIP = -0.12;
		static final double W_GRIP = -0.062;
		static final V U = v(0, -Math.cos(RIFLE_TILT), Math.sin(RIFLE_TILT));
		static final V W = v(0, Math.sin(RIFLE_TILT), Math.cos(RIFLE_TILT));
		static final V X = v(1, 0, 0);
		static final int IVORY = 0, CARVE = 1, FELT = 2, EYE_WHITE = 3, IRIS = 4, PUPIL = 5, LASH = 6, BROW = 7, LIP = 8, MOUTH = 9, TEETH = 10,
			GUN = 11, GUN_EDGE = 12, STOCK = 13, GRIP = 14, LENS = 15, GLASS = 16, GLINT = 17, NAIL = 18;

		@Override
		String name() {
			return "White Queen";
		}

		@Override
		double height() {
			return 1.95;
		}

		@Override
		double bounce() {
			return 0.09;
		}

		@Override
		double waddle() {
			return 6;
		}

		@Override
		double headTurn() {
			return 0.6;
		}

		@Override
		String arms() {
			return "rifle";
		}

		@Override
		boolean hideHeldItems() {
			return true;
		}

		@Override
		V iconEye() {
			return v(0.02, 1.3, 0.92);
		}

		@Override
		V iconTarget() {
			return v(0, 1.27, 0);
		}

		@Override
		double iconFov() {
			return 0.75;
		}

		@Override
		Rig rig() {
			return new Rig(22, HEAD_PIVOT, JAW_PIVOT, v(0, 0.5, 0), RIGHT_SHOULDER, RIGHT_HAND, LEFT_SHOULDER, LEFT_HAND, v(-0.1, 0.3, 0), v(0.1, 0.3, 0),
				v(0, 0.42, -0.16));
		}

		// ------------------------------------------------------------------ shapes

		/** A turned shape: at each height {@code y} between y0 and y1 it is a circle of radius {@code radius(y)}. */
		static double lathe(V p, double y0, double y1, java.util.function.DoubleUnaryOperator radius) {
			double y = clamp(p.y, y0, y1);
			double r = radius.applyAsDouble(y);
			double slope = (radius.applyAsDouble(y + 0.002) - radius.applyAsDouble(y - 0.002)) / 0.004;
			double side = (Math.hypot(p.x, p.z) - r) / Math.sqrt(1 + slope * slope);
			return Math.max(side, Math.max(y0 - p.y, p.y - y1));
		}

		static double torusY(V p, double y, double major, double minor) {
			return Math.hypot(Math.hypot(p.x, p.z) - major, p.y - y) - minor;
		}

		/** The fluted stem: wide at the bottom, curving in to a slim neck. */
		static double stemRadius(double y) {
			double t = clamp01((y - 0.22) / 0.57);
			return 0.1 + 0.1 * Math.pow(1 - t, 2.3);
		}

		/** The cup that holds the crown, flaring out toward the top. */
		static double cupRadius(double y) {
			double t = clamp01((y - 0.85) / 0.65);
			return 0.094 + 0.132 * Math.pow(t, 1.65);
		}

		static double body(V p) {
			double d = roundCylinderY(p, v(0, 0.012, 0), 0.288, 0.012, 0.005); // felt pad
			d = smin(d, roundCylinderY(p, v(0, 0.045, 0), 0.298, 0.024, 0.014), 0.008);
			d = smin(d, torusY(p, 0.06, 0.285, 0.036), 0.01);
			d = smin(d, lathe(p, 0.08, 0.17, y -> 0.258 - 0.03 * Math.sin(Math.PI * (y - 0.08) / 0.09)), 0.012); // cove
			d = smin(d, torusY(p, 0.168, 0.252, 0.022), 0.008);
			d = smin(d, roundCylinderY(p, v(0, 0.19, 0), 0.232, 0.018, 0.008), 0.006);
			d = smin(d, torusY(p, 0.214, 0.212, 0.016), 0.006);
			double angle = Math.atan2(p.x, p.z);
			double flutes = 0.0032 * Math.cos(16 * angle) * smoothstep(0.24, 0.32, p.y) * (1 - smoothstep(0.62, 0.72, p.y));
			d = smin(d, lathe(p, 0.21, 0.81, WhiteQueen::stemRadius) + flutes, 0.02);
			d = smin(d, torusY(p, 0.792, 0.108, 0.017), 0.008); // the collar
			d = smin(d, roundCylinderY(p, v(0, 0.824, 0), 0.182, 0.016, 0.011), 0.008);
			d = smin(d, torusY(p, 0.85, 0.104, 0.014), 0.006);
			return d;
		}

		static double crown(V p) {
			double d = torusY(p, 1.505, 0.214, 0.026);
			d = smin(d, ellipsoid(p, v(0, 1.53, 0), v(0.19, 0.105, 0.19)), 0.02); // the dome inside the points
			d = smin(d, lathe(p, 1.49, 1.56, y -> 0.212 + 0.02 * (y - 1.49) / 0.07), 0.01); // the band
			for (int k = 0; k < 8; k++) {
				double a = Math.toRadians(45 * k);
				V base = v(0.208 * Math.sin(a), 1.52, 0.208 * Math.cos(a));
				V tip = v(0.244 * Math.sin(a), 1.632, 0.244 * Math.cos(a));
				d = smin(d, roundCone(p, base, tip, 0.042, 0.017), 0.016);
				d = smin(d, SkinnyAlien.sphere(p, tip.add(v(0.01 * Math.sin(a), 0.016, 0.01 * Math.cos(a))), 0.026), 0.01);
			}
			d = smin(d, roundCone(p, v(0, 1.61, 0), v(0, 1.705, 0), 0.052, 0.034), 0.02);
			d = smin(d, torusY(p, 1.703, 0.047, 0.016), 0.006);
			d = smin(d, SkinnyAlien.sphere(p, v(0, 1.782, 0), 0.07), 0.012);
			d = smin(d, roundCone(p, v(0, 1.842, 0), v(0, 1.88, 0), 0.022, 0.014), 0.008);
			d = smin(d, SkinnyAlien.sphere(p, v(0, 1.895, 0), 0.023), 0.006);
			return d;
		}

		/** The cup with the face's shapes (sockets, nose, cheeks) and the crown, as one smooth piece. */
		static double headShape(V p) {
			double d = lathe(p, 0.85, 1.51, WhiteQueen::cupRadius);
			for (int s = -1; s <= 1; s += 2) {
				d = smin(d, ellipsoid(p, v(s * 0.075, 1.205, 0.112), v(0.04, 0.035, 0.03)), 0.03); // cheeks
				d = smax(d, -eyeShape(p.withX(p.x * s), 1.12, -0.004), 0.012); // sockets
			}
			d = smin(d, ellipsoid(p, v(0, 1.222, 0.14), v(0.012, 0.03, 0.018)), 0.016); // the nose
			d = smin(d, ellipsoid(p, v(0, 1.198, 0.148), v(0.013, 0.01, 0.01)), 0.008);
			d = smin(d, ellipsoid(p, v(0, 1.07, 0.098), v(0.04, 0.03, 0.03)), 0.03); // the chin
			return smin(d, crown(p), 0.02);
		}

		static double eyeShape(V p, double grow, double pushBack) {
			V q = rotZ(p.sub(EYE.add(v(0, 0, -pushBack))), -EYE_TILT);
			return ellipsoid(q, v(0, 0, 0), EYE_R.mul(grow));
		}

		static double cutPlane(V p) {
			double cutY = MOUTH_Y + (0.13 - p.z) * 0.08;
			return p.y - cutY;
		}

		static double jawRegion(V p) {
			return Math.max(cutPlane(p), ellipsoid(p, v(0, 1.075, 0.13), v(0.052, 0.075, 0.09)));
		}

		static double cavity(V p) {
			return ellipsoid(p, CAVITY_C, CAVITY_R);
		}

		static double head(V p) {
			return smax(smax(smax(headShape(p), -jawRegion(p), 0.003), -cavity(p), 0.004), HEAD_PIVOT.y - 0.008 - p.y, 0.004);
		}

		static double jaw(V p) {
			return smax(smax(headShape(p), jawRegion(p), 0.003), -cavity(p), 0.004);
		}

		/** The body reaches a little into the cup, so turning the head never opens a gap. */
		static double bodyPart(V p) {
			return smax(smin(body(p), lathe(p, 0.84, 0.9, WhiteQueen::cupRadius), 0.01), p.y - 0.9, 0.006);
		}

		// ------------------------------------------------------------------ arms

		static double arm(V p, double s) {
			V sh = s < 0 ? RIGHT_SHOULDER : LEFT_SHOULDER;
			V el = v(s * 0.183, 0.77, 0.012);
			V wr = v(s * 0.194, 0.532, 0.042);
			double d = SkinnyAlien.sphere(p, sh, 0.04);
			d = smin(d, ellipsoid(p, sh.add(v(s * 0.012, -0.03, 0)), v(0.056, 0.062, 0.054)), 0.02); // puffed sleeve
			d = smin(d, roundCone(p, sh.add(v(s * 0.01, -0.06, 0)), el, 0.033, 0.026), 0.025);
			d = smin(d, SkinnyAlien.sphere(p, el, 0.027), 0.012);
			d = smin(d, roundCone(p, el, wr, 0.026, 0.019), 0.015);
			d = smin(d, ellipsoid(p, SkinnyAlien.between(el, wr, 0.3), v(0.024, 0.07, 0.024)), 0.02);
			return smax(d, wr.y - 0.02 - p.y, 0.008);
		}

		/** The sleeve's carved ridges, and the bracelet. */
		static double sleeveRidges(V p, double s) {
			V sh = s < 0 ? RIGHT_SHOULDER : LEFT_SHOULDER;
			return 0.5 + 0.5 * Math.cos(Math.atan2(p.z - sh.z, (p.x - sh.x) * s) * 9);
		}

		/** A right hand closed around the rifle's grip, the index finger along the trigger guard (toward the muzzle). */
		static double gripHand(V p, List<V[]> tips) {
			V palm = v(-0.174, 0.49, 0.052);
			double d = ellipsoid(p, palm, v(0.021, 0.034, 0.03));
			d = smin(d, SkinnyAlien.sphere(p, v(-0.19, 0.528, 0.045), 0.02), 0.015);
			for (int f = 0; f < 3; f++) { // middle, ring and little finger wrapped around the grip
				double y = 0.478 - f * 0.014;
				V root = v(-0.158, y, 0.074);
				V[] tip = new V[2];
				d = smin(d, SkinnyAlien.finger(p, root, v(0.6, -0.05, 0.6), v(0.25, 0, -0.55), 0.05 - f * 0.005, 0.0072 - f * 0.0006, tip), 0.004);
				if (tips != null) {
					tips.add(tip);
				}
			}
			V[] index = new V[2];
			d = smin(d, SkinnyAlien.finger(p, v(-0.16, 0.476, 0.088), v(0.04, -0.94, 0.34), v(0, 0, 0.02), 0.06, 0.0072, index), 0.004);
			V[] thumb = new V[2];
			d = smin(d, SkinnyAlien.finger(p, v(-0.19, 0.5, 0.066), v(0.55, -0.4, 0.75), v(0.15, 0, 0), 0.045, 0.0078, thumb), 0.005);
			if (tips != null) {
				tips.add(index);
				tips.add(thumb);
			}
			return d;
		}

		/** The palm of the open (left) hand, with knuckles where its fingers grow out (they are parts of their own). */
		static double openPalm(V p, double s) {
			V palm = v(s * 0.198, 0.478, 0.048);
			double d = ellipsoid(p, palm, v(0.016, 0.034, 0.026));
			d = smin(d, SkinnyAlien.sphere(p, v(s * 0.196, 0.522, 0.042), 0.019), 0.015);
			for (int f = -1; f <= 2; f++) {
				d = smin(d, SkinnyAlien.sphere(p, v(s * 0.199, 0.448, 0.048 + f * 0.0115 - 0.006), 0.0072), 0.004);
			}
			return smin(d, SkinnyAlien.sphere(p, v(s * 0.19, 0.49, 0.072), 0.0075), 0.005);
		}

		/**
		 * The left hand's four fingers and thumb, a little curled toward the front, each a part of its own that curls toward
		 * the palm (the body side), with a pink nail. {@code prefix} is "left_" or "fp_left_".
		 */
		void addOpenHand(Map<String, Mesh> parts, String prefix, boolean detailed) {
			double s = 1;
			String[] roles = {"little", "ring", "middle", "index"};
			for (int f = -1; f <= 2; f++) {
				V root = v(s * 0.199, 0.448, 0.048 + f * 0.0115 - 0.006);
				this.addQueenFinger(parts, prefix, roles[f + 1], root, v(s * 0.05, -1, 0.06), v(0, 0, 0.09), 0.072 - Math.abs(f - 0.5) * 0.008, 0.0062,
					v(-s, 0, 0), detailed);
			}
			this.addQueenFinger(parts, prefix, "thumb", v(s * 0.19, 0.49, 0.072), v(-s * 0.2, -0.7, 0.6), v(0, 0, 0.05), 0.045, 0.0068, v(-s * 0.6, 0, -0.8),
				detailed);
		}

		void addQueenFinger(Map<String, Mesh> parts, String prefix, String role, V base, V dir, V curve, double length, double radius, V curl,
			boolean detailed) {
			V[] tip = new V[2];
			SkinnyAlien.finger(v(0, 0, 0), base, dir, curve, length, radius, tip);
			Mesh m = sdfFinger(p -> SkinnyAlien.finger(p, base, dir, curve, length, radius, null), base, tip[0], radius + 0.01, detailed ? 0.0031 : 0.0042,
				IVORY);
			addCone(m, tip[0].sub(tip[1].mul(0.003)), tip[0].add(tip[1].mul(0.011)), 0.0048, 6, NAIL);
			Mesh finished = finish(m, this::paint);
			if (prefix.startsWith("fp_")) {
				parts.put(prefix + role, finished);
			} else {
				this.addFinger(parts, prefix + role, finished, new FingerSpec(base, tip[0], curl, false));
			}
		}

		/** A fist with the middle finger pointing forward (+Z), which is up once the arm is raised. */
		static double fist(V p, List<V[]> tips) {
			V palm = v(-0.195, 0.482, 0.05);
			double d = ellipsoid(p, palm, v(0.021, 0.034, 0.03));
			d = smin(d, SkinnyAlien.sphere(p, v(-0.195, 0.525, 0.044), 0.02), 0.015);
			for (int f = -1; f <= 2; f++) {
				V knuckle = v(-0.195 + f * 0.0105 - 0.005, 0.455, 0.074);
				d = smin(d, SkinnyAlien.sphere(p, knuckle, 0.0082), 0.004);
				if (f == 0) {
					V[] tip = new V[2];
					d = smin(d, SkinnyAlien.finger(p, knuckle, v(0, 0.05, 1), v(0, 0.01, 0), 0.075, 0.0072, tip), 0.004);
					if (tips != null) {
						tips.add(tip);
					}
				} else {
					d = smin(d, roundCone(p, knuckle, knuckle.add(v(0, -0.016, 0.008)), 0.0075, 0.0068), 0.003);
					d = smin(d, roundCone(p, knuckle.add(v(0, -0.016, 0.008)), knuckle.add(v(0, -0.024, -0.012)), 0.0068, 0.006), 0.003);
				}
			}
			return smin(d, roundCone(p, v(-0.213, 0.49, 0.068), v(-0.2, 0.468, 0.08), 0.0075, 0.0068), 0.005);
		}

		Mesh buildArm(double s, boolean middleFinger, boolean detailed) {
			double h = detailed ? 0.0058 : 0.0088;
			V sh = s < 0 ? RIGHT_SHOULDER : LEFT_SHOULDER;
			Mesh m = surfaceNets(p -> arm(p, s), v(sh.x + s * 0.06 - 0.1, 0.5, -0.08), v(sh.x + s * 0.06 + 0.1, 1.1, 0.09), h);
			List<V[]> tips = new ArrayList<>();
			Sdf hand;
			if (middleFinger) {
				hand = p -> fist(p, null);
				fist(v(0, 0, 0), tips);
			} else if (s < 0) {
				hand = p -> gripHand(p, null);
				gripHand(v(0, 0, 0), tips);
			} else {
				hand = p -> openPalm(p, s);
			}
			m.append(surfaceNets(hand, v(s * 0.195 - 0.05, 0.36, -0.01), v(s * 0.195 + 0.05, 0.56, 0.19), detailed ? 0.0031 : 0.0042), IVORY);
			for (V[] tip : tips) {
				addCone(m, tip[0].sub(tip[1].mul(0.003)), tip[0].add(tip[1].mul(0.011)), 0.0048, 6, NAIL);
			}
			// A pearl bracelet at the wrist.
			V wrist = v(s * 0.194, 0.538, 0.043);
			int pearls = detailed ? 18 : 12;
			for (int k = 0; k < pearls; k++) {
				double a = 2 * Math.PI * k / pearls;
				addSphere(m, wrist.add(v(Math.cos(a) * 0.024, 0.004 * Math.sin(3 * a), Math.sin(a) * 0.024)), 0.0065, CARVE);
			}
			return m;
		}

		/** The sniper rifle, held in the right hand: a part of its own, so it stays in the hand when the elbow bends. */
		Mesh buildRifle(boolean detailed) {
			Mesh m = new Mesh();
			addRifle(m, detailed);
			return m;
		}

		// ------------------------------------------------------------------ the rifle

		/** A point on the rifle: u toward the muzzle, w toward the scope, x to the character's left. */
		static V rifle(double u, double w, double x) {
			return GRIP_POINT.add(U.mul((u - U_GRIP) * RIFLE_SCALE)).add(W.mul((w - W_GRIP) * RIFLE_SCALE)).add(X.mul(x * RIFLE_SCALE));
		}

		/** The rifle is drawn this much bigger than its numbers say. */
		static final double RIFLE_SCALE = 1.25;

		/** A slightly domed round glass facing {@code facing}. */
		static void addLens(Mesh m, V center, double radius, V facing, int material) {
			V f = facing.norm();
			V a = (Math.abs(f.y) < 0.9 ? v(0, 1, 0) : v(1, 0, 0)).cross(f).norm();
			V b = f.cross(a);
			int seg = 20;
			int base = m.pos.size();
			for (int ring = 0; ring <= 3; ring++) {
				double r = radius * ring / 3;
				double bulge = 0.004 * (1 - sq(ring / 3.0));
				for (int k = 0; k <= seg; k++) {
					double t = 2 * Math.PI * k / seg;
					V p = center.add(a.mul(Math.cos(t) * r)).add(b.mul(Math.sin(t) * r)).add(f.mul(bulge));
					m.add(p, f.add(a.mul(Math.cos(t) * ring * 0.15)).add(b.mul(Math.sin(t) * ring * 0.15)).norm(), material, 0);
				}
			}
			gridQuads(m, base, 3, seg);
		}

		static void rifleBox(Mesh m, double u, double w, double x, double hu, double hw, double hx, double bevel, int material, int edge) {
			double k = RIFLE_SCALE;
			addBevelBox(m, rifle(u, w, x), X, W, U, v(hx * k, hw * k, hu * k), bevel * k, material, edge);
		}

		static void addRifle(Mesh m, boolean detailed) {
			int seg = detailed ? 20 : 12;
			// Stock: butt pad, the stock, a raised cheek rest and the toe under the butt.
			rifleBox(m, -0.392, -0.006, 0, 0.012, 0.056, 0.022, 0.006, GRIP, GRIP);
			rifleBox(m, -0.27, -0.004, 0, 0.11, 0.036, 0.02, 0.009, STOCK, STOCK);
			rifleBox(m, -0.28, 0.043, 0, 0.075, 0.013, 0.017, 0.006, STOCK, STOCK);
			rifleBox(m, -0.35, -0.045, 0, 0.035, 0.016, 0.019, 0.007, STOCK, STOCK);
			rifleBox(m, -0.15, -0.006, 0, 0.03, 0.03, 0.019, 0.008, STOCK, STOCK); // the wrist of the stock
			// Pistol grip, trigger guard and trigger.
			addTube(m, path(rifle(-0.105, -0.022, 0), rifle(-0.118, -0.06, 0), rifle(-0.134, -0.102, 0)), radii(0.0165 * RIFLE_SCALE, 0.0158 * RIFLE_SCALE, 0.0152 * RIFLE_SCALE), seg, false, GRIP, GRIP);
			addTorus(m, rifle(-0.07, -0.042, 0), X, 0.021 * RIFLE_SCALE, 0.0035 * RIFLE_SCALE, seg, 6, GUN);
			rifleBox(m, -0.078, -0.03, 0, 0.004, 0.012, 0.003, 0.0015, GUN_EDGE, GUN_EDGE);
			// Receiver, bolt and bolt handle, magazine, and the rail on top.
			rifleBox(m, -0.02, 0.012, 0, 0.09, 0.03, 0.024, 0.006, GUN, GUN_EDGE);
			addCylinder(m, rifle(-0.075, 0.026, -0.027), U, 0.0095 * RIFLE_SCALE, 0.07 * RIFLE_SCALE, seg, GUN_EDGE);
			addTube(m, path(rifle(-0.06, 0.02, -0.028), rifle(-0.064, 0.008, -0.05), rifle(-0.068, -0.002, -0.068)), radii(0.0056, 0.0056, 0.0056), 8, false,
				GUN_EDGE, GUN_EDGE);
			addSphere(m, rifle(-0.068, -0.004, -0.072), 0.013, GUN);
			rifleBox(m, 0.0, -0.047, 0, 0.028, 0.03, 0.018, 0.004, GUN, GUN_EDGE);
			rifleBox(m, -0.01, 0.047, 0, 0.075, 0.0055, 0.013, 0.002, GUN_EDGE, GUN_EDGE);
			for (int k = 0; k < (detailed ? 12 : 6); k++) { // notches along the rail
				double u = -0.075 + (k + 0.5) * 0.15 / (detailed ? 12 : 6);
				rifleBox(m, u, 0.054, 0, 0.0035, 0.002, 0.0138, 0.0008, GUN, GUN);
			}
			// The scope: a long tube with a wide lens at the front and turrets on top and on the side, in two rings.
			double sw = 0.087;
			addTube(m, path(rifle(-0.118, sw, 0), rifle(-0.095, sw, 0), rifle(-0.07, sw, 0)), radii(0.0375, 0.036, 0.029), seg, false, GUN, GUN);
			addTube(m, path(rifle(-0.07, sw, 0), rifle(0.0, sw, 0), rifle(0.07, sw, 0)), radii(0.028, 0.028, 0.028), seg, false, GUN, GUN);
			addTube(m, path(rifle(0.07, sw, 0), rifle(0.1, sw, 0), rifle(0.132, sw, 0)), radii(0.03, 0.039, 0.0425), seg, false, GUN, GUN);
			addCylinder(m, rifle(-0.005, sw + 0.018, 0), W, 0.015, 0.035, seg, GUN_EDGE); // elevation turret
			addCylinder(m, rifle(-0.005, sw, -0.018), v(-1, 0, 0), 0.014, 0.032, seg, GUN_EDGE); // windage turret
			addCylinder(m, rifle(-0.035, sw, 0.018), X, 0.011, 0.022, seg, GUN_EDGE); // focus knob
			for (double u : new double[] {-0.045, 0.045}) {
				addTorus(m, rifle(u, sw, 0), U, 0.0305, 0.006, seg, 6, GUN_EDGE);
				rifleBox(m, u, 0.064, 0, 0.007, 0.012, 0.007, 0.002, GUN_EDGE, GUN_EDGE);
			}
			addLens(m, rifle(0.13, sw, 0), 0.0375, U, LENS);
			addLens(m, rifle(-0.118, sw, 0), 0.0325, U.mul(-1), GLASS);
			// Forend with vents, the barrel and its muzzle brake, and the folded bipod under it.
			rifleBox(m, 0.145, 0.006, 0, 0.078, 0.025, 0.022, 0.007, STOCK, STOCK);
			for (int k = 0; k < 3; k++) {
				for (int side = -1; side <= 1; side += 2) {
					rifleBox(m, 0.105 + k * 0.035, 0.008, side * 0.0215, 0.011, 0.008, 0.0015, 0.001, GRIP, GRIP);
				}
			}
			addCylinder(m, rifle(0.2, 0.012, 0), U, 0.013, 0.14, seg, GUN);
			addCylinder(m, rifle(0.302, 0.012, 0), U, 0.0205, 0.07, seg, GUN_EDGE);
			for (int side = -1; side <= 1; side += 2) {
				rifleBox(m, 0.325, 0.012, side * 0.0165, 0.012, 0.007, 0.002, 0.001, GRIP, GRIP); // ports
				addTube(m, path(rifle(0.205, -0.028, side * 0.011), rifle(0.25, -0.027, side * 0.011), rifle(0.29, -0.026, side * 0.011)),
					radii(0.006, 0.0056, 0.0052), 8, false, GUN_EDGE, GUN_EDGE);
				addSphere(m, rifle(0.293, -0.026, side * 0.011), 0.0078, GRIP);
			}
			addCylinder(m, rifle(0.2, -0.026, -0.016), X, 0.008, 0.04, 10, GUN);
			// Sling loops under the stock and the forend.
			addTorus(m, rifle(-0.33, -0.06, 0), X, 0.011, 0.003, 10, 5, GUN_EDGE);
			addTorus(m, rifle(0.19, -0.022, 0), X, 0.01, 0.003, 10, 5, GUN_EDGE);
		}

		// ------------------------------------------------------------------ the face

		static void addEye(Mesh m, int side, boolean detailed) {
			int seg = 16;
			int rings = 9;
			int base = m.pos.size();
			for (int r = 0; r <= rings; r++) {
				double theta = Math.PI * r / rings;
				for (int k = 0; k <= seg; k++) {
					double phi = 2 * Math.PI * k / seg;
					V unit = v(Math.sin(theta) * Math.cos(phi), Math.cos(theta), Math.sin(theta) * Math.sin(phi));
					V local = v(unit.x * EYE_R.x * 0.99, unit.y * EYE_R.y * 0.99, unit.z * EYE_R.z * 0.99);
					V normal = v(unit.x / EYE_R.x, unit.y / EYE_R.y, unit.z / EYE_R.z);
					V world = rotZ(local, EYE_TILT).add(EYE);
					V n = rotZ(normal, EYE_TILT).norm();
					if (side < 0) {
						world = world.withX(-world.x);
						n = n.withX(-n.x);
					}
					m.add(world, n, EYE_WHITE, 0);
				}
			}
			gridQuads(m, base, rings, seg);
			V eye = EYE.withX(side * EYE.x);
			V look = v(-side * 0.08, 0.02, 1);
			addEllipsoidCap(m, eye, EYE_R, look, 44, 1.004, IRIS);
			addEllipsoidCap(m, eye, EYE_R, look, 22, 1.008, PUPIL);
			V front = eye.add(v(side * 0.006 - 0.008, 0.008, EYE_R.z * 1.0));
			addEllipsoid(m, front, v(0.005, 0.005, 0.002), GLINT);
		}

		/** Long lashes curling up from the top of each eye, and a few short ones below. */
		static void addLashes(Mesh m, int side) {
			for (int k = 0; k < 6; k++) {
				double t = -0.85 + 1.7 * k / 5;
				V root = rotZ(v(t * EYE_R.x * 0.95, EYE_R.y * Math.sqrt(1 - t * t * 0.8) * 0.95, EYE_R.z * 0.55), EYE_TILT).add(EYE);
				double outward = 0.35 + 0.65 * (t + 1) / 2; // longer toward the outer corner
				V dir = v(t * 0.5 + 0.25, 1, 0.75).norm();
				V tip = root.add(dir.mul(0.016 + 0.01 * outward));
				V mid = root.add(dir.mul(0.009)).add(v(0, 0, 0.004));
				if (side < 0) {
					root = root.withX(-root.x);
					mid = mid.withX(-mid.x);
					tip = tip.withX(-tip.x);
				}
				addTube(m, path(root, mid, tip), radii(0.0019, 0.0016, 0.0009), 4, false, LASH, LASH);
			}
			for (int k = 0; k < 3; k++) {
				double t = 0.1 + 0.3 * k;
				V root = rotZ(v(t * EYE_R.x, -EYE_R.y * 0.9, EYE_R.z * 0.6), EYE_TILT).add(EYE);
				V tip = root.add(v(0.003, -0.007, 0.005));
				if (side < 0) {
					root = root.withX(-root.x);
					tip = tip.withX(-tip.x);
				}
				addTube(m, path(root, root.add(tip).mul(0.5), tip), radii(0.0012, 0.001, 0.0007), 4, false, LASH, LASH);
			}
		}

		/** Red lips: a bowed upper lip and a fuller lower one, meeting at the mouth line. */
		static void addLips(Mesh m, boolean upper) {
			int n = 9;
			List<V> points = new ArrayList<>();
			double[] r = new double[n];
			for (int k = 0; k < n; k++) {
				double t = -1 + 2.0 * k / (n - 1);
				double x = t * 0.034;
				double z = Math.sqrt(Math.max(0, sq(cupRadius(MOUTH_Y) + 0.012) - x * x)) - 0.0035;
				double bow = upper ? 0.0035 * Math.cos(t * Math.PI * 1.5) * (1 - t * t) : 0;
				double y = MOUTH_Y + (upper ? 0.0055 + bow : -0.0065) + 0.004 * t * t;
				points.add(v(x, y, z));
				r[k] = (upper ? 0.0055 : 0.0072) * (1 - 0.65 * t * t) + 0.0012;
			}
			addTube(m, points, r, 8, false, LIP, LIP);
		}

		static void addTeeth(Mesh m) {
			for (int k = 0; k < 6; k++) {
				double t = -0.75 + 1.5 * k / 5;
				double x = t * CAVITY_R.x * 0.7;
				double z = CAVITY_C.z + CAVITY_R.z * Math.sqrt(1 - t * t) * 0.78;
				addBevelBox(m, v(x, MOUTH_Y - 0.004, z), v(1, 0, 0), v(0, 1, 0), v(0, 0, 1), v(0.0055, 0.006, 0.0035), 0.0018, TEETH, TEETH);
			}
		}

		static void addBrows(Mesh m, boolean mad) {
			for (int s = -1; s <= 1; s += 2) {
				double[][] xy = mad
					? new double[][] {{s * 0.103, 1.336}, {s * 0.08, 1.33}, {s * 0.056, 1.32}, {s * 0.035, 1.304}, {s * 0.022, 1.294}}
					: new double[][] {{s * 0.1, 1.32}, {s * 0.085, 1.334}, {s * 0.064, 1.339}, {s * 0.043, 1.333}, {s * 0.028, 1.323}};
				addSurfaceBrow(m, WhiteQueen::headShape, xy, 0.4, mad ? 0.0062 : 0.0042, mad ? 0.005 : 0.0032, 0.001, BROW);
			}
		}

		// ------------------------------------------------------------------ building

		@Override
		Map<String, Mesh> build() {
			Map<String, Mesh> parts = new LinkedHashMap<>();
			Mesh head = surfaceNormals(surfaceNets(WhiteQueen::head, v(-0.27, 0.85, -0.27), v(0.27, 1.93, 0.27), 0.011), WhiteQueen::headShape, 0.006);
			for (int s = -1; s <= 1; s += 2) {
				addEye(head, s, false);
				addLashes(head, s);
			}
			addBrows(head, false);
			addLips(head, true);
			addTeeth(head);
			parts.put("head", finish(head, this::paint));

			Mesh jaw = surfaceNormals(surfaceNets(WhiteQueen::jaw, v(-0.16, 0.96, 0.02), v(0.16, 1.16, 0.2), 0.006), WhiteQueen::headShape, 0.005);
			addLips(jaw, false);
			parts.put("jaw", finish(jaw, this::paint));

			parts.put("body", finish(surfaceNets(WhiteQueen::bodyPart, v(-0.32, -0.005, -0.32), v(0.32, 0.92, 0.32), 0.0158), this::paint));
			parts.put("right_arm", finish(this.buildArm(-1, false, false), this::paint));
			parts.put("right_hand", finish(this.buildRifle(false), this::paint));
			parts.put("left_arm", finish(this.buildArm(1, false, false), this::paint));
			this.addOpenHand(parts, "left_", false);
			for (int side = -1; side <= 1; side += 2) {
				this.elbow(side, v(side * 0.183, 0.77, 0.012), v(side * 0.194, 0.532, 0.042));
			}

			Mesh mad = new Mesh();
			addBrows(mad, true);
			for (int s = -1; s <= 1; s += 2) {
				V eye = EYE.withX(s * EYE.x);
				mad.append(eyelid(eye, EYE_R.mul(1.05), s, 0.0, 22, 0.12), IVORY);
			}
			parts.put("mad_head", finish(mad, this::paint));
			parts.put("blink_head", finish(blinkLids(new V[] {EYE.withX(-EYE.x), EYE}, EYE_R.mul(1.06), 0.14), (p, n, m, e) -> this.paint(p, n, IVORY, e)));
			parts.put("middle_finger", finish(this.buildArm(-1, true, false), this::paint));
			parts.put("glow_right_hand", finish(lensGlow(), (p, n, m, e) -> rgb(0.45, 0.9, 1.0)));
			parts.put("fp_right_arm", finish(this.buildArm(-1, false, true), this::paint));
			parts.put("fp_right_hand", finish(this.buildRifle(true), this::paint));
			parts.put("fp_left_arm", finish(this.buildArm(1, false, true), this::paint));
			this.addOpenHand(parts, "fp_left_", true);
			parts.put("fp_middle_finger", finish(this.buildArm(-1, true, true), this::paint));
			return parts;
		}

		/** The scope's front lens again, a hair in front of it: it glows, like a sniper's scope catching the light. */
		static Mesh lensGlow() {
			Mesh m = new Mesh();
			addLens(m, rifle(0.1325, 0.087, 0), 0.0356, U, LENS);
			addTorus(m, rifle(0.134, 0.087, 0), U, 0.0256, 0.0035, 20, 6, LENS);
			return m;
		}

		static double ivoryOcclusion(V p) {
			return Math.min(body(p), headShape(p));
		}

		double[] paint(V p, V n, int material, double extra) {
			double grain = noise(p.mul(40)) * 0.012 + noise(v(p.x * 6, p.y * 90, p.z * 6)) * 0.008;
			switch (material) {
				case EYE_WHITE:
					return rgb(0.97, 0.97, 0.96);
				case IRIS: {
					double ring = clamp01(1.2 - extra);
					return rgb(0.12 + 0.12 * ring, 0.32 + 0.2 * ring, 0.78 + 0.1 * ring);
				}
				case PUPIL:
					return glossyBlack(n);
				case GLINT:
					return rgb(1, 1, 1);
				case LASH:
					return rgb(0.05, 0.04, 0.05);
				case BROW:
					return rgb(0.32, 0.26, 0.24);
				case LIP: {
					double gloss = Math.pow(Math.max(0, n.dot(v(0.2, 0.8, 0.55).norm())), 10) * 0.35;
					return rgb(0.74 + gloss, 0.08 + gloss, 0.17 + gloss);
				}
				case MOUTH:
					return rgb(0.36, 0.06, 0.1);
				case TEETH:
					return rgb(0.97, 0.96, 0.92);
				case NAIL:
					return rgb(0.95, 0.85, 0.85);
				case GUN:
					return gunmetal(n, rgb(0.07, 0.075, 0.082));
				case GUN_EDGE:
					return gunmetal(n, rgb(0.2, 0.21, 0.23));
				case STOCK: {
					double fleck = noise(p.mul(300)) * 0.02;
					return gunmetal(n, rgb(0.2 + fleck, 0.22 + fleck, 0.15 + fleck));
				}
				case GRIP:
					return rgb(0.045 + grain * 0.5, 0.045 + grain * 0.5, 0.05 + grain * 0.5);
				case LENS:
					return rgb(0.4, 0.85, 1.0);
				case GLASS:
					return rgb(0.08, 0.12, 0.18);
				case FELT:
					return rgb(0.08, 0.32, 0.16);
				default:
					break;
			}
			if (p.y < 0.024 && material == IVORY) {
				return rgb(0.07 + grain, 0.3 + grain, 0.15 + grain); // the green felt pad underneath
			}
			// Polished ivory: warm white, a faint grain, darker in the grooves, with a soft shine.
			double ao = occlusion(WhiteQueen::ivoryOcclusion, p, n);
			double shine = Math.pow(Math.max(0, n.dot(v(0.35, 0.8, 0.5).norm())), 18) * 0.22;
			double k = 0.7 + 0.3 * ao;
			double r = (0.96 + grain) * k + shine;
			double g = (0.93 + grain) * k + shine;
			double b = (0.84 + grain) * k + shine;
			if (material == CARVE) {
				return rgb(0.97 + shine, 0.95 + shine, 0.9 + shine); // pearls
			}
			// Make-up: rosy cheeks and a hint of shadow over the eyes.
			if (p.y > 1.0 && p.z > 0.05) {
				for (int s = -1; s <= 1; s += 2) {
					double cheek = Math.exp(-(sq(p.x - s * 0.08) + sq(p.y - 1.2) * 1.6) / 0.0011);
					r = lerp(r, 0.95, cheek * 0.45);
					g = lerp(g, 0.62, cheek * 0.45);
					b = lerp(b, 0.62, cheek * 0.45);
					double lid = Math.exp(-(sq(p.x - s * 0.062) + sq(p.y - 1.315) * 3) / 0.0007);
					r = lerp(r, 0.72, lid * 0.4);
					g = lerp(g, 0.66, lid * 0.4);
					b = lerp(b, 0.8, lid * 0.4);
				}
				double mole = p.sub(v(0.084, 1.188, 0.14)).len();
				if (mole < 0.0045) {
					return rgb(0.25, 0.17, 0.15);
				}
				if (cavity(p) < 0.004 && p.z > 0.04) {
					return rgb(0.36, 0.06, 0.1);
				}
			}
			return rgb(r, g, b);
		}

		static double[] gunmetal(V n, double[] base) {
			double spec = Math.pow(Math.max(0, n.dot(v(0.3, 0.8, 0.5).norm())), 22) * 0.25;
			return rgb(base[0] + spec, base[1] + spec, base[2] + spec * 1.1);
		}
	}

	// ================================================================== Black Wolf

	/**
	 * A big black wolf that walks on four legs: a deep chest under a thick ruff of fur, a tucked waist, strong haunches,
	 * long legs with toes, claws and pads, a long muzzle with a black nose, fangs and a tongue, tall pointed ears, and
	 * three fluffy tails tipped with blue spirit flames. Its eyes and the runes on its forehead, shoulders and legs glow in
	 * the dark. Fur tufts stick out all over. The arms of the player model are its front legs.
	 */
	static final class BlackWolf extends Design {
		static final V RIGHT_SHOULDER = v(-0.15, 0.98, 0.42);
		static final V LEFT_SHOULDER = v(0.15, 0.98, 0.42);
		static final V RIGHT_HIP = v(-0.16, 0.94, -0.47);
		static final V LEFT_HIP = v(0.16, 0.94, -0.47);
		static final V HEAD_PIVOT = v(0, 1.17, 0.62);
		static final V JAW_PIVOT = v(0, 1.302, 0.84);
		static final V EYE = v(0.087, 1.432, 1.0);
		static final V EYE_R = v(0.03, 0.0165, 0.02);
		static final double EYE_TILT = Math.toRadians(-14);
		static final V NOSE = v(0, 1.348, 1.232);
		static final double MOUTH_Y = 1.305;
		static final V TAIL_BASE = v(0, 0.99, -0.64);
		static final String[] TAILS = {"tail_right", "tail_middle", "tail_left"};
		static final int FUR = 0, UNDERCOAT = 1, NOSE_MAT = 2, LIP = 3, MOUTH = 4, TONGUE = 5, TOOTH = 6, CLAW = 7, PAD = 8, EAR_INSIDE = 9, GLOW = 10,
			GLOW_CORE = 11, EYE_DARK = 12, EAR_FUR = 13;

		@Override
		String name() {
			return "Black Wolf";
		}

		@Override
		double height() {
			return 1.9;
		}

		@Override
		double bounce() {
			return 0.035;
		}

		@Override
		String stance() {
			return "four_legs";
		}

		@Override
		boolean hideHeldItems() {
			return true;
		}

		@Override
		V iconEye() {
			return v(0.62, 1.52, 1.72);
		}

		@Override
		V iconTarget() {
			return v(0.0, 1.4, 0.98);
		}

		@Override
		double iconFov() {
			return 0.62;
		}

		@Override
		Rig rig() {
			return new Rig(30, HEAD_PIVOT, JAW_PIVOT, v(0, 0.95, 0), RIGHT_SHOULDER, v(-0.15, 0.05, 0.5), LEFT_SHOULDER, v(0.15, 0.05, 0.5), RIGHT_HIP,
				LEFT_HIP, v(0, 0.84, -0.74));
		}

		@Override
		Map<String, V> extraJoints() {
			Map<String, V> joints = new LinkedHashMap<>();
			for (int i = 0; i < TAILS.length; i++) {
				joints.put(TAILS[i], tailCurve(i - 1).get(0));
			}
			return joints;
		}

		@Override
		List<String> particles() {
			List<String> list = new ArrayList<>();
			for (String tail : TAILS) {
				list.add("{ \"particle\": \"soul_fire_flame\", \"part\": \"" + tail + "\", \"every\": 4, \"spread\": 0.035, \"velocity\": [0, 0.012, 0] }");
			}
			for (int s = -1; s <= 1; s += 2) {
				list.add("{ \"particle\": \"dust\", \"color\": \"#5FE6FF\", \"size\": 0.5, \"part\": \"head\", \"at\": " + arr(EYE.withX(s * (EYE.x + 0.01)).add(v(0, 0, 0.02)))
					+ ", \"every\": 13, \"spread\": 0.006, \"velocity\": [" + fmt(s * 0.004) + ", 0.004, -0.01] }");
			}
			list.add("{ \"particle\": \"squid_ink\", \"part\": \"body\", \"at\": [0, 0.08, 0.45], \"when\": \"sprinting\", \"every\": 2, \"spread\": 0.12 }");
			list.add("{ \"particle\": \"squid_ink\", \"part\": \"body\", \"at\": [0, 0.08, -0.5], \"when\": \"sprinting\", \"every\": 2, \"spread\": 0.12 }");
			list.add("{ \"particle\": \"sculk_soul\", \"part\": \"body\", \"at\": [0, 1.2, 0], \"when\": \"still\", \"every\": 50, \"spread\": 0.3 }");
			return list;
		}

		// ------------------------------------------------------------------ shapes

		/** A little shaggy unevenness everywhere, so the fur doesn't look like rubber. */
		static double shag(V p) {
			return noise(p.mul(26)) * 0.006 + noise(v(p.x * 70, p.y * 55, p.z * 30)) * 0.004;
		}

		static double torso(V p) {
			double d = ellipsoid(p, v(0, 0.86, 0.32), v(0.205, 0.235, 0.28)); // deep chest
			d = smin(d, ellipsoid(p, v(0, 0.9, 0.0), v(0.185, 0.19, 0.33)), 0.12);
			d = smin(d, ellipsoid(p, v(0, 0.93, -0.28), v(0.145, 0.13, 0.2)), 0.12); // tucked waist
			d = smin(d, ellipsoid(p, v(0, 0.9, -0.5), v(0.18, 0.18, 0.2)), 0.12); // haunches
			d = smin(d, ellipsoid(p, v(0, 0.99, -0.6), v(0.15, 0.11, 0.13)), 0.08);
			for (int s = -1; s <= 1; s += 2) {
				d = smin(d, ellipsoid(p, v(s * 0.125, 1.0, 0.38), v(0.085, 0.13, 0.12)), 0.08); // shoulder blades
				d = smin(d, ellipsoid(p, v(s * 0.13, 0.86, -0.47), v(0.09, 0.15, 0.15)), 0.08); // thighs, where they join
			}
			d = smin(d, roundCone(p, v(0, 1.0, 0.45), v(0, 1.27, 0.7), 0.165, 0.115), 0.1); // neck
			d = smax(d, -ellipsoid(p, v(0, 0.62, -0.18), v(0.16, 0.11, 0.3)), 0.08); // belly tucked up
			return d + shag(p);
		}

		/** The thick ruff of fur around the neck and chest. */
		static double ruff(V p) {
			double d = ellipsoid(p, v(0, 1.06, 0.55), v(0.215, 0.2, 0.2));
			d = smin(d, ellipsoid(p, v(0, 0.86, 0.6), v(0.15, 0.17, 0.11)), 0.06); // chest fur
			return d + noise(p.mul(18)) * 0.015 + noise(p.mul(45)) * 0.006;
		}

		static double bodyShape(V p) {
			return smin(torso(p), ruff(p), 0.05);
		}

		static double skull(V p) {
			double d = ellipsoid(p, v(0, 1.405, 0.79), v(0.145, 0.125, 0.155));
			d = smin(d, ellipsoid(p, v(0, 1.37, 0.69), v(0.135, 0.13, 0.11)), 0.06);
			for (int s = -1; s <= 1; s += 2) {
				d = smin(d, ellipsoid(p, v(s * 0.1, 1.345, 0.85), v(0.075, 0.072, 0.09)), 0.05); // cheeks
				d = smin(d, ellipsoid(p, v(s * 0.06, 1.47, 0.95), v(0.05, 0.022, 0.05)), 0.03); // brow ridges
				d = smax(d, -eyeShape(p.withX(p.x * s), 1.25, -0.004), 0.01); // sockets
			}
			// The muzzle: long, narrowing to the nose, flat on top.
			d = smin(d, roundCone(p, v(0, 1.37, 0.9), v(0, 1.345, 1.19), 0.082, 0.047), 0.05);
			d = smin(d, ellipsoid(p, v(0, 1.4, 1.02), v(0.06, 0.028, 0.1)), 0.03);
			return d + shag(p) * 0.6;
		}

		static double lowerJaw(V p) {
			double d = roundCone(p, v(0, 1.262, 0.86), v(0, 1.276, 1.165), 0.072, 0.036);
			d = smin(d, ellipsoid(p, v(0, 1.235, 0.92), v(0.07, 0.04, 0.09)), 0.04); // the chin's fur
			return smax(d, p.y - (MOUTH_Y - 0.004), 0.006) + shag(p) * 0.6;
		}

		static double cavity(V p) {
			return ellipsoid(p, v(0, 1.3, 1.02), v(0.05, 0.03, 0.15));
		}

		static double head(V p) {
			double d = smax(skull(p), MOUTH_Y + 0.004 - p.y, 0.008); // the lower jaw is its own part
			return smax(d, -cavity(p), 0.006);
		}

		static double eyeShape(V p, double grow, double pushBack) {
			V q = rotZ(p.sub(EYE.add(v(0, 0, -pushBack))), -EYE_TILT);
			return ellipsoid(q, v(0, 0, 0), EYE_R.mul(grow));
		}

		/** The front legs: shoulder down to the elbow, a straight forearm to the wrist, then a short pastern to the paw. */
		static double frontLeg(V p, double s) {
			V shoulder = s < 0 ? RIGHT_SHOULDER : LEFT_SHOULDER;
			V elbow = v(s * 0.162, 0.62, 0.33);
			V wrist = v(s * 0.152, 0.18, 0.41);
			V paw = v(s * 0.15, 0.07, 0.46);
			double d = ellipsoid(p, shoulder.add(v(s * 0.01, -0.12, -0.02)), v(0.09, 0.19, 0.12)); // the big shoulder muscle
			d = smin(d, roundCone(p, shoulder.add(v(0, -0.05, 0)), elbow, 0.075, 0.055), 0.06);
			d = smin(d, ellipsoid(p, elbow.add(v(0, 0.04, -0.045)), v(0.05, 0.08, 0.05)), 0.04); // triceps behind the elbow
			d = smin(d, roundCone(p, elbow, wrist, 0.052, 0.036), 0.04);
			d = smin(d, roundCone(p, wrist, paw, 0.036, 0.034), 0.02);
			return d + shag(p) * 0.7;
		}

		/** The back legs bend like a dog's: thigh forward to the knee, back to the hock, then down to the paw. */
		static double hindLeg(V p, double s) {
			V hip = s < 0 ? RIGHT_HIP : LEFT_HIP;
			V knee = v(s * 0.172, 0.6, -0.36);
			V hock = v(s * 0.158, 0.27, -0.6);
			V paw = v(s * 0.155, 0.07, -0.52);
			double d = ellipsoid(p, hip.add(v(s * 0.01, -0.14, 0.02)), v(0.105, 0.2, 0.17)); // thigh
			d = smin(d, roundCone(p, hip.add(v(0, -0.05, 0)), knee, 0.09, 0.06), 0.06);
			d = smin(d, roundCone(p, knee, hock, 0.065, 0.036), 0.05);
			d = smin(d, ellipsoid(p, SkinnyAlien.between(knee, hock, 0.35).add(v(0, 0, -0.02)), v(0.05, 0.1, 0.055)), 0.04); // calf
			d = smin(d, SkinnyAlien.sphere(p, hock.add(v(0, 0.01, -0.025)), 0.032), 0.02);
			d = smin(d, roundCone(p, hock, paw, 0.034, 0.032), 0.03);
			return d + shag(p) * 0.7;
		}

		/** A paw: a pad, four toes with claws, a dewclaw on the front legs; the middle toe can stick out like a finger. */
		static void addPaw(Mesh m, V paw, double s, boolean front, boolean finger, boolean detailed) {
			addEllipsoid(m, paw.add(v(0, -0.025, 0.01)), v(0.055, 0.035, 0.06), FUR);
			addEllipsoid(m, paw.add(v(0, -0.055, 0.0)), v(0.035, 0.012, 0.035), PAD);
			for (int t = 0; t < 4; t++) {
				V toe = toe(paw, t);
				boolean middle = finger && t == (s < 0 ? 1 : 2);
				if (middle) {
					// The middle toe, long like a finger, pointing forward (up once the leg is raised).
					addTube(m, path(toe, toe.add(v(0, 0.02, 0.06)), toe.add(v(0, 0.035, 0.13))), radii(0.019, 0.017, 0.015), detailed ? 10 : 8, false, FUR, FUR);
					addCone(m, toe.add(v(0, 0.036, 0.135)), toe.add(v(0, 0.04, 0.17)), 0.009, 6, CLAW);
					continue;
				}
				if (!front || finger) {
					addToe(m, paw, t);
				}
			}
			if (front && finger) {
				addDewclaw(m, paw, s);
			}
		}

		/** Where toe {@code t} (0 to 3, across the paw) sits on a paw. */
		static V toe(V paw, int t) {
			double a = Math.toRadians((t - 1.5) * 22);
			return paw.add(v(Math.sin(a) * 0.04, -0.045, 0.045 + Math.cos(a) * 0.03 - Math.abs(t - 1.5) * 0.008));
		}

		/** A toe: furry, with a pad under it and a claw. Returns the tip of the claw. */
		static V addToe(Mesh m, V paw, int t) {
			double a = Math.toRadians((t - 1.5) * 22);
			V toe = toe(paw, t);
			addEllipsoid(m, toe, v(0.019, 0.017, 0.022), FUR);
			addEllipsoid(m, toe.add(v(0, -0.012, 0.002)), v(0.012, 0.006, 0.012), PAD);
			V tip = toe.add(v(Math.sin(a) * 0.01, -0.016, 0.042));
			addCone(m, toe.add(v(0, -0.002, 0.016)), tip, 0.0075, 6, CLAW);
			return tip;
		}

		static V addDewclaw(Mesh m, V paw, double s) {
			V dew = paw.add(v(-s * 0.04, 0.03, -0.01));
			addEllipsoid(m, dew, v(0.012, 0.016, 0.012), FUR);
			V tip = dew.add(v(-s * 0.006, -0.02, 0.02));
			addCone(m, dew.add(v(0, -0.008, 0.006)), tip, 0.005, 5, CLAW);
			return tip;
		}

		/**
		 * The front paws' four toes and dewclaw, each a part of its own that curls down toward the pads (the dewclaw is the
		 * thumb). {@code prefix} is "right_", "left_" or "fp_right_"...
		 */
		void addFrontToes(Map<String, Mesh> parts, String prefix, double s) {
			V paw = v(s * 0.15, 0.075, 0.46);
			// Across the paw from the inside (next to the dewclaw) out: index, middle, ring and little toe.
			String[] roles = {"index", "middle", "ring", "little"};
			for (int k = 0; k < 4; k++) {
				int t = s < 0 ? 3 - k : k;
				Mesh m = new Mesh();
				V tip = addToe(m, paw, t);
				V base = toe(paw, t).add(v(0, 0.006, -0.02));
				this.addPawPart(parts, prefix, roles[k], m, new FingerSpec(base, tip, v(0, -1, -0.35), false, new double[] {0, 0.45}, new double[] {45, 55}));
			}
			Mesh dew = new Mesh();
			V tip = addDewclaw(dew, paw, s);
			this.addPawPart(parts, prefix, "thumb", dew, new FingerSpec(paw.add(v(-s * 0.04, 0.045, -0.016)), tip, v(s * 0.3, 0, -1), false,
				new double[] {0, 0.5}, new double[] {30, 40}));
		}

		void addPawPart(Map<String, Mesh> parts, String prefix, String role, Mesh m, FingerSpec spec) {
			Mesh finished = finish(m, this::paint);
			if (prefix.startsWith("fp_")) {
				parts.put(prefix + role, finished);
			} else {
				this.addFinger(parts, prefix + role, finished, spec);
			}
		}

		// ------------------------------------------------------------------ tails

		/** The middle line of a tail: back from the rump, then up, the tip curling. {@code side} -1, 0 or 1. */
		static List<V> tailCurve(int side) {
			List<V> base = List.of(v(0, 0, 0), v(0, 0.09, -0.27), v(0, 0.29, -0.55), v(0, 0.55, -0.68), v(0, 0.76, -0.62), v(0, 0.86, -0.5));
			double spread = Math.toRadians(44) * side;
			double lean = Math.toRadians(-30) * side;
			double drop = Math.abs(side) * 0.15;
			List<V> points = new ArrayList<>();
			for (V q : base) {
				V r = rotZ(v(q.x, q.y * (1 - drop), q.z), lean); // side tails lean out to the side
				r = rotY(r, spread);
				points.add(TAIL_BASE.add(v(side * 0.035, 0, 0)).add(r));
			}
			return points;
		}

		/** Thickness along a tail (0 at the base, 1 at the tip): thin at the root, bushy in the middle, a soft point. */
		static double tailRadius(double t) {
			return 0.042 + 0.09 * Math.sin(Math.PI * Math.pow(Math.min(1, t * 1.05), 0.75)) * (1 - 0.35 * t);
		}

		/** Distance to a tail: a bushy tube along its curve, with clumps of fur. */
		static double tail(V p, List<V> curve, double[] tOut) {
			double best = 1e9;
			double bestT = 0;
			int n = curve.size() - 1;
			for (int i = 0; i < n; i++) {
				V a = curve.get(i);
				V b = curve.get(i + 1);
				V ab = b.sub(a);
				double u = clamp01(p.sub(a).dot(ab) / ab.dot(ab));
				double dist = p.sub(a.add(ab.mul(u))).len();
				double t = (i + u) / n;
				double d = dist - tailRadius(t);
				if (d < best) {
					best = d;
					bestT = t;
				}
			}
			if (tOut != null) {
				tOut[0] = bestT;
			}
			return best + noise(p.mul(16)) * 0.02 + noise(p.mul(40)) * 0.008;
		}

		static Mesh buildTail(int side, boolean glowOnly) {
			List<V> curve = tailCurve(side);
			if (glowOnly) {
				return tailFlame(curve);
			}
			Mesh m = surfaceNets(p -> tail(p, curve, null), v(-0.9, 0.82, -1.55), v(0.9, 2.05, -0.5), 0.034);
			// Fur tufts sticking out backward along the tail.
			java.util.Random random = new java.util.Random(77 + side);
			for (int k = 0; k < 26; k++) {
				double t = 0.12 + 0.8 * random.nextDouble();
				V center = along(curve, t);
				V dirAlong = along(curve, Math.min(1, t + 0.02)).sub(along(curve, Math.max(0, t - 0.02))).norm();
				V out = randomPerpendicular(dirAlong, random);
				V root = center.add(out.mul(tailRadius(t) * 0.85));
				V tip = root.add(out.mul(0.03 + 0.05 * random.nextDouble())).add(dirAlong.mul(0.05 + 0.04 * random.nextDouble()));
				addCone(m, root, tip, 0.021 + 0.014 * random.nextDouble(), 4, FUR);
			}
			return m;
		}

		/** A blue spirit flame at the tip of a tail, and a glowing ring just below it. */
		static Mesh tailFlame(List<V> curve) {
			Mesh m = new Mesh();
			V tip = curve.get(curve.size() - 1);
			V before = along(curve, 0.9);
			V dir = tip.sub(before).norm();
			V up = v(0, 1, 0);
			for (int k = 0; k < 5; k++) {
				double a = 2 * Math.PI * k / 5;
				V wobble = randomish(a).mul(0.025);
				V root = tip.sub(dir.mul(0.02)).add(wobble);
				V flameTip = tip.add(dir.mul(0.06 + 0.03 * (k % 2))).add(up.mul(0.12 + 0.05 * (k % 3))).add(wobble.mul(1.6));
				addCone(m, root, flameTip, 0.032 - 0.004 * (k % 3), 7, k == 0 ? GLOW_CORE : GLOW);
			}
			addEllipsoid(m, tip.add(up.mul(0.03)), v(0.04, 0.05, 0.04), GLOW_CORE);
			V ring = along(curve, 0.86);
			addTorus(m, ring, along(curve, 0.88).sub(along(curve, 0.84)), tailRadius(0.86) * 0.95, 0.007, 20, 5, GLOW);
			return m;
		}

		static V randomish(double a) {
			return v(Math.cos(a), 0.3 * Math.sin(2 * a), Math.sin(a));
		}

		static V along(List<V> curve, double t) {
			int n = curve.size() - 1;
			double x = clamp(t, 0, 1) * n;
			int i = Math.min(n - 1, (int) Math.floor(x));
			double u = x - i;
			return curve.get(i).add(curve.get(i + 1).sub(curve.get(i)).mul(u));
		}

		static V randomPerpendicular(V axis, java.util.Random random) {
			V a = axis.norm();
			V u = (Math.abs(a.y) < 0.9 ? v(0, 1, 0) : v(1, 0, 0)).cross(a).norm();
			V w = a.cross(u);
			double angle = random.nextDouble() * 2 * Math.PI;
			return u.mul(Math.cos(angle)).add(w.mul(Math.sin(angle)));
		}

		// ------------------------------------------------------------------ fur tufts

		/** Moves a point onto a surface along its gradient. */
		static V onSurface(Sdf f, V p) {
			for (int i = 0; i < 6; i++) {
				V g = gradient(f, p, 0.002);
				double gl = g.dot(g);
				if (gl < 1e-12) {
					break;
				}
				p = p.sub(g.mul(f.at(p) / gl));
			}
			return p;
		}

		/**
		 * Tufts of fur over a region: cones from points on the surface, pointing out and swept toward {@code sweep}.
		 *
		 * @param region where tufts go (points with region(p) < 0)
		 */
		static void addTufts(Mesh m, Sdf surface, Sdf region, V min, V max, int count, double length, double radius, V sweep, long seed) {
			addTufts(m, surface, region, min, max, count, length, radius, sweep, seed, 4);
		}

		static void addTufts(Mesh m, Sdf surface, Sdf region, V min, V max, int count, double length, double radius, V sweep, long seed, int seg) {
			java.util.Random random = new java.util.Random(seed);
			int placed = 0;
			for (int tries = 0; tries < count * 40 && placed < count; tries++) {
				V guess = v(lerp(min.x, max.x, random.nextDouble()), lerp(min.y, max.y, random.nextDouble()), lerp(min.z, max.z, random.nextDouble()));
				if (region.at(guess) > 0) {
					continue;
				}
				V root = onSurface(surface, guess);
				if (root.sub(guess).len() > 0.2 || region.at(root) > 0.02) {
					continue;
				}
				V normal = gradient(surface, root, 0.003).norm();
				V dir = normal.mul(0.55).add(sweep.norm().mul(0.75)).norm();
				double len = length * (0.6 + 0.8 * random.nextDouble());
				addCone(m, root.sub(normal.mul(0.008)), root.add(dir.mul(len)), radius * (0.7 + 0.6 * random.nextDouble()), seg, FUR);
				placed++;
			}
		}

		// ------------------------------------------------------------------ glowing runes

		/** A glowing line lying on a surface, through points pushed onto it. */
		static void addRune(Mesh m, Sdf surface, double width, V... points) {
			addLine(m, surface, width, GLOW, points);
		}

		static void addLine(Mesh m, Sdf surface, double width, int material, V... points) {
			List<V> onIt = new ArrayList<>();
			for (V p : points) {
				V q = onSurface(surface, p);
				onIt.add(q.add(gradient(surface, q, 0.003).norm().mul(0.004)));
			}
			double[] r = new double[onIt.size()];
			java.util.Arrays.fill(r, width);
			r[0] = width * 0.5;
			r[r.length - 1] = width * 0.5;
			addTube(m, onIt, r, 5, false, material, material);
		}

		// ------------------------------------------------------------------ building

		@Override
		Map<String, Mesh> build() {
			Map<String, Mesh> parts = new LinkedHashMap<>();
			Mesh head = surfaceNets(BlackWolf::head, v(-0.24, 1.26, 0.56), v(0.24, 1.6, 1.3), 0.0152);
			addNoseAndEars(head);
			addTeeth(head, true);
			addTufts(head, BlackWolf::skull, p -> Math.min(Math.min(ellipsoid(p, v(0.12, 1.34, 0.83), v(0.075, 0.075, 0.085)),
				ellipsoid(p, v(-0.12, 1.34, 0.83), v(0.075, 0.075, 0.085))), ellipsoid(p, v(0, 1.44, 0.68), v(0.16, 0.1, 0.09))),
				v(-0.25, 1.25, 0.55), v(0.25, 1.58, 0.95), 42, 0.056, 0.023, v(0, -0.3, -1), 11);
			parts.put("head", finish(head, this::paint));

			Mesh jaw = surfaceNets(p -> smax(lowerJaw(p), -cavity(p), 0.005), v(-0.12, 1.16, 0.76), v(0.12, 1.32, 1.22), 0.0138);
			addTeeth(jaw, false);
			addEllipsoid(jaw, v(0, 1.287, 1.02), v(0.035, 0.009, 0.12), TONGUE);
			addTufts(jaw, BlackWolf::lowerJaw, p -> ellipsoid(p, v(0, 1.22, 0.9), v(0.09, 0.06, 0.1)), v(-0.1, 1.15, 0.8), v(0.1, 1.28, 1.0), 10, 0.045,
				0.017, v(0, -1, -0.3), 12);
			parts.put("jaw", finish(jaw, this::paint));

			Mesh body = surfaceNets(BlackWolf::bodyShape, v(-0.32, 0.55, -0.84), v(0.32, 1.44, 0.92), 0.031);
			// A mane of tufts around the neck and chest, and shaggy fur along the back and the belly.
			addTufts(body, BlackWolf::bodyShape, p -> ellipsoid(p, v(0, 1.02, 0.55), v(0.26, 0.28, 0.24)), v(-0.3, 0.7, 0.3), v(0.3, 1.32, 0.82), 80, 0.095,
				0.037, v(0, -0.3, -1), 21);
			addTufts(body, BlackWolf::bodyShape, p -> ellipsoid(p, v(0, 1.08, -0.1), v(0.12, 0.08, 0.6)), v(-0.15, 0.95, -0.7), v(0.15, 1.2, 0.5), 36, 0.058,
				0.026, v(0, 0.1, -1), 22);
			addTufts(body, BlackWolf::bodyShape, p -> ellipsoid(p, v(0, 0.62, 0.0), v(0.18, 0.1, 0.42)), v(-0.2, 0.55, -0.4), v(0.2, 0.75, 0.45), 28, 0.066,
				0.024, v(0, -1, -0.2), 23);
			parts.put("body", finish(body, this::paint));

			for (int side = -1; side <= 1; side += 2) {
				double s = side;
				parts.put(side < 0 ? "right_arm" : "left_arm", finish(this.buildFrontLeg(s, false, false), this::paint));
				this.addFrontToes(parts, side < 0 ? "right_" : "left_", s);
				this.elbow(s, v(s * 0.162, 0.62, 0.33), v(s * 0.152, 0.18, 0.41));
				parts.put(side < 0 ? "right_leg" : "left_leg", finish(this.buildHindLeg(s), this::paint));
			}
			for (int i = 0; i < TAILS.length; i++) {
				parts.put(TAILS[i], finish(buildTail(i - 1, false), this::paint));
			}

			// Glowing eyes, and runes on the forehead, the shoulders and the front legs.
			Mesh glowHead = new Mesh();
			for (int s = -1; s <= 1; s += 2) {
				addGlowEye(glowHead, s);
			}
			addRune(glowHead, BlackWolf::skull, 0.0055, v(0, 1.53, 0.9), v(0, 1.5, 0.96), v(0, 1.46, 1.0));
			for (int s = -1; s <= 1; s += 2) {
				addRune(glowHead, BlackWolf::skull, 0.0045, v(s * 0.035, 1.535, 0.86), v(s * 0.02, 1.515, 0.93), v(s * 0.008, 1.49, 0.97));
			}
			parts.put("glow_head", finish(glowHead, this::paintGlow));
			Mesh glowBody = new Mesh();
			for (int s = -1; s <= 1; s += 2) {
				addRune(glowBody, BlackWolf::bodyShape, 0.007, v(s * 0.14, 1.13, 0.47), v(s * 0.2, 1.02, 0.4), v(s * 0.2, 0.9, 0.33), v(s * 0.17, 0.82, 0.24));
				addRune(glowBody, BlackWolf::bodyShape, 0.006, v(s * 0.15, 1.03, 0.2), v(s * 0.19, 0.93, 0.12), v(s * 0.18, 0.85, 0.04));
				addRune(glowBody, BlackWolf::bodyShape, 0.006, v(s * 0.13, 1.0, -0.52), v(s * 0.19, 0.92, -0.58), v(s * 0.18, 0.82, -0.62));
			}
			for (int k = 0; k < 5; k++) { // a dashed line down the spine
				double z = 0.3 - k * 0.17;
				addRune(glowBody, BlackWolf::bodyShape, 0.0065, v(0, 1.2, z), v(0, 1.2, z - 0.09));
			}
			parts.put("glow_body", finish(glowBody, this::paintGlow));
			for (int side = -1; side <= 1; side += 2) {
				double s = side;
				Mesh rune = new Mesh();
				Sdf leg = p -> frontLeg(p, s);
				addRune(rune, leg, 0.0055, v(s * 0.2, 0.5, 0.36), v(s * 0.205, 0.42, 0.39), v(s * 0.2, 0.34, 0.4));
				addRune(rune, leg, 0.0045, v(s * 0.19, 0.3, 0.42), v(s * 0.2, 0.26, 0.44));
				parts.put(side < 0 ? "glow_right_arm" : "glow_left_arm", finish(rune, this::paintGlow));
			}
			for (int i = 0; i < TAILS.length; i++) {
				parts.put("glow_" + TAILS[i], finish(buildTail(i - 1, true), this::paintGlow));
			}

			// Angry: brows pulled down and the snout wrinkled up. Blinking: fur lids over the eyes.
			Mesh mad = new Mesh();
			for (int s = -1; s <= 1; s += 2) {
				addSurfaceBrow(mad, BlackWolf::skull, new double[][] {{s * 0.108, 1.462}, {s * 0.088, 1.466}, {s * 0.064, 1.458}, {s * 0.044, 1.446}, {s * 0.03, 1.436}},
					1.3, 0.013, 0.01, 0.002, FUR);
			}
			for (int k = 0; k < 3; k++) { // wrinkles across the top of the snout
				double z = 1.0 + k * 0.045;
				addLine(mad, BlackWolf::skull, 0.0055, LIP, v(-0.05, 1.42, z), v(-0.02, 1.44, z + 0.012), v(0.02, 1.44, z + 0.012), v(0.05, 1.42, z));
			}
			parts.put("mad_head", finish(mad, this::paint));
			parts.put("blink_head", finish(blinkLids(new V[] {EYE.withX(-EYE.x), EYE}, v(EYE_R.x * 1.2, EYE_R.y * 1.6, EYE_R.z * 1.25), 0.15),
				(p, n, m, e) -> this.paint(p, n, FUR, e)));
			parts.put("middle_finger", finish(this.buildFrontLeg(-1, true, false), this::paint));
			parts.put("fp_right_arm", finish(this.buildFrontLeg(-1, false, true), this::paint));
			this.addFrontToes(parts, "fp_right_", -1);
			parts.put("fp_left_arm", finish(this.buildFrontLeg(1, false, true), this::paint));
			this.addFrontToes(parts, "fp_left_", 1);
			parts.put("fp_middle_finger", finish(this.buildFrontLeg(-1, true, true), this::paint));
			return parts;
		}

		Mesh buildFrontLeg(double s, boolean finger, boolean detailed) {
			V shoulder = s < 0 ? RIGHT_SHOULDER : LEFT_SHOULDER;
			Mesh m = surfaceNets(p -> frontLeg(p, s), v(shoulder.x - 0.13, 0.04, 0.18), v(shoulder.x + 0.13, 1.1, 0.6), detailed ? 0.0098 : 0.0255);
			addPaw(m, v(s * 0.15, 0.075, 0.46), s, true, finger, detailed);
			// Shaggy tufts behind the elbow and down the back of the leg.
			addTufts(m, p -> frontLeg(p, s), p -> ellipsoid(p, v(s * 0.16, 0.55, 0.28), v(0.12, 0.22, 0.08)), v(shoulder.x - 0.12, 0.3, 0.2),
				v(shoulder.x + 0.12, 0.8, 0.4), detailed ? 50 : 13, 0.056, 0.021, v(0, -0.6, -1), 31 + (long) s, detailed ? 5 : 4);
			addTufts(m, p -> frontLeg(p, s), p -> ellipsoid(p, v(s * 0.16, 0.88, 0.42), v(0.11, 0.12, 0.13)), v(shoulder.x - 0.12, 0.75, 0.3),
				v(shoulder.x + 0.12, 1.05, 0.56), detailed ? 30 : 8, 0.05, 0.023, v(0, -1, -0.4), 33 + (long) s, detailed ? 5 : 4);
			return m;
		}

		Mesh buildHindLeg(double s) {
			V hip = s < 0 ? RIGHT_HIP : LEFT_HIP;
			Mesh m = surfaceNets(p -> hindLeg(p, s), v(hip.x - 0.14, 0.04, -0.8), v(hip.x + 0.14, 1.08, -0.2), 0.0255);
			addPaw(m, v(s * 0.155, 0.075, -0.52), s, false, false, false);
			addTufts(m, p -> hindLeg(p, s), p -> ellipsoid(p, v(s * 0.16, 0.62, -0.55), v(0.12, 0.2, 0.1)), v(hip.x - 0.13, 0.4, -0.72), v(hip.x + 0.13, 0.85, -0.4),
				15, 0.062, 0.024, v(0, -0.4, -1), 41 + (long) s);
			return m;
		}

		static void addNoseAndEars(Mesh m) {
			addEllipsoid(m, NOSE, v(0.04, 0.029, 0.03), NOSE_MAT);
			for (int s = -1; s <= 1; s += 2) {
				addEllipsoid(m, NOSE.add(v(s * 0.017, -0.004, 0.026)), v(0.009, 0.0065, 0.005), MOUTH); // nostrils
				// Tall pointed ears: a furry outside and a darker hollow inside, leaning back a little.
				V base = v(s * 0.095, 1.49, 0.76);
				V tip = v(s * 0.135, 1.75, 0.69);
				V dir = tip.sub(base);
				for (int layer = 0; layer < 2; layer++) {
					boolean inside = layer == 1;
					double grow = inside ? 0.72 : 1.0;
					V offset = inside ? v(0, 0.008, 0.0125) : v(0, 0, 0);
					int seg = 10;
					int rows = 8;
					int b0 = m.pos.size();
					for (int r = 0; r <= rows; r++) {
						double t = (double) r / rows;
						V center = base.add(dir.mul(t * (inside ? 0.93 : 1.0))).add(offset);
						double width = 0.066 * (1 - t) * grow + 0.004;
						double depth = 0.03 * (1 - t) * grow + 0.003;
						for (int k = 0; k <= seg; k++) {
							double a = 2 * Math.PI * k / seg;
							V radial = v(Math.cos(a) * width, 0, Math.sin(a) * depth);
							m.add(center.add(radial), v(Math.cos(a) / width, 0.3, Math.sin(a) / depth).norm(), inside ? EAR_INSIDE : FUR, 0);
						}
					}
					gridQuads(m, b0, rows, seg);
				}
				addCone(m, tip.sub(dir.mul(0.05)), tip.add(dir.norm().mul(0.03)), 0.01, 5, FUR); // a tuft on the tip
				for (int k = 0; k < 5; k++) { // light fur inside the ear
					double t = 0.12 + 0.14 * k;
					V root = base.add(dir.mul(t)).add(v(s * (0.02 - 0.006 * k), 0, 0.022 * (1 - t)));
					addCone(m, root, root.add(v(-s * 0.012, 0.035, 0.02)), 0.006, 4, EAR_FUR);
				}
			}
		}

		static void addTeeth(Mesh m, boolean upper) {
			for (int k = 0; k < 6; k++) {
				double z = 0.93 + k * 0.042;
				double x = 0.05 - k * 0.004;
				boolean fang = k == 5;
				for (int s = -1; s <= 1; s += 2) {
					double length = fang ? (upper ? 0.05 : 0.036) : 0.018 + 0.004 * (k % 2);
					V root = v(s * x, MOUTH_Y + (upper ? 0.006 : -0.008), z);
					V tip = root.add(v(-s * 0.003, upper ? -length : length, 0.004));
					addCone(m, root, tip, fang ? 0.011 : 0.0075, 6, TOOTH);
				}
			}
			for (int k = 0; k < 4; k++) { // small front teeth between the fangs
				double x = (k - 1.5) * 0.012;
				V root = v(x, MOUTH_Y + (upper ? 0.004 : -0.006), 1.18);
				addCone(m, root, root.add(v(0, upper ? -0.016 : 0.013, 0.004)), 0.0055, 5, TOOTH);
			}
			// Black lips along the mouth line.
			for (int s = -1; s <= 1; s += 2) {
				List<V> line = new ArrayList<>();
				for (int k = 0; k <= 8; k++) {
					double z = 0.88 + k * 0.038;
					double x = 0.066 - k * 0.0045;
					line.add(v(s * x, MOUTH_Y + (upper ? 0.008 : -0.009), z));
				}
				addTube(m, line, radii(0.008, 0.008, 0.007, 0.007, 0.007, 0.006, 0.006, 0.006, 0.005), 6, false, LIP, LIP);
			}
		}

		static void addGlowEye(Mesh m, int side) {
			int seg = 14;
			int rings = 7;
			int base = m.pos.size();
			for (int r = 0; r <= rings; r++) {
				double theta = Math.PI * r / rings;
				for (int k = 0; k <= seg; k++) {
					double phi = 2 * Math.PI * k / seg;
					V unit = v(Math.sin(theta) * Math.cos(phi), Math.cos(theta), Math.sin(theta) * Math.sin(phi));
					V local = v(unit.x * EYE_R.x, unit.y * EYE_R.y, unit.z * EYE_R.z);
					V world = rotZ(local, EYE_TILT).add(EYE);
					V n = rotZ(v(unit.x / EYE_R.x, unit.y / EYE_R.y, unit.z / EYE_R.z), EYE_TILT).norm();
					if (side < 0) {
						world = world.withX(-world.x);
						n = n.withX(-n.x);
					}
					m.add(world, n, unit.z > 0.55 && Math.abs(unit.y) < 0.5 ? GLOW_CORE : GLOW, 0);
				}
			}
			gridQuads(m, base, rings, seg);
			// A thin dark slit of a pupil.
			V eye = EYE.withX(side * EYE.x);
			addEllipsoid(m, eye.add(v(0, 0, EYE_R.z * 0.92)), v(0.0035, 0.012, 0.003), EYE_DARK);
		}

		// ------------------------------------------------------------------ painting

		static double wholeShape(V p) {
			return Math.min(Math.min(bodyShape(p), skull(p)), Math.min(frontLeg(p, -1), frontLeg(p, 1)));
		}

		double[] paint(V p, V n, int material, double extra) {
			switch (material) {
				case NOSE_MAT:
				case LIP:
					return glossyBlack(n);
				case MOUTH:
					return rgb(0.22, 0.04, 0.06);
				case TONGUE:
					return rgb(0.72, 0.28, 0.33);
				case TOOTH: {
					double root = clamp01(extra);
					return rgb(0.95 - root * 0.08, 0.94 - root * 0.1, 0.88 - root * 0.15);
				}
				case CLAW: {
					double spec = Math.pow(Math.max(0, n.dot(v(0.3, 0.8, 0.5).norm())), 12) * 0.25;
					return rgb(0.1 + spec, 0.1 + spec, 0.11 + spec);
				}
				case PAD:
					return rgb(0.07, 0.06, 0.065);
				case EAR_INSIDE:
					return rgb(0.13, 0.095, 0.105);
				case EAR_FUR:
					return rgb(0.42, 0.44, 0.5);
				case EYE_DARK:
					return rgb(0.02, 0.05, 0.08);
				case GLOW:
				case GLOW_CORE:
					return this.paintGlow(p, n, material, extra);
				default:
					break;
			}
			if (cavity(p) < 0.003 && p.z > 0.86 && p.y > 1.27 && p.y < 1.33) {
				return rgb(0.22, 0.04, 0.06); // inside the mouth
			}
			// Black fur: streaks along the body, a lighter undercoat underneath, and a cold blue sheen where the light hits.
			double ao = occlusion(BlackWolf::wholeShape, p, n);
			double streak = noise(v(p.x * 60, p.y * 60, p.z * 14)) * 0.02 + noise(p.mul(140)) * 0.01;
			double under = smoothstep(0.1, -0.7, n.y) * 0.035;
			double sheen = Math.pow(Math.max(0, n.dot(v(0.25, 0.85, 0.45).norm())), 4) * 0.2;
			double back = Math.pow(Math.max(0, n.dot(v(-0.4, 0.5, -0.75).norm())), 6) * 0.1; // light from behind on the fur's edge
			double frost = extra < 0.95 ? (1 - extra) * 0.12 : 0; // tufts get lighter toward their tips
			double k = 0.6 + 0.4 * ao;
			double base = (0.07 + streak + under) * k;
			return rgb(base + sheen * 0.75 + back + frost, base + sheen * 0.82 + back + frost * 1.05, base * 1.18 + sheen * 1.1 + back * 1.3 + frost * 1.2);
		}

		double[] paintGlow(V p, V n, int material, double extra) {
			if (material == EYE_DARK) {
				return rgb(0.02, 0.06, 0.09);
			}
			return material == GLOW_CORE ? rgb(0.82, 1.0, 1.0) : rgb(0.3, 0.86, 1.0);
		}
	}

	// ================================================================== emote helpers

	/** Where a line from {@code from} along {@code dir} first enters the shape, or null if it doesn't within {@code range}. */
	static V hitSurface(Sdf f, V from, V dir, double range) {
		V d = dir.norm();
		double step = 0.002;
		double prev = f.at(from);
		for (double t = step; t <= range; t += step) {
			double now = f.at(from.add(d.mul(t)));
			if (prev > 0 && now <= 0) {
				double lo = t - step;
				double hi = t;
				for (int i = 0; i < 20; i++) {
					double mid = (lo + hi) / 2;
					if (f.at(from.add(d.mul(mid))) > 0) {
						lo = mid;
					} else {
						hi = mid;
					}
				}
				return from.add(d.mul(hi));
			}
			prev = now;
		}
		return null;
	}

	/**
	 * A brow (or wrinkle) lying on a surface: each control point is pushed back onto the face along -Z,
	 * then lifted off it by {@code lift}.
	 */
	static void addSurfaceBrow(Mesh m, Sdf face, double[][] xy, double startZ, double radius0, double radius1, double lift, int material) {
		List<V> points = new ArrayList<>();
		for (double[] c : xy) {
			V hit = hitSurface(face, v(c[0], c[1], startZ), v(0, 0, -1), startZ + 0.5);
			if (hit == null) {
				throw new IllegalStateException("brow point " + c[0] + ", " + c[1] + " misses the face");
			}
			points.add(hit.add(gradient(face, hit, 0.002).norm().mul(lift)));
		}
		double[] r = new double[points.size()];
		for (int i = 0; i < r.length; i++) {
			double t = (double) i / (r.length - 1);
			r[i] = lerp(radius0, radius1, t) * (1 - 0.35 * sq(2 * t - 1));
		}
		addTube(m, points, r, 7, false, material, material);
	}

	/**
	 * An angry eyelid: a shell over the top of an eye, cut off by a line that slopes down toward the nose.
	 *
	 * @param side      +1 for the eye at +X (the character's left), -1 for the other
	 * @param cover     how far below the eye's middle the lid comes down at its middle, as a fraction of its height
	 * @param slope     how steeply the lid's edge drops toward the nose, in degrees
	 * @param thickness how thick the lid is, as a fraction of the eye's size
	 */
	static Mesh eyelid(V c, V r, double side, double cover, double slope, double thickness) {
		Mesh m = new Mesh();
		V n = v(-side * Math.tan(Math.toRadians(slope)), 1, 0).norm();
		double offset = -cover * r.y;
		int seg = 22;
		int rows = 9;
		double[] cutTheta = new double[seg + 1];
		for (int j = 0; j <= seg; j++) {
			double phi = 2 * Math.PI * j / seg;
			double lo = 0;
			double hi = Math.PI;
			for (int it = 0; it < 30; it++) {
				double mid = (lo + hi) / 2;
				if (ellipsoidPoint(c, r, mid, phi, 1).sub(c).dot(n) > offset) {
					lo = mid;
				} else {
					hi = mid;
				}
			}
			cutTheta[j] = lo;
		}
		int base = m.pos.size();
		for (int i = 0; i <= rows; i++) {
			for (int j = 0; j <= seg; j++) {
				double phi = 2 * Math.PI * j / seg;
				double theta = cutTheta[j] * i / rows;
				m.add(ellipsoidPoint(c, r, theta, phi, 1 + thickness), ellipsoidNormal(r, theta, phi), 0, 0);
			}
		}
		gridQuads(m, base, rows, seg);
		// the lid's edge, from its outside down to the eye
		int rim = m.pos.size();
		for (int j = 0; j <= seg; j++) {
			double phi = 2 * Math.PI * j / seg;
			V edgeNormal = n.mul(-1);
			m.add(ellipsoidPoint(c, r, cutTheta[j], phi, 1 + thickness), edgeNormal, 0, 0);
			m.add(ellipsoidPoint(c, r, cutTheta[j], phi, 1.0), edgeNormal, 0, 0);
		}
		for (int j = 0; j < seg; j++) {
			int a = rim + j * 2;
			m.quad(a, a + 2, a + 3, a + 1);
		}
		return m;
	}

	/** A round patch lying on an ellipsoid's surface, around the direction {@code dir} (irises and pupils). */
	static void addEllipsoidCap(Mesh m, V c, V r, V dir, double angleDegrees, double scale, int material) {
		V d = v(dir.x / r.x, dir.y / r.y, dir.z / r.z).norm(); // so the cap is round on the surface, not in the sphere's coordinates
		V u = (Math.abs(d.y) < 0.9 ? v(0, 1, 0) : v(1, 0, 0)).cross(d).norm();
		V w = d.cross(u);
		int seg = 16;
		int rows = 4;
		double angle = Math.toRadians(angleDegrees);
		int base = m.pos.size();
		for (int i = 0; i <= rows; i++) {
			double theta = angle * i / rows;
			for (int j = 0; j <= seg; j++) {
				double phi = 2 * Math.PI * j / seg;
				V unit = d.mul(Math.cos(theta)).add(u.mul(Math.cos(phi) * Math.sin(theta))).add(w.mul(Math.sin(phi) * Math.sin(theta)));
				V point = c.add(v(unit.x * r.x, unit.y * r.y, unit.z * r.z).mul(scale));
				m.add(point, v(unit.x / r.x, unit.y / r.y, unit.z / r.z).norm(), material, 0);
			}
		}
		gridQuads(m, base, rows, seg);
	}

	static V ellipsoidPoint(V c, V r, double theta, double phi, double scale) {
		return c.add(v(Math.sin(theta) * Math.cos(phi) * r.x, Math.cos(theta) * r.y, Math.sin(theta) * Math.sin(phi) * r.z).mul(scale));
	}

	static V ellipsoidNormal(V r, double theta, double phi) {
		return v(Math.sin(theta) * Math.cos(phi) / r.x, Math.cos(theta) / r.y, Math.sin(theta) * Math.sin(phi) / r.z).norm();
	}

	/**
	 * A fist with the middle finger sticking out forward (+Z). With the arm hanging down that is straight ahead;
	 * when the emote raises the arm forward, the finger points up.
	 *
	 * @param c        middle of the fist
	 * @param r        size of the fist
	 * @param finger   radius of the finger at its base
	 * @param length   length of the finger
	 */
	static void addFist(Mesh m, V c, V r, double finger, double length, int material, int tipMaterial) {
		addEllipsoid(m, c, r, material);
		// curled fingers: knuckles along the front of the fist
		for (int i = -1; i <= 2; i++) {
			if (i == 0) {
				continue;
			}
			double x = c.x + i * r.x * 0.42 - r.x * 0.21;
			addEllipsoid(m, v(x, c.y - r.y * 0.35, c.z + r.z * 0.62), v(r.x * 0.24, r.y * 0.36, r.z * 0.42), material);
		}
		// thumb folded over the curled fingers
		addEllipsoid(m, v(c.x - r.x * 0.15, c.y - r.y * 0.05, c.z + r.z * 0.78), v(r.x * 0.42, r.y * 0.2, r.z * 0.22), material);
		double x = c.x + r.x * 0.0;
		V base = v(x, c.y - r.y * 0.2, c.z + r.z * 0.55);
		addTube(m, path(base, base.add(v(0, 0.08 * length, 0.5 * length)), base.add(v(0, 0.1 * length, length))), radii(finger, finger * 0.95, finger * 0.85),
			7, false, material, tipMaterial);
	}

	// ================================================================== shared painting helpers

	static double[] glossyBlack(V n) {
		double gloss = Math.max(0, n.dot(v(0.35, 0.75, 0.56).norm()));
		double hi = Math.pow(gloss, 14) * 0.55 + Math.pow(Math.max(0, n.dot(v(-0.5, 0.3, 0.8).norm())), 30) * 0.2;
		return rgb(0.025 + hi, 0.027 + hi, 0.034 + hi * 1.1);
	}

	static double[] mix(double[] a, double[] b, double t) {
		t = clamp01(t);
		return rgb(lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t));
	}

	/** Ambient occlusion from a signed distance field: darker where nearby surfaces block the light. */
	static double occlusion(Sdf body, V p, V n) {
		double occ = 0;
		double weight = 1;
		for (double d : new double[] {0.012, 0.03, 0.06}) {
			occ += weight * (d - body.at(p.add(n.mul(d)))) / d;
			weight *= 0.5;
		}
		return clamp01(1 - occ * 0.55);
	}

	// ================================================================== surface nets

	interface Sdf {
		double at(V p);
	}

	static Mesh surfaceNets(Sdf f, V min, V max, double h) {
		h *= detailScale;
		int nx = (int) Math.ceil((max.x - min.x) / h) + 1;
		int ny = (int) Math.ceil((max.y - min.y) / h) + 1;
		int nz = (int) Math.ceil((max.z - min.z) / h) + 1;
		double[] values = new double[nx * ny * nz];
		for (int k = 0; k < nz; k++) {
			for (int j = 0; j < ny; j++) {
				for (int i = 0; i < nx; i++) {
					values[i + nx * (j + ny * k)] = f.at(v(min.x + i * h, min.y + j * h, min.z + k * h));
				}
			}
		}

		Mesh m = new Mesh();
		int cx = nx - 1;
		int cy = ny - 1;
		int[] cellVertex = new int[cx * cy * (nz - 1)];
		Arrays.fill(cellVertex, -1);
		int[][] corners = {{0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {1, 1, 0}, {0, 0, 1}, {1, 0, 1}, {0, 1, 1}, {1, 1, 1}};
		int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
		for (int k = 0; k < nz - 1; k++) {
			for (int j = 0; j < ny - 1; j++) {
				for (int i = 0; i < nx - 1; i++) {
					double[] cv = new double[8];
					int inside = 0;
					for (int c = 0; c < 8; c++) {
						cv[c] = values[(i + corners[c][0]) + nx * ((j + corners[c][1]) + ny * (k + corners[c][2]))];
						if (cv[c] < 0) {
							inside++;
						}
					}
					if (inside == 0 || inside == 8) {
						continue;
					}
					double sx = 0;
					double sy = 0;
					double sz = 0;
					int count = 0;
					for (int[] e : edges) {
						double a = cv[e[0]];
						double b = cv[e[1]];
						if ((a < 0) != (b < 0)) {
							double t = a / (a - b);
							sx += corners[e[0]][0] + t * (corners[e[1]][0] - corners[e[0]][0]);
							sy += corners[e[0]][1] + t * (corners[e[1]][1] - corners[e[0]][1]);
							sz += corners[e[0]][2] + t * (corners[e[1]][2] - corners[e[0]][2]);
							count++;
						}
					}
					V p = v(min.x + (i + sx / count) * h, min.y + (j + sy / count) * h, min.z + (k + sz / count) * h);
					V cellMin = v(min.x + i * h, min.y + j * h, min.z + k * h);
					for (int it = 0; it < 3; it++) { // pull the vertex onto the surface, but keep it in its cell
						V g = gradient(f, p, h * 0.25);
						double gl = g.dot(g);
						if (gl < 1e-12) {
							break;
						}
						p = p.sub(g.mul(f.at(p) / gl));
						p = v(clamp(p.x, cellMin.x, cellMin.x + h), clamp(p.y, cellMin.y, cellMin.y + h), clamp(p.z, cellMin.z, cellMin.z + h));
					}
					cellVertex[i + cx * (j + cy * k)] = m.add(p, gradient(f, p, h * 0.3).norm(), 0, 0);
				}
			}
		}

		for (int k = 0; k < nz; k++) {
			for (int j = 0; j < ny; j++) {
				for (int i = 0; i < nx; i++) {
					boolean a = values[i + nx * (j + ny * k)] < 0;
					if (i < nx - 1 && j > 0 && k > 0 && j < ny - 1 && k < nz - 1 && a != (values[(i + 1) + nx * (j + ny * k)] < 0)) {
						quadFromCells(m, cellVertex, cx, cy, i, j - 1, k - 1, i, j, k - 1, i, j, k, i, j - 1, k);
					}
					if (j < ny - 1 && i > 0 && k > 0 && i < nx - 1 && k < nz - 1 && a != (values[i + nx * ((j + 1) + ny * k)] < 0)) {
						quadFromCells(m, cellVertex, cx, cy, i - 1, j, k - 1, i, j, k - 1, i, j, k, i - 1, j, k);
					}
					if (k < nz - 1 && i > 0 && j > 0 && i < nx - 1 && j < ny - 1 && a != (values[i + nx * (j + ny * (k + 1))] < 0)) {
						quadFromCells(m, cellVertex, cx, cy, i - 1, j - 1, k, i, j - 1, k, i, j, k, i - 1, j, k);
					}
				}
			}
		}
		return m;
	}

	static void quadFromCells(Mesh m, int[] cellVertex, int cx, int cy, int... c) {
		int[] q = new int[4];
		for (int n = 0; n < 4; n++) {
			q[n] = cellVertex[c[n * 3] + cx * (c[n * 3 + 1] + cy * c[n * 3 + 2])];
			if (q[n] < 0) {
				return;
			}
		}
		m.quad(q[0], q[1], q[2], q[3]);
	}

	static V gradient(Sdf f, V p, double e) {
		return v(
			f.at(p.add(v(e, 0, 0))) - f.at(p.add(v(-e, 0, 0))),
			f.at(p.add(v(0, e, 0))) - f.at(p.add(v(0, -e, 0))),
			f.at(p.add(v(0, 0, e))) - f.at(p.add(v(0, 0, -e)))).mul(1 / (2 * e));
	}

	// ================================================================== parametric shapes

	static void gridQuads(Mesh m, int base, int rows, int seg) {
		for (int r = 0; r < rows; r++) {
			for (int s = 0; s < seg; s++) {
				int a = base + r * (seg + 1) + s;
				int b = a + seg + 1;
				m.quad(a, a + 1, b + 1, b);
			}
		}
	}

	static void addSphere(Mesh m, V c, double r, int material) {
		addEllipsoid(m, c, v(r, r, r), material);
	}

	static void addEllipsoid(Mesh m, V c, V r, int material) {
		addOrientedEllipsoid(m, c, v(0, 1, 0), 0, r, material);
	}

	/** An ellipsoid whose local Y axis points along {@code up}, turned by {@code yaw} around it. */
	static void addOrientedEllipsoid(Mesh m, V c, V up, double yaw, V r, int material) {
		V u = up.norm();
		V a = (Math.abs(u.y) < 0.9 ? v(0, 1, 0) : v(0, 0, 1)).cross(u).norm(); // for an upright ellipsoid: a = -X, so radius x is along X
		V b = u.cross(a);
		V ax = a.mul(Math.cos(yaw)).add(b.mul(Math.sin(yaw)));
		V az = ax.cross(u).mul(-1);
		int seg = segments(Math.min(12, Math.max(8, (int) Math.round(Math.max(r.x, r.z) * 400))));
		int rings = Math.max(5, seg / 2);
		int base = m.pos.size();
		for (int i = 0; i <= rings; i++) {
			double theta = Math.PI * i / rings;
			for (int s = 0; s <= seg; s++) {
				double phi = 2 * Math.PI * s / seg;
				double ux = Math.sin(theta) * Math.cos(phi);
				double uy = Math.cos(theta);
				double uz = Math.sin(theta) * Math.sin(phi);
				V p = c.add(ax.mul(ux * r.x)).add(u.mul(uy * r.y)).add(az.mul(uz * r.z));
				V n = ax.mul(ux / r.x).add(u.mul(uy / r.y)).add(az.mul(uz / r.z)).norm();
				m.add(p, n, material, 0);
			}
		}
		gridQuads(m, base, rings, seg);
	}

	/** Axis-aligned box with all six faces (flat shading). */
	static void addBox(Mesh m, V c, V h, int material) {
		V[] normals = {v(1, 0, 0), v(-1, 0, 0), v(0, 1, 0), v(0, -1, 0), v(0, 0, 1), v(0, 0, -1)};
		for (V n : normals) {
			V u = Math.abs(n.y) > 0.5 ? v(1, 0, 0) : v(0, 1, 0);
			V w = n.cross(u);
			V center = c.add(v(n.x * h.x, n.y * h.y, n.z * h.z));
			V du = v(u.x * h.x, u.y * h.y, u.z * h.z);
			V dw = v(w.x * h.x, w.y * h.y, w.z * h.z);
			int a = m.add(center.sub(du).sub(dw), n, material, 0);
			int b = m.add(center.add(du).sub(dw), n, material, 0);
			int cc = m.add(center.add(du).add(dw), n, material, 0);
			int d = m.add(center.sub(du).add(dw), n, material, 0);
			m.quad(a, b, cc, d);
		}
	}

	/** A ring around the vertical axis (glove cuffs). */
	static void addTorusY(Mesh m, V c, double major, double minor, int material) {
		int seg = segments(14);
		int sides = segments(6);
		int base = m.pos.size();
		for (int i = 0; i <= seg; i++) {
			double a = 2 * Math.PI * i / seg;
			V dir = v(Math.cos(a), 0, Math.sin(a));
			for (int j = 0; j <= sides; j++) {
				double b = 2 * Math.PI * j / sides;
				V n = dir.mul(Math.cos(b)).add(v(0, Math.sin(b), 0));
				m.add(c.add(dir.mul(major)).add(n.mul(minor)), n, material, 0);
			}
		}
		gridQuads(m, base, seg, sides);
	}

	/** A ring around {@code axis} through {@code c}. */
	static void addTorus(Mesh m, V c, V axis, double major, double minor, int seg, int sides, int material) {
		seg = segments(seg);
		sides = segments(sides);
		V a = axis.norm();
		V u = (Math.abs(a.y) < 0.9 ? v(0, 1, 0) : v(1, 0, 0)).cross(a).norm();
		V w = a.cross(u);
		int base = m.pos.size();
		for (int i = 0; i <= seg; i++) {
			double t = 2 * Math.PI * i / seg;
			V dir = u.mul(Math.cos(t)).add(w.mul(Math.sin(t)));
			for (int j = 0; j <= sides; j++) {
				double b = 2 * Math.PI * j / sides;
				V n = dir.mul(Math.cos(b)).add(a.mul(Math.sin(b)));
				m.add(c.add(dir.mul(major)).add(n.mul(minor)), n, material, 0);
			}
		}
		gridQuads(m, base, seg, sides);
	}

	/** A cylinder standing on {@code base} along {@code axis}, with a closed, slightly rounded top (toy studs). */
	static void addCylinder(Mesh m, V base, V axis, double radius, double height, int seg, int material) {
		seg = segments(seg);
		V a = axis.norm();
		V u = (Math.abs(a.y) < 0.9 ? v(0, 1, 0) : v(1, 0, 0)).cross(a).norm();
		V w = a.cross(u);
		double bevel = Math.min(radius, height) * 0.25;
		int[] ring = new int[4];
		for (int r = 0; r < 4; r++) {
			ring[r] = m.pos.size();
			for (int s = 0; s <= seg; s++) {
				double angle = 2 * Math.PI * s / seg;
				V radial = u.mul(Math.cos(angle)).add(w.mul(Math.sin(angle)));
				V p;
				V n;
				if (r == 0) {
					p = base.add(radial.mul(radius));
					n = radial;
				} else if (r == 1) {
					p = base.add(a.mul(height - bevel)).add(radial.mul(radius));
					n = radial;
				} else {
					p = base.add(a.mul(height)).add(radial.mul(radius - bevel));
					n = r == 2 ? radial.add(a).norm() : a;
				}
				m.add(p, n, material, 0);
			}
		}
		for (int r = 0; r < 2; r++) {
			for (int s = 0; s < seg; s++) {
				int i0 = ring[r] + s;
				int i1 = ring[r + 1] + s;
				m.quad(i0, i0 + 1, i1 + 1, i1);
			}
		}
		int center = m.add(base.add(a.mul(height)), a, material, 0);
		for (int s = 0; s < seg; s++) {
			int i0 = ring[3] + s;
			m.quad(i0, i0 + 1, center, center);
		}
	}

	/**
	 * A box with beveled edges along the axes {@code ex}, {@code ey}, {@code ez} (unit length, at right angles), with half
	 * sizes {@code h}. The faces get {@code material} and the bevels {@code bevelMaterial}. Flat shaded, like a toy brick.
	 */
	static void addBevelBox(Mesh m, V c, V ex, V ey, V ez, V h, double bevel, int material, int bevelMaterial) {
		V[] axes = {ex, ey, ez};
		double[] half = {h.x, h.y, h.z};
		double[] inner = {h.x - bevel, h.y - bevel, h.z - bevel};
		for (int k = 0; k < 3; k++) {
			int i = (k + 1) % 3;
			int j = (k + 2) % 3;
			for (int s = -1; s <= 1; s += 2) {
				V n = axes[k].mul(s);
				V center = c.add(n.mul(half[k]));
				V a = axes[i].mul(inner[i]);
				V b = axes[j].mul(inner[j]);
				int p0 = m.add(center.sub(a).sub(b), n, material, 0);
				int p1 = m.add(center.add(a).sub(b), n, material, 0);
				int p2 = m.add(center.add(a).add(b), n, material, 0);
				int p3 = m.add(center.sub(a).add(b), n, material, 0);
				m.quad(p0, p1, p2, p3);
			}
		}
		for (int k = 0; k < 3; k++) { // the edges along axes[k]
			int i = (k + 1) % 3;
			int j = (k + 2) % 3;
			for (int si = -1; si <= 1; si += 2) {
				for (int sj = -1; sj <= 1; sj += 2) {
					V n = axes[i].mul(si).add(axes[j].mul(sj)).norm();
					V onI = c.add(axes[i].mul(si * half[i])).add(axes[j].mul(sj * inner[j]));
					V onJ = c.add(axes[i].mul(si * inner[i])).add(axes[j].mul(sj * half[j]));
					V along = axes[k].mul(inner[k]);
					int p0 = m.add(onI.sub(along), n, bevelMaterial, 0);
					int p1 = m.add(onI.add(along), n, bevelMaterial, 0);
					int p2 = m.add(onJ.add(along), n, bevelMaterial, 0);
					int p3 = m.add(onJ.sub(along), n, bevelMaterial, 0);
					m.quad(p0, p1, p2, p3);
				}
			}
		}
		for (int sx = -1; sx <= 1; sx += 2) { // the corners
			for (int sy = -1; sy <= 1; sy += 2) {
				for (int sz = -1; sz <= 1; sz += 2) {
					double[] sign = {sx, sy, sz};
					V n = ex.mul(sx).add(ey.mul(sy)).add(ez.mul(sz)).norm();
					int[] corner = new int[3];
					for (int k = 0; k < 3; k++) {
						V p = c;
						for (int a = 0; a < 3; a++) {
							p = p.add(axes[a].mul(sign[a] * (a == k ? half[a] : inner[a])));
						}
						corner[k] = m.add(p, n, bevelMaterial, 0);
					}
					m.quad(corner[0], corner[1], corner[2], corner[2]);
				}
			}
		}
	}

	/** A smooth tube along a Catmull-Rom curve through the points. With {@code pointed} it ends in a claw tip. */
	static void addTube(Mesh m, List<V> points, double[] radius, int seg, boolean pointed, int material, int tipMaterial) {
		seg = segments(seg);
		List<V> samples = new ArrayList<>();
		List<Double> sampleRadius = new ArrayList<>();
		List<Double> sampleT = new ArrayList<>();
		int spans = points.size() - 1;
		for (int span = 0; span < spans; span++) {
			V p0 = points.get(Math.max(0, span - 1));
			V p1 = points.get(span);
			V p2 = points.get(span + 1);
			V p3 = points.get(Math.min(points.size() - 1, span + 2));
			double length = p2.sub(p1).len();
			int steps = Math.max(2, (int) Math.ceil(length / (0.03 * detailScale)));
			for (int s = 0; s < steps; s++) {
				double t = (double) s / steps;
				samples.add(catmullRom(p0, p1, p2, p3, t));
				sampleRadius.add(lerp(radius[span], radius[span + 1], t));
				sampleT.add((span + t) / spans);
			}
		}
		samples.add(points.get(points.size() - 1));
		sampleRadius.add(radius[radius.length - 1]);
		sampleT.add(1.0);

		V normal = null;
		int base = m.pos.size();
		for (int i = 0; i < samples.size(); i++) {
			V tangent = samples.get(Math.min(i + 1, samples.size() - 1)).sub(samples.get(Math.max(i - 1, 0))).norm();
			if (normal == null) {
				normal = Math.abs(tangent.y) < 0.9 ? v(0, 1, 0).cross(tangent).norm() : v(1, 0, 0).cross(tangent).norm();
			} else {
				normal = normal.sub(tangent.mul(normal.dot(tangent))).norm();
			}
			V binormal = tangent.cross(normal);
			double t = sampleT.get(i);
			double tipAmount = pointed ? smoothstep(0.62, 0.9, t) : 0;
			int mat = pointed && tipAmount > 0.5 ? tipMaterial : material;
			for (int s = 0; s <= seg; s++) {
				double a = 2 * Math.PI * s / seg;
				V dir = normal.mul(Math.cos(a)).add(binormal.mul(Math.sin(a)));
				m.add(samples.get(i).add(dir.mul(sampleRadius.get(i))), dir, mat, tipAmount);
			}
		}
		gridQuads(m, base, samples.size() - 1, seg);
		int last = samples.size() - 1;
		V end = samples.get(last);
		V dir = end.sub(samples.get(last - 1)).norm();
		double r = radius[radius.length - 1];
		int apex = m.add(end.add(dir.mul(pointed ? r * 3.0 : r * 0.9)), dir, pointed ? tipMaterial : material, pointed ? 1.0 : 0);
		for (int s = 0; s < seg; s++) {
			int a = base + last * (seg + 1) + s;
			m.quad(a, a + 1, apex, apex);
		}
		// round start cap
		V start = samples.get(0);
		V back = samples.get(0).sub(samples.get(1)).norm();
		int startApex = m.add(start.add(back.mul(radius[0] * 0.9)), back, material, 0);
		for (int s = 0; s < seg; s++) {
			int a = base + s;
			m.quad(a + 1, a, startApex, startApex);
		}
	}

	static void addCone(Mesh m, V root, V tip, double radius, int seg, int material) {
		seg = segments(seg);
		V axis = tip.sub(root);
		V dir = axis.norm();
		V normal = Math.abs(dir.y) < 0.9 ? v(0, 1, 0).cross(dir).norm() : v(1, 0, 0).cross(dir).norm();
		V binormal = dir.cross(normal);
		int base = m.pos.size();
		double[] ts = {0, 0.45, 0.8};
		double[] rs = {radius, radius * 0.62, radius * 0.28};
		for (int r = 0; r < ts.length; r++) {
			for (int s = 0; s <= seg; s++) {
				double a = 2 * Math.PI * s / seg;
				V d = normal.mul(Math.cos(a)).add(binormal.mul(Math.sin(a)));
				m.add(root.add(axis.mul(ts[r])).add(d.mul(rs[r])), d.add(dir.mul(0.35)).norm(), material, 1 - ts[r]);
			}
		}
		gridQuads(m, base, ts.length - 1, seg);
		int apex = m.add(tip, dir, material, 0);
		for (int s = 0; s < seg; s++) {
			int a = base + (ts.length - 1) * (seg + 1) + s;
			m.quad(a, a + 1, apex, apex);
		}
	}

	static V catmullRom(V p0, V p1, V p2, V p3, double t) {
		double t2 = t * t;
		double t3 = t2 * t;
		return p0.mul(-0.5 * t3 + t2 - 0.5 * t)
			.add(p1.mul(1.5 * t3 - 2.5 * t2 + 1))
			.add(p2.mul(-1.5 * t3 + 2 * t2 + 0.5 * t))
			.add(p3.mul(0.5 * t3 - 0.5 * t2));
	}

	static List<V> path(V... points) {
		return List.of(points);
	}

	static double[] radii(double... r) {
		return r;
	}

	// ================================================================== output

	static void writeObj(Path file, Design design, Map<String, Mesh> parts) throws IOException {
		try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
			out.println("# " + design.name() + " character for the Player Characters mod, made by tools/CharacterModels.java");
			out.println("# 1 unit = 1 block, Y up, feet at 0, facing +Z. Vertex colors after the position (r g b).");
			int offset = 1;
			for (Map.Entry<String, Mesh> entry : parts.entrySet()) {
				Mesh m = entry.getValue();
				out.println("o " + entry.getKey());
				for (int i = 0; i < m.pos.size(); i++) {
					V p = m.pos.get(i);
					double[] c = m.color.get(i);
					out.printf(Locale.ROOT, "v %.5f %.5f %.5f %.3f %.3f %.3f%n", p.x, p.y, p.z, c[0], c[1], c[2]);
				}
				for (V n : m.nrm) {
					out.printf(Locale.ROOT, "vn %.4f %.4f %.4f%n", n.x, n.y, n.z);
				}
				for (int[] q : m.quads) {
					if (q[2] == q[3]) {
						out.printf(Locale.ROOT, "f %d//%d %d//%d %d//%d%n", q[0] + offset, q[0] + offset, q[1] + offset, q[1] + offset, q[2] + offset, q[2] + offset);
					} else {
						out.printf(Locale.ROOT, "f %d//%d %d//%d %d//%d %d//%d%n", q[0] + offset, q[0] + offset, q[1] + offset, q[1] + offset,
							q[2] + offset, q[2] + offset, q[3] + offset, q[3] + offset);
					}
				}
				offset += m.pos.size();
			}
		}
	}

	static void writeJson(Path file, Design design) throws IOException {
		Rig r = design.rig();
		StringBuilder extra = new StringBuilder();
		if (design.waddle() != 0) {
			extra.append("\t\"waddle\": ").append(fmt(design.waddle())).append(",\n");
		}
		if (design.headTurn() != 1) {
			extra.append("\t\"head_turn\": ").append(fmt(design.headTurn())).append(",\n");
		}
		if (design.smoke() != null) {
			extra.append("\t\"smoke\": ").append(arr(design.smoke())).append(",\n");
		}
		if (design.bounce() != 0) {
			extra.append("\t\"bounce\": ").append(fmt(design.bounce())).append(",\n");
		}
		if (design.stance() != null) {
			extra.append("\t\"stance\": \"").append(design.stance()).append("\",\n");
		}
		if (design.arms() != null) {
			extra.append("\t\"arms\": \"").append(design.arms()).append("\",\n");
		}
		if (design.hideHeldItems()) {
			extra.append("\t\"hide_held_items\": true,\n");
		}
		if (!design.particles().isEmpty()) {
			extra.append("\t\"particles\": [\n");
			for (int i = 0; i < design.particles().size(); i++) {
				extra.append("\t\t").append(design.particles().get(i)).append(i + 1 < design.particles().size() ? ",\n" : "\n");
			}
			extra.append("\t],\n");
		}
		StringBuilder joints = new StringBuilder();
		for (Map.Entry<String, V> joint : design.extraJoints().entrySet()) {
			joints.append(",\n\t\t\"").append(joint.getKey()).append("\": { \"pivot\": ").append(arr(joint.getValue())).append(" }");
		}
		for (Map.Entry<String, FingerSpec> finger : design.fingerSpecs.entrySet()) {
			FingerSpec f = finger.getValue();
			joints.append(",\n\t\t\"").append(finger.getKey()).append("\": { \"pivot\": ").append(arr(f.base())).append(", \"tip\": ").append(arr(f.tip()))
				.append(", \"curl\": ").append(arr(f.curl().norm()));
			if (f.joints() != null) {
				joints.append(", \"joints\": ").append(list(f.joints()));
			}
			if (f.angles() != null) {
				joints.append(", \"angles\": ").append(list(f.angles()));
			}
			if (f.rigid()) {
				joints.append(", \"rigid\": true");
			}
			joints.append(" }");
		}
		String rightElbow = armJointJson(design, "right");
		String leftElbow = armJointJson(design, "left");
		String json = """
			{
				"name": "%s",
				"size": 1.0,
				"jaw_angle": %s,
				"butt": %s,
			%s	"parts": {
					"head": { "pivot": %s },
					"jaw": { "pivot": %s },
					"body": { "pivot": %s },
					"right_arm": { "pivot": %s, "hand": %s%s },
					"left_arm": { "pivot": %s, "hand": %s%s },
					"right_leg": { "pivot": %s },
					"left_leg": { "pivot": %s }%s
				}
			}
			""".formatted(design.displayName(), fmt(r.jawAngle()), arr(r.butt()), extra, arr(r.head()), arr(r.jaw()), arr(r.body()),
			arr(r.rightArm()), arr(r.rightHand()), rightElbow, arr(r.leftArm()), arr(r.leftHand()), leftElbow, arr(r.rightLeg()), arr(r.leftLeg()), joints);
		Files.writeString(file, json.replace("\t", "  "), StandardCharsets.UTF_8);
	}

	/** {@code , "elbow": [...], "wrist": [...]} for an arm, or nothing if the design didn't say. */
	static String armJointJson(Design design, String side) {
		V elbow = design.armJoints.get(side + "_elbow");
		V wrist = design.armJoints.get(side + "_wrist");
		return elbow == null ? "" : ", \"elbow\": " + arr(elbow) + ", \"wrist\": " + arr(wrist);
	}

	static String list(double[] values) {
		StringBuilder s = new StringBuilder("[");
		for (int i = 0; i < values.length; i++) {
			s.append(i > 0 ? ", " : "").append(fmt(values[i]));
		}
		return s.append("]").toString();
	}

	static String arr(V p) {
		return "[" + fmt(p.x) + ", " + fmt(p.y) + ", " + fmt(p.z) + "]";
	}

	static String fmt(double d) {
		String s = String.format(Locale.ROOT, "%.3f", d).replaceAll("0+$", "").replaceAll("\\.$", "");
		return s.equals("-0") ? "0" : s;
	}

	// ================================================================== preview renderer

	static BufferedImage renderIcon(Design design, Map<String, Mesh> parts) {
		BufferedImage big = render(design, parts, design.iconFov(), design.iconEye(), design.iconTarget(), 256, 256, false, design.rig().jawAngle() * 0.8, false);
		BufferedImage icon = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				double a = 0;
				double r = 0;
				double g = 0;
				double b = 0;
				for (int sy = 0; sy < 4; sy++) {
					for (int sx = 0; sx < 4; sx++) {
						int c = big.getRGB(x * 4 + sx, y * 4 + sy);
						double ca = (c >>> 24) / 255.0;
						a += ca;
						r += ((c >> 16) & 255) * ca;
						g += ((c >> 8) & 255) * ca;
						b += (c & 255) * ca;
					}
				}
				if (a > 0) {
					icon.setRGB(x, y, ((int) Math.round(a / 16 * 255) << 24) | ((int) (r / a) << 16) | ((int) (g / a) << 8) | (int) (b / a));
				}
			}
		}
		return icon;
	}

	static void writePreviews(Design design, Map<String, Mesh> parts, Path dir) throws IOException {
		String n = design.name().toLowerCase(Locale.ROOT);
		double h = design.height();
		V mid = v(0, h * 0.53, 0);
		double fov = h / 1.9;
		double jaw = design.rig().jawAngle();
		ImageIO.write(render(design, parts, fov, mid.add(v(0, 0.1, 3.4)), mid, 400, 560, true, 0, false), "png", dir.resolve(n + "-front.png").toFile());
		ImageIO.write(render(design, parts, fov, mid.add(v(2.4, 0.4, 2.4)), mid, 400, 560, true, jaw, false), "png", dir.resolve(n + "-three-quarter-talking.png").toFile());
		ImageIO.write(render(design, parts, fov, mid.add(v(3.4, 0.1, 0)), mid, 400, 560, true, 0, false), "png", dir.resolve(n + "-side.png").toFile());
		ImageIO.write(render(design, parts, fov, mid.add(v(-1.2, 0.25, 3.2)), mid, 400, 560, true, 0, true), "png", dir.resolve(n + "-emote.png").toFile());
		V face = design.iconTarget();
		V eye = design.iconEye();
		V away = eye.sub(face);
		V eye34 = face.add(rotY(away, Math.toRadians(25))).add(v(0, 0.1, 0));
		ImageIO.write(render(design, parts, design.iconFov(), eye34, face, 500, 500, true, 0, false), "png", dir.resolve(n + "-face-closed.png").toFile());
		ImageIO.write(render(design, parts, design.iconFov(), eye34, face, 500, 500, true, jaw, false), "png", dir.resolve(n + "-face-open.png").toFile());
		ImageIO.write(render(design, parts, design.iconFov(), eye34, face, 500, 500, true, 0, true), "png", dir.resolve(n + "-face-mad.png").toFile());
		blinkPreview = true;
		ImageIO.write(render(design, parts, design.iconFov(), eye34, face, 500, 500, true, 0, false), "png", dir.resolve(n + "-face-blink.png").toFile());
		blinkPreview = false;
		if (parts.containsKey("fp_right_arm")) {
			// Roughly what you see in first person: the hand from above and behind the shoulder.
			V hand = design.rig().rightHand();
			V shoulder = design.rig().rightArm();
			firstPersonPreview = true;
			ImageIO.write(render(design, parts, 0.45, shoulder.add(v(0.25, 0.35, -0.35)), hand, 500, 500, true, 0, false), "png",
				dir.resolve(n + "-first-person.png").toFile());
			firstPersonPreview = false;
		}
	}

	/** While drawing the blink preview: draw the closed eyelids. */
	static boolean blinkPreview;
	/** While drawing the first-person preview: draw only the first-person arms. */
	static boolean firstPersonPreview;

	/** Small software rasterizer with two directional lights, roughly like Minecraft's entity lighting. */
	static BufferedImage render(Design design, Map<String, Mesh> parts, double fovScale, V eye, V target, int w, int h, boolean background, double jawDegrees,
		boolean emote) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		double[] depth = new double[w * h];
		Arrays.fill(depth, Double.MAX_VALUE);
		if (background) {
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int g = (int) (70 + 50.0 * y / h);
					img.setRGB(x, y, 0xFF000000 | (g << 16) | (g << 8) | (g + 10));
				}
			}
		}
		V forward = target.sub(eye).norm();
		V right = forward.cross(v(0, 1, 0)).norm();
		V up = right.cross(forward);
		double focal = h / (2 * 0.42 * fovScale);
		V light1 = v(0.2, 1.0, -0.7).norm();
		V light2 = v(-0.2, 1.0, 0.7).norm();
		V hinge = design.rig().jaw();
		V shoulder = design.rig().rightArm();
		for (Map.Entry<String, Mesh> entry : parts.entrySet()) {
			String name = entry.getKey();
			boolean blinkPart = name.startsWith("blink_");
			if (name.startsWith("fp_") != firstPersonPreview) {
				continue;
			}
			if (blinkPart ? !blinkPreview : emote ? name.equals("right_arm") || name.equals("glow_right_arm") || isCalmPart(name) : isEmotePart(name)) {
				continue;
			}
			boolean glow = name.startsWith("glow_");
			Mesh m = entry.getValue();
			boolean isJaw = name.equals("jaw") || name.equals("mad_jaw") || name.equals("calm_jaw");
			boolean raised = name.equals("middle_finger");
			V[] sp = new V[m.pos.size()];
			V[] wn = new V[m.pos.size()];
			for (int i = 0; i < m.pos.size(); i++) {
				V p = m.pos.get(i);
				V n = m.nrm.get(i);
				if (isJaw && jawDegrees != 0) {
					p = rotX(p.sub(hinge), Math.toRadians(jawDegrees)).add(hinge);
					n = rotX(n, Math.toRadians(jawDegrees));
				}
				if (raised) {
					p = rotX(p.sub(shoulder), EMOTE_RAISE).add(shoulder);
					n = rotX(n, EMOTE_RAISE);
				}
				V rel = p.sub(eye);
				double z = rel.dot(forward);
				sp[i] = v(w / 2.0 + rel.dot(right) * focal / z, h / 2.0 - rel.dot(up) * focal / z, z);
				wn[i] = n;
			}
			for (int[] q : m.quads) {
				int[][] tris = q[2] == q[3] ? new int[][] {{q[0], q[1], q[2]}} : new int[][] {{q[0], q[1], q[2]}, {q[0], q[2], q[3]}};
				for (int[] t : tris) {
					raster(img, depth, w, h, sp[t[0]], sp[t[1]], sp[t[2]], m, wn, t, glow ? null : light1, light2);
				}
			}
		}
		return img;
	}

	static void raster(BufferedImage img, double[] depth, int w, int h, V a, V b, V c, Mesh m, V[] wn, int[] t, V l1, V l2) {
		if (a.z <= 0.05 || b.z <= 0.05 || c.z <= 0.05) {
			return;
		}
		int minX = (int) Math.max(0, Math.floor(Math.min(a.x, Math.min(b.x, c.x))));
		int maxX = (int) Math.min(w - 1, Math.ceil(Math.max(a.x, Math.max(b.x, c.x))));
		int minY = (int) Math.max(0, Math.floor(Math.min(a.y, Math.min(b.y, c.y))));
		int maxY = (int) Math.min(h - 1, Math.ceil(Math.max(a.y, Math.max(b.y, c.y))));
		double area = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
		if (Math.abs(area) < 1e-9) {
			return;
		}
		for (int y = minY; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				double px = x + 0.5;
				double py = y + 0.5;
				double w0 = ((b.x - px) * (c.y - py) - (b.y - py) * (c.x - px)) / area;
				double w1 = ((c.x - px) * (a.y - py) - (c.y - py) * (a.x - px)) / area;
				double w2 = 1 - w0 - w1;
				if (w0 < 0 || w1 < 0 || w2 < 0) {
					continue;
				}
				double z = w0 * a.z + w1 * b.z + w2 * c.z;
				int idx = x + y * w;
				if (z >= depth[idx]) {
					continue;
				}
				depth[idx] = z;
				V n = wn[t[0]].mul(w0).add(wn[t[1]].mul(w1)).add(wn[t[2]].mul(w2)).norm();
				double light = l1 == null ? 1.0 : Math.min(1.0, 0.42 + 0.45 * Math.max(0, n.dot(l1)) + 0.45 * Math.max(0, n.dot(l2)));
				double[] c0 = m.color.get(t[0]);
				double[] c1 = m.color.get(t[1]);
				double[] c2 = m.color.get(t[2]);
				int r = (int) Math.min(255, (w0 * c0[0] + w1 * c1[0] + w2 * c2[0]) * light * 255);
				int g = (int) Math.min(255, (w0 * c0[1] + w1 * c1[1] + w2 * c2[1]) * light * 255);
				int bl = (int) Math.min(255, (w0 * c0[2] + w1 * c1[2] + w2 * c2[2]) * light * 255);
				img.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | bl);
			}
		}
	}

	// ================================================================== mesh

	static final class Mesh {
		final List<V> pos = new ArrayList<>();
		final List<V> nrm = new ArrayList<>();
		final List<Integer> material = new ArrayList<>();
		final List<Double> extra = new ArrayList<>();
		final List<double[]> color = new ArrayList<>();
		final List<int[]> quads = new ArrayList<>();

		int add(V p, V n, int mat, double ex) {
			pos.add(p);
			nrm.add(n);
			material.add(mat);
			extra.add(ex);
			return pos.size() - 1;
		}

		void quad(int a, int b, int c, int d) {
			quads.add(new int[] {a, b, c, d});
		}

		/** Adds another mesh's geometry, giving all of it {@code mat}. */
		void append(Mesh other, int mat) {
			int offset = pos.size();
			for (int i = 0; i < other.pos.size(); i++) {
				add(other.pos.get(i), other.nrm.get(i), mat, other.extra.get(i));
			}
			for (int[] q : other.quads) {
				quad(q[0] + offset, q[1] + offset, q[2] + offset, q[3] + offset);
			}
		}

		/** Makes every quad wind counter-clockwise when seen from outside (the side its vertex normals point to). */
		void orientQuads() {
			for (int[] q : quads) {
				V p0 = pos.get(q[0]);
				V faceNormal = pos.get(q[1]).sub(p0).cross(pos.get(q[2]).sub(p0));
				if (q[2] != q[3]) {
					faceNormal = faceNormal.add(pos.get(q[2]).sub(p0).cross(pos.get(q[3]).sub(p0)));
				}
				V avg = nrm.get(q[0]).add(nrm.get(q[1])).add(nrm.get(q[2])).add(nrm.get(q[3]));
				if (faceNormal.dot(avg) < 0) {
					if (q[2] == q[3]) {
						int tmp = q[0];
						q[0] = q[1];
						q[1] = tmp;
					} else {
						int tmp = q[1];
						q[1] = q[3];
						q[3] = tmp;
					}
				}
			}
		}
	}

	// ================================================================== math

	record V(double x, double y, double z) {
		V add(V o) {
			return new V(x + o.x, y + o.y, z + o.z);
		}

		V sub(V o) {
			return new V(x - o.x, y - o.y, z - o.z);
		}

		V mul(double s) {
			return new V(x * s, y * s, z * s);
		}

		double dot(V o) {
			return x * o.x + y * o.y + z * o.z;
		}

		V cross(V o) {
			return new V(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
		}

		double len() {
			return Math.sqrt(dot(this));
		}

		V norm() {
			double l = len();
			return l < 1e-12 ? new V(0, 1, 0) : mul(1 / l);
		}

		V withX(double nx) {
			return new V(nx, y, z);
		}

		V abs() {
			return new V(Math.abs(x), Math.abs(y), Math.abs(z));
		}
	}

	static V v(double x, double y, double z) {
		return new V(x, y, z);
	}

	static V rotX(V p, double a) {
		double c = Math.cos(a);
		double s = Math.sin(a);
		return v(p.x, p.y * c - p.z * s, p.y * s + p.z * c);
	}

	static V rotY(V p, double a) {
		double c = Math.cos(a);
		double s = Math.sin(a);
		return v(p.x * c + p.z * s, p.y, -p.x * s + p.z * c);
	}

	static V rotZ(V p, double a) {
		double c = Math.cos(a);
		double s = Math.sin(a);
		return v(p.x * c - p.y * s, p.x * s + p.y * c, p.z);
	}

	static double ellipsoid(V p, V c, V r) {
		V q = p.sub(c);
		double k0 = v(q.x / r.x, q.y / r.y, q.z / r.z).len();
		double k1 = v(q.x / (r.x * r.x), q.y / (r.y * r.y), q.z / (r.z * r.z)).len();
		return k1 < 1e-12 ? -Math.min(r.x, Math.min(r.y, r.z)) : k0 * (k0 - 1) / k1;
	}

	/** Box with rounded edges: center, half size, edge radius. */
	static double roundBox(V p, V c, V h, double r) {
		V q = p.sub(c).abs().sub(h).add(v(r, r, r));
		V outside = v(Math.max(q.x, 0), Math.max(q.y, 0), Math.max(q.z, 0));
		return outside.len() + Math.min(Math.max(q.x, Math.max(q.y, q.z)), 0) - r;
	}

	/** Upright cylinder with rounded edges. */
	static double roundCylinderY(V p, V c, double radius, double halfHeight, double r) {
		double dx = Math.sqrt(sq(p.x - c.x) + sq(p.z - c.z)) - radius + r;
		double dy = Math.abs(p.y - c.y) - halfHeight + r;
		return Math.min(Math.max(dx, dy), 0) + Math.sqrt(sq(Math.max(dx, 0)) + sq(Math.max(dy, 0))) - r;
	}

	/** Cylinder along X with rounded edges. */
	static double roundCylinderX(V p, V c, double radius, double halfLength, double r) {
		return roundCylinderY(v(p.y - c.y + c.x, p.x - c.x + c.y, p.z), v(c.x, c.y, c.z), radius, halfLength, r);
	}

	/** Cone with rounded ends (Inigo Quilez). */
	static double roundCone(V p, V a, V b, double r1, double r2) {
		V ba = b.sub(a);
		double l2 = ba.dot(ba);
		double rr = r1 - r2;
		double a2 = l2 - rr * rr;
		double il2 = 1.0 / l2;
		V pa = p.sub(a);
		double y = pa.dot(ba);
		double z = y - l2;
		V xv = pa.mul(l2).sub(ba.mul(y));
		double x2 = xv.dot(xv);
		double y2 = y * y * l2;
		double z2 = z * z * l2;
		double k = Math.signum(rr) * rr * rr * x2;
		if (Math.signum(z) * a2 * z2 > k) {
			return Math.sqrt(x2 + z2) * il2 - r2;
		}
		if (Math.signum(y) * a2 * y2 < k) {
			return Math.sqrt(x2 + y2) * il2 - r1;
		}
		return (Math.sqrt(x2 * a2 * il2) + y * rr) * il2 - r1;
	}

	static double smin(double a, double b, double k) {
		double h = Math.max(k - Math.abs(a - b), 0) / k;
		return Math.min(a, b) - h * h * k * 0.25;
	}

	static double smax(double a, double b, double k) {
		return -smin(-a, -b, k);
	}

	static double noise(V p) {
		int xi = (int) Math.floor(p.x);
		int yi = (int) Math.floor(p.y);
		int zi = (int) Math.floor(p.z);
		double fx = p.x - xi;
		double fy = p.y - yi;
		double fz = p.z - zi;
		fx = fx * fx * (3 - 2 * fx);
		fy = fy * fy * (3 - 2 * fy);
		fz = fz * fz * (3 - 2 * fz);
		double result = 0;
		for (int c = 0; c < 8; c++) {
			int dx = c & 1;
			int dy = (c >> 1) & 1;
			int dz = (c >> 2) & 1;
			double weight = (dx == 1 ? fx : 1 - fx) * (dy == 1 ? fy : 1 - fy) * (dz == 1 ? fz : 1 - fz);
			result += weight * hash(xi + dx, yi + dy, zi + dz);
		}
		return result * 2 - 1;
	}

	static double hash(int x, int y, int z) {
		long h = x * 374761393L + y * 668265263L + z * 2147483647L;
		h = (h ^ (h >>> 13)) * 1274126177L;
		return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
	}

	static double[] rgb(double r, double g, double b) {
		return new double[] {clamp01(r), clamp01(g), clamp01(b)};
	}

	static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	static double clamp01(double v) {
		return clamp(v, 0, 1);
	}

	static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	static double sq(double v) {
		return v * v;
	}

	static double smoothstep(double e0, double e1, double x) {
		double t = clamp01((x - e0) / (e1 - e0));
		return t * t * (3 - 2 * t);
	}
}
