package com.playercharacters.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.playercharacters.PlayerCharactersClient;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The middle finger emote for players drawn with Minecraft's own model (their own skin, or a skin character), which
 * has no fingers or eyebrows: a finger on the raised fist, colored like their hand, and angry eyebrows.
 */
public final class VanillaEmoteParts {
	private static final Identifier WHITE = Identifier.fromNamespaceAndPath(PlayerCharactersClient.MOD_ID, "textures/white.png");
	private static final int BROW_COLOR = 0xFF24170E;
	/** A pixel at the bottom of the right arm's front side in the skin: the hand. */
	private static final float HAND_U = 45.5F / 64.0F;
	private static final float HAND_V = 31.5F / 64.0F;

	private VanillaEmoteParts() {
	}

	/** Adds the finger and eyebrows, if this player is doing the emote. Call next to the player model being drawn. */
	public static void submit(PlayerModel model, AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay) {
		CharacterRenderState characterState = (CharacterRenderState) state;
		boolean finger = characterState.playercharacters$middleFinger() > 0.4F;
		boolean mad = characterState.playercharacters$mad();
		if (!finger && !mad) {
			return;
		}
		// Drawing is deferred, so the model hasn't been posed for this player yet.
		model.setupAnim(state);

		if (finger && model.rightArm.visible) {
			poseStack.pushPose();
			model.root().translateAndRotate(poseStack);
			model.rightArm.translateAndRotate(poseStack);
			float x = state.skin.model() == PlayerModelType.SLIM ? -0.5F : -1.0F; // the middle of the arm
			Identifier skin = state.skin.body().texturePath();
			// One pixel thick, sticking out of the front of the hand. With the arm raised forward, that is up.
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutNoCull(skin), (pose, buffer) ->
				box(pose, buffer, new Vector3f(x, 9.4F, -3.6F), new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0.5F, 0.5F, 1.6F),
					-1, HAND_U, HAND_V, light, overlay));
			poseStack.popPose();
		}

		if (mad && model.head.visible) {
			poseStack.pushPose();
			model.root().translateAndRotate(poseStack);
			model.head.translateAndRotate(poseStack);
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutNoCull(WHITE), (pose, buffer) -> {
				for (int side = -1; side <= 1; side += 2) {
					// From above the eye's outer corner down to just above the nose: an angry V. Y points down here.
					Vector3f inner = new Vector3f(side * 0.6F, -4.45F, -4.25F);
					Vector3f outer = new Vector3f(side * 3.5F, -5.5F, -4.25F);
					Vector3f along = new Vector3f(outer).sub(inner).normalize();
					Vector3f center = new Vector3f(inner).add(outer).mul(0.5F);
					float halfLength = inner.distance(outer) / 2.0F;
					box(pose, buffer, center, along, new Vector3f(-along.y, along.x, 0), new Vector3f(halfLength, 0.42F, 0.2F), BROW_COLOR, 0.5F, 0.5F, light, overlay);
				}
			});
			poseStack.popPose();
		}
	}

	/**
	 * A box in pixels (1/16 block), centered on {@code center}, with its sides along {@code xAxis} and {@code yAxis},
	 * every corner using the same texture spot.
	 */
	private static void box(PoseStack.Pose pose, VertexConsumer buffer, Vector3f center, Vector3f xAxis, Vector3f yAxis, Vector3f half, int color,
		float u, float v, int light, int overlay) {
		Vector3f ax = new Vector3f(xAxis).normalize();
		Vector3f ay = new Vector3f(yAxis).normalize();
		Vector3f az = new Vector3f(ax).cross(ay).normalize();
		Vector3f[] axes = {ax, ay, az};
		float[] sizes = {half.x, half.y, half.z};
		for (int a = 0; a < 3; a++) {
			for (int sign = -1; sign <= 1; sign += 2) {
				Vector3f normal = new Vector3f(axes[a]).mul(sign);
				Vector3f faceCenter = new Vector3f(center).add(new Vector3f(axes[a]).mul(sign * sizes[a]));
				Vector3f u1 = new Vector3f(axes[(a + 1) % 3]).mul(sizes[(a + 1) % 3]);
				Vector3f u2 = new Vector3f(axes[(a + 2) % 3]).mul(sizes[(a + 2) % 3]);
				if (sign < 0) {
					u1.negate(); // keep the winding outward on both sides
				}
				Vector3f[] corners = {
					new Vector3f(faceCenter).sub(u1).sub(u2), new Vector3f(faceCenter).add(u1).sub(u2),
					new Vector3f(faceCenter).add(u1).add(u2), new Vector3f(faceCenter).sub(u1).add(u2)
				};
				for (Vector3f corner : corners) {
					vertex(pose, buffer, corner, normal, color, u, v, light, overlay);
				}
			}
		}
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vector3f pixel, Vector3f normal, int color, float u, float v, int light, int overlay) {
		Matrix4f matrix = pose.pose();
		Vector3f p = matrix.transformPosition(pixel.x / 16.0F, pixel.y / 16.0F, pixel.z / 16.0F, new Vector3f());
		Vector3f n = pose.transformNormal(normal.x, normal.y, normal.z, new Vector3f());
		buffer.addVertex(p.x, p.y, p.z, color, u, v, overlay, light, n.x, n.y, n.z);
	}
}
