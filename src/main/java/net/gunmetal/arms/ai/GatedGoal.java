package net.gunmetal.arms.ai;

import net.minecraft.world.entity.ai.goal.Goal;

import java.util.function.BooleanSupplier;

/** Wraps another mod's goal so it only runs while a condition holds. */
public class GatedGoal extends Goal {
    private final Goal inner;
    private final BooleanSupplier allowed;

    public GatedGoal(Goal inner, BooleanSupplier allowed) {
        this.inner = inner;
        this.allowed = allowed;
        setFlags(inner.getFlags());
    }

    public Goal inner() {
        return inner;
    }

    @Override
    public boolean canUse() {
        return allowed.getAsBoolean() && inner.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return allowed.getAsBoolean() && inner.canContinueToUse();
    }

    @Override
    public boolean isInterruptable() {
        return inner.isInterruptable();
    }

    @Override
    public void start() {
        inner.start();
    }

    @Override
    public void stop() {
        inner.stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return inner.requiresUpdateEveryTick();
    }

    @Override
    public void tick() {
        inner.tick();
    }
}
