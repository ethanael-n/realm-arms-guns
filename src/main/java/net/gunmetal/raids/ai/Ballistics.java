package net.gunmetal.raids.ai;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Aiming for Realm Arms bullets, which fly like arrows: each tick they move by their velocity, then
 * the velocity is multiplied by 0.99 (air drag) and gravity is subtracted (0.05 x (1 - drop reduction)).
 * Finds the launch angle that lands on a point by simulating the flight for a range of angles
 * (a few thousand cheap steps; only run when a shot is fired).
 */
public final class Ballistics {
    private static final double DRAG = 0.99;
    private static final int MAX_TICKS = 300;

    private Ballistics() {
    }

    /**
     * @param speed   blocks per tick
     * @param gravity blocks per tick^2
     * @param highArc true = the steep solution (45-85 degrees, lobbing over cover); false = the flat one (-60-45)
     * @return unit launch direction, or null if no angle gets within 3 blocks of the point
     */
    public static @Nullable Vec3 solve(Vec3 from, Vec3 to, double speed, double gravity, boolean highArc) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double height = to.y - from.y;
        if (horizontal < 0.5) {
            return new Vec3(dx, height, dz).normalize();
        }
        double bestError = Double.MAX_VALUE;
        double bestAngle = Double.NaN;
        double min = highArc ? 45.0 : -60.0;
        double max = highArc ? 85.0 : 45.0;
        for (double degrees = min; degrees <= max; degrees += 0.5) {
            double landed = landingDistance(Math.toRadians(degrees), speed, gravity, height, !highArc);
            if (Double.isNaN(landed)) {
                continue;
            }
            double error = Math.abs(landed - horizontal);
            if (error < bestError) {
                bestError = error;
                bestAngle = degrees;
            }
        }
        if (Double.isNaN(bestAngle) || bestError > 3.0) {
            return null;
        }
        double radians = Math.toRadians(bestAngle);
        double cos = Math.cos(radians);
        return new Vec3(dx / horizontal * cos, Math.sin(radians), dz / horizontal * cos);
    }

    /**
     * Horizontal distance at which the shot reaches the given height, or NaN if it never does.
     * @param firstCrossing true = the first time it passes that height either way (flat shots at a
     *                      target above still rising); false = only on the way down (lobs)
     */
    private static double landingDistance(double angle, double speed, double gravity, double height, boolean firstCrossing) {
        double vx = speed * Math.cos(angle);
        double vy = speed * Math.sin(angle);
        double x = 0.0;
        double y = 0.0;
        for (int tick = 0; tick < MAX_TICKS; ++tick) {
            double px = x;
            double py = y;
            x += vx;
            y += vy;
            vx *= DRAG;
            vy = vy * DRAG - gravity;
            boolean down = py >= height && y < height;
            boolean up = firstCrossing && py < height && y >= height;
            if (down || up) {
                double t = (py - height) / (py - y);
                return px + (x - px) * t;
            }
            if (vy < 0.0 && y < height - 64.0) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    /**
     * Furthest a shot can land (on level ground) at the steep angles, for the given speed. Useful to
     * check what a speed can reach; not used at runtime.
     */
    public static double maxHighArcRange(double speed, double gravity) {
        double best = 0.0;
        for (double degrees = 45.0; degrees <= 85.0; degrees += 0.5) {
            double landed = landingDistance(Math.toRadians(degrees), speed, gravity, 0.0, false);
            if (!Double.isNaN(landed)) {
                best = Math.max(best, landed);
            }
        }
        return best;
    }
}
