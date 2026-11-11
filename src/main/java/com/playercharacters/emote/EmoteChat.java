package com.playercharacters.emote;

import com.playercharacters.config.CharacterSettings;
import com.playercharacters.effects.FartEffects;
import com.playercharacters.effects.MiddleFingerEmote;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Shows emotes to other players. This mod only runs on clients, so an emote is sent as a {@code /me} chat message
 * ("* Steve does the Default Dance"), which players without the mod simply read. Clients with the mod recognize the
 * message and play the emote on that player's character. Typing {@code /me waves} yourself works too.
 */
public final class EmoteChat {
	/** Servers kick players who chat too fast: at most one emote message every two seconds. */
	private static final long COOLDOWN_MILLIS = 2000;
	/** How long the server's copy of our own message is ignored, because the emote already plays. */
	private static final long ECHO_MILLIS = 5000;
	/** Servers that turn /me into a plain message: "* Name text". */
	private static final Pattern PLAIN_EMOTE = Pattern.compile("^\\* (\\S+) (.+)$");
	private static long lastSent = -COOLDOWN_MILLIS;
	private static @Nullable Emote lastLocal;
	private static long lastLocalMillis;
	private static int sent;

	private EmoteChat() {
	}

	/** How many emote messages were sent since the game started. */
	public static int sentCount() {
		return sent;
	}

	/** Sends {@code /me <emote>} if sharing is on and someone else is online to see it. */
	static void announce(Emote emote) {
		long now = Util.getMillis();
		lastLocal = emote;
		lastLocalMillis = now;
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (!CharacterSettings.get().shareEmotes() || connection == null || connection.getOnlinePlayers().size() < 2 || now - lastSent < COOLDOWN_MILLIS) {
			return;
		}
		lastSent = now;
		sent++;
		connection.sendCommand("me " + emote.chat());
	}

	/**
	 * A chat message from a player. If it is an emote's text ("waves"), plays that emote on their character.
	 *
	 * @return whether it was an emote message
	 */
	public static boolean onChat(UUID sender, String text) {
		Emote emote = Emotes.byChat(text);
		if (emote == null) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		boolean self = client.player != null && client.player.getUUID().equals(sender);
		if (self && emote == lastLocal && Util.getMillis() - lastLocalMillis < ECHO_MILLIS) {
			return true; // our own message coming back: it is already playing
		}
		switch (emote.kind()) {
			case FART -> FartEffects.start(sender);
			case MIDDLE_FINGER -> MiddleFingerEmote.start(sender);
			case POSE -> {
				Player player = client.level == null ? null : client.level.getPlayerByUUID(sender);
				if (player != null) {
					EmotePlayer.start(player, emote);
				}
			}
		}
		return true;
	}

	/** A message from the server that isn't player chat, for servers that send emotes as "* Name waves". */
	public static void onSystemMessage(String text) {
		Matcher matcher = PLAIN_EMOTE.matcher(text);
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (!matcher.matches() || connection == null || Emotes.byChat(matcher.group(2)) == null) {
			return;
		}
		PlayerInfo info = connection.getPlayerInfo(matcher.group(1));
		if (info != null) {
			onChat(info.getProfile().id(), matcher.group(2));
		}
	}
}
