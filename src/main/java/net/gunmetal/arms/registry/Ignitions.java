package net.gunmetal.arms.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

import static net.gunmetal.arms.RealmArmsWeapons.MOD_ID;

/**
 * Copper, brass and steel ignitions: the guns' crafting and repair material (pillagers and the
 * Sharpshooter/Trooper drop them; they smelt back into their ingot). Moved here from the old
 * attachment code when attachments were removed (1.0.4).
 */
public final class Ignitions {
    public static final List<Item> IGNITIONS = new ArrayList<>();

    private Ignitions() {
    }

    public static void register() {
        for (String tier : List.of("copper", "brass", "steel")) {
            Identifier itemId = Identifier.fromNamespaceAndPath(MOD_ID, tier + "_ignition");
            Item item = new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, itemId)));
            Registry.register(BuiltInRegistries.ITEM, itemId, item);
            IGNITIONS.add(item);
        }
    }
}
