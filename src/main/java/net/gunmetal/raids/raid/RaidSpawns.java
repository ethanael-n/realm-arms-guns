package net.gunmetal.raids.raid;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.Map;
import java.util.Optional;

import static net.gunmetal.raids.RealmRaids.MOD_ID;

/**
 * Adds extra raiders to each raid wave, from data/gunmetal/raid_spawns.json (a datapack can replace
 * it; it's re-read every wave, so /reload applies at once). Format:
 *
 *   { "spawns": { "<entity id>": { "<wave>": { "chance": 0.5, "min": 1, "max": 2 }, ... }, ... } }
 *
 * Waves count from 1 as the raid bar shows them (the bonus wave on Hard with a high enough raid omen
 * is wave 8). A wave that isn't listed uses the closest listed wave below it (so "7" also covers 8+),
 * and waves below the lowest listed one get none. Per wave: with probability "chance" (default 1),
 * spawn a uniform random count from "min" to "max" (both default 1); otherwise none. Optional
 * "bonus_chance": a separate roll for one more (whether or not the first roll spawned any); or a list
 * of chances, each its own independent roll for one more (1.0.3).
 * Any raider entity works, including vanilla and other mods' raiders.
 */
public final class RaidSpawns {
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Identifier FILE = Identifier.fromNamespaceAndPath(MOD_ID, "raid_spawns.json");

    private RaidSpawns() {
    }

    /** Called from RaidMixin while the vanilla wave spawns, before the raid bar is updated. */
    public static void spawnExtras(Raid raid, ServerLevel level, int wave, BlockPos pos) {
        JsonObject spawns = load(level);
        if (spawns == null) {
            return;
        }
        RandomSource random = level.getRandom();
        for (Map.Entry<String, JsonElement> entry : spawns.entrySet()) {
            Identifier id = Identifier.tryParse(entry.getKey());
            if (id == null || !entry.getValue().isJsonObject()) {
                continue;
            }
            Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
            if (type.isEmpty()) {
                continue;
            }
            JsonObject rule = ruleFor(entry.getValue().getAsJsonObject(), wave);
            if (rule == null) {
                continue;
            }
            double chance = rule.has("chance") ? rule.get("chance").getAsDouble() : 1.0;
            int count = 0;
            if (random.nextDouble() < chance) {
                int min = rule.has("min") ? rule.get("min").getAsInt() : 1;
                int max = Math.max(min, rule.has("max") ? rule.get("max").getAsInt() : min);
                count = min + random.nextInt(max - min + 1);
            }
            if (rule.has("bonus_chance")) {
                // A number: one extra roll. A list (1.0.3): one independent roll per entry, each adding one.
                com.google.gson.JsonElement bonus = rule.get("bonus_chance");
                if (bonus.isJsonArray()) {
                    for (com.google.gson.JsonElement roll : bonus.getAsJsonArray()) {
                        if (random.nextDouble() < roll.getAsDouble()) {
                            ++count;
                        }
                    }
                } else if (random.nextDouble() < bonus.getAsDouble()) {
                    ++count;
                }
            }
            for (int i = 0; i < count; ++i) {
                Entity entity = type.get().create(level, EntitySpawnReason.EVENT);
                if (entity instanceof Raider raider) {
                    raid.joinRaid(level, wave, raider, pos, false);
                } else if (entity != null) {
                    entity.discard();
                }
            }
        }
    }

    /** The entry for this wave, or the closest listed wave below it; null if none. */
    private static JsonObject ruleFor(JsonObject waves, int wave) {
        JsonObject best = null;
        int bestWave = Integer.MIN_VALUE;
        for (Map.Entry<String, JsonElement> entry : waves.entrySet()) {
            int listed;
            try {
                listed = Integer.parseInt(entry.getKey().trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (listed <= wave && listed > bestWave && entry.getValue().isJsonObject()) {
                best = entry.getValue().getAsJsonObject();
                bestWave = listed;
            }
        }
        return best;
    }

    private static JsonObject load(ServerLevel level) {
        Optional<Resource> resource = level.getServer().getResourceManager().getResource(FILE);
        if (resource.isEmpty()) {
            return null;
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            return root.has("spawns") && root.get("spawns").isJsonObject() ? root.getAsJsonObject("spawns") : null;
        } catch (Exception e) {
            LOGGER.error("Couldn't read {} (no extra raiders this wave)", FILE, e);
            return null;
        }
    }
}
