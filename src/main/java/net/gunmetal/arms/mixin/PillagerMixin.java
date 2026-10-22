package net.gunmetal.arms.mixin;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.gunmetal.arms.ai.PillagerGunGoal;
import net.gunmetal.arms.guns.GunConfig;
import net.gunmetal.arms.guns.GunItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

import static net.gunmetal.arms.RealmArmsWeapons.MOD_ID;

/**
 * Pillagers: the Realm Arms gun AI, and a chance (GunConfig.PILLAGER_GUN_CHANCE) to spawn holding
 * a gun from the loot table gunmetal:pillager_weapon (rolled with the pillager as "this", so
 * entity conditions work), and the crossbow arm pose while holding one.
 */
@Mixin(Pillager.class)
abstract class PillagerMixin {
    private static final ResourceKey<LootTable> REALMARMS$WEAPONS =
            ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(MOD_ID, "pillager_weapon"));

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void gunmetal$addGunGoal(CallbackInfo ci) {
        Pillager self = (Pillager) (Object) this;
        ((MobAccessor) self).gunmetal$getGoalSelector().addGoal(2, new PillagerGunGoal<>(self));
    }

    @Inject(method = "populateDefaultEquipmentSlots", at = @At("TAIL"))
    private void gunmetal$maybeGiveGun(RandomSource random, DifficultyInstance difficulty, CallbackInfo ci) {
        Pillager self = (Pillager) (Object) this;
        if (!(self.level() instanceof ServerLevel level) || random.nextFloat() >= GunConfig.PILLAGER_GUN_CHANCE) {
            return;
        }
        LootTable table = level.getServer().reloadableRegistries().getLootTable(REALMARMS$WEAPONS);
        List<ItemStack> items = table.getRandomItems(new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, self)
                .withParameter(LootContextParams.ORIGIN, self.position())
                .create(LootContextParamSets.SELECTOR));
        if (!items.isEmpty()) {
            self.setItemSlot(EquipmentSlot.MAINHAND, items.get(0));
        }
    }

    /** Holds a gun like a crossbow (raised to aim while loading). */
    @Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
    private void gunmetal$gunArmPose(CallbackInfoReturnable<AbstractIllager.IllagerArmPose> cir) {
        Pillager self = (Pillager) (Object) this;
        if (GunItem.getHoldingHand(self) != null) {
            cir.setReturnValue(self.isUsingItem() ? AbstractIllager.IllagerArmPose.CROSSBOW_CHARGE
                    : AbstractIllager.IllagerArmPose.CROSSBOW_HOLD);
        }
    }
}
