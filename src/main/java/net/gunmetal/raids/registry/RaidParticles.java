package net.gunmetal.raids.registry;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import static net.gunmetal.raids.RealmRaids.MOD_ID;

public final class RaidParticles {
    /**
     * The Sharpshooter's scope glint, shown for the 5 s before it fires. Always shown, however far
     * away (the "force" flag), and drawn bigger the further the camera is, so it reads at 40-70+ blocks.
     * Texture: assets/gunmetal/textures/particle/scope_glint.png.
     */
    public static final SimpleParticleType SCOPE_GLINT = FabricParticleTypes.simple(true);
    /**
     * The windup, shown for the last 3 s of the glint (aim locked). Drawn like the glint, 2 pixels
     * lower (scaled with the glint's size), without the flicker; its textures play as animation frames
     * over its lifetime. Texture list: assets/gunmetal/particles/scope_windup.json.
     */
    public static final SimpleParticleType SCOPE_WINDUP = FabricParticleTypes.simple(true);
    /**
     * Trooper smoke cloud puffs (25 per smoke round): big, spread out fast across the ground, settle,
     * stick where they stop, last ~6 s. Textures: assets/gunmetal/particles/smoke_cloud.json (each
     * puff picks one at random).
     */
    public static final SimpleParticleType SMOKE_CLOUD = FabricParticleTypes.simple(true);

    /**
     * The Artificer's pulse: three big flat rings at its feet, 0.2 blocks apart, each with its own
     * texture (pulse_ring_low/mid/top; several textures = animation frames). They spread out with the
     * wave to the pulse radius, spin (low and top counter-clockwise, middle clockwise), throb slightly.
     * Top: 1.5 s, pops in, 0.5 s fade-out, follows. Middle: 3.5 s, 1.5 s fade-out, follows. Bottom: 2 s,
     * no fade-out (put it in the frames), stays put.
     */
    public static final SimpleParticleType PULSE_RING_LOW = FabricParticleTypes.simple(true);
    public static final SimpleParticleType PULSE_RING_MID = FabricParticleTypes.simple(true);
    public static final SimpleParticleType PULSE_RING_TOP = FabricParticleTypes.simple(true);
    /**
     * Summon marker: the top pulse ring's texture (particles/summon_marker.json), 2.5 blocks across,
     * on a drone when it upgrades and a golem when it appears; follows it, spins, 1 s, no fades.
     */
    public static final SimpleParticleType SUMMON_MARKER = FabricParticleTypes.simple(true);
    /** The sparks when the pulse destroys a projectile: electric sparks drawn 1.5x bigger (particles/artificer_zap.json). */
    public static final SimpleParticleType ARTIFICER_ZAP = FabricParticleTypes.simple(false);
    /**
     * Debris from the Artificer's summons when hit or destroyed: big tumbling pieces that bounce, 1.5 s.
     * Textures: assets/gunmetal/particles/construct_bits.json (each piece picks one at random).
     */
    public static final SimpleParticleType CONSTRUCT_BITS = FabricParticleTypes.simple(false);
    /**
     * Envelope tatters: scraps of the Siege Balloon's envelope, thrown out at its death and at each damage
     * stage change. Same behavior as construct_bits but lasting 4-12 s (1.0.3); textures: particles/envelope_tatters.json (1.0.2).
     */
    public static final SimpleParticleType ENVELOPE_TATTERS = FabricParticleTypes.simple(false);
    /** Burning debris (a flame-textured, full-bright construct_bits piece): the Siege Balloon's death. */
    public static final SimpleParticleType BURNING_DEBRIS = FabricParticleTypes.simple(false);
    /** Electric sparks: the Artificer's pulse warning, stunned mobs, destroyed projectiles (vanilla electric spark look). */
    public static final SimpleParticleType ARTIFICER_SPARK = FabricParticleTypes.simple(false);

    /** Siege Balloon death: a camera-facing ring that grows from 6 to 18 blocks across in 0.5 s (particles/balloon_death_ring.json). */
    public static final SimpleParticleType BALLOON_DEATH_RING = FabricParticleTypes.simple(true);
    /** Siege Balloon death: big smoke puffs (3.75 blocks), hang 4 s with a slow random drift, fade over 2 s (particles/balloon_death_smoke.json). */
    public static final SimpleParticleType BALLOON_DEATH_SMOKE = FabricParticleTypes.simple(true);

    private RaidParticles() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "scope_glint"), SCOPE_GLINT);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "burning_debris"), BURNING_DEBRIS);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "scope_windup"), SCOPE_WINDUP);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "smoke_cloud"), SMOKE_CLOUD);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "pulse_ring_low"), PULSE_RING_LOW);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "pulse_ring_mid"), PULSE_RING_MID);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "pulse_ring_top"), PULSE_RING_TOP);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "artificer_spark"), ARTIFICER_SPARK);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "summon_marker"), SUMMON_MARKER);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "artificer_zap"), ARTIFICER_ZAP);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "construct_bits"), CONSTRUCT_BITS);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "envelope_tatters"), ENVELOPE_TATTERS);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "balloon_death_ring"), BALLOON_DEATH_RING);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "balloon_death_smoke"), BALLOON_DEATH_SMOKE);
    }
}
