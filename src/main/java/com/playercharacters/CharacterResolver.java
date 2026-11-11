package com.playercharacters;

import com.playercharacters.character.CharacterLibrary;
import com.playercharacters.character.PlayerCharacter;
import com.playercharacters.config.CharacterSettings;
import com.playercharacters.config.Choice;
import com.playercharacters.share.CharacterSharing;
import com.playercharacters.voice.TalkTracker;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

/** Decides which character (if any) a player is shown as. */
public final class CharacterResolver {
	/** While the character picker draws its preview: what to show for one player instead of their real choice. */
	private static @Nullable Preview preview;

	private record Preview(UUID player, @Nullable PlayerCharacter character, float jaw, boolean mouthOpen) {
	}

	private CharacterResolver() {
	}

	/**
	 * The character this player currently appears as, or null for their own skin. Players who have the mod and picked
	 * a character for themselves appear as that one (unless "Their own picks" is turned off, or we don't have it).
	 */
	public static @Nullable PlayerCharacter characterFor(UUID player) {
		Preview active = preview;
		if (active != null && active.player.equals(player)) {
			return active.character;
		}
		CharacterSettings settings = CharacterSettings.get();
		if (settings.othersPicks() && !isSelf(player)) {
			CharacterSharing.Look look = CharacterSharing.lookOf(player);
			if (look != null) {
				if (look.ownSkin()) {
					return null;
				}
				PlayerCharacter picked = CharacterSharing.characterOf(look);
				if (picked != null) {
					return picked;
				}
				// A character we don't have: show what we picked for them instead.
			}
		}
		return resolve(settings.choiceFor(player), player);
	}

	/** What you picked for yourself (or the default gave you), which is what other players with the mod are told. */
	public static @Nullable PlayerCharacter ownPick(UUID self) {
		return resolve(CharacterSettings.get().choiceFor(self), self);
	}

	private static boolean isSelf(UUID player) {
		LocalPlayer self = Minecraft.getInstance().player;
		return self != null && self.getUUID().equals(player);
	}

	/** What {@code choice} means for {@code player}. {@link Choice#DEFAULT} follows the default setting. */
	public static @Nullable PlayerCharacter resolve(Choice choice, UUID player) {
		if (choice.kind() == Choice.Kind.DEFAULT) {
			choice = CharacterSettings.get().defaultChoice();
		}
		return switch (choice.kind()) {
			case DEFAULT, OWN_SKIN -> null;
			case RANDOM -> CharacterLibrary.randomFor(player);
			case CHARACTER -> CharacterLibrary.get(choice.characterId());
		};
	}

	/** For skin characters: the skin to draw this frame. Null keeps the player's real skin (also used for 3D characters). */
	public static @Nullable PlayerSkin skinFor(UUID player) {
		PlayerCharacter character = characterFor(player);
		if (character == null || character.isModel()) {
			return null;
		}
		Preview active = preview;
		boolean mouthOpen = active != null && active.player.equals(player)
			? active.mouthOpen
			: CharacterSettings.get().talkingMouths() && TalkTracker.isMouthOpen(player);
		return character.skin(mouthOpen);
	}

	/** The jaw opening to show for this player in the picker preview, or null when no preview is being drawn for them. */
	public static @Nullable Float previewJaw(UUID player) {
		Preview active = preview;
		return active != null && active.player.equals(player) ? active.jaw : null;
	}

	/** Runs {@code draw} with {@code player} shown as {@code character} (null = own skin). Render thread only. */
	public static void withPreview(UUID player, @Nullable PlayerCharacter character, float jaw, Runnable draw) {
		Preview previous = preview;
		preview = new Preview(player, character, jaw, jaw > 0.35F);
		try {
			draw.run();
		} finally {
			preview = previous;
		}
	}
}
