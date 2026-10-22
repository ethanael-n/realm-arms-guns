package net.gunmetal.raids.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.gunmetal.raids.entity.ThrownBrassDeployable;
import net.gunmetal.raids.registry.RaidSounds;

import java.util.function.Consumer;

/**
 * Brass Deployable: thrown like a snowball (8 s cooldown, stacks to 16). It sits where it lands for
 * 3 s, then becomes a deployed brass golem carrying the item's load (GolemLoad). The tooltip lists
 * the load.
 */
public class BrassDeployableItem extends Item {
    public static final int THROW_COOLDOWN_TICKS = 160;
    public static final float THROW_POWER = 1.5f;

    public BrassDeployableItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), RaidSounds.BRASS_DEPLOYABLE_THROW, SoundSource.NEUTRAL,
                0.5f, 0.4f / (level.getRandom().nextFloat() * 0.4f + 0.8f));
        if (level instanceof ServerLevel serverLevel) {
            Projectile.spawnProjectileFromRotation(ThrownBrassDeployable::new, serverLevel, stack, player, 0.0f, THROW_POWER, 1.0f);
        }
        player.getCooldowns().addCooldown(stack, THROW_COOLDOWN_TICKS);
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
                                TooltipFlag flag) {
        GolemLoad load = GolemLoad.of(stack);
        line(tooltip, "gunpowder", load.gunpowder());
        line(tooltip, "blaze", load.blaze());
        line(tooltip, "patina", load.patina());
        line(tooltip, "redstone", load.redstone());
        line(tooltip, "glowstone", load.glowstone());
        line(tooltip, "sulfur", load.sulfur());
    }

    private static void line(Consumer<Component> tooltip, String key, int count) {
        if (count > 0) {
            tooltip.accept(Component.translatable("item.gunmetal.brass_deployable.load." + key, count)
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
