package net.gunmetal.arms.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.core.particles.SimpleParticleType;
import net.gunmetal.arms.client.particle.RealmParticle;
import net.gunmetal.arms.client.particle.ShotRingParticle;
import net.gunmetal.arms.client.particle.RealmParticle.Style;
import net.gunmetal.arms.entity.RealmBulletEntity;
import net.gunmetal.arms.guns.FireHooks;
import net.gunmetal.arms.registry.RealmParticles;

public class RealmArmsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(RealmBulletEntity.ENTITY_TYPE, RealmBulletRenderer::new);

        FireHooks.setClientListener(GunRecoil::onFire);
        FireHooks.setClipShotListener(() -> GunClient.preventFiring = true);
        FireHooks.setClipShotSentListener(offhand -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new net.gunmetal.arms.network.ClipShotPayload(offhand)));
        ClientTickEvents.END_CLIENT_TICK.register(GunRecoil::tick);
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, GunCrosshair::wrap);

        particle(RealmParticles.TRAIL_SMALL, Style.TRAIL_SMALL);
        particle(RealmParticles.TRAIL_STANDARD, Style.TRAIL_STANDARD);
        particle(RealmParticles.TRAIL_DENSE, Style.TRAIL_DENSE);
        particle(RealmParticles.TRAIL_BLACK, Style.TRAIL_BLACK);
        particle(RealmParticles.TRAIL_CANNONBALL, Style.TRAIL_CANNONBALL);
        particle(RealmParticles.TRAIL_SNIPER, Style.TRAIL_SNIPER);
        ParticleProviderRegistry.getInstance().register(RealmParticles.TRAIL_SNIPER_RING,
                (ParticleProviderRegistry.PendingParticleProvider<SimpleParticleType>) ShotRingParticle.Provider::new);

        particle(RealmParticles.ACCENT_BLAZE, Style.ACCENT_BLAZE);
        particle(RealmParticles.ACCENT_ICE, Style.ACCENT_ICE);
        particle(RealmParticles.ACCENT_NUCLEAR, Style.ACCENT_NUCLEAR);
        particle(RealmParticles.ACCENT_VOID, Style.ACCENT_VOID);
        particle(RealmParticles.ACCENT_SUNKEN, Style.ACCENT_SUNKEN);
        particle(RealmParticles.ACCENT_POISON, Style.ACCENT_POISON);

        particle(RealmParticles.SMOKE_SUBTLE, Style.SMOKE_SUBTLE);
        particle(RealmParticles.SMOKE_MEDIUM, Style.SMOKE_MEDIUM);
        particle(RealmParticles.SMOKE_HEAVY, Style.SMOKE_HEAVY);
        particle(RealmParticles.AFTERSMOKE_SUBTLE, Style.AFTERSMOKE_SUBTLE);
        particle(RealmParticles.AFTERSMOKE_MEDIUM, Style.AFTERSMOKE_MEDIUM);
        particle(RealmParticles.AFTERSMOKE_HEAVY, Style.AFTERSMOKE_HEAVY);

        particle(RealmParticles.FLASH_LIGHT, Style.FLASH_LIGHT);
        particle(RealmParticles.FLASH_MEDIUM, Style.FLASH_MEDIUM);
        particle(RealmParticles.FLASH_HEAVY, Style.FLASH_HEAVY);
        particle(RealmParticles.FLASH_VOLLEYGUN, Style.FLASH_VOLLEYGUN);
        particle(RealmParticles.FLASH_ACCENT_BLAZE, Style.FLASH_ACCENT);
        particle(RealmParticles.FLASH_ACCENT_ICE, Style.FLASH_ACCENT);
        particle(RealmParticles.FLASH_ACCENT_NUCLEAR, Style.FLASH_ACCENT);
        particle(RealmParticles.FLASH_ACCENT_VOID, Style.FLASH_ACCENT);
        particle(RealmParticles.FLASH_ACCENT_SUNKEN, Style.FLASH_ACCENT);
        particle(RealmParticles.FLASH_ACCENT_POISON, Style.FLASH_ACCENT);
        particle(RealmParticles.FLASH_ACCENT_RIFLE, Style.FLASH_ACCENT);

        particle(RealmParticles.SPARK_COPPER, Style.SPARK);
        particle(RealmParticles.SPARK_BRASS, Style.SPARK);
        particle(RealmParticles.SPARK_STEEL, Style.SPARK);
    }

    private static void particle(SimpleParticleType type, Style style) {
        ParticleProviderRegistry.getInstance().register(type,
                (ParticleProviderRegistry.PendingParticleProvider<SimpleParticleType>) sprites -> new RealmParticle.Provider(sprites, style));
    }
}
