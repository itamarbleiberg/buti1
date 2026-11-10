package com.playercharacters.effects;

import com.playercharacters.CharacterResolver;
import com.playercharacters.character.CharacterModel;
import com.playercharacters.character.CharacterModel.Bone;
import com.playercharacters.character.PlayerCharacter;
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

/** A thin line of smoke rising from characters that smoke (the penguin's cigarette). Runs on the client thread. */
public final class CigaretteSmoke {
	/** The player renderer's 15/16 scale, and the model space y of the feet. */
	private static final float PLAYER_SCALE = 0.9375F;
	private static final float FEET_Y = 1.501F;
	/** Sneaking lowers the head: the player model moves it down 4.2 pixels and the renderer moves everything down 2. */
	private static final float CROUCH_DROP = 4.2F / 16.0F * PLAYER_SCALE + 2.0F / 16.0F;
	private static final double MAX_DISTANCE_SQR = 32.0 * 32.0;
	private static final RandomSource RANDOM = RandomSource.create();
	private static int puffs;

	private CigaretteSmoke() {
	}

	/** How many puffs of smoke were made since the game started. */
	public static int puffCount() {
		return puffs;
	}

	/** Called every client tick. */
	public static void tick(Minecraft client) {
		ClientLevel level = client.level;
		if (level == null || client.player == null || client.isPaused()) {
			return;
		}
		int every = switch (client.options.particles().get()) {
			case ALL -> 3;
			case DECREASED -> 6;
			case MINIMAL -> 0;
		};
		if (every == 0) {
			return;
		}
		for (Player player : level.players()) {
			if ((player.tickCount + player.getId()) % every != 0 || !canSmoke(client, player)) {
				continue;
			}
			PlayerCharacter character = CharacterResolver.characterFor(player.getUUID());
			CharacterModel model = character == null ? null : character.model();
			Vec3 tip = model == null ? null : tipPosition(model, player);
			if (tip == null || client.player.distanceToSqr(tip) > MAX_DISTANCE_SQR) {
				continue;
			}
			Particle particle = client.particleEngine.createParticle(ParticleTypes.WHITE_SMOKE, tip.x, tip.y, tip.z,
				RANDOM.nextGaussian() * 0.002, 0.012 + RANDOM.nextDouble() * 0.01, RANDOM.nextGaussian() * 0.002);
			if (particle != null) {
				float scale = player.getScale();
				particle.scale((0.3F + RANDOM.nextFloat() * 0.15F) * scale);
				particle.setLifetime(18 + RANDOM.nextInt(16));
				if (particle instanceof SingleQuadParticle quad) {
					float gray = 0.82F + RANDOM.nextFloat() * 0.12F;
					quad.setColor(gray, gray, gray);
				}
				puffs++;
			}
		}
	}

	private static boolean canSmoke(Minecraft client, Player player) {
		if (player.isInvisible() || player.isSpectator() || player.isEyeInFluid(FluidTags.WATER)) {
			return false;
		}
		Pose pose = player.getPose();
		if (pose != Pose.STANDING && pose != Pose.CROUCHING) {
			return false; // swimming, gliding, sleeping and so on: the head isn't where the math below puts it
		}
		// In first person your own cigarette is right in front of the camera, and its smoke would cover the screen.
		return player != client.player || !client.options.getCameraType().isFirstPerson();
	}

	/** Where the tip of the cigarette is in the world, following the head's turning. */
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
