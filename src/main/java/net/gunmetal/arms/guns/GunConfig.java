package net.gunmetal.arms.guns;

/**
 * Settings that used to come from Musket Mod's config/musketmod.txt, now fixed here (values are
 * Musket Mod's defaults). Per-gun stats are in {@link GunType}.
 */
public final class GunConfig {
    /** Bullets vanish after travelling this far (blocks). */
    public static final float BULLET_MAX_DISTANCE = 256.0f;
    /**
     * Bullets fired by mobs, guards and dispensers (anything but a player) vanish sooner, to keep big
     * fights (raids against gun-carrying guards) cheaper on the server: this many ticks / blocks.
     */
    public static final short MOB_BULLET_LIFETIME = 60;
    public static final float MOB_BULLET_MAX_DISTANCE = 160.0f;
    /** Damage multiplier for player-vs-player hits. */
    public static final float PVP_MULTIPLIER = 1.0f;
    /**
     * Raider friendly fire (1.0.5): a bullet fired by a raider (pillager, Trooper, Bombardier shell, Siege
     * Balloon cannon...) deals this share of its damage to another raider (ravagers included). Its
     * burst, if any, is unchanged (Bombardier bursts already go easy on illagers).
     */
    public static final float RAIDER_FRIENDLY_FIRE = 0.5f;
    /** Player shot spread while sneaking / sprinting (x0.75 = 25% tighter, x1.5 = 50% wider; 1.0.6). Players only. */
    public static final float SNEAK_SPREAD = 0.75f, SPRINT_SPREAD = 1.5f;
    /** Cartridges fired from a dispenser. */
    public static final float DISPENSER_SPREAD = 2.0f;
    public static final float DISPENSER_VELOCITY = 120.0f;
    public static final float DISPENSER_DAMAGE = 10.0f;
    /** Chance for a newly spawned pillager to get a gun from the gunmetal:pillager_weapon loot table. */
    public static final float PILLAGER_GUN_CHANCE = 0.2f;
    /** Rifle scope zoom (field of view divided by this). */
    public static final float SCOPE_ZOOM = 3.0f;
    /** The rifle's bullets fall this much less than others (0.5 = half the drop). */
    public static final float RIFLE_DROP_REDUCTION = 0.5f;
    /** Players only raise their gun to aim when it's loaded (true = always aim when holding one). */
    public static final boolean ALWAYS_AIM = false;

    private GunConfig() {
    }
}
