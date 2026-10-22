package net.gunmetal.arms.guns;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.gunmetal.arms.entity.RealmBulletEntity;
import org.jetbrains.annotations.Nullable;

/**
 * For add-on mobs (Realm Raids) that fire Realm Arms guns their own way. Implemented by the shooting
 * entity; checked when it fires a gun through {@link GunItem#mobUse}.
 */
public interface CustomGunner {
    /**
     * Server: adjust each bullet just before it spawns (velocity, damage, lifetime, burst radius,
     * knockback, visuals, gunType...). Runs after the gun has set everything up the normal way.
     */
    default void customizeBullet(RealmBulletEntity bullet, ItemStack gun) {
    }

    /** Fire sound to play instead of the gun's own, or null for the gun's. */
    default @Nullable SoundEvent gunFireSound(ItemStack gun) {
        return null;
    }

    /** Bullets per shot instead of the gun's own pellet count (0 or less = the gun's). */
    default int pelletCount(ItemStack gun) {
        return 0;
    }

    /** Spread (degrees, standard deviation) for each bullet instead of the gun's own (negative = the gun's). */
    default float spreadOverride(ItemStack gun) {
        return -1f;
    }

    /**
     * Ticks a mob aims before each shot with this gun (after loading, and between the rounds of a
     * clip gun), instead of the AI's normal delay. Negative = the normal delay.
     */
    default int mobShotDelay(ItemStack gun) {
        return -1;
    }

    /**
     * Clip guns (revolver, repeater): whether the mob is fanning the hammer with this gun right now
     * (its shots land anywhere in the gun's fan cone instead of the normal spread).
     */
    default boolean isFanning(ItemStack gun) {
        return false;
    }

    /** Fire sound volume (mob guns normally 3.5, heard out to about 56 blocks). */
    default float gunFireVolume() {
        return 3.5f;
    }
}
