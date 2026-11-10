package com.playercharacters.test;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import com.playercharacters.CharacterResolver;
import com.playercharacters.character.CharacterLibrary;
import com.playercharacters.character.CharacterSkin;
import com.playercharacters.config.CharacterSettings;
import com.playercharacters.config.Choice;
import com.playercharacters.gui.CharacterMenuScreen;
import com.playercharacters.gui.CharacterPickerScreen;
import com.playercharacters.voice.TalkTracker;
import java.nio.file.Files;
import java.nio.file.Path;
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
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Starts the real game, makes a world, and checks the mod end to end:
 * example characters, skins on another player, talking mouths, both key binds and the menus.
 * Screenshots end up in run/screenshots.
 */
@SuppressWarnings("UnstableApiUsage")
public class PlayerCharactersClientGameTest implements FabricClientGameTest {
	private static final UUID FRIEND = UUID.fromString("5f1a6c3e-1111-4222-8333-00000000f00d");

	@Override
	public void runTest(ClientGameTestContext context) {
		// Start from default settings, even if an earlier test run saved some.
		context.runOnClient(client -> {
			CharacterSettings settings = CharacterSettings.get();
			settings.setTalkingMouths(true);
			settings.setDefaultChoice(Choice.OWN_SKIN);
			settings.setChoice(FRIEND, "Friend", Choice.DEFAULT);
			settings.setChoice(client.getUser().getProfileId(), client.getUser().getName(), Choice.DEFAULT);
		});

		if (FabricLoader.getInstance().isModLoaded("voicechat")) {
			context.runOnClient(client -> VoiceChatCheck.finishOnboarding());
		}

		List<String> ids = context.computeOnClient(client -> CharacterLibrary.all().stream().map(CharacterSkin::id).toList());
		check(ids.equals(List.of("Frog", "Robot")), "example characters loaded, got " + ids);
		check(CharacterLibrary.get("Robot").customTalkingFrame(), "Robot uses Robot_talking.png");
		check(!CharacterLibrary.get("Frog").customTalkingFrame(), "Frog uses the automatic mouth");
		check(CharacterLibrary.problems().isEmpty(), "no load problems, got " + CharacterLibrary.problems());

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("weather clear");
			world.getClientWorld().waitForChunksRender();
			context.runOnClient(client -> client.options.fov().set(40));

			// Another player standing in front of us.
			context.runOnClient(PlayerCharactersClientGameTest::spawnFriend);
			context.waitTicks(10);
			check(context.computeOnClient(client -> CharacterResolver.skinFor(FRIEND)) == null, "friend keeps own skin by default");
			context.takeScreenshot("01-friend-own-skin");

			// Default for everyone = Random.
			context.runOnClient(client -> CharacterSettings.get().setDefaultChoice(Choice.RANDOM));
			check(context.computeOnClient(client -> CharacterResolver.characterFor(FRIEND)) != null, "random default gives the friend a character");
			context.waitTicks(2);
			context.takeScreenshot("02-friend-random-default");

			// A specific character, quiet and talking.
			context.runOnClient(client -> CharacterSettings.get().setChoice(FRIEND, "Friend", Choice.character("Robot")));
			context.waitTicks(2);
			checkTexture(context, FRIEND, "/idle");
			context.takeScreenshot("03-friend-robot-quiet");
			try (Talking ignored = Talking.start(FRIEND)) {
				context.waitTicks(3);
				checkTexture(context, FRIEND, "/talking");
				context.takeScreenshot("04-friend-robot-talking");

				context.runOnClient(client -> CharacterSettings.get().setChoice(FRIEND, "Friend", Choice.character("Frog")));
				context.waitTicks(3);
				checkTexture(context, FRIEND, "/talking");
				context.takeScreenshot("05-friend-frog-talking");

				// "Toggle talking mouths" key (U).
				context.getInput().pressKey(InputConstants.KEY_U);
				context.waitTicks(3);
				check(!CharacterSettings.get().talkingMouths(), "U turned talking mouths off");
				checkTexture(context, FRIEND, "/idle");
				context.takeScreenshot("06-mouths-off-while-talking");
				context.getInput().pressKey(InputConstants.KEY_U);
				context.waitTicks(3);
				check(CharacterSettings.get().talkingMouths(), "U turned talking mouths back on");
			}
			context.waitTicks(10);
			checkTexture(context, FRIEND, "/idle");

			// Both keys are listed in Options > Controls > Key Binds, where they can be changed.
			check(context.computeOnClient(client -> java.util.Arrays.stream(client.options.keyMappings)
					.filter(key -> key.getName().startsWith("key.playercharacters."))
					.count()) == 2, "both keys are in the Key Binds menu");
			context.setScreen(() -> new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(null, Minecraft.getInstance().options));
			context.waitTicks(2);
			context.runOnClient(client -> {
				// Scroll so the "Open character menu" row (bound to K) is near the top.
				ContainerObjectSelectionList<?> list = find(client.screen.children(), ContainerObjectSelectionList.class);
				for (ContainerObjectSelectionList.Entry<?> entry : list.children()) {
					if (!entry.children().isEmpty() && entry.children().getFirst() instanceof AbstractWidget button
						&& button.getMessage().getString().equals("K")) {
						list.setScrollAmount(list.scrollAmount() + entry.getY() - list.getY() - 30);
						break;
					}
				}
			});
			context.waitTicks(2);
			context.takeScreenshot("06b-key-binds");
			context.setScreen(() -> null);

			// "Open character menu" key (K).
			context.getInput().pressKey(InputConstants.KEY_K);
			context.waitForScreen(CharacterMenuScreen.class);
			context.takeScreenshot("07-menu");

			// Click our own row's button, pick Robot in the picker, press Choose.
			clickWidget(context, client -> {
				ContainerObjectSelectionList<?> list = find(client.screen.children(), ContainerObjectSelectionList.class);
				return (AbstractWidget) list.children().getFirst().children().getFirst();
			});
			context.waitForScreen(CharacterPickerScreen.class);
			context.takeScreenshot("08-picker");
			clickWidget(context, client -> {
				ObjectSelectionList<?> list = find(client.screen.children(), ObjectSelectionList.class);
				for (ObjectSelectionList.Entry<?> entry : list.children()) {
					if (entry.getNarration().getString().contains("Robot")) {
						return new Area(entry.getX(), entry.getY(), entry.getWidth(), entry.getHeight());
					}
				}
				throw new AssertionError("Robot is not in the picker");
			});
			context.waitTicks(2);
			context.takeScreenshot("09-picker-robot-selected");
			context.clickScreenButton("playercharacters.picker.choose");
			context.waitForScreen(CharacterMenuScreen.class);
			UUID self = context.computeOnClient(client -> client.player.getUUID());
			check(context.computeOnClient(client -> CharacterSettings.get().choiceFor(self)).equals(Choice.character("Robot")),
				"picker saved Robot for us");
			context.clickScreenButton("playercharacters.menu.reload");
			context.waitTicks(2);
			check(context.computeOnClient(client -> CharacterLibrary.all().size()) == 2, "reload keeps both characters");
			context.takeScreenshot("10-menu-after-choosing");
			context.setScreen(() -> null);

			// Ourselves in third person, talking. The friend would block the camera, so remove them first.
			context.runOnClient(client -> {
				client.level.removeEntity(client.level.getPlayerByUUID(FRIEND).getId(), net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
				client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			});
			try (Talking ignored = Talking.start(self)) {
				context.waitTicks(3);
				checkTexture(context, self, "/talking");
				context.takeScreenshot("11-self-robot-talking");
			}

			// Real audio through Simple Voice Chat, when it is installed in the test game.
			if (FabricLoader.getInstance().isModLoaded("voicechat")) {
				context.waitTicks(10);
				VoiceChatCheck.run(context, world, self, () -> context.takeScreenshot("12-voice-chat-self-talking"));
			} else {
				System.out.println("[playercharacters-test] Simple Voice Chat not installed, skipping the voice chat check");
			}
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

			Path config = FabricLoader.getInstance().getConfigDir().resolve("playercharacters.json");
			String saved = readString(config);
			check(saved.contains("\"character:Robot\"") && saved.contains(FRIEND.toString()), "choices saved to playercharacters.json:\n" + saved);
		}
	}

	private static void spawnFriend(Minecraft client) {
		LocalPlayer self = client.player;
		RemotePlayer friend = new RemotePlayer(client.level, new GameProfile(FRIEND, "Friend"));
		Vec3 forward = Vec3.directionFromRotation(0.0F, self.getYRot());
		float facingUs = self.getYRot() + 180.0F;
		friend.snapTo(self.getX() + forward.x * 3.0, self.getY(), self.getZ() + forward.z * 3.0, facingUs, 0.0F);
		friend.setYHeadRot(facingUs);
		friend.setYBodyRot(facingUs);
		friend.yHeadRotO = facingUs;
		friend.yBodyRotO = facingUs;
		client.level.addEntity(friend);
		self.setXRot(0.0F);
	}

	private static void checkTexture(ClientGameTestContext context, UUID player, String suffix) {
		PlayerSkin skin = context.computeOnClient(client -> client.level.getPlayerByUUID(player) instanceof net.minecraft.client.player.AbstractClientPlayer p ? p.getSkin() : null);
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

	private static String readString(Path file) {
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
