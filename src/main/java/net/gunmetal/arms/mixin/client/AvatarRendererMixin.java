package net.gunmetal.arms.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Third person: a player holding a loaded Realm Arms (or any other modded) crossbow aims it like a
 * vanilla crossbow. Vanilla only checks for the vanilla crossbow item; Ranged Weapon API patches
 * that check too, but that patch is optional (require = 0) and wasn't taking effect in the pack.
 */
@Mixin(AvatarRenderer.class)
abstract class AvatarRendererMixin {
    @Inject(method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
            at = @At("HEAD"), cancellable = true)
    private static void gunmetal$loadedCrossbowPose(Avatar avatar, ItemStack stack, InteractionHand hand,
                                                     CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
        if (!avatar.swinging && stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
            cir.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_HOLD);
        }
    }
}
