package com.playercharacters.test;

import com.playercharacters.effects.FartEffects;
import com.playercharacters.speech.SpeechModel;
import com.playercharacters.voice.TalkTracker;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Plays audio through the real Simple Voice Chat server and checks that it reaches this mod on the client,
 * for all three ways voice arrives: a nearby entity, a static (group-style) channel, and yourself. With recorded
 * sentences, also checks that saying "fart" is recognized. Only loaded when Simple Voice Chat is installed.
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

	/**
	 * Speaks recorded sentences as our own player through the real voice chat pipeline (encoded, sent, decoded),
	 * and checks that the fart detection hears "fart" but not a similar word.
	 */
	static void speech(ClientGameTestContext context, TestSingleplayerContext world, Path clips, int fartsBeforeVoice, Runnable screenshot) {
		VoicechatServerApi api = TestVoicePlugin.serverApi;
		// The voices played before this started loading the speech model.
		context.waitFor(client -> SpeechModel.status() == SpeechModel.Status.READY || SpeechModel.status() == SpeechModel.Status.UNAVAILABLE, TIMEOUT_TICKS);
		check(SpeechModel.status() == SpeechModel.Status.READY, "speech recognition started (" + SpeechModel.status() + ", " + SpeechModel.problem() + ")");
		check(context.computeOnClient(client -> FartEffects.startedCount()) == fartsBeforeVoice, "the test tones in voice chat didn't cause a fart");

		// 1. "... fart ...": our character farts.
		int before = context.computeOnClient(client -> FartEffects.startedCount());
		AudioPlayer sentence = say(world, api, clips.resolve("p2_com.wav"));
		context.waitFor(client -> FartEffects.startedCount() > before, TIMEOUT_TICKS);
		check(context.computeOnClient(client -> FartEffects.isFarting(client.player.getUUID())), "saying \"fart\" in voice chat made our character fart");
		context.waitTicks(6);
		screenshot.run();
		context.waitFor(client -> !sentence.isPlaying(), TIMEOUT_TICKS);
		context.waitTicks(40);

		// 2. A sentence with "farm" in it: nobody farts.
		int beforeOther = context.computeOnClient(client -> FartEffects.startedCount());
		AudioPlayer other = say(world, api, clips.resolve("n1_com.wav"));
		context.waitFor(client -> !other.isPlaying(), TIMEOUT_TICKS);
		context.waitTicks(40); // time for the last words to be recognized
		check(context.computeOnClient(client -> FartEffects.startedCount()) == beforeOther, "a sentence with \"farm\" in it didn't cause a fart");
	}

	/** Plays a 48 kHz mono WAV file through a voice chat channel attached to our own player. */
	private static AudioPlayer say(TestSingleplayerContext world, VoicechatServerApi api, Path wav) {
		short[] pcm;
		try (AudioInputStream in = AudioSystem.getAudioInputStream(wav.toFile())) {
			byte[] bytes = in.readAllBytes();
			pcm = new short[bytes.length / 2];
			ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm);
		} catch (Exception e) {
			throw new AssertionError("Couldn't read " + wav, e);
		}
		short[] audio = pcm;
		int[] offset = {0};
		Supplier<short[]> frames = () -> {
			if (offset[0] >= audio.length) {
				return null;
			}
			short[] frame = Arrays.copyOfRange(audio, offset[0], offset[0] + 960); // padded with silence at the end
			offset[0] += 960;
			return frame;
		};
		return world.getServer().computeOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			// Like a real player's voice: the channel has the player's UUID. (Voice chat reports sound from the entity
			// you are as a "static" sound with the channel's UUID.)
			AudioChannel channel = api.createEntityAudioChannel(player.getUUID(), api.fromEntity(player));
			AudioPlayer voice = api.createAudioPlayer(channel, api.createEncoder(), frames);
			voice.startPlaying();
			return voice;
		});
	}

	private static void check(boolean condition, String what) {
		PlayerCharactersClientGameTest.check(condition, what);
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
