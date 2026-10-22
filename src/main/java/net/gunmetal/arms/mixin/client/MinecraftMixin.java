package net.gunmetal.arms.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.gunmetal.arms.client.GunClient;
import net.gunmetal.arms.client.RifleScope;
import net.gunmetal.arms.guns.GunItem;
import net.gunmetal.arms.guns.ScopeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Input handling for guns:
 *  - a finished reload lets go of right-click by itself (so holding it doesn't fire immediately)
 *  - rifle scope: holding right-click on a loaded rifle scopes in; left-click fires while scoped
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Shadow
    protected abstract void startUseItem();

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void gunmetal$fireWhileScoped(CallbackInfoReturnable<Boolean> cir) {
        if (ScopeState.isScoping) {
            startUseItem();
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void gunmetal$noMiningWhileScoped(CallbackInfo ci) {
        if (ScopeState.isScoping) {
            ci.cancel();
        }
    }

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void gunmetal$handleGunKeys(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        boolean canUseScope = RifleScope.canUseScope();
        if (player.isUsingItem()) {
            ItemStack used = player.getUseItem();
            int delay = canUseScope ? 10 : 5;
            if (used.getItem() instanceof GunItem && GunItem.isLoaded(used)
                    && player.getTicksUsingItem() >= GunItem.reloadDuration(used) + delay) {
                GunClient.preventFiring = true;
                client.gameMode.releaseUsingItem(player);
            }
        }
        boolean canContinueScoping = client.options.keyUse.isDown()
                && (GunItem.isReady(stack) || client.options.keyAttack.isDown());
        if (!canUseScope || !canContinueScoping) {
            GunClient.setScoping(player, false);
        }
        GunClient.canUseScope = canUseScope;
        GunClient.attackKeyDown = client.options.keyAttack.isDown();
        if (!client.options.keyUse.isDown()) {
            GunClient.preventFiring = false;
        }
    }
}
