package com.playercharacters.effects;

import com.playercharacters.CharacterResolver;
import com.playercharacters.PlayerCharactersClient;
import com.playercharacters.character.PlayerCharacter;
import com.playercharacters.config.CharacterSettings;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * The fart animation: the player bends over, a green cloud puffs out of their butt and you hear it.
 * Everything here runs on the client thread. Timing is in game ticks, so it stops while the game is paused.
 */
public final class FartEffects {
	public static final Identifier SOUND = Identifier.fromNamespaceAndPath(PlayerCharactersClient.MOD_ID, "fart");
	/** Seconds: the whole animation, bending over until standing straight again. */
	private static final float DURATION = 1.35F;
	private static final float BENT_OVER_AFTER = 0.16F;
	private static final float STRAIGHTEN_FROM = 0.8F;
	/** Ticks after the start when the cloud and the sound come out (once the character is bent over). */
	private static final int PUFF_AT = 2;
	/** For how many ticks gas keeps coming out. */
	private static final int PUFF_TICKS = 10;
	/** Saying "fart" again sooner than this (in ticks) doesn't start over. */
	private static final int RESTART_AFTER = 12;
	/** Butt of normal players and skin characters, in blocks from the feet: x toward their left, y up, z forward. */
	private static final Vector3fc PLAYER_BUTT = new Vector3f(0.0F, 0.72F, -0.15F);
	private static final int[] GAS_COLORS = {0x9DB83A, 0x7F9A2E, 0xB3A63F, 0x6E8530, 0x8DA33A};
	private static final double MAX_DISTANCE_SQR = 64.0 * 64.0;

	private static final Map<UUID, Fart> ACTIVE = new HashMap<>();
	private static final RandomSource RANDOM = RandomSource.create();
	private static int started;

	private FartEffects() {
	}

	private static final class Fart {
		int age;
		int puffTicks;
	}

	/** Makes this player fart, if farts are turned on. */
	public static void start(UUID player) {
		if (!CharacterSettings.get().fartDetection()) {
			return;
		}
		Fart current = ACTIVE.get(player);
		if (current != null && current.age < RESTART_AFTER) {
			return;
		}
		ACTIVE.put(player, new Fart());
		started++;
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && client.player.getUUID().equals(player)) {
			FartScreen.start(); // your own fart: in first person, your screen goes green
		}
	}

	public static boolean isFarting(UUID player) {
		return ACTIVE.containsKey(player);
	}

	/** How many farts were started since the game started. */
	public static int startedCount() {
		return started;
	}

	/** How far this player is bent over right now: 0 when standing, up to about 1. */
	public static float bend(UUID player, float partialTick) {
		Fart fart = ACTIVE.get(player);
		return fart == null ? 0.0F : bendAt((fart.age + partialTick) / 20.0F);
	}

	/** How far a fart bends a player over {@code t} seconds after it started. */
	public static float bendAt(float t) {
		if (t <= 0.0F || t >= DURATION) {
			return 0.0F;
		}
		float in = smooth(t / BENT_OVER_AFTER);
		float out = 1.0F - smooth((t - STRAIGHTEN_FROM) / (DURATION - STRAIGHTEN_FROM));
		// A little shake while pushing.
		float shake = (float) Math.sin(t * Math.PI * 2.0 * 9.0) * 0.07F;
		return in * out * (1.0F + shake);
	}

	private static float smooth(float x) {
		float t = Math.max(0.0F, Math.min(1.0F, x));
		return t * t * (3.0F - 2.0F * t);
	}

	public static void clear() {
		ACTIVE.clear();
	}

	/** Called every client tick: lets out the gas and the sound, and forgets finished farts. */
	public static void tick(Minecraft client) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		ClientLevel level = client.level;
		if (level == null) {
			ACTIVE.clear();
			return;
		}
		Iterator<Map.Entry<UUID, Fart>> iterator = ACTIVE.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Fart> entry = iterator.next();
			Fart fart = entry.getValue();
			fart.age++;
			if (fart.age / 20.0F > DURATION) {
				iterator.remove();
				continue;
			}
			Player player = level.getPlayerByUUID(entry.getKey());
			if (player == null || fart.age < PUFF_AT || fart.puffTicks >= PUFF_TICKS) {
				continue;
			}
			fart.puffTicks++;
			Vec3 butt = buttPosition(player);
			if (client.player != null && client.player.distanceToSqr(butt) > MAX_DISTANCE_SQR) {
				continue;
			}
			// Out of the butt means backward, away from where the body faces.
			double yaw = Math.toRadians(player.yBodyRot);
			Vec3 backward = new Vec3(Math.sin(yaw), 0.0, -Math.cos(yaw));
			if (fart.puffTicks == 1) {
				client.getSoundManager().play(new SimpleSoundInstance(SOUND, SoundSource.PLAYERS, 1.0F, 0.9F + RANDOM.nextFloat() * 0.25F,
					RandomSource.create(), false, 0, SoundInstance.Attenuation.LINEAR, butt.x, butt.y, butt.z, false));
			}
			puff(client, level, butt, backward, fart.puffTicks, player.getScale());
		}
	}

	/** Where gas comes out of this player, in the world. */
	static Vec3 buttPosition(Player player) {
		PlayerCharacter character = CharacterResolver.characterFor(player.getUUID());
		Vector3fc butt = character != null && character.model() != null ? character.model().butt() : PLAYER_BUTT;
		float scale = player.getScale();
		double x = butt.x() * scale;
		double y = butt.y() * scale;
		double z = butt.z() * scale;
		if (player.isCrouching()) {
			y -= 0.2 * scale;
			z -= 0.2 * scale;
		}
		double yaw = Math.toRadians(player.yBodyRot);
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		return new Vec3(player.getX() + x * cos - z * sin, player.getY() + y, player.getZ() + x * sin + z * cos);
	}

	/** One tick of gas: puffs of green smoke shooting backward, plus a few stinky specks hanging in the air. */
	private static void puff(Minecraft client, ClientLevel level, Vec3 butt, Vec3 backward, int tick, float scale) {
		float strength = 1.0F - (tick - 1) / (float) PUFF_TICKS;
		float amount = switch (client.options.particles().get()) {
			case ALL -> 1.0F;
			case DECREASED -> 0.5F;
			case MINIMAL -> 0.25F;
		};

		// About 20 small puffs in all, most of them right at the start.
		int puffs = Math.max(tick == 1 ? 1 : 0, Math.round((0.6F + 3.2F * strength * strength) * amount));
		for (int i = 0; i < puffs; i++) {
			double speed = (0.03 + 0.06 * strength + RANDOM.nextDouble() * 0.02) * scale;
			double x = butt.x + RANDOM.nextGaussian() * 0.03 * scale;
			double y = butt.y + RANDOM.nextGaussian() * 0.03 * scale;
			double z = butt.z + RANDOM.nextGaussian() * 0.03 * scale;
			Particle particle = client.particleEngine.createParticle(ParticleTypes.POOF, x, y, z,
				backward.x * speed + RANDOM.nextGaussian() * 0.01, -0.01 + RANDOM.nextGaussian() * 0.008, backward.z * speed + RANDOM.nextGaussian() * 0.01);
			if (particle instanceof SingleQuadParticle quad) {
				int color = GAS_COLORS[RANDOM.nextInt(GAS_COLORS.length)];
				float light = 0.85F + RANDOM.nextFloat() * 0.25F;
				quad.setColor(Math.min(1.0F, ARGB.redFloat(color) * light), Math.min(1.0F, ARGB.greenFloat(color) * light),
					Math.min(1.0F, ARGB.blueFloat(color) * light));
			}
			if (particle != null) {
				particle.scale((0.5F + RANDOM.nextFloat() * 0.4F) * scale);
				particle.setLifetime(12 + RANDOM.nextInt(14)); // gone before it drifts up far
			}
		}

		int specks = Math.round(1.2F * strength * amount + RANDOM.nextFloat() * 0.4F);
		for (int i = 0; i < specks; i++) {
			double distance = (0.05 + RANDOM.nextDouble() * 0.35) * scale;
			int color = GAS_COLORS[RANDOM.nextInt(GAS_COLORS.length)];
			level.addParticle(new DustParticleOptions(color, 0.8F + RANDOM.nextFloat() * 0.6F),
				butt.x + backward.x * distance + RANDOM.nextGaussian() * 0.06 * scale,
				butt.y + RANDOM.nextGaussian() * 0.06 * scale,
				butt.z + backward.z * distance + RANDOM.nextGaussian() * 0.06 * scale,
				0.0, 0.0, 0.0);
		}
	}
}
