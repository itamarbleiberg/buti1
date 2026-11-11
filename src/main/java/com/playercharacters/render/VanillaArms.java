package com.playercharacters.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.playercharacters.character.PlayerCharacter;
import com.playercharacters.emote.EmotePose;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Elbows for Minecraft's own player model (players in their own skin, and skin characters), whose arms are single
 * boxes. While an emote bends an arm, the box is hidden and drawn again here bent in the middle: the upper half stays,
 * the lower half turns at the elbow, and the ring between them turns half as far, so the skin stays in one piece.
 * Players wearing a chestplate, and hands holding something, keep straight arms (the armor and items don't bend).
 */
public final class VanillaArms {
	private VanillaArms() {
	}

	/** Whether this arm of a player drawn with Minecraft's model can be drawn bent. */
	public static boolean canBend(AvatarRenderState state, boolean right) {
		PlayerCharacter character = ((CharacterRenderState) state).playercharacters$character();
		if (character != null && character.isModel()) {
			return false; // 3D characters bend their own arms
		}
		boolean armorShows = character == null && !state.chestEquipment.isEmpty(); // characters hide armor
		boolean holding = !(right ? state.rightHandItemState : state.leftHandItemState).isEmpty();
		return !armorShows && !holding;
	}

	/** Hides an arm (and its sleeve) of the posed model, because it is drawn bent instead. */
	public static void hide(ModelPart arm) {
		arm.xScale = 0.0F;
		arm.yScale = 0.0F;
		arm.zScale = 0.0F;
	}

	/** Draws the bent arms, if any. Call next to the player model being drawn. */
	public static void submit(PlayerModel model, AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay) {
		CharacterRenderState characterState = (CharacterRenderState) state;
		EmotePose emote = characterState.playercharacters$emote();
		if (emote == null || emote.weight <= 0.0F || emote.rightElbow == 0.0F && emote.leftElbow == 0.0F || !canBend(state, true) && !canBend(state, false)) {
			return;
		}
		// Drawing is deferred, so the model hasn't been posed for this player yet (and the elbows not worked out).
		model.setupAnim(state);
		ArmPose right = characterState.playercharacters$arm(true);
		ArmPose left = characterState.playercharacters$arm(false);
		for (int side = 0; side < 2; side++) {
			boolean isRight = side == 0;
			ModelPart arm = isRight ? model.rightArm : model.leftArm;
			ModelPart sleeve = isRight ? model.rightSleeve : model.leftSleeve;
			float bend = (isRight ? right : left).bend;
			if (bend == 0.0F || !arm.visible || arm.xScale != 0.0F) {
				continue; // straight, or not hidden for bending
			}
			List<ModelPart.Cube> cubes = new ArrayList<>();
			arm.visit(new PoseStack(), (pose, path, index, cube) -> {
				if (path.isEmpty() || sleeve.visible) {
					cubes.add(cube);
				}
			});
			poseStack.pushPose();
			model.root().translateAndRotate(poseStack);
			poseStack.translate(arm.x / 16.0F, arm.y / 16.0F, arm.z / 16.0F);
			poseStack.mulPose(new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot));
			collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(state.skin.body().texturePath()), (pose, buffer) -> {
				for (ModelPart.Cube cube : cubes) {
					emitBent(cube, bend, pose, buffer, light, overlay);
				}
			});
			poseStack.popPose();
		}
	}

	/** One box of an arm, bent forward by {@code bend} radians in the middle of its length. */
	private static void emitBent(ModelPart.Cube cube, float bend, PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay) {
		float top = cube.minY;
		float bottom = cube.maxY;
		Vector3f elbow = new Vector3f((cube.minX + cube.maxX) / 2.0F, (top + bottom) / 2.0F, (cube.minZ + cube.maxZ) / 2.0F);
		// Turning around -X moves a point below the elbow toward the front (-Z), like a real elbow.
		Vector3f hinge = new Vector3f(-1.0F, 0.0F, 0.0F);
		Matrix3f[] turns = {new Matrix3f(), new Matrix3f().rotation(bend * 0.5F, hinge), new Matrix3f().rotation(bend, hinge)};
		Matrix3f[] faceTurns = {new Matrix3f().rotation(bend * 0.25F, hinge), new Matrix3f().rotation(bend * 0.75F, hinge)};
		for (ModelPart.Polygon polygon : cube.polygons) {
			ModelPart.Vertex[] v = polygon.vertices();
			Vector3f normal = new Vector3f(polygon.normal());
			if (Math.abs(polygon.normal().y()) > 0.5F) {
				// The ends: the shoulder end stays, the hand end turns all the way.
				int level = polygon.normal().y() < 0.0F ? 0 : 2;
				emitQuad(pose, buffer, v, new int[] {level, level, level, level}, null, turns, turns[level], normal, elbow, light, overlay);
				continue;
			}
			// A side: split at the elbow into an upper and a lower half.
			int[] upper = new int[4];
			int[] lower = new int[4];
			ModelPart.Vertex[] middle = new ModelPart.Vertex[4];
			for (int i = 0; i < 4; i++) {
				boolean atTop = Math.abs(v[i].y() - top) < Math.abs(v[i].y() - bottom);
				ModelPart.Vertex partner = v[i];
				for (ModelPart.Vertex other : v) {
					if (other != v[i] && Math.abs(other.x() - v[i].x()) < 1.0E-4F && Math.abs(other.z() - v[i].z()) < 1.0E-4F) {
						partner = other;
					}
				}
				middle[i] = new ModelPart.Vertex(v[i].x(), elbow.y, v[i].z(), (v[i].u() + partner.u()) / 2.0F, (v[i].v() + partner.v()) / 2.0F);
				upper[i] = atTop ? 0 : 1;
				lower[i] = atTop ? 1 : 2;
			}
			emitQuad(pose, buffer, v, upper, middle, turns, faceTurns[0], normal, elbow, light, overlay);
			emitQuad(pose, buffer, v, lower, middle, turns, faceTurns[1], normal, elbow, light, overlay);
		}
	}

	/**
	 * One quad: corner i is the polygon's own corner when its level is 0 (top) or 2 (bottom) of its half, or the corner
	 * moved to the elbow ({@code middle[i]}) when its level is 1, turned by that level's turn.
	 */
	private static void emitQuad(PoseStack.Pose pose, VertexConsumer buffer, ModelPart.Vertex[] corners, int[] levels, ModelPart.Vertex[] middle,
		Matrix3f[] turns, Matrix3f faceTurn, Vector3f faceNormal, Vector3f elbow, int light, int overlay) {
		Matrix4f matrix = pose.pose();
		Vector3f n = faceTurn.transform(new Vector3f(faceNormal));
		pose.transformNormal(n.x, n.y, n.z, n);
		Vector3f p = new Vector3f();
		for (int i = 0; i < 4; i++) {
			ModelPart.Vertex corner = levels[i] == 1 && middle != null ? middle[i] : corners[i];
			p.set(corner.x(), corner.y(), corner.z()).sub(elbow);
			turns[levels[i]].transform(p).add(elbow).div(16.0F);
			matrix.transformPosition(p);
			buffer.addVertex(p.x, p.y, p.z, -1, corner.u(), corner.v(), overlay, light, n.x, n.y, n.z);
		}
	}
}
