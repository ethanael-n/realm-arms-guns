package net.gunmetal.arms.entity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Pellet damage: every pellet that hits a target during a tick is added up and dealt as one hit
 * at the end of the tick, so damage immunity doesn't eat all but the first pellet.
 * (Ported from Musket Mod Reloaded, MIT.)
 */
public final class DeferredDamage {
    private static final Map<Entity, Entry> ENTRIES = new HashMap<>();

    private static final class Entry {
        DamageSource source;
        float damage;
        float igniteSeconds;
    }

    private DeferredDamage() {
    }

    public static void register() {
        ServerTickEvents.END_LEVEL_TICK.register(level -> apply());
    }

    public static void hurt(Entity target, DamageSource source, float damage, float igniteSeconds) {
        Entry entry = ENTRIES.computeIfAbsent(target, t -> new Entry());
        entry.source = source;
        entry.damage += damage;
        entry.igniteSeconds = Math.min(5.0f, entry.igniteSeconds + igniteSeconds);
    }

    private static void apply() {
        if (ENTRIES.isEmpty()) {
            return;
        }
        Map<Entity, Entry> pending = new HashMap<>(ENTRIES);
        ENTRIES.clear();
        pending.forEach((target, entry) -> {
            target.invulnerableTime = 0;
            target.hurt(entry.source, entry.damage);
            if (entry.igniteSeconds > 0.0f) {
                target.igniteForSeconds(entry.igniteSeconds);
            }
        });
    }
}
