package net.gunmetal.arms.guns;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static net.gunmetal.arms.RealmArmsWeapons.MOD_ID;

/** Registers every gun in {@link GunType} for each of its tiers. */
public final class RealmArmsGuns {
    private static final Logger LOGGER = LoggerFactory.getLogger("gunmetal");

    /** Registration order, used for the creative tab. */
    public static final List<Item> ALL = new ArrayList<>();

    private RealmArmsGuns() {
    }

    public static void register() {
        for (GunType type : GunType.values()) {
            for (GunTier tier : type.tiers()) {
                Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, type.itemId(tier));
                Item.Properties properties = new Item.Properties()
                        .setId(ResourceKey.create(Registries.ITEM, id))
                        .durability(type.durabilityFor(tier));
                if (type.isMaintainable()) {
                    properties = properties.repairable(tier.repairTag);
                }
                if (type == GunType.CARBINE) {
                    properties = properties.attributes(RealmGunItem.bayonetAttributes(
                            GunType.CARBINE_JAB_DAMAGE[tier.ordinal()], GunType.CARBINE_JAB_SPEED));
                    properties = withSpearJab(properties);
                } else if (type == GunType.MUSKETOON) {
                    properties = properties.attributes(RealmGunItem.bayonetAttributes(
                            GunType.MUSKETOON_MELEE_DAMAGE[tier.ordinal()], GunType.MUSKETOON_MELEE_SPEED));
                }
                properties = withUseEffects(properties, type.moveSpeed);

                Item item = type == GunType.RIFLE
                        ? new RealmRifleItem(properties, type, tier)
                        : new RealmGunItem(properties, type, tier);
                Registry.register(BuiltInRegistries.ITEM, id, item);
                ALL.add(item);
            }
        }
        registerClipGuns();
    }

    /** Revolver and repeater: untiered, unrepairable (no repair material, not enchantable), no bayonet. */
    private static void registerClipGuns() {
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "clip_rounds"),
                ClipGunItem.CLIP_ROUNDS);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "last_shot"),
                ClipGunItem.LAST_SHOT);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "fanning"),
                ClipGunItem.FANNING);
        ClipGunItem.registerFanTicks();
        for (ClipGunItem.Kind kind : ClipGunItem.Kind.values()) {
            Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, kind.id);
            Item.Properties properties = new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id))
                    .durability(ClipGunItem.DURABILITY);
            properties = withUseEffects(properties, kind.type.moveSpeed);
            Item item = new ClipGunItem(properties, kind);
            Registry.register(BuiltInRegistries.ITEM, id, item);
            ALL.add(item);
        }
    }

    /**
     * Movement while loading, via the vanilla minecraft:use_effects component (the same one the
     * old datapack used). Built from JSON through the component's own codec so it doesn't depend
     * on the Java class layout. If it ever fails to parse, guns still work at vanilla speed.
     */
    private static Item.Properties withUseEffects(Item.Properties properties, float speed) {
        JsonObject json = new JsonObject();
        json.addProperty("can_sprint", true);
        json.addProperty("speed_multiplier", speed);
        json.addProperty("interact_vibrations", true);
        try {
            return withComponent(properties, Identifier.withDefaultNamespace("use_effects"), json);
        } catch (RuntimeException e) {
            LOGGER.warn("Couldn't apply minecraft:use_effects to a gun; it will use default movement speed", e);
            return properties;
        }
    }

    /**
     * The vanilla spear's left-click jab, using the same components (and values) as the vanilla
     * spears in 26.1.2, minus the charge attack. Each part is optional: if one fails to parse, the
     * carbine just keeps a normal melee swing for that part and a warning is logged.
     */
    private static Item.Properties withSpearJab(Item.Properties properties) {
        JsonObject range = new JsonObject();
        range.addProperty("min_reach", 2.0);
        range.addProperty("max_reach", 4.5);
        range.addProperty("min_creative_reach", 2.0);
        range.addProperty("max_creative_reach", 6.5);
        range.addProperty("hitbox_margin", 0.125);
        range.addProperty("mob_factor", 0.5);

        JsonObject piercing = new JsonObject();
        piercing.add("sound", sound("minecraft:item.spear.attack"));
        piercing.add("hit_sound", sound("minecraft:item.spear.hit"));

        JsonObject swing = new JsonObject();
        swing.addProperty("type", "stab");
        swing.addProperty("duration", 17);

        properties = tryComponent(properties, "attack_range", range);
        properties = tryComponent(properties, "piercing_weapon", piercing);
        properties = tryComponent(properties, "minimum_attack_charge", new JsonPrimitive(1.0f));
        properties = tryComponent(properties, "swing_animation", swing);
        // A registry holder, so it has to be resolved once registries exist (a plain JSON parse here fails).
        properties = properties.delayedHolderComponent(net.minecraft.core.component.DataComponents.DAMAGE_TYPE,
                net.minecraft.world.damagesource.DamageTypes.SPEAR);
        return properties;
    }

    private static JsonObject sound(String id) {
        JsonObject sound = new JsonObject();
        sound.addProperty("sound_id", id);
        return sound;
    }

    private static Item.Properties tryComponent(Item.Properties properties, String vanillaComponent, JsonElement json) {
        try {
            return withComponent(properties, Identifier.withDefaultNamespace(vanillaComponent), json);
        } catch (RuntimeException e) {
            LOGGER.warn("Couldn't apply minecraft:{} to the carbine", vanillaComponent, e);
            return properties;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Item.Properties withComponent(Item.Properties properties, Identifier componentId, JsonElement json) {
        DataComponentType<T> type = (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(componentId)
                .map(Holder::value)
                .orElseThrow(() -> new IllegalStateException("Unknown component " + componentId));
        T value = type.codecOrThrow().parse(JsonOps.INSTANCE, json).getOrThrow();
        return properties.component(type, value);
    }
}
