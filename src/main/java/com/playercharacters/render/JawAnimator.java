package com.playercharacters.render;

import com.playercharacters.CharacterResolver;
import com.playercharacters.config.CharacterSettings;
import com.playercharacters.voice.TalkTracker;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Moves each 3D character's jaw smoothly toward how wide the voice says it should be. Render thread only. */
public final class JawAnimator {
	/** Per second: how fast the jaw opens and closes. */
	private static final float OPEN_SPEED = 30.0F;
	private static final float CLOSE_SPEED = 16.0F;
	private static final Map<UUID, Jaw> JAWS = new HashMap<>();

	private JawAnimator() {
	}

	private static final class Jaw {
		float value;
		long lastNanos;
	}

	/** Call once per frame per player. Returns 0 (closed) to 1 (open). */
	public static float jawFor(UUID player) {
		Float preview = CharacterResolver.previewJaw(player);
		if (preview != null) {
			return preview;
		}
		float target = CharacterSettings.get().talkingMouths() ? TalkTracker.mouthOpenness(player) : 0.0F;
		Jaw jaw = JAWS.computeIfAbsent(player, id -> new Jaw());
		long now = System.nanoTime();
		float seconds = jaw.lastNanos == 0L ? 0.0F : Math.min(0.25F, (now - jaw.lastNanos) / 1.0E9F);
		jaw.lastNanos = now;
		float speed = target > jaw.value ? OPEN_SPEED : CLOSE_SPEED;
		jaw.value += (target - jaw.value) * (1.0F - (float) Math.exp(-speed * seconds));
		return jaw.value;
	}

	public static void clear() {
		JAWS.clear();
	}
}
