package net.gunmetal.raids.client;

import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteSet;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.gunmetal.raids.entity.Sharpshooter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Sharpshooter's scope glint and windup. Full-bright, stays put, and drawn bigger the further the
 * camera is, so it reads about the same on screen at 10 or 80 blocks. Each one ties itself to the
 * nearest Sharpshooter when it appears and disappears at once if that Sharpshooter dies.
 *   Glint:  5 s, flickers a little. Each glint picks one of the textures in particles/scope_glint.json
 *           at random (variants).
 *   Windup: 3 s, no flicker, 1.5x the glint's size, turned 45 degrees (a diamond); its textures
 *           (particles/scope_windup.json) play in order as animation frames over its lifetime.
 */
public class ScopeParticle extends SingleQuadParticle {
    // ---- Tuning -------------------------------------------------------------------------
    /** Lifetimes (ticks): match Sharpshooter.GLINT_TICKS and WINDUP_TICKS. */
    public static final int GLINT_LIFETIME = 100, WINDUP_LIFETIME = 60;
    /** Half-width in blocks up close, and per block of camera distance beyond CLOSE_DISTANCE. */
    private static final float BASE_SIZE = 0.5f, SIZE_PER_BLOCK = 0.04f;
    private static final float CLOSE_DISTANCE = 8.0f;
    private static final float MAX_SIZE = 5.0f;
    /** Glint flicker amplitude (share of the size) and speed. */
    private static final float FLICKER = 0.15f, FLICKER_SPEED = 1.95f;
    /** The windup is drawn this much bigger than the glint, turned this far (radians, 45 degrees). */
    private static final float WINDUP_SCALE = 1.0f, WINDUP_ROLL = Mth.PI / 4f;
    /** How far from where it appears to look for its Sharpshooter. */
    private static final double OWNER_SEARCH = 4.0;
    // -------------------------------------------------------------------------------------

    private static final int FULL_BRIGHT = 15728880;
    private final boolean windup;
    private final FabricSpriteSet sprites;
    private final @Nullable Sharpshooter owner;
    private float cameraDistance;

    protected ScopeParticle(ClientLevel level, double x, double y, double z, FabricSpriteSet sprites, boolean windup) {
        super(level, x, y, z, sprites.first());
        this.windup = windup;
        this.sprites = sprites;
        this.lifetime = windup ? WINDUP_LIFETIME : GLINT_LIFETIME;
        this.gravity = 0f;
        this.hasPhysics = false;
        this.xd = 0;
        this.yd = 0;
        this.zd = 0;
        this.quadSize = BASE_SIZE;
        this.owner = findOwner(level, x, y, z);
        if (windup) {
            setSpriteFromAge(sprites);
            roll = WINDUP_ROLL;
            oRoll = WINDUP_ROLL;
        } else {
            List<TextureAtlasSprite> all = sprites.getSprites();
            if (all.size() > 1) {
                setSprite(all.get(random.nextInt(all.size())));
            }
        }
    }

    private static @Nullable Sharpshooter findOwner(ClientLevel level, double x, double y, double z) {
        List<Sharpshooter> nearby = level.getEntitiesOfClass(Sharpshooter.class,
                new AABB(x, y, z, x, y, z).inflate(OWNER_SEARCH), Sharpshooter::isAlive);
        Sharpshooter best = null;
        double bestDist = Double.MAX_VALUE;
        for (Sharpshooter s : nearby) {
            double d = s.distanceToSqr(x, y, z);
            if (d < bestDist) {
                best = s;
                bestDist = d;
            }
        }
        return best;
    }

    @Override
    public void tick() {
        super.tick();
        if (owner != null && (!owner.isAlive() || owner.isRemoved())) {
            remove();
            return;
        }
        if (windup && isAlive()) {
            setSpriteFromAge(sprites);
        }
    }

    private float sizeScale() {
        float size = BASE_SIZE + SIZE_PER_BLOCK * Math.max(0f, cameraDistance - CLOSE_DISTANCE);
        return Math.min(MAX_SIZE, size) / BASE_SIZE;
    }

    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        Vec3 cam = camera.position();
        cameraDistance = (float) Math.sqrt(cam.distanceToSqr(x, y, z));
        super.extract(state, camera, partialTick);
    }

    @Override
    public float getQuadSize(float partialTick) {
        float size = BASE_SIZE * sizeScale();
        if (windup) {
            return size * WINDUP_SCALE;
        }
        float time = age + partialTick;
        float flicker = 1f + FLICKER * Mth.sin(time * FLICKER_SPEED);
        // Fade in over the first 3 ticks, out over the last 3.
        float fade = Mth.clamp(Math.min(time / 3f, (lifetime - time) / 3f), 0f, 1f);
        return size * flicker * fade;
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
        private final boolean windup;

        public Provider(FabricSpriteSet sprites, boolean windup) {
            this.sprites = sprites;
            this.windup = windup;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz, RandomSource random) {
            return new ScopeParticle(level, x, y, z, sprites, windup);
        }
    }
}
