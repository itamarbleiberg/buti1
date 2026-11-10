import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the "Alien" example character for the Player Characters mod: a 3D model with a separate jaw,
 * mouth cavity and teeth, written as model.obj (with vertex colors), character.json and icon.png.
 * <p>
 * The head and torso are sculpted as signed distance fields and turned into quads with surface nets.
 * Limbs, fingers, eyes and teeth are built directly as tubes and ellipsoids, which needs far fewer
 * polygons for thin shapes.
 * <p>
 * Coordinates: 1 unit = 1 block, Y up, feet at y = 0, facing +Z, the character's right side at -X.
 * <p>
 * Run: {@code java AlienGenerator.java <outputFolder> [previewFolder]}
 */
public class AlienGenerator {
	// ------------------------------------------------------------------ landmarks

	static final double MOUTH_Y = 1.666;      // mouth line at the front of the face
	static final double MOUTH_FRONT_Z = 0.20;
	static final double CUT_SLOPE = 0.24;      // the cut between head and jaw rises toward the back
	static final double JAW_BACK_Z = -0.03;   // the jaw ends here; behind it the skull goes on
	static final V JAW_PIVOT = v(0, 1.705, 0.0);
	static final double JAW_OPEN_DEGREES = 32;

	static final V CAVITY_C = v(0, 1.675, 0.092);
	static final V CAVITY_R = v(0.082, 0.039, 0.088);

	/** Hunch: the upper body leans forward around the waist, up to this angle at the neck and head. */
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

	// ------------------------------------------------------------------ materials

	static final int SKIN = 0, EYE = 1, TEETH = 2, MOUTH = 3, CLAW = 4, LIP = 5;

	public static void main(String[] args) throws IOException {
		Path out = Path.of(args[0]);
		Files.createDirectories(out);
		Path previews = args.length > 1 ? Path.of(args[1]) : null;

		Map<String, Mesh> parts = new LinkedHashMap<>();
		parts.put("head", buildHead());
		parts.put("jaw", buildJaw());
		parts.put("body", buildBody());
		parts.put("right_arm", buildArm(-1));
		parts.put("left_arm", buildArm(1));
		parts.put("right_leg", buildLeg(-1));
		parts.put("left_leg", buildLeg(1));

		int quads = 0;
		int vertices = 0;
		for (Map.Entry<String, Mesh> part : parts.entrySet()) {
			part.getValue().orientQuads();
			colorize(part.getKey(), part.getValue());
			hunch(part.getKey(), part.getValue());
			quads += part.getValue().quads.size();
			vertices += part.getValue().pos.size();
			System.out.printf(Locale.ROOT, "%-10s %6d quads %6d vertices%n", part.getKey(), part.getValue().quads.size(), part.getValue().pos.size());
		}
		System.out.printf(Locale.ROOT, "total      %6d quads %6d vertices%n", quads, vertices);

		writeObj(out.resolve("model.obj"), parts);
		writeJson(out.resolve("character.json"));
		ImageIO.write(renderIcon(parts), "png", out.resolve("icon.png").toFile());

		if (previews != null) {
			Files.createDirectories(previews);
			writePreviews(parts, previews);
		}
	}

	// ------------------------------------------------------------------ head and jaw

	/** Whole skull with eye sockets, before it is split into head and jaw. */
	static double skull(V p) {
		double d = ellipsoid(p, v(0, 1.835, -0.035), v(0.205, 0.165, 0.235));      // cranium
		d = smin(d, ellipsoid(p, v(0, 1.735, 0.055), v(0.146, 0.150, 0.148)), 0.08); // face
		d = smin(d, ellipsoid(p, v(0, 1.622, 0.118), v(0.068, 0.062, 0.074)), 0.07); // chin
		for (int s = -1; s <= 1; s += 2) {
			d = smin(d, ellipsoid(p, v(s * 0.112, 1.742, 0.088), v(0.05, 0.032, 0.05)), 0.035); // cheekbones
			d = smax(d, -eyeSdf(p.withX(p.x * s), 1.10, 0.012), 0.035);                         // eye sockets
		}
		return d;
	}

	/** Above the cut is positive (head side), below is negative (jaw side). */
	static double cutPlane(V p) {
		double cutY = MOUTH_Y + (MOUTH_FRONT_Z - p.z) * CUT_SLOPE;
		return (p.y - cutY) / Math.sqrt(1 + CUT_SLOPE * CUT_SLOPE);
	}

	/** Negative inside the region that belongs to the jaw. */
	static double jawRegion(V p) {
		return Math.max(cutPlane(p), JAW_BACK_Z - p.z);
	}

	static double cavity(V p) {
		return ellipsoid(p, CAVITY_C, CAVITY_R);
	}

	// Slightly rounded cuts give clean lips instead of a jagged edge.
	static double headSdf(V p) {
		return smax(smax(skull(p), -jawRegion(p), 0.012), -cavity(p), 0.012);
	}

	static double jawSdf(V p) {
		return smax(smax(skull(p), jawRegion(p), 0.012), -cavity(p), 0.012);
	}

	static Mesh buildHead() {
		Mesh m = surfaceNets(AlienGenerator::headSdf, v(-0.24, 1.55, -0.30), v(0.24, 2.03, 0.24), 0.0185);
		for (int s = -1; s <= 1; s += 2) {
			addEye(m, s);
		}
		addTeeth(m, true);
		return m;
	}

	static Mesh buildJaw() {
		Mesh m = surfaceNets(AlienGenerator::jawSdf, v(-0.20, 1.53, -0.06), v(0.20, 1.76, 0.23), 0.0145);
		addTeeth(m, false);
		return m;
	}

	/** Signed distance to an eye (s = +1 left eye at +X; mirror x for the right eye). */
	static double eyeSdf(V p, double grow, double pushBack) {
		V local = eyeLocal(p, pushBack);
		return ellipsoid(local, v(0, 0, 0), EYE_R.mul(grow));
	}

	static V eyeLocal(V p, double pushBack) {
		V c = EYE_C.add(v(0, 0, -pushBack));
		V q = p.sub(c);
		q = rotY(q, -EYE_YAW);
		return rotZ(q, -EYE_ROLL);
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
		for (int r = 0; r < rings; r++) {
			for (int s = 0; s < seg; s++) {
				int a = base + r * (seg + 1) + s;
				int b = a + seg + 1;
				m.quad(a, a + 1, b + 1, b);
			}
		}
	}

	/** Sharp teeth along the front of the mouth: upper ones hang from the head, lower ones stand on the jaw. */
	static void addTeeth(Mesh m, boolean upper) {
		int count = upper ? 10 : 9;
		for (int i = 0; i < count; i++) {
			double t = -0.86 + 1.72 * i / (count - 1);
			double x = t * CAVITY_R.x * 0.92;
			double z = CAVITY_C.z + CAVITY_R.z * Math.sqrt(Math.max(0, 1 - t * t)) * 0.86 - (upper ? 0.0 : 0.010);
			double wall = cavityWallY(x, z, upper);
			double length = (upper ? 0.044 : 0.032) * (1.0 - 0.38 * t * t);
			double radius = 0.0082 * (1.0 - 0.25 * t * t);
			V root = v(x, wall + (upper ? 0.006 : -0.006), z);
			V tip = v(x * 0.97, wall + (upper ? -length : length), z - 0.004);
			addCone(m, root, tip, radius, 5, TEETH);
		}
	}

	static double cavityWallY(double x, double z, boolean upper) {
		double k = 1 - sq(x / CAVITY_R.x) - sq((z - CAVITY_C.z) / CAVITY_R.z);
		double h = CAVITY_R.y * Math.sqrt(Math.max(0, k));
		return CAVITY_C.y + (upper ? h : -h);
	}

	// ------------------------------------------------------------------ body

	static double bodySdf(V p) {
		double d = ellipsoid(p, v(0, 1.275, 0.004), v(0.134, 0.165, 0.094));                // ribcage
		d = smin(d, roundCone(p, v(0, 1.16, 0.004), v(0, 0.995, -0.002), 0.094, 0.066), 0.05); // belly to waist
		d = smin(d, ellipsoid(p, v(0, 0.948, -0.006), v(0.108, 0.075, 0.077)), 0.05);        // pelvis
		d = smin(d, roundCone(p, v(0, 1.43, -0.016), v(0, 1.585, -0.01), 0.046, 0.038), 0.03); // neck (stays below the mouth)
		d = smin(d, roundCone(p, v(0, 1.30, -0.075), v(0, 0.99, -0.06), 0.032, 0.03), 0.04);  // spine
		for (int s = -1; s <= 1; s += 2) {
			d = smin(d, roundCone(p, v(0, 1.432, -0.012), v(s * 0.172, 1.414, -0.012), 0.05, 0.038), 0.04); // shoulders
		}
		return d;
	}

	static Mesh buildBody() {
		return surfaceNets(AlienGenerator::bodySdf, v(-0.24, 0.84, -0.14), v(0.24, 1.70, 0.14), 0.0215);
	}

	// ------------------------------------------------------------------ limbs

	static Mesh buildArm(int side) {
		Mesh m = new Mesh();
		double s = side;
		addSphere(m, v(s * 0.185, 1.415, -0.010), 0.042, SKIN);
		addTube(m, path(v(s * 0.19, 1.40, -0.01), v(s * 0.205, 1.24, -0.006), v(s * 0.215, 1.085, 0.0)), radii(0.035, 0.031, 0.025), 8, false);
		addSphere(m, v(s * 0.215, 1.085, 0.0), 0.029, SKIN);
		addTube(m, path(v(s * 0.215, 1.085, 0.0), v(s * 0.222, 0.95, 0.018), v(s * 0.226, 0.785, 0.045)), radii(0.027, 0.024, 0.017), 8, false);
		addSphere(m, v(s * 0.226, 0.785, 0.045), 0.019, SKIN);
		addEllipsoid(m, v(s * 0.228, 0.745, 0.056), v(0.016, 0.042, 0.029), SKIN);
		for (int f = -1; f <= 1; f++) {
			double z = 0.056 + f * 0.017;
			double reach = f == 0 ? 0.0 : 0.012;
			addFinger(m, path(v(s * 0.228, 0.715, z), v(s * 0.234, 0.64 + reach, z + 0.010), v(s * 0.229, 0.552 + reach * 1.5, z + 0.024)), 0.0074);
		}
		addFinger(m, path(v(s * 0.220, 0.742, 0.078), v(s * 0.213, 0.690, 0.103), v(s * 0.210, 0.652, 0.112)), 0.0068); // thumb
		return m;
	}

	static Mesh buildLeg(int side) {
		Mesh m = new Mesh();
		double s = side;
		addSphere(m, v(s * 0.072, 0.93, 0.0), 0.056, SKIN);
		addTube(m, path(v(s * 0.073, 0.93, 0.0), v(s * 0.078, 0.73, 0.012), v(s * 0.083, 0.525, 0.028)), radii(0.054, 0.046, 0.035), 9, false);
		addSphere(m, v(s * 0.083, 0.525, 0.028), 0.038, SKIN);
		addTube(m, path(v(s * 0.083, 0.525, 0.024), v(s * 0.085, 0.36, 0.0), v(s * 0.086, 0.09, -0.008)), radii(0.034, 0.033, 0.021), 8, false);
		addSphere(m, v(s * 0.086, 0.09, -0.008), 0.024, SKIN);
		addEllipsoid(m, v(s * 0.086, 0.042, -0.010), v(0.027, 0.040, 0.033), SKIN);
		for (int t = -1; t <= 1; t++) {
			double dx = t * 0.014;
			addFinger(m, path(v(s * (0.086 + dx), 0.036, 0.012), v(s * (0.086 + dx * 1.5), 0.020, 0.072), v(s * (0.086 + dx * 2.0), 0.009, 0.128 - Math.abs(t) * 0.012)), 0.012);
		}
		return m;
	}

	static void addFinger(Mesh m, List<V> points, double baseRadius) {
		addTube(m, points, radii(baseRadius, baseRadius * 0.8, baseRadius * 0.35), 5, true);
	}

	// ------------------------------------------------------------------ posture

	static double hunchAngle(double y) {
		return HUNCH * smoothstep(HUNCH_CENTER.y, 1.52, y);
	}

	/** Where a point of the upright model ends up in the hunched pose. */
	static V hunched(V p) {
		return rotX(p.sub(HUNCH_CENTER), hunchAngle(p.y)).add(HUNCH_CENTER);
	}

	/**
	 * Leans the upper body forward like the reference picture. The head and jaw turn as one piece, the body
	 * bends gradually above the waist, and the arms move with their shoulders but keep hanging straight down.
	 * Done after coloring, so colors are computed on the upright shapes.
	 */
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

	/** The head rides forward with the top of the neck but only tips down a little, so it still looks ahead. */
	static V headPose(V p) {
		V shift = hunched(HEAD_PIVOT).sub(HEAD_PIVOT);
		return rotX(p.sub(HEAD_PIVOT), HEAD_TILT).add(HEAD_PIVOT).add(shift);
	}

	// ------------------------------------------------------------------ colors

	static void colorize(String part, Mesh m) {
		for (int i = 0; i < m.pos.size(); i++) {
			V p = m.pos.get(i);
			V n = m.nrm.get(i);
			int material = m.material.get(i);
			// Surfaces inside the original skull are cut faces: the inside of the mouth and the throat.
			boolean onCut = Math.abs(jawRegion(p)) < 0.009 && skull(p) < -0.002;
			if ((part.equals("head") || part.equals("jaw")) && material == SKIN && (skull(p) < -0.006 || cavity(p) < 0.008 || onCut)) {
				material = MOUTH;
				if (skull(p) > -0.016 && cavity(p) > 0.004) {
					material = LIP; // the edge of the mouth: a dark line when closed
				}
			}
			m.color.add(shade(material, part, p, n, m.extra.get(i)));
		}
	}

	static double[] shade(int material, String part, V p, V n, double extra) {
		switch (material) {
			case EYE: {
				double gloss = Math.max(0, n.dot(v(0.35, 0.75, 0.56).norm()));
				double hi = Math.pow(gloss, 14) * 0.55 + Math.pow(Math.max(0, n.dot(v(-0.5, 0.3, 0.8).norm())), 30) * 0.2;
				return rgb(0.025 + hi, 0.027 + hi, 0.034 + hi * 1.1);
			}
			case TEETH: {
				double root = clamp01(extra);
				return rgb(0.93 - root * 0.12, 0.91 - root * 0.16, 0.82 - root * 0.22);
			}
			case LIP:
				return rgb(0.25, 0.13, 0.12);
			case MOUTH: {
				// Bright red near the lips, darker toward the throat.
				double back = clamp01((0.17 - p.z) / 0.20);
				double gum = clamp01(1 - Math.abs(cutPlane(p)) / 0.03);
				return rgb(0.62 - 0.40 * back + 0.08 * gum, 0.07 - 0.04 * back + 0.05 * gum, 0.08 - 0.04 * back + 0.05 * gum);
			}
			default: {
				double ao = occlusion(p, n);
				double mottle = noise(p.mul(9)) * 0.07 + noise(p.mul(37)) * 0.035;
				double r = 0.60 + mottle;
				double g = 0.585 + mottle;
				double b = 0.50 + mottle * 0.8;
				boolean face = part.equals("head") || part.equals("jaw");
				double shadeK = face ? 0.62 + 0.38 * ao : 0.42 + 0.58 * ao;
				if (face && Math.abs(cutPlane(p)) < 0.014 && p.z > 0.02) {
					// Thin dark lips where the head and jaw meet.
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
					for (int s = -1; s <= 1; s += 2) { // nostrils
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

	/** Ambient occlusion from the whole body in its rest pose. */
	static double occlusion(V p, V n) {
		double occ = 0;
		double weight = 1;
		for (double d : new double[] {0.012, 0.03, 0.06}) {
			occ += weight * (d - wholeBody(p.add(n.mul(d)))) / d;
			weight *= 0.5;
		}
		return clamp01(1 - occ * 0.55);
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

	// ------------------------------------------------------------------ surface nets

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
		java.util.Arrays.fill(cellVertex, -1);
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
					cellVertex[i + cx * (j + cy * k)] = m.add(p, gradient(f, p, h * 0.3).norm(), SKIN, 0);
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

	// ------------------------------------------------------------------ parametric shapes

	static void addSphere(Mesh m, V c, double r, int material) {
		addEllipsoid(m, c, v(r, r, r), material);
	}

	static void addEllipsoid(Mesh m, V c, V r, int material) {
		int seg = Math.max(8, (int) Math.round(Math.max(r.x, r.z) * 400));
		seg = Math.min(seg, 12);
		int rings = Math.max(5, seg / 2);
		int base = m.pos.size();
		for (int i = 0; i <= rings; i++) {
			double theta = Math.PI * i / rings;
			for (int s = 0; s <= seg; s++) {
				double phi = 2 * Math.PI * s / seg;
				V unit = v(Math.sin(theta) * Math.cos(phi), Math.cos(theta), Math.sin(theta) * Math.sin(phi));
				m.add(c.add(v(unit.x * r.x, unit.y * r.y, unit.z * r.z)), v(unit.x / r.x, unit.y / r.y, unit.z / r.z).norm(), material, 0);
			}
		}
		for (int i = 0; i < rings; i++) {
			for (int s = 0; s < seg; s++) {
				int a = base + i * (seg + 1) + s;
				int b = a + seg + 1;
				m.quad(a, a + 1, b + 1, b);
			}
		}
	}

	/** A smooth tube along a Catmull-Rom curve through the points. With {@code pointed} it ends in a claw tip. */
	static void addTube(Mesh m, List<V> points, double[] radius, int seg, boolean pointed) {
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

		int material = pointed ? CLAW : SKIN;
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
			double clawAmount = pointed ? smoothstep(0.62, 0.9, t) : 0;
			for (int s = 0; s <= seg; s++) {
				double a = 2 * Math.PI * s / seg;
				V dir = normal.mul(Math.cos(a)).add(binormal.mul(Math.sin(a)));
				m.add(samples.get(i).add(dir.mul(sampleRadius.get(i))), dir, material, clawAmount);
			}
		}
		for (int i = 0; i < samples.size() - 1; i++) {
			for (int s = 0; s < seg; s++) {
				int a = base + i * (seg + 1) + s;
				int b = a + seg + 1;
				m.quad(a, a + 1, b + 1, b);
			}
		}
		// Close the end with a point (claw) or a flat-ish cap.
		int last = samples.size() - 1;
		V end = samples.get(last);
		V dir = end.sub(samples.get(last - 1)).norm();
		int apex = m.add(end.add(dir.mul(pointed ? radius[radius.length - 1] * 3.0 : radius[radius.length - 1] * 0.6)), dir, material, pointed ? 1.0 : 0);
		for (int s = 0; s < seg; s++) {
			int a = base + last * (seg + 1) + s;
			m.quad(a, a + 1, apex, apex);
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
		for (int r = 0; r < ts.length - 1; r++) {
			for (int s = 0; s < seg; s++) {
				int a = base + r * (seg + 1) + s;
				int b = a + seg + 1;
				m.quad(a, a + 1, b + 1, b);
			}
		}
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

	// ------------------------------------------------------------------ output

	static void writeObj(Path file, Map<String, Mesh> parts) throws IOException {
		try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
			out.println("# Alien character for the Player Characters mod, made by tools/AlienGenerator.java");
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

	/** Joints in the final (hunched) pose. */
	static void writeJson(Path file) throws IOException {
		V rightShift = hunched(RIGHT_SHOULDER).sub(RIGHT_SHOULDER);
		V leftShift = hunched(LEFT_SHOULDER).sub(LEFT_SHOULDER);
		String json = """
			{
				"name": "Alien",
				"size": 1.0,
				"jaw_angle": %s,
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
			""".formatted(fmt(JAW_OPEN_DEGREES), arr(headPose(HEAD_PIVOT)), arr(headPose(JAW_PIVOT)), arr(hunched(BODY_PIVOT)),
			arr(RIGHT_SHOULDER.add(rightShift)), arr(RIGHT_HAND.add(rightShift)), arr(LEFT_SHOULDER.add(leftShift)), arr(LEFT_HAND.add(leftShift)),
			arr(RIGHT_HIP), arr(LEFT_HIP));
		Files.writeString(file, json.replace("\t", "  "), StandardCharsets.UTF_8);
	}

	static String arr(V p) {
		return "[" + fmt(p.x) + ", " + fmt(p.y) + ", " + fmt(p.z) + "]";
	}

	static String fmt(double d) {
		String s = String.format(Locale.ROOT, "%.3f", d).replaceAll("0+$", "").replaceAll("\\.$", "");
		return s.equals("-0") ? "0" : s;
	}

	// ------------------------------------------------------------------ preview renderer

	static BufferedImage renderIcon(Map<String, Mesh> parts) {
		BufferedImage big = render(parts, 0.55, headPose(v(0.0, 1.76, 1.05)), headPose(v(0, 1.76, 0)), 256, 256, false, 26);
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

	static void writePreviews(Map<String, Mesh> parts, Path dir) throws IOException {
		ImageIO.write(render(parts, 1.0, v(0, 1.15, 3.4), v(0, 1.0, 0), 400, 560, true, 0), "png", dir.resolve("front.png").toFile());
		ImageIO.write(render(parts, 1.0, v(2.4, 1.5, 2.4), v(0, 1.0, 0), 400, 560, true, 0), "png", dir.resolve("three-quarter.png").toFile());
		ImageIO.write(render(parts, 1.0, v(3.4, 1.2, 0), v(0, 1.0, 0), 400, 560, true, 0), "png", dir.resolve("side.png").toFile());
		ImageIO.write(render(parts, 0.5, headPose(v(0.35, 1.85, 1.1)), headPose(v(0, 1.74, 0)), 500, 500, true, 0), "png", dir.resolve("face-closed.png").toFile());
		ImageIO.write(render(parts, 0.5, headPose(v(0.35, 1.85, 1.1)), headPose(v(0, 1.74, 0)), 500, 500, true, JAW_OPEN_DEGREES), "png", dir.resolve("face-open.png").toFile());
		ImageIO.write(render(parts, 0.5, headPose(v(1.1, 1.75, 0.2)), headPose(v(0, 1.72, 0)), 500, 500, true, JAW_OPEN_DEGREES), "png", dir.resolve("face-open-side.png").toFile());
	}

	/** Small software rasterizer with two directional lights, roughly like Minecraft's entity lighting. */
	static BufferedImage render(Map<String, Mesh> parts, double fovScale, V eye, V target, int w, int h, boolean background, double jawDegrees) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		double[] depth = new double[w * h];
		java.util.Arrays.fill(depth, Double.MAX_VALUE);
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
		V light1 = v(0.2, 1.0, -0.7).norm().mul(-1).mul(-1);
		V light2 = v(-0.2, 1.0, 0.7).norm();
		for (Map.Entry<String, Mesh> entry : parts.entrySet()) {
			Mesh m = entry.getValue();
			boolean isJaw = entry.getKey().equals("jaw");
			V[] sp = new V[m.pos.size()];
			V[] wn = new V[m.pos.size()];
			for (int i = 0; i < m.pos.size(); i++) {
				V p = m.pos.get(i);
				V n = m.nrm.get(i);
				if (isJaw && jawDegrees != 0) {
					V hinge = headPose(JAW_PIVOT);
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
				double light = Math.min(1.0, 0.42 + 0.6 * Math.max(0, n.dot(l1)) * 0.75 + 0.6 * Math.max(0, n.dot(l2)) * 0.75);
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

	// ------------------------------------------------------------------ mesh

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

	// ------------------------------------------------------------------ math

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
