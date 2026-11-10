package com.playercharacters.test;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;

/** Test-only Simple Voice Chat plugin: gives the test access to the voice server so it can play audio. */
public final class TestVoicePlugin implements VoicechatPlugin {
	static volatile VoicechatServerApi serverApi;
	static volatile boolean clientConnected;

	@Override
	public String getPluginId() {
		return "playercharacters_test";
	}

	@Override
	public void registerEvents(EventRegistration registration) {
		registration.registerEvent(VoicechatServerStartedEvent.class, event -> serverApi = event.getVoicechat());
		registration.registerEvent(ClientVoicechatConnectionEvent.class, event -> clientConnected = event.isConnected());
	}
}
