package net.gunmetal.raids.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.gunmetal.arms.guns.GunItem;
import net.gunmetal.raids.entity.Sharpshooter;
import net.gunmetal.raids.registry.RaidParticles;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * Sharpshooter: closes in when the target is over 70 blocks away or out of view. Inside 40-70 it holds
 * (never backs off); between shots it keeps 12 blocks from other Bombardiers/Sharpshooters and every
 * few seconds looks for higher ground. Each shot:
 *   glint (4 s)     - stands still, keeps tracking the target for the first 2.5 s
 *   windup (1.5 s)  - the aim locks on where the target was, the windup particle shows
 *   shot            - fired at the locked spot, even if the target has moved or ducked out of view
 * No aim error at any range. A smoke teleport drops the glint (it aims again once visible); a reload carries on.
 */
public class SharpshooterAttackGoal extends Goal {
    /** How often it looks for higher ground (ticks), how many spots it tries, and how far around itself. */
    private static final int VANTAGE_SEARCH_INTERVAL = 100;
    private static final int VANTAGE_CANDIDATES = 12;
    private static final int VANTAGE_RADIUS = 10;
    /** A spot has to be at least this many blocks higher than where it stands to be worth the walk. */
    private static final int VANTAGE_MIN_GAIN = 2;
    /** Stays within this distance of the raid centre (raiders over 112 blocks away drop out of the raid). */
    private static final double RAID_LEASH = 100.0;
    /** How often (ticks) it checks its spacing while free to move. */
    private static final int SPACING_CHECK_INTERVAL = 10;
    /** The glint shows this far in front of its eyes, along its aim; the windup shows at its eyes (its head). */
    private static final double GLINT_FORWARD = 1.6, WINDUP_FORWARD = 0.0;

    private final Sharpshooter mob;
    private int cooldown;
    private int glintTicks;
    /** Where it is aiming: follows the target until the windup, then stays put. */
    private @Nullable Vec3 aim;
    private boolean aimLocked;
    private int aimTargetId = -1;
    private int pathDelay;
    private int vantageTimer;
    private int spacingTimer;
    private boolean moving;
    /** True during the cooldown after a shot (model state "reloading"). */
    private boolean reloading;

    public SharpshooterAttackGoal(Sharpshooter mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private @Nullable ItemStack heldGun() {
        ItemStack stack = mob.getMainHandItem();
        return stack.getItem() instanceof GunItem ? stack : null;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && heldGun() != null;
    }

    @Override
    public boolean canContinueToUse() {
        // Keeps running through the reload even when the target is gone (it stays put until it's done).
        return heldGun() != null && (canUse() || glintTicks > 0 || reloading);
    }

    @Override
    public void start() {
        mob.setAggressive(true);
        cooldown = 20 + mob.getRandom().nextInt(40);
        glintTicks = 0;
        aim = null;
        aimLocked = false;
        pathDelay = 0;
        vantageTimer = 20;
        spacingTimer = 0;
        moving = false;
        reloading = false;
        mob.setGoalAnimState(Sharpshooter.STATE_ACQUIRED);
    }

    @Override
    public void stop() {
        mob.setAggressive(false);
        mob.getNavigation().stop();
        glintTicks = 0;
        aim = null;
        aimLocked = false;
        mob.setAiming(false);
        reloading = false;
        mob.setGoalAnimState(Sharpshooter.STATE_IDLE);
        ItemStack gun = heldGun();
        if (gun != null) {
            GunItem.setLoaded(gun, false);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ItemStack gun = heldGun();
        if (gun == null) {
            return;
        }
        LivingEntity target = mob.getTarget();
        if (mob.consumeAimInterrupt() && glintTicks > 0) {
            glintTicks = 0;
            aim = null;
            aimLocked = false;
            mob.setAiming(false);
            mob.setGoalAnimState(Sharpshooter.STATE_ACQUIRED);
            cooldown = 0;
        }
        if (glintTicks > 0) {
            tickGlint(gun, target);
            return;
        }
        if (reloading) {
            // Reloading: stays put (target or not) until the cooldown is over.
            mob.getNavigation().stop();
            moving = false;
            if (target != null) {
                mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
            }
            if (cooldown > 0) {
                --cooldown;
                return;
            }
            reloading = false;
            mob.setGoalAnimState(Sharpshooter.STATE_ACQUIRED);
        }
        if (target == null || !target.isAlive()) {
            return;
        }
        double dist = mob.distanceTo(target);
        boolean canSee = mob.getSensing().hasLineOfSight(target);
        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
        move(target, dist, canSee);

        if (cooldown > 0) {
            --cooldown;
        } else if (canSee && dist <= Sharpshooter.MAX_FIRE_RANGE && !mob.isStealthed() && GunItem.canUse(mob)) {
            startGlint(gun, target);
        }
        // Between shots: walks in the idle pose (not the aiming tilt), aims again once it stops.
        if (!reloading && glintTicks <= 0) {
            mob.setGoalAnimState(mob.getNavigation().isDone() ? Sharpshooter.STATE_ACQUIRED : Sharpshooter.STATE_IDLE);
        }
    }

    private void tickGlint(ItemStack gun, @Nullable LivingEntity target) {
        mob.getNavigation().stop();
        if (!aimLocked && target != null && target.isAlive()) {
            aim = new Vec3(target.getX(), target.getY(0.5), target.getZ());
            aimTargetId = target.getId();
        }
        if (aim != null) {
            mob.getLookControl().setLookAt(aim.x, aim.y, aim.z, 30.0f, 30.0f);
        }
        --glintTicks;
        if (glintTicks == Sharpshooter.WINDUP_TICKS && !aimLocked) {
            aimLocked = true;
            mob.setGoalAnimState(Sharpshooter.STATE_LOCKED);
            if (aim != null) {
                Vec3 eye = mob.getEyePosition();
                sendParticle(RaidParticles.SCOPE_WINDUP, eye.add(aim.subtract(eye).normalize().scale(WINDUP_FORWARD)));
            }
        }
        if (glintTicks <= 0) {
            mob.setAiming(false);
            if (aim != null) {
                fire(gun, aim);
            }
            aim = null;
            aimLocked = false;
            reloading = true;
            mob.setGoalAnimState(Sharpshooter.STATE_RELOADING);
            cooldown = Sharpshooter.COOLDOWN_MIN
                    + mob.getRandom().nextInt(Sharpshooter.COOLDOWN_MAX - Sharpshooter.COOLDOWN_MIN + 1);
        }
    }

    private void move(LivingEntity target, double dist, boolean canSee) {
        if (dist > Sharpshooter.RANGE_MAX || !canSee) {
            moving = false;
            if (--pathDelay <= 0) {
                mob.getNavigation().moveTo(target, 1.0);
                pathDelay = 10 + mob.getRandom().nextInt(6);
            }
            return;
        }
        if (moving && !mob.getNavigation().isDone()) {
            return;
        }
        moving = false;
        mob.getNavigation().stop();
        // Spacing first: step away from other backliners that are too close.
        if (--spacingTimer <= 0) {
            spacingTimer = SPACING_CHECK_INTERVAL;
            List<Mob> crowding = Spacing.crowding(mob, Sharpshooter.SPACING);
            if (!crowding.isEmpty()) {
                BlockPos spot = Spacing.findSpot(mob, target, crowding, Sharpshooter.SPACING,
                        Sharpshooter.RANGE_MIN, Sharpshooter.RANGE_MAX);
                if (spot != null && mob.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0)) {
                    moving = true;
                    return;
                }
            }
        }
        if (--vantageTimer <= 0) {
            vantageTimer = VANTAGE_SEARCH_INTERVAL;
            BlockPos spot = findVantage(target);
            if (spot != null && mob.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0)) {
                moving = true;
            }
        }
    }

    /** The highest of a few random nearby surface spots that is higher than here, in the band, in view of the target. */
    private @Nullable BlockPos findVantage(LivingEntity target) {
        ServerLevel level = (ServerLevel) mob.level();
        RandomSource random = mob.getRandom();
        Raid raid = mob.getCurrentRaid();
        int baseY = mob.getBlockY();
        Vec3 targetEye = target.getEyePosition();
        BlockPos best = null;
        for (int i = 0; i < VANTAGE_CANDIDATES; ++i) {
            int x = mob.getBlockX() + random.nextInt(VANTAGE_RADIUS * 2 + 1) - VANTAGE_RADIUS;
            int z = mob.getBlockZ() + random.nextInt(VANTAGE_RADIUS * 2 + 1) - VANTAGE_RADIUS;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y < baseY + VANTAGE_MIN_GAIN || (best != null && y <= best.getY())) {
                continue;
            }
            BlockPos pos = new BlockPos(x, y, z);
            double d = Math.sqrt(pos.distToCenterSqr(target.getX(), target.getY(), target.getZ()));
            if (d < Sharpshooter.RANGE_MIN || d > Sharpshooter.RANGE_MAX) {
                continue;
            }
            if (raid != null && Math.sqrt(raid.getCenter().distSqr(pos)) > RAID_LEASH) {
                continue;
            }
            if (!Spacing.crowding(mob, Sharpshooter.SPACING).isEmpty()) {
                // Don't climb into a crowd; spacing is handled first anyway.
                break;
            }
            Vec3 eye = new Vec3(x + 0.5, y + mob.getEyeHeight(), z + 0.5);
            if (level.clip(new ClipContext(eye, targetEye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob))
                    .getType() != HitResult.Type.MISS) {
                continue;
            }
            best = pos;
        }
        return best;
    }

    private void startGlint(ItemStack gun, LivingEntity target) {
        aim = new Vec3(target.getX(), target.getY(0.5), target.getZ());
        aimTargetId = target.getId();
        aimLocked = false;
        glintTicks = Sharpshooter.GLINT_TICKS;
        mob.setGoalAnimState(Sharpshooter.STATE_ACQUIRED);
        mob.getNavigation().stop();
        moving = false;
        GunItem.setLoaded(gun, true);
        mob.setAiming(true);
        Vec3 eye = mob.getEyePosition();
        sendParticle(RaidParticles.SCOPE_GLINT, eye.add(aim.subtract(eye).normalize().scale(GLINT_FORWARD)));
    }

    /** Forced, so it shows at any distance; the client ties it to the nearest Sharpshooter (it vanishes if that dies). */
    private void sendParticle(net.minecraft.core.particles.SimpleParticleType type, Vec3 pos) {
        ((ServerLevel) mob.level()).sendParticles(type, true, true, pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private void fire(ItemStack gun, Vec3 target) {
        GunItem item = (GunItem) gun.getItem();
        Vec3 origin = new Vec3(mob.getX(), mob.getEyeY(), mob.getZ());
        // 1.0.3: its bullets have no drop (Sharpshooter#customizeBullet), so it aims straight at the spot.
        Vec3 direction = target.subtract(origin).normalize();
        mob.prepareShot(direction, aimTargetId);
        GunItem.setLoaded(gun, true);
        item.mobUse(mob, InteractionHand.MAIN_HAND, direction);
        mob.swing(InteractionHand.MAIN_HAND); // swing_progress in models = recoil
        mob.markShot();
    }
}
