package net.gunmetal.arms.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.gunmetal.arms.advancement.RealmAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks a pillager the first time it targets anything, for the Intruder Alert advancement. */
@Mixin(Mob.class)
abstract class MobMixin {
    @Inject(method = "setTarget", at = @At("HEAD"))
    private void gunmetal$markAggro(LivingEntity target, CallbackInfo ci) {
        if (target != null && (Object) this instanceof Pillager pillager) {
            pillager.addTag(RealmAdvancements.AGGROED_TAG);
        }
    }
}
