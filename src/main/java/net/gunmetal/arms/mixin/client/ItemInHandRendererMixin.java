package net.gunmetal.arms.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.gunmetal.arms.client.GunClient;
import net.gunmetal.arms.guns.GunItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws guns in first person with GunClient instead of the normal held-item pose. */
@Mixin(ItemInHandRenderer.class)
abstract class ItemInHandRendererMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void gunmetal$renderGun(AbstractClientPlayer player, float tickDelta, float pitch, InteractionHand hand, float swingProgress,
                                     ItemStack stack, float equipProgress, PoseStack poseStack, SubmitNodeCollector collector,
                                     int light, CallbackInfo ci) {
        if (stack.getItem() instanceof GunItem) {
            GunClient.renderGunInHand((ItemInHandRenderer) (Object) this, player, hand, tickDelta, swingProgress, equipProgress,
                    stack, poseStack, collector, light);
            ci.cancel();
        }
    }
}
