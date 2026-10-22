package net.gunmetal.arms.guns;

import java.util.EnumSet;
import java.util.List;

import static net.gunmetal.arms.guns.GunTier.BRASS;
import static net.gunmetal.arms.guns.GunTier.COPPER;
import static net.gunmetal.arms.guns.GunTier.STEEL;

/**
 * Every gun type and all of its balance and visual settings, in one place.
 *
 * Stats are given for the gun's BASE tier (copper for everything except the rifle, whose
 * base tier is brass). Higher tiers get +20% damage per step (rounded) and -0.1 degrees of
 * spread per step (never below 0).
 *
 * Columns:
 *   stages     - reload stages; reload time = stages x LOADING_STAGE_SECONDS (0.5s)
 *   spread     - shot deviation in degrees (standard deviation of the spread)
 *   velocity   - muzzle velocity in blocks/second
 *   damage     - damage at the base tier; for spread guns this is the total if every pellet hits
 *   pellets    - projectiles per shot (1 = single-shot gun)
 *   oneHanded  - can be used from the offhand
 *   moveSpeed  - movement speed while loading (1 = full speed, 0 = can't move); sprinting allowed
 */
public enum GunType {
    //          id             stages spread velocity damage pellets oneHanded moveSpeed
    PISTOL("pistol", 2, 1.5f, 140f, 14f, 1, true, 0.8f,
            Projectile.ROUND, Trail.SMALL, Smoke.SUBTLE, Flash.LIGHT),
    CARBINE("carbine", 3, 1.3f, 160f, 15.2f, 1, true, 0.6f,
            Projectile.ROUND, Trail.SMALL, Smoke.MEDIUM, Flash.MEDIUM),
    MUSKETOON("musketoon", 3, 2.0f, 100f, 17.1f, 5, true, 0.6f,
            Projectile.JAGGED, Trail.STANDARD, Smoke.SUBTLE, Flash.LIGHT),
    ARQUEBUS("arquebus", 4, 1.0f, 180f, 24.84f, 1, false, 0.2f,
            Projectile.ROUND, Trail.STANDARD, Smoke.MEDIUM, Flash.MEDIUM),
    BLUNDERBUSS("blunderbuss", 4, 3.0f, 160f, 27.6f, 9, false, 0.4f,
            Projectile.JAGGED, Trail.DENSE, Smoke.HEAVY, Flash.HEAVY),
    RIFLE("rifle", 5, 0.3f, 200f, 24f, 1, false, 0.2f,
            Projectile.ROUND, Trail.STANDARD, Smoke.MEDIUM, Flash.HEAVY),
    HAND_CANNON("hand_cannon", 5, 3.0f, 140f, 37f, 1, false, 0.0f,
            Projectile.HEAVY, Trail.BLACK, Smoke.HEAVY, Flash.HEAVY),
    VOLLEYGUN("volleygun", 5, 6.0f, 140f, 40f, 42, false, 0.2f,
            Projectile.JAGGED, Trail.DENSE, Smoke.HEAVY, Flash.VOLLEYGUN);

    /**
     * Seconds per loading stage for every Realm Arms gun.
     */
    public static final float LOADING_STAGE_SECONDS = 0.5f;

    /**
     * Carbine bayonet: a spear-style jab (reaches 2-4.5 blocks, hits everything in line, needs a
     * full attack charge, no crits or sweeps). Damage per tier as shown in the tooltip; attack
     * speed 1.0 = one jab per second.
     */
    public static final float[] CARBINE_JAB_DAMAGE = {7f, 8f, 9.5f};
    public static final float CARBINE_JAB_SPEED = 1.0f;
    /** Musketoon axe-head: a normal melee swing. Damage per tier and attack speed. */
    public static final float[] MUSKETOON_MELEE_DAMAGE = {9f, 11f, 13f};
    public static final float MUSKETOON_MELEE_SPEED = 1.2f;

    /** Burst guns fire their pellets over several shots, this many ticks apart. */
    public static final int BURST_INTERVAL_TICKS = 2;

    /**
     * Damage multiplier for bullets fired by any non-player (pillagers, guards, and dispensers, which have no shooter).
     */
    public static final float MOB_DAMAGE_MULTIPLIER = 0.6f;

    /** Cartridges fired from a dispenser play at a random pitch within 1 +/- this. */
    public static final float DISPENSER_PITCH_VARIATION = 0.1f;

    /** Each gunshot plays at a random pitch within 1 +/- this (so 0.1 = 0.9 to 1.1). */
    public float firePitchVariation() {
        return switch (this) {
            case PISTOL -> 0.1f;
            case CARBINE, VOLLEYGUN -> 0.3f;
            case BLUNDERBUSS, RIFLE -> 0.4f;
            case ARQUEBUS, HAND_CANNON, MUSKETOON -> 0.5f;
        };
    }

    public float randomFirePitch() {
        return randomPitch(firePitchVariation());
    }

    public static float randomPitch(float variation) {
        return 1.0f + (java.util.concurrent.ThreadLocalRandom.current().nextFloat() * 2.0f - 1.0f) * variation;
    }

    /**
     * Hand cannon balls burst on impact: a small explosion that never breaks blocks and never applies
     * cartridge effects (the ball itself still does). The radius grows with the distance the ball has
     * flown: 0.2 blocks at the muzzle, rising linearly to 1.4 at 64 blocks and beyond.
     */
    public static final float HAND_CANNON_MIN_RADIUS = 0.2f;
    public static final float HAND_CANNON_MAX_RADIUS = 1.4f;
    public static final float HAND_CANNON_RADIUS_PEAK = 64.0f;

    /** Largest burst radius this gun can produce (0 = no explosion); used by mob AI for clearance. */
    public float impactExplosionRadius() {
        return this == HAND_CANNON ? HAND_CANNON_MAX_RADIUS : 0f;
    }

    /** Burst radius for a ball that has flown this many blocks (0 = no explosion). */
    public float impactExplosionRadius(float distance) {
        if (this != HAND_CANNON) {
            return 0f;
        }
        return HAND_CANNON_MIN_RADIUS + (HAND_CANNON_MAX_RADIUS - HAND_CANNON_MIN_RADIUS) * rangeFraction(distance, HAND_CANNON_RADIUS_PEAK);
    }

    /** Hand cannon is disposable: 4 shots, no repair, no Mending/Unbreaking. */
    public static final int HAND_CANNON_DURABILITY = 4;

    /** CANNONBALL, Trail.CANNONBALL and Trail.SNIPER: not used by any gun; for add-ons (Realm Raids). */
    public enum Projectile { ROUND, JAGGED, HEAVY, CANNONBALL, SHRAPNEL }
    public enum Trail { SMALL, STANDARD, DENSE, BLACK, CANNONBALL, SNIPER, FLAME }
    public enum Smoke { SUBTLE, MEDIUM, HEAVY }
    /** Muzzle flash. All are drawn the same size; VOLLEYGUN uses its own texture. */
    public enum Flash { NONE, LIGHT, MEDIUM, HEAVY, VOLLEYGUN }
    /** Gun-kick strength; the actual angles/distances are in client/GunRecoil.java. */
    public enum Kick { LIGHT, MEDIUM, HEAVY }

    /**
     * Shots per trigger pull. The pellets are split evenly across them (volleygun: 7 x 6 = 42),
     * and each pellet still deals damage / pellets, so a full hit does the same total damage.
     * One cartridge and one point of durability per burst.
     */
    public int burstShots() {
        return this == VOLLEYGUN ? 7 : 1;
    }

    /**
     * Guns held lower in the arm emit their muzzle flash, smoke and sparks this many model pixels
     * (1/16 block) lower, measured "down" from the shooter's view.
     */
    public float muzzleDropPixels() {
        return switch (this) {
            case BLUNDERBUSS, MUSKETOON, VOLLEYGUN, HAND_CANNON -> 3f;
            default -> 0f;
        };
    }

    /** Sparks from the lock, thrown out to EACH side (left and right), per trigger pull. */
    public int sparksPerSide() {
        return switch (this) {
            case PISTOL, CARBINE, MUSKETOON -> 3;
            case ARQUEBUS, RIFLE -> 5;
            case BLUNDERBUSS, VOLLEYGUN -> 8;
            case HAND_CANNON -> 0;
        };
    }

    /** Sparks thrown upward from the top of the gun instead (the hand cannon's touch-hole). */
    public int sparksUpward() {
        return this == HAND_CANNON ? 10 : 0;
    }

    /** Fine-tunes the gun kick on top of its strength (1 = the strength's normal kick). */
    public float gunKickScale() {
        return switch (this) {
            case ARQUEBUS -> 0.6f;
            case VOLLEYGUN -> 0.5f;
            case BLUNDERBUSS -> 1.2f;
            default -> 1.0f;
        };
    }

    /** How hard the gun model snaps back in first person when firing. */
    public Kick gunKick() {
        return switch (this) {
            case PISTOL -> Kick.LIGHT;
            case CARBINE, MUSKETOON, BLUNDERBUSS -> Kick.MEDIUM;
            case ARQUEBUS, HAND_CANNON, VOLLEYGUN, RIFLE -> Kick.HEAVY;
        };
    }

    /**
     * How mobs (pillagers, and guards with Guard Villagers installed) fight with this gun.
     *   range      - they close to this distance (blocks) before stopping to aim
     *   loadOnMove - they start reloading while still walking in
     *   retreat    - they back away when a target gets closer than 60% of the range
     *   fullSpeedLoaded - they walk at full speed while carrying a loaded gun (otherwise half)
     */
    public record Tactics(float range, boolean loadOnMove, boolean retreat, boolean fullSpeedLoaded) {
    }

    public Tactics mobTactics() {
        return switch (this) {
            case PISTOL -> new Tactics(10, true, true, true);
            case CARBINE -> new Tactics(14, true, false, true);
            case MUSKETOON -> new Tactics(12, true, false, true);
            case BLUNDERBUSS -> new Tactics(8, false, false, true);
            case ARQUEBUS -> new Tactics(16, true, true, true);
            case RIFLE, HAND_CANNON -> new Tactics(26, false, true, false);
            case VOLLEYGUN -> new Tactics(22, false, false, false);
        };
    }

    public final String id;
    public final int stages;
    public final float spread;
    public final float velocity;
    public final float damage;
    public final int pellets;
    public final boolean oneHanded;
    public final float moveSpeed;
    public final Projectile projectile;
    public final Trail trail;
    public final Smoke smoke;
    public final Flash flash;

    GunType(String id, int stages, float spread, float velocity, float damage, int pellets,
            boolean oneHanded, float moveSpeed, Projectile projectile, Trail trail, Smoke smoke,
            Flash flash) {
        this.id = id;
        this.stages = stages;
        this.spread = spread;
        this.velocity = velocity;
        this.damage = damage;
        this.pellets = pellets;
        this.oneHanded = oneHanded;
        this.moveSpeed = moveSpeed;
        this.projectile = projectile;
        this.trail = trail;
        this.smoke = smoke;
        this.flash = flash;
    }

    /** All tiers this gun exists in (steel versions exist but are not craftable). */
    public List<GunTier> tiers() {
        return switch (this) {
            case RIFLE -> List.of(BRASS, STEEL);
            case HAND_CANNON -> List.of(COPPER);
            default -> List.of(COPPER, BRASS, STEEL);
        };
    }

    /** Tiers with a crafting recipe. */
    public EnumSet<GunTier> craftableTiers() {
        return switch (this) {
            case RIFLE -> EnumSet.of(BRASS);
            case HAND_CANNON -> EnumSet.of(COPPER);
            default -> EnumSet.of(COPPER, BRASS);
        };
    }

    public GunTier baseTier() {
        return this == RIFLE ? BRASS : COPPER;
    }

    private int stepsAboveBase(GunTier tier) {
        return tier.ordinal() - baseTier().ordinal();
    }

    /**
     * Rifle reverse falloff: listed damage is 0.8x an arquebus of the same tier (24 brass, 29 steel),
     * rising linearly with the distance the bullet has flown, to 1.7x at 92 blocks and beyond
     * (40.8 brass, 49.3 steel). Uses the distance the bullet already tracks, so it costs nothing.
     */
    public static final float RIFLE_RANGE_BONUS = 0.7f;
    public static final float RIFLE_RANGE_PEAK = 92.0f;
    /** Pistol and carbine falloff: linearly down to 0.9x at 32 blocks and beyond. */
    public static final float SIDEARM_FALLOFF = 0.1f;
    public static final float SIDEARM_FALLOFF_END = 32.0f;

    /** 0 at the muzzle, rising linearly to 1 at {@code full} blocks of flight. */
    private static float rangeFraction(float distance, float full) {
        return Math.min(Math.max(distance, 0.0f), full) / full;
    }

    /** Damage multiplier for a hit after this many blocks of flight (rifle up, pistol/carbine down, others 1). */
    public float rangeDamageMultiplier(float distance) {
        return switch (this) {
            case RIFLE -> 1.0f + RIFLE_RANGE_BONUS * rangeFraction(distance, RIFLE_RANGE_PEAK);
            case PISTOL, CARBINE -> 1.0f - SIDEARM_FALLOFF * rangeFraction(distance, SIDEARM_FALLOFF_END);
            default -> 1.0f;
        };
    }

    public float damageFor(GunTier tier) {
        return Math.round(damage * Math.pow(1.2, stepsAboveBase(tier)));
    }

    public float spreadFor(GunTier tier) {
        return Math.max(0f, Math.round((spread - 0.1f * stepsAboveBase(tier)) * 100f) / 100f);
    }

    public int durabilityFor(GunTier tier) {
        return this == HAND_CANNON ? HAND_CANNON_DURABILITY : tier.durability;
    }

    /** Repairable, and can take Mending/Unbreaking. */
    public boolean isMaintainable() {
        return this != HAND_CANNON;
    }

    public boolean isSpreadGun() {
        return pellets > 1;
    }

    public String itemId(GunTier tier) {
        return this == HAND_CANNON ? id : tier.id + "_" + id;
    }
}
