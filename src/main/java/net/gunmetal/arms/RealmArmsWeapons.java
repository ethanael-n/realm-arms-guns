package net.gunmetal.arms;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.rpg_foundation.ranged_weapon.api.CustomBow;
import net.rpg_foundation.ranged_weapon.api.CustomCrossbow;
import net.rpg_foundation.ranged_weapon.api.RangedWeaponConfig;
import net.gunmetal.arms.advancement.RealmAdvancements;
import net.gunmetal.arms.ammo.ChestLoot;
import net.gunmetal.arms.ammo.RealmArmsAmmo;
import net.gunmetal.arms.entity.DeferredDamage;
import net.gunmetal.arms.guns.GunItem;
import net.gunmetal.arms.legacy.LegacyMusket;
import net.gunmetal.arms.compat.CaptainBanners;
import net.gunmetal.arms.registry.Ignitions;
import net.gunmetal.arms.compat.GuardVillagersCompat;
import net.gunmetal.arms.entity.RealmBulletEntity;
import net.gunmetal.arms.guns.BurstFire;
import net.gunmetal.arms.guns.RealmArmsGuns;
import net.gunmetal.arms.registry.RealmParticles;
import net.gunmetal.arms.registry.RealmSounds;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registers every tiered bow/crossbow for the modpack, on top of Ranged Weapon API.
 *
 * All balance numbers (damage, pull time, velocity) live in {@link #MATERIALS} below -
 * see the README for how these map onto RangedWeaponConfig:
 *   - damage      = bows: the damage of a fully drawn, non-critical, point-blank hit (players' full draws
 *                   always crit, x1.1-1.6). Crossbows: the hit is 0.7 x this (Ranged Weapon API's crossbow
 *                   baseline), x1.1-1.6 for players (always crit).
 *   - pullTicks   = draw time in ticks (20 ticks = 1 second)
 *   - velocityBonus = (this weapon's projectile velocity) - (baseline velocity: 3.0 for bows, 3.15 for crossbows)
 *                     use 0 for weapons that keep the vanilla velocity
 */
public class RealmArmsWeapons implements ModInitializer {
    public static final String MOD_ID = "gunmetal";

    public static final Map<String, Item> BOWS = new LinkedHashMap<>();
    public static final Map<String, Item> CROSSBOWS = new LinkedHashMap<>();

    /**
     * One entry per crafting-material tier.
     *
     * @param key            item id fragment, e.g. "stone" -> gunmetal:stone_bow
     * @param durability     shared by both the bow and the crossbow of this tier
     * @param repairTag      item tag used for anvil/grindstone-style repair (and, for tiers with
     *                       a base recipe, the same tag/item used in the crafting recipe)
     * @param bowDamage      final damage of a fully charged bow shot
     * @param bowPullTicks   bow draw time, in ticks
     * @param bowVelocityBonus bonus over the 3.0 baseline bow velocity (0 = vanilla velocity)
     * @param cbDamage       final damage of a fully charged crossbow shot
     * @param cbPullTicks    crossbow draw time, in ticks
     * @param cbVelocityBonus bonus over the 3.15 baseline crossbow velocity (0 = vanilla velocity)
     * @param hasBaseRecipe  false for netherite, which is only reachable via smithing upgrade
     * @param smithingFrom   non-null only for netherite: the tier it upgrades from
     */
    private record MaterialTier(
            String key,
            int durability,
            TagKey<Item> repairTag,
            float bowDamage, int bowPullTicks, float bowVelocityBonus,
            float cbDamage, int cbPullTicks, float cbVelocityBonus,
            boolean hasBaseRecipe,
            String smithingFrom
    ) {
        MaterialTier(String key, int durability, TagKey<Item> repairTag,
                     float bowDamage, int bowPullTicks, float bowVelocityBonus,
                     float cbDamage, int cbPullTicks, float cbVelocityBonus) {
            this(key, durability, repairTag, bowDamage, bowPullTicks, bowVelocityBonus,
                    cbDamage, cbPullTicks, cbVelocityBonus, true, null);
        }
    }

    private static TagKey<Item> ownTag(String path) {
        return TagKey.create(Registries.ITEM, id(path));
    }

    private static TagKey<Item> commonTag(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", path));
    }

    private static final MaterialTier[] MATERIALS = new MaterialTier[]{
            // Balanced 2026-10-02: damage set from target DPS (average full-draw hit / draw time,
            // full draws crit x1.35 on average). Crossbows: 87.5% of the bow's DPS, 0.25 s to fire
            // counted, charge = 1.25 x bow draw. Targets are 88.2% of the original spec; steel bow
            // matches iron. Steel crossbow is the exception: 4 s charge, 34 damage (slow, heavy shot).
            // Damage is rounded to whole numbers (tooltips show whole numbers).
            // key         durability  repairTag                 bowDmg pull  bowVelBonus  cbDmg pull  cbVelBonus
            new MaterialTier("stone", 515, commonTag("stones"), 11f, 32, 0f, 20f, 40, 0f),
            new MaterialTier("copper", 574, ownTag("copper_repair"), 9f, 20, 0.2f, 16f, 25, 0.15f),
            new MaterialTier("iron", 634, ownTag("iron_repair"), 16f, 32, 0.4f, 29f, 40, 0.3f),
            new MaterialTier("golden", 64, ownTag("golden_repair"), 7f, 14, 0.6f, 14f, 18, 0.45f),
            new MaterialTier("rose_gold", 900, ownTag("rose_gold_repair"), 12f, 20, 0.8f, 23f, 25, 0.6f),
            new MaterialTier("platinum", 1281, ownTag("platinum_repair"), 10f, 14, 1.0f, 21f, 18, 0.75f),
            new MaterialTier("steel", 1000, ownTag("steel_repair"), 21f, 40, 1.2f, 34f, 80, 1.0f),
            new MaterialTier("diamond", 1561, ownTag("diamond_repair"), 14f, 20, 1.4f, 25f, 25, 1.15f),
            new MaterialTier("netherite", 2031, ownTag("netherite_repair"),
                    15f, 20, 1.6f, 29f, 25, 1.3f, false, "diamond"),
    };

    @Override
    public void onInitialize() {
        for (MaterialTier tier : MATERIALS) {
            registerBow(tier);
            registerCrossbow(tier);
        }
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("loaded"), GunItem.LOADED);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("loading_stage"), GunItem.LOADING_STAGE);
        LegacyMusket.register();
        RealmSounds.register();
        RealmParticles.register();
        RealmArmsAmmo.register();
        RealmBulletEntity.register();
        RealmArmsGuns.register();
        net.gunmetal.arms.network.ClipShotPayload.register();
        Ignitions.register();
        BurstFire.register();
        DeferredDamage.register();
        ChestLoot.register();
        GuardVillagersCompat.register();
        CaptainBanners.register();
        RealmAdvancements.register();

        // Slot every custom weapon in right after the vanilla bow/crossbow in the Combat tab.
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(creativeTab -> {
            for (Item item : BOWS.values()) {
                creativeTab.accept(item);
            }
            for (Item item : CROSSBOWS.values()) {
                creativeTab.accept(item);
            }
            for (Item item : RealmArmsGuns.ALL) {
                creativeTab.accept(item);
            }
            for (Item item : RealmArmsAmmo.CARTRIDGES) {
                creativeTab.accept(item);
            }
            for (Item item : Ignitions.IGNITIONS) {
                creativeTab.accept(item);
            }
        });
    }

    private void registerBow(MaterialTier tier) {
        Identifier itemId = id(tier.key() + "_bow");
        Item.Properties settings = new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, itemId))
                .durability(tier.durability())
                .repairable(tier.repairTag());
        Float velocity = tier.bowVelocityBonus() != 0f ? tier.bowVelocityBonus() : null;
        RangedWeaponConfig config = new RangedWeaponConfig(tier.bowDamage(), tier.bowPullTicks(), velocity, null);

        CustomBow bow = new CustomBow(settings, config);
        Registry.register(BuiltInRegistries.ITEM, itemId, bow);
        BOWS.put(tier.key(), bow);
    }

    private void registerCrossbow(MaterialTier tier) {
        Identifier itemId = id(tier.key() + "_crossbow");
        Item.Properties settings = new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, itemId))
                .durability(tier.durability())
                .repairable(tier.repairTag());
        Float velocity = tier.cbVelocityBonus() != 0f ? tier.cbVelocityBonus() : null;
        RangedWeaponConfig config = new RangedWeaponConfig(tier.cbDamage(), tier.cbPullTicks(), velocity, null);

        CustomCrossbow crossbow = new CustomCrossbow(settings, config);
        Registry.register(BuiltInRegistries.ITEM, itemId, crossbow);
        CROSSBOWS.put(tier.key(), crossbow);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
