package net.gunmetal.arms.client.particle;

import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One class for every Realm Arms particle. Colors come only from the textures; this class only
 * decides size, lifetime and movement.
 *
 * Textures (listed in assets/gunmetal/particles/<name>.json) are grouped by name:
 *  - A name ending in _<number> is an animation frame: "smoke_heavy_0", "smoke_heavy_1", ...
 *    Frames with the same base name form one animation, played over the particle's lifetime.
 *  - Different base names are variants: each particle picks one at random when it spawns.
 *    "smoke_heavy_a", "smoke_heavy_b", "smoke_heavy_c" = 3 still variants.
 *    "smoke_heavy_a_0".."smoke_heavy_a_3" + "smoke_heavy_b_0".."smoke_heavy_b_3" = 2 animated variants.
 *  - A single texture with no number is just a still image (what the placeholders are).
 */
public class RealmParticle extends SingleQuadParticle {
    /**
     * Behavior presets. Tune here.
     *   size     - quad size in blocks (half the visible width)
     *   life     - lifetime in ticks, +/- lifeJitter
     *   gravity  - positive falls, negative rises (vanilla scale: 1 = falling-block-ish)
     *   friction - velocity kept each tick (1 = never slows)
     *   growth   - how much bigger it gets by the end of its life (1 = no growth)
     *   emissive - full-bright, ignores world lighting (flashes, sparks, glowing accents)
     *   physics  - collides with blocks (so sinking particles settle on the ground)
     *   spin     - spawns at a random 90-degree rotation (flashes)
     *   delay    - stays invisible for this many ticks first (added on top of its lifetime)
     */
    public enum Style {
        //                size    life jit gravity  friction growth emissive physics spin
        // Trails are all drawn at one size (the textures carry the differences).
        TRAIL_SMALL(0.225f, 10, 3, 0f, 0.90f, 1.4f, false, false, false),
        TRAIL_STANDARD(0.225f, 14, 4, 0f, 0.90f, 1.4f, false, false, false),
        TRAIL_DENSE(0.225f, 18, 4, 0f, 0.90f, 1.4f, false, false, false),
        TRAIL_BLACK(0.225f, 22, 5, 0f, 0.90f, 1.4f, false, false, false),
        TRAIL_CANNONBALL(0.3f, 30, 6, 0f, 0.90f, 1.6f, false, false, false),
        TRAIL_SNIPER(0.225f, 20, 4, 0f, 0.90f, 1.4f, false, false, false),
        /** Blaze: short-lived and glowing. */
        ACCENT_BLAZE(0.2625f, 6, 2, 0f, 0.85f, 1.0f, true, false, false),
        /** Void: 2 seconds. */
        ACCENT_VOID(0.2625f, 40, 4, 0f, 0.85f, 1.0f, false, false, false),
        /** Ice: 2 seconds, sinks slowly and settles on the ground. */
        ACCENT_ICE(0.2625f, 40, 4, 0.1f, 0.96f, 1.0f, false, true, false),
        /** Sunken: as ice, but sinks twice as fast. */
        ACCENT_SUNKEN(0.2625f, 40, 4, 0.2f, 0.96f, 1.0f, false, true, false),
        /** Poison: 3 seconds, drifts off in a random direction. */
        ACCENT_POISON(0.2625f, 60, 6, 0f, 0.96f, 1.0f, false, false, false),
        /** Nuclear: 1.5 seconds, drifts off in a random direction, glowing. */
        ACCENT_NUCLEAR(0.2625f, 30, 4, 0f, 0.96f, 1.0f, true, false, false),
        // Smoke and aftersmoke are all drawn at one size and grow at the same rate.
        SMOKE_SUBTLE(0.45f, 30, 8, -0.08f, 0.92f, 2.0f, false, false, false, SMOKE_DELAY),
        SMOKE_MEDIUM(0.45f, 40, 10, -0.08f, 0.92f, 2.0f, false, false, false, SMOKE_DELAY),
        SMOKE_HEAVY(0.45f, 55, 12, -0.08f, 0.93f, 2.0f, false, false, false, SMOKE_DELAY),
        /** Aftersmoke: the same as the gun's smoke, twice as long-lived. */
        AFTERSMOKE_SUBTLE(0.45f, 60, 16, -0.08f, 0.92f, 2.0f, false, false, false, SMOKE_DELAY),
        AFTERSMOKE_MEDIUM(0.45f, 80, 20, -0.08f, 0.92f, 2.0f, false, false, false, SMOKE_DELAY),
        AFTERSMOKE_HEAVY(0.45f, 110, 24, -0.08f, 0.93f, 2.0f, false, false, false, SMOKE_DELAY),
        // Flashes: all the same size (the textures carry the size differences), keep their starting
        // velocity (friction 1) so they move along with the shooter, and spawn at a random 90-degree turn.
        FLASH_LIGHT(1.294f, 3, 0, 0f, 1.0f, 1.0f, true, false, true),
        FLASH_MEDIUM(1.294f, 3, 0, 0f, 1.0f, 1.0f, true, false, true),
        FLASH_HEAVY(1.294f, 4, 0, 0f, 1.0f, 1.0f, true, false, true),
        FLASH_VOLLEYGUN(1.294f, 3, 0, 0f, 1.0f, 1.0f, true, false, true),
        /** Accent flashes: drawn over the normal flash, gone a tick sooner. */
        FLASH_ACCENT(1.294f, 2, 0, 0f, 1.0f, 1.0f, true, false, true),
        SPARK(0.07f, 8, 3, 0.8f, 0.90f, 1.0f, true, false, false);

        final float size;
        final int life;
        final int lifeJitter;
        final float gravity;
        final float friction;
        final float growth;
        final boolean emissive;
        final boolean physics;
        final boolean spin;
        final int delay;

        Style(float size, int life, int lifeJitter, float gravity, float friction, float growth,
              boolean emissive, boolean physics, boolean spin) {
            this.size = size;
            this.life = life;
            this.lifeJitter = lifeJitter;
            this.gravity = gravity;
            this.friction = friction;
            this.growth = growth;
            this.emissive = emissive;
            this.physics = physics;
            this.spin = spin;
            this.delay = 0;
        }

        Style(float size, int life, int lifeJitter, float gravity, float friction, float growth,
              boolean emissive, boolean physics, boolean spin, int delay) {
            this.size = size;
            this.life = life + delay;
            this.lifeJitter = lifeJitter;
            this.gravity = gravity;
            this.friction = friction;
            this.growth = growth;
            this.emissive = emissive;
            this.physics = physics;
            this.spin = spin;
            this.delay = delay;
        }
    }

    private static final int FULL_BRIGHT = 15728880;
    /**
     * Smoke and aftersmoke stay invisible for this many ticks so they never cover the muzzle flash
     * (flashes last 3-4 ticks). They still drift during the delay, and their visible lifetime is unchanged.
     */
    private static final int SMOKE_DELAY = 4;

    private final List<TextureAtlasSprite> frames;
    private final Style style;
    private final float startSize;

    protected RealmParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                            List<TextureAtlasSprite> frames, Style style) {
        super(level, x, y, z, frames.get(0));
        this.frames = frames;
        this.style = style;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.gravity = style.gravity;
        this.friction = style.friction;
        this.hasPhysics = style.physics;
        this.lifetime = Math.max(1, style.life + (style.lifeJitter > 0 ? random.nextInt(style.lifeJitter * 2 + 1) - style.lifeJitter : 0));
        this.startSize = style.size * (0.85f + random.nextFloat() * 0.3f);
        this.quadSize = startSize;
        if (style.spin) {
            this.roll = random.nextInt(4) * (float) (Math.PI / 2);
            this.oRoll = this.roll;
        }
        updateFrame();
    }

    private void updateFrame() {
        if (frames.size() > 1) {
            int visibleAge = Math.max(0, age - style.delay);
            int index = Math.min(frames.size() - 1, visibleAge * frames.size() / Math.max(1, lifetime - style.delay));
            setSprite(frames.get(index));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (isAlive()) {
            oRoll = roll;
            updateFrame();
            float progress = Math.min(1f, (float) Math.max(0, age - style.delay) / Math.max(1, lifetime - style.delay));
            quadSize = startSize * (1f + (style.growth - 1f) * progress);
        }
    }

    /** Zero size (nothing drawn) while still in its start delay. */
    @Override
    public float getQuadSize(float partialTick) {
        return age < style.delay ? 0f : super.getQuadSize(partialTick);
    }

    @Override
    public int getLightCoords(float partialTick) {
        return style.emissive ? FULL_BRIGHT : super.getLightCoords(partialTick);
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private static final Pattern FRAME = Pattern.compile("^(.*)_(\\d+)$");

        private final FabricSpriteSet sprites;
        private final Style style;
        private List<TextureAtlasSprite> groupedFrom;
        private List<List<TextureAtlasSprite>> variants = List.of();

        public Provider(FabricSpriteSet sprites, Style style) {
            this.sprites = sprites;
            this.style = style;
        }

        /** Groups the texture list into variants of animation frames (re-done after a resource reload). */
        private List<List<TextureAtlasSprite>> variants() {
            List<TextureAtlasSprite> all = sprites.getSprites();
            if (all != groupedFrom) {
                Map<String, List<TextureAtlasSprite>> groups = new LinkedHashMap<>();
                for (TextureAtlasSprite sprite : all) {
                    String name = sprite.contents().name().getPath();
                    name = name.substring(name.lastIndexOf('/') + 1);
                    Matcher frame = FRAME.matcher(name);
                    String base = frame.matches() ? frame.group(1) : name;
                    groups.computeIfAbsent(base, key -> new ArrayList<>()).add(sprite);
                }
                variants = new ArrayList<>(groups.values());
                groupedFrom = all;
            }
            return variants;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz, RandomSource random) {
            List<List<TextureAtlasSprite>> options = variants();
            List<TextureAtlasSprite> frames = options.isEmpty()
                    ? List.of(sprites.first())
                    : options.get(options.size() == 1 ? 0 : random.nextInt(options.size()));
            return new RealmParticle(level, x, y, z, vx, vy, vz, frames, style);
        }
    }
}
