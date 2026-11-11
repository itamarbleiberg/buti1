package com.playercharacters.effects;

import com.playercharacters.CharacterResolver;
import com.playercharacters.character.CharacterModel;
import com.playercharacters.character.CharacterModel.Bone;
import com.playercharacters.character.PlayerCharacter;
import com.playercharacters.mixin.SingleQuadParticleAccessor;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Smoke from characters that smoke (the penguin's cigarette): a thin, wavering plume of soft gray smoke rising from the
 * burning tip, and every few seconds a puff blown out in front. The tip is where the renderer last drew it, so the smoke
 * follows the head through dances and the rest; when it wasn't drawn (behind the camera), it is worked out from the
 * player's head instead. In your own first-person view only the puffs show, out in front of you. Runs on the client thread.
 */
public final class CigaretteSmoke {
	/** The player renderer's 15/16 scale, and the model space y of the feet. */
	private static final float PLAYER_SCALE = 0.9375F;
	private static final float FEET_Y = 1.501F;
	/** Sneaking lowers the head: the player model moves it down 4.2 pixels and the renderer moves everything down 2. */
	private static final float CROUCH_DROP = 4.2F / 16.0F * PLAYER_SCALE + 2.0F / 16.0F;
	private static final double MAX_DISTANCE_SQR = 40.0 * 40.0;
	/** Ticks between two puffs blown out, at least (a little more at random). */
	private static final int PUFF_EVERY = 120;
	private static final RandomSource RANDOM = RandomSource.create();
	/** Where each player's cigarette tip was last drawn, by entity id. */
	private static final Map<Integer, Seen> SEEN = new HashMap<>();
	private static final Map<Integer, Integer> NEXT_PUFF = new HashMap<>();
	private static int puffs;
	private static int blown;
	private static int fromDrawnTip;

	private record Seen(Vec3 tip, long tick) {
	}

	/** Client ticks counted here. */
	private static long ticks;

	private CigaretteSmoke() {
	}

	/** How many bits of smoke were made since the game started. */
	public static int puffCount() {
		return puffs;
	}

	/** How many puffs were blown out since the game started. */
	public static int blownCount() {
		return blown;
	}

	/** How many bits of smoke came from where the tip was drawn (rather than where it was worked out to be). */
	public static int fromDrawnTipCount() {
		return fromDrawnTip;
	}

	/**
	 * The renderer drew a character's cigarette tip here: blocks from the camera. Called while drawing; GUI previews
	 * (where the position means nothing in the world) are told apart by being far from the player.
	 */
	public static void drawn(int entityId, double entityX, double entityY, double entityZ, float x, float y, float z) {
		Minecraft client = Minecraft.getInstance();
		Vec3 camera = client.gameRenderer.getMainCamera().position();
		Vec3 tip = new Vec3(camera.x + x, camera.y + y, camera.z + z);
		if (tip.distanceToSqr(entityX, entityY + 1.0, entityZ) < 3.0 * 3.0) {
			SEEN.put(entityId, new Seen(tip, ticks));
		}
	}

	/** Called every client tick. */
	public static void tick(Minecraft client) {
		ticks++;
		ClientLevel level = client.level;
		if (level == null || client.player == null || client.isPaused()) {
			return;
		}
		int every = switch (client.options.particles().get()) {
			case ALL -> 2;
			case DECREASED -> 4;
			case MINIMAL -> 0;
		};
		if (every == 0) {
			return;
		}
		for (Player player : level.players()) {
			if (player.isInvisible() || player.isSpectator() || player.isEyeInFluid(FluidTags.WATER)) {
				continue;
			}
			PlayerCharacter character = CharacterResolver.characterFor(player.getUUID());
			CharacterModel model = character == null ? null : character.model();
			if (model == null || model.extras().smoke() == null) {
				continue;
			}
			boolean ownFirstPerson = player == client.player && client.options.getCameraType().isFirstPerson();
			if (ownFirstPerson) {
				// Your own cigarette is right under your eyes: its smoke would cover the screen, so only the puffs show.
				if (puffDue(player)) {
					Vec3 look = player.getLookAngle();
					Vec3 eyes = player.getEyePosition();
					blowPuff(client, eyes.add(look.scale(0.45)).add(0, -0.12, 0), look, player.getScale());
				}
				continue;
			}
			Vec3 tip = tip(model, player);
			if (tip == null || client.player.distanceToSqr(tip) > MAX_DISTANCE_SQR) {
				continue;
			}
			if ((player.tickCount + player.getId()) % every == 0) {
				rise(client, tip, player.getScale());
			}
			if (puffDue(player)) {
				blowPuff(client, tip, player.getLookAngle(), player.getScale());
			}
		}
		SEEN.values().removeIf(seen -> ticks - seen.tick > 40);
		NEXT_PUFF.keySet().removeIf(id -> level.getEntity(id) == null);
	}

	/** Where the tip is: where it was just drawn, or else worked out from the head. */
	private static @Nullable Vec3 tip(CharacterModel model, Player player) {
		Seen seen = SEEN.get(player.getId());
		if (seen != null && ticks - seen.tick <= 2) {
			fromDrawnTip++;
			return seen.tip;
		}
		Pose pose = player.getPose();
		if (pose != Pose.STANDING && pose != Pose.CROUCHING) {
			return null; // swimming, gliding, sleeping and so on: the head isn't where the math below puts it
		}
		return tipPosition(model, player);
	}

	private static boolean puffDue(Player player) {
		int now = player.tickCount;
		Integer next = NEXT_PUFF.get(player.getId());
		if (next == null || next > now + PUFF_EVERY * 2) {
			NEXT_PUFF.put(player.getId(), now + 40 + RANDOM.nextInt(PUFF_EVERY));
			return false;
		}
		if (now < next) {
			return false;
		}
		NEXT_PUFF.put(player.getId(), now + PUFF_EVERY + RANDOM.nextInt(60));
		return true;
	}

	/** A bit of the thin plume rising from the tip, wavering as it goes up. */
	private static void rise(Minecraft client, Vec3 tip, float scale) {
		double sway = 0.004;
		Particle particle = client.particleEngine.createParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, tip.x, tip.y + 0.01, tip.z,
			RANDOM.nextGaussian() * sway, (0.016 + RANDOM.nextDouble() * 0.008) * scale, RANDOM.nextGaussian() * sway);
		if (particle != null) {
			particle.scale((0.16F + RANDOM.nextFloat() * 0.1F) * scale);
			particle.setLifetime(40 + RANDOM.nextInt(20));
			look(particle, 0.5F + RANDOM.nextFloat() * 0.15F);
			puffs++;
		}
	}

	/** A puff blown out in front: a little cloud drifting forward and up. */
	private static void blowPuff(Minecraft client, Vec3 from, Vec3 forward, float scale) {
		for (int i = 0; i < 9; i++) {
			double speed = (0.035 + RANDOM.nextDouble() * 0.03) * scale;
			Particle particle = client.particleEngine.createParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, from.x, from.y, from.z,
				forward.x * speed + RANDOM.nextGaussian() * 0.008, 0.008 + forward.y * speed * 0.5 + RANDOM.nextGaussian() * 0.004,
				forward.z * speed + RANDOM.nextGaussian() * 0.008);
			if (particle != null) {
				particle.scale((0.26F + RANDOM.nextFloat() * 0.16F) * scale);
				particle.setLifetime(34 + RANDOM.nextInt(16));
				look(particle, 0.55F + RANDOM.nextFloat() * 0.15F);
				puffs++;
			}
		}
		blown++;
	}

	/** Light bluish gray, and see-through at first (it fades out by itself as it gets old). */
	private static void look(Particle particle, float alpha) {
		if (particle instanceof SingleQuadParticle quad) {
			float gray = 0.74F + RANDOM.nextFloat() * 0.1F;
			quad.setColor(gray, gray, gray * 1.04F);
			((SingleQuadParticleAccessor) quad).playercharacters$setAlpha(alpha);
		}
	}

	/** Where the tip of the cigarette is in the world, worked out from the head's turning (when it wasn't just drawn). */
	static @Nullable Vec3 tipPosition(CharacterModel model, Player player) {
		Vector3f smoke = model.extras().smoke();
		if (smoke == null) {
			return null;
		}
		CharacterModel.Part head = model.part(Bone.HEAD) != null ? model.part(Bone.HEAD) : model.part(Bone.JAW);
		Vector3f pivot = head != null ? head.pivot() : new Vector3f(0.0F, 0.0F, 0.0F);
		// To blocks relative to the feet: x toward the character's left, y up, z forward.
		Vector3f tip = bodySpace(smoke);
		Vector3f joint = bodySpace(pivot);
		float headTurn = model.extras().headTurn();
		float yaw = Mth.wrapDegrees(player.getYHeadRot() - player.yBodyRot) * Mth.DEG_TO_RAD * headTurn;
		float pitch = player.getXRot() * Mth.DEG_TO_RAD * headTurn;
		// Looking right turns the front toward -x, looking down tips it toward -y.
		new Quaternionf().rotationY(-yaw).rotateX(pitch).transform(tip.sub(joint)).add(joint);

		float scale = player.getScale();
		double x = tip.x * scale;
		double y = tip.y * scale - (player.isCrouching() ? CROUCH_DROP * scale : 0.0F);
		double z = tip.z * scale;
		double bodyYaw = Math.toRadians(player.yBodyRot);
		double sin = Math.sin(bodyYaw);
		double cos = Math.cos(bodyYaw);
		return new Vec3(player.getX() + x * cos - z * sin, player.getY() + y, player.getZ() + x * sin + z * cos);
	}

	private static Vector3f bodySpace(Vector3f model) {
		return new Vector3f(model.x * PLAYER_SCALE, (FEET_Y - model.y) * PLAYER_SCALE, -model.z * PLAYER_SCALE);
	}
}
