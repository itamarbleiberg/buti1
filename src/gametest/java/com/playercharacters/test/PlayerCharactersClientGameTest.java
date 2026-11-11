package com.playercharacters.test;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import com.playercharacters.CharacterResolver;
import com.playercharacters.character.CharacterLibrary;
import com.playercharacters.character.CharacterModel;
import com.playercharacters.character.PlayerCharacter;
import com.playercharacters.config.CharacterSettings;
import com.playercharacters.config.Choice;
import com.playercharacters.effects.CigaretteSmoke;
import com.playercharacters.emote.Emote;
import com.playercharacters.emote.EmoteChat;
import com.playercharacters.emote.EmotePlayer;
import com.playercharacters.emote.EmotePose;
import com.playercharacters.emote.Emotes;
import com.playercharacters.gui.EmoteWheelScreen;
import com.playercharacters.effects.FartEffects;
import com.playercharacters.effects.FartScreen;
import com.playercharacters.effects.MiddleFingerEmote;
import com.playercharacters.gui.CharacterMenuScreen;
import com.playercharacters.gui.CharacterPickerScreen;
import com.playercharacters.render.CharacterRenderState;
import com.playercharacters.render.FirstPersonHands;
import com.playercharacters.voice.TalkTracker;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Starts the real game, makes a world, and checks the mod end to end: the eight 3D example characters and a skin
 * character on another player (armor hidden, held item and name tag kept), talking jaws and mouths, farts and the
 * middle finger (typed in chat, and spoken through Simple Voice Chat when it is installed), both key binds and the
 * menus. Screenshots end up in run/screenshots.
 * <p>
 * Optional system properties: {@code playercharacters.test.speechClips}, a folder with fart_sentence.wav,
 * farm_sentence.wav, fuck_you_sentence.wav, fuck_off_sentence.wav, back_off_sentence.wav and thank_you_sentence.wav
 * (48 kHz mono);
 * {@code playercharacters.test.v2jar}, {@code playercharacters.test.v3jar} and {@code playercharacters.test.v4jar}, older jars of the mod, to check that
 * characters folders made by them are brought up to date.
 */
@SuppressWarnings("UnstableApiUsage")
public class PlayerCharactersClientGameTest implements FabricClientGameTest {
	private static final UUID FRIEND = UUID.fromString("5f1a6c3e-1111-4222-8333-00000000f00d");
	private static final List<String> MODELS = List.of("Alien", "Robot", "Frog", "Burger", "Penguin", "SpongeBob", "Skinny Alien", "Blue");

	@Override
	public void runTest(ClientGameTestContext context) {
		// Start from default settings, even if an earlier test run saved some.
		context.runOnClient(client -> {
			CharacterSettings settings = CharacterSettings.get();
			settings.setTalkingMouths(true);
			settings.setFartDetection(true);
			settings.setMiddleFinger(true);
			settings.setDefaultChoice(Choice.OWN_SKIN);
			settings.setChoice(FRIEND, "Friend", Choice.DEFAULT);
			settings.setChoice(client.getUser().getProfileId(), client.getUser().getName(), Choice.DEFAULT);
		});
		if (FabricLoader.getInstance().isModLoaded("voicechat")) {
			context.runOnClient(client -> VoiceChatCheck.finishOnboarding());
		}

		checkExamples(context);
		String v2jar = System.getProperty("playercharacters.test.v2jar");
		if (v2jar != null) {
			checkUpgradeFromVersion2(context, Path.of(v2jar));
		}
		String v3jar = System.getProperty("playercharacters.test.v3jar");
		if (v3jar != null) {
			checkUpgradeFromVersion3(context, Path.of(v3jar));
		}
		String v4jar = System.getProperty("playercharacters.test.v4jar");
		if (v4jar != null) {
			checkUpgradeFromVersion4(context, Path.of(v4jar));
		}
		String v5jar = System.getProperty("playercharacters.test.v5jar");
		if (v5jar != null) {
			checkUpgradeFromVersion5(context, Path.of(v5jar));
		}
		addSkinCharacter(context);

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("weather clear");
			world.getClientWorld().waitForChunksRender();
			context.runOnClient(client -> client.options.fov().set(40));

			// Another player in diamond armor holding a sword, standing in front of us.
			context.runOnClient(PlayerCharactersClientGameTest::spawnFriend);
			context.waitTicks(10);
			check(context.computeOnClient(client -> CharacterResolver.characterFor(FRIEND)) == null, "friend keeps own skin by default");
			context.takeScreenshot("01-friend-normal-with-armor");

			// A skin character: armor hidden, sword still there.
			setChoice(context, Choice.character("Tester"));
			checkSkin(context, FRIEND, "/idle");
			context.takeScreenshot("02-friend-skin-character-no-armor");

			// Every 3D character, quiet and talking.
			int shot = 3;
			for (String id : MODELS) {
				setChoice(context, Choice.character(id));
				checkRenderState(context, id, false);
				context.takeScreenshot(String.format("%02d-friend-%s-quiet", shot++, id.toLowerCase()));
				try (Talking ignored = Talking.start(FRIEND)) {
					context.waitTicks(4);
					checkRenderState(context, id, true);
					context.takeScreenshot(String.format("%02d-friend-%s-talking", shot++, id.toLowerCase()));
				}
				context.waitTicks(10);
			}

			// Far away, simpler copies are drawn. Zoomed in to see that they still look right.
			shot = 24;
			for (String id : List.of("Alien", "SpongeBob", "Robot")) {
				setChoice(context, Choice.character(id));
				for (double distance : new double[] {18.0, 34.0}) {
					context.runOnClient(client -> {
						Entity friend = client.level.getPlayerByUUID(FRIEND);
						Vec3 forward = Vec3.directionFromRotation(0.0F, client.player.getYRot());
						friend.setPos(client.player.getX() + forward.x * distance, client.player.getY(), client.player.getZ() + forward.z * distance);
						client.options.fov().set((int) Math.round(40 * 3.2 / distance));
					});
					context.waitTicks(5);
					context.takeScreenshot(String.format("%02d-friend-%s-far-%d", shot++, id.toLowerCase(), (int) distance));
				}
			}
			context.runOnClient(client -> {
				Entity friend = client.level.getPlayerByUUID(FRIEND);
				Vec3 forward = Vec3.directionFromRotation(0.0F, client.player.getYRot());
				friend.setPos(client.player.getX() + forward.x * 3.2, client.player.getY(), client.player.getZ() + forward.z * 3.2);
				client.options.fov().set(40);
			});
			context.waitTicks(5);

			// 3D characters blink every few seconds.
			setChoice(context, Choice.character("Frog"));
			context.waitFor(client -> friendRenderState(client).playercharacters$blink(), 200);
			check(true, "the frog blinks");
			context.takeScreenshot("18b-friend-frog-blinking");
			context.waitTicks(3);

			// Penguins waddle: the whole penguin rocks from side to side while it walks.
			setChoice(context, Choice.character("Penguin"));
			float[] rock = context.computeOnClient(client -> {
				CharacterRenderState state = friendRenderState(client);
				net.minecraft.client.renderer.entity.state.AvatarRenderState avatar = (net.minecraft.client.renderer.entity.state.AvatarRenderState) state;
				avatar.walkAnimationSpeed = 1.0F;
				avatar.walkAnimationPos = (float) (Math.PI / 2 / 0.6662);
				net.minecraft.client.model.player.PlayerModel model = ((net.minecraft.client.renderer.entity.player.AvatarRenderer<?>) client.getEntityRenderDispatcher()
					.getRenderer(client.level.getPlayerByUUID(FRIEND))).getModel();
				model.setupAnim(avatar);
				return new float[] {model.body.zRot, model.leftLeg.zRot};
			});
			float waddle = CharacterLibrary.get("Penguin").model().extras().waddleRadians();
			check(Math.abs(rock[0] - waddle) < 0.01 && waddle > 0.1F && Math.abs(rock[1] - rock[0]) < 0.2,
				"the penguin waddles (body tilted " + Math.toDegrees(rock[0]) + " degrees)");

			// Closer, from the side, looking a little down: shows the held sword and the open jaw.
			setChoice(context, Choice.character("Alien"));
			try (Talking ignored = Talking.start(FRIEND)) {
				context.waitTicks(4);
				context.runOnClient(client -> {
					client.options.fov().set(30);
					client.player.setXRot(9.0F);
					turnFriend(client, 50.0F);
				});
				context.waitTicks(2);
				context.takeScreenshot("19-friend-alien-talking-side");
				context.runOnClient(client -> {
					client.options.fov().set(40);
					client.player.setXRot(0.0F);
					turnFriend(client, 0.0F);
				});

				// "Toggle talking mouths" key (U).
				context.getInput().pressKey(InputConstants.KEY_U);
				context.waitTicks(10);
				check(!CharacterSettings.get().talkingMouths(), "U turned talking mouths off");
				checkRenderState(context, "Alien", false);
				context.takeScreenshot("20-mouths-off-while-talking");
				context.getInput().pressKey(InputConstants.KEY_U);
				context.waitTicks(3);
				check(CharacterSettings.get().talkingMouths(), "U turned talking mouths back on");

				// Skin characters still open their mouth too.
				setChoice(context, Choice.character("Tester"));
				checkSkin(context, FRIEND, "/talking");
				context.takeScreenshot("21-friend-skin-character-talking");
			}
			context.waitTicks(10);
			checkSkin(context, FRIEND, "/idle");

			// Farts, seen from the side: the character bends over and a cloud comes out of its butt.
			context.runOnClient(client -> {
				turnFriend(client, 90.0F);
				client.options.hideGui = true;
				client.player.setXRot(12.0F);
			});
			shot = 30;
			for (String id : List.of("Burger", "Robot", "Frog", "Alien", "Penguin", "SpongeBob", "Skinny Alien", "Blue", "Tester")) {
				setChoice(context, Choice.character(id));
				context.runOnClient(client -> FartEffects.start(FRIEND));
				context.waitTicks(7);
				float bend = fartBend(context);
				check(bend > 0.8F, id + " bends over while farting (" + String.format("%.2f", bend) + ")");
				context.takeScreenshot(String.format("%02d-friend-%s-fart", shot++, id.toLowerCase()));
				context.waitTicks(25);
				check(fartBend(context) == 0.0F && !context.computeOnClient(client -> FartEffects.isFarting(FRIEND)), id + " stands up straight again");
			}
			// The middle finger, at us: the arm goes up, the held sword goes away, and the face gets angry.
			context.runOnClient(client -> turnFriend(client, 0.0F));
			shot = 40;
			for (String id : List.of("Burger", "Robot", "Frog", "Alien", "Penguin", "SpongeBob", "Skinny Alien", "Blue", "Tester")) {
				setChoice(context, Choice.character(id));
				context.runOnClient(client -> MiddleFingerEmote.start(FRIEND));
				context.waitTicks(8);
				boolean[] seen = context.computeOnClient(client -> {
					CharacterRenderState state = friendRenderState(client);
					net.minecraft.client.renderer.entity.state.AvatarRenderState avatar = (net.minecraft.client.renderer.entity.state.AvatarRenderState) state;
					return new boolean[] {state.playercharacters$middleFinger() > 0.95F, state.playercharacters$mad(), avatar.rightHandItemState.isEmpty()};
				});
				check(seen[0] && seen[1] && seen[2], id + " shows the middle finger, looks mad and puts the sword away");
				context.takeScreenshot(String.format("%02d-friend-%s-middle-finger", shot++, id.toLowerCase().replace(' ', '-')));
				context.waitTicks(56);
				check(!context.computeOnClient(client -> MiddleFingerEmote.isActive(FRIEND)), id + " calms down again");
			}
			// The penguin smokes: a thin line of smoke rises from its cigarette.
			setChoice(context, Choice.character("Penguin"));
			int puffs = context.computeOnClient(client -> CigaretteSmoke.puffCount());
			context.runOnClient(client -> {
				client.options.fov().set(20);
				client.player.setXRot(6.0F);
			});
			context.waitTicks(40);
			check(context.computeOnClient(client -> CigaretteSmoke.puffCount()) > puffs + 5, "smoke rises from the penguin's cigarette");
			context.takeScreenshot("49-friend-penguin-smoking");
			context.runOnClient(client -> client.options.fov().set(40));

			// Emotes on the friend: 3D characters and a skin, with music for the dances.
			context.runOnClient(client -> client.player.setXRot(8.0F));
			shot = 70;
			int notesBefore = context.computeOnClient(client -> EmotePlayer.notesPlayed());
			String[][] emoteShots = {{"Alien", "default_dance"}, {"Blue", "six_seven"}, {"SpongeBob", "floss"}, {"Robot", "robot"}, {"Penguin", "dab"},
				{"Frog", "griddy"}, {"Burger", "jumping_jacks"}, {"Skinny Alien", "zombie"}, {"Tester", "wave"}, {"Alien", "push_ups"}, {"SpongeBob", "sit"},
				{"Blue", "heart"}, {"Tester", "air_guitar"}, {"Robot", "spin"}};
			for (String[] emoteShot : emoteShots) {
				setChoice(context, Choice.character(emoteShot[0]));
				Emote emote = Emotes.byId(emoteShot[1]);
				context.runOnClient(client -> EmotePlayer.start(client.level.getPlayerByUUID(FRIEND), emote));
				context.waitTicks(emote == Emotes.SPIN ? 16 : 14);
				float weight = context.computeOnClient(client -> {
					EmotePose pose = friendRenderState(client).playercharacters$emote();
					return pose == null ? 0.0F : pose.weight;
				});
				check(context.computeOnClient(client -> EmotePlayer.playing(FRIEND)) == emote && weight > 0.95F,
					emoteShot[0] + " does the " + emoteShot[1] + " emote (" + String.format("%.2f", weight) + ")");
				context.takeScreenshot(String.format("%02d-friend-%s-%s", shot++, emoteShot[0].toLowerCase().replace(' ', '-'), emoteShot[1]));
				context.runOnClient(client -> EmotePlayer.stop(FRIEND));
				context.waitTicks(8);
				check(context.computeOnClient(client -> EmotePlayer.playing(FRIEND)) == null, emoteShot[0] + " stops the emote");
			}
			int notes = context.computeOnClient(client -> EmotePlayer.notesPlayed()) - notesBefore;
			check(notes > 15, "the dances play their music (" + notes + " notes)");
			check(Emotes.all().size() >= 30, "there are " + Emotes.all().size() + " emotes");
			// A "/me waves" from another player makes their character wave.
			context.runOnClient(client -> EmoteChat.onChat(FRIEND, "waves"));
			context.waitTicks(2);
			check(context.computeOnClient(client -> EmotePlayer.playing(FRIEND)) == Emotes.WAVE, "\"/me waves\" from another player makes them wave");
			context.waitTicks(55);
			context.runOnClient(client -> client.player.setXRot(0.0F));
			context.runOnClient(client -> {
				client.options.hideGui = false;
				client.player.setXRot(0.0F);
			});
			setChoice(context, Choice.character("Alien"));
			context.waitTicks(5);
			checkRenderState(context, "Alien", false);

			// Both keys are listed in Options > Controls > Key Binds, where they can be changed.
			check(context.computeOnClient(client -> Arrays.stream(client.options.keyMappings)
				.filter(key -> key.getName().startsWith("key.playercharacters.")).count()) == 3, "all three keys are in the Key Binds menu");

			// "Open character menu" key (K).
			context.getInput().pressKey(InputConstants.KEY_K);
			context.waitForScreen(CharacterMenuScreen.class);
			check(context.computeOnClient(client -> client.screen.children().stream()
				.anyMatch(child -> child instanceof CycleButton<?> button && button.getMessage().getString().startsWith("Farts: ON"))),
				"the menu has the Farts button");
			context.takeScreenshot("50-menu");

			// Click our own row's button, pick the Burger in the picker, press Choose.
			clickWidget(context, client -> {
				ContainerObjectSelectionList<?> list = find(client.screen.children(), ContainerObjectSelectionList.class);
				return (AbstractWidget) list.children().getFirst().children().getFirst();
			});
			context.waitForScreen(CharacterPickerScreen.class);
			clickWidget(context, client -> {
				ObjectSelectionList<?> list = find(client.screen.children(), ObjectSelectionList.class);
				for (ObjectSelectionList.Entry<?> entry : list.children()) {
					if (entry.getNarration().getString().contains("Burger")) {
						return new Area(entry.getX(), entry.getY(), entry.getWidth(), entry.getHeight());
					}
				}
				throw new AssertionError("Burger is not in the picker");
			});
			context.waitTicks(3);
			context.takeScreenshot("51-picker-burger-preview");
			context.clickScreenButton("playercharacters.picker.choose");
			context.waitForScreen(CharacterMenuScreen.class);
			UUID self = context.computeOnClient(client -> client.player.getUUID());
			check(context.computeOnClient(client -> CharacterSettings.get().choiceFor(self)).equals(Choice.character("Burger")), "picker saved Burger for us");
			context.clickScreenButton("playercharacters.menu.reload");
			context.waitTicks(2);
			check(context.computeOnClient(client -> CharacterLibrary.all().size()) == 9, "reload keeps all nine characters");
			context.takeScreenshot("52-menu-after-choosing");
			context.setScreen(() -> null);

			// Ourselves in third person. The friend would block the camera, so remove them first.
			context.runOnClient(client -> {
				client.level.removeEntity(client.level.getPlayerByUUID(FRIEND).getId(), Entity.RemovalReason.DISCARDED);
				client.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TORCH));
				client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			});
			try (Talking ignored = Talking.start(self)) {
				context.waitTicks(4);
				context.takeScreenshot("53-self-burger-talking");
			}

			// Typing "fart" in chat makes you fart. From behind, you see it come out.
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(5);
			int before = context.computeOnClient(client -> FartEffects.startedCount());
			context.runOnClient(client -> client.player.connection.sendChat("I really need to fart"));
			context.waitFor(client -> FartEffects.startedCount() > before, 100);
			check(context.computeOnClient(client -> FartEffects.isFarting(self)), "typing \"fart\" in chat made our character fart");
			context.waitTicks(7);
			context.takeScreenshot("54-self-fart-from-chat");
			context.waitTicks(30);

			// Typing "fuck you" does the middle finger. From the front, you see it.
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			int fingersBefore = context.computeOnClient(client -> MiddleFingerEmote.startedCount());
			context.runOnClient(client -> client.player.connection.sendChat("well fuck you then"));
			context.waitFor(client -> MiddleFingerEmote.startedCount() > fingersBefore, 100);
			check(context.computeOnClient(client -> MiddleFingerEmote.isActive(self)), "typing \"fuck you\" in chat made our character show the middle finger");
			context.waitTicks(8);
			context.takeScreenshot("55-self-middle-finger-from-chat");
			context.waitTicks(60);
			int fingersBeforeOff = context.computeOnClient(client -> MiddleFingerEmote.startedCount());
			context.runOnClient(client -> client.player.connection.sendChat("oh just f off"));
			context.waitFor(client -> MiddleFingerEmote.startedCount() > fingersBeforeOff, 100);
			check(true, "typing \"f off\" in chat made our character show the middle finger");
			context.waitTicks(68);
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));

			// With farts turned off in the menu, nothing happens.
			context.runOnClient(client -> {
				CharacterSettings.get().setFartDetection(false);
				CharacterSettings.get().setMiddleFinger(false);
			});
			int whileOff = context.computeOnClient(client -> FartEffects.startedCount());
			context.runOnClient(client -> client.player.connection.sendChat("fart"));
			context.runOnClient(client -> client.player.connection.sendChat("fuck you"));
			context.waitTicks(40);
			check(context.computeOnClient(client -> FartEffects.startedCount()) == whileOff, "no fart while farts are turned off");
			check(!context.computeOnClient(client -> MiddleFingerEmote.isActive(self)), "no middle finger while it is turned off");
			context.runOnClient(client -> {
				CharacterSettings.get().setFartDetection(true);
				CharacterSettings.get().setMiddleFinger(true);
			});
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));

			// Real audio through Simple Voice Chat, when it is installed in the test game.
			if (FabricLoader.getInstance().isModLoaded("voicechat")) {
				context.waitTicks(10);
				int fartsBeforeVoice = context.computeOnClient(client -> FartEffects.startedCount());
				VoiceChatCheck.run(context, world, self, () -> context.takeScreenshot("56-voice-chat-self-talking"));
				String clips = System.getProperty("playercharacters.test.speechClips");
				if (clips != null) {
					context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
					VoiceChatCheck.speech(context, world, Path.of(clips), fartsBeforeVoice, () -> context.takeScreenshot("57-self-fart-from-voice-chat"),
						() -> context.takeScreenshot("58-self-middle-finger-from-voice-chat"));
				} else {
					System.out.println("[playercharacters-test] No speech clips given, skipping the spoken \"fart\" check");
				}
			} else {
				System.out.println("[playercharacters-test] Simple Voice Chat not installed, skipping the voice chat check");
			}
			// First person: a 3D character sees its own arm, and its own emotes.
			context.runOnClient(client -> {
				client.options.setCameraType(CameraType.FIRST_PERSON);
				client.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				client.player.setXRot(15.0F);
			});
			shot = 60;
			for (String id : List.of("Alien", "Blue", "Penguin", "Tester")) {
				context.runOnClient(client -> CharacterSettings.get().setChoice(self, client.getUser().getName(), Choice.character(id)));
				context.waitTicks(3);
				int arms = context.computeOnClient(client -> FirstPersonHands.armsDrawn());
				context.waitTicks(3);
				boolean model = !id.equals("Tester");
				check((context.computeOnClient(client -> FirstPersonHands.armsDrawn()) > arms) == model,
					model ? "in first person, " + id + "'s own arm is drawn" : "in first person, a skin character keeps Minecraft's arm");
				context.takeScreenshot(String.format("%02d-first-person-%s-arm", shot++, id.toLowerCase()));
				int fingers = context.computeOnClient(client -> FirstPersonHands.fingersDrawn());
				context.runOnClient(client -> MiddleFingerEmote.start(self));
				context.waitTicks(8);
				check(context.computeOnClient(client -> FirstPersonHands.fingersDrawn()) > fingers, "in first person, " + id + " sees its own middle finger");
				context.takeScreenshot(String.format("%02d-first-person-%s-middle-finger", shot++, id.toLowerCase()));
				context.waitTicks(56);
			}
			int frames = context.computeOnClient(client -> FartScreen.framesDrawn());
			context.runOnClient(client -> FartEffects.start(self));
			context.waitTicks(10);
			check(context.computeOnClient(client -> FartScreen.isShowing()) && context.computeOnClient(client -> FartScreen.framesDrawn()) > frames,
				"farting in first person turns the screen green");
			context.takeScreenshot(String.format("%02d-first-person-fart-screen", shot++));
			context.waitTicks(60);
			check(!context.computeOnClient(client -> FartScreen.isShowing()), "the green screen goes away again");

			// The emote wheel (B): scroll to the next emote and click it. In first person, the camera turns to watch it.
			context.runOnClient(client -> CharacterSettings.get().setChoice(self, client.getUser().getName(), Choice.character("Blue")));
			context.getInput().pressKey(InputConstants.KEY_B);
			context.waitForScreen(EmoteWheelScreen.class);
			double[] middle = context.computeOnClient(client -> new double[] {client.getWindow().getScreenWidth() / 2.0, client.getWindow().getScreenHeight() / 2.0});
			context.getInput().setCursorPos(middle[0], middle[1]);
			context.waitTicks(5);
			context.takeScreenshot("80-emote-wheel");
			Emote firstChoice = context.computeOnClient(client -> ((EmoteWheelScreen) client.screen).selected());
			context.getInput().scroll(-1);
			context.waitTicks(10);
			Emote chosen = context.computeOnClient(client -> ((EmoteWheelScreen) client.screen).selected());
			check(chosen != firstChoice && Emotes.all().indexOf(chosen) == Emotes.all().indexOf(firstChoice) + 1,
				"scrolling chose the next emote: " + chosen.id());
			context.takeScreenshot("81-emote-wheel-scrolled");
			for (int i = 0; i < 9; i++) {
				context.getInput().scroll(-1); // past the end of the page
			}
			context.waitTicks(5);
			check(context.computeOnClient(client -> Emotes.all().indexOf(((EmoteWheelScreen) client.screen).selected())) == Emotes.all().indexOf(chosen) + 9,
				"scrolling on goes to the next page");
			context.takeScreenshot("82-emote-wheel-page-2");
			for (int i = 0; i < 9; i++) {
				context.getInput().scroll(1);
			}
			context.waitTicks(3);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitFor(client -> client.screen == null, 40);
			context.waitTicks(16);
			check(context.computeOnClient(client -> EmotePlayer.playing(self)) == chosen
				&& context.computeOnClient(client -> client.options.getCameraType()) == CameraType.THIRD_PERSON_FRONT,
				"clicking played " + chosen.id() + ", watched from the front");
			context.takeScreenshot("83-self-emote-from-wheel");
			context.runOnClient(client -> EmotePlayer.stop(self));
			context.waitTicks(10);
			check(context.computeOnClient(client -> client.options.getCameraType()) == CameraType.FIRST_PERSON, "back to first person after the emote");
			// Typing "/me does the Robot" goes through the server and comes back as an emote message.
			context.runOnClient(client -> client.player.connection.sendCommand("me does the Robot"));
			context.waitFor(client -> EmotePlayer.playing(self) == Emotes.ROBOT, 100);
			check(true, "\"/me does the Robot\" makes our character do the Robot");
			context.runOnClient(client -> EmotePlayer.stop(self));
			context.waitTicks(10);
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			context.runOnClient(client -> CharacterSettings.get().setChoice(self, client.getUser().getName(), Choice.character("Tester")));

			Path config = FabricLoader.getInstance().getConfigDir().resolve("playercharacters.json");
			String saved = readString(config);
			check(saved.contains("\"character:Tester\"") && saved.contains(FRIEND.toString()) && saved.contains("\"fartDetection\": true")
				&& saved.contains("\"middleFinger\": true"),
				"choices saved to playercharacters.json:\n" + saved);
		}
	}

	/** The examples: eight 3D characters, put in the characters folder by the mod. */
	private static void checkExamples(ClientGameTestContext context) {
		List<String> ids = context.computeOnClient(client -> CharacterLibrary.all().stream().map(PlayerCharacter::id).toList());
		// Sorted by name: Blue is called "67" now.
		check(ids.equals(List.of("Blue", "Alien", "Burger", "Frog", "Penguin", "Robot", "Skinny Alien", "SpongeBob")), "example characters loaded, got " + ids);
		check(CharacterLibrary.get("Blue").displayName().equals("67"), "Blue is called 67, got " + CharacterLibrary.get("Blue").displayName());
		check(CharacterLibrary.get("Blue").model().jawOpenRadians() < 0.0F, "the top of the 7 lifts up when 67 talks");
		check(CharacterLibrary.get("Penguin").model().extras().smoke() != null, "the penguin has a cigarette to smoke");
		for (String id : MODELS) {
			PlayerCharacter character = CharacterLibrary.get(id);
			check(character.isModel() && character.model().quadCount() > 5000 && character.icon() != null,
				id + " is a 3D model with an icon (" + character.model().quadCount() + " polygons)");
			check(character.model().butt().y() > 0.2F && character.model().butt().z() < 0.0F,
				id + "'s butt is behind it, above the ground: " + character.model().butt());
			CharacterModel.Extras extras = character.model().extras();
			check(!extras.madParts().isEmpty() && extras.middleFinger() != null && extras.middleFinger().vertexCount() > 0,
				id + " has an angry face " + extras.madParts().keySet() + " and a middle finger");
			check(!extras.blinkParts().isEmpty() && extras.bounce() > 0.0F, id + " blinks " + extras.blinkParts().keySet() + " and bobs as it walks");
			int full = character.model().quadCount();
			int near = character.model().forDistance(15.0 * 15.0).quadCount();
			int far = character.model().forDistance(40.0 * 40.0).quadCount();
			check(near < full * 0.75 && far < near * 0.8 && far > 300, id + " has simpler copies for far away: " + full + ", " + near + ", " + far + " polygons");
		}
		check(CharacterLibrary.get("Penguin").model().extras().waddleRadians() > 0.1F, "the penguin waddles");
		check(CharacterLibrary.get("SpongeBob").model().extras().headTurn() == 0.3F && CharacterLibrary.get("Blue").model().extras().headTurn() == 0.0F,
			"SpongeBob's sponge turns a little, Blue's block not at all");
		check(CharacterLibrary.get("Skinny Alien").model().height() > 2.1F, "the skinny alien is tall: " + CharacterLibrary.get("Skinny Alien").model().height());
		CharacterModel burger = CharacterLibrary.get("Burger").model();
		check(burger.jawOpenRadians() < 0.0F, "the burger's bun opens upward");
		CharacterModel.Part head = burger.part(CharacterModel.Bone.HEAD);
		check(head != null && head.vertexCount() == 0, "the burger has a head joint without polygons");
		check(CharacterLibrary.problems().isEmpty(), "no load problems, got " + CharacterLibrary.problems());
	}

	/**
	 * Puts the characters folder back the way version 2 left it (skin Robot and Frog, the old alien), with one file
	 * changed by the "user", then reloads: unchanged old examples are replaced, changed ones are kept.
	 */
	private static void checkUpgradeFromVersion2(ClientGameTestContext context, Path v2jar) {
		Path folder = CharacterLibrary.folder();
		context.runOnClient(client -> {
			try (java.nio.file.FileSystem jar = java.nio.file.FileSystems.newFileSystem(v2jar)) {
				for (String example : List.of("Alien", "Robot", "Frog", "Burger")) {
					Path dir = folder.resolve(example);
					if (Files.isDirectory(dir)) {
						try (var files = Files.list(dir)) {
							for (Path file : files.toList()) {
								Files.delete(file);
							}
						}
						Files.delete(dir);
					}
				}
				Files.createDirectories(folder.resolve("Alien"));
				for (String file : List.of("Robot.png", "Robot_talking.png", "Frog.png", "Frog.json", "README.txt",
					"Alien/model.obj", "Alien/character.json", "Alien/icon.png")) {
					Files.copy(jar.getPath("assets/playercharacters/examples/" + file), folder.resolve(file), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				}
				// The user changed the frog's settings, so the old frog must stay.
				Files.writeString(folder.resolve("Frog.json"), "{ \"name\": \"My Frog\" }");
				Files.writeString(folder.resolve(".examples"), "Robot\nFrog\nAlien\n");
			} catch (IOException e) {
				throw new AssertionError("Couldn't set up the version 2 folder", e);
			}
			CharacterLibrary.reload();
		});
		check(!Files.exists(folder.resolve("Robot.png")) && !Files.exists(folder.resolve("Robot_talking.png")), "upgrade: the old skin robot was removed");
		check(CharacterLibrary.get("Robot").isModel(), "upgrade: the robot is 3D now");
		check(readString(folder.resolve("Alien").resolve("character.json")).contains("\"butt\""), "upgrade: the alien of version 2 was updated");
		check(Files.exists(folder.resolve("Frog.png")) && readString(folder.resolve("Frog.json")).contains("My Frog"), "upgrade: the changed old frog was kept");
		check(CharacterLibrary.get("Frog").isModel() && CharacterLibrary.problems().stream().anyMatch(problem -> problem.startsWith("Frog.png: skipped")),
			"upgrade: the 3D frog is used and Frog.png is reported: " + CharacterLibrary.problems());
		check(readString(folder.resolve(".examples")).equals("Robot\nFrog\nAlien\nRobot 3D\nFrog 3D\nBurger\nPenguin\nSpongeBob\nSkinny Alien\nBlue\n"),
			"upgrade: the examples list was updated");
		check(readString(folder.resolve("README.txt")).contains("middle finger"), "upgrade: README.txt was updated");
		context.runOnClient(client -> {
			try {
				Files.delete(folder.resolve("Frog.png"));
				Files.delete(folder.resolve("Frog.json"));
			} catch (IOException e) {
				throw new AssertionError(e);
			}
			CharacterLibrary.reload();
		});
		check(CharacterLibrary.problems().isEmpty() && CharacterLibrary.all().size() == 8, "upgrade: eight 3D characters and no problems");
	}

	/** Puts the robot back the way version 3 left it, with an old examples list: it gets updated, and the new characters arrive. */
	private static void checkUpgradeFromVersion3(ClientGameTestContext context, Path v3jar) {
		Path folder = CharacterLibrary.folder();
		context.runOnClient(client -> {
			try (java.nio.file.FileSystem jar = java.nio.file.FileSystems.newFileSystem(v3jar)) {
				for (String file : List.of("Robot/model.obj", "Robot/character.json", "Robot/icon.png")) {
					Files.copy(jar.getPath("assets/playercharacters/examples/" + file), folder.resolve(file), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				}
				for (String example : List.of("Penguin", "Blue")) {
					try (var files = Files.list(folder.resolve(example))) {
						for (Path file : files.toList()) {
							Files.delete(file);
						}
					}
					Files.delete(folder.resolve(example));
				}
				Files.writeString(folder.resolve(".examples"), "Robot\nFrog\nAlien\nRobot 3D\nFrog 3D\nBurger\nSpongeBob\nSkinny Alien\n");
			} catch (IOException e) {
				throw new AssertionError("Couldn't set up the version 3 folder", e);
			}
			CharacterLibrary.reload();
		});
		check(readString(folder.resolve("Robot").resolve("model.obj")).contains("o middle_finger"), "upgrade: the robot of version 3 got its middle finger");
		check(Files.isDirectory(folder.resolve("Penguin")) && Files.isDirectory(folder.resolve("Blue")), "upgrade: the new characters were added");
		check(CharacterLibrary.problems().isEmpty() && CharacterLibrary.all().size() == 8, "upgrade from 3: eight characters and no problems");
	}

	/**
	 * Puts all eight examples back the way version 4 left them, with the old penguin's settings changed by the "user":
	 * the old Blue, SpongeBob and skinny alien are updated, and the changed penguin settings are kept.
	 */
	private static void checkUpgradeFromVersion4(ClientGameTestContext context, Path v4jar) {
		Path folder = CharacterLibrary.folder();
		context.runOnClient(client -> {
			try (java.nio.file.FileSystem jar = java.nio.file.FileSystems.newFileSystem(v4jar)) {
				for (String example : List.of("Alien", "Robot", "Frog", "Burger", "Penguin", "SpongeBob", "Skinny Alien", "Blue")) {
					for (String file : List.of("model.obj", "character.json", "icon.png")) {
						Files.copy(jar.getPath("assets/playercharacters/examples/" + example + "/" + file), folder.resolve(example).resolve(file),
							java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					}
				}
				Files.writeString(folder.resolve("Penguin").resolve("character.json"), readString(folder.resolve("Penguin").resolve("character.json"))
					.replace("\"name\": \"Penguin\"", "\"name\": \"Pingu\""));
			} catch (IOException e) {
				throw new AssertionError("Couldn't set up the version 4 folder", e);
			}
			CharacterLibrary.reload();
		});
		check(CharacterLibrary.get("Blue").displayName().equals("67") && readString(folder.resolve("Blue").resolve("model.obj")).contains("o jaw"),
			"upgrade: Blue of version 4 became 67");
		check(readString(folder.resolve("SpongeBob").resolve("model.obj")).equals(bundled(context, "SpongeBob/model.obj"))
			&& readString(folder.resolve("Skinny Alien").resolve("model.obj")).equals(bundled(context, "Skinny Alien/model.obj")),
			"upgrade: SpongeBob and the skinny alien of version 4 were updated");
		check(CharacterLibrary.get("Penguin").displayName().equals("Pingu") && CharacterLibrary.get("Penguin").model().extras().smoke() == null,
			"upgrade: the penguin with changed settings was kept as it was, model too");
		context.runOnClient(client -> {
			try (InputStream in = Files.newInputStream(FabricLoader.getInstance().getModContainer("playercharacters").orElseThrow()
				.findPath("assets/playercharacters/examples/Penguin/character.json").orElseThrow())) {
				Files.write(folder.resolve("Penguin").resolve("character.json"), in.readAllBytes());
			} catch (IOException e) {
				throw new AssertionError(e);
			}
			CharacterLibrary.reload();
		});
		check(CharacterLibrary.problems().isEmpty() && CharacterLibrary.all().size() == 8 && CharacterLibrary.get("Penguin").model().extras().smoke() != null
			&& readString(folder.resolve("Penguin").resolve("model.obj")).equals(bundled(context, "Penguin/model.obj")),
			"upgrade from 4: once the settings match again the penguin is updated too, and it smokes");
	}

	/** Puts all eight examples back the way version 5 left them: every one of them is updated. */
	private static void checkUpgradeFromVersion5(ClientGameTestContext context, Path v5jar) {
		Path folder = CharacterLibrary.folder();
		List<String> examples = List.of("Alien", "Robot", "Frog", "Burger", "Penguin", "SpongeBob", "Skinny Alien", "Blue");
		context.runOnClient(client -> {
			try (java.nio.file.FileSystem jar = java.nio.file.FileSystems.newFileSystem(v5jar)) {
				for (String example : examples) {
					for (String file : List.of("model.obj", "character.json", "icon.png")) {
						Files.copy(jar.getPath("assets/playercharacters/examples/" + example + "/" + file), folder.resolve(example).resolve(file),
							java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					}
				}
			} catch (IOException e) {
				throw new AssertionError("Couldn't set up the version 5 folder", e);
			}
			CharacterLibrary.reload();
		});
		for (String example : examples) {
			check(readString(folder.resolve(example).resolve("model.obj")).equals(bundled(context, example + "/model.obj"))
				&& readString(folder.resolve(example).resolve("character.json")).equals(bundled(context, example + "/character.json")),
				"upgrade: " + example + " of version 5 was updated");
		}
		check(CharacterLibrary.problems().isEmpty() && CharacterLibrary.all().size() == 8, "upgrade from 5: eight characters and no problems");
	}

	/** An example file as the mod has it. */
	private static String bundled(ClientGameTestContext context, String file) {
		return context.computeOnClient(client -> readString(FabricLoader.getInstance().getModContainer("playercharacters").orElseThrow()
			.findPath("assets/playercharacters/examples/" + file).orElseThrow()));
	}

	/** A skin character made from Minecraft's own Steve skin, and what happens when a skin has a 3D character's name. */
	private static void addSkinCharacter(ClientGameTestContext context) {
		Path folder = CharacterLibrary.folder();
		context.runOnClient(client -> {
			try (InputStream in = client.getResourceManager().open(Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png"))) {
				byte[] png = in.readAllBytes();
				Files.write(folder.resolve("Tester.png"), png);
				Files.write(folder.resolve("Robot.png"), png);
			} catch (IOException e) {
				throw new AssertionError("Couldn't write the test skins", e);
			}
			CharacterLibrary.reload();
		});
		check(CharacterLibrary.get("Tester") != null && !CharacterLibrary.get("Tester").isModel()
			&& !CharacterLibrary.get("Tester").customTalkingFrame(), "Tester.png is a skin character with the automatic mouth");
		check(CharacterLibrary.get("Robot").isModel(), "the 3D Robot wins over Robot.png");
		check(CharacterLibrary.problems().stream().anyMatch(problem -> problem.startsWith("Robot.png: skipped")),
			"Robot.png is reported: " + CharacterLibrary.problems());
		context.runOnClient(client -> {
			try {
				Files.delete(folder.resolve("Robot.png"));
			} catch (IOException e) {
				throw new AssertionError("Couldn't delete Robot.png", e);
			}
			CharacterLibrary.reload();
		});
		check(CharacterLibrary.problems().isEmpty() && CharacterLibrary.all().size() == 9, "nine characters and no problems");
	}

	private static void setChoice(ClientGameTestContext context, Choice choice) {
		context.runOnClient(client -> CharacterSettings.get().setChoice(FRIEND, "Friend", choice));
		context.waitTicks(3);
	}

	private static void spawnFriend(Minecraft client) {
		LocalPlayer self = client.player;
		RemotePlayer friend = new RemotePlayer(client.level, new GameProfile(FRIEND, "Friend"));
		Vec3 forward = Vec3.directionFromRotation(0.0F, self.getYRot());
		float facingUs = self.getYRot() + 180.0F;
		friend.snapTo(self.getX() + forward.x * 3.2, self.getY(), self.getZ() + forward.z * 3.2, facingUs, 0.0F);
		friend.setYHeadRot(facingUs);
		friend.setYBodyRot(facingUs);
		friend.yHeadRotO = facingUs;
		friend.yBodyRotO = facingUs;
		friend.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		friend.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
		friend.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
		friend.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
		friend.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
		client.level.addEntity(friend);
		self.setXRot(0.0F);
	}

	/** Turns the friend away from facing us by {@code degrees}. */
	private static void turnFriend(Minecraft client, float degrees) {
		Entity friend = client.level.getPlayerByUUID(FRIEND);
		float yaw = client.player.getYRot() + 180.0F + degrees;
		friend.setYRot(yaw);
		friend.yRotO = yaw;
		friend.setYHeadRot(yaw);
		friend.setYBodyRot(yaw);
		if (friend instanceof RemotePlayer remote) {
			remote.yHeadRotO = yaw;
			remote.yBodyRotO = yaw;
		}
	}

	private static CharacterRenderState friendRenderState(Minecraft client) {
		Entity friend = client.level.getPlayerByUUID(FRIEND);
		EntityRenderState state = client.getEntityRenderDispatcher().getRenderer(friend).createRenderState(friend, 1.0F);
		return (CharacterRenderState) state;
	}

	/** Checks what the player renderer is told to draw for the friend this frame. */
	private static void checkRenderState(ClientGameTestContext context, String character, boolean jawOpen) {
		float[] jaw = new float[1];
		String drawn = context.computeOnClient(client -> {
			CharacterRenderState state = friendRenderState(client);
			jaw[0] = state.playercharacters$jaw();
			PlayerCharacter drawnCharacter = state.playercharacters$character();
			return drawnCharacter == null ? null : drawnCharacter.id();
		});
		check(character.equals(drawn), "friend is drawn as " + drawn);
		check(jawOpen ? jaw[0] > 0.4F : jaw[0] < 0.05F, "jaw is " + (jawOpen ? "open" : "closed") + " (" + String.format("%.2f", jaw[0]) + ")");
	}

	private static float fartBend(ClientGameTestContext context) {
		return context.computeOnClient(client -> friendRenderState(client).playercharacters$fart());
	}

	private static void checkSkin(ClientGameTestContext context, UUID player, String suffix) {
		PlayerSkin skin = context.computeOnClient(client -> client.level.getPlayerByUUID(player) instanceof AbstractClientPlayer p ? p.getSkin() : null);
		check(skin != null && skin.body().texturePath().getPath().endsWith(suffix),
			"rendered skin is the " + suffix.substring(1) + " frame (" + (skin == null ? null : skin.body().texturePath()) + ")");
	}

	private record Area(int x, int y, int width, int height) {
	}

	private interface AreaFinder {
		Object find(Minecraft client);
	}

	/** Moves the mouse to the middle of a widget (or area) and left-clicks it. */
	private static void clickWidget(ClientGameTestContext context, AreaFinder finder) {
		double[] position = context.computeOnClient(client -> {
			Object found = finder.find(client);
			Area area = found instanceof AbstractWidget widget
				? new Area(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight())
				: (Area) found;
			int scale = client.getWindow().getGuiScale();
			return new double[] {(area.x() + area.width() / 2.0) * scale, (area.y() + area.height() / 2.0) * scale};
		});
		context.getInput().setCursorPos(position[0], position[1]);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
	}

	@SuppressWarnings("unchecked")
	private static <T> T find(List<? extends GuiEventListener> children, Class<T> type) {
		for (GuiEventListener child : children) {
			if (type.isInstance(child)) {
				return (T) child;
			}
		}
		throw new AssertionError("No " + type.getSimpleName() + " on screen");
	}

	static String readString(Path file) {
		try {
			return Files.readString(file);
		} catch (Exception e) {
			throw new AssertionError("Couldn't read " + file, e);
		}
	}

	static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("Check failed: " + what);
		}
		System.out.println("[playercharacters-test] OK: " + what);
	}

	/** Feeds loud 20 ms voice frames for a player from a background thread, like Simple Voice Chat does. */
	private static final class Talking implements AutoCloseable {
		private final AtomicBoolean running = new AtomicBoolean(true);
		private final Thread thread;

		private Talking(UUID player) {
			short[] frame = new short[960];
			for (int i = 0; i < frame.length; i++) {
				frame[i] = (short) (6000 * Math.sin(2 * Math.PI * 220 * i / 48000.0));
			}
			this.thread = new Thread(() -> {
				while (this.running.get()) {
					TalkTracker.onAudioFrame(player, frame);
					try {
						Thread.sleep(20);
					} catch (InterruptedException e) {
						return;
					}
				}
				TalkTracker.onAudioFrame(player, new short[0]);
			}, "fake-voice-" + player);
			this.thread.setDaemon(true);
			this.thread.start();
		}

		static Talking start(UUID player) {
			return new Talking(player);
		}

		@Override
		public void close() {
			this.running.set(false);
			try {
				this.thread.join(1000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}
}
