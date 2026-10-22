package net.gunmetal.raids.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.Level;

/**
 * The Siege Balloon's crossbow arrow: a plain arrow that can't be picked up, always deals the same
 * damage (4 before difficulty: 6 to a player on Hard; half to raiders, ravagers included), and is gone
 * 5 s after it lands (instead of vanilla's 60 s). Drawn as a normal arrow, with a firework-rocket
 * spark left behind every tick while it flies.
 */
public class BalloonArrow extends Arrow {
    /** Ticks it stays stuck in a block. */
    public static final int STUCK_LIFETIME = 100;
    /** One trail spark every this many ticks in flight. */
    public static final int TRAIL_INTERVAL = 1;
    private int stuckTicks;
    /** This tick's base damage (damage / speed). */
    private double base;

    public BalloonArrow(EntityType<? extends Arrow> type, Level level) {
        super(type, level);
    }

    /**
     * Keeps the damage flat at SiegeBalloon.ARROW_DAMAGE whatever its speed (vanilla multiplies the base
     * damage by the arrow's speed and rounds up; a balloon shooting down would otherwise hit harder).
     */
    @Override
    public void tick() {
        if (level().isClientSide() && !isInGround() && tickCount % TRAIL_INTERVAL == 0) {
            // Trail (client only): the plain firework spark, left behind with no speed of its own, so it
            // hangs, slowly sinks and fades (vanilla's 2.4-3 s), like /particle minecraft:firework.
            level().addParticle(net.minecraft.core.particles.ParticleTypes.FIREWORK, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
        }
        double speed = getDeltaMovement().length();
        if (speed > 0.05) {
            base = (SiegeBalloon.ARROW_DAMAGE - 0.01) / speed;
            setBaseDamage(base);
        }
        super.tick();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (result.getEntity() instanceof Raider && base > 0.0) {
            setBaseDamage(base * SiegeBalloon.ARROW_RAIDER_DAMAGE);
        }
        super.onHitEntity(result);
    }

    @Override
    protected void tickDespawn() {
        if (++stuckTicks >= STUCK_LIFETIME) {
            discard();
        }
    }
}
