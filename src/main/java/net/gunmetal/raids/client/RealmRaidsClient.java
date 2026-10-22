package net.gunmetal.raids.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.animal.golem.CopperGolemModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.particle.GlowParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.Identifier;
import net.gunmetal.raids.registry.RaidEntities;
import net.gunmetal.raids.registry.RaidParticles;

import static net.gunmetal.raids.RealmRaids.MOD_ID;

/**
 * Renderers: each mob has its own model layer (gunmetal:<mob>#main: illager geometry for the
 * illagers, bee for the drone, copper golem for the brass golem) and texture, so a resource
 * pack / EMF can replace each one separately. The Bombardier's hand cannon is not drawn (its cannon
 * belongs to its model).
 */
public class RealmRaidsClient implements ClientModInitializer {
    public static final ModelLayerLocation BOMBARDIER_LAYER = layer("bombardier");
    public static final ModelLayerLocation SHARPSHOOTER_LAYER = layer("sharpshooter");
    public static final ModelLayerLocation TROOPER_LAYER = layer("trooper");
    public static final ModelLayerLocation ARTIFICER_LAYER = layer("artificer");
    public static final ModelLayerLocation DRONE_LAYER = layer("drone");
    public static final ModelLayerLocation BRASS_GOLEM_LAYER = layer("brass_golem");
    public static final ModelLayerLocation PUPPETED_INCOMPLETE_LAYER = layer("puppeted_incomplete");
    public static final ModelLayerLocation PUPPETED_COMPLETE_LAYER = layer("puppeted_complete");
    public static final ModelLayerLocation SIEGE_BALLOON_LAYER = layer("siege_balloon");
    public static final ModelLayerLocation AVIATOR_LAYER = layer("aviator");

    public static final float ILLAGER_SCALE = 0.9375f;
    /** Envelope tatters (Siege Balloon) last a random 4-12 s (1.0.3). */
    public static final int TATTERS_MIN_LIFE = 80, TATTERS_MAX_LIFE = 240;

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(Identifier.fromNamespaceAndPath(MOD_ID, name), "main");
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(MOD_ID, "textures/entity/illager/" + name + ".png");
    }

    private static Identifier entityTexture(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, "textures/entity/" + path + ".png");
    }

    @Override
    public void onInitializeClient() {
        // Vanilla draws illagers at 93.75% (pillager size); the Bombardier stays at full size, a little bigger.
        ModelLayerRegistry.registerModelLayer(BOMBARDIER_LAYER, IllagerModel::createBodyLayer);
        for (ModelLayerLocation layer : java.util.List.of(SHARPSHOOTER_LAYER, TROOPER_LAYER, AVIATOR_LAYER)) {
            ModelLayerRegistry.registerModelLayer(layer, () -> IllagerModel.createBodyLayer().apply(MeshTransformer.scaling(ILLAGER_SCALE)));
        }
        // The Artificer's layer also carries its empty summon telegraph parts (RaidIllagerModel).
        ModelLayerRegistry.registerModelLayer(ARTIFICER_LAYER, () -> RaidIllagerModel.addSummonSignals(
                IllagerModel.createBodyLayer().apply(MeshTransformer.scaling(ILLAGER_SCALE))));
        ModelLayerRegistry.registerModelLayer(DRONE_LAYER, DroneModel::createBodyLayer);
        ModelLayerRegistry.registerModelLayer(BRASS_GOLEM_LAYER, BrassGolemModel::createBodyLayer);
        ModelLayerRegistry.registerModelLayer(PUPPETED_INCOMPLETE_LAYER, PuppetedModel::createBodyLayer);
        ModelLayerRegistry.registerModelLayer(PUPPETED_COMPLETE_LAYER, PuppetedModel::createBodyLayer);
        ModelLayerRegistry.registerModelLayer(SIEGE_BALLOON_LAYER, SiegeBalloonModel::createBodyLayer);
        EntityRendererRegistry.register(RaidEntities.BOMBARDIER,
                context -> new RaidIllagerRenderer<>(context, BOMBARDIER_LAYER, texture("bombardier"), false));
        EntityRendererRegistry.register(RaidEntities.SHARPSHOOTER,
                context -> new RaidIllagerRenderer<>(context, SHARPSHOOTER_LAYER, texture("sharpshooter"), true));
        EntityRendererRegistry.register(RaidEntities.TROOPER,
                context -> new RaidIllagerRenderer<>(context, TROOPER_LAYER, texture("trooper"), texture("trooper_officer"), true));
        EntityRendererRegistry.register(RaidEntities.ARTIFICER,
                context -> new RaidIllagerRenderer<>(context, ARTIFICER_LAYER, texture("artificer"), false));
        EntityRendererRegistry.register(RaidEntities.DRONE, context -> new DroneRenderer(context, DRONE_LAYER, entityTexture("drone/drone")));
        EntityRendererRegistry.register(RaidEntities.BRASS_GOLEM, context -> new BrassGolemRenderer<>(context, BRASS_GOLEM_LAYER));
        EntityRendererRegistry.register(RaidEntities.DEPLOYED_GOLEM, context -> new BrassGolemRenderer<>(context, BRASS_GOLEM_LAYER));
        EntityRendererRegistry.register(RaidEntities.PUPPETED,
                context -> new PuppetedRenderer(context, PUPPETED_INCOMPLETE_LAYER, PUPPETED_COMPLETE_LAYER));
        EntityRendererRegistry.register(RaidEntities.THROWN_BRASS_DEPLOYABLE, ThrownItemRenderer::new);
        EntityRendererRegistry.register(RaidEntities.SIEGE_BALLOON, context -> new SiegeBalloonRenderer(context, SIEGE_BALLOON_LAYER,
                new Identifier[]{entityTexture("siege_balloon/siege_balloon"), entityTexture("siege_balloon/siege_balloon_damaged"),
                        entityTexture("siege_balloon/siege_balloon_burning")},
                entityTexture("siege_balloon/tether")));
        EntityRendererRegistry.register(RaidEntities.BALLOON_ARROW, net.minecraft.client.renderer.entity.TippableArrowRenderer::new);
        EntityRendererRegistry.register(RaidEntities.AVIATOR,
                context -> new RaidIllagerRenderer<>(context, AVIATOR_LAYER, true, texture("aviator_pilot"),
                        texture("aviator_airman_a"), texture("aviator_airman_b"), texture("aviator_airman_c")));

        ParticleProviderRegistry.getInstance().register(RaidParticles.SCOPE_GLINT,
                (ParticleProviderRegistry.PendingParticleProvider<SimpleParticleType>) sprites -> new ScopeParticle.Provider(sprites, false));
        ParticleProviderRegistry.getInstance().register(RaidParticles.SCOPE_WINDUP,
                (ParticleProviderRegistry.PendingParticleProvider<SimpleParticleType>) sprites -> new ScopeParticle.Provider(sprites, true));
        ParticleProviderRegistry.getInstance().register(RaidParticles.SMOKE_CLOUD,
                (ParticleProviderRegistry.PendingParticleProvider<SimpleParticleType>) SmokeCloudParticle.Provider::new);
        // Pulse rings (timings in PulseRingParticle.Style) and the summon marker.
        ParticleProviderRegistry.getInstance().register(RaidParticles.PULSE_RING_LOW,
                (ParticleProviderRegistry.PendingParticleProvider<SimpleParticleType>) sprites -> new PulseRingParticle.Provider(sprites, PulseRingParticle.Style.PULSE_LOW));
        ParticleProviderRegistry.getInstance().register(RaidParticles.PULSE_RING_MID,
                (ParticleProviderRegistry.PendingParticleProvider<SimpleParticleType>) sprites -> new PulseRingParticle.Provider(sprites, PulseRingParticle.Style.PULSE_MID));
        ParticleProviderRegistry.getInstance().register(RaidParticles.PULSE_RING_TOP,
                (ParticleProviderRegistry.PendingParticleProvider<SimpleParticleType>) sprites -> new PulseRingParticle.Provider(sprites, PulseRingParticle.Style.PULSE_TOP));
        ParticleProviderRegistry.getInstance().register(RaidParticles.SUMMON_MARKER,
                (ParticleProviderRegistry.PendingParticleProvider<SimpleParticleType>) sprites -> new PulseRingParticle.Provider(sprites, PulseRingParticle.Style.SUMMON_MARKER));
        ParticleProviderRegistry.getInstance().register(RaidParticles.CONSTRUCT_BITS,
                (ParticleProviderRegistry.PendingParticleProvider<SimpleParticleType>) ConstructBitsParticle.Provider::new);
        ParticleProviderRegistry.getInstance().register(RaidParticles.ENVELOPE_TATTERS,
                (ParticleProviderRegistry.PendingParticleProvider<SimpleParticleType>) sprites -> new ConstructBitsParticle.Provider(
                        sprites, false, TATTERS_MIN_LIFE, TATTERS_MAX_LIFE));
        ParticleProviderRegistry.getInstance().register(RaidParticles.BALLOON_DEATH_RING,
                (ParticleProviderRegistry.PendingParticleProvider<SimpleParticleType>) sprites -> new BalloonDeathParticle.Provider(sprites, BalloonDeathParticle.Style.RING));
        ParticleProviderRegistry.getInstance().register(RaidParticles.BALLOON_DEATH_SMOKE,
                (ParticleProviderRegistry.PendingParticleProvider<SimpleParticleType>) sprites -> new BalloonDeathParticle.Provider(sprites, BalloonDeathParticle.Style.SMOKE));
        ParticleProviderRegistry.getInstance().register(RaidParticles.BURNING_DEBRIS,
                (ParticleProviderRegistry.PendingParticleProvider<SimpleParticleType>) sprites -> new ConstructBitsParticle.Provider(sprites, true));
        ParticleProviderRegistry.getInstance().register(RaidParticles.ARTIFICER_SPARK,
                (ParticleProviderRegistry.PendingParticleProvider<SimpleParticleType>) GlowParticle.ElectricSparkProvider::new);
        // The projectile-zap sparks: the same particle, 1.5x bigger.
        ParticleProviderRegistry.getInstance().register(RaidParticles.ARTIFICER_ZAP,
                (ParticleProviderRegistry.PendingParticleProvider<SimpleParticleType>) sprites -> {
                    GlowParticle.ElectricSparkProvider sparks = new GlowParticle.ElectricSparkProvider(sprites);
                    return (type, level, x, y, z, vx, vy, vz, random) -> {
                        Particle particle = sparks.createParticle(type, level, x, y, z, vx, vy, vz, random);
                        return particle == null ? null : particle.scale(1.5f);
                    };
                });
    }
}
