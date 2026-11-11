package com.playercharacters.effects;

import com.playercharacters.CharacterResolver;
import com.playercharacters.PlayerCharactersClient;
import com.playercharacters.character.CharacterModel;
import com.playercharacters.character.CharacterModel.Bone;
import com.playercharacters.character.CharacterModel.Emitter;
import com.playercharacters.character.PlayerCharacter;
import com.playercharacters.emote.EmotePlayer;
import com.playercharacters.render.CharacterPoses;
import com.playercharacters.render.TailMotion;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Particles that keep coming out of some characters (see "particles" in character.json): flames on the tips of the
 * wolf's tails, mist at its paws when it runs, sparks from its eyes. Runs on the client thread.
 */
public final class CharacterParticles {
	private static final float PLAYER_SCALE = 0.9375F;
	private static final float FEET_Y = 1.501F;
	private static final double MAX_DISTANCE_SQR = 48.0 * 48.0;
	private static final RandomSource RANDOM = RandomSource.create();
	private static final Map<String, @Nullable ParticleOptions> TYPES = new HashMap<>();
	private static final Set<String> WARNED = new HashSet<>();
	private static int spawned;

	private CharacterParticles() {
	}

	/** How many particles characters gave off since the game started. */
	public static int spawnedCount() {
		return spawned;
	}

	/** Called every client tick. */
	public static void tick(Minecraft client) {
		ClientLevel level = client.level;
		if (level == null || client.player == null || client.isPaused()) {
			return;
		}
		int thin = switch (client.options.particles().get()) {
			case ALL -> 1;
			case DECREASED -> 2;
			case MINIMAL -> 4;
		};
		for (Player player : level.players()) {
			if (player.isInvisible() || player.isSpectator() || client.player.distanceToSqr(player) > MAX_DISTANCE_SQR) {
				continue;
			}
			PlayerCharacter character = CharacterResolver.characterFor(player.getUUID());
			CharacterModel model = character == null ? null : character.model();
			if (model == null || model.extras().particles().isEmpty()) {
				continue;
			}
			boolean firstPerson = player == client.player && client.options.getCameraType().isFirstPerson();
			double speed = player.getDeltaMovement().horizontalDistance();
			for (int i = 0; i < model.extras().particles().size(); i++) {
				Emitter emitter = model.extras().particles().get(i);
				int every = emitter.every() * thin;
				if ((player.tickCount + player.getId() * 3 + i * 7) % every != 0 || !shows(emitter, player, speed)) {
					continue;
				}
				if (firstPerson && emitter.attachedTo() == Bone.HEAD) {
					continue; // right in front of the camera
				}
				ParticleOptions options = options(emitter);
				if (options == null) {
					continue;
				}
				Vec3 at = position(model, emitter, player);
				Vec3 velocity = toWorld(emitter.velocity(), player);
				float spread = emitter.spread() * player.getScale();
				level.addParticle(options, at.x + RANDOM.nextGaussian() * spread, at.y + RANDOM.nextGaussian() * spread,
					at.z + RANDOM.nextGaussian() * spread, velocity.x, velocity.y, velocity.z);
				spawned++;
			}
		}
	}

	private static boolean shows(Emitter emitter, Player player, double speed) {
		Pose pose = player.getPose();
		if (pose != Pose.STANDING && pose != Pose.CROUCHING) {
			return false; // swimming, gliding, sleeping: the math below doesn't know where the parts are
		}
		return switch (emitter.when()) {
			case ALWAYS -> true;
			case MOVING -> speed > 0.03;
			case SPRINTING -> player.isSprinting() && speed > 0.05;
			case STANDING_STILL -> speed < 0.01;
		};
	}

	/** The particle to make, or null (and a warning in the log once) if the id is unknown or needs settings this can't give. */
	private static @Nullable ParticleOptions options(Emitter emitter) {
		String key = emitter.particle() + "#" + emitter.color() + "#" + emitter.size();
		if (TYPES.containsKey(key)) {
			return TYPES.get(key);
		}
		ParticleOptions options = null;
		Identifier id = Identifier.tryParse(emitter.particle());
		ParticleType<?> type = id == null ? null : BuiltInRegistries.PARTICLE_TYPE.getValue(id);
		if (id != null && id.getPath().equals("dust") && id.getNamespace().equals("minecraft")) {
			options = new DustParticleOptions(emitter.color(), emitter.size());
		} else if (type instanceof SimpleParticleType simple) {
			options = simple;
		} else if (WARNED.add(emitter.particle())) {
			PlayerCharactersClient.LOGGER.warn("Character particle \"{}\" isn't a particle this mod can make", emitter.particle());
		}
		TYPES.put(key, options);
		return options;
	}

	/** Where an emitter is in the world right now: following the head's turning, or the tip of a swaying tail. */
	static Vec3 position(CharacterModel model, Emitter emitter, Player player) {
		Vector3f point = new Vector3f(emitter.at());
		float emote = EmotePlayer.playing(player.getUUID()) != null ? 1.0F : 0.0F;
		if (emitter.tail() >= 0 && emitter.tail() < model.extras().tails().size()) {
			float seconds = (player.tickCount + (player.getId() & 255)) / 20.0F;
			float lift = model.extras().fourLegs() ? CharacterPoses.REAR_UP * 0.85F * emote : 0.0F;
			TailMotion.of(seconds, player.walkAnimation.speed(), emote, lift).bend(model.extras().tails().get(emitter.tail()), point, new Vector3f());
		}
		if (model.extras().fourLegs() && emote > 0.0F && emitter.attachedTo() == Bone.BODY) {
			// Standing up on the back legs for the emote: the body turns up around the hips (the head has its own turn below).
			Vector3f hip = new Vector3f(model.hip());
			new Quaternionf().rotationX(-CharacterPoses.REAR_UP).transform(point.sub(hip)).add(hip);
		}
		Vector3f body = bodySpace(point);
		if (emitter.attachedTo() == Bone.HEAD || emitter.attachedTo() == Bone.JAW) {
			CharacterModel.Part head = model.part(Bone.HEAD) != null ? model.part(Bone.HEAD) : model.part(Bone.JAW);
			Vector3f joint = bodySpace(head != null ? head.pivot() : new Vector3f());
			float headTurn = model.extras().headTurn();
			float yaw = Mth.wrapDegrees(player.getYHeadRot() - player.yBodyRot) * Mth.DEG_TO_RAD * headTurn;
			float pitch = player.getXRot() * Mth.DEG_TO_RAD * headTurn;
			new Quaternionf().rotationY(-yaw).rotateX(pitch).transform(body.sub(joint)).add(joint);
		}
		float scale = player.getScale();
		double x = body.x * scale;
		double y = body.y * scale - (player.isCrouching() ? 0.125 * scale : 0.0);
		double z = body.z * scale;
		double yaw = Math.toRadians(player.yBodyRot);
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		return new Vec3(player.getX() + x * cos - z * sin, player.getY() + y, player.getZ() + x * sin + z * cos);
	}

	/** A velocity in the character's frame (x left, y up, z forward) turned to face where the body faces. */
	private static Vec3 toWorld(Vector3f velocity, Player player) {
		double yaw = Math.toRadians(player.yBodyRot);
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		return new Vec3(velocity.x * cos - velocity.z * sin, velocity.y, velocity.x * sin + velocity.z * cos);
	}

	/** Model space (Y down, front -Z) to blocks from the feet: x toward the character's left, y up, z forward. */
	private static Vector3f bodySpace(Vector3f model) {
		return new Vector3f(model.x * PLAYER_SCALE, (FEET_Y - model.y) * PLAYER_SCALE, -model.z * PLAYER_SCALE);
	}
}
