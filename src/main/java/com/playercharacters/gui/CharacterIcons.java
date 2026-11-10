package com.playercharacters.gui;

import com.playercharacters.character.PlayerCharacter;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

/** Small square pictures of characters for the menus. */
final class CharacterIcons {
	private CharacterIcons() {
	}

	/**
	 * Draws a character's icon: its icon.png, the skin's face, or its first letter.
	 *
	 * @param skin for skin characters, the frame to show (to make the face talk); null uses the idle skin
	 */
	static void draw(GuiGraphics guiGraphics, Font font, PlayerCharacter character, @Nullable PlayerSkin skin, int x, int y, int size) {
		if (character.icon() != null) {
			guiGraphics.blit(RenderPipelines.GUI_TEXTURED, character.icon(), x, y, 0.0F, 0.0F, size, size, size, size);
			return;
		}
		PlayerSkin face = skin != null ? skin : character.idleSkin();
		if (face != null) {
			PlayerFaceRenderer.draw(guiGraphics, face, x, y, size);
			return;
		}
		guiGraphics.fill(x, y, x + size, y + size, 0xFF4A4A4A);
		String letter = character.displayName().isEmpty() ? "?" : character.displayName().substring(0, 1).toUpperCase(Locale.ROOT);
		guiGraphics.drawCenteredString(font, letter, x + size / 2, y + (size - 8) / 2, -1);
	}
}
