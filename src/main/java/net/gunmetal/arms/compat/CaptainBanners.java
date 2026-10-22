package net.gunmetal.arms.compat;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;

import java.util.ArrayList;
import java.util.List;

/**
 * Gives patrol, outpost and raid captains their ominous banner back.
 *
 * Armored Foes clears a mob's armor slots before applying its equipment table when the mob is in
 * {@code armoredfoes:discards_original_equipment} (the pack adds pillagers and vindicators), and the
 * captain banner sits in the head slot, so captains spawned without it: no banner on their head,
 * no ominous bottle drop (vanilla checks for the banner), no Voluntary Exile. Captains are queued
 * when they're added to the world and checked at the end of the tick, after Armored Foes has run,
 * whatever order the two mods' listeners fire in. Costs one check per spawned raider.
 */
public final class CaptainBanners {
    private static final List<Raider> PENDING = new ArrayList<>();

    private CaptainBanners() {
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Raider raider && raider.isPatrolLeader()) {
                PENDING.add(raider);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (PENDING.isEmpty()) {
                return;
            }
            for (Raider raider : PENDING) {
                if (raider.isAlive() && raider.isPatrolLeader() && raider.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                    raider.setItemSlot(EquipmentSlot.HEAD,
                            Raid.getOminousBannerInstance(raider.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
                    raider.setDropChance(EquipmentSlot.HEAD, 2.0f);
                }
            }
            PENDING.clear();
        });
    }
}
