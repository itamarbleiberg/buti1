package com.playercharacters.gui;

import com.playercharacters.CharacterResolver;
import com.playercharacters.character.CharacterLibrary;
import com.playercharacters.character.PlayerCharacter;
import com.playercharacters.config.CharacterSettings;
import com.playercharacters.speech.SpeechModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/** The menu opened with the "Open character menu" key: pick a character for each player on the server. */
public final class CharacterMenuScreen extends Screen {
	private static final Component TITLE = Component.translatable("playercharacters.menu.title");
	private static final Component SEARCH_HINT = Component.translatable("playercharacters.menu.search").withStyle(EditBox.SEARCH_HINT_STYLE);
	private static final Component YOU = Component.translatable("playercharacters.menu.you").withStyle(ChatFormatting.GRAY);
	private static final boolean VOICE_CHAT_INSTALLED = FabricLoader.getInstance().isModLoaded("voicechat");
	private static final int HEADER_HEIGHT = 44;
	private static final int FOOTER_HEIGHT = 96;
	private static final int ROW_HEIGHT = 24;
	private static final int FACE_SIZE = 16;
	private static final int CHOICE_BUTTON_WIDTH = 130;

	private final @Nullable Screen parent;
	private final CharacterSettings settings = CharacterSettings.get();
	private @Nullable HeaderAndFooterLayout layout;
	private @Nullable PlayerList playerList;
	private @Nullable EditBox search;
	private @Nullable Button defaultButton;
	private String filter = "";

	public CharacterMenuScreen(@Nullable Screen parent) {
		super(TITLE);
		this.parent = parent;
	}

	@Override
	protected void init() {
		HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, HEADER_HEIGHT, FOOTER_HEIGHT);
		this.layout = layout;

		LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(4));
		header.defaultCellSetting().alignHorizontallyCenter();
		header.addChild(new StringWidget(TITLE, this.font));
		EditBox search = header.addChild(new EditBox(this.font, 0, 0, 200, 15, SEARCH_HINT));
		search.setHint(SEARCH_HINT);
		search.setValue(this.filter);
		search.setResponder(text -> {
			this.filter = text;
			if (this.playerList != null) {
				this.playerList.refresh();
			}
		});
		this.search = search;

		this.playerList = layout.addToContents(new PlayerList(layout));

		LinearLayout footer = layout.addToFooter(LinearLayout.vertical().spacing(4));
		footer.defaultCellSetting().alignHorizontallyCenter();
		StringWidget status = footer.addChild(new StringWidget(this.statusText(), this.font));
		status.setMaxWidth(this.width - 20);
		status.setTooltip(this.statusTooltip());

		LinearLayout settingsRow = footer.addChild(LinearLayout.horizontal().spacing(8));
		this.defaultButton = settingsRow.addChild(Button.builder(ChoiceText.forDefault(), button -> this.openDefaultPicker())
			.width(170)
			.tooltip(Tooltip.create(Component.translatable("playercharacters.menu.default.tooltip")))
			.build());
		settingsRow.addChild(CycleButton.onOffBuilder(this.settings.talkingMouths())
			.withTooltip(value -> Tooltip.create(Component.translatable("playercharacters.menu.talking_mouths.tooltip")))
			.create(0, 0, 140, 20, Component.translatable("playercharacters.menu.talking_mouths"),
				(button, value) -> this.settings.setTalkingMouths(value)));

		LinearLayout emoteRow = footer.addChild(LinearLayout.horizontal().spacing(8));
		emoteRow.addChild(CycleButton.onOffBuilder(this.settings.fartDetection())
			.withTooltip(value -> Tooltip.create(Component.translatable("playercharacters.menu.farts.tooltip")))
			.create(0, 0, 130, 20, Component.translatable("playercharacters.menu.farts"),
				(button, value) -> this.settings.setFartDetection(value)));
		emoteRow.addChild(CycleButton.onOffBuilder(this.settings.middleFinger())
			.withTooltip(value -> Tooltip.create(Component.translatable("playercharacters.menu.middle_finger.tooltip")))
			.create(0, 0, 180, 20, Component.translatable("playercharacters.menu.middle_finger"),
				(button, value) -> this.settings.setMiddleFinger(value)));

		LinearLayout buttonRow = footer.addChild(LinearLayout.horizontal().spacing(8));
		buttonRow.addChild(Button.builder(Component.translatable("playercharacters.menu.open_folder"),
				button -> Util.getPlatform().openPath(CharacterLibrary.folder()))
			.width(120)
			.tooltip(Tooltip.create(Component.translatable("playercharacters.menu.open_folder.tooltip")))
			.build());
		buttonRow.addChild(Button.builder(Component.translatable("playercharacters.menu.reload"), button -> {
				CharacterLibrary.reload();
				this.rebuildWidgets();
			})
			.width(100)
			.build());
		buttonRow.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(80).build());

		layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
	}

	@Override
	protected void repositionElements() {
		if (this.layout == null) {
			return;
		}
		this.layout.arrangeElements();
		if (this.playerList != null) {
			this.playerList.updateSize(this.width, this.layout);
		}
		// Also runs when coming back from the character picker.
		if (this.defaultButton != null) {
			this.defaultButton.setMessage(ChoiceText.forDefault());
		}
	}

	@Override
	protected void setInitialFocus() {
		if (this.search != null) {
			this.setInitialFocus(this.search);
		} else {
			super.setInitialFocus();
		}
	}

	@Override
	public void tick() {
		if (this.playerList != null) {
			this.playerList.refreshIfPlayersChanged();
		}
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}

	private void openDefaultPicker() {
		this.minecraft.setScreen(new CharacterPickerScreen(this, null, null, this.settings.defaultChoice(), this.settings::setDefaultChoice));
	}

	private Component statusText() {
		int count = CharacterLibrary.all().size();
		MutableComponent text = switch (count) {
			case 0 -> Component.translatable("playercharacters.status.none").withStyle(ChatFormatting.YELLOW);
			case 1 -> Component.translatable("playercharacters.status.loaded_one").withStyle(ChatFormatting.GRAY);
			default -> Component.translatable("playercharacters.status.loaded", count).withStyle(ChatFormatting.GRAY);
		};
		int problems = CharacterLibrary.problems().size();
		if (problems > 0) {
			text.append(Component.literal("  "))
				.append(Component.translatable("playercharacters.status.problems", problems).withStyle(ChatFormatting.YELLOW));
		}
		if (!VOICE_CHAT_INSTALLED) {
			text.append(Component.literal("  "))
				.append(Component.translatable("playercharacters.status.no_voice_chat").withStyle(ChatFormatting.RED));
		} else if (SpeechModel.status() == SpeechModel.Status.UNAVAILABLE && (this.settings.fartDetection() || this.settings.middleFinger())) {
			text.append(Component.literal("  "))
				.append(Component.translatable("playercharacters.status.no_speech").withStyle(ChatFormatting.RED));
		}
		return text;
	}

	private @Nullable Tooltip statusTooltip() {
		List<String> lines = new ArrayList<>(CharacterLibrary.problems());
		if (!VOICE_CHAT_INSTALLED) {
			lines.add(Component.translatable("playercharacters.status.no_voice_chat.tooltip").getString());
		} else if (SpeechModel.status() == SpeechModel.Status.UNAVAILABLE && (this.settings.fartDetection() || this.settings.middleFinger())) {
			lines.add(Component.translatable("playercharacters.status.no_speech.tooltip", String.valueOf(SpeechModel.problem())).getString());
		}
		return lines.isEmpty() ? null : Tooltip.create(Component.literal(String.join("\n", lines)));
	}

	/** Everyone in the tab list, you first, then alphabetical. */
	private List<PlayerInfo> onlinePlayers() {
		ClientPacketListener connection = this.minecraft.getConnection();
		if (connection == null) {
			return List.of();
		}
		List<PlayerInfo> players = new ArrayList<>(connection.getListedOnlinePlayers());
		LocalPlayer self = this.minecraft.player;
		UUID selfId = self == null ? null : self.getUUID();
		if (selfId != null && players.stream().noneMatch(info -> info.getProfile().id().equals(selfId))) {
			PlayerInfo selfInfo = connection.getPlayerInfo(selfId);
			if (selfInfo != null) {
				players.add(selfInfo);
			}
		}
		players.sort(Comparator.comparing((PlayerInfo info) -> !info.getProfile().id().equals(selfId))
			.thenComparing(info -> info.getProfile().name(), String.CASE_INSENSITIVE_ORDER));
		return players;
	}

	private final class PlayerList extends ContainerObjectSelectionList<PlayerRow> {
		private Set<UUID> shownPlayers = Set.of();

		PlayerList(HeaderAndFooterLayout layout) {
			super(CharacterMenuScreen.this.minecraft, CharacterMenuScreen.this.width, layout.getContentHeight(), layout.getHeaderHeight(), ROW_HEIGHT);
			this.refresh();
		}

		void refresh() {
			List<PlayerInfo> players = CharacterMenuScreen.this.onlinePlayers();
			this.shownPlayers = ids(players);
			String needle = CharacterMenuScreen.this.filter.strip().toLowerCase(Locale.ROOT);
			this.replaceEntries(players.stream()
				.filter(info -> needle.isEmpty() || info.getProfile().name().toLowerCase(Locale.ROOT).contains(needle))
				.map(info -> new PlayerRow(info))
				.toList());
			this.refreshScrollAmount();
		}

		/** Keeps the list up to date when players join or leave while the menu is open. */
		void refreshIfPlayersChanged() {
			if (!ids(CharacterMenuScreen.this.onlinePlayers()).equals(this.shownPlayers)) {
				this.refresh();
			}
		}

		private static Set<UUID> ids(List<PlayerInfo> players) {
			Set<UUID> ids = new HashSet<>();
			players.forEach(info -> ids.add(info.getProfile().id()));
			return ids;
		}

		@Override
		public int getRowWidth() {
			return Math.min(320, this.width - 40);
		}
	}

	private final class PlayerRow extends ContainerObjectSelectionList.Entry<PlayerRow> {
		private final PlayerInfo info;
		private final UUID id;
		private final String name;
		private final boolean self;
		private final Button button;

		PlayerRow(PlayerInfo info) {
			this.info = info;
			this.id = info.getProfile().id();
			this.name = info.getProfile().name();
			LocalPlayer player = CharacterMenuScreen.this.minecraft.player;
			this.self = player != null && player.getUUID().equals(this.id);
			this.button = Button.builder(Component.empty(), button -> this.openPicker()).width(CHOICE_BUTTON_WIDTH).build();
		}

		private void openPicker() {
			CharacterSettings settings = CharacterMenuScreen.this.settings;
			CharacterMenuScreen.this.minecraft.setScreen(new CharacterPickerScreen(
				CharacterMenuScreen.this, this.id, this.name, settings.choiceFor(this.id),
				choice -> settings.setChoice(this.id, this.name, choice)));
		}

		@Override
		public void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			int faceY = this.getContentYMiddle() - FACE_SIZE / 2;
			int faceX = this.getContentX() + 2;
			int textX = faceX + FACE_SIZE + 6;
			int textY = this.getContentYMiddle() - 4;

			this.button.setMessage(ChoiceText.forPlayer(CharacterMenuScreen.this.settings.choiceFor(this.id), this.id));
			this.button.setX(this.getContentRight() - this.button.getWidth());
			this.button.setY(this.getContentYMiddle() - this.button.getHeight() / 2);
			int characterFaceX = this.button.getX() - FACE_SIZE - 6;

			// Real face and name on the left, so you can tell who is who.
			PlayerFaceRenderer.draw(guiGraphics, this.info.getSkin(), faceX, faceY, FACE_SIZE);
			int maxTextWidth = characterFaceX - 6 - textX;
			String shownName = CharacterMenuScreen.this.font.plainSubstrByWidth(this.name, maxTextWidth);
			guiGraphics.drawString(CharacterMenuScreen.this.font, shownName, textX, textY, -1);
			int nameWidth = CharacterMenuScreen.this.font.width(shownName);
			if (this.self && nameWidth + 4 + CharacterMenuScreen.this.font.width(YOU) <= maxTextWidth) {
				guiGraphics.drawString(CharacterMenuScreen.this.font, YOU, textX + nameWidth + 4, textY, -1);
			}

			// The character's picture next to the button. Skin characters move their mouth while they talk.
			PlayerCharacter character = CharacterResolver.characterFor(this.id);
			if (character != null) {
				CharacterIcons.draw(guiGraphics, CharacterMenuScreen.this.font, character, CharacterResolver.skinFor(this.id), characterFaceX, faceY, FACE_SIZE);
			}
			this.button.render(guiGraphics, mouseX, mouseY, partialTick);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of(this.button);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of(this.button);
		}
	}
}
