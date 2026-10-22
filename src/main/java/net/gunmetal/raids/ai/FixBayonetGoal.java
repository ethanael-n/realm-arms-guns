package net.gunmetal.raids.ai;

import net.minecraft.world.entity.ai.goal.Goal;
import net.gunmetal.raids.entity.Trooper;

import java.util.EnumSet;

/** Holds the Trooper still (no walking, no attacking) for the 1 s it spends fixing its bayonet. */
public class FixBayonetGoal extends Goal {
    private final Trooper mob;

    public FixBayonetGoal(Trooper mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return mob.isFixingBayonet();
    }

    @Override
    public void start() {
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        mob.getNavigation().stop();
        if (mob.getTarget() != null) {
            mob.getLookControl().setLookAt(mob.getTarget(), 30.0f, 30.0f);
        }
    }
}
