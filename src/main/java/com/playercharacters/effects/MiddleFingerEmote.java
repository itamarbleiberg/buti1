package com.playercharacters.effects;

import com.playercharacters.CharacterResolver;
import com.playercharacters.character.PlayerCharacter;
import com.playercharacters.config.CharacterSettings;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * The middle finger emote: the character raises its right arm toward whoever is in front of it, shows the middle
 * finger and looks mad for a couple of seconds. Runs on the client thread; timing is in game ticks.
 */
public final class MiddleFingerEmote {
	/** Ticks: the arm goes up, stays up, then comes down. The angry face lasts a little longer. */
	private static final int RAISED_AT = 4;
	private static final int LOWER_FROM = 44;
	private static final int LOWERED_AT = 52;
	private static final int MAD_UNTIL = 60;
	/** Saying it again during this many ticks keeps the arm up instead of starting over. */
	private static final int EXTEND_FROM = RAISED_AT;
	private static final RandomSource RANDOM = RandomSource.create();
	private static final Map<UUID, Emote> ACTIVE = new HashMap<>();
	private static int started;

	private MiddleFingerEmote() {
	}

	private static final class Emote {
		int age;
	}

	/** Makes this player flip off whoever is in front of them, if the emote is turned on. */
	public static void start(UUID player) {
		if (!CharacterSettings.get().middleFinger()) {
			return;
		}
		Emote current = ACTIVE.get(player);
		if (current != null && current.age >= EXTEND_FROM && current.age < LOWER_FROM) {
			current.age = EXTEND_FROM; // still at it: keep the finger up longer
		} else if (current == null || current.age >= LOWER_FROM) {
			ACTIVE.put(player, new Emote());
		}
		started++;
	}

	public static boolean isActive(UUID player) {
		return ACTIVE.containsKey(player);
	}

	/** How many times the emote was started since the game started. */
	public static int startedCount() {
		return started;
	}

	/** How far the right arm is raised: 0 when down, 1 when pointing at someone. */
	public static float raise(UUID player, float partialTick) {
		Emote emote = ACTIVE.get(player);
		if (emote == null) {
			return 0.0F;
		}
		float t = emote.age + partialTick;
		if (t < RAISED_AT) {
			float x = t / RAISED_AT;
			return x * x * (3 - 2 * x) * (1.0F + 0.12F * (float) Math.sin(x * Math.PI)); // a little overshoot, like a jab
		}
		if (t < LOWER_FROM) {
			return 1.0F;
		}
		float x = Math.min(1.0F, (t - LOWER_FROM) / (LOWERED_AT - LOWER_FROM));
		return 1.0F - x * x * (3 - 2 * x);
	}

	/** Whether the character shows its angry face right now. */
	public static boolean isMad(UUID player) {
		Emote emote = ACTIVE.get(player);
		return emote != null && emote.age <= MAD_UNTIL;
	}

	public static void clear() {
		ACTIVE.clear();
	}

	/** Called every client tick: angry puffs above the head, and forgetting finished emotes. */
	public static void tick(Minecraft client) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		ClientLevel level = client.level;
		if (level == null) {
			ACTIVE.clear();
			return;
		}
		Iterator<Map.Entry<UUID, Emote>> iterator = ACTIVE.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Emote> entry = iterator.next();
			Emote emote = entry.getValue();
			emote.age++;
			if (emote.age > MAD_UNTIL) {
				iterator.remove();
				continue;
			}
			if (emote.age == 2 || emote.age == 22 || emote.age == 42) {
				Player player = level.getPlayerByUUID(entry.getKey());
				if (player != null) {
					angryPuff(level, player);
				}
			}
		}
	}

	/** The angry villager cloud, above the character's head. */
	private static void angryPuff(ClientLevel level, Player player) {
		PlayerCharacter character = CharacterResolver.characterFor(player.getUUID());
		double height = character != null && character.model() != null ? character.model().height() : player.getBbHeight();
		double top = player.getY() + Math.max(height, 0.9) * player.getScale() + 0.25;
		level.addParticle(ParticleTypes.ANGRY_VILLAGER, player.getX() + (RANDOM.nextDouble() - 0.5) * 0.4, top + RANDOM.nextDouble() * 0.15,
			player.getZ() + (RANDOM.nextDouble() - 0.5) * 0.4, 0.0, 0.0, 0.0);
	}
}
