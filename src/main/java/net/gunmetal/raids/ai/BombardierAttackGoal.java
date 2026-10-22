package net.gunmetal.raids.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.gunmetal.arms.guns.GunItem;
import net.gunmetal.raids.entity.Bombardier;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * Bombardier: walks toward its target until {@link Bombardier#IDEAL_RANGE} (never backs off). Between
 * shots it keeps {@link Bombardier#SPACING} blocks from other Bombardiers and Sharpshooters; for the
 * last 3 s before each shot it stands still ("loading"). Close and in view it fires straight (the hand
 * cannon's normal spread); otherwise it lobs at a random point within its current miss margin of
 * where the target stands. Reload, miss margin and burst size ramp up with every shot (see Bombardier).
 */
public class BombardierAttackGoal extends Goal {
    /** How often (ticks) it checks its spacing while free to move. */
    private static final int SPACING_CHECK_INTERVAL = 10;

    private final Bombardier mob;
    private int pathDelay;
    private int spacingTimer;
    private boolean repositioning;

    public BombardierAttackGoal(Bombardier mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && heldGun() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        mob.setAggressive(true);
        if (mob.reloadTicks() <= 0) {
            mob.setReloadTicks(Bombardier.FIRST_SHOT_DELAY_MIN
                    + mob.getRandom().nextInt(Bombardier.FIRST_SHOT_DELAY_MAX - Bombardier.FIRST_SHOT_DELAY_MIN + 1));
        }
        pathDelay = 0;
        spacingTimer = 0;
        repositioning = false;
    }

    @Override
    public void stop() {
        mob.setAggressive(false);
        mob.setGoalAnimState(Bombardier.STATE_IDLE);
        mob.getNavigation().stop();
        repositioning = false;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private @Nullable ItemStack heldGun() {
        ItemStack stack = mob.getMainHandItem();
        return stack.getItem() instanceof GunItem ? stack : null;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        ItemStack gun = heldGun();
        if (target == null || gun == null) {
            return;
        }
        double dist = mob.distanceTo(target);
        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
        boolean inRange = dist <= Bombardier.MAX_FIRE_RANGE;
        boolean loading = inRange && mob.reloadTicks() <= Bombardier.LOADING_STANCE_TICKS;
        mob.setGoalAnimState(!loading ? Bombardier.STATE_RELOADING
                : willFireDirect(target, dist) ? Bombardier.STATE_AIM_DIRECT : Bombardier.STATE_AIM_LOB);

        if (loading) {
            mob.getNavigation().stop();
            repositioning = false;
        } else if (dist > Bombardier.IDEAL_RANGE) {
            repositioning = false;
            if (--pathDelay <= 0) {
                mob.getNavigation().moveTo(target, 1.0);
                pathDelay = 10 + mob.getRandom().nextInt(6);
            }
        } else {
            keepSpacing(target);
        }

        if (mob.reloadTicks() > 0) {
            mob.setReloadTicks(mob.reloadTicks() - 1);
            return;
        }
        if (!inRange || !GunItem.canUse(mob)) {
            return;
        }
        fire(target, gun, dist);
    }

    /** Close and in view: a straight shot; otherwise a lob. */
    private boolean willFireDirect(LivingEntity target, double dist) {
        return dist <= Bombardier.DIRECT_FIRE_RANGE && mob.getSensing().hasLineOfSight(target);
    }

    /** Between shots, inside its range: step away from other backliners closer than its spacing. */
    private void keepSpacing(LivingEntity target) {
        if (repositioning && !mob.getNavigation().isDone()) {
            return;
        }
        repositioning = false;
        mob.getNavigation().stop();
        if (--spacingTimer > 0) {
            return;
        }
        spacingTimer = SPACING_CHECK_INTERVAL;
        List<Mob> crowding = Spacing.crowding(mob, Bombardier.SPACING);
        if (crowding.isEmpty()) {
            return;
        }
        BlockPos spot = Spacing.findSpot(mob, target, crowding, Bombardier.SPACING, 0.0, Bombardier.IDEAL_RANGE);
        if (spot != null && mob.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0)) {
            repositioning = true;
        }
    }

    private void fire(LivingEntity target, ItemStack gun, double dist) {
        GunItem item = (GunItem) gun.getItem();
        // Fired from beside its head, MUZZLE_RIGHT blocks to its right (seen along the line to the target).
        Vec3 eye = new Vec3(mob.getX(), mob.getEyeY(), mob.getZ());
        Vec3 toTarget = new Vec3(target.getX() - eye.x, 0.0, target.getZ() - eye.z);
        Vec3 right = toTarget.lengthSqr() < 1.0E-6
                ? Vec3.directionFromRotation(0.0f, mob.getYRot() + 90.0f)
                : toTarget.normalize().cross(new Vec3(0.0, 1.0, 0.0));
        Vec3 shift = right.scale(Bombardier.MUZZLE_RIGHT);
        // Against a wall on its right: fire from the eyes as before, so the shell doesn't start inside the wall.
        if (mob.level().clip(new net.minecraft.world.level.ClipContext(eye, eye.add(right.scale(Bombardier.MUZZLE_RIGHT + 0.2)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mob))
                .getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            shift = Vec3.ZERO;
        }
        Vec3 origin = eye.add(shift);
        double speed = Bombardier.SHELL_SPEED / 20.0;
        double gravity = 0.05;
        boolean direct = willFireDirect(target, dist);

        Vec3 direction;
        Vec3 exact;
        if (direct) {
            Vec3 aim = new Vec3(target.getX(), target.getY(0.5), target.getZ());
            Vec3 solved = Ballistics.solve(origin, aim, speed, gravity, false);
            direction = solved != null ? solved : aim.subtract(origin).normalize();
            exact = null; // keep the hand cannon's own spread on straight shots
        } else {
            double angle = mob.getRandom().nextDouble() * Math.PI * 2.0;
            double radius = mob.missMarginNow() * Math.sqrt(mob.getRandom().nextDouble());
            Vec3 aim = new Vec3(target.getX() + Math.cos(angle) * radius, target.getY(), target.getZ() + Math.sin(angle) * radius);
            Vec3 solved = Ballistics.solve(origin, aim, speed, gravity, true);
            if (solved == null) {
                // Out of reach of a steep arc: 45 degrees toward it (its furthest shot).
                Vec3 flat = new Vec3(aim.x - origin.x, 0.0, aim.z - origin.z).normalize();
                solved = new Vec3(flat.x * Math.sqrt(0.5), Math.sqrt(0.5), flat.z * Math.sqrt(0.5));
            }
            direction = solved;
            exact = solved;
        }

        mob.prepareShot(exact, origin);
        GunItem.setLoaded(gun, true);
        Vec3 smoke = item.smokeOffsetFor(mob, mob.getMainArm()).add(shift);
        item.mobUse(mob, gun, direction, smoke);
        mob.swing(InteractionHand.MAIN_HAND); // swing_progress in models = recoil
        mob.onShotFired();
    }
}
