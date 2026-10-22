package net.gunmetal.arms.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import net.gunmetal.arms.guns.ScopeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rifle scope: no sprinting, walk slowly (as if using an item), and the view kicks up when firing. */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
    @Unique
    private boolean gunmetal$insideAiStep;

    @Inject(method = "canStartSprinting", at = @At("HEAD"), cancellable = true)
    private void gunmetal$noSprintScoped(CallbackInfoReturnable<Boolean> cir) {
        if (ScopeState.isScoping) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void gunmetal$aiStepHead(CallbackInfo ci) {
        gunmetal$insideAiStep = true;
    }

    @Inject(method = "isUsingItem", at = @At("HEAD"), cancellable = true)
    private void gunmetal$slowWhileScoped(CallbackInfoReturnable<Boolean> cir) {
        if (gunmetal$insideAiStep && ScopeState.isScoping) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void gunmetal$aiStepTail(CallbackInfo ci) {
        gunmetal$insideAiStep = false;
        if (ScopeState.recoilTicks > 0) {
            --ScopeState.recoilTicks;
        }
    }

    @Inject(method = "getViewXRot", at = @At("HEAD"), cancellable = true)
    private void gunmetal$scopeRecoil(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (ScopeState.isScoping && ScopeState.recoilTicks > 0) {
            LocalPlayer player = (LocalPlayer) (Object) this;
            cir.setReturnValue(player.getXRot() - (ScopeState.recoilTicks - partialTick) * ScopeState.RECOIL_AMOUNT);
        }
    }
}
