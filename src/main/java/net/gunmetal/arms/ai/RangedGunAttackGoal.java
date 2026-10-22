package net.gunmetal.arms.ai;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.gunmetal.arms.guns.GunItem;

import java.util.EnumSet;

/**
 * Base for mob gun AI: keeps the gun loaded and fires at the target. Subclasses decide movement
 * and when to reload/fire. (Ported from Musket Mod Reloaded's RangedGunAttackGoal, MIT.)
 */
public class RangedGunAttackGoal<T extends Mob> extends Goal {
    public final T mob;

    public RangedGunAttackGoal(T mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return isTargetValid() && canUseGun();
    }

    public boolean isTargetValid() {
        return mob.getTarget() != null && mob.getTarget().isAlive();
    }

    public boolean canUseGun() {
        return GunItem.isHoldingGun(mob) && GunItem.canUse(mob);
    }

    public boolean isReady() {
        InteractionHand hand = GunItem.getHoldingHand(mob);
        return hand != null && GunItem.isReady(mob.getItemInHand(hand));
    }

    public boolean isLoading() {
        return mob.isUsingItem();
    }

    public void onReady() {
    }

    public void reload() {
        InteractionHand hand = GunItem.getHoldingHand(mob);
        if (hand != null && !isLoading() && !GunItem.isLoaded(mob.getItemInHand(hand))) {
            GunItem.mobReload(mob, hand);
        }
    }

    public void fire(float spread) {
        InteractionHand hand = GunItem.getHoldingHand(mob);
        if (hand == null) {
            return;
        }
        ItemStack stack = mob.getItemInHand(hand);
        GunItem gun = (GunItem) stack.getItem();
        Vec3 direction = gun.aimAt(mob, mob.getTarget());
        if (spread > 0.0f) {
            direction = GunItem.addUniformSpread(direction, mob.getRandom(), spread);
        }
        gun.mobUse(mob, hand, direction);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /** Lets go of the use button once the gun has finished loading. */
    @Override
    public void tick() {
        if (isLoading() && GunItem.isLoaded(mob.getUseItem())) {
            mob.releaseUsingItem();
            onReady();
        }
    }

    @Override
    public void stop() {
        super.stop();
        mob.setAggressive(false);
        mob.setTarget(null);
        if (mob.isUsingItem()) {
            mob.stopUsingItem();
        }
    }
}
