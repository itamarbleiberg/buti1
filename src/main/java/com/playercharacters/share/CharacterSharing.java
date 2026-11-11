package com.playercharacters.share;

import com.playercharacters.CharacterResolver;
import com.playercharacters.character.CharacterLibrary;
import com.playercharacters.character.PlayerCharacter;
import com.playercharacters.config.CharacterSettings;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ChatType;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Shows other players who have the mod as the character they picked for themselves, and tells them yours.
 * <p>
 * The mod only runs on clients, so the only way to reach the others is chat. When you join a server (or pick another
 * character), you say so with {@code /me}: "* Steve is playing as Black Wolf", which players without the mod simply
 * read. Players with the mod who see that answer with a whisper telling you what they are playing as, so you see them
 * right too. Those whispers start with "[Player Characters]" and are hidden from the chat of players with the mod.
 */
public final class CharacterSharing {
	private static final String PLAYING_AS = "is playing as ";
	private static final String OWN_SKIN = "is using their own skin";
	private static final String MARKER = "[Player Characters]";
	/** "is playing as Black Wolf" or "is using their own skin". */
	private static final Pattern ANNOUNCEMENT = Pattern.compile("^(?:is playing as (.+)|is using their own skin)$");
	/** "[Player Characters] Steve is playing as Black Wolf", whispered. */
	private static final Pattern WHISPER = Pattern.compile("\\[Player Characters] (\\S+) (is playing as .+|is using their own skin)$");
	/** Servers that turn /me into a plain message: "* Steve is playing as Black Wolf". */
	private static final Pattern PLAIN_ANNOUNCEMENT = Pattern.compile("^\\* (\\S+) (is playing as .+|is using their own skin)$");
	/** Wait this long after joining before saying anything, so the player list has arrived. */
	private static final int JOIN_DELAY_TICKS = 60;
	/** After picking another character, wait this long in case you change your mind again. */
	private static final long SETTLE_MILLIS = 2000;
	/** Servers kick players who chat too fast. */
	private static final long SEND_GAP_MILLIS = 1500;
	private static final Map<UUID, Look> LOOKS = new ConcurrentHashMap<>();
	private static final Set<UUID> GREETED = new HashSet<>();
	private static final Deque<String> OUTBOX = new ArrayDeque<>();
	private static @Nullable ClientPacketListener connection;
	private static int ticksConnected;
	/** What you last told everyone: a character name, "" for your own skin, or null if nothing yet on this server. */
	private static @Nullable String announced;
	private static @Nullable String pending;
	private static long pendingSince;
	private static long lastSent = -SEND_GAP_MILLIS;
	private static int sent;
	private static int received;

	private CharacterSharing() {
	}

	/**
	 * What another player picked for themselves.
	 *
	 * @param character the character's name, or null for their own skin
	 */
	public record Look(@Nullable String character) {
		public boolean ownSkin() {
			return this.character == null;
		}
	}

	/** What this player picked for themselves with the mod, if they told us. */
	public static @Nullable Look lookOf(UUID player) {
		return LOOKS.get(player);
	}

	/** The character this player picked for themselves and we have too. Null if they didn't say, or picked their own skin. */
	public static @Nullable PlayerCharacter characterOf(Look look) {
		return look.character() == null ? null : CharacterLibrary.byName(look.character());
	}

	/** How many sharing messages were sent and understood since the game started. */
	public static int sentCount() {
		return sent;
	}

	public static int receivedCount() {
		return received;
	}

	/** Forgets everything, for example when leaving a server. */
	public static void clear() {
		LOOKS.clear();
		GREETED.clear();
		OUTBOX.clear();
		announced = null;
		pending = null;
	}

	// ------------------------------------------------------------------ receiving

	/**
	 * A chat message from a player. If it is one of ours, remembers what they are playing as.
	 *
	 * @return true if it was a sharing message, so it isn't taken for anything else (like the word "fart")
	 */
	public static boolean onChat(UUID sender, String senderName, String text, ChatType.@Nullable Bound params) {
		if (params != null && !params.chatType().is(ChatType.EMOTE_COMMAND)) {
			return false; // only /me counts, not someone typing the same words
		}
		Matcher matcher = ANNOUNCEMENT.matcher(text.strip());
		if (!matcher.matches()) {
			return false;
		}
		if (!isSelf(sender)) {
			heard(sender, matcher.group(1) == null ? null : matcher.group(1).strip());
			greet(sender, senderName);
		}
		return true;
	}

	/** A whisper (or any chat message) that might be one of ours. Returns false to hide it from chat. */
	public static boolean allowChat(@Nullable UUID sender, String text) {
		if (!text.contains(MARKER)) {
			return true;
		}
		Matcher matcher = WHISPER.matcher(text.strip());
		if (matcher.find() && sender != null && !isSelf(sender)) {
			heard(sender, announcedCharacter(matcher.group(2)));
		}
		return false;
	}

	/** A message from the server that isn't player chat: some servers send /me and whispers that way. Returns false to hide it. */
	public static boolean allowSystemMessage(String text) {
		ClientPacketListener listener = Minecraft.getInstance().getConnection();
		if (text.contains(MARKER)) {
			Matcher matcher = WHISPER.matcher(text.strip());
			if (matcher.find() && listener != null) {
				PlayerInfo info = listener.getPlayerInfo(matcher.group(1));
				if (info != null && !isSelf(info.getProfile().id())) {
					heard(info.getProfile().id(), announcedCharacter(matcher.group(2)));
				}
			}
			return false;
		}
		Matcher plain = PLAIN_ANNOUNCEMENT.matcher(text.strip());
		if (plain.matches() && listener != null) {
			PlayerInfo info = listener.getPlayerInfo(plain.group(1));
			if (info != null && !isSelf(info.getProfile().id())) {
				heard(info.getProfile().id(), announcedCharacter(plain.group(2)));
				greet(info.getProfile().id(), plain.group(1));
			}
		}
		return true;
	}

	private static @Nullable String announcedCharacter(String words) {
		return words.startsWith(PLAYING_AS) ? words.substring(PLAYING_AS.length()).strip() : null;
	}

	private static void heard(UUID player, @Nullable String character) {
		LOOKS.put(player, new Look(character == null || character.isEmpty() ? null : character));
		received++;
	}

	/** Someone with the mod announced themselves: whisper back what we are playing as, once per visit of theirs. */
	private static void greet(UUID player, String name) {
		if (!CharacterSettings.get().shareEmotes() || !GREETED.add(player)) {
			return;
		}
		OUTBOX.add("msg " + name + " " + MARKER + " " + ownName() + " " + words(currentLook()));
	}

	private static boolean isSelf(UUID player) {
		LocalPlayer self = Minecraft.getInstance().player;
		return self != null && self.getUUID().equals(player);
	}

	private static String ownName() {
		return Minecraft.getInstance().getUser().getName();
	}

	// ------------------------------------------------------------------ sending

	/** What we look like to ourselves: a character name, or "" for our own skin. */
	static String currentLook() {
		LocalPlayer self = Minecraft.getInstance().player;
		if (self == null) {
			return "";
		}
		PlayerCharacter character = CharacterResolver.ownPick(self.getUUID());
		return character == null ? "" : character.displayName();
	}

	static String words(String look) {
		return look.isEmpty() ? OWN_SKIN : PLAYING_AS + look;
	}

	/** Called every client tick: says what we are playing as after joining or changing, and sends queued whispers. */
	public static void tick(Minecraft client) {
		ClientPacketListener listener = client.getConnection();
		if (listener != connection) {
			connection = listener;
			ticksConnected = 0;
			clear();
		}
		if (listener == null || client.player == null) {
			return;
		}
		ticksConnected++;
		if (ticksConnected % 20 == 0) {
			// People who left: forget them, so they are greeted again when they come back.
			LOOKS.keySet().removeIf(id -> listener.getPlayerInfo(id) == null);
			GREETED.removeIf(id -> listener.getPlayerInfo(id) == null);
		}
		CharacterSettings settings = CharacterSettings.get();
		if (!settings.shareEmotes()) {
			announced = null;
			OUTBOX.clear();
			return;
		}
		boolean others = listener.getOnlinePlayers().size() >= 2;
		String look = currentLook();
		long now = Util.getMillis();
		if (!look.equals(pending)) {
			pending = look;
			pendingSince = now;
		}
		boolean settled = now - pendingSince >= SETTLE_MILLIS && client.screen == null;
		if (others && ticksConnected >= JOIN_DELAY_TICKS && settled && !look.equals(announced)) {
			announced = look;
			OUTBOX.add("me " + words(look));
		}
		if (!OUTBOX.isEmpty() && now - lastSent >= SEND_GAP_MILLIS) {
			lastSent = now;
			sent++;
			listener.sendCommand(OUTBOX.poll());
		}
	}

	/** The message that announces a look, as typed after "/me" (for tests). */
	public static String announcement(@Nullable String character) {
		return words(character == null ? "" : character);
	}
}
