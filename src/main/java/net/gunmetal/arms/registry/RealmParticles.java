package net.gunmetal.arms.registry;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

import static net.gunmetal.arms.RealmArmsWeapons.MOD_ID;

/**
 * Every particle the guns use. Each one's look comes entirely from its texture(s) in
 * assets/gunmetal/textures/particle/ (listed in assets/gunmetal/particles/<name>.json);
 * how it moves and how long it lives is set in client/particle/RealmParticle.Style.
 */
public final class RealmParticles {
    /** Registration order; also used by the client to attach providers. */
    public static final Map<String, SimpleParticleType> ALL = new LinkedHashMap<>();

    // Bullet trails (emitted constantly along the bullet's path)
    public static final SimpleParticleType TRAIL_SMALL = create("trail_small");
    public static final SimpleParticleType TRAIL_STANDARD = create("trail_standard");
    public static final SimpleParticleType TRAIL_DENSE = create("trail_dense");
    public static final SimpleParticleType TRAIL_BLACK = create("trail_black");
    /** Not used by any gun; for add-ons (Realm Raids' Bombardier shells). */
    public static final SimpleParticleType TRAIL_CANNONBALL = create("trail_cannonball");
    /** Not used by any gun; for add-ons (Realm Raids' Sharpshooter shots). */
    public static final SimpleParticleType TRAIL_SNIPER = create("trail_sniper");
    /** Flat ring left along the sniper trail every few blocks, facing along the shot (ShotRingParticle). */
    public static final SimpleParticleType TRAIL_SNIPER_RING = create("trail_sniper_ring");

    // Cartridge accents (mixed into the trail of special cartridges)
    public static final SimpleParticleType ACCENT_BLAZE = create("accent_blaze");
    public static final SimpleParticleType ACCENT_ICE = create("accent_ice");
    public static final SimpleParticleType ACCENT_NUCLEAR = create("accent_nuclear");
    public static final SimpleParticleType ACCENT_VOID = create("accent_void");
    public static final SimpleParticleType ACCENT_SUNKEN = create("accent_sunken");
    public static final SimpleParticleType ACCENT_POISON = create("accent_poison");

    // Firing smoke (drifts slowly outward and upward from the barrel)
    public static final SimpleParticleType SMOKE_SUBTLE = create("smoke_subtle");
    public static final SimpleParticleType SMOKE_MEDIUM = create("smoke_medium");
    public static final SimpleParticleType SMOKE_HEAVY = create("smoke_heavy");

    // Aftersmoke: follows every smoke burst, lasts twice as long, half as many. All three share
    // one texture (textures/particle/aftersmoke.png); they only differ in size/lifetime like the smoke.
    public static final SimpleParticleType AFTERSMOKE_SUBTLE = create("aftersmoke_subtle");
    public static final SimpleParticleType AFTERSMOKE_MEDIUM = create("aftersmoke_medium");
    public static final SimpleParticleType AFTERSMOKE_HEAVY = create("aftersmoke_heavy");

    // Muzzle flash (one large, brief particle at the barrel)
    public static final SimpleParticleType FLASH_LIGHT = create("flash_light");
    public static final SimpleParticleType FLASH_MEDIUM = create("flash_medium");
    public static final SimpleParticleType FLASH_HEAVY = create("flash_heavy");
    public static final SimpleParticleType FLASH_VOLLEYGUN = create("flash_volleygun");

    // Accent flashes: drawn on top of the normal flash, shorter-lived. One per special cartridge, plus the rifle.
    public static final SimpleParticleType FLASH_ACCENT_BLAZE = create("flash_accent_blaze");
    public static final SimpleParticleType FLASH_ACCENT_ICE = create("flash_accent_ice");
    public static final SimpleParticleType FLASH_ACCENT_NUCLEAR = create("flash_accent_nuclear");
    public static final SimpleParticleType FLASH_ACCENT_VOID = create("flash_accent_void");
    public static final SimpleParticleType FLASH_ACCENT_SUNKEN = create("flash_accent_sunken");
    public static final SimpleParticleType FLASH_ACCENT_POISON = create("flash_accent_poison");
    public static final SimpleParticleType FLASH_ACCENT_RIFLE = create("flash_accent_rifle");

    // Sparks, by gun material
    public static final SimpleParticleType SPARK_COPPER = create("spark_copper");
    public static final SimpleParticleType SPARK_BRASS = create("spark_brass");
    public static final SimpleParticleType SPARK_STEEL = create("spark_steel");

    private RealmParticles() {
    }

    private static SimpleParticleType create(String name) {
        SimpleParticleType type = FabricParticleTypes.simple();
        ALL.put(name, type);
        return type;
    }

    public static void register() {
        ALL.forEach((name, type) ->
                Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, name), type));
    }
}
