package net.gunmetal.arms.mixin.client;

import net.minecraft.world.entity.player.Player;
import net.gunmetal.arms.guns.ScopeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Counts the rifle scope as scoping (vanilla then hides the hand and draws the spyglass overlay). */
@Mixin(Player.class)
abstract class PlayerMixin {
    @Inject(method = "isScoping", at = @At("HEAD"), cancellable = true)
    private void gunmetal$rifleScoping(CallbackInfoReturnable<Boolean> cir) {
        if (ScopeState.isScoping) {
            cir.setReturnValue(true);
        }
    }
}
