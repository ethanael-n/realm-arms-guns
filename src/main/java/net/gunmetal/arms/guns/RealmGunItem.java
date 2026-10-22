package net.gunmetal.arms.guns;

import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** Every Realm Arms gun except the rifle. All behavior is in {@link GunItem}; stats come from {@link GunType}. */
public class RealmGunItem extends GunItem {
    public RealmGunItem(Properties properties, GunType type, GunTier tier) {
        super(properties, type, tier);
    }

    /** Main-hand-only melee stats (carbine jab, musketoon axe-head). */
    public static ItemAttributeModifiers bayonetAttributes(float damage, float speed) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID, damage - 1.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, speed - 4.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }
}
