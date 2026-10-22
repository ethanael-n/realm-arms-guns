package net.gunmetal.arms.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Quick Charge (or any other charge-time enchantment) can't bring a crossbow's charge below
 * MIN_CHARGE_SECONDS. Crossbows that are already faster than that unenchanted keep their own time.
 * Applies to every crossbow (vanilla's 1.25 s with Quick Charge III is exactly 0.5 s, so it's
 * unchanged); used by both vanilla and Ranged Weapon API's charge-time code.
 */
@Mixin(EnchantmentHelper.class)
abstract class QuickChargeFloorMixin {
    private static final float MIN_CHARGE_SECONDS = 0.5f;

    @Inject(method = "modifyCrossbowChargingTime", at = @At("RETURN"), cancellable = true)
    private static void gunmetal$chargeFloor(ItemStack crossbow, LivingEntity holder, float time,
                                              CallbackInfoReturnable<Float> cir) {
        float floor = Math.min(time, MIN_CHARGE_SECONDS);
        if (cir.getReturnValue() < floor) {
            cir.setReturnValue(floor);
        }
    }
}
