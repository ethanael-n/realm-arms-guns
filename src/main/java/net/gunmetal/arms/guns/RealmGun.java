package net.gunmetal.arms.guns;

/** Shared by every Realm Arms gun. */
public interface RealmGun {
    GunType gunType();

    GunTier gunTier();
}
