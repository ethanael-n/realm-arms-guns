package net.gunmetal.arms.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.gunmetal.arms.client.GunClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Third person: players and humanoid mobs (pillagers, guards) raise their arms to aim a loaded
 * gun. The arm pose is borrowed as "spyglass" during posing so vanilla doesn't override it, then
 * restored.
 */
@Mixin(HumanoidModel.class)
abstract class HumanoidModelMixin {
    @Unique
    private HumanoidRenderState gunmetal$state;
    @Unique
    private HumanoidModel.ArmPose gunmetal$leftArmPose;
    @Unique
    private HumanoidModel.ArmPose gunmetal$rightArmPose;

    @Inject(method = "setupAnim", at = @At("HEAD"))
    private void gunmetal$setupAnimHead(HumanoidRenderState state, CallbackInfo ci) {
        gunmetal$state = state;
        gunmetal$leftArmPose = state.leftArmPose;
        gunmetal$rightArmPose = state.rightArmPose;
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "poseRightArm", at = @At("TAIL"))
    private void gunmetal$poseRightArm(HumanoidRenderState state, CallbackInfo ci) {
        HumanoidModel<HumanoidRenderState> model = (HumanoidModel<HumanoidRenderState>) (Object) this;
        if (GunClient.poseArm(state, model, model.rightArm)) {
            state.rightArmPose = HumanoidModel.ArmPose.SPYGLASS;
        }
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "poseLeftArm", at = @At("TAIL"))
    private void gunmetal$poseLeftArm(HumanoidRenderState state, CallbackInfo ci) {
        HumanoidModel<HumanoidRenderState> model = (HumanoidModel<HumanoidRenderState>) (Object) this;
        if (GunClient.poseArm(state, model, model.leftArm)) {
            state.leftArmPose = HumanoidModel.ArmPose.SPYGLASS;
        }
    }

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void gunmetal$setupAnimTail(CallbackInfo ci) {
        if (gunmetal$state != null) {
            gunmetal$state.rightArmPose = gunmetal$rightArmPose;
            gunmetal$state.leftArmPose = gunmetal$leftArmPose;
        }
    }
}
