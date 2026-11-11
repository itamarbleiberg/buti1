package com.playercharacters.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.playercharacters.character.CharacterModel.ArmRig;
import com.playercharacters.character.CharacterModel.Finger;
import com.playercharacters.character.CharacterModel.Part;
import net.minecraft.util.ARGB;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Bends a 3D character's arm for one frame: the forearm turns at the elbow (smoothly, over a short stretch of the arm,
 * so the surface doesn't tear), it twists around itself between the elbow and the wrist, and each finger curls at its
 * joints. Works on the model's corners before the arm's own turn is applied, in model space.
 */
public final class ArmBender {
	private final Vector3f elbow;
	/** The plane that splits the upper arm from the forearm: through the elbow, facing down the arm. */
	private final Vector3f split;
	private final float blend;
	private final Vector3f forearm;
	private final float forearmLength;
	private final float twist;
	private final Matrix3f bend;
	private final FingerBend[] fingers;

	private ArmBender(ArmRig rig, ArmPose pose, boolean right) {
		this.elbow = rig.elbow();
		Vector3f upper = new Vector3f(rig.elbow()).sub(rig.shoulder());
		Vector3f fore = new Vector3f(rig.wrist()).sub(rig.elbow());
		this.forearmLength = Math.max(1.0E-4F, fore.length());
		this.forearm = fore.lengthSquared() < 1.0E-10F ? new Vector3f(0.0F, 1.0F, 0.0F) : fore.normalize();
		this.split = upper.lengthSquared() < 1.0E-10F ? new Vector3f(this.forearm) : upper.normalize().add(this.forearm).normalize();
		this.blend = rig.blend();
		// A hanging hand's palm faces the body: turning around the forearm (pointing down) one way turns the right palm
		// forward, and the left one back. So the left arm turns the other way.
		this.twist = right ? pose.twist : -pose.twist;
		this.bend = new Matrix3f().rotation(pose.bend, rig.hinge());
		this.fingers = new FingerBend[rig.fingers().size()];
		for (int i = 0; i < this.fingers.length; i++) {
			Finger finger = rig.fingers().get(i);
			this.fingers[i] = new FingerBend(finger, pose.curl[finger.role().ordinal()]);
		}
	}

	/** The bending for this arm this frame, or null when it is straight and its fingers are as modeled. */
	public static @Nullable ArmBender of(@Nullable ArmRig rig, ArmPose pose, boolean right) {
		return rig == null || pose.isStraight() ? null : new ArmBender(rig, pose, right);
	}

	/** Where a point of the hand (past the wrist) ends up: through the twist and the bend. */
	public Vector3f handPoint(Vector3f point, Vector3f out) {
		out.set(point).sub(this.elbow);
		this.forearmTurn(1.0F).transform(out);
		return out.add(this.elbow);
	}

	/** How the hand is turned: the forearm's twist, then the elbow's bend. */
	public Matrix3f handTurn() {
		return this.forearmTurn(1.0F);
	}

	private Matrix3f forearmTurn(float twistAmount) {
		return new Matrix3f(this.bend).mul(new Matrix3f().rotation(this.twist * twistAmount, this.forearm));
	}

	/** Draws a part of the arm (the arm itself, or its glowing, angry or blinking bits), bent at the elbow. */
	public void emitArm(Part part, PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int tint) {
		Matrix4f matrix = pose.pose();
		float[] v = part.vertices();
		int[] colors = part.colors();
		boolean tinted = tint != -1;
		Vector3f p = new Vector3f();
		Vector3f n = new Vector3f();
		Vector3f d = new Vector3f();
		Vector3f bentP = new Vector3f();
		Vector3f bentN = new Vector3f();
		Matrix3f turn = new Matrix3f();
		Matrix3f spin = new Matrix3f();
		float blend = this.blend;
		for (int i = 0, count = colors.length; i < count; i++) {
			int o = i * 8;
			p.set(v[o], v[o + 1], v[o + 2]);
			n.set(v[o + 3], v[o + 4], v[o + 5]);
			d.set(p).sub(this.elbow);
			float s = d.dot(this.split);
			float w = s <= -blend ? 0.0F : s >= blend ? 1.0F : smooth((s + blend) / (2.0F * blend));
			if (w > 0.0F) {
				// The forearm twists more toward the wrist, like a real one, then the whole of it turns at the elbow.
				float along = Math.max(0.0F, Math.min(1.0F, d.dot(this.forearm) / this.forearmLength));
				turn.set(this.bend);
				if (this.twist != 0.0F) {
					turn.mul(spin.rotation(this.twist * along, this.forearm));
				}
				turn.transform(bentP.set(d)).add(this.elbow);
				turn.transform(bentN.set(n));
				p.lerp(bentP, w);
				n.lerp(bentN, w);
				if (w < 1.0F) {
					float length = n.length();
					if (length > 1.0E-6F) {
						n.div(length);
					}
				}
			}
			emitVertex(matrix, pose, buffer, p, n, tinted ? ARGB.multiply(colors[i], tint) : colors[i], v[o + 6], v[o + 7], overlay, light);
		}
	}

	/** Draws something held in the hand, like a rifle: it turns with the hand as a whole. */
	public void emitHeld(Part part, PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int tint) {
		Matrix4f matrix = pose.pose();
		float[] v = part.vertices();
		int[] colors = part.colors();
		boolean tinted = tint != -1;
		Matrix3f turn = this.forearmTurn(1.0F);
		Vector3f p = new Vector3f();
		Vector3f n = new Vector3f();
		for (int i = 0, count = colors.length; i < count; i++) {
			int o = i * 8;
			turn.transform(p.set(v[o], v[o + 1], v[o + 2]).sub(this.elbow)).add(this.elbow);
			turn.transform(n.set(v[o + 3], v[o + 4], v[o + 5]));
			emitVertex(matrix, pose, buffer, p, n, tinted ? ARGB.multiply(colors[i], tint) : colors[i], v[o + 6], v[o + 7], overlay, light);
		}
	}

	/** Draws a finger: curled at its joints, then turned with the hand. */
	public void emitFinger(int index, Part part, PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int tint) {
		FingerBend finger = this.fingers[index];
		Matrix4f matrix = pose.pose();
		float[] v = part.vertices();
		int[] colors = part.colors();
		boolean tinted = tint != -1;
		Matrix3f hand = this.forearmTurn(1.0F);
		Vector3f p = new Vector3f();
		Vector3f n = new Vector3f();
		Vector3f a = new Vector3f();
		Vector3f b = new Vector3f();
		Vector3f na = new Vector3f();
		Vector3f nb = new Vector3f();
		for (int i = 0, count = colors.length; i < count; i++) {
			int o = i * 8;
			p.set(v[o], v[o + 1], v[o + 2]);
			n.set(v[o + 3], v[o + 4], v[o + 5]);
			if (finger.curls) {
				finger.bend(p, n, a, b, na, nb);
			}
			hand.transform(p.sub(this.elbow)).add(this.elbow);
			hand.transform(n);
			emitVertex(matrix, pose, buffer, p, n, tinted ? ARGB.multiply(colors[i], tint) : colors[i], v[o + 6], v[o + 7], overlay, light);
		}
	}

	private static void emitVertex(Matrix4f matrix, PoseStack.Pose pose, VertexConsumer buffer, Vector3f p, Vector3f n, int color, float u, float v,
		int overlay, int light) {
		matrix.transformPosition(p);
		pose.transformNormal(n.x, n.y, n.z, n);
		buffer.addVertex(p.x, p.y, p.z, color, u, v, overlay, light, n.x, n.y, n.z);
	}

	static float smooth(float t) {
		return t * t * (3.0F - 2.0F * t);
	}

	/** One finger's joints this frame: each piece of the finger turned by all the joints before it. */
	private static final class FingerBend {
		private final Vector3f base;
		private final Vector3f axis;
		private final float length;
		private final float[] joints;
		/** Per piece: its turn, and where its first joint is now and was in the model. */
		private final Matrix3f[] turns;
		private final Vector3f[] movedJoints;
		private final Vector3f[] restJoints;
		private final float soft;
		final boolean curls;

		FingerBend(Finger finger, float curl) {
			this.base = finger.base();
			this.axis = finger.axis();
			this.length = finger.length();
			this.joints = finger.joints();
			this.soft = finger.rigid() ? 0.0F : 0.07F;
			this.curls = curl != 0.0F;
			int count = this.joints.length;
			this.turns = new Matrix3f[count];
			this.movedJoints = new Vector3f[count];
			this.restJoints = new Vector3f[count];
			float angle = 0.0F;
			Vector3f moved = new Vector3f(this.base);
			for (int k = 0; k < count; k++) {
				Vector3f rest = new Vector3f(this.axis).mul(this.joints[k] * this.length).add(this.base);
				if (k > 0) {
					// This joint moved with the piece before it.
					Vector3f fromPrevious = new Vector3f(rest).sub(this.restJoints[k - 1]);
					moved = this.turns[k - 1].transform(fromPrevious).add(this.movedJoints[k - 1]);
				}
				angle += finger.angles()[k] * curl;
				this.restJoints[k] = rest;
				this.movedJoints[k] = moved;
				this.turns[k] = new Matrix3f().rotation(angle, finger.bend());
			}
		}

		/** Bends a point of the finger (and its normal) in place. The other vectors are scratch space. */
		void bend(Vector3f p, Vector3f n, Vector3f a, Vector3f b, Vector3f na, Vector3f nb) {
			float s = (p.x - this.base.x) * this.axis.x + (p.y - this.base.y) * this.axis.y + (p.z - this.base.z) * this.axis.z;
			s /= this.length;
			int piece = 0;
			for (int k = 1; k < this.joints.length; k++) {
				if (s >= this.joints[k]) {
					piece = k;
				}
			}
			this.place(piece, p, n, a, na);
			// Close to a joint, the two pieces meeting there share the bend, so the skin stays smooth.
			if (this.soft > 0.0F) {
				if (piece + 1 < this.joints.length && s > this.joints[piece + 1] - this.soft) {
					float w = smooth(Math.min(1.0F, (s - (this.joints[piece + 1] - this.soft)) / (2.0F * this.soft)));
					this.place(piece + 1, p, n, b, nb);
					a.lerp(b, w);
					na.lerp(nb, w);
				} else if (piece > 0 && s < this.joints[piece] + this.soft) {
					float w = smooth(Math.max(0.0F, (s - (this.joints[piece] - this.soft)) / (2.0F * this.soft)));
					this.place(piece - 1, p, n, b, nb);
					b.lerp(a, w);
					nb.lerp(na, w);
					a.set(b);
					na.set(nb);
				}
			}
			p.set(a);
			float length = na.length();
			n.set(na).div(length > 1.0E-6F ? length : 1.0F);
		}

		private void place(int piece, Vector3f p, Vector3f n, Vector3f out, Vector3f outNormal) {
			this.turns[piece].transform(out.set(p).sub(this.restJoints[piece])).add(this.movedJoints[piece]);
			this.turns[piece].transform(outNormal.set(n));
		}
	}
}
