package net.gunmetal.arms.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.gunmetal.arms.guns.ClipGunItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The clip guns (revolver, repeater) are unrepairable: two can't be combined in the crafting grid either. */
@Mixin(RepairItemRecipe.class)
abstract class RepairItemRecipeMixin {
    @Inject(method = "canCombine", at = @At("HEAD"), cancellable = true)
    private static void gunmetal$noClipGunRepair(ItemStack first, ItemStack second, CallbackInfoReturnable<Boolean> cir) {
        if (first.getItem() instanceof ClipGunItem || second.getItem() instanceof ClipGunItem) {
            cir.setReturnValue(false);
        }
    }
}
