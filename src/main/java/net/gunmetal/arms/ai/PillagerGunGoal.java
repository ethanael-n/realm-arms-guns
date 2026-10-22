package net.gunmetal.arms.ai;

import net.gunmetal.arms.guns.GunItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import net.gunmetal.arms.guns.GunType;
import net.gunmetal.arms.guns.CustomGunner;
import org.jetbrains.annotations.Nullable;

/**
 * Pillager AI for Realm Arms guns (added to every pillager by PillagerMixin, above the vanilla
 * crossbow goal). Per-gun ranges and behavior come from {@link GunType#mobTactics()}.
 */
public class PillagerGunGoal<T extends PathfinderMob> extends RangedGunAttackGoal<T> {
    // ---- Tuning -------------------------------------------------------------------------
    /** Retreating guns back off when a target is closer than this share of their range. */
    private static final float RETREAT_FRACTION = 0.6f;
    /** They give up retreating after this many ticks and stand to shoot (until they fire again). */
    private static final int MAX_RETREAT_TICKS = 60;
    /** They only fire at targets within range x this (stops the long cleanup shots). */
    private static final float FIRE_RANGE_MULTIPLIER = 1.25f;
    /** Movement speeds: normal, and while carrying a loaded gun or loading. */
    private static final double SPEED = 1.0, SPEED_ENCUMBERED = 0.5, RETREAT_SPEED = 1.0;
    /** Pause after the gun becomes ready before the first shot, in ticks (plus up to the same again at random). */
    private static final int AIM_TICKS = 20;
    /** Pillager aim error, in degrees (guards use 2). */
    private static final float AIM_SPREAD = 3.0f;
    // -------------------------------------------------------------------------------------

    private int seeTime;
    private int attackDelay;
    private int pathDelay;
    private int retreatTicks;
    private boolean retreatExhausted;

    /** Extra ticks of aiming before every shot (add-on mobs that fire slower than a pillager). */
    private final int extraAimTicks;

    public PillagerGunGoal(T mob) {
        this(mob, 0);
    }

    public PillagerGunGoal(T mob, int extraAimTicks) {
        super(mob);
        this.extraAimTicks = extraAimTicks;
    }

    private @Nullable GunType.Tactics tactics() {
        InteractionHand hand = GunItem.getHoldingHand(mob);
        if (hand != null && mob.getItemInHand(hand).getItem() instanceof GunItem gun) {
            return gun.mobTactics();
        }
        return null;
    }

    @Override
    public boolean canUse() {
        return super.canUse() && tactics() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        super.tick(); // releases the use button once loaded -> onReady()
        LivingEntity target = mob.getTarget();
        GunType.Tactics tactics = tactics();
        if (target == null || tactics == null) {
            return;
        }

        boolean canSee = mob.getSensing().hasLineOfSight(target);
        if (canSee != (seeTime > 0)) {
            seeTime = 0;
        }
        seeTime += canSee ? 1 : -1;

        float dist = mob.distanceTo(target);
        boolean ready = isReady();
        boolean loading = isLoading();
        PathNavigation navigation = mob.getNavigation();

        boolean inRetreatZone = tactics.retreat() && canSee && dist < tactics.range() * RETREAT_FRACTION;
        if (!inRetreatZone) {
            retreatTicks = 0;
            retreatExhausted = false;
        }
        boolean retreating = inRetreatZone && !retreatExhausted;
        boolean approaching = !retreating && (dist > tactics.range() || seeTime < 5) && attackDelay == 0;

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
            } else if (canSee && dist <= tactics.range() * FIRE_RANGE_MULTIPLIER) {
                fire(AIM_SPREAD);
                if (isReady()) {
                    attackDelay = shotDelay(); // clip guns: rounds left
                }
                retreatTicks = 0;
                retreatExhausted = false;
            }
        } else if (!loading && (tactics.loadOnMove() || !(retreating || approaching))) {
            reload();
        }
    }

    @Override
    public void onReady() {
        attackDelay = shotDelay();
    }

    private int shotDelay() {
        InteractionHand hand = GunItem.getHoldingHand(mob);
        if (hand != null && mob instanceof CustomGunner custom) {
            int delay = custom.mobShotDelay(mob.getItemInHand(hand));
            if (delay >= 0) {
                return delay;
            }
        }
        return AIM_TICKS + mob.getRandom().nextInt(AIM_TICKS) + extraAimTicks;
    }

    @Override
    public void stop() {
        super.stop();
        seeTime = 0;
        attackDelay = 0;
        retreatTicks = 0;
        retreatExhausted = false;
    }
}
