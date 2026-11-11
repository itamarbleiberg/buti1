package com.playercharacters.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.playercharacters.CharacterResolver;
import com.playercharacters.PlayerCharactersClient;
import com.playercharacters.character.CharacterModel;
import com.playercharacters.character.PlayerCharacter;
import com.playercharacters.emote.Emote;
import com.playercharacters.emote.EmoteMusic;
import com.playercharacters.emote.EmotePlayer;
import com.playercharacters.emote.Emotes;
import java.util.List;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The emote wheel, like Fortnite's: eight glassy slices around a circle, each showing your own character frozen in
 * the middle of that emote, and in the middle your character doing the highlighted one. Point the mouse at a slice or
 * scroll (scrolling past the last one goes to the next page), then click, press Enter, or let go of the wheel key
 * after holding it.
 */
public final class EmoteWheelScreen extends Screen {
	public static final int PER_PAGE = 8;
	private static final Identifier SLICE = texture("wheel_slice");
	private static final Identifier SLICE_HOVER = texture("wheel_slice_hover");
	private static final Identifier SLICE_GLOW = texture("wheel_slice_glow");
	private static final Identifier HUB = texture("wheel_hub");
	private static final Identifier RING = texture("wheel_ring");
	private static final Identifier VIGNETTE = texture("wheel_vignette");
	private static final Identifier DOT = texture("wheel_dot");
	private static final Identifier ARROW = texture("wheel_arrow");
	/** Where a slice's picture sits around the wheel's middle, in outer radii (see tools/EmoteWheelTextures.java). */
	private static final float SLICE_HALF_WIDTH = 0.4727F;
	private static final float SLICE_TOP = -1.09F;
	private static final float SLICE_BOTTOM = -0.3165F;
	private static final float INNER = 0.44F;
	/** The hub picture's disc fills 0.86 of its half size; the ring's circle 0.92. */
	private static final float HUB_DISC = 0.86F;
	private static final float RING_CIRCLE = 0.92F;
	/** Letting go of the key plays the emote only if it was held at least this long (a tap leaves the wheel open). */
	private static final long HOLD_MILLIS = 300;
	private static final long OPEN_MILLIS = 160;
	private static int lastPage;
	private static int lastSlot;

	private final KeyMapping key;
	private final long openedAt = Util.getMillis();
	private final List<Emote> emotes = Emotes.all();
	private final float[] highlight = new float[PER_PAGE];
	private boolean keyHeld;
	private int page = lastPage;
	private int slot = lastSlot;
	private long chosenAt = Util.getMillis();
	private long lastFrame = Util.getMillis();
	private int iconsSubmitted;

	public EmoteWheelScreen(KeyMapping key) {
		super(Component.translatable("playercharacters.emote_wheel"));
		this.key = key;
		// Opening a screen releases all key bindings, so ask the keyboard whether the key is still held down.
		InputConstants.Key bound = KeyBindingHelper.getBoundKeyOf(key);
		this.keyHeld = bound.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), bound.getValue());
		this.highlight[Mth.clamp(this.slot, 0, PER_PAGE - 1)] = 1.0F;
		EmoteMusic.rescan();
	}

	private static Identifier texture(String name) {
		return Identifier.fromNamespaceAndPath(PlayerCharactersClient.MOD_ID, "textures/gui/" + name + ".png");
	}

	private int pages() {
		return (this.emotes.size() + PER_PAGE - 1) / PER_PAGE;
	}

	/** The outer radius: as big as fits between the title at the top and the name and page dots at the bottom. */
	private int radius() {
		return Math.max(48, Math.min(Math.min(this.width * 2 / 5, 170), (this.height - 78) / 2));
	}

	private int centerY() {
		return Math.max(this.height / 2 - 4, 30 + this.radius());
	}

	private @Nullable Emote emoteAt(int page, int slot) {
		int index = page * PER_PAGE + slot;
		return index < this.emotes.size() ? this.emotes.get(index) : null;
	}

	/** The emote that is highlighted now. */
	public Emote selected() {
		Emote emote = this.emoteAt(this.page, this.slot);
		return emote != null ? emote : this.emotes.getFirst();
	}

	/** How many emote pictures the last frame showed. */
	public int iconsShown() {
		return this.iconsSubmitted;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		float open = this.openAmount();
		graphics.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0, 0, this.width, this.height, this.width, this.height,
			ARGB.white(open));
	}

	/** 0 to 1 while the wheel opens. */
	private float openAmount() {
		float t = Mth.clamp((Util.getMillis() - this.openedAt) / (float) OPEN_MILLIS, 0.0F, 1.0F);
		return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		long now = Util.getMillis();
		float dt = Math.min(0.1F, (now - this.lastFrame) / 1000.0F);
		this.lastFrame = now;
		for (int i = 0; i < PER_PAGE; i++) {
			float target = i == this.slot ? 1.0F : 0.0F;
			this.highlight[i] += (target - this.highlight[i]) * (1.0F - (float) Math.exp(-18.0F * dt));
		}
		float open = this.openAmount();
		int cx = this.width / 2;
		int cy = this.centerY();
		float r = this.radius() * (0.88F + 0.12F * open);
		int fade = ARGB.white(open);

		// The ring around everything, then the slices.
		graphics.nextStratum();
		int ring = Math.round(r * 1.06F / RING_CIRCLE);
		graphics.blit(RenderPipelines.GUI_TEXTURED, RING, cx - ring, cy - ring, 0, 0, ring * 2, ring * 2, ring * 2, ring * 2, ARGB.white(0.8F * open));
		for (int i = 0; i < PER_PAGE; i++) {
			Emote emote = this.emoteAt(this.page, i);
			float h = this.highlight[i];
			float pop = r * 0.045F * h;
			graphics.pose().pushMatrix();
			graphics.pose().translate(cx, cy);
			graphics.pose().rotate((float) Math.toRadians(45.0 * i));
			graphics.pose().translate(0, -pop);
			int x0 = Math.round(-SLICE_HALF_WIDTH * r);
			int y0 = Math.round(SLICE_TOP * r);
			int w = Math.round(2 * SLICE_HALF_WIDTH * r);
			int sliceHeight = Math.round((SLICE_BOTTOM - SLICE_TOP) * r);
			if (h > 0.02F) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, SLICE_GLOW, x0, y0, 0, 0, w, sliceHeight, w, sliceHeight, ARGB.white(h * open));
			}
			int alpha = emote == null ? ARGB.white(0.35F * open) : fade;
			graphics.blit(RenderPipelines.GUI_TEXTURED, SLICE, x0, y0, 0, 0, w, sliceHeight, w, sliceHeight, alpha);
			if (h > 0.02F) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, SLICE_HOVER, x0, y0, 0, 0, w, sliceHeight, w, sliceHeight, ARGB.white(h * open));
			}
			graphics.pose().popMatrix();
		}
		int hub = Math.round(r * (INNER - 0.04F) / HUB_DISC);
		graphics.blit(RenderPipelines.GUI_TEXTURED, HUB, cx - hub, cy - hub, 0, 0, hub * 2, hub * 2, hub * 2, hub * 2, fade);

		// Your character in every slice, and doing the highlighted emote in the middle.
		graphics.nextStratum();
		this.iconsSubmitted = 0;
		LocalPlayer player = this.minecraft.player;
		Emote chosen = this.selected();
		if (player != null) {
			PlayerCharacter character = CharacterResolver.characterFor(player.getUUID());
			for (int i = 0; i < PER_PAGE; i++) {
				Emote emote = this.emoteAt(this.page, i);
				if (emote == null) {
					continue;
				}
				double angle = Math.toRadians(-90 + 45 * i);
				float middle = r * ((INNER + 1.0F) / 2.0F + 0.045F * this.highlight[i]);
				int x = cx + Math.round((float) Math.cos(angle) * middle);
				int y = cy + Math.round((float) Math.sin(angle) * middle);
				int size = Math.round(r * (1.0F - INNER) * (0.86F + 0.1F * this.highlight[i]));
				this.icon(graphics, player, character, emote, x, y, size);
			}
			int inner = Math.round(hub * HUB_DISC * 0.92F);
			this.preview(graphics, player, character, chosen, cx, cy, inner);
		}

		// The name of the highlighted emote under the wheel, page dots, arrows, and the title and hint above.
		graphics.nextStratum();
		Component name = Component.translatable(chosen.translationKey());
		boolean music = chosen.music() != null || EmoteMusic.fileFor(chosen) != null;
		if (music) {
			name = Component.empty().append(name).append(Component.literal("  \u266b").withStyle(ChatFormatting.AQUA));
		}
		int nameY = cy + Math.round(r * 1.06F) + 6;
		graphics.pose().pushMatrix();
		graphics.pose().translate(cx, nameY);
		graphics.pose().scale(1.5F, 1.5F);
		graphics.drawCenteredString(this.font, name.copy().withStyle(ChatFormatting.BOLD), 0, 0, ARGB.color(Math.round(255 * open), 0xFFFFFF));
		graphics.pose().popMatrix();
		int dotsY = nameY + 18;
		for (int p = 0; p < this.pages(); p++) {
			int dx = cx + (p * 2 - (this.pages() - 1)) * 6;
			int size = p == this.page ? 7 : 5;
			int color = p == this.page ? ARGB.color(Math.round(255 * open), 0x6FE9FF) : ARGB.color(Math.round(150 * open), 0x9AA6D0);
			graphics.blit(RenderPipelines.GUI_TEXTURED, DOT, dx - size / 2, dotsY - size / 2, 0, 0, size, size, size, size, color);
		}
		for (int side = -1; side <= 1; side += 2) {
			int ax = this.arrowX(side);
			boolean hot = Math.abs(mouseX - ax) <= 12 && Math.abs(mouseY - cy) <= 14;
			int size = hot ? 20 : 16;
			graphics.pose().pushMatrix();
			graphics.pose().translate(ax, cy);
			graphics.pose().scale(side, 1.0F);
			graphics.blit(RenderPipelines.GUI_TEXTURED, ARROW, -size / 2, -size / 2, 0, 0, size, size, size, size, ARGB.white((hot ? 1.0F : 0.7F) * open));
			graphics.pose().popMatrix();
		}
		Component title = this.title.copy().withStyle(ChatFormatting.BOLD)
			.append(Component.literal("   ").append(Component.translatable("playercharacters.emote_wheel.page", this.page + 1, this.pages()))
				.withStyle(ChatFormatting.GRAY));
		graphics.drawCenteredString(this.font, title, cx, 6, 0xFFFFFFFF);
		graphics.drawCenteredString(this.font, Component.translatable("playercharacters.emote_wheel.hint"), cx, 17, 0xFFA8B4D8);
	}

	/** One slice's picture: your character frozen at the emote's best moment. Only drawn again when it changes. */
	private void icon(GuiGraphics graphics, LocalPlayer player, @Nullable PlayerCharacter character, Emote emote, int x, int y, int size) {
		long frozen = Util.getMillis() - (long) (emote.iconTime() * 1000.0F);
		EntityRenderState state = this.extract(player, character, emote, frozen, 25.0F);
		if (state == null) {
			return;
		}
		state.distanceToCameraSq = 20.0 * 20.0; // the medium copy of 3D characters: plenty for a picture this small
		float height = heightOf(character);
		float scale = size / (height * 1.12F);
		Vector3f translation = new Vector3f(0.0F, height / 2.0F + (fourLegs(character) ? 0.05F : 0.0F), 0.0F);
		Quaternionf rotation = new Quaternionf().rotateZ(Mth.PI).rotateX((float) Math.toRadians(-8));
		Object key = List.of(emote.id(), character == null ? "own" : character, size, player.getSkin().body().texturePath());
		int half = size / 2;
		graphics.guiRenderState.submitPicturesInPictureState(
			new EmoteIconRenderer.State(state, key, translation, rotation, x - half, y - half, x + half, y + half, scale));
		this.iconsSubmitted++;
	}

	/** The middle: your character doing the highlighted emote, over and over. */
	private void preview(GuiGraphics graphics, LocalPlayer player, @Nullable PlayerCharacter character, Emote emote, int cx, int cy, int inner) {
		EntityRenderState state = this.extract(player, character, emote, this.chosenAt, 20.0F + 15.0F * Mth.sin(Util.getMillis() / 1400.0F));
		if (state == null) {
			return;
		}
		float height = heightOf(character);
		float scale = inner * 2 / (height * 1.15F);
		Vector3f translation = new Vector3f(0.0F, height / 2.0F, 0.0F);
		Quaternionf rotation = new Quaternionf().rotateZ(Mth.PI).rotateX((float) Math.toRadians(-6));
		graphics.submitEntityRenderState(state, scale, translation, rotation, null, cx - inner, cy - inner, cx + inner, cy + inner);
	}

	/** Your render state with the emote's pose at a time, turned {@code turn} degrees from facing the screen. */
	private @Nullable EntityRenderState extract(LocalPlayer player, @Nullable PlayerCharacter character, Emote emote, long startMillis, float turn) {
		EntityRenderState[] out = new EntityRenderState[1];
		EntityRenderer<? super LocalPlayer, ?> renderer = this.minecraft.getEntityRenderDispatcher().getRenderer(player);
		CharacterResolver.withPreview(player.getUUID(), character, 0.0F,
			() -> EmotePlayer.withPreview(player.getUUID(), emote, startMillis, () -> out[0] = renderer.createRenderState(player, 1.0F)));
		EntityRenderState state = out[0];
		if (state == null) {
			return null;
		}
		state.lightCoords = LightTexture.FULL_BRIGHT;
		state.shadowPieces.clear();
		state.outlineColor = 0;
		if (state instanceof LivingEntityRenderState living) {
			living.bodyRot = 180.0F + turn;
			living.yRot = 0.0F;
			living.xRot = 0.0F;
			living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
			living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
			living.scale = 1.0F;
		}
		return state;
	}

	private static float heightOf(@Nullable PlayerCharacter character) {
		CharacterModel model = character == null ? null : character.model();
		return Math.max(1.8F, model != null ? model.height() : 1.8F);
	}

	private static boolean fourLegs(@Nullable PlayerCharacter character) {
		return character != null && character.model() != null && character.model().extras().fourLegs();
	}

	private int arrowX(int side) {
		return this.width / 2 + side * Math.min(this.width / 2 - 12, this.radius() + 34);
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		double dx = mouseX - this.width / 2.0;
		double dy = mouseY - this.centerY();
		if (Math.abs(dx) > this.radius() + 22) {
			return; // over the page arrows
		}
		if (dx * dx + dy * dy < Math.pow(this.radius() * INNER * 0.9, 2)) {
			return; // the middle: keep the current choice
		}
		int pointed = Math.floorMod((int) Math.round((Math.toDegrees(Math.atan2(dy, dx)) + 90) / 45.0), PER_PAGE);
		if (this.emoteAt(this.page, pointed) != null) {
			this.choose(this.page, pointed);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0) {
			this.step(scrollY > 0 ? -1 : 1);
		}
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			this.onClose();
			return true;
		}
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		for (int side = -1; side <= 1; side += 2) { // the page arrows
			if (Math.abs(event.x() - this.arrowX(side)) <= 12 && Math.abs(event.y() - this.centerY()) <= 14) {
				this.turnPage(side);
				return true;
			}
		}
		this.play();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.key.matches(event)) {
			if (!this.keyHeld) {
				this.onClose(); // pressed again: close
			}
			return true;
		}
		switch (event.key()) {
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_SPACE -> {
				this.play();
				return true;
			}
			case InputConstants.KEY_LEFT -> {
				this.turnPage(-1);
				return true;
			}
			case InputConstants.KEY_RIGHT -> {
				this.turnPage(1);
				return true;
			}
			case InputConstants.KEY_UP -> {
				this.step(-1);
				return true;
			}
			case InputConstants.KEY_DOWN -> {
				this.step(1);
				return true;
			}
			default -> {
			}
		}
		int digit = event.key() - InputConstants.KEY_1;
		if (digit >= 0 && digit < PER_PAGE && this.emoteAt(this.page, digit) != null) {
			this.choose(this.page, digit);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (this.key.matches(event) && this.keyHeld) {
			this.keyHeld = false;
			// Held down while choosing, then let go: play it (or just close if nothing was chosen meanwhile).
			if (Util.getMillis() - this.openedAt >= HOLD_MILLIS) {
				if (this.chosenAt > this.openedAt) {
					this.play();
				} else {
					this.onClose();
				}
			}
			return true;
		}
		return super.keyReleased(event);
	}

	/** Moves the highlight by {@code delta} emotes, across pages. */
	public void step(int delta) {
		int index = Math.floorMod(this.page * PER_PAGE + this.slot + delta, this.emotes.size());
		this.choose(index / PER_PAGE, index % PER_PAGE);
	}

	public void turnPage(int delta) {
		int page = Math.floorMod(this.page + delta, this.pages());
		int slot = Math.min(this.slot, Math.max(0, this.emotes.size() - page * PER_PAGE - 1));
		this.choose(page, slot);
	}

	private void choose(int page, int slot) {
		if (page != this.page || slot != this.slot) {
			this.page = page;
			this.slot = slot;
			this.chosenAt = Util.getMillis();
		}
	}

	/** Plays the highlighted emote and closes the wheel. */
	public void play() {
		Emote emote = this.selected();
		lastPage = this.page;
		lastSlot = this.slot;
		this.onClose();
		EmotePlayer.playLocal(emote);
	}

	@Override
	public void onClose() {
		lastPage = this.page;
		lastSlot = Mth.clamp(this.slot, 0, PER_PAGE - 1);
		super.onClose();
	}
}
