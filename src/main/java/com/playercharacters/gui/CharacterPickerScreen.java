package com.playercharacters.gui;

import com.playercharacters.CharacterResolver;
import com.playercharacters.character.CharacterLibrary;
import com.playercharacters.character.PlayerCharacter;
import com.playercharacters.config.Choice;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

/**
 * Lists "Default", "Own skin", "Random" and every character, next to a preview of your own player as the
 * highlighted character. The preview opens and closes the mouth so you can see what talking looks like.
 */
public final class CharacterPickerScreen extends Screen {
	private static final int ROW_HEIGHT = 26;
	private static final int FACE_SIZE = 16;
	private static final int PREVIEW_WIDTH = 90;
	private static final int GAP = 20;
	private static final int DESCRIPTION_COLOR = 0xFFA0A0A0;

	private final Screen parent;
	/** The player being edited, or null when editing the default for everyone. */
	private final @Nullable UUID playerId;
	private final UUID previewPlayer;
	private final Choice current;
	private final Consumer<Choice> onChosen;
	private @Nullable HeaderAndFooterLayout layout;
	private @Nullable OptionList list;
	private @Nullable CharacterPreviewWidget preview;

	public CharacterPickerScreen(Screen parent, @Nullable UUID playerId, @Nullable String playerName, Choice current, Consumer<Choice> onChosen) {
		super(playerName == null
			? Component.translatable("playercharacters.picker.title.default")
			: Component.translatable("playercharacters.picker.title.player", playerName));
		this.parent = parent;
		this.playerId = playerId;
		this.current = current;
		this.onChosen = onChosen;
		if (playerId != null) {
			this.previewPlayer = playerId;
		} else {
			this.previewPlayer = this.minecraft.player != null ? this.minecraft.player.getUUID() : new UUID(0L, 0L);
		}
	}

	@Override
	protected void init() {
		HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
		this.layout = layout;
		layout.addTitleHeader(this.title, this.font);
		LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(Component.translatable("playercharacters.picker.choose"), button -> this.chooseSelected()).width(150).build());
		footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).width(150).build());

		OptionList list = new OptionList(layout);
		CharacterPreviewWidget preview = new CharacterPreviewWidget(PREVIEW_WIDTH, 140, this::previewCharacter);
		this.list = list;
		this.preview = preview;
		this.addRenderableWidget(list);
		this.addRenderableOnly(preview);    // drawn after the list, so the list background doesn't cover it
		layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
	}

	@Override
	protected void repositionElements() {
		if (this.layout == null || this.list == null || this.preview == null) {
			return;
		}
		this.layout.arrangeElements();
		this.list.updateSize(this.width, this.layout);
		int previewHeight = Math.min(this.layout.getContentHeight() - 16, 150);
		this.preview.setRectangle(PREVIEW_WIDTH, previewHeight, this.list.getRowRight() + GAP,
			this.layout.getHeaderHeight() + (this.layout.getContentHeight() - previewHeight) / 2);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}

	private void chooseSelected() {
		Option selected = this.list == null ? null : this.list.getSelected();
		if (selected != null) {
			this.onChosen.accept(selected.choice);
		}
		this.minecraft.setScreen(this.parent);
	}

	/** The character shown in the preview for the highlighted option, or null for the player's own skin. */
	private @Nullable PlayerCharacter previewCharacter() {
		Option selected = this.list == null ? null : this.list.getSelected();
		return CharacterResolver.resolve(selected == null ? Choice.OWN_SKIN : selected.choice, this.previewPlayer);
	}

	private PlayerSkin ownSkin() {
		ClientPacketListener connection = this.minecraft.getConnection();
		PlayerInfo info = connection == null ? null : connection.getPlayerInfo(this.previewPlayer);
		return info != null ? info.getSkin() : DefaultPlayerSkin.get(this.previewPlayer);
	}

	private final class OptionList extends ObjectSelectionList<Option> {
		OptionList(HeaderAndFooterLayout layout) {
			super(CharacterPickerScreen.this.minecraft, CharacterPickerScreen.this.width, layout.getContentHeight(), layout.getHeaderHeight(), ROW_HEIGHT);
			boolean forPlayer = CharacterPickerScreen.this.playerId != null;
			UUID player = CharacterPickerScreen.this.previewPlayer;

			if (forPlayer) {
				this.addEntry(new Option(Choice.DEFAULT, Component.translatable("playercharacters.picker.default"),
					Component.translatable("playercharacters.picker.default.desc", ChoiceText.result(Choice.DEFAULT, player))));
			}
			this.addEntry(new Option(Choice.OWN_SKIN, Component.translatable("playercharacters.choice.own"),
				Component.translatable(forPlayer ? "playercharacters.picker.own.desc" : "playercharacters.picker.own.desc_all")));
			Component randomDescription;
			if (CharacterLibrary.all().isEmpty()) {
				randomDescription = Component.translatable("playercharacters.picker.random.empty");
			} else if (forPlayer) {
				randomDescription = Component.translatable("playercharacters.picker.random.desc", ChoiceText.result(Choice.RANDOM, player));
			} else {
				randomDescription = Component.translatable("playercharacters.picker.random.desc_all");
			}
			this.addEntry(new Option(Choice.RANDOM, Component.translatable("playercharacters.choice.random"), randomDescription));
			for (PlayerCharacter character : CharacterLibrary.all()) {
				this.addEntry(new Option(Choice.character(character.id()), Component.literal(character.displayName()),
					Component.translatable(character.isModel() ? "playercharacters.picker.model"
						: character.customTalkingFrame() ? "playercharacters.picker.mouth.custom" : "playercharacters.picker.mouth.auto")));
			}

			for (Option option : this.children()) {
				if (option.choice.equals(CharacterPickerScreen.this.current)) {
					this.setSelected(option);
					break;
				}
			}
			if (this.getSelected() == null && !this.children().isEmpty()) {
				this.setSelected(this.children().getFirst());
			}
		}

		@Override
		public int getRowWidth() {
			return Mth.clamp(this.width - PREVIEW_WIDTH - GAP - 40, 150, 230);
		}

		/** Rows sit left of center to leave room for the preview on the right. */
		@Override
		public int getRowLeft() {
			return this.getX() + (this.width - (this.getRowWidth() + GAP + PREVIEW_WIDTH)) / 2;
		}
	}

	private final class Option extends ObjectSelectionList.Entry<Option> {
		final Choice choice;
		private final Component label;
		private final Component description;

		Option(Choice choice, Component label, Component description) {
			this.choice = choice;
			this.label = label;
			this.description = description;
		}

		@Override
		public void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			CharacterPickerScreen screen = CharacterPickerScreen.this;
			int faceX = this.getContentX() + 3;
			int faceY = this.getContentYMiddle() - FACE_SIZE / 2;
			this.drawIcon(guiGraphics, faceX, faceY);

			int textX = faceX + FACE_SIZE + 6;
			int maxWidth = this.getContentRight() - textX - 2;
			guiGraphics.drawString(screen.font, screen.font.plainSubstrByWidth(this.label.getString(), maxWidth), textX, this.getContentY() + 2, -1);
			guiGraphics.drawString(screen.font, screen.font.plainSubstrByWidth(this.description.getString(), maxWidth), textX, this.getContentY() + 12,
				DESCRIPTION_COLOR);
		}

		private void drawIcon(GuiGraphics guiGraphics, int x, int y) {
			CharacterPickerScreen screen = CharacterPickerScreen.this;
			if (this.choice.kind() == Choice.Kind.RANDOM && screen.playerId == null) {
				guiGraphics.drawCenteredString(screen.font, "?", x + FACE_SIZE / 2, y + 4, -1);
				return;
			}
			PlayerCharacter character = CharacterResolver.resolve(this.choice, screen.previewPlayer);
			if (character != null) {
				CharacterIcons.draw(guiGraphics, screen.font, character, null, x, y, FACE_SIZE);
			} else if (this.choice.kind() == Choice.Kind.RANDOM) {
				guiGraphics.drawCenteredString(screen.font, "?", x + FACE_SIZE / 2, y + 4, -1);
			} else {
				PlayerFaceRenderer.draw(guiGraphics, screen.ownSkin(), x, y, FACE_SIZE);
			}
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			CharacterPickerScreen.this.list.setSelected(this);
			if (doubleClick) {
				CharacterPickerScreen.this.chooseSelected();
			}
			return true;
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			if (event.isSelection()) {
				CharacterPickerScreen.this.list.setSelected(this);
				CharacterPickerScreen.this.chooseSelected();
				return true;
			}
			return super.keyPressed(event);
		}

		@Override
		public Component getNarration() {
			return Component.translatable("narrator.select", this.label);
		}
	}
}
