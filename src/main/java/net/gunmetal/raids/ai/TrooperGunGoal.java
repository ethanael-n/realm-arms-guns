package net.gunmetal.raids.ai;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.gunmetal.arms.ai.PillagerGunGoal;
import net.gunmetal.raids.entity.Trooper;

/** The pillager gun AI (Realm Arms), only while the Trooper still has shots, counting each one (the last is the smoke round). */
public class TrooperGunGoal extends PillagerGunGoal<Trooper> {
    public TrooperGunGoal(Trooper trooper, int extraAimTicks) {
        super(trooper, extraAimTicks);
    }

    @Override
    public boolean canUse() {
        return mob.inGunPhase() && super.canUse();
    }

    /** The base gun goal clears the target when it stops; keep it so the bayonet charge starts at once. */
    @Override
    public void stop() {
        LivingEntity target = mob.getTarget();
        super.stop();
        if (target != null && target.isAlive() && !mob.inGunPhase()) {
            mob.setTarget(target);
        }
    }

    @Override
    public void fire(float spread) {
        mob.prepareShot();
        super.fire(spread);
        mob.swing(InteractionHand.MAIN_HAND); // swing_progress in models = recoil
        mob.onShotFired();
    }
}
