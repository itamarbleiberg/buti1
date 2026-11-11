package com.playercharacters;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import com.playercharacters.character.CharacterLibrary;
import com.playercharacters.config.CharacterSettings;
import com.playercharacters.effects.CharacterParticles;
import com.playercharacters.effects.CigaretteSmoke;
import com.playercharacters.effects.FartEffects;
import com.playercharacters.effects.FartScreen;
import com.playercharacters.effects.MiddleFingerEmote;
import com.playercharacters.emote.EmoteChat;
import com.playercharacters.emote.EmoteMusic;
import com.playercharacters.emote.EmotePlayer;
import com.playercharacters.gui.CharacterMenuScreen;
import com.playercharacters.gui.EmoteIconRenderer;
import com.playercharacters.gui.EmoteWheelScreen;
import com.playercharacters.share.CharacterSharing;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

public final class PlayerCharactersClient implements ClientModInitializer {
	public static final String MOD_ID = "playercharacters";
	public static final Logger LOGGER = LogUtils.getLogger();

	private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
	/** "fart" as a word of its own in a chat message (also "farts", "farted" and "farting"). */
	private static final Pattern FART_IN_CHAT = Pattern.compile("(?<![\\p{L}\\p{N}_])fart(s|ed|ing)?(?![\\p{L}\\p{N}_])",
		Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	/** "fuck you" or "fuck off", also written "fck you", "f*ck u", "f off" and similar. */
	private static final Pattern FUCK_YOU_IN_CHAT = Pattern.compile(
		"(?<![\\p{L}\\p{N}_])(fuck|fuk|fck|fk|f\\*+c?k?|f)\\s+(you|u|ya|off)(?![\\p{L}\\p{N}_])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

	private static KeyMapping openMenuKey;
	private static KeyMapping toggleMouthsKey;
	private static KeyMapping emoteWheelKey;

	@Override
	public void onInitializeClient() {
		CharacterSettings.get().load();

		// The keys show up under "Player Characters" in Options > Controls > Key Binds and can be changed there.
		openMenuKey = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.playercharacters.open_menu", InputConstants.KEY_K, KEY_CATEGORY));
		toggleMouthsKey = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.playercharacters.toggle_mouths", InputConstants.KEY_U, KEY_CATEGORY));
		emoteWheelKey = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.playercharacters.emote_wheel", InputConstants.KEY_B, KEY_CATEGORY));

		// Textures can only be created once the game window exists.
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			CharacterLibrary.reload();
			EmoteMusic.setUp();
		});
		ClientTickEvents.END_CLIENT_TICK.register(PlayerCharactersClient::onEndTick);
		// The green fart screen goes over the world, under the crosshair and the hotbar.
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, FartScreen.ID, FartScreen::render);
		// The emote wheel's pictures of your character doing each emote.
		SpecialGuiElementRegistry.register(context -> new EmoteIconRenderer(context.vertexConsumers(), context.client().getEntityRenderDispatcher()));

		// Whispers between players with the mod ("[Player Characters] ...") are hidden from chat.
		ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
			CharacterSharing.allowChat(sender == null ? null : sender.id(), signedMessage != null ? signedMessage.signedContent() : message.getString()));
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || CharacterSharing.allowSystemMessage(message.getString()));
		// Typing "fart", "fuck you" or "fuck off" in chat does those emotes; "/me waves" and the like do the others.
		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
			if (sender != null) {
				String text = signedMessage != null ? signedMessage.signedContent() : message.getString();
				if (CharacterSharing.onChat(sender.id(), sender.name(), text, params)) {
					return; // "* Steve is playing as Black Wolf"
				}
				if (EmoteChat.onChat(sender.id(), text)) {
					return; // "/me waves" and the like, sent by the emote wheel
				}
				if (FART_IN_CHAT.matcher(text).find()) {
					FartEffects.start(sender.id());
				}
				if (FUCK_YOU_IN_CHAT.matcher(text).find()) {
					MiddleFingerEmote.start(sender.id());
				}
			}
		});
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				EmoteChat.onSystemMessage(message.getString());
			}
		});

		Thread cleanUp = new Thread(PlayerCharactersClient::removeOldSpeechModel, "Player Characters clean-up");
		cleanUp.setDaemon(true);
		cleanUp.start();
	}

	/**
	 * Versions 3 to 6 listened for "fart" in voice chat with a speech recognizer, unpacked to
	 * {@code .minecraft/playercharacters/speech-model} (about 40 MB). It isn't used anymore, so it is deleted.
	 */
	private static void removeOldSpeechModel() {
		Path folder = FabricLoader.getInstance().getGameDir().resolve(MOD_ID).resolve("speech-model");
		if (!Files.isDirectory(folder.resolve("vosk-model-small-en-us-0.15"))) {
			return;
		}
		try (Stream<Path> files = Files.walk(folder)) {
			for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(file);
			}
			Path parent = folder.getParent();
			try (Stream<Path> rest = Files.list(parent)) {
				if (rest.findAny().isEmpty()) {
					Files.delete(parent);
				}
			}
			LOGGER.info("Removed the speech recognizer of older versions from {}", folder);
		} catch (IOException e) {
			LOGGER.warn("Couldn't remove the old speech recognizer from {}", folder, e);
		}
	}

	private static void onEndTick(Minecraft client) {
		while (openMenuKey.consumeClick()) {
			if (client.screen == null && client.getConnection() != null) {
				client.setScreen(new CharacterMenuScreen(null));
			}
		}

		while (emoteWheelKey.consumeClick()) {
			if (client.screen == null && client.player != null) {
				client.setScreen(new EmoteWheelScreen(emoteWheelKey));
			}
		}

		while (toggleMouthsKey.consumeClick()) {
			CharacterSettings settings = CharacterSettings.get();
			boolean enabled = !settings.talkingMouths();
			settings.setTalkingMouths(enabled);
			if (client.player != null) {
				// true = show above the hotbar instead of in chat
				client.player.displayClientMessage(Component.translatable(
					enabled ? "playercharacters.message.mouths_on" : "playercharacters.message.mouths_off"), true);
			}
		}

		FartEffects.tick(client);
		FartScreen.tick(client);
		MiddleFingerEmote.tick(client);
		EmotePlayer.tick(client);
		CigaretteSmoke.tick(client);
		CharacterParticles.tick(client);
		CharacterSharing.tick(client);
		EmoteMusic.tick();
	}
}
