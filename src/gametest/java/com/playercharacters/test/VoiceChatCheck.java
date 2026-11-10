package com.playercharacters.test;

import com.playercharacters.voice.TalkTracker;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import java.util.UUID;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Plays audio through the real Simple Voice Chat server and checks that it reaches this mod on the client,
 * for all three ways voice arrives: a nearby entity, a static (group-style) channel, and yourself.
 * Only loaded when Simple Voice Chat is installed.
 */
@SuppressWarnings("UnstableApiUsage")
final class VoiceChatCheck {
	private static final int TIMEOUT_TICKS = 20 * 20;
	/** Fixed UUID for the pig used as a speaking entity: matches {UUID:[I;1,2,3,4]} in the summon command. */
	private static final UUID PIG = new UUID((1L << 32) | 2L, (3L << 32) | 4L);

	private VoiceChatCheck() {
	}

	/**
	 * Voice chat stays disabled until its first-time setup ("onboarding") is done. The test game starts with a
	 * fresh config every run, so mark it as done. Uses reflection because this is not part of the public API.
	 */
	static void finishOnboarding() {
		try {
			Object config = Class.forName("de.maxhenkel.voicechat.VoicechatClient").getField("CLIENT_CONFIG").get(null);
			Object entry = config.getClass().getField("onboardingFinished").get(config);
			Object updated = entry.getClass().getMethod("set", Object.class).invoke(entry, Boolean.TRUE);
			updated.getClass().getMethod("save").invoke(updated);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Couldn't finish voice chat onboarding", e);
		}
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext world, UUID self, Runnable screenshot) {
		context.waitFor(client -> TestVoicePlugin.serverApi != null && TestVoicePlugin.clientConnected, TIMEOUT_TICKS);
		VoicechatServerApi api = TestVoicePlugin.serverApi;
		PlayerCharactersClientGameTest.check(true, "Simple Voice Chat server started and the client connected");

		// 1. Another entity talking nearby (what other players' voices look like to the client).
		// Off to the side, so it stays out of the screenshot.
		world.getServer().runCommand("execute as @p at @s run summon pig ^6 ^ ^ {NoAI:1b,UUID:[I;1,2,3,4]}");
		context.waitTicks(5);
		AudioPlayer pigVoice = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			Entity pig = level.getEntity(PIG);
			AudioChannel channel = api.createEntityAudioChannel(UUID.randomUUID(), api.fromEntity(pig));
			return play(api, channel, 100);
		});
		context.waitFor(client -> TalkTracker.isMouthOpen(PIG), TIMEOUT_TICKS);
		PlayerCharactersClientGameTest.check(true, "voice from a nearby entity reached the mod (EntitySound)");
		pigVoice.stopPlaying();

		// 2. A static channel, which is how group chat voices arrive.
		UUID staticChannel = UUID.randomUUID();
		AudioPlayer groupVoice = world.getServer().computeOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayer(self);
			AudioChannel channel = api.createStaticAudioChannel(staticChannel, api.fromServerLevel(player.level()), api.getConnectionOf(self));
			return play(api, channel, 100);
		});
		context.waitFor(client -> TalkTracker.isMouthOpen(staticChannel), TIMEOUT_TICKS);
		PlayerCharactersClientGameTest.check(true, "voice from a static/group channel reached the mod (StaticSound)");
		groupVoice.stopPlaying();

		// 3. Our own character, speaking through the real voice chat pipeline.
		AudioPlayer ownVoice = world.getServer().computeOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayer(self);
			AudioChannel channel = api.createEntityAudioChannel(self, api.fromEntity(player));
			return play(api, channel, 250);
		});
		context.waitFor(client -> TalkTracker.isMouthOpen(self), TIMEOUT_TICKS);
		PlayerCharactersClientGameTest.check(true, "voice chat audio opened our own character's mouth");
		screenshot.run();
		ownVoice.stopPlaying();
		context.waitFor(client -> !TalkTracker.isMouthOpen(self), TIMEOUT_TICKS);
		PlayerCharactersClientGameTest.check(true, "mouth closed again after the voice stopped");
	}

	/** Plays a steady tone for {@code frames} x 20 ms through a voice chat channel. */
	private static AudioPlayer play(VoicechatServerApi api, AudioChannel channel, int frames) {
		short[] frame = new short[960];
		for (int i = 0; i < frame.length; i++) {
			frame[i] = (short) (8000 * Math.sin(2 * Math.PI * 220 * i / 48000.0));
		}
		int[] remaining = {frames};
		Supplier<short[]> audio = () -> remaining[0]-- > 0 ? frame.clone() : null;
		AudioPlayer player = api.createAudioPlayer(channel, api.createEncoder(), audio);
		player.startPlaying();
		return player;
	}
}
