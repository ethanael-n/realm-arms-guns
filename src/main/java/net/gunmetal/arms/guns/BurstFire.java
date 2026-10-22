package net.gunmetal.arms.guns;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.gunmetal.arms.ammo.Cartridge;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Fires the rest of a burst, one shot every {@link GunType#BURST_INTERVAL_TICKS} ticks (server side).
 * Each shot aims where the shooter is looking at that moment, so players can walk a burst onto a
 * target. The burst stops if the shooter dies or stops holding that gun.
 */
public final class BurstFire {
    /** Pillager aim error, in degrees, applied to each follow-up shot fired by a mob. */
    private static final float MOB_AIM_SPREAD = 3.0f;
    /** Same for non-hostile mobs (guards). */
    private static final float ALLY_AIM_SPREAD = 2.0f;
    /** Same volume as a normal gunshot. */
    private static final float FIRE_VOLUME = 3.5f;

    private static final List<Burst> ACTIVE = new ArrayList<>();

    private BurstFire() {
    }

    private static final class Burst {
        final GunItem gun;
        final RealmGun realm;
        final LivingEntity shooter;
        final ItemStack stack;
        final Cartridge cartridge;
        final int shotId;
        final boolean misfire;
        final boolean spareShooter;
        int remaining;
        int delay = GunType.BURST_INTERVAL_TICKS;

        Burst(GunItem gun, RealmGun realm, LivingEntity shooter, ItemStack stack, Cartridge cartridge, int shotId, int remaining, boolean misfire,
              boolean spareShooter) {
            this.gun = gun;
            this.realm = realm;
            this.shooter = shooter;
            this.stack = stack;
            this.cartridge = cartridge;
            this.shotId = shotId;
            this.remaining = remaining;
            this.misfire = misfire;
            this.spareShooter = spareShooter;
        }
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
    }

    static void schedule(GunItem gun, RealmGun realm, LivingEntity shooter, ItemStack stack, Cartridge cartridge, int shotId,
                         int remaining, boolean misfire, boolean spareShooter) {
        ACTIVE.add(new Burst(gun, realm, shooter, stack, cartridge, shotId, remaining, misfire, spareShooter));
    }

    private static void tick() {
        Iterator<Burst> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Burst burst = it.next();
            if (--burst.delay > 0) {
                continue;
            }
            InteractionHand hand = handHolding(burst.shooter, burst.stack);
            if (!burst.shooter.isAlive() || burst.shooter.isRemoved() || hand == null || burst.stack.isEmpty()
                    || !(burst.shooter.level() instanceof ServerLevel level)) {
                it.remove();
                continue;
            }
            GunType type = burst.realm.gunType();
            GunFiring.shoot(level, burst.gun, burst.realm, burst.shooter, aim(burst), burst.gun.smokeOffsetFor(burst.shooter, hand),
                    burst.cartridge, burst.shotId, Math.max(1, type.burstShots()), burst.stack, burst.misfire, burst.spareShooter);
            level.playSound(null, burst.shooter.getX(), burst.shooter.getY(), burst.shooter.getZ(),
                    burst.gun.fireSound(burst.stack), burst.shooter.getSoundSource(), FIRE_VOLUME, type.randomFirePitch());
            if (--burst.remaining <= 0) {
                it.remove();
            } else {
                burst.delay = GunType.BURST_INTERVAL_TICKS;
            }
        }
    }

    private static Vec3 aim(Burst burst) {
        LivingEntity shooter = burst.shooter;
        if (shooter instanceof Mob mob && !(shooter instanceof Player) && mob.getTarget() != null && mob.getTarget().isAlive()) {
            Vec3 dir = burst.gun.aimAt(mob, mob.getTarget());
            return GunItem.addUniformSpread(dir, mob.getRandom(),
                    mob instanceof net.minecraft.world.entity.monster.Enemy ? MOB_AIM_SPREAD : ALLY_AIM_SPREAD);
        }
        return Vec3.directionFromRotation(shooter.getXRot(), shooter.getYRot());
    }

    private static @Nullable InteractionHand handHolding(LivingEntity shooter, ItemStack stack) {
        if (shooter.getMainHandItem() == stack) {
            return InteractionHand.MAIN_HAND;
        }
        if (shooter.getOffhandItem() == stack) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }
}
