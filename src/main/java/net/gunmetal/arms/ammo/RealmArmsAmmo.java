package net.gunmetal.arms.ammo;

import com.mojang.serialization.Codec;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.Vec3;
import net.gunmetal.arms.entity.RealmBulletEntity;
import net.gunmetal.arms.guns.GunConfig;
import net.gunmetal.arms.guns.GunFiring;
import net.gunmetal.arms.guns.GunItem;
import net.gunmetal.arms.registry.RealmSounds;
import net.gunmetal.arms.guns.GunType;

import java.util.ArrayList;
import java.util.List;

import static net.gunmetal.arms.RealmArmsWeapons.MOD_ID;

/** The special cartridge items, the "which cartridge is loaded" gun component, and dispenser firing. */
public final class RealmArmsAmmo {
    /**
     * Stored on a gun when it finishes loading: the {@link Cartridge} ordinal that went in.
     * Read when the gun fires. Missing = standard.
     */
    public static final DataComponentType<Byte> LOADED_CARTRIDGE = new DataComponentType.Builder<Byte>()
            .persistent(Codec.BYTE)
            .networkSynchronized(ByteBufCodecs.BYTE)
            .build();

    /** Special cartridges in creative-tab order. */
    public static final List<Item> CARTRIDGES = new ArrayList<>();

    private RealmArmsAmmo() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath(MOD_ID, "loaded_cartridge"), LOADED_CARTRIDGE);

        for (Cartridge cartridge : Cartridge.values()) {
            if (cartridge.itemId == null) {
                continue;
            }
            Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, cartridge.itemId);
            Item item = new CartridgeItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)));
            Registry.register(BuiltInRegistries.ITEM, id, item);
            cartridge.item = item;
            CARTRIDGES.add(item);
            DispenserBlock.registerBehavior(item, new CartridgeDispenseBehavior(cartridge));
        }
    }

    /** Makes dispensers fire this item as the given cartridge (used for the legacy Musket Mod cartridge). */
    public static void registerDispenser(Item item, Cartridge cartridge) {
        DispenserBlock.registerBehavior(item, new CartridgeDispenseBehavior(cartridge));
    }

    /** Stamps the cartridge that's about to be used onto the gun. Called just before GunItem consumes ammo. */
    public static void stampLoadedCartridge(ItemStack gun, ItemStack ammo) {
        Cartridge cartridge = ammo.isEmpty() ? Cartridge.STANDARD : Cartridge.fromAmmo(ammo);
        if (cartridge == Cartridge.STANDARD) {
            gun.remove(LOADED_CARTRIDGE);
        } else {
            gun.set(LOADED_CARTRIDGE, (byte) cartridge.ordinal());
        }
    }

    /** Dispensers fire cartridges as bullets (settings in GunConfig), keeping the cartridge effect. */
    private static final class CartridgeDispenseBehavior extends DefaultDispenseItemBehavior {
        private final Cartridge cartridge;

        CartridgeDispenseBehavior(Cartridge cartridge) {
            this.cartridge = cartridge;
        }

        @Override
        public ItemStack execute(BlockSource blockSource, ItemStack stack) {
            ServerLevel level = blockSource.level();
            Direction facing = blockSource.state().getValue(DispenserBlock.FACING);
            Vec3 direction = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
            Position position = DispenserBlock.getDispensePosition(blockSource);
            Vec3 origin = new Vec3(position.x(), position.y(), position.z());
            direction = GunItem.addSpread(direction, level.getRandom(), GunConfig.DISPENSER_SPREAD);

            RealmBulletEntity bullet = new RealmBulletEntity(level);
            bullet.setPos(origin);
            bullet.setVelocity(GunConfig.DISPENSER_VELOCITY, direction);
            bullet.damage = GunConfig.DISPENSER_DAMAGE * cartridge.damageMultiplier;
            bullet.setVisuals(GunType.Projectile.ROUND, GunType.Trail.STANDARD);
            bullet.setCartridge(cartridge);
            bullet.shotId = level.getRandom().nextInt();
            level.addFreshEntity(bullet);

            level.playSound(null, origin.x(), origin.y(), origin.z(), RealmSounds.DISPENSER_FIRE, SoundSource.BLOCKS, 2.5f, GunType.randomPitch(GunType.DISPENSER_PITCH_VARIATION));
            GunFiring.dispenserEffects(level, origin, direction);
            stack.shrink(1);
            return stack;
        }
    }
}
