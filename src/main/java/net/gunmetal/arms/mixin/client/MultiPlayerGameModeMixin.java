package net.gunmetal.arms.mixin.client;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.gunmetal.arms.client.GunClient;
import net.gunmetal.arms.guns.GunItem;
import net.gunmetal.arms.guns.ScopeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Right-click with a ready rifle scopes in instead of firing (left-click fires); nothing else can
 * be clicked while scoped; and a gun that just finished loading won't fire until right-click is
 * released and pressed again.
 */
@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void gunmetal$noInteractScoped(Player player, Entity entity, EntityHitResult hit, InteractionHand hand,
                                            CallbackInfoReturnable<InteractionResult> cir) {
        if (ScopeState.isScoping) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void gunmetal$noUseOnScoped(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                         CallbackInfoReturnable<InteractionResult> cir) {
        if (ScopeState.isScoping) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void gunmetal$useItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = player.getItemInHand(hand);
        if (GunClient.canUseScope && hand == InteractionHand.MAIN_HAND && GunItem.isReady(stack)) {
            GunClient.setScoping(player, true);
            if (GunClient.attackKeyDown) {
                return; // left-click while scoped: fire
            }
        }
        if (ScopeState.isScoping) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
        if (stack.getItem() instanceof GunItem gun && GunItem.isReady(stack) && gun.canUseFrom(player, hand)
                && GunClient.preventFiring) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
