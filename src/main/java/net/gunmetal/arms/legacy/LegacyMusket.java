package net.gunmetal.arms.legacy;

import com.mojang.serialization.Codec;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.gunmetal.arms.ammo.Cartridge;
import net.gunmetal.arms.guns.GunItem;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps items from worlds that ran Realm Arms with Musket Mod installed. Only active when Musket
 * Mod is NOT installed:
 *  - musketmod:cartridge still loads, and turns into gunmetal:cartridge in a player's inventory
 *  - guns carrying the old musketmod:loaded / musketmod:loading_stage data still load, and switch
 *    to the Realm Arms versions the first time they tick in an inventory
 * Without this, those items would fail to load and be lost. Nothing here is visible in game.
 */
public final class LegacyMusket {
    private static final String NAMESPACE = "musketmod";
    public static @Nullable DataComponentType<Boolean> LOADED;
    public static @Nullable DataComponentType<Byte> LOADING_STAGE;
    public static @Nullable Item CARTRIDGE;

    private LegacyMusket() {
    }

    public static void register() {
        if (FabricLoader.getInstance().isModLoaded(NAMESPACE)) {
            return;
        }
        LOADED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("loaded"),
                new DataComponentType.Builder<Boolean>().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());
        LOADING_STAGE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("loading_stage"),
                new DataComponentType.Builder<Byte>().persistent(Codec.BYTE).networkSynchronized(ByteBufCodecs.BYTE).build());
        Identifier cartridgeId = id("cartridge");
        CARTRIDGE = Registry.register(BuiltInRegistries.ITEM, cartridgeId,
                new LegacyCartridgeItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, cartridgeId))));
        // Old cartridges left in dispensers still fire.
        net.gunmetal.arms.ammo.RealmArmsAmmo.registerDispenser(CARTRIDGE, Cartridge.STANDARD);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, path);
    }

    /** Called from GunItem#inventoryTick: moves old loading data over to the Realm Arms components. */
    public static void migrateGun(ItemStack stack) {
        if (LOADED != null && stack.has(LOADED)) {
            if (Boolean.TRUE.equals(stack.remove(LOADED))) {
                GunItem.setLoaded(stack, true);
            }
        }
        if (LOADING_STAGE != null && stack.has(LOADING_STAGE)) {
            Byte stage = stack.remove(LOADING_STAGE);
            if (stage != null && GunItem.getLoadingStage(stack) == 0) {
                GunItem.setLoadingStage(stack, stage);
            }
        }
    }

    /** The old standard cartridge: swaps itself for gunmetal:cartridge when it's in a player's inventory. */
    private static final class LegacyCartridgeItem extends Item {
        LegacyCartridgeItem(Properties properties) {
            super(properties);
        }

        @Override
        public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
            if (!(entity instanceof Player player) || Cartridge.STANDARD.item == null) {
                return;
            }
            for (int i = 0; i < player.getInventory().getContainerSize(); ++i) {
                if (player.getInventory().getItem(i) == stack) {
                    player.getInventory().setItem(i, new ItemStack(Cartridge.STANDARD.item, stack.getCount()));
                    return;
                }
            }
        }
    }
}
