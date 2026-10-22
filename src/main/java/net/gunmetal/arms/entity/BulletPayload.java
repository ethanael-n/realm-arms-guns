package net.gunmetal.arms.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side extra behavior for a bullet (add-ons, e.g. Realm Raids' Trooper smoke round). Set it on
 * {@link GunBullet#payload} before the bullet spawns.
 */
public interface BulletPayload {
    /** A direct hit on an entity. Return true to skip the bullet's normal damage. */
    default boolean onHitEntity(GunBullet bullet, Entity target) {
        return false;
    }

    /** Any impact (block or entity), after the normal handling. */
    default void onImpact(GunBullet bullet, Vec3 pos) {
    }

    /** The bullet ran out of lifetime or range without hitting anything. */
    default void onExpire(GunBullet bullet, Vec3 pos) {
    }
}
