package net.gunmetal.raids.client;

import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A puff of the Trooper's smoke cloud: thrown outward fast, slows down quickly (strong drag), sinks a
 * little and settles. When it runs into a wall or a mob it loses its sideways speed and drifts down
 * under light gravity until it lands; on the ground it stays put. Lives ~9 s, grows a bit, fades out
 * over its last 5 seconds. Lit by the world (not full-bright). Each puff picks one of
 * the textures listed in assets/gunmetal/particles/smoke_cloud.json.
 */
public class SmokeCloudParticle extends SingleQuadParticle {
    // ---- Tuning -------------------------------------------------------------------------
    private static final int LIFETIME = 180, LIFETIME_JITTER = 10;
    /** Half-width in blocks (random between), and how much bigger it gets by the end. */
    private static final float SIZE_MIN = 1.2f, SIZE_MAX = 1.8f, GROWTH = 1.35f;
    /** Velocity kept each tick while drifting (lower = settles sooner), and downward pull. */
    private static final float DRAG = 0.82f, SINK = 0.004f;
    /** Gravity once it has hit a wall or mob (light: it falls ~0.9 blocks/s at most). */
    private static final float SETTLE_GRAVITY = 0.25f;
    /** Mobs only stop puffs this many ticks after the burst (so a direct hit doesn't drop the whole cloud on the target). */
    private static final int MOB_CHECK_AFTER = 4;
    private static final int FADE_TICKS = 100;
    private static final float MAX_ALPHA = 0.85f;
    /** Each puff also creeps this fast (blocks per tick, 0.15 blocks/s) in its own random direction, all its life. */
    private static final double DRIFT_SPEED = 0.0075;
    // -------------------------------------------------------------------------------------

    private final float startSize;
    private final double driftX, driftZ;
    /** On the ground, not moving any more. */
    private boolean stuck;
    /** Hit a wall or mob: no sideways motion, sinking to the ground. */
    private boolean settling;

    protected SmokeCloudParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                                 FabricSpriteSet sprites, RandomSource random) {
        super(level, x, y, z, pickSprite(sprites, random));
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.friction = DRAG;
        this.gravity = SINK;
        this.hasPhysics = true;
        this.lifetime = LIFETIME + random.nextInt(LIFETIME_JITTER * 2 + 1) - LIFETIME_JITTER;
        this.startSize = SIZE_MIN + random.nextFloat() * (SIZE_MAX - SIZE_MIN);
        this.quadSize = startSize;
        this.roll = random.nextFloat() * (float) (Math.PI * 2);
        this.oRoll = roll;
        this.alpha = MAX_ALPHA;
        double angle = random.nextDouble() * Math.PI * 2;
        this.driftX = Math.cos(angle) * DRIFT_SPEED;
        this.driftZ = Math.sin(angle) * DRIFT_SPEED;
    }

    /**
     * The slow creep, done by hand: Particle.move stops for good once a particle has landed, and would
     * also zero its sideways speed when blocked. Walls stop it; a puff can drift out over an edge and
     * hang there, as smoke does.
     */
    private void creep() {
        Vec3 moved = Entity.collideBoundingBox(null, new Vec3(driftX, 0, driftZ), getBoundingBox(), level, List.of());
        if (moved.lengthSqr() > 0) {
            setBoundingBox(getBoundingBox().move(moved));
            setLocationFromBoundingbox();
        }
    }

    private static net.minecraft.client.renderer.texture.TextureAtlasSprite pickSprite(FabricSpriteSet sprites, RandomSource random) {
        List<net.minecraft.client.renderer.texture.TextureAtlasSprite> all = sprites.getSprites();
        return all.isEmpty() ? sprites.first() : all.get(random.nextInt(all.size()));
    }

    @Override
    public void tick() {
        if (stuck) {
            xo = x;
            yo = y;
            zo = z;
            if (age++ >= lifetime) {
                remove();
            }
        } else {
            double vx = xd;
            double vz = zd;
            if (settling) {
                xd = 0;
                zd = 0;
            }
            super.tick();
            if (!settling) {
                // Ran into a wall (lost its sideways speed at once) or into a mob: settle from here.
                boolean blocked = (Math.abs(vx) > 0.02 && Math.abs(xd) < 1.0E-4) || (Math.abs(vz) > 0.02 && Math.abs(zd) < 1.0E-4);
                if (blocked || (age > MOB_CHECK_AFTER && touchesMob())) {
                    settling = true;
                    gravity = SETTLE_GRAVITY;
                    xd = 0;
                    zd = 0;
                }
            }
            if (onGround && (settling || xd * xd + zd * zd < 1.0E-4)) {
                stuck = true;
                xd = 0;
                yd = 0;
                zd = 0;
            }
        }
        if (isAlive()) {
            creep();
        }
        float progress = Mth.clamp((float) age / lifetime, 0f, 1f);
        quadSize = startSize * (1f + (GROWTH - 1f) * progress);
        int left = lifetime - age;
        alpha = MAX_ALPHA * Mth.clamp(left / (float) FADE_TICKS, 0f, 1f);
    }

    /** Whether the puff's centre is inside a living mob's hitbox. */
    private boolean touchesMob() {
        return !level.getEntitiesOfClass(LivingEntity.class, new AABB(x - 0.1, y - 0.1, z - 0.1, x + 0.1, y + 0.1, z + 0.1),
                LivingEntity::isAlive).isEmpty();
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final FabricSpriteSet sprites;

        public Provider(FabricSpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz, RandomSource random) {
            return new SmokeCloudParticle(level, x, y, z, vx, vy, vz, sprites, random);
        }
    }
}
