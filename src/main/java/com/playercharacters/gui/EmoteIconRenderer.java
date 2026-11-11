package com.playercharacters.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Draws one emote wheel picture: your character frozen in the middle of an emote. Like Minecraft's entity pictures in
 * menus, but a picture is only drawn again when it changes (another page, another character), because the wheel shows
 * eight of them at once. Fabric gives every picture on screen its own renderer.
 */
public final class EmoteIconRenderer extends PictureInPictureRenderer<EmoteIconRenderer.State> {
	private final EntityRenderDispatcher entityRenderDispatcher;
	private @Nullable Object drawnKey;
	private static int drawn;

	public EmoteIconRenderer(MultiBufferSource.BufferSource bufferSource, EntityRenderDispatcher entityRenderDispatcher) {
		super(bufferSource);
		this.entityRenderDispatcher = entityRenderDispatcher;
	}

	/** How many emote pictures were drawn (not just shown again) since the game started. */
	public static int drawnCount() {
		return drawn;
	}

	/**
	 * One picture.
	 *
	 * @param key what is in it: the same key means the same picture, so it doesn't need drawing again
	 */
	public record State(EntityRenderState renderState, Object key, Vector3f translation, Quaternionf rotation, int x0, int y0, int x1, int y1, float scale,
		@Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {
		public State(EntityRenderState renderState, Object key, Vector3f translation, Quaternionf rotation, int x0, int y0, int x1, int y1, float scale) {
			this(renderState, key, translation, rotation, x0, y0, x1, y1, scale, null, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, null));
		}
	}

	@Override
	public Class<State> getRenderStateClass() {
		return State.class;
	}

	@Override
	protected boolean textureIsReadyToBlit(State state) {
		return Objects.equals(state.key(), this.drawnKey);
	}

	@Override
	protected void renderToTexture(State state, PoseStack poseStack) {
		Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
		Vector3f translation = state.translation();
		poseStack.translate(translation.x, translation.y, translation.z);
		poseStack.mulPose(state.rotation());
		FeatureRenderDispatcher features = Minecraft.getInstance().gameRenderer.getFeatureRenderDispatcher();
		this.entityRenderDispatcher.submit(state.renderState(), new CameraRenderState(), 0.0, 0.0, 0.0, poseStack, features.getSubmitNodeStorage());
		features.renderAllFeatures();
		this.drawnKey = state.key();
		drawn++;
	}

	@Override
	protected float getTranslateY(int height, int guiScale) {
		return height / 2.0F;
	}

	@Override
	protected String getTextureLabel() {
		return "emote icon";
	}
}
