package net.gunmetal.raids.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.gunmetal.raids.entity.Bombardier;
import net.gunmetal.raids.entity.Sharpshooter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Keeps backline illagers (Bombardiers and Sharpshooters) spread out: finds the others too close to
 * one of them, and a nearby spot that restores the spacing while staying in its range band.
 * Cost: one small entity query per check (every half second) plus a dozen heightmap lookups when a
 * move is needed.
 */
public final class Spacing {
    private static final int CANDIDATES = 12;
    /** Stay this close to the raid centre (raiders over 112 blocks away drop out of the raid). */
    private static final double RAID_LEASH = 100.0;
    /** Candidate spots may be at most this much higher / lower than where it stands. */
    private static final int MAX_RISE = 6, MAX_DROP = 4;

    private Spacing() {
    }

    /** Other Bombardiers and Sharpshooters within the given distance. */
    public static List<Mob> crowding(Mob mob, double spacing) {
        double spacingSqr = spacing * spacing;
        return mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(spacing),
                other -> other != mob && other.isAlive() && (other instanceof Bombardier || other instanceof Sharpshooter)
                        && other.distanceToSqr(mob) < spacingSqr);
    }

    /**
     * A surface spot a few blocks away, roughly away from the crowding mobs, that is at least
     * {@code spacing} from each of them and between {@code minRange} and {@code maxRange} from the
     * target. Null if none of the tried spots work (it then just stays put and tries again later).
     */
    public static @Nullable BlockPos findSpot(Mob mob, LivingEntity target, List<Mob> crowding, double spacing,
                                              double minRange, double maxRange) {
        Vec3 away = Vec3.ZERO;
        for (Mob other : crowding) {
            away = away.add(mob.position().subtract(other.position()));
        }
        double baseAngle = away.horizontalDistanceSqr() < 1.0E-4
                ? mob.getRandom().nextDouble() * Math.PI * 2.0
                : Math.atan2(away.z, away.x);
        Raid raid = mob instanceof Raider raider ? raider.getCurrentRaid() : null;
        int baseY = mob.getBlockY();
        for (int i = 0; i < CANDIDATES; ++i) {
            double angle = baseAngle + (mob.getRandom().nextDouble() - 0.5) * Math.toRadians(200);
            double step = 3.0 + mob.getRandom().nextDouble() * spacing;
            int x = (int) Math.floor(mob.getX() + Math.cos(angle) * step);
            int z = (int) Math.floor(mob.getZ() + Math.sin(angle) * step);
            int y = mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y > baseY + MAX_RISE || y < baseY - MAX_DROP) {
                continue;
            }
            BlockPos pos = new BlockPos(x, y, z);
            Vec3 at = Vec3.atBottomCenterOf(pos);
            double toTarget = at.distanceTo(target.position());
            if (toTarget < minRange || toTarget > maxRange) {
                continue;
            }
            if (raid != null && Math.sqrt(raid.getCenter().distSqr(pos)) > RAID_LEASH) {
                continue;
            }
            boolean clear = true;
            for (Mob other : crowding) {
                if (other.position().distanceTo(at) < spacing) {
                    clear = false;
                    break;
                }
            }
            if (clear) {
                return pos;
            }
        }
        return null;
    }
}
