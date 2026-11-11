package com.playercharacters.voice;

import com.playercharacters.PlayerCharactersClient;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Simple Voice Chat plugin, registered through the "voicechat" entrypoint in fabric.mod.json. It only measures how loud
 * each voice is, to move mouths. Simple Voice Chat only loads this class when it is installed, so the mod also works
 * without it.
 */
public final class VoiceChatPlugin implements VoicechatPlugin {
	@Override
	public String getPluginId() {
		return PlayerCharactersClient.MOD_ID;
	}

	@Override
	public void registerEvents(EventRegistration registration) {
		// Other players talking nearby. Simple Voice Chat dispatches by exact event type, so each kind is registered.
		registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class, event -> heard(event.getEntityId(), event.getRawAudio()));
		// Group chat: the channel id is the speaking player's UUID.
		registration.registerEvent(ClientReceiveSoundEvent.StaticSound.class, event -> heard(event.getId(), event.getRawAudio()));
		// Your own microphone, so your character talks too: visible in third person and in the inventory.
		registration.registerEvent(ClientSoundEvent.class, event -> {
			LocalPlayer player = Minecraft.getInstance().player;
			if (player != null) {
				heard(player.getUUID(), event.getRawAudio());
			}
		});
		registration.registerEvent(ClientVoicechatConnectionEvent.class, event -> {
			if (!event.isConnected()) {
				TalkTracker.clear();
			}
		});
	}

	/** One 20 ms frame of someone's voice: moves their mouth. */
	private static void heard(@Nullable UUID speaker, short @Nullable [] audio) {
		TalkTracker.onAudioFrame(speaker, audio);
	}
}
