package net.gunmetal.arms.guns;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.gunmetal.arms.registry.RealmSounds;
import org.apache.commons.lang3.tuple.Pair;

/**
 * The loading tick (GunItem#onUseTick) for Realm Arms guns, with per-gun sounds:
 *
 *   end of stage 1            -> gunmetal:gun.<type>.load_first   (this gun's own)
 *   end of stages 2..N-2      -> gunmetal:gun.load1               (shared by all guns)
 *   end of stage N-1          -> gunmetal:gun.load2               (shared by all guns)
 *   end of stage N (loaded)   -> gunmetal:gun.<type>.ready        (this gun's own)
 *
 * So a 2-stage pistol plays only load_first then ready; a 5-stage rifle plays load_first,
 * load1, load1, load2, ready. gunmetal:gun.load0 plays halfway into the stage you resume at,
 * if you let go of a reload past stage 1 and carry on.
 */
public final class GunLoading {
    private static final float VOLUME = 0.8f;

    private GunLoading() {
    }

    public static void onUseTick(GunItem gun, GunType type, Level level, LivingEntity entity, ItemStack stack, int ticksLeft) {
        Pair<Integer, Integer> duration = GunItem.getLoadingDuration(stack);
        int stages = duration.getLeft();
        int ticksPerStage = duration.getRight();
        int usingTicks = gun.getUseDuration(stack, entity) - ticksLeft;
        int previousStage = GunItem.getLoadingStage(stack);
        int stage = previousStage + usingTicks / ticksPerStage;

        if (stage > 1 && stage < stages && usingTicks == ticksPerStage / 2) {
            entity.playSound(RealmSounds.LOAD_RESUME, VOLUME, 1.0f);
        }
        if (usingTicks > 0 && usingTicks % ticksPerStage == 0) {
            int finished = stage - 1;
            if (finished == 1) {
                entity.playSound(RealmSounds.loadFirst(type), VOLUME, 1.0f);
            } else if (stage < stages) {
                entity.playSound(RealmSounds.LOAD_MIDDLE, VOLUME, 1.0f);
            } else if (stage == stages) {
                entity.playSound(RealmSounds.LOAD_LAST, VOLUME, 1.0f);
            }
        }

        if (level.isClientSide() && entity instanceof Player) {
            GunItem.setActiveStack(entity.getUsedItemHand(), stack);
            return;
        }
        if (stage > stages && !GunItem.isLoaded(stack)) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), RealmSounds.ready(type),
                    entity.getSoundSource(), VOLUME, 1.0f);
            if (entity instanceof Player player) {
                if (previousStage == 1) {
                    GunItem.consumeAmmo(player, stack);
                }
            } else if (!level.isClientSide()) {
                GunItem.mobLoadAmmo(entity, stack);
            }
            GunItem.setLoaded(stack, true);
        }
    }
}
