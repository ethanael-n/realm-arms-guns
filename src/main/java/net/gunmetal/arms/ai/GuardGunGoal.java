package net.gunmetal.arms.ai;

import net.gunmetal.arms.guns.GunItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.gunmetal.arms.guns.GunType;
import net.gunmetal.arms.guns.RealmGun;

import java.lang.reflect.Method;
import java.util.EnumSet;

/**
 * Gun AI for Guard Villagers' guards holding a Realm Arms gun. Same tactics as pillagers
 * ({@link GunType#mobTactics()}: range, loading on the move, retreating), plus:
 *  - never fires while a villager, player, iron golem, other guard or tamed pet is in the line
 *    of fire (it waits, then walks to get a clear shot)
 *  - a guard set to patrol holds its post: it loads and fires from where it stands
 *  - pauses while the guard is eating
 * Guards never use up ammo (mobs never do), and don't take gun durability.
 */
public class GuardGunGoal extends Goal {
    // ---- Tuning -------------------------------------------------------------------------
    private static final float RETREAT_FRACTION = 0.6f;
    private static final int MAX_RETREAT_TICKS = 60;
    private static final float FIRE_RANGE_MULTIPLIER = 1.25f;
    /** Guard walking speeds (Guard Villagers' own melee goal uses 0.8). */
    private static final double SPEED = 0.8, SPEED_ENCUMBERED = 0.5, RETREAT_SPEED = 0.8;
    private static final int AIM_TICKS = 20;
    /** Aim error in degrees (same as pillagers). */
    private static final float AIM_SPREAD = 2.0f;
    /**
     * Friendly-fire check: the shot is treated as a cone this much wider than the aim error plus
     * the gun's own spread x SPREAD_MARGIN, and blocked if a friendly's hitbox (grown by
     * CLEARANCE blocks) touches it.
     */
    private static final float SPREAD_MARGIN = 1.5f;
    private static final double CLEARANCE = 0.3;
    /** After this many ticks without a clear shot, the guard moves to find one. */
    private static final int BLOCKED_TICKS_BEFORE_MOVING = 20;
    // -------------------------------------------------------------------------------------

    private final PathfinderMob mob;
    private int seeTime;
    private int attackDelay;
    private int pathDelay;
    private int retreatTicks;
    private boolean retreatExhausted;
    private int blockedTicks;

    public GuardGunGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /** The Realm Arms gun this entity can fire right now, or null. */
    public static RealmGun heldRealmGun(LivingEntity entity) {
        InteractionHand hand = GunItem.getHoldingHand(entity);
        if (hand != null && entity.getItemInHand(hand).getItem() instanceof RealmGun gun) {
            return gun;
        }
        return null;
    }

    /** Eating, drinking or blocking with something other than the gun. */
    private boolean busyWithOtherItem() {
        return mob.isUsingItem() && !(mob.getUseItem().getItem() instanceof GunItem);
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && GunItem.canUse(mob) && heldRealmGun(mob) != null && !busyWithOtherItem();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        mob.setAggressive(true);
    }

    @Override
    public void stop() {
        mob.setAggressive(false);
        if (mob.isUsingItem() && mob.getUseItem().getItem() instanceof GunItem) {
            mob.stopUsingItem();
        }
        mob.getNavigation().stop();
        seeTime = 0;
        attackDelay = 0;
        retreatTicks = 0;
        retreatExhausted = false;
        blockedTicks = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        RealmGun realm = heldRealmGun(mob);
        InteractionHand hand = GunItem.getHoldingHand(mob);
        if (target == null || realm == null || hand == null) {
            return;
        }
        ItemStack stack = mob.getItemInHand(hand);
        GunItem gun = (GunItem) stack.getItem();
        GunType.Tactics tactics = gun.mobTactics();

        boolean loading = mob.isUsingItem();
        if (loading && GunItem.isLoaded(mob.getUseItem())) {
            mob.releaseUsingItem();
            attackDelay = AIM_TICKS + mob.getRandom().nextInt(AIM_TICKS);
            loading = false;
        }
        boolean ready = GunItem.isReady(stack);

        boolean canSee = mob.getSensing().hasLineOfSight(target);
        if (canSee != (seeTime > 0)) {
            seeTime = 0;
        }
        seeTime += canSee ? 1 : -1;
        float dist = mob.distanceTo(target);
        boolean inFireRange = canSee && dist <= tactics.range() * FIRE_RANGE_MULTIPLIER;
        boolean clear = !ready || !inFireRange || clearShot(target, gun);
        blockedTicks = clear ? 0 : blockedTicks + 1;
        boolean patrolling = isPatrolling(mob);
        PathNavigation navigation = mob.getNavigation();

        boolean inRetreatZone = tactics.retreat() && canSee && dist < tactics.range() * RETREAT_FRACTION;
        if (!inRetreatZone) {
            retreatTicks = 0;
            retreatExhausted = false;
        }
        boolean retreating = !patrolling && inRetreatZone && !retreatExhausted;
        boolean approaching = !patrolling && !retreating && attackDelay == 0
                && (dist > tactics.range() || seeTime < 5 || blockedTicks > BLOCKED_TICKS_BEFORE_MOVING);

        if (retreating) {
            if (++retreatTicks > MAX_RETREAT_TICKS) {
                retreatExhausted = true;
            }
            if (--pathDelay <= 0) {
                Vec3 away = DefaultRandomPos.getPosAway(mob, 16, 7, target.position());
                if (away != null) {
                    navigation.moveTo(away.x, away.y, away.z, RETREAT_SPEED);
                } else {
                    navigation.stop();
                }
                pathDelay = 10;
            }
        } else if (approaching) {
            if (--pathDelay <= 0) {
                navigation.moveTo(target, loading || (ready && !tactics.fullSpeedLoaded()) ? SPEED_ENCUMBERED : SPEED);
                pathDelay = 4 + mob.getRandom().nextInt(7);
            }
        } else {
            pathDelay = 0;
            navigation.stop();
        }
        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);

        if (ready) {
            if (attackDelay > 0) {
                --attackDelay;
            } else if (inFireRange && clear) {
                Vec3 direction = GunItem.addUniformSpread(gun.aimAt(mob, target), mob.getRandom(), AIM_SPREAD);
                gun.mobUse(mob, hand, direction);
                if (GunItem.isReady(stack)) {
                    attackDelay = AIM_TICKS + mob.getRandom().nextInt(AIM_TICKS); // clip guns: rounds left
                }
                retreatTicks = 0;
                retreatExhausted = false;
            }
        } else if (!loading && !busyWithOtherItem() && (tactics.loadOnMove() || !(retreating || approaching))) {
            GunItem.mobReload(mob, hand);
        }
    }

    /** False if anyone the guard shouldn't hit is in (or right behind the target in) the line of fire. */
    private boolean clearShot(LivingEntity target, GunItem gun) {
        Vec3 from = mob.getEyePosition();
        Vec3 to = target.getBoundingBox().getCenter();
        Vec3 line = to.subtract(from);
        double length = line.length();
        if (length < 1.0E-3) {
            return true;
        }
        Vec3 dir = line.scale(1.0 / length);
        // A missed shot keeps going, so check a little past the target too.
        Vec3 end = to.add(dir.scale(3.0));
        double coneTan = Math.tan(Math.toRadians(AIM_SPREAD + SPREAD_MARGIN * gun.bulletStdDev()));
        double maxRadius = CLEARANCE + (length + 3.0) * coneTan;
        AABB area = new AABB(from, end).inflate(maxRadius);
        // Explosive guns (hand cannon): also keep clear of friendlies near the target.
        if (gun instanceof RealmGun realm && realm.gunType().impactExplosionRadius() > 0f) {
            double blast = realm.gunType().impactExplosionRadius() * 2.0 + CLEARANCE;
            if (!mob.level().getEntities(mob, target.getBoundingBox().inflate(blast), e -> e != target && isFriendly(e)).isEmpty()) {
                return false;
            }
        }
        for (Entity entity : mob.level().getEntities(mob, area, e -> e != target && isFriendly(e))) {
            double along = entity.getBoundingBox().getCenter().subtract(from).dot(dir);
            if (along < 0) {
                continue;
            }
            double radius = CLEARANCE + along * coneTan;
            if (entity.getBoundingBox().inflate(radius).clip(from, end).isPresent()
                    || entity.getBoundingBox().inflate(radius).contains(from)) {
                return false;
            }
        }
        return true;
    }

    private boolean isFriendly(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
            return false;
        }
        if (entity instanceof Player player) {
            return !player.isSpectator();
        }
        return entity instanceof AbstractVillager
                || entity instanceof IronGolem
                || entity.getType() == mob.getType()
                || (entity instanceof TamableAnimal pet && pet.isTame());
    }

    // Guard Villagers isn't a compile-time dependency, so its patrol flag is read by reflection.
    private static Method isPatrolling;
    private static boolean isPatrollingLookedUp;

    private static boolean isPatrolling(PathfinderMob mob) {
        if (!isPatrollingLookedUp) {
            isPatrollingLookedUp = true;
            try {
                isPatrolling = mob.getClass().getMethod("isPatrolling");
            } catch (ReflectiveOperationException | RuntimeException e) {
                isPatrolling = null;
            }
        }
        if (isPatrolling == null) {
            return false;
        }
        try {
            return (Boolean) isPatrolling.invoke(mob);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
