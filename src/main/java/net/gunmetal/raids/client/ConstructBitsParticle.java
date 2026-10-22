package net.gunmetal.raids.client;

import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Debris from the Artificer's summons ("construct bits"): a big piece thrown out with the given
 * velocity that falls, bounces off floors and walls (losing speed each time), tumbles, and vanishes
 * after 1.5 s (fading over the last few ticks). Each piece picks one of the textures listed in
 * assets/gunmetal/particles/construct_bits.json. Movement is done by hand (vanilla particles stop
 * for good on their first landing).
 */
public class ConstructBitsParticle extends SingleQuadParticle {
    // ---- Tuning -------------------------------------------------------------------------
    private static final int LIFETIME = 30, FADE_TICKS = 6;
    /** Half-width in blocks (the Sharpshooter's shot ring is 0.5), and the half-size of its collision box (centred on it). */
    private static final float SIZE = 0.2625f; // 1.5.2: -30% (1.5.1: 0.375, 1.5.0: 0.5)
    private static final double BOX = SIZE * 0.5; // half the drawn size: rests on floors
    private static final float GRAVITY = 0.06f, AIR_DRAG = 0.98f;
    /** Bounces keep this much of their speed (up, and sideways on landing / off walls). */
    private static final double BOUNCE = 0.45, LANDING_SLIDE = 0.7;
    /** Tumbling (radians per tick, random up to this either way); slows on each bounce. */
    private static final float MAX_SPIN = 0.5f;
    // -------------------------------------------------------------------------------------

    private float spin;
    /**
     * Burning debris: drawn full-bright, upright and never turning, sticks where it lands (no bounces),
     * less gravity, lasts longer, and shrinks over its life like a vanilla flame (to half size, then the
     * usual short fade).
     */
    private boolean burning;
    public static final float BURNING_GRAVITY = 0.49f;
    public static final int BURNING_EXTRA_LIFETIME = 80;

    protected ConstructBitsParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                                    FabricSpriteSet sprites, RandomSource random) {
        super(level, x, y, z, pickSprite(sprites, random));
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = LIFETIME;
        this.quadSize = SIZE;
        this.hasPhysics = false; // moved by hand below
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        this.spin = (random.nextFloat() * 2f - 1f) * MAX_SPIN;
        setBoundingBox(new net.minecraft.world.phys.AABB(x - BOX, y - BOX, z - BOX, x + BOX, y + BOX, z + BOX));
    }

    private static TextureAtlasSprite pickSprite(FabricSpriteSet sprites, RandomSource random) {
        List<TextureAtlasSprite> all = sprites.getSprites();
        return all.isEmpty() ? sprites.first() : all.get(random.nextInt(all.size()));
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        yd -= burning ? GRAVITY * BURNING_GRAVITY : GRAVITY;
        Vec3 wanted = new Vec3(xd, yd, zd);
        Vec3 moved = Entity.collideBoundingBox(null, wanted, getBoundingBox(), level, List.of());
        if (moved.lengthSqr() > 0) {
            setBoundingBox(getBoundingBox().move(moved));
            // The box is centred on the piece (vanilla puts a particle's position at the box's bottom).
            net.minecraft.world.phys.AABB box = getBoundingBox();
            x = (box.minX + box.maxX) * 0.5;
            y = (box.minY + box.maxY) * 0.5;
            z = (box.minZ + box.maxZ) * 0.5;
        }
        if (burning && (moved.y != wanted.y || moved.x != wanted.x || moved.z != wanted.z)) {
            // Sticks: stops dead on whatever it touches.
            xd = moved.x != wanted.x ? 0 : xd;
            zd = moved.z != wanted.z ? 0 : zd;
            if (moved.y != wanted.y) {
                xd = 0;
                yd = 0;
                zd = 0;
                spin = 0;
            }
        } else if (moved.y != wanted.y) {
            boolean landed = wanted.y < 0;
            yd = -yd * BOUNCE;
            if (Math.abs(yd) < 0.04) {
                yd = 0;
            }
            if (landed) {
                xd *= LANDING_SLIDE;
                zd *= LANDING_SLIDE;
            }
            spin *= 0.6f;
        }
        if (moved.x != wanted.x) {
            xd = -xd * BOUNCE;
        }
        if (moved.z != wanted.z) {
            zd = -zd * BOUNCE;
        }
        xd *= AIR_DRAG;
        zd *= AIR_DRAG;
        roll += spin;
        alpha = Mth.clamp((lifetime - age) / (float) FADE_TICKS, 0f, 1f);
    }

    /** Burning: the vanilla flame's shrink (1 - 0.5 x life fraction squared). */
    @Override
    public float getQuadSize(float partialTick) {
        if (!burning) {
            return quadSize;
        }
        float life = Math.min(1f, (age + partialTick) / lifetime);
        return quadSize * (1f - life * life * 0.5f);
    }

    @Override
    public int getLightCoords(float partialTick) {
        return burning ? 15728880 : super.getLightCoords(partialTick);
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final FabricSpriteSet sprites;
        private final boolean bright;
        /** Lifetime range in ticks (-1 = the normal LIFETIME). */
        private final int minLife, maxLife;

        public Provider(FabricSpriteSet sprites) {
            this(sprites, false);
        }

        /** burning = the Siege Balloon's burning debris (see the burning field). */
        public Provider(FabricSpriteSet sprites, boolean burning) {
            this(sprites, burning, -1, -1);
        }

        /** Normal pieces lasting a random minLife to maxLife ticks (envelope tatters: 4-12 s, 1.0.3). */
        public Provider(FabricSpriteSet sprites, boolean burning, int minLife, int maxLife) {
            this.sprites = sprites;
            this.bright = burning;
            this.minLife = minLife;
            this.maxLife = maxLife;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz, RandomSource random) {
            ConstructBitsParticle particle = new ConstructBitsParticle(level, x, y, z, vx, vy, vz, sprites, random);
            particle.burning = bright;
            if (minLife > 0) {
                particle.lifetime = minLife + random.nextInt(Math.max(1, maxLife - minLife + 1));
            }
            if (bright) {
                particle.lifetime += BURNING_EXTRA_LIFETIME;
                particle.spin = 0f;
                particle.roll = 0f;
                particle.oRoll = 0f;
            }
            return particle;
        }
    }
}
