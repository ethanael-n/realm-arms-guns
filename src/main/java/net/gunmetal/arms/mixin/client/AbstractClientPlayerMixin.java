package net.gunmetal.arms.mixin.client;

import net.minecraft.client.player.AbstractClientPlayer;
import net.gunmetal.arms.guns.GunConfig;
import net.gunmetal.arms.guns.ScopeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rifle scope zoom. */
@Mixin(AbstractClientPlayer.class)
abstract class AbstractClientPlayerMixin {
    @Inject(method = "getFieldOfViewModifier", at = @At("HEAD"), cancellable = true)
    private void gunmetal$scopeZoom(CallbackInfoReturnable<Float> cir) {
        if (ScopeState.isScoping) {
            cir.setReturnValue(1.0f / GunConfig.SCOPE_ZOOM);
        }
    }
}
