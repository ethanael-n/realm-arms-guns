package net.gunmetal.raids.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.util.RandomSource;
import net.gunmetal.raids.registry.RaidParticles;

/**
 * Client only: the Artificer's summons give off the same electric sparks as its pulse warning, at
 * the same rate (3 every 2 ticks) times a multiplier, so they can be told apart from other mobs.
 * Spawned by each player's own game: no network traffic.
 */
public final class SummonSparks {
    /** Sparks per tick at multiplier 1 (= the Artificer's pulse warning). */
    public static final float BASE_PER_TICK = 1.5f;
    private float owed;

    public void tick(Entity entity, float multiplier) {
        owed += BASE_PER_TICK * multiplier;
        RandomSource random = entity.getRandom();
        double spreadXZ = entity.getBbWidth() * 0.6, spreadY = entity.getBbHeight() * 0.4;
        while (owed >= 1f) {
            owed -= 1f;
            entity.level().addParticle(RaidParticles.ARTIFICER_SPARK,
                    entity.getX() + random.nextGaussian() * spreadXZ,
                    entity.getY(0.6) + random.nextGaussian() * spreadY,
                    entity.getZ() + random.nextGaussian() * spreadXZ,
                    random.nextGaussian() * 0.05, random.nextGaussian() * 0.05, random.nextGaussian() * 0.05);
        }
    }
}
