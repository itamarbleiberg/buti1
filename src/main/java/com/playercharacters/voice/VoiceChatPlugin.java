package com.playercharacters.voice;

import com.playercharacters.PlayerCharactersClient;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Simple Voice Chat plugin, registered through the "voicechat" entrypoint in fabric.mod.json.
 * Simple Voice Chat only loads this class when it is installed, so the mod also works without it.
 */
public final class VoiceChatPlugin implements VoicechatPlugin {
	@Override
	public String getPluginId() {
		return PlayerCharactersClient.MOD_ID;
	}

	@Override
	public void registerEvents(EventRegistration registration) {
		// Other players talking nearby. Simple Voice Chat dispatches by exact event type, so each kind is registered.
		registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class,
			event -> TalkTracker.onAudioFrame(event.getEntityId(), event.getRawAudio()));
		// Group chat: the channel id is the speaking player's UUID.
		registration.registerEvent(ClientReceiveSoundEvent.StaticSound.class,
			event -> TalkTracker.onAudioFrame(event.getId(), event.getRawAudio()));
		// Your own microphone, so your character talks too (visible in third person and in the inventory).
		registration.registerEvent(ClientSoundEvent.class, event -> {
			LocalPlayer player = Minecraft.getInstance().player;
			if (player != null) {
				TalkTracker.onAudioFrame(player.getUUID(), event.getRawAudio());
			}
		});
		registration.registerEvent(ClientVoicechatConnectionEvent.class, event -> {
			if (!event.isConnected()) {
				TalkTracker.clear();
			}
		});
	}
}
