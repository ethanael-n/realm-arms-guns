package net.gunmetal.raids.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.gunmetal.raids.registry.RaidParticles;

/**
 * Shared effects for the Artificer's summons (drones, brass golems, Puppeted).
 *
 * Debris ("construct bits"): tumbling pieces thrown out sideways that fall, bounce and vanish after
 * 1.5 s, on direct hits (something struck it: no fire, poison or similar ticks) and on death. How
 * many is set per mob and per event (the DEBRIS_* constants in each mob).
 * Spawned by each player's own game from the hit/death events the game already sends: no network cost.
 *
 * Delayed upgrade sound: an upgrade's sound plays 0, 3, 6 ... 21 ticks late (random), so a whole
 * group upgrading at once doesn't play one loud sound.
 */
public final class SummonFx {
    // ---- Tuning -------------------------------------------------------------------------
    /** Sideways launch speed and upward kick (blocks/tick), random between. */
    public static final double DEBRIS_SPEED_MIN = 0.12, DEBRIS_SPEED_MAX = 0.28, DEBRIS_UP_MIN = 0.12, DEBRIS_UP_MAX = 0.3;
    /** Upgrade sound delay: a random multiple of this many ticks, up to the max. */
    public static final int SOUND_DELAY_STEP = 3, SOUND_DELAY_MAX = 21;
    /** At the max level an upgrade gives Regeneration I for this long instead (drones and golems). */
    public static final int MAX_LEVEL_REGEN_TICKS = 100;
    /** Custom entity event: "spray debris" (for a golem that blows up instead of dying). */
    public static final byte DEBRIS_EVENT = 101;
    // -------------------------------------------------------------------------------------

    private SummonFx() {
    }

    /** Client: debris from a hit (min-max pieces), if it was a direct hit. */
    public static void onDamageEvent(Entity entity, DamageSource source, int min, int max) {
        if (entity.level().isClientSide() && source.getDirectEntity() != null) {
            spawnDebris(entity, min, max);
        }
    }

    /** Client: min-max pieces (random, both included) from around the middle of the entity. */
    public static void spawnDebris(Entity entity, int min, int max) {
        RandomSource random = entity.getRandom();
        int count = min + random.nextInt(Math.max(0, max - min) + 1);
        for (int i = 0; i < count; ++i) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double speed = DEBRIS_SPEED_MIN + random.nextDouble() * (DEBRIS_SPEED_MAX - DEBRIS_SPEED_MIN);
            double up = DEBRIS_UP_MIN + random.nextDouble() * (DEBRIS_UP_MAX - DEBRIS_UP_MIN);
            entity.level().addParticle(RaidParticles.CONSTRUCT_BITS,
                    entity.getX() + (random.nextDouble() - 0.5) * entity.getBbWidth(),
                    entity.getY(0.3 + random.nextDouble() * 0.4),
                    entity.getZ() + (random.nextDouble() - 0.5) * entity.getBbWidth(),
                    Math.cos(angle) * speed, up, Math.sin(angle) * speed);
        }
    }

    /**
     * Client: a burst of count pieces of this particle from all over the entity's box, thrown out
     * sideways (speedScale x the normal debris speed; upward kick unscaled).
     */
    public static void spawnDebrisBurst(Entity entity, net.minecraft.core.particles.ParticleOptions particle, int count, double speedScale) {
        spawnDebrisBurst(entity, particle, count, speedScale, 1.0);
    }

    /** As above, with the upward kick scaled too. */
    public static void spawnDebrisBurst(Entity entity, net.minecraft.core.particles.ParticleOptions particle, int count,
                                        double speedScale, double upScale) {
        RandomSource random = entity.getRandom();
        for (int i = 0; i < count; ++i) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double speed = (DEBRIS_SPEED_MIN + random.nextDouble() * (DEBRIS_SPEED_MAX - DEBRIS_SPEED_MIN)) * speedScale;
            double up = (DEBRIS_UP_MIN + random.nextDouble() * (DEBRIS_UP_MAX - DEBRIS_UP_MIN)) * upScale;
            entity.level().addParticle(particle,
                    entity.getX() + (random.nextDouble() - 0.5) * entity.getBbWidth(),
                    entity.getY(random.nextDouble()),
                    entity.getZ() + (random.nextDouble() - 0.5) * entity.getBbWidth(),
                    Math.cos(angle) * speed, up, Math.sin(angle) * speed);
        }
    }

    /** Server: max-level upgrade reward for drones and golems. */
    public static void maxLevelRegen(LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, MAX_LEVEL_REGEN_TICKS, 0));
    }

    /** A sound played a little late (counts down in the owner's server tick). */
    public static final class DelayedSound {
        private int ticksLeft = -1;
        private SoundEvent sound;

        /** Starts the countdown (a random 0-21 ticks in steps of 3). */
        public void schedule(SoundEvent sound, RandomSource random) {
            this.sound = sound;
            this.ticksLeft = SOUND_DELAY_STEP * random.nextInt(SOUND_DELAY_MAX / SOUND_DELAY_STEP + 1);
        }

        public void tick(LivingEntity entity) {
            if (ticksLeft < 0 || !(entity.level() instanceof ServerLevel level)) {
                return;
            }
            if (ticksLeft-- == 0 && sound != null && entity.isAlive()) {
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound, entity.getSoundSource(), 1.0f,
                        0.9f + entity.getRandom().nextFloat() * 0.2f);
            }
        }
    }
}
