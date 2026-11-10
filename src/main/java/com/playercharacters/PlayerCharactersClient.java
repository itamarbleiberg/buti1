package com.playercharacters;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import com.playercharacters.character.CharacterLibrary;
import com.playercharacters.config.CharacterSettings;
import com.playercharacters.gui.CharacterMenuScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

public final class PlayerCharactersClient implements ClientModInitializer {
	public static final String MOD_ID = "playercharacters";
	public static final Logger LOGGER = LogUtils.getLogger();

	private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

	private static KeyMapping openMenuKey;
	private static KeyMapping toggleMouthsKey;

	@Override
	public void onInitializeClient() {
		CharacterSettings.get().load();

		// Both keys show up under "Player Characters" in Options > Controls > Key Binds and can be changed there.
		openMenuKey = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.playercharacters.open_menu", InputConstants.KEY_K, KEY_CATEGORY));
		toggleMouthsKey = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.playercharacters.toggle_mouths", InputConstants.KEY_U, KEY_CATEGORY));

		// Textures can only be created once the game window exists.
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> CharacterLibrary.reload());
		ClientTickEvents.END_CLIENT_TICK.register(PlayerCharactersClient::onEndTick);
	}

	private static void onEndTick(Minecraft client) {
		while (openMenuKey.consumeClick()) {
			if (client.screen == null && client.getConnection() != null) {
				client.setScreen(new CharacterMenuScreen(null));
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
	}
}
