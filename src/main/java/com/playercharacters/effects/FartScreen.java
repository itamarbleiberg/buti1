package com.playercharacters.effects;

import com.playercharacters.PlayerCharactersClient;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * What you see when you fart in first person: the screen turns green and bubbles of gas float up and pop.
 * Runs on the client thread; timing is in client ticks and stops while the game is paused.
 */
public final class FartScreen {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(PlayerCharactersClient.MOD_ID, "fart_screen");
	private static final Identifier BUBBLE = Identifier.withDefaultNamespace("hud/air");
	private static final Identifier POPPING = Identifier.withDefaultNamespace("hud/air_bursting");
	/** Ticks: the green fades in quickly, stays, then fades out. */
	private static final int DURATION = 60;
	private static final int FADE_IN = 4;
	private static final int FADE_OUT_FROM = 34;
	/** New bubbles come up from the bottom until this tick. */
	private static final int SPAWN_UNTIL = 30;
	/** How long a bubble shows its popping picture. */
	private static final int POP_TICKS = 3;
	private static final int GREEN = 0x6FA83A;
	private static final float MAX_GREEN = 0.42F;
	private static final RandomSource RANDOM = RandomSource.create();

	private static final List<Bubble> BUBBLES = new ArrayList<>();
	/** Ticks since the last fart started, or -1 when nothing is showing. */
	private static int age = -1;
	private static int started;
	private static int framesDrawn;

	private FartScreen() {
	}

	/** One bubble. Positions are fractions of the screen, so they stay put when the window is resized. */
	private static final class Bubble {
		float x;
		float y;
		float oldY;
		/** Fraction of the screen height. */
		final float size;
		final float speed;
		final float wobble;
		final boolean green;
		int age;
		final int popAt;

		Bubble(float x, float y, int popAt) {
			this.x = x;
			this.y = y;
			this.oldY = y;
			this.size = 0.05F + RANDOM.nextFloat() * RANDOM.nextFloat() * 0.1F;
			this.speed = 0.004F + RANDOM.nextFloat() * 0.01F;
			this.wobble = RANDOM.nextFloat() * Mth.TWO_PI;
			this.green = RANDOM.nextInt(4) != 0;
			this.popAt = popAt;
		}

		boolean popping() {
			return this.age >= this.popAt;
		}
	}

	/** Starts the green screen. Called when the local player farts. */
	public static void start() {
		boolean fresh = age < 0;
		age = 0;
		started++;
		int bubbles = fresh ? 14 : 6;
		for (int i = 0; i < bubbles; i++) {
			// Bubbles all over the screen right away, popping one after another.
			BUBBLES.add(new Bubble(RANDOM.nextFloat(), 0.2F + RANDOM.nextFloat() * 0.9F, 3 + RANDOM.nextInt(26)));
		}
	}

	/** How many times the green screen was started since the game started. */
	public static int startedCount() {
		return started;
	}

	/** How many frames the green screen was drawn in since the game started. */
	public static int framesDrawn() {
		return framesDrawn;
	}

	/** Whether the green screen is showing (in first person) right now. */
	public static boolean isShowing() {
		return age >= 0 && Minecraft.getInstance().options.getCameraType().isFirstPerson();
	}

	/** Called every client tick: moves, adds and pops bubbles. */
	public static void tick(Minecraft client) {
		if (age < 0) {
			return;
		}
		if (client.level == null || client.player == null) {
			age = -1;
			BUBBLES.clear();
			return;
		}
		if (client.isPaused()) {
			return;
		}
		age++;
		if (age < SPAWN_UNTIL && RANDOM.nextFloat() < 0.7F) {
			int lifetime = Math.min(DURATION - POP_TICKS - age, 10 + RANDOM.nextInt(25));
			BUBBLES.add(new Bubble(RANDOM.nextFloat(), 1.05F, lifetime));
		}

		boolean popSound = false;
		boolean firstPerson = client.options.getCameraType().isFirstPerson();
		Iterator<Bubble> iterator = BUBBLES.iterator();
		while (iterator.hasNext()) {
			Bubble bubble = iterator.next();
			bubble.age++;
			bubble.oldY = bubble.y;
			if (!bubble.popping()) {
				bubble.y -= bubble.speed;
			}
			if (bubble.age == bubble.popAt && firstPerson && !popSound) {
				// One pop sound per tick at most, like the air bar under water.
				popSound = true;
				client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BUBBLE_POP, 0.8F + RANDOM.nextFloat() * 0.8F, 0.45F));
			}
			if (bubble.age >= bubble.popAt + POP_TICKS || bubble.y < -0.2F) {
				iterator.remove();
			}
		}
		if (age >= DURATION) {
			age = -1;
			BUBBLES.clear();
		}
	}

	/** Draws the green and the bubbles, under the crosshair and the hotbar. */
	public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		if (!isShowing()) {
			return;
		}
		framesDrawn++;
		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
		float t = age + partialTick;
		float strength;
		if (t < FADE_IN) {
			strength = t / FADE_IN;
		} else if (t < FADE_OUT_FROM) {
			strength = 1.0F;
		} else {
			strength = Math.max(0.0F, 1.0F - (t - FADE_OUT_FROM) / (DURATION - FADE_OUT_FROM));
		}
		strength = strength * strength * (3.0F - 2.0F * strength);
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();

		// The gas swirls: the green gets a little stronger and weaker, and is thickest at the top and bottom.
		float green = MAX_GREEN * strength * (1.0F + 0.12F * Mth.sin(t * 0.55F));
		graphics.fill(0, 0, width, height, ARGB.color(Math.round(green * 255.0F), GREEN));
		int edge = ARGB.color(Math.round(0.35F * strength * 255.0F), 0x4E8A22);
		graphics.fillGradient(0, 0, width, height / 3, edge, ARGB.color(0, 0x4E8A22));
		graphics.fillGradient(0, height * 2 / 3, width, height, ARGB.color(0, 0x4E8A22), edge);

		int alpha = Math.round(Math.min(1.0F, strength * 1.5F) * 255.0F);
		for (Bubble bubble : BUBBLES) {
			// The sprite is 9 pixels wide; sizes in whole steps of 9 keep its pixels square.
			int size = Math.max(9, Math.round(bubble.size * height / 9.0F) * 9);
			float y = Mth.lerp(partialTick, bubble.oldY, bubble.y);
			float x = bubble.x + 0.012F * Mth.sin((bubble.age + partialTick) * 0.25F + bubble.wobble);
			int left = Math.round(x * width - size / 2.0F);
			int top = Math.round(y * height - size / 2.0F);
			int tint = bubble.green ? 0xC8FF8C : 0xFFFFFF;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, bubble.popping() ? POPPING : BUBBLE, left, top, size, size, ARGB.color(alpha, tint));
		}
	}
}
