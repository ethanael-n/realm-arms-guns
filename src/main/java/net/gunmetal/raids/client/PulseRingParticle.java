package net.gunmetal.raids.client;

import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteSet;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.gunmetal.raids.entity.AbstractBrassGolem;
import net.gunmetal.raids.entity.Artificer;
import net.gunmetal.raids.entity.ArtificerDrone;
import org.joml.Quaternionf;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * A big flat horizontal ring, full-bright, drawn from above and below, spinning and throbbing
 * slightly. Used for the Artificer's three pulse rings and for the summon marker (the top ring's
 * texture on a drone that upgrades or a golem that appears). Each kind's timing is a Style below.
 * Spin: +1 = counter-clockwise seen from above. Textures: particles/<name>.json (several = animation
 * frames over the lifetime).
 */
public class PulseRingParticle extends SingleQuadParticle {
    /** Spin speed (radians per tick; 0.09 = about 100 degrees a second). */
    private static final float SPIN_SPEED = 0.09f;
    /** Throb: share of the size and speed. */
    private static final float THROB = 0.03f, THROB_SPEED = 0.45f;
    private static final float MAX_ALPHA = 0.9f;
    private static final int FULL_BRIGHT = 15728880;
    /** A brass golem's blast rings (standalone): this share of the pulse rings' lifetime (fades and spread too) and size. */
    public static final float GOLEM_BLAST_TIME = 0.75f, GOLEM_BLAST_SIZE = 0.6f;

    private static final float PULSE_SIZE = (float) Artificer.PULSE_RADIUS;
    /** The pulse rings spread out with the wave (5 blocks at 0.2 blocks/tick = 25 ticks). */
    private static final float PULSE_SPREAD = (float) (Artificer.PULSE_RADIUS / Artificer.PULSE_WAVE_SPEED);

    /**
     * lifetime (ticks), half-width (blocks), spread time (ticks, 0 = full size at once), fade-in and
     * fade-out (ticks, 0 = none), whether it follows its mob, spin direction, and which mob it follows.
     */
    public enum Style {
        /** 2 s, short fade-in, fades out over its last 1 s, stays where the pulse started. */
        PULSE_LOW(40, PULSE_SIZE, PULSE_SPREAD, 2, 20, false, 1, e -> e instanceof Artificer, 1.5),
        /** 3.5 s, short fade-in, 1.5 s fade-out, follows the Artificer. */
        PULSE_MID(70, PULSE_SIZE, PULSE_SPREAD, 2, 30, true, -1, e -> e instanceof Artificer, 1.5),
        /** 1.5 s, pops in, 0.5 s fade-out, follows the Artificer. */
        PULSE_TOP(30, PULSE_SIZE, PULSE_SPREAD, 0, 10, true, 1, e -> e instanceof Artificer, 1.5),
        /** 2.5 blocks across, 1 s, no fades, on the middle of the drone / golem. */
        SUMMON_MARKER(20, 1.25f, 0f, 0, 0, true, 1, e -> e instanceof ArtificerDrone || e instanceof AbstractBrassGolem, 1.0);

        final int lifetime;
        final float size;
        final float spreadTicks;
        final int fadeIn;
        final int fadeOut;
        final boolean follows;
        final int spin;
        final Predicate<Entity> owner;
        final double ownerSearch;

        Style(int lifetime, float size, float spreadTicks, int fadeIn, int fadeOut, boolean follows, int spin,
              Predicate<Entity> owner, double ownerSearch) {
            this.lifetime = lifetime;
            this.size = size;
            this.spreadTicks = spreadTicks;
            this.fadeIn = fadeIn;
            this.fadeOut = fadeOut;
            this.follows = follows;
            this.spin = spin;
            this.owner = owner;
            this.ownerSearch = ownerSearch;
        }
    }

    private final FabricSpriteSet sprites;
    private final Style style;
    private final float startAngle;
    private @Nullable Entity owner;
    private double heightAboveOwner;

    /** Sent with an x velocity of 1 (a brass golem's blast): the ring stays put instead of following an Artificer. */
    private final boolean standalone;
    /** Size and timing (lifetime, spread, fades) relative to the style: 1 except for a golem's blast rings. */
    private final float sizeScale, timeScale;

    protected PulseRingParticle(ClientLevel level, double x, double y, double z, FabricSpriteSet sprites, Style style,
                                RandomSource random, boolean standalone) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        this.style = style;
        this.standalone = standalone;
        this.sizeScale = standalone ? GOLEM_BLAST_SIZE : 1f;
        this.timeScale = standalone ? GOLEM_BLAST_TIME : 1f;
        this.lifetime = Math.max(1, Math.round(style.lifetime * timeScale));
        this.gravity = 0f;
        this.hasPhysics = false;
        this.xd = 0;
        this.yd = 0;
        this.zd = 0;
        this.startAngle = random.nextFloat() * Mth.TWO_PI;
        this.alpha = style.fadeIn > 0 ? 0f : MAX_ALPHA;
        setSize(style.size * sizeScale * 2f, 0.5f);
        setSpriteFromAge(sprites);
        findOwner();
    }

    /** Ties itself to the nearest matching mob (tried for the first few ticks: the mob may arrive after the particle). */
    private void findOwner() {
        if (!style.follows || standalone) {
            return;
        }
        List<Entity> nearby = level.getEntities((Entity) null, new AABB(x, y, z, x, y, z).inflate(style.ownerSearch),
                e -> e.isAlive() && style.owner.test(e));
        double bestDist = Double.MAX_VALUE;
        for (Entity e : nearby) {
            double d = e.distanceToSqr(x, y, z);
            if (d < bestDist) {
                owner = e;
                bestDist = d;
            }
        }
        if (owner != null) {
            heightAboveOwner = y - owner.getY();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (style.follows) {
            if (owner == null && age <= 5) {
                findOwner();
            }
            if (owner != null && owner.isAlive() && !owner.isRemoved()) {
                setPos(owner.getX(), owner.getY() + heightAboveOwner, owner.getZ());
            }
        }
        if (isAlive()) {
            setSpriteFromAge(sprites);
        }
        float fadeIn = style.fadeIn > 0 ? (age + 1) / (style.fadeIn * timeScale) : 1f;
        float fadeOut = style.fadeOut > 0 ? (lifetime - age) / (style.fadeOut * timeScale) : 1f;
        alpha = MAX_ALPHA * Mth.clamp(Math.min(fadeIn, fadeOut), 0f, 1f);
    }

    @Override
    public float getQuadSize(float partialTick) {
        float time = age + partialTick;
        float spread = 1f;
        if (style.spreadTicks > 0f) {
            spread = Mth.clamp(time / (style.spreadTicks * timeScale), 0f, 1f);
            spread = Math.max(0.1f, 1f - (1f - spread) * (1f - spread)); // eases out like a shockwave
        }
        float throb = 1f + THROB * Mth.sin(time * THROB_SPEED);
        return style.size * sizeScale * spread * throb;
    }

    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        float angle = startAngle + style.spin * SPIN_SPEED * (age + partialTick);
        // Laid flat facing up, and a second copy facing down (particle quads are one-sided).
        extractRotatedQuad(state, camera, new Quaternionf().rotationY(angle).rotateX(-Mth.HALF_PI), partialTick);
        extractRotatedQuad(state, camera, new Quaternionf().rotationY(angle).rotateX(Mth.HALF_PI), partialTick);
    }

    @Override
    public int getLightCoords(float partialTick) {
        return FULL_BRIGHT;
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final FabricSpriteSet sprites;
        private final Style style;

        public Provider(FabricSpriteSet sprites, Style style) {
            this.sprites = sprites;
            this.style = style;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz, RandomSource random) {
            return new PulseRingParticle(level, x, y, z, sprites, style, random, vx > 0.5);
        }
    }
}
