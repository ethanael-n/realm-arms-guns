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

import java.util.List;

/**
 * Siege Balloon death visuals (client only, spawned with the death event, no network cost):
 *   RING  - faces the camera, grows from RING_START to RING_END blocks across over RING_TICKS (fast at
 *           first, easing out), full-bright, fades over its last RING_FADE_TICKS.
 *           Textures: particles/balloon_death_ring.json (several = animation frames over its life).
 *   SMOKE - SMOKE_SIZE blocks across, appears at once, hangs SMOKE_HOLD_TICKS with a slow random drift
 *           (no gravity, passes through blocks), then fades over SMOKE_FADE_TICKS. Lit by the world.
 *           Textures: particles/balloon_death_smoke.json (each puff picks one at random).
 */
public class BalloonDeathParticle extends SingleQuadParticle {
    // ---- Tuning -------------------------------------------------------------------------
    public static final float RING_START = 6.0f, RING_END = 18.0f; // 1.0.1: x3 (was 2 -> 6)
    public static final int RING_TICKS = 10, RING_FADE_TICKS = 4;
    public static final float SMOKE_SIZE = 3.75f, SMOKE_ALPHA = 0.85f; // 1.0.1: +25% (was 3)
    public static final int SMOKE_HOLD_TICKS = 80, SMOKE_FADE_TICKS = 40;
    /** Smoke drift (blocks per tick; 0.0075 = 0.15 blocks/s, like the Trooper's smoke). */
    public static final double SMOKE_DRIFT = 0.0075;
    // -------------------------------------------------------------------------------------

    public enum Style { RING, SMOKE }

    private final Style style;
    private final FabricSpriteSet sprites;

    protected BalloonDeathParticle(ClientLevel level, double x, double y, double z, FabricSpriteSet sprites,
                                   RandomSource random, Style style) {
        super(level, x, y, z, pick(sprites, random, style));
        this.style = style;
        this.sprites = sprites;
        this.hasPhysics = false;
        this.gravity = 0f;
        this.friction = 1f;
        if (style == Style.RING) {
            this.lifetime = RING_TICKS;
            this.quadSize = RING_START / 2f;
            this.xd = this.yd = this.zd = 0;
        } else {
            this.lifetime = SMOKE_HOLD_TICKS + SMOKE_FADE_TICKS;
            this.quadSize = SMOKE_SIZE / 2f;
            this.alpha = SMOKE_ALPHA;
            this.roll = random.nextFloat() * Mth.TWO_PI;
            this.oRoll = roll;
            double yaw = random.nextDouble() * Math.PI * 2;
            double pitch = (random.nextDouble() - 0.5) * Math.PI;
            this.xd = Math.cos(yaw) * Math.cos(pitch) * SMOKE_DRIFT;
            this.yd = Math.sin(pitch) * SMOKE_DRIFT;
            this.zd = Math.sin(yaw) * Math.cos(pitch) * SMOKE_DRIFT;
        }
    }

    private static TextureAtlasSprite pick(FabricSpriteSet sprites, RandomSource random, Style style) {
        List<TextureAtlasSprite> all = sprites.getSprites();
        if (all.isEmpty() || style == Style.RING) {
            return sprites.first();
        }
        return all.get(random.nextInt(all.size()));
    }

    @Override
    public void tick() {
        super.tick();
        if (style == Style.RING) {
            List<TextureAtlasSprite> frames = sprites.getSprites();
            if (frames.size() > 1) {
                setSprite(frames.get(Math.min(frames.size() - 1, age * frames.size() / Math.max(1, lifetime))));
            }
            alpha = Mth.clamp((lifetime - age) / (float) RING_FADE_TICKS, 0f, 1f);
        } else {
            int left = lifetime - age;
            alpha = SMOKE_ALPHA * Mth.clamp(left / (float) SMOKE_FADE_TICKS, 0f, 1f);
        }
    }

    @Override
    public float getQuadSize(float partialTick) {
        if (style != Style.RING) {
            return quadSize;
        }
        float t = Mth.clamp((age + partialTick) / RING_TICKS, 0f, 1f);
        float eased = 1f - (1f - t) * (1f - t);
        return (RING_START + (RING_END - RING_START) * eased) / 2f;
    }

    @Override
    public int getLightCoords(float partialTick) {
        return style == Style.RING ? 15728880 : super.getLightCoords(partialTick);
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
            return new BalloonDeathParticle(level, x, y, z, sprites, random, style);
        }
    }
}
