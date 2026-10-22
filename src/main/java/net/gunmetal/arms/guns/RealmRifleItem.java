package net.gunmetal.arms.guns;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The rifle: scoped (hold right-click while loaded to look down the scope, see client/RifleScope),
 * with half the bullet drop, a view kick when fired while scoped, and 2 durability per melee hit.
 */
public class RealmRifleItem extends RealmGunItem {
    public RealmRifleItem(Properties properties, GunType type, GunTier tier) {
        super(properties, type, tier);
    }

    @Override
    public float bulletDropReduction() {
        return GunConfig.RIFLE_DROP_REDUCTION;
    }

    @Override
    public int hitDurabilityDamage() {
        return 2;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        boolean wasLoaded = isLoaded(player.getItemInHand(hand));
        InteractionResult result = super.use(level, player, hand);
        if (level.isClientSide() && wasLoaded && result.consumesAction()) {
            ScopeState.recoilTicks = ScopeState.RECOIL_TICKS;
        }
        return result;
    }
}
