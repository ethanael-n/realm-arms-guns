package net.gunmetal.arms.ammo;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Every cartridge item. Datapacks give mobs cartridges in the offhand by adding an
 * {@code minecraft:equippable} (offhand) component; once a player picks such a stack up, the
 * component is removed so it stacks with ordinary cartridges again.
 */
public class CartridgeItem extends Item {
    public CartridgeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        if (entity instanceof Player && stack.has(DataComponents.EQUIPPABLE)) {
            stack.remove(DataComponents.EQUIPPABLE);
        }
    }
}
