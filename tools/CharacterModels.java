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
		for (Design design : List.of(new Alien(), new Robot(), new Frog(), new Burger(), new Penguin(), new SpongeBob(), new SkinnyAlien(), new SixSeven())) {
			if (only != null && !only.equalsIgnoreCase(design.name())) {
				continue;
			}
			Map<String, Mesh> parts = design.build();
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

	// ================================================================== designs

	/** Joints and special points in the final pose, written to character.json. */
	record Rig(double jawAngle, V head, V jaw, V body, V rightArm, V rightHand, V leftArm, V leftHand, V rightLeg, V leftLeg, V butt) {
	}

	abstract static class Design {
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
	}

	/** Parts that are only drawn during the middle finger emote: an angry face, and a right arm making the gesture. */
	static boolean isEmotePart(String name) {
		return name.startsWith("mad_") || name.equals("middle_finger");
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
		static final int SKIN = 0, EYE = 1, TEETH = 2, MOUTH = 3, CLAW = 4, LIP = 5;

		@Override
		String name() {
			return "Alien";
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
			parts.put("body", surfaceNets(Alien::bodySdf, v(-0.24, 0.84, -0.14), v(0.24, 1.70, 0.14), 0.0215));
			parts.put("right_arm", buildArm(-1, false));
			parts.put("left_arm", buildArm(1, false));
			parts.put("right_leg", buildLeg(-1));
			parts.put("left_leg", buildLeg(1));
			parts.put("mad_head", buildMadHead());
			parts.put("middle_finger", buildArm(-1, true));
			for (Map.Entry<String, Mesh> part : parts.entrySet()) {
				String name = part.getKey();
				Mesh m = part.getValue();
				m.orientQuads();
				for (int i = 0; i < m.pos.size(); i++) {
					V p = m.pos.get(i);
					int material = m.material.get(i);
					boolean onCut = Math.abs(jawRegion(p)) < 0.009 && skull(p) < -0.002;
					if ((name.equals("head") || name.equals("jaw")) && material == SKIN && (skull(p) < -0.006 || cavity(p) < 0.008 || onCut)) {
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
			Mesh m = surfaceNets(p -> smax(smax(skull(p), -jawRegion(p), 0.012), -cavity(p), 0.012), v(-0.24, 1.55, -0.30), v(0.24, 2.03, 0.24), 0.0185);
			for (int s = -1; s <= 1; s += 2) {
				addEye(m, s);
			}
			addTeeth(m, true);
			return m;
		}

		static Mesh buildJaw() {
			Mesh m = surfaceNets(p -> smax(smax(skull(p), jawRegion(p), 0.012), -cavity(p), 0.012), v(-0.20, 1.53, -0.06), v(0.20, 1.76, 0.23), 0.0145);
			addTeeth(m, false);
			return m;
		}

		static double eyeSdf(V p, double grow, double pushBack) {
			V q = rotZ(rotY(p.sub(EYE_C.add(v(0, 0, -pushBack))), -EYE_YAW), -EYE_ROLL);
			return ellipsoid(q, v(0, 0, 0), EYE_R.mul(grow));
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

		static Mesh buildArm(int side, boolean middleFinger) {
			Mesh m = new Mesh();
			double s = side;
			addSphere(m, v(s * 0.185, 1.415, -0.010), 0.042, SKIN);
			addTube(m, path(v(s * 0.19, 1.40, -0.01), v(s * 0.205, 1.24, -0.006), v(s * 0.215, 1.085, 0.0)), radii(0.035, 0.031, 0.025), 8, false, SKIN, CLAW);
			addSphere(m, v(s * 0.215, 1.085, 0.0), 0.029, SKIN);
			addTube(m, path(v(s * 0.215, 1.085, 0.0), v(s * 0.222, 0.95, 0.018), v(s * 0.226, 0.785, 0.045)), radii(0.027, 0.024, 0.017), 8, false, SKIN, CLAW);
			addSphere(m, v(s * 0.226, 0.785, 0.045), 0.019, SKIN);
			if (middleFinger) {
				addFist(m, v(s * 0.228, 0.75, 0.058), v(0.02, 0.034, 0.03), 0.0078, 0.088, SKIN, SKIN);
				addTube(m, path(v(s * 0.228, 0.75 + 0.0088 - 0.0068, 0.058 + 0.03 * 0.55 + 0.086), v(s * 0.228, 0.75 - 0.0062, 0.058 + 0.0165 + 0.106),
					v(s * 0.228, 0.75 - 0.0055, 0.058 + 0.0165 + 0.12)), radii(0.0062, 0.0045, 0.002), 5, true, SKIN, CLAW);
				return m;
			}
			addEllipsoid(m, v(s * 0.228, 0.745, 0.056), v(0.029, 0.042, 0.016), SKIN);
			for (int f = -1; f <= 1; f++) {
				double z = 0.056 + f * 0.017;
				double reach = f == 0 ? 0.0 : 0.012;
				addClawFinger(m, path(v(s * 0.228, 0.715, z), v(s * 0.234, 0.64 + reach, z + 0.010), v(s * 0.229, 0.552 + reach * 1.5, z + 0.024)), 0.0074);
			}
			addClawFinger(m, path(v(s * 0.220, 0.742, 0.078), v(s * 0.213, 0.690, 0.103), v(s * 0.210, 0.652, 0.112)), 0.0068);
			return m;
		}

		static Mesh buildLeg(int side) {
			Mesh m = new Mesh();
			double s = side;
			addSphere(m, v(s * 0.072, 0.93, 0.0), 0.056, SKIN);
			addTube(m, path(v(s * 0.073, 0.93, 0.0), v(s * 0.078, 0.73, 0.012), v(s * 0.083, 0.525, 0.028)), radii(0.054, 0.046, 0.035), 9, false, SKIN, CLAW);
			addSphere(m, v(s * 0.083, 0.525, 0.028), 0.038, SKIN);
			addTube(m, path(v(s * 0.083, 0.525, 0.024), v(s * 0.085, 0.36, 0.0), v(s * 0.086, 0.09, -0.008)), radii(0.034, 0.033, 0.021), 8, false, SKIN, CLAW);
			addSphere(m, v(s * 0.086, 0.09, -0.008), 0.024, SKIN);
			addEllipsoid(m, v(s * 0.086, 0.042, -0.010), v(0.033, 0.040, 0.027), SKIN);
			for (int t = -1; t <= 1; t++) {
				double dx = t * 0.014;
				addClawFinger(m, path(v(s * (0.086 + dx), 0.036, 0.012), v(s * (0.086 + dx * 1.5), 0.020, 0.072), v(s * (0.086 + dx * 2.0), 0.009, 0.128 - Math.abs(t) * 0.012)), 0.012);
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

		/** Leans the upper body forward; the head rides along but stays looking ahead, arms hang straight. */
		static void hunch(String part, Mesh m) {
			if (part.endsWith("_leg")) {
				return;
			}
			for (int i = 0; i < m.pos.size(); i++) {
				V p = m.pos.get(i);
				switch (part) {
					case "head", "jaw", "mad_head" -> {
						m.pos.set(i, headPose(p));
						m.nrm.set(i, rotX(m.nrm.get(i), HEAD_TILT));
					}
					case "right_arm", "middle_finger" -> m.pos.set(i, p.add(hunched(RIGHT_SHOULDER).sub(RIGHT_SHOULDER)));
					case "left_arm" -> m.pos.set(i, p.add(hunched(LEFT_SHOULDER).sub(LEFT_SHOULDER)));
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

	/** A friendly metal robot: boxy head with a visor and glowing eyes, a jaw that drops open, chest lights, gripper hands. */
	static final class Robot extends Design {
		static final V HEAD_C = v(0, 1.70, 0.0);
		static final V HEAD_H = v(0.17, 0.145, 0.15);
		static final double MOUTH_Y = 1.615;
		static final double JAW_BACK_Z = -0.07;
		static final V JAW_PIVOT = v(0, 1.66, -0.06);
		static final V CAVITY_C = v(0, 1.617, 0.045);
		static final V CAVITY_H = v(0.118, 0.032, 0.085);
		static final int METAL = 0, DARK = 1, GLOW = 2, VISOR = 3, RED = 4, YELLOW = 5, GREEN = 6, TEETH = 7, MOUTH = 8;

		@Override
		String name() {
			return "Robot";
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

		static double head(V p) {
			double d = roundBox(p, HEAD_C, HEAD_H, 0.06);
			// visor recess across the face
			d = smax(d, -roundBox(p, v(0, 1.735, 0.155), v(0.135, 0.045, 0.03), 0.025), 0.01);
			for (int s = -1; s <= 1; s += 2) { // ear bolts, also the jaw hinge
				d = smin(d, roundCylinderX(p, v(s * 0.175, 1.66, -0.06), 0.045, 0.02, 0.008), 0.01);
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
			d = Math.min(d, roundCylinderY(p, v(0, 1.07, 0), 0.10, 0.05, 0.02));                   // waist joint
			d = smin(d, roundBox(p, v(0, 0.955, 0), v(0.17, 0.07, 0.11), 0.035), 0.02);             // hips
			d = Math.min(d, roundCylinderY(p, v(0, 1.50, -0.005), 0.055, 0.055, 0.015));           // neck
			return d;
		}

		static double leg(V p, double s) {
			double d = roundCylinderY(p, v(s * 0.105, 0.725, 0), 0.052, 0.17, 0.02);                // thigh
			d = Math.min(d, ellipsoid(p, v(s * 0.105, 0.535, 0.012), v(0.068, 0.062, 0.068)));     // knee
			d = Math.min(d, roundBox(p, v(s * 0.105, 0.30, 0.0), v(0.063, 0.205, 0.068), 0.03));   // shin
			d = Math.min(d, roundBox(p, v(s * 0.105, 0.047, 0.045), v(0.078, 0.047, 0.125), 0.03)); // foot
			return d;
		}

		static double arm(V p, double s) {
			double d = ellipsoid(p, v(s * 0.262, 1.40, 0), v(0.075, 0.075, 0.075));               // shoulder
			d = Math.min(d, roundCylinderY(p, v(s * 0.272, 1.215, 0), 0.042, 0.13, 0.015));         // upper arm
			d = Math.min(d, ellipsoid(p, v(s * 0.272, 1.065, 0.005), v(0.052, 0.052, 0.052)));     // elbow
			d = Math.min(d, roundBox(p, v(s * 0.272, 0.90, 0.02), v(0.055, 0.13, 0.058), 0.028)); // forearm
			d = Math.min(d, roundBox(p, v(s * 0.272, 0.725, 0.035), v(0.03, 0.045, 0.046), 0.015)); // palm
			return d;
		}

		@Override
		Map<String, Mesh> build() {
			Map<String, Mesh> parts = new LinkedHashMap<>();
			Mesh headMesh = surfaceNets(p -> smax(smax(head(p), -jawRegion(p), 0.006), -cavity(p), 0.006), v(-0.24, 1.53, -0.20), v(0.24, 1.88, 0.22), 0.019);
			addEyes(headMesh);
			addAntenna(headMesh);
			addTeethRow(headMesh, true);
			parts.put("head", finish(headMesh, (p, n, m, e) -> paintHead(p, n, m, true)));

			Mesh jawMesh = surfaceNets(p -> smax(smax(head(p), jawRegion(p), 0.006), -cavity(p), 0.006), v(-0.20, 1.53, -0.09), v(0.20, 1.66, 0.19), 0.016);
			addTeethRow(jawMesh, false);
			parts.put("jaw", finish(jawMesh, (p, n, m, e) -> paintHead(p, n, m, false)));

			Mesh bodyMesh = surfaceNets(Robot::body, v(-0.27, 0.87, -0.16), v(0.27, 1.57, 0.17), 0.022);
			int[] lights = {RED, YELLOW, GREEN, GLOW};
			for (int i = 0; i < 4; i++) {
				addEllipsoid(bodyMesh, v(-0.06 + i * 0.04, 1.31, 0.118), v(0.008, 0.014, 0.014), lights[i]);
			}
			for (int i = 0; i < 3; i++) { // speaker grille slots
				addEllipsoid(bodyMesh, v(0, 1.245 - i * 0.022, 0.116), v(0.006, 0.0045, 0.06), DARK);
			}
			parts.put("body", finish(bodyMesh, (p, n, m, e) -> paintBody(p, n, m)));

			for (int side = -1; side <= 1; side += 2) {
				double s = side;
				Mesh arm = surfaceNets(p -> arm(p, s), v(s * 0.272 - 0.09, 0.66, -0.09), v(s * 0.272 + 0.09, 1.49, 0.10), 0.02);
				for (int f = -1; f <= 1; f += 2) { // two gripper fingers and a thumb
					addTube(arm, path(v(s * (0.272 + f * 0.018), 0.69, 0.05), v(s * (0.272 + f * 0.022), 0.635, 0.075), v(s * (0.272 + f * 0.016), 0.60, 0.09)), radii(0.011, 0.01, 0.008), 6, false, DARK, DARK);
				}
				addTube(arm, path(v(s * 0.255, 0.71, 0.075), v(s * 0.25, 0.67, 0.105), v(s * 0.255, 0.645, 0.115)), radii(0.01, 0.009, 0.007), 6, false, DARK, DARK);
				parts.put(side < 0 ? "right_arm" : "left_arm", finish(arm, (p, n, m, e) -> paintLimb(p, n, m)));

				Mesh leg = surfaceNets(p -> leg(p, s), v(s * 0.105 - 0.10, -0.01, -0.10), v(s * 0.105 + 0.10, 0.92, 0.19), 0.021);
				parts.put(side < 0 ? "right_leg" : "left_leg", finish(leg, (p, n, m, e) -> paintLimb(p, n, m)));
			}

			// Angry: the eyes glow red under slanted shutters.
			Mesh mad = new Mesh();
			for (int s = -1; s <= 1; s += 2) {
				addEllipsoid(mad, v(s * 0.058, 1.738, 0.1395), v(0.0128, 0.0275, 0.0355), RED);
				addTube(mad, path(v(s * 0.104, 1.771, 0.153), v(s * 0.06, 1.762, 0.154), v(s * 0.018, 1.745, 0.153)), radii(0.012, 0.012, 0.011), 6, false, DARK, DARK);
			}
			parts.put("mad_head", finish(mad, (p, n, m, e) -> paintMetal(p, n, m)));

			// The right arm with one gripper finger sticking out.
			double s = -1;
			Mesh finger = surfaceNets(p -> arm(p, s), v(s * 0.272 - 0.09, 0.66, -0.09), v(s * 0.272 + 0.09, 1.49, 0.10), 0.02);
			for (int f = -1; f <= 1; f += 2) {
				addTube(finger, path(v(s * (0.272 + f * 0.02), 0.695, 0.06), v(s * (0.272 + f * 0.021), 0.678, 0.083), v(s * (0.272 + f * 0.02), 0.688, 0.092)), radii(0.011, 0.01, 0.009), 6, false, DARK, DARK);
			}
			addTube(finger, path(v(s * 0.272, 0.705, 0.07), v(s * 0.272, 0.708, 0.135), v(s * 0.272, 0.71, 0.195)), radii(0.018, 0.017, 0.016), 8, false, METAL, METAL);
			parts.put("middle_finger", finish(finger, (p, n, m, e) -> paintLimb(p, n, m)));
			return parts;
		}

		static void addEyes(Mesh m) {
			for (int s = -1; s <= 1; s += 2) {
				addEllipsoid(m, v(s * 0.058, 1.738, 0.138), v(0.012, 0.026, 0.034), GLOW);
			}
		}

		static void addAntenna(Mesh m) {
			addTube(m, path(v(0, 1.83, -0.02), v(0, 1.90, -0.025), v(0, 1.97, -0.03)), radii(0.011, 0.009, 0.008), 6, false, DARK, DARK);
			addSphere(m, v(0, 1.985, -0.03), 0.026, RED);
		}

		/** Square metal teeth along the mouth edge. */
		static void addTeethRow(Mesh m, boolean upper) {
			for (int i = 0; i < 7; i++) {
				double x = -0.09 + i * 0.03;
				double y = upper ? CAVITY_C.y + CAVITY_H.y - 0.006 : CAVITY_C.y - CAVITY_H.y + 0.006;
				addBox(m, v(x, y + (upper ? -0.013 : 0.013), 0.112), v(0.011, 0.014, 0.008), TEETH);
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
			return paintMetal(p, n, material);
		}

		double[] paintBody(V p, V n, int material) {
			if (material == METAL && p.z > 0.11 && Math.abs(p.x) < 0.12 && Math.abs(p.y - 1.28) < 0.09) {
				material = DARK;
			}
			if (material == METAL && (Math.abs(p.y - 1.07) < 0.06 || p.y > 1.44)) {
				material = DARK; // waist and neck joints
			}
			return paintMetal(p, n, material);
		}

		double[] paintLimb(V p, V n, int material) {
			if (material == METAL) {
				double y = p.y;
				boolean joint = Math.abs(y - 1.065) < 0.05 || Math.abs(y - 0.535) < 0.05 || Math.abs(y - 1.40) < 0.06 && Math.abs(p.x) > 0.2;
				if (joint) {
					material = DARK;
				}
			}
			return paintMetal(p, n, material);
		}

		static double[] paintMetal(V p, V n, int material) {
			double brushed = noise(v(p.x * 60, p.y * 6, p.z * 60)) * 0.035 + noise(p.mul(14)) * 0.03;
			double rim = Math.pow(1 - Math.abs(n.y), 2) * 0.05;
			return switch (material) {
				case DARK -> rgb(0.26 + brushed, 0.28 + brushed, 0.31 + brushed);
				case GLOW -> rgb(0.45, 1.0, 1.0);
				case VISOR -> {
					double shine = Math.pow(Math.max(0, n.dot(v(0.3, 0.8, 0.5).norm())), 10) * 0.35;
					yield rgb(0.05 + shine, 0.07 + shine, 0.10 + shine);
				}
				case RED -> rgb(1.0, 0.25, 0.22);
				case YELLOW -> rgb(1.0, 0.85, 0.25);
				case GREEN -> rgb(0.35, 1.0, 0.35);
				case TEETH -> rgb(0.85, 0.88, 0.9);
				default -> rgb(0.64 + brushed - rim, 0.68 + brushed - rim, 0.72 + brushed - rim);
			};
		}
	}

	// ================================================================== Frog

	/** A cheerful upright frog with big eyes on top, a wide mouth with a tongue, a pale belly and webbed feet. */
	static final class Frog extends Design {
		static final double MOUTH_Y = 1.235;
		static final double MOUTH_FRONT_Z = 0.22;
		static final double CUT_SLOPE = 0.20;
		static final double JAW_BACK_Z = -0.10;
		static final V JAW_PIVOT = v(0, 1.275, -0.07);
		static final V CAVITY_C = v(0, 1.238, 0.065);
		static final V CAVITY_R = v(0.185, 0.034, 0.13);
		static final V EYE_L = v(0.125, 1.405, 0.085);
		static final int SKIN = 0, WHITE = 1, PUPIL = 2, MOUTH = 3, TONGUE = 4, PAD = 5;

		@Override
		String name() {
			return "Frog";
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
			}
			return d;
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
			return smax(d, p.y - top, 0.04);
		}

		@Override
		Map<String, Mesh> build() {
			Map<String, Mesh> parts = new LinkedHashMap<>();
			Mesh head = surfaceNets(p -> smax(smax(skull(p), -jawRegion(p), 0.01), -cavity(p), 0.01), v(-0.27, 1.12, -0.22), v(0.27, 1.50, 0.25), 0.019);
			for (int s = -1; s <= 1; s += 2) {
				V eye = EYE_L.withX(s * EYE_L.x);
				addEllipsoid(head, eye.add(v(0, 0.004, 0.01)), v(0.058, 0.056, 0.058), WHITE);
				addEllipsoid(head, eye.add(v(s * 0.006, 0.006, 0.058)), v(0.012, 0.022, 0.026), PUPIL);
			}
			parts.put("head", finish(head, this::paintHead));

			Mesh jaw = surfaceNets(p -> smax(smax(skull(p), jawRegion(p), 0.01), -cavity(p), 0.01), v(-0.25, 1.12, -0.13), v(0.25, 1.30, 0.25), 0.016);
			addEllipsoid(jaw, v(0, 1.212, 0.07), v(0.085, 0.016, 0.075), TONGUE);
			parts.put("jaw", finish(jaw, this::paintHead));

			parts.put("body", finish(surfaceNets(Frog::body, v(-0.24, 0.56, -0.20), v(0.24, 1.25, 0.21), 0.022), this::paintBody));

			for (int side = -1; side <= 1; side += 2) {
				double s = side;
				Mesh arm = new Mesh();
				addSphere(arm, v(s * 0.17, 1.03, 0.0), 0.045, SKIN);
				addTube(arm, path(v(s * 0.17, 1.03, 0.0), v(s * 0.205, 0.93, 0.03), v(s * 0.222, 0.86, 0.05)), radii(0.038, 0.033, 0.03), 9, false, SKIN, SKIN);
				addSphere(arm, v(s * 0.222, 0.86, 0.05), 0.03, SKIN);
				addTube(arm, path(v(s * 0.222, 0.86, 0.05), v(s * 0.218, 0.78, 0.08), v(s * 0.212, 0.72, 0.105)), radii(0.03, 0.027, 0.024), 9, false, SKIN, SKIN);
				addEllipsoid(arm, v(s * 0.212, 0.705, 0.115), v(0.035, 0.03, 0.03), SKIN);
				for (int f = 0; f < 4; f++) {
					double a = Math.toRadians(-50 + f * 33);
					V tip = v(s * (0.212 + Math.sin(a) * 0.045), 0.645, 0.13 + Math.cos(a) * 0.035);
					addTube(arm, path(v(s * 0.212, 0.70, 0.12), v(s * (0.212 + Math.sin(a) * 0.028), 0.672, 0.125 + Math.cos(a) * 0.02), tip), radii(0.011, 0.009, 0.008), 6, false, SKIN, SKIN);
					addSphere(arm, tip, 0.014, PAD);
				}
				parts.put(side < 0 ? "right_arm" : "left_arm", finish(arm, this::paintLimb));

				Mesh leg = new Mesh();
				addEllipsoid(leg, v(s * 0.115, 0.62, 0.0), v(0.10, 0.09, 0.085), SKIN);
				addTube(leg, path(v(s * 0.115, 0.62, 0.02), v(s * 0.145, 0.50, 0.11), v(s * 0.165, 0.40, 0.15)), radii(0.088, 0.072, 0.058), 11, false, SKIN, SKIN);
				addSphere(leg, v(s * 0.165, 0.40, 0.15), 0.058, SKIN);
				addTube(leg, path(v(s * 0.165, 0.40, 0.15), v(s * 0.15, 0.24, 0.05), v(s * 0.13, 0.08, -0.02)), radii(0.056, 0.045, 0.034), 10, false, SKIN, SKIN);
				addSphere(leg, v(s * 0.13, 0.07, -0.02), 0.032, SKIN);
				addEllipsoid(leg, v(s * 0.135, 0.028, 0.06), v(0.11, 0.026, 0.07), SKIN);
				for (int t = 0; t < 3; t++) {
					double a = Math.toRadians(-30 + t * 30);
					V tip = v(s * (0.135 + Math.sin(a) * 0.09), 0.018, 0.08 + Math.cos(a) * 0.12);
					addTube(leg, path(v(s * 0.135, 0.03, 0.09), v(s * (0.135 + Math.sin(a) * 0.05), 0.022, 0.09 + Math.cos(a) * 0.07), tip), radii(0.016, 0.013, 0.011), 6, false, SKIN, SKIN);
					addSphere(leg, tip, 0.02, PAD);
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

			double s = -1;
			Mesh finger = new Mesh();
			addSphere(finger, v(s * 0.17, 1.03, 0.0), 0.045, SKIN);
			addTube(finger, path(v(s * 0.17, 1.03, 0.0), v(s * 0.205, 0.93, 0.03), v(s * 0.222, 0.86, 0.05)), radii(0.038, 0.033, 0.03), 9, false, SKIN, SKIN);
			addSphere(finger, v(s * 0.222, 0.86, 0.05), 0.03, SKIN);
			addTube(finger, path(v(s * 0.222, 0.86, 0.05), v(s * 0.218, 0.78, 0.08), v(s * 0.212, 0.72, 0.105)), radii(0.03, 0.027, 0.024), 9, false, SKIN, SKIN);
			addFist(finger, v(s * 0.212, 0.695, 0.115), v(0.031, 0.034, 0.034), 0.0105, 0.07, SKIN, SKIN);
			addSphere(finger, v(s * 0.212, 0.695 - 0.034 * 0.2 + 0.007, 0.115 + 0.034 * 0.55 + 0.07), 0.0135, PAD);
			parts.put("middle_finger", finish(finger, this::paintLimb));
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
				case PUPIL:
					return glossyBlack(n);
				case TONGUE:
					return rgb(0.95, 0.45, 0.55);
				default:
					break;
			}
			boolean inside = skull(p) < -0.006 || cavity(p) < 0.008 || Math.abs(jawRegion(p)) < 0.008 && skull(p) < -0.002;
			if (inside) {
				double back = clamp01((0.18 - p.z) / 0.25);
				return rgb(0.78 - 0.35 * back, 0.30 - 0.15 * back, 0.36 - 0.15 * back);
			}
			if (Math.abs(cutPlane(p)) < 0.012 && p.z > 0.0) {
				return rgb(0.16, 0.30, 0.10); // mouth line
			}
			double[] c = green(p, n, 1);
			for (int s = -1; s <= 1; s += 2) {
				double blush = 1 - smoothstep(0.0, 0.05, p.sub(v(s * 0.175, 1.255, 0.14)).len());
				c = mix(c, rgb(0.92, 0.55, 0.58), blush * 0.85);
				double nostril = p.sub(v(s * 0.035, 1.32, 0.205)).len();
				if (nostril < 0.012) {
					c = mix(c, rgb(0.1, 0.18, 0.06), 1 - nostril / 0.012);
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
			return green(p, n, 1);
		}
	}

	// ================================================================== Burger

	/**
	 * A burger with arms and legs: bottom bun, patty, cheese, lettuce, tomato and a sesame top bun with big eyes.
	 * The top bun is the "jaw": it lifts open (upward) while he talks, showing teeth and a tongue.
	 */
	static final class Burger extends Design {
		static final double TOP_BASE_Y = 0.905;
		static final V HINGE = v(0, TOP_BASE_Y, -0.36);
		static final int BUN = 0, BUN_INSIDE = 1, PATTY = 2, CHEESE = 3, LETTUCE = 4, TOMATO = 5, SEED = 6, WHITE = 7, PUPIL = 8,
			BROW = 9, TEETH = 10, TONGUE = 11, LIMB = 12, GLOVE = 13, SHOE = 14, SOLE = 15;

		@Override
		String name() {
			return "Burger";
		}

		@Override
		double height() {
			return 1.35;
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
			return roundBox(q.add(v(0, droop, 0)), v(0, 0.835, 0), v(0.40, 0.013, 0.40), 0.008);
		}

		static double lettuce(V p) {
			double angle = Math.atan2(p.z, p.x);
			double radial = Math.sqrt(p.x * p.x + p.z * p.z);
			double wave = Math.sin(angle * 13) * 0.012 * smoothstep(0.3, 0.45, radial);
			double edge = 0.445 + Math.sin(angle * 9 + 1) * 0.015;
			V q = v(p.x, p.y - wave, p.z);
			double flat = Math.max(Math.abs(q.y - 0.860) - 0.012, radial - edge);
			return flat - 0.004;
		}

		static double tomato(V p) {
			double d = 1e9;
			double[][] slices = {{-0.22, 0.17}, {0.21, 0.19}, {0.02, -0.25}};
			for (double[] s : slices) {
				d = Math.min(d, roundCylinderY(p, v(s[0], 0.876, s[1]), 0.15, 0.012, 0.008));
			}
			return d;
		}

		static double stack(V p) {
			return Math.min(Math.min(Math.min(bottomBun(p), patty(p)), Math.min(cheese(p), lettuce(p))), tomato(p));
		}

		/** Which layer of the stack a point belongs to. */
		static int layerAt(V p) {
			double[] d = {bottomBun(p), patty(p), cheese(p), lettuce(p), tomato(p)};
			int[] layers = {BUN, PATTY, CHEESE, LETTUCE, TOMATO};
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
			Mesh body = surfaceNets(Burger::stack, v(-0.58, 0.58, -0.58), v(0.58, 0.91, 0.58), 0.025);
			addEllipsoid(body, v(0, 0.884, 0.20), v(0.11, 0.012, 0.09), TONGUE);
			parts.put("body", finish(body, (p, n, m, e) -> paint(p, n, m == TONGUE ? TONGUE : layerAt(p), e)));

			// Jaw: the top bun with eyes, eyebrows, seeds and teeth.
			Mesh top = new Mesh();
			top.append(surfaceNets(Burger::topBun, v(-0.44, TOP_BASE_Y - 0.03, -0.44), v(0.44, 1.26, 0.44), 0.029), BUN);
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
			parts.put("mad_jaw", finish(mad, (p, n, m, e) -> m == BUN ? lidColor(n) : paint(p, n, m, e)));

			for (int side = -1; side <= 1; side += 2) {
				double s = side;
				Mesh arm = new Mesh();
				addTube(arm, path(v(s * 0.40, 0.785, 0), v(s * 0.49, 0.66, 0.03), v(s * 0.525, 0.50, 0.075)), radii(0.026, 0.024, 0.022), 8, false, LIMB, LIMB);
				addTorusY(arm, v(s * 0.527, 0.50, 0.078), 0.034, 0.012, GLOVE); // cuff
				V palm = v(s * 0.53, 0.445, 0.095);
				addEllipsoid(arm, palm, v(0.035, 0.05, 0.045), GLOVE);
				for (int f = 0; f < 3; f++) {
					double z = 0.095 + (f - 1) * 0.024;
					addTube(arm, path(v(s * 0.53, 0.42, z), v(s * 0.535, 0.39, z + 0.008), v(s * 0.53, 0.37, z + 0.012)), radii(0.015, 0.014, 0.013), 7, false, GLOVE, GLOVE);
				}
				addTube(arm, path(v(s * 0.49, 0.45, 0.115), v(s * 0.475, 0.43, 0.13), v(s * 0.465, 0.415, 0.135)), radii(0.014, 0.013, 0.012), 7, false, GLOVE, GLOVE);
				parts.put(side < 0 ? "right_arm" : "left_arm", finish(arm, this::paint));

				Mesh leg = new Mesh();
				addTube(leg, path(v(s * 0.12, 0.62, 0), v(s * 0.13, 0.36, 0.02), v(s * 0.125, 0.11, 0.0)), radii(0.028, 0.026, 0.024), 8, false, LIMB, LIMB);
				leg.append(surfaceNets(p -> shoe(p, s), v(s * 0.125 - 0.10, -0.01, -0.12), v(s * 0.125 + 0.10, 0.17, 0.22), 0.019), SHOE);
				parts.put(side < 0 ? "right_leg" : "left_leg", finish(leg, this::paint));
			}

			double s = -1;
			Mesh finger = new Mesh();
			addTube(finger, path(v(s * 0.40, 0.785, 0), v(s * 0.49, 0.66, 0.03), v(s * 0.525, 0.50, 0.075)), radii(0.026, 0.024, 0.022), 8, false, LIMB, LIMB);
			addTorusY(finger, v(s * 0.527, 0.50, 0.078), 0.034, 0.012, GLOVE);
			addFist(finger, v(s * 0.53, 0.45, 0.095), v(0.044, 0.048, 0.042), 0.0155, 0.085, GLOVE, GLOVE);
			parts.put("middle_finger", finish(finger, this::paint));
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
			while (placed < 34) {
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
					return rgb(0.86 - toast + grain, 0.52 - toast * 1.2 + grain, 0.18 - toast * 0.6 + grain * 0.5);
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
				case TOMATO:
					return rgb(0.88, 0.16 + grain, 0.12);
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
			FILTER = 11, PAPER = 12, EMBER = 13, ASH = 14;

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

		static double flipper(V p, double s, boolean finger) {
			V root = v(s * 0.232, 1.035, -0.012);
			V mid = v(s * 0.29, 0.78, 0.02);
			V tip = finger ? v(s * 0.31, 0.6, 0.04) : v(s * 0.305, 0.5, 0.042);
			V thin = tip.sub(root).cross(v(0, 0, 1)).norm();
			double d = squashed(q -> roundCone(q, root, mid, 0.07, 0.054), p, root, thin, 3.4);
			return smin(d, squashed(q -> roundCone(q, mid, tip, 0.054, 0.026), p, mid, thin, 3.4), 0.03);
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
			addCigarette(headMesh);
			parts.put("head", finish(headMesh, this::paint));

			Mesh jaw = surfaceNets(Penguin::lowerBeak, v(-0.06, 1.2, 0.13), v(0.06, 1.27, 0.34), 0.005);
			addEllipsoid(jaw, v(0, mouthLine(0.23) - 0.002, 0.225), v(0.017, 0.004, 0.035), TONGUE);
			parts.put("jaw", finish(jaw, (p, n, m, e) -> paint(p, n, m == TONGUE ? TONGUE : BEAK_LOW, e)));

			parts.put("body", finish(surfaceNormals(surfaceNets(Penguin::body, v(-0.33, 0.06, -0.38), v(0.33, 1.19, 0.31), 0.019), Penguin::full, 0.019),
				this::paint));

			for (int side = -1; side <= 1; side += 2) {
				double s = side;
				Mesh arm = surfaceNets(p -> flipper(p, s, false), v(s * 0.27 - 0.12, 0.43, -0.12), v(s * 0.27 + 0.12, 1.11, 0.13), 0.0085);
				parts.put(side < 0 ? "right_arm" : "left_arm", finish(arm, (p, n, m, e) -> paintFlipper(p, n, s)));

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

			double s = -1;
			Mesh finger = surfaceNets(p -> flipper(p, s, true), v(s * 0.27 - 0.12, 0.43, -0.12), v(s * 0.27 + 0.12, 1.11, 0.13), 0.0085);
			// White like the flipper's underside, so it shows against the black head.
			V tip = v(s * 0.31, 0.6, 0.04);
			addTube(finger, path(tip.add(v(0, -0.01, 0.0)), tip.add(v(0, -0.004, 0.07)), tip.add(v(0, 0.0, 0.135))), radii(0.021, 0.02, 0.018), 9, false, WHITE,
				WHITE);
			parts.put("middle_finger", finish(finger, (p, n, m, e) -> m == WHITE ? rgb(0.94, 0.94, 0.92) : paintFlipper(p, n, s)));
			return parts;
		}

		/** A cigarette in the corner of the beak: orange filter, white paper and a glowing tip with a little ash. */
		static void addCigarette(Mesh m) {
			double r = 0.0105;
			V filterEnd = CIGARETTE.add(CIGARETTE_DIR.mul(0.052));
			V paperEnd = CIGARETTE.add(CIGARETTE_DIR.mul(CIGARETTE_LENGTH - 0.01));
			V end = CIGARETTE.add(CIGARETTE_DIR.mul(CIGARETTE_LENGTH));
			addTube(m, path(CIGARETTE, CIGARETTE.add(CIGARETTE_DIR.mul(0.026)), filterEnd), radii(r, r, r), 10, false, FILTER, FILTER);
			addTube(m, path(filterEnd, filterEnd.add(paperEnd).mul(0.5), paperEnd), radii(r * 0.99, r * 0.99, r * 0.99), 10, false, PAPER, PAPER);
			addTube(m, path(paperEnd, paperEnd.add(CIGARETTE_DIR.mul(0.006)), end), radii(r * 0.97, r * 0.95, r * 0.8), 10, false, EMBER, ASH);
			addEllipsoid(m, end, v(r * 0.75, r * 0.75, r * 0.75), ASH);
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
				case EMBER:
					return rgb(1.0, 0.46, 0.1);
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
				parts.put(side < 0 ? "right_arm" : "left_arm", finish(arm(s, false), this::paintBody));
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
			parts.put("middle_finger", finish(arm(-1, true), this::paintBody));
			return parts;
		}

		static Mesh arm(double s, boolean middleFinger) {
			Mesh m = new Mesh();
			addTube(m, path(v(s * 0.29, 0.53, 0.0), v(s * 0.33, 0.528, 0.0), v(s * 0.372, 0.52, 0.0)), radii(0.05, 0.053, 0.056), 14, false, SHIRT, SHIRT);
			addTorus(m, v(s * 0.372, 0.52, 0.0), v(1, -0.15 * s, 0).mul(s), 0.054, 0.006, 16, 6, SHIRT); // sleeve hem
			addTube(m, path(v(s * 0.37, 0.52, 0.0), v(s * 0.41, 0.42, 0.02), v(s * 0.425, 0.31, 0.05)), radii(0.022, 0.021, 0.02), 9, false, SKIN, SKIN);
			V palm = v(s * 0.428, 0.275, 0.062);
			if (middleFinger) {
				addFist(m, palm, v(0.032, 0.038, 0.034), 0.016, 0.1, SKIN, SKIN);
				return m;
			}
			addEllipsoid(m, palm, v(0.03, 0.038, 0.026), SKIN);
			for (int f = 0; f < 3; f++) {
				double z = palm.z + (f - 1) * 0.019;
				addTube(m, path(v(palm.x, palm.y - 0.025, z), v(palm.x + s * 0.004, palm.y - 0.05, z + 0.004), v(palm.x, palm.y - 0.072, z + 0.008)),
					radii(0.0108, 0.0102, 0.0094), 8, false, SKIN, SKIN);
			}
			addTube(m, path(v(palm.x - s * 0.02, palm.y - 0.005, palm.z + 0.02), v(palm.x - s * 0.03, palm.y - 0.025, palm.z + 0.03),
				v(palm.x - s * 0.034, palm.y - 0.04, palm.z + 0.032)), radii(0.0102, 0.0097, 0.009), 8, false, SKIN, SKIN);
			return m;
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

		/** The hand: a narrow palm, four very long fingers and a thumb. */
		static double hand(V p, double s, List<V[]> tips) {
			V palm = palm(s);
			double d = ellipsoid(p, palm, v(0.0145, 0.042, 0.029));
			d = smin(d, sphere(p, wrist(s).add(v(0, -0.012, 0.004)), 0.016), 0.02);
			for (int f = 0; f < 4; f++) {
				double z = palm.z - 0.022 + f * 0.0145;
				V base = v(palm.x, palm.y - 0.035, z);
				d = smin(d, sphere(p, base, 0.0088), 0.006); // knuckle
				V[] tip = new V[2];
				d = smin(d, finger(p, base, v(s * 0.02, -1, 0.07), v(0, 0, 0.16), FINGER_LENGTH[f], 0.0074, tip), 0.006);
				d = smin(d, roundCone(p, palm.add(v(s * -0.008, 0.02, (f - 1.5) * 0.009)), base.add(v(s * -0.006, 0.004, 0)), 0.0028, 0.0035), 0.005); // tendons
				if (tips != null) {
					tips.add(tip);
				}
			}
			V thumbBase = v(palm.x - s * 0.006, palm.y - 0.008, palm.z + 0.026);
			V[] tip = new V[2];
			d = smin(d, finger(p, thumbBase, v(-s * 0.08, -0.85, 0.42), v(0, 0.02, 0.06), 0.085, 0.0078, tip), 0.008);
			if (tips != null) {
				tips.add(tip);
			}
			return d;
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
				parts.put(side < 0 ? "right_arm" : "left_arm", finish(armMesh(s, false), (p, n, m, e) -> paint(p, n, m, e, "arm")));
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
			parts.put("middle_finger", finish(armMesh(-1, true), (p, n, m, e) -> paint(p, n, m, e, "arm")));
			return parts;
		}

		static Mesh armMesh(double s, boolean middleFinger) {
			V sh = shoulder(s);
			Mesh m = surfaceNets(p -> arm(p, s), v(sh.x - 0.07, 0.76, -0.09), v(sh.x + 0.09, 1.61, 0.06), 0.009);
			List<V[]> tips = new ArrayList<>();
			V palm = palm(s);
			Sdf shape = middleFinger ? p -> fist(p, s, null) : p -> hand(p, s, null);
			Mesh hand = middleFinger
				? surfaceNets(shape, v(palm.x - 0.035, 0.68, palm.z - 0.05), v(palm.x + 0.035, 0.81, palm.z + 0.2), 0.0041)
				: surfaceNets(shape, v(palm.x - 0.05, 0.55, palm.z - 0.05), v(palm.x + 0.05, 0.81, palm.z + 0.11), 0.0041);
			m.append(hand, SKIN);
			if (middleFinger) {
				fist(v(0, 0, 0), s, tips);
			} else {
				hand(v(0, 0, 0), s, tips);
			}
			for (V[] tip : tips) {
				addCone(m, tip[0].sub(tip[1].mul(0.004)), tip[0].add(tip[1].mul(0.017)), 0.0052, 6, NAIL);
			}
			return m;
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
				parts.put(side < 0 ? "right_arm" : "left_arm", finish(arm(s, false), this::paint));
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
			parts.put("middle_finger", finish(arm(-1, true), this::paint));
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

		/**
		 * A blocky arm hanging from the side of its digit: dark blue upper arm, blue forearm, a light cuff and a white glove.
		 * The middle finger version makes a fist with the middle finger pointing forward (up, once the arm is raised).
		 */
		static Mesh arm(double s, boolean middleFinger) {
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
			V palm = v(ax + s * 0.012, shoulder.y - 0.45, 0.014);
			if (middleFinger) {
				addBevelBox(m, palm, x, y, z, v(0.04, 0.042, 0.044), 0.009, GLOVE, GLOVE);
				for (int f = 0; f < 4; f++) { // curled fingers along the front, the middle one sticking out
					double fx = palm.x + s * (f - 1.5) * 0.019;
					if (f == 2 - (s < 0 ? 1 : 0)) {
						addBevelBox(m, v(fx, palm.y - 0.018, palm.z + 0.044 + 0.045), x, y, z, v(0.0092, 0.0105, 0.05), 0.004, GLOVE, GLOVE);
					} else {
						addBevelBox(m, v(fx, palm.y - 0.022, palm.z + 0.047), x, y, z, v(0.0088, 0.016, 0.012), 0.004, GLOVE, GLOVE);
					}
				}
				addBevelBox(m, v(palm.x - s * 0.026, palm.y + 0.004, palm.z + 0.03), x, y, z, v(0.012, 0.024, 0.014), 0.004, GLOVE, GLOVE); // thumb
				return m;
			}
			addBevelBox(m, palm, x, y, z, v(0.034, 0.045, 0.046), 0.009, GLOVE, GLOVE);
			for (int f = 0; f < 4; f++) {
				double fz = palm.z + (f - 1.5) * 0.023;
				addBevelBox(m, v(palm.x + s * 0.004, palm.y - 0.068, fz), x, y, z, v(0.026, 0.026, 0.0098), 0.004, GLOVE, GLOVE);
			}
			addBevelBox(m, v(palm.x - s * 0.03, palm.y - 0.006, palm.z + 0.034), x, y, z, v(0.012, 0.03, 0.012), 0.004, GLOVE, GLOVE); // thumb
			return m;
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
		int seg = Math.min(12, Math.max(8, (int) Math.round(Math.max(r.x, r.z) * 400)));
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
		int seg = 14;
		int sides = 6;
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
			int steps = Math.max(2, (int) Math.ceil(length / 0.03));
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
					"right_arm": { "pivot": %s, "hand": %s },
					"left_arm": { "pivot": %s, "hand": %s },
					"right_leg": { "pivot": %s },
					"left_leg": { "pivot": %s }
				}
			}
			""".formatted(design.displayName(), fmt(r.jawAngle()), arr(r.butt()), extra, arr(r.head()), arr(r.jaw()), arr(r.body()),
			arr(r.rightArm()), arr(r.rightHand()), arr(r.leftArm()), arr(r.leftHand()), arr(r.rightLeg()), arr(r.leftLeg()));
		Files.writeString(file, json.replace("\t", "  "), StandardCharsets.UTF_8);
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
	}

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
			if (emote ? name.equals("right_arm") || isCalmPart(name) : isEmotePart(name)) {
				continue;
			}
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
					raster(img, depth, w, h, sp[t[0]], sp[t[1]], sp[t[2]], m, wn, t, light1, light2);
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
				double light = Math.min(1.0, 0.42 + 0.45 * Math.max(0, n.dot(l1)) + 0.45 * Math.max(0, n.dot(l2)));
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
