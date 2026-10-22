package net.gunmetal.arms.guns;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import static net.gunmetal.arms.RealmArmsWeapons.MOD_ID;

/**
 * Gun materials. Each tier step is +20% damage and -0.1 degrees of spread
 * (see {@link GunType#damageFor} and {@link GunType#spreadFor}).
 */
public enum GunTier {
    //      id        durability  repair tag (the tier's ignition)
    COPPER("copper", 190, "copper_gun_repair"),
    BRASS("brass", 906, "brass_gun_repair"),
    STEEL("steel", 1796, "steel_gun_repair");

    public final String id;
    public final int durability;
    public final TagKey<Item> repairTag;

    GunTier(String id, int durability, String repairTag) {
        this.id = id;
        this.durability = durability;
        this.repairTag = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, repairTag));
    }
}
