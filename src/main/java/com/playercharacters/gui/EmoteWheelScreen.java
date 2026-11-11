package com.playercharacters.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.playercharacters.CharacterResolver;
import com.playercharacters.PlayerCharactersClient;
import com.playercharacters.character.PlayerCharacter;
import com.playercharacters.emote.Emote;
import com.playercharacters.emote.EmotePlayer;
import com.playercharacters.emote.Emotes;
import java.util.List;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * The emote wheel, like Fortnite's: eight emotes around a circle with your character doing the chosen one in the
 * middle. Point the mouse at an emote or scroll through them (scrolling past the last one goes to the next page),
 * then click, press Enter, or let go of the wheel key after holding it.
 */
public final class EmoteWheelScreen extends Screen {
	public static final int PER_PAGE = 8;
	private static final Identifier WHEEL = texture("emote_wheel");
	private static final Identifier SLOT = texture("emote_slot");
	private static final Identifier SLOT_SELECTED = texture("emote_slot_selected");
	/** Letting go of the key plays the emote only if it was held at least this long (a tap leaves the wheel open). */
	private static final long HOLD_MILLIS = 300;
	private static int lastPage;
	private static int lastSlot;

	private final KeyMapping key;
	private final long openedAt = Util.getMillis();
	private final List<Emote> emotes = Emotes.all();
	private boolean keyHeld;
	private int page = lastPage;
	private int slot = lastSlot;
	private long chosenAt = Util.getMillis();

	public EmoteWheelScreen(KeyMapping key) {
		super(Component.translatable("playercharacters.emote_wheel"));
		this.key = key;
		// Opening a screen releases all key bindings, so ask the keyboard whether the key is still held down.
		InputConstants.Key bound = KeyBindingHelper.getBoundKeyOf(key);
		this.keyHeld = bound.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), bound.getValue());
	}

	private static Identifier texture(String name) {
		return Identifier.fromNamespaceAndPath(PlayerCharactersClient.MOD_ID, "textures/gui/" + name + ".png");
	}

	private int pages() {
		return (this.emotes.size() + PER_PAGE - 1) / PER_PAGE;
	}

	/** Room for the two lines of text at the top and the names under the bottom slot. */
	private int radius() {
		return Math.max(40, Math.min(this.width * 3 / 10, (this.height - 88) / 2));
	}

	private int centerY() {
		return Math.max(this.height / 2, 51 + this.radius());
	}

	private Emote emoteAt(int page, int slot) {
		int index = page * PER_PAGE + slot;
		return index < this.emotes.size() ? this.emotes.get(index) : null;
	}

	/** The emote that is highlighted now. */
	public Emote selected() {
		Emote emote = this.emoteAt(this.page, this.slot);
		return emote != null ? emote : this.emotes.getFirst();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, this.width, this.height, 0x70000000); // a shade: the world stays visible
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		int cx = this.width / 2;
		int cy = this.centerY();
		int r = this.radius();
		int wheel = r * 2 + 64;
		graphics.blit(RenderPipelines.GUI_TEXTURED, WHEEL, cx - wheel / 2, cy - wheel / 2, 0, 0, wheel, wheel, wheel, wheel);

		for (int i = 0; i < PER_PAGE; i++) {
			Emote emote = this.emoteAt(this.page, i);
			if (emote == null) {
				continue;
			}
			double angle = Math.toRadians(-90 + 45 * i);
			int x = cx + (int) Math.round(Math.cos(angle) * r);
			int y = cy + (int) Math.round(Math.sin(angle) * r);
			boolean selected = i == this.slot;
			int size = selected ? 46 : 38;
			graphics.blit(RenderPipelines.GUI_TEXTURED, selected ? SLOT_SELECTED : SLOT, x - size / 2, y - size / 2, 0, 0, size, size, size, size);
			String icon = emote.icon();
			float iconScale = icon.length() > 3 ? 0.75F : selected ? 2.0F : 1.6F;
			graphics.pose().pushMatrix();
			graphics.pose().translate(x, y - 4 * iconScale + (icon.length() > 3 ? 1 : 0));
			graphics.pose().scale(iconScale, iconScale);
			graphics.drawCenteredString(this.font, icon, 0, 0, selected ? 0xFF3A2A08 : 0xFFFFFFFF);
			graphics.pose().popMatrix();
			Component name = Component.translatable(emote.translationKey());
			graphics.pose().pushMatrix();
			graphics.pose().translate(x, y + size / 2.0F + 2);
			graphics.pose().scale(0.8F, 0.8F);
			graphics.drawCenteredString(this.font, name, 0, 0, selected ? 0xFFFFE08A : 0xFFE0E0E0);
			graphics.pose().popMatrix();
		}

		// You, doing the chosen emote, sized to fit the middle of the wheel.
		LocalPlayer player = this.minecraft.player;
		Emote chosen = this.selected();
		int inner = Math.max(24, r - 30);
		if (player != null) {
			PlayerCharacter character = CharacterResolver.characterFor(player.getUUID());
			float height = Math.max(1.8F, character != null && character.model() != null ? character.model().height() : 1.8F);
			int scale = Math.max(10, (int) ((inner * 2 - 18) / height));
			EmotePlayer.withPreview(player.getUUID(), chosen, this.chosenAt, () -> InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
				cx - inner, cy - inner + 2, cx + inner, cy + inner - 10, scale, 0.0625F, cx, cy, player));
		}
		graphics.drawCenteredString(this.font, Component.translatable(chosen.translationKey()), cx, cy + inner - 9, 0xFFFFFFFF);

		// Page arrows on both sides, and the title and hint above the wheel.
		graphics.drawCenteredString(this.font, Component.literal("<"), this.arrowX(-1), cy - 4, 0xFFFFFFFF);
		graphics.drawCenteredString(this.font, Component.literal(">"), this.arrowX(1), cy - 4, 0xFFFFFFFF);
		Component pageText = Component.translatable("playercharacters.emote_wheel.page", this.page + 1, this.pages());
		graphics.drawCenteredString(this.font, this.title.copy().append(" - ").append(pageText), cx, 4, 0xFFFFFFFF);
		graphics.drawCenteredString(this.font, Component.translatable("playercharacters.emote_wheel.hint"), cx, 15, 0xFFB0B0B0);
	}

	private int arrowX(int side) {
		return this.width / 2 + side * Math.min(this.width / 2 - 8, this.radius() + 52);
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		double dx = mouseX - this.width / 2.0;
		double dy = mouseY - this.centerY();
		if (Math.abs(dx) > this.radius() + 40) {
			return; // over the page arrows
		}
		if (dx * dx + dy * dy < Math.pow(this.radius() * 0.45, 2)) {
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
			if (Math.abs(event.x() - this.arrowX(side)) <= 12 && Math.abs(event.y() - this.centerY()) <= 12) {
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
