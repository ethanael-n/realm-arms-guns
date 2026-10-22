package net.gunmetal.arms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.gunmetal.arms.guns.GunConfig;
import net.gunmetal.arms.guns.GunItem;
import net.gunmetal.arms.guns.GunType;
import net.gunmetal.arms.guns.ScopeState;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Client-side gun presentation: the first-person gun (pose, reload motion, melee swing, gun kick)
 * and the third-person aiming arms. Ported from Musket Mod Reloaded's ClientUtilities (MIT), with
 * the Realm Arms tweaks built in: the carbine's bayonet thrust, a forward chop instead of the stock
 * flip for other guns, per-gun reload pull-back, and the gun kick.
 */
public final class GunClient {
    /** Set each frame by MinecraftMixin; used by MultiPlayerGameModeMixin. */
    public static boolean canUseScope;
    public static boolean attackKeyDown;
    /** After a reload finishes while right-click is still held, don't fire until it's released. */
    public static boolean preventFiring;
    private static boolean disableMainHandEquipAnimation;
    private static boolean disableOffhandEquipAnimation;

    private GunClient() {
    }

    public static void setScoping(Player player, boolean scoping) {
        if (scoping != ScopeState.isScoping) {
            player.playSound(scoping ? SoundEvents.SPYGLASS_USE : SoundEvents.SPYGLASS_STOP_USING, 1.0f, 1.0f);
            ScopeState.isScoping = scoping;
        }
        if (!scoping) {
            ScopeState.recoilTicks = 0;
        }
    }

    // ---- Third person --------------------------------------------------------------------

    /** Raises the arm(s) to aim a loaded gun. Returns true if it posed this arm. */
    public static boolean poseArm(HumanoidRenderState state, HumanoidModel<? extends HumanoidRenderState> model, ModelPart arm) {
        if (state.isUsingItem) {
            return false;
        }
        boolean isRight = arm == model.rightArm;
        boolean isMainHand = (state.mainArm == HumanoidArm.RIGHT) == isRight;
        ItemStack stack = isRight ? state.rightHandItemStack : state.leftHandItemStack;
        if (stack.getItem() instanceof GunItem && shouldAim(state, stack, isMainHand)) {
            arm.xRot = model.head.xRot + 0.1f - Mth.HALF_PI;
            if (state.isCrouching) {
                arm.xRot -= 0.4f;
            }
            arm.yRot = model.head.yRot + (isRight ? -0.3f : 0.3f);
            return true;
        }
        // The other arm supports a two-handed gun (or a one-handed one with this hand free).
        ItemStack other = isRight ? state.leftHandItemStack : state.rightHandItemStack;
        if (other.getItem() instanceof GunItem otherGun && shouldAim(state, other, !isMainHand)
                && (otherGun.twoHanded() || stack.isEmpty())) {
            arm.xRot = model.head.xRot - 1.5f;
            if (state.isCrouching) {
                arm.xRot -= 0.4f;
            }
            arm.yRot = model.head.yRot + (isRight ? -0.6f : 0.6f);
            return true;
        }
        return false;
    }

    private static boolean shouldAim(HumanoidRenderState state, ItemStack stack, boolean isMainHand) {
        if (state.isUsingItem || !(stack.getItem() instanceof GunItem gun)) {
            return false;
        }
        if (!isMainHand && gun.twoHanded()) {
            return false;
        }
        ItemStack main = state.mainArm == HumanoidArm.RIGHT ? state.rightHandItemStack : state.leftHandItemStack;
        if (!isMainHand && main.getItem() instanceof GunItem mainGun && mainGun.twoHanded()) {
            return false;
        }
        return GunItem.isLoaded(stack) || GunConfig.ALWAYS_AIM;
    }

    // ---- First person --------------------------------------------------------------------

    public static void renderGunInHand(ItemInHandRenderer renderer, AbstractClientPlayer player, InteractionHand hand, float dt,
                                       float swingProgress, float equipProgress, ItemStack stack, PoseStack poseStack,
                                       SubmitNodeCollector collector, int light) {
        if (player.isScoping()) {
            return;
        }
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        boolean isRightHand = arm == HumanoidArm.RIGHT;
        float sign = isRightHand ? 1.0f : -1.0f;
        ItemDisplayContext context = isRightHand ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        GunItem gun = (GunItem) stack.getItem();
        GunType type = gun.gunType();

        if (!gun.canUseFrom(player, hand)) {
            // A two-handed gun in the offhand is just held low.
            poseStack.pushPose();
            poseStack.translate(sign * 0.5, -0.5 - 0.6 * equipProgress, -0.7);
            poseStack.mulPose(Axis.XP.rotationDegrees(70.0f));
            renderer.renderItem(player, stack, context, poseStack, collector, light);
            poseStack.popPose();
            return;
        }

        ItemStack activeStack = GunItem.getActiveStack(hand);
        if (stack == activeStack) {
            setEquipAnimationDisabled(hand, true);
        } else if (activeStack != null && activeStack.getItem() != gun) {
            setEquipAnimationDisabled(hand, false);
        }

        poseStack.pushPose();
        poseStack.translate(sign * 0.15, -0.25, -0.35);
        if (net.gunmetal.arms.guns.ClipGunItem.showsFanPose(player, stack)) {
            // Fanning the hammer: held lower, from the hip.
            poseStack.translate(0.0, -net.gunmetal.arms.guns.ClipGunItem.FAN_DROP_PIXELS / 16.0, 0.0);
        }
        if (swingProgress > 0.0f) {
            float swingSharp = Mth.sin(Mth.sqrt(swingProgress) * Mth.PI);
            float swingNormal = Mth.sin(swingProgress * Mth.PI);
            if (gun.hasBayonetJab()) {
                // Bayonet thrust.
                poseStack.translate(sign * -0.05 * swingNormal, 0.0, 0.05 - 0.3 * swingSharp);
                poseStack.mulPose(Axis.YP.rotationDegrees(5.0f * swingSharp));
            } else {
                // A forward chop: the muzzle dips down by up to 50 degrees.
                poseStack.translate(sign * 0.05 * (1.0f - swingNormal), 0.05 * (1.0f - swingNormal), 0.05 - 0.4 * swingSharp);
                poseStack.mulPose(Axis.XP.rotationDegrees(-50.0f * swingSharp));
            }
        } else if (player.isUsingItem() && player.getUsedItemHand() == hand) {
            Pair<Integer, Integer> duration = GunItem.getLoadingDuration(stack);
            int stages = duration.getLeft();
            int ticksPerStage = duration.getRight();
            float usingTicks = player.getTicksUsingItem() + dt - 1.0f;
            int stage = GunItem.getLoadingStage(stack) + (int) (usingTicks / ticksPerStage);
            int reloadDuration = GunItem.reloadDuration(stack);
            if (reloadDuration > 0 && usingTicks < reloadDuration + 5) {
                // Tilted up to load, with a small push at each stage.
                poseStack.translate(0.0, -0.3, 0.05);
                poseStack.mulPose(Axis.XP.rotationDegrees(60.0f));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sign * 10.0f));
                float t = 0.0f;
                if (usingTicks >= ticksPerStage && stage <= stages && (usingTicks %= ticksPerStage) < 4.0f) {
                    t = (4.0f - usingTicks) / 4.0f;
                }
                if (usingTicks >= ticksPerStage - 2 && stage < stages) {
                    t = (usingTicks - ticksPerStage + 2.0f) / 2.0f;
                    t = Mth.sin(Mth.HALF_PI * Mth.sqrt(t));
                }
                poseStack.translate(0.0, 0.0, 0.025 * t);
                // Shorter guns are pulled back so they stay in view.
                poseStack.translate(0.0, 0.0, switch (type) {
                    case PISTOL -> -0.12;
                    case BLUNDERBUSS, MUSKETOON -> -0.06;
                    default -> 0.0;
                });
            }
        } else if (isEquipAnimationDisabled(hand)) {
            if (equipProgress == 0.0f) {
                setEquipAnimationDisabled(hand, false);
                GunItem.setActiveStack(hand, null);
            }
        } else {
            poseStack.translate(0.0, -0.6 * equipProgress, 0.0);
        }
        if (player instanceof LocalPlayer local) {
            GunRecoil.applyGunKick(local, hand, dt, poseStack);
        }
        renderer.renderItem(player, stack, context, poseStack, collector, light);
        poseStack.popPose();
    }

    private static boolean isEquipAnimationDisabled(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? disableMainHandEquipAnimation : disableOffhandEquipAnimation;
    }

    private static void setEquipAnimationDisabled(InteractionHand hand, boolean disabled) {
        if (hand == InteractionHand.MAIN_HAND) {
            disableMainHandEquipAnimation = disabled;
        } else {
            disableOffhandEquipAnimation = disabled;
        }
    }
}
