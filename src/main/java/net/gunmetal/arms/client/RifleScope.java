package net.gunmetal.arms.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.gunmetal.arms.guns.GunItem;
import net.gunmetal.arms.guns.RealmRifleItem;

/** When the rifle scope can be used: rifle in the main hand, usable, first-person view. */
public final class RifleScope {
    private RifleScope() {
    }

    public static boolean canUseScope() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        return player != null
                && player.getMainHandItem().getItem() instanceof RealmRifleItem
                && GunItem.canUse(player)
                && client.options.getCameraType().isFirstPerson();
    }
}
