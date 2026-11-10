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
		for (Design design : List.of(new Alien(), new Robot(), new Frog(), new Burger())) {
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
		abstract String name();

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
	}

	interface Painter {
		double[] paint(V p, V n, int material, double extra);
	}

	static void paint(Mesh m, Painter painter) {
		m.color.clear();
		for (int i = 0; i < m.pos.size(); i++) {
			m.color.add(painter.paint(m.pos.get(i), m.nrm.get(i), m.material.get(i), m.extra.get(i)));
		}
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
			parts.put("right_arm", buildArm(-1));
			parts.put("left_arm", buildArm(1));
			parts.put("right_leg", buildLeg(-1));
			parts.put("left_leg", buildLeg(1));
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

		static Mesh buildArm(int side) {
			Mesh m = new Mesh();
			double s = side;
			addSphere(m, v(s * 0.185, 1.415, -0.010), 0.042, SKIN);
			addTube(m, path(v(s * 0.19, 1.40, -0.01), v(s * 0.205, 1.24, -0.006), v(s * 0.215, 1.085, 0.0)), radii(0.035, 0.031, 0.025), 8, false, SKIN, CLAW);
			addSphere(m, v(s * 0.215, 1.085, 0.0), 0.029, SKIN);
			addTube(m, path(v(s * 0.215, 1.085, 0.0), v(s * 0.222, 0.95, 0.018), v(s * 0.226, 0.785, 0.045)), radii(0.027, 0.024, 0.017), 8, false, SKIN, CLAW);
			addSphere(m, v(s * 0.226, 0.785, 0.045), 0.019, SKIN);
			addEllipsoid(m, v(s * 0.228, 0.745, 0.056), v(0.016, 0.042, 0.029), SKIN);
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
			addEllipsoid(m, v(s * 0.086, 0.042, -0.010), v(0.027, 0.040, 0.033), SKIN);
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
					case "head", "jaw" -> {
						m.pos.set(i, headPose(p));
						m.nrm.set(i, rotX(m.nrm.get(i), HEAD_TILT));
					}
					case "right_arm" -> m.pos.set(i, p.add(hunched(RIGHT_SHOULDER).sub(RIGHT_SHOULDER)));
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
				addEllipsoid(bodyMesh, v(-0.06 + i * 0.04, 1.31, 0.118), v(0.014, 0.014, 0.008), lights[i]);
			}
			for (int i = 0; i < 3; i++) { // speaker grille slots
				addEllipsoid(bodyMesh, v(0, 1.245 - i * 0.022, 0.116), v(0.06, 0.0045, 0.006), DARK);
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
			return parts;
		}

		static void addEyes(Mesh m) {
			for (int s = -1; s <= 1; s += 2) {
				addEllipsoid(m, v(s * 0.058, 1.738, 0.138), v(0.034, 0.026, 0.012), GLOW);
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
				addEllipsoid(head, eye.add(v(s * 0.006, 0.006, 0.058)), v(0.026, 0.022, 0.012), PUPIL);
			}
			parts.put("head", finish(head, this::paintHead));

			Mesh jaw = surfaceNets(p -> smax(smax(skull(p), jawRegion(p), 0.01), -cavity(p), 0.01), v(-0.25, 1.12, -0.13), v(0.25, 1.30, 0.25), 0.016);
			addEllipsoid(jaw, v(0, 1.212, 0.07), v(0.075, 0.016, 0.085), TONGUE);
			parts.put("jaw", finish(jaw, this::paintHead));

			parts.put("body", finish(surfaceNets(Frog::body, v(-0.24, 0.56, -0.20), v(0.24, 1.25, 0.21), 0.022), this::paintBody));

			for (int side = -1; side <= 1; side += 2) {
				double s = side;
				Mesh arm = new Mesh();
				addSphere(arm, v(s * 0.17, 1.03, 0.0), 0.045, SKIN);
				addTube(arm, path(v(s * 0.17, 1.03, 0.0), v(s * 0.205, 0.93, 0.03), v(s * 0.222, 0.86, 0.05)), radii(0.038, 0.033, 0.03), 9, false, SKIN, SKIN);
				addSphere(arm, v(s * 0.222, 0.86, 0.05), 0.03, SKIN);
				addTube(arm, path(v(s * 0.222, 0.86, 0.05), v(s * 0.218, 0.78, 0.08), v(s * 0.212, 0.72, 0.105)), radii(0.03, 0.027, 0.024), 9, false, SKIN, SKIN);
				addEllipsoid(arm, v(s * 0.212, 0.705, 0.115), v(0.03, 0.03, 0.035), SKIN);
				for (int f = 0; f < 4; f++) {
					double a = Math.toRadians(-50 + f * 33);
					V tip = v(s * (0.212 + Math.sin(a) * 0.045), 0.645, 0.13 + Math.cos(a) * 0.035);
					addTube(arm, path(v(s * 0.212, 0.70, 0.12), v(s * (0.212 + Math.sin(a) * 0.028), 0.672, 0.125 + Math.cos(a) * 0.02), tip), radii(0.011, 0.009, 0.008), 6, false, SKIN, SKIN);
					addSphere(arm, tip, 0.014, PAD);
				}
				parts.put(side < 0 ? "right_arm" : "left_arm", finish(arm, this::paintLimb));

				Mesh leg = new Mesh();
				addEllipsoid(leg, v(s * 0.115, 0.62, 0.0), v(0.085, 0.09, 0.10), SKIN);
				addTube(leg, path(v(s * 0.115, 0.62, 0.02), v(s * 0.145, 0.50, 0.11), v(s * 0.165, 0.40, 0.15)), radii(0.088, 0.072, 0.058), 11, false, SKIN, SKIN);
				addSphere(leg, v(s * 0.165, 0.40, 0.15), 0.058, SKIN);
				addTube(leg, path(v(s * 0.165, 0.40, 0.15), v(s * 0.15, 0.24, 0.05), v(s * 0.13, 0.08, -0.02)), radii(0.056, 0.045, 0.034), 10, false, SKIN, SKIN);
				addSphere(leg, v(s * 0.13, 0.07, -0.02), 0.032, SKIN);
				addEllipsoid(leg, v(s * 0.135, 0.028, 0.06), v(0.07, 0.026, 0.11), SKIN);
				for (int t = 0; t < 3; t++) {
					double a = Math.toRadians(-30 + t * 30);
					V tip = v(s * (0.135 + Math.sin(a) * 0.09), 0.018, 0.08 + Math.cos(a) * 0.12);
					addTube(leg, path(v(s * 0.135, 0.03, 0.09), v(s * (0.135 + Math.sin(a) * 0.05), 0.022, 0.09 + Math.cos(a) * 0.07), tip), radii(0.016, 0.013, 0.011), 6, false, SKIN, SKIN);
					addSphere(leg, tip, 0.02, PAD);
				}
				parts.put(side < 0 ? "right_leg" : "left_leg", finish(leg, this::paintLimb));
			}
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
			addEllipsoid(body, v(0, 0.884, 0.20), v(0.09, 0.012, 0.11), TONGUE);
			parts.put("body", finish(body, (p, n, m, e) -> paint(p, n, m == TONGUE ? TONGUE : layerAt(p), e)));

			// Jaw: the top bun with eyes, eyebrows, seeds and teeth.
			Mesh top = new Mesh();
			top.append(surfaceNets(Burger::topBun, v(-0.44, TOP_BASE_Y - 0.03, -0.44), v(0.44, 1.26, 0.44), 0.029), BUN);
			for (int s = -1; s <= 1; s += 2) {
				V eye = v(s * 0.115, 1.07, 0.325);
				addEllipsoid(top, eye, v(0.075, 0.088, 0.05), WHITE);
				addEllipsoid(top, eye.add(v(s * -0.01, -0.012, 0.042)), v(0.034, 0.04, 0.015), PUPIL);
				addEllipsoid(top, eye.add(v(s * -0.018, 0.002, 0.052)), v(0.009, 0.011, 0.005), WHITE);
				addTube(top, path(eye.add(v(s * 0.065, 0.10, -0.03)), eye.add(v(s * 0.0, 0.125, 0.0)), eye.add(v(s * -0.065, 0.11, -0.02))),
					radii(0.009, 0.012, 0.009), 6, false, BROW, BROW);
			}
			addSeeds(top);
			for (int i = 0; i < 9; i++) {
				double a = Math.toRadians(-48 + i * 12);
				V root = v(Math.sin(a) * 0.36, TOP_BASE_Y + 0.004, Math.cos(a) * 0.36);
				addCone(top, root, root.add(v(0, -0.035, 0.004)), 0.022, 6, TEETH);
			}
			parts.put("jaw", finish(top, this::paint));

			for (int side = -1; side <= 1; side += 2) {
				double s = side;
				Mesh arm = new Mesh();
				addTube(arm, path(v(s * 0.40, 0.785, 0), v(s * 0.49, 0.66, 0.03), v(s * 0.525, 0.50, 0.075)), radii(0.026, 0.024, 0.022), 8, false, LIMB, LIMB);
				addTorusY(arm, v(s * 0.527, 0.50, 0.078), 0.034, 0.012, GLOVE); // cuff
				V palm = v(s * 0.53, 0.445, 0.095);
				addEllipsoid(arm, palm, v(0.045, 0.05, 0.035), GLOVE);
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
			return parts;
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
		V a = (Math.abs(u.y) < 0.9 ? v(0, 1, 0) : v(1, 0, 0)).cross(u).norm();
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
		String json = """
			{
				"name": "%s",
				"size": 1.0,
				"jaw_angle": %s,
				"butt": %s,
				"parts": {
					"head": { "pivot": %s },
					"jaw": { "pivot": %s },
					"body": { "pivot": %s },
					"right_arm": { "pivot": %s, "hand": %s },
					"left_arm": { "pivot": %s, "hand": %s },
					"right_leg": { "pivot": %s },
					"left_leg": { "pivot": %s }
				}
			}
			""".formatted(design.name(), fmt(r.jawAngle()), arr(r.butt()), arr(r.head()), arr(r.jaw()), arr(r.body()),
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
		BufferedImage big = render(design, parts, design.iconFov(), design.iconEye(), design.iconTarget(), 256, 256, false, design.rig().jawAngle() * 0.8);
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
		ImageIO.write(render(design, parts, fov, mid.add(v(0, 0.1, 3.4)), mid, 400, 560, true, 0), "png", dir.resolve(n + "-front.png").toFile());
		ImageIO.write(render(design, parts, fov, mid.add(v(2.4, 0.4, 2.4)), mid, 400, 560, true, jaw), "png", dir.resolve(n + "-three-quarter-talking.png").toFile());
		ImageIO.write(render(design, parts, fov, mid.add(v(3.4, 0.1, 0)), mid, 400, 560, true, 0), "png", dir.resolve(n + "-side.png").toFile());
		V face = design.iconTarget();
		V eye = design.iconEye();
		V away = eye.sub(face);
		V eye34 = face.add(rotY(away, Math.toRadians(25))).add(v(0, 0.1, 0));
		ImageIO.write(render(design, parts, design.iconFov(), eye34, face, 500, 500, true, 0), "png", dir.resolve(n + "-face-closed.png").toFile());
		ImageIO.write(render(design, parts, design.iconFov(), eye34, face, 500, 500, true, jaw), "png", dir.resolve(n + "-face-open.png").toFile());
	}

	/** Small software rasterizer with two directional lights, roughly like Minecraft's entity lighting. */
	static BufferedImage render(Design design, Map<String, Mesh> parts, double fovScale, V eye, V target, int w, int h, boolean background, double jawDegrees) {
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
		for (Map.Entry<String, Mesh> entry : parts.entrySet()) {
			Mesh m = entry.getValue();
			boolean isJaw = entry.getKey().equals("jaw");
			V[] sp = new V[m.pos.size()];
			V[] wn = new V[m.pos.size()];
			for (int i = 0; i < m.pos.size(); i++) {
				V p = m.pos.get(i);
				V n = m.nrm.get(i);
				if (isJaw && jawDegrees != 0) {
					p = rotX(p.sub(hinge), Math.toRadians(jawDegrees)).add(hinge);
					n = rotX(n, Math.toRadians(jawDegrees));
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
