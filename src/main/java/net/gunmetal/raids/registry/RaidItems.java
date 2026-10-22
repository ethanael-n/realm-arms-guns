package net.gunmetal.raids.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.gunmetal.raids.item.BrassDeployableItem;
import net.gunmetal.raids.item.BrassDeployableRecipe;

import static net.gunmetal.raids.RealmRaids.MOD_ID;

public final class RaidItems {
    /** Brass Deployable: thrown, becomes a deployed brass golem (stacks to 16). Texture: textures/item/brass_deployable.png. */
    public static Item BRASS_DEPLOYABLE;

    private RaidItems() {
    }

    public static void register() {
        Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, "brass_deployable");
        BRASS_DEPLOYABLE = Registry.register(BuiltInRegistries.ITEM, id,
                new BrassDeployableItem(new Item.Properties().stacksTo(16).setId(ResourceKey.create(Registries.ITEM, id))));
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.fromNamespaceAndPath(MOD_ID, "brass_deployable_loaded"),
                BrassDeployableRecipe.SERIALIZER);
    }
}
