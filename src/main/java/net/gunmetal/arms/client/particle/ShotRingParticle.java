package net.gunmetal.arms.client.particle;

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
import org.joml.Quaternionf;

/**
 * A flat square left along a sniper shot (Realm Raids' Sharpshooter): its face looks down the shot's
 * path, it doesn't move, and it vanishes after its lifetime. Drawn from both sides (particle quads are
 * one-sided). The spawn "velocity" carries the shot's direction. Texture: particles/trail_sniper_ring.json
 * (several textures = animation frames over the lifetime).
 */
public class ShotRingParticle extends SingleQuadParticle {
    /** Half the visible width in blocks (0.5 = one block across). */
    private static final float SIZE = 0.5f;
    /** Lifetime in ticks (0.5 s). */
    private static final int LIFETIME = 10;

    private final FabricSpriteSet sprites;
    private final Quaternionf front;
    private final Quaternionf back;

    protected ShotRingParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
                               FabricSpriteSet sprites) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        this.lifetime = LIFETIME;
        this.gravity = 0f;
        this.hasPhysics = false;
        this.xd = 0;
        this.yd = 0;
        this.zd = 0;
        this.quadSize = SIZE;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-4f) {
            dx = 0;
            dy = 0;
            dz = 1;
            length = 1f;
        }
        // The quad faces +Z before rotation; turn it to face along the shot, and a copy facing back.
        this.front = new Quaternionf().rotationTo(0f, 0f, 1f, (float) dx / length, (float) dy / length, (float) dz / length);
        this.back = new Quaternionf(front).rotateY(Mth.PI);
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        // No movement: just age and step through the animation frames.
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        setSpriteFromAge(sprites);
    }

    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        extractRotatedQuad(state, camera, front, partialTick);
        extractRotatedQuad(state, camera, back, partialTick);
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
            return new ShotRingParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
