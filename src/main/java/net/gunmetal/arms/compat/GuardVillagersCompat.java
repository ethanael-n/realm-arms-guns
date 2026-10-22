package net.gunmetal.arms.compat;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.gunmetal.arms.ai.GatedGoal;
import net.gunmetal.arms.ai.GuardGunGoal;
import net.gunmetal.arms.mixin.MobAccessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Guard Villagers support (only active when that mod is installed; it isn't needed to build).
 *
 * When a guard is added to a world, it gets {@link GuardGunGoal} at priority 1 - ahead of its own
 * melee goal (2), level with its bow goal - so a guard holding a Realm Arms gun shoots with it.
 * Its shield goal is gated so it doesn't raise a shield while holding a Realm Arms gun (raising
 * the shield would cancel every reload).
 */
public final class GuardVillagersCompat {
    private static final Identifier GUARD = Identifier.fromNamespaceAndPath("guardvillagers", "guard");
    private static final String SHIELD_GOAL = "dev.sterner.guardvillagers.common.entity.goal.RaiseShieldGoal";
    private static final int GUN_GOAL_PRIORITY = 1;

    private GuardVillagersCompat() {
    }

    public static void register() {
        if (!FabricLoader.getInstance().isModLoaded("guardvillagers")) {
            return;
        }
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof PathfinderMob guard && GUARD.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))) {
                install(guard);
            }
        });
    }

    private static void install(PathfinderMob guard) {
        GoalSelector goals = ((MobAccessor) guard).gunmetal$getGoalSelector();
        List<WrappedGoal> shieldGoals = new ArrayList<>();
        for (WrappedGoal wrapped : goals.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof GuardGunGoal) {
                return; // already set up
            }
            if (wrapped.getGoal().getClass().getName().equals(SHIELD_GOAL)) {
                shieldGoals.add(wrapped);
            }
        }
        for (WrappedGoal wrapped : shieldGoals) {
            goals.removeGoal(wrapped.getGoal());
            goals.addGoal(wrapped.getPriority(), new GatedGoal(wrapped.getGoal(), () -> GuardGunGoal.heldRealmGun(guard) == null));
        }
        goals.addGoal(GUN_GOAL_PRIORITY, new GuardGunGoal(guard));
    }
}
