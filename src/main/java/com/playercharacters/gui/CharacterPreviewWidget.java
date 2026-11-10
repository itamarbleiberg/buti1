package com.playercharacters.gui;

import com.playercharacters.CharacterResolver;
import com.playercharacters.character.PlayerCharacter;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Shows your own player as the chosen character, like the inventory screen does: it turns toward the mouse,
 * and the mouth keeps opening and closing so you can see what talking looks like.
 */
final class CharacterPreviewWidget extends AbstractWidget {
	private final Supplier<@Nullable PlayerCharacter> character;

	CharacterPreviewWidget(int width, int height, Supplier<@Nullable PlayerCharacter> character) {
		super(0, 0, width, height, Component.empty());
		this.character = character;
		this.active = false;
	}

	@Override
	protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		int scale = Math.max(10, (int) (this.getHeight() / 2.45F));
		CharacterResolver.withPreview(player.getUUID(), this.character.get(), demoJaw(),
			() -> InventoryScreen.renderEntityInInventoryFollowsMouse(
				guiGraphics, this.getX(), this.getY(), this.getRight(), this.getBottom(), scale, 0.0625F, mouseX, mouseY, player));
	}

	/** An uneven open-close pattern that looks a bit like speech. */
	private static float demoJaw() {
		double t = Util.getMillis() / 1000.0;
		double wave = Math.sin(t * 8.5) * 0.65 + Math.sin(t * 3.7 + 1.0) * 0.45;
		return (float) Math.max(0.0, Math.min(1.0, wave));
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
	}
}
