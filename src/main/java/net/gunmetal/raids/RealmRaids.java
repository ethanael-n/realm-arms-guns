package net.gunmetal.raids;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.gunmetal.raids.registry.RaidEntities;
import net.gunmetal.raids.registry.RaidItems;
import net.gunmetal.raids.registry.RaidParticles;
import net.gunmetal.raids.registry.RaidSounds;

/**
 * Realm Raids: extra raid illagers built on Realm Arms guns.
 *   Bombardier   - backline artillery, lobs hand cannon shells over walls (waves 4+)
 *   Sharpshooter - backline rifle sniper with a scope glint before each shot (waves 2+)
 *   Trooper      - steel arquebus for a few shots, then a bayonet charge (waves 3+)
 *   Artificer    - summons drones and brass golems, electric pulse (waves 3+)
 *   Siege Balloon - tethered to its own ravager, a pilot fires a hand cannon from its basket (waves 4+)
 * Brass Deployable: a thrown brass golem for players (lure and bomb).
 * How many join each wave: data/gunmetal/raid_spawns.json (overridable from a datapack).
 */
public class RealmRaids implements ModInitializer {
    public static final String MOD_ID = "gunmetal";

    @Override
    public void onInitialize() {
        RaidSounds.register();
        RaidParticles.register();
        RaidEntities.register();
        RaidItems.register();
        net.gunmetal.raids.entity.Bombardier.registerEvents();

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(tab -> {
            for (Item egg : RaidEntities.SPAWN_EGGS) {
                tab.accept(egg);
            }
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(tab -> tab.accept(RaidItems.BRASS_DEPLOYABLE));
    }
}
