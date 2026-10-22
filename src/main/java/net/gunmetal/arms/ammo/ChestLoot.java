package net.gunmetal.arms.ammo;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

import static net.gunmetal.arms.RealmArmsWeapons.MOD_ID;

/**
 * Extra loot: whenever a vanilla loot table rolls (a chest, a mob drop...), a Realm Arms table at
 * the same path also rolls if one exists, and its items are added. E.g.
 * data/gunmetal/loot_table/chests/pillager_outpost.json adds to outpost chests. None ship with the
 * mod; add them in a datapack. (The same system Musket Mod used under its own namespace.)
 */
public final class ChestLoot {
    private ChestLoot() {
    }

    public static void register() {
        LootTableEvents.MODIFY_DROPS.register((holder, context, drops) -> holder.unwrapKey().ifPresent(key -> {
            Identifier id = key.identifier();
            if (!id.getNamespace().equals("minecraft")) {
                return;
            }
            ResourceKey<LootTable> extra = ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(MOD_ID, id.getPath()));
            context.getResolver().get(extra).ifPresent(table ->
                    table.value().getRandomItemsRaw(context, LootTable.createStackSplitter(context.getLevel(), drops::add)));
        }));
    }
}
