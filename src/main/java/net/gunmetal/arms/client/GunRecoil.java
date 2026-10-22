package net.gunmetal.arms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.gunmetal.arms.guns.GunType;
import net.gunmetal.arms.guns.RealmGun;

/**
 * Client-only gun kick for the local player: the first-person gun model snaps back and tips up
 * when firing, then settles. The camera itself is never moved (no camera kick, no screen shake).
 * The rifle's scope recoil (ScopeState) still applies while scoped.
 */
public final class GunRecoil {
    // ---- Tuning -------------------------------------------------------------------------
    /** Gun model pushed back toward the camera (blocks), per strength (LIGHT, MEDIUM, HEAVY). */
    private static final float[] GUN_BACK = {0.105f, 0.21f, 0.35f};
    /** Gun model muzzle tipped up (degrees), per strength. */
    private static final float[] GUN_TILT = {17.5f, 35f, 56f};
    /** How quickly the gun settles; larger = slower (ticks). */
    private static final float GUN_SETTLE_TICKS = 3.6f;
    private static final float GUN_KICK_MAX_TICKS = 20f;
    // -------------------------------------------------------------------------------------

    /** Remaining follow-up shots of a burst gun; each one re-kicks the gun on its tick. */
    private static int burstShotsLeft;
    /** The end of the tick the player fired in comes before the server's second shot; skip it. */
    private static boolean burstSkipNextTick;
    private static InteractionHand burstHand = InteractionHand.MAIN_HAND;

    private static final int[] kickTick = {Integer.MIN_VALUE, Integer.MIN_VALUE};
    private static final GunType.Kick[] kickStrength = {GunType.Kick.LIGHT, GunType.Kick.LIGHT};
    private static final float[] kickScale = {1f, 1f};

    private GunRecoil() {
    }

    /** The local player just fired. */
    public static void onFire(GunType type, InteractionHand hand) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        int shots = Math.max(1, type.burstShots());
        if (shots > 1) {
            burstShotsLeft = (shots - 1) * GunType.BURST_INTERVAL_TICKS;
            burstHand = hand;
            burstSkipNextTick = true;
        }
        kickTick[hand.ordinal()] = player.tickCount;
        kickStrength[hand.ordinal()] = type.gunKick();
        kickScale[hand.ordinal()] = type.gunKickScale();
    }

    /** End of every client tick: re-kicks the gun for each follow-up shot of a burst. */
    public static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) {
            burstShotsLeft = 0;
            return;
        }
        if (burstShotsLeft > 0 && burstSkipNextTick) {
            burstSkipNextTick = false;
        } else if (burstShotsLeft > 0) {
            burstShotsLeft--;
            if (burstShotsLeft % GunType.BURST_INTERVAL_TICKS == 0) {
                kickTick[burstHand.ordinal()] = player.tickCount;
            }
        }
    }

    /** Applied to the first-person gun just before it's drawn. */
    public static void applyGunKick(LocalPlayer player, InteractionHand hand, float partialTick, PoseStack poseStack) {
        int start = kickTick[hand.ordinal()];
        if (start == Integer.MIN_VALUE) {
            return;
        }
        float elapsed = Math.max(0f, player.tickCount - start + partialTick - 1f);
        if (elapsed > GUN_KICK_MAX_TICKS) {
            kickTick[hand.ordinal()] = Integer.MIN_VALUE;
            return;
        }
        float amount = (float) Math.exp(-elapsed / GUN_SETTLE_TICKS) * kickScale[hand.ordinal()];
        int s = kickStrength[hand.ordinal()].ordinal();
        poseStack.translate(0.0, 0.0, GUN_BACK[s] * amount);
        poseStack.mulPose(Axis.XP.rotationDegrees(GUN_TILT[s] * amount));
    }
}
