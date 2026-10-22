package net.gunmetal.raids.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.gunmetal.arms.entity.BulletPayload;
import net.gunmetal.arms.entity.GunBullet;
import net.gunmetal.raids.registry.RaidParticles;
import net.gunmetal.raids.registry.RaidSounds;

/**
 * The Trooper's smoke round (Trooper-only; no item). On impact, or when it runs out after 1 s, it
 * bursts into a smoke cloud: 25 big puffs thrown outward across the horizontal plane that settle and
 * linger ~6 s (SmokeCloudParticle). A direct hit deals no damage but blinds the target for 3 s.
 * Cost: 25 particle packets per burst to nearby players; nothing on the server afterwards.
 */
public final class SmokeRound implements BulletPayload {
    public static final SmokeRound INSTANCE = new SmokeRound();

    // ---- Tuning -------------------------------------------------------------------------
    public static final int PUFFS = 25;
    /** Outward speed of the puffs (blocks per tick), random between these. */
    public static final double PUFF_SPEED_MIN = 0.35, PUFF_SPEED_MAX = 0.6;
    public static final int BLINDNESS_TICKS = 60;
    // -------------------------------------------------------------------------------------

    private SmokeRound() {
    }

    @Override
    public boolean onHitEntity(GunBullet bullet, Entity target) {
        if (target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLINDNESS_TICKS), bullet.getOwner());
        }
        return true; // no damage
    }

    @Override
    public void onImpact(GunBullet bullet, Vec3 pos) {
        burst(bullet, pos);
    }

    @Override
    public void onExpire(GunBullet bullet, Vec3 pos) {
        burst(bullet, pos);
    }

    private static void burst(GunBullet bullet, Vec3 pos) {
        if (bullet.level() instanceof ServerLevel level) {
            burstAt(level, pos);
        }
    }

    /** The smoke round's cloud and sound at a spot (also the Sharpshooter's smoke teleport). */
    public static void burstAt(ServerLevel level, Vec3 pos) {
        level.playSound(null, pos.x, pos.y, pos.z, RaidSounds.TROOPER_SMOKE, SoundSource.HOSTILE, 1.5f,
                0.8f + level.getRandom().nextFloat() * 0.2f);
        double start = level.getRandom().nextDouble() * Math.PI * 2.0;
        for (int i = 0; i < PUFFS; ++i) {
            double angle = start + Math.PI * 2.0 * i / PUFFS + (level.getRandom().nextDouble() - 0.5) * 0.2;
            double speed = PUFF_SPEED_MIN + level.getRandom().nextDouble() * (PUFF_SPEED_MAX - PUFF_SPEED_MIN);
            // Count 0 = the offsets are a direction and the last value its speed (one particle each).
            level.sendParticles(RaidParticles.SMOKE_CLOUD, true, true, pos.x, pos.y + 0.3, pos.z, 0,
                    Math.cos(angle), 0.02, Math.sin(angle), speed);
        }
    }
}
