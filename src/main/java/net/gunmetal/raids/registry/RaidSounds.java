package net.gunmetal.raids.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import static net.gunmetal.raids.RealmRaids.MOD_ID;

/**
 * Sound events. The actual sounds are set in assets/gunmetal/sounds.json; until custom .ogg
 * files are added there, each one plays a placeholder (see that file).
 */
public final class RaidSounds {
    /** Bombardier cannon shot. */
    public static final SoundEvent BOMBARDIER_FIRE = event("entity.bombardier.fire");
    /** Sharpshooter rifle shot. */
    public static final SoundEvent SHARPSHOOTER_FIRE = event("entity.sharpshooter.fire");
    /** Trooper fixing its bayonet (the 1 second pause before the charge). */
    public static final SoundEvent TROOPER_FIX_BAYONET = event("entity.trooper.fix_bayonet");
    /** Trooper switching to the bayonet (end of that pause). */
    public static final SoundEvent TROOPER_EQUIP_BAYONET = event("entity.trooper.equip_bayonet");
    /** Trooper's shield breaking (the event kept its helmet-era name). */
    public static final SoundEvent TROOPER_SHELL_BREAK = event("entity.trooper.shell_break");
    /** Trooper hurt while its shield is up (instead of the hurt sound). */
    public static final SoundEvent TROOPER_SHIELD_HIT = event("entity.trooper.shield_hit");
    /** Trooper rally horn: a hurt Trooper's smoke round calls nearby Troopers to charge (heard ~128 blocks). */
    public static final SoundEvent TROOPER_HORN = event("entity.trooper.horn");
    /** Trooper smoke round bursting into a cloud. */
    public static final SoundEvent TROOPER_SMOKE = event("entity.trooper.smoke");

    public static final SoundEvent BOMBARDIER_AMBIENT = event("entity.bombardier.ambient");
    public static final SoundEvent BOMBARDIER_HURT = event("entity.bombardier.hurt");
    public static final SoundEvent BOMBARDIER_DEATH = event("entity.bombardier.death");
    public static final SoundEvent SHARPSHOOTER_AMBIENT = event("entity.sharpshooter.ambient");
    public static final SoundEvent SHARPSHOOTER_HURT = event("entity.sharpshooter.hurt");
    public static final SoundEvent SHARPSHOOTER_DEATH = event("entity.sharpshooter.death");
    public static final SoundEvent TROOPER_AMBIENT = event("entity.trooper.ambient");
    public static final SoundEvent TROOPER_HURT = event("entity.trooper.hurt");
    public static final SoundEvent TROOPER_DEATH = event("entity.trooper.death");

    /** Each Trooper a rally reaches yells this, 0-2 s after the horn. */
    public static final SoundEvent TROOPER_RALLY_YELL = event("entity.trooper.rally_yell");
    /** Throwing a Brass Deployable. */
    public static final SoundEvent BRASS_DEPLOYABLE_THROW = event("item.brass_deployable.throw");
    public static final SoundEvent ARTIFICER_AMBIENT = event("entity.artificer.ambient");
    public static final SoundEvent ARTIFICER_HURT = event("entity.artificer.hurt");
    public static final SoundEvent ARTIFICER_DEATH = event("entity.artificer.death");
    /** Artificer starting a summon (arms raised). */
    public static final SoundEvent ARTIFICER_SUMMON = event("entity.artificer.summon");
    /** 2 s before each pulse. */
    public static final SoundEvent ARTIFICER_PULSE_WARNING = event("entity.artificer.pulse_warning");
    /** The pulse itself. */
    public static final SoundEvent ARTIFICER_PULSE = event("entity.artificer.pulse");
    /** Played on each mob the pulse stuns. */
    public static final SoundEvent ARTIFICER_ELECTRIFIED = event("entity.artificer.electrified");
    /** A projectile destroyed by the pulse. */
    public static final SoundEvent ARTIFICER_ZAP = event("entity.artificer.zap");
    public static final SoundEvent DRONE_AMBIENT = event("entity.drone.ambient");
    public static final SoundEvent DRONE_HURT = event("entity.drone.hurt");
    public static final SoundEvent DRONE_DEATH = event("entity.drone.death");
    /** A drone upgrading (anvil). */
    public static final SoundEvent DRONE_UPGRADE = event("entity.drone.upgrade");
    /** The brass golem's constant ticking (faster and higher while its fuse burns). */
    public static final SoundEvent BRASS_GOLEM_TICK = event("entity.brass_golem.tick");
    /** The brass golem's fuse starting (creeper hiss). */
    public static final SoundEvent BRASS_GOLEM_FUSE = event("entity.brass_golem.fuse");
    public static final SoundEvent BRASS_GOLEM_HURT = event("entity.brass_golem.hurt");
    public static final SoundEvent BRASS_GOLEM_DEATH = event("entity.brass_golem.death");
    public static final SoundEvent BRASS_GOLEM_STEP = event("entity.brass_golem.step");
    /** Puppeted: ambient per state (incomplete / completed); hurt, death and steps shared. */
    public static final SoundEvent PUPPETED_AMBIENT_INCOMPLETE = event("entity.puppeted.ambient_incomplete");
    public static final SoundEvent PUPPETED_AMBIENT_COMPLETE = event("entity.puppeted.ambient_complete");
    public static final SoundEvent PUPPETED_HURT = event("entity.puppeted.hurt");
    public static final SoundEvent PUPPETED_DEATH = event("entity.puppeted.death");
    public static final SoundEvent PUPPETED_STEP = event("entity.puppeted.step");

    /** Siege Balloon: idle creaks, hurt, death (the blast is the explosion's own sound). */
    public static final SoundEvent SIEGE_BALLOON_AMBIENT = event("entity.siege_balloon.ambient");
    public static final SoundEvent SIEGE_BALLOON_HURT = event("entity.siege_balloon.hurt");
    public static final SoundEvent SIEGE_BALLOON_DEATH = event("entity.siege_balloon.death");
    /** Changing rise state, dropping a paratrooper, each weapon's ready cue (1.5 s before), the crossbow shot, damage stages, burning down. */
    public static final SoundEvent SIEGE_BALLOON_RISE_CHANGE = event("entity.siege_balloon.rise_change");
    public static final SoundEvent SIEGE_BALLOON_DEPLOY = event("entity.siege_balloon.deploy");
    public static final SoundEvent SIEGE_BALLOON_CANNON_READY = event("entity.siege_balloon.cannon_ready");
    public static final SoundEvent SIEGE_BALLOON_CROSSBOW_READY = event("entity.siege_balloon.crossbow_ready");
    public static final SoundEvent SIEGE_BALLOON_CROSSBOW_FIRE = event("entity.siege_balloon.crossbow_fire");
    public static final SoundEvent SIEGE_BALLOON_STAGE_1 = event("entity.siege_balloon.damage_stage_1");
    public static final SoundEvent SIEGE_BALLOON_STAGE_2 = event("entity.siege_balloon.damage_stage_2");
    public static final SoundEvent SIEGE_BALLOON_BLEED = event("entity.siege_balloon.bleed");
    public static final SoundEvent SIEGE_BALLOON_DROP_WARNING = event("entity.siege_balloon.drop_warning");
    public static final SoundEvent SIEGE_BALLOON_MAYDAY = event("entity.siege_balloon.mayday");
    /** The tether snapping or the ravager dying (1.0.2; default: a lead breaking). */
    public static final SoundEvent SIEGE_BALLOON_UNTETHER = event("entity.siege_balloon.untether");

    /** The hit that sets off a Sharpshooter's smoke bomb (1.0.3; default: the illusioner's mirror move). */
    public static final SoundEvent SHARPSHOOTER_SMOKE_BOMB = event("entity.sharpshooter.smoke_bomb");

    /** Aviator idle / hurt / death (1.0.2; defaults: the pillager's). Celebrating stays the pillager's. */
    public static final SoundEvent AVIATOR_AMBIENT = event("entity.aviator.ambient");
    public static final SoundEvent AVIATOR_HURT = event("entity.aviator.hurt");
    public static final SoundEvent AVIATOR_DEATH = event("entity.aviator.death");

    private RaidSounds() {
    }

    private static SoundEvent event(String path) {
        return SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(MOD_ID, path));
    }

    public static void register() {
        for (SoundEvent sound : java.util.List.of(BOMBARDIER_FIRE, SHARPSHOOTER_FIRE, TROOPER_FIX_BAYONET,
                TROOPER_EQUIP_BAYONET, TROOPER_SHELL_BREAK, TROOPER_SHIELD_HIT, TROOPER_HORN, TROOPER_SMOKE,
                BOMBARDIER_AMBIENT, BOMBARDIER_HURT, BOMBARDIER_DEATH,
                SHARPSHOOTER_AMBIENT, SHARPSHOOTER_HURT, SHARPSHOOTER_DEATH,
                TROOPER_AMBIENT, TROOPER_HURT, TROOPER_DEATH,
                ARTIFICER_AMBIENT, ARTIFICER_HURT, ARTIFICER_DEATH, ARTIFICER_SUMMON, ARTIFICER_PULSE_WARNING,
                ARTIFICER_PULSE, ARTIFICER_ELECTRIFIED, ARTIFICER_ZAP,
                DRONE_AMBIENT, DRONE_HURT, DRONE_DEATH, DRONE_UPGRADE,
                TROOPER_RALLY_YELL, BRASS_DEPLOYABLE_THROW,
                BRASS_GOLEM_TICK, BRASS_GOLEM_FUSE, BRASS_GOLEM_HURT, BRASS_GOLEM_DEATH, BRASS_GOLEM_STEP,
                PUPPETED_AMBIENT_INCOMPLETE, PUPPETED_AMBIENT_COMPLETE, PUPPETED_HURT, PUPPETED_DEATH, PUPPETED_STEP,
                SIEGE_BALLOON_AMBIENT, SIEGE_BALLOON_HURT, SIEGE_BALLOON_DEATH, SIEGE_BALLOON_RISE_CHANGE,
                SIEGE_BALLOON_DEPLOY, SIEGE_BALLOON_CANNON_READY, SIEGE_BALLOON_CROSSBOW_READY, SIEGE_BALLOON_CROSSBOW_FIRE,
                SIEGE_BALLOON_STAGE_1, SIEGE_BALLOON_STAGE_2, SIEGE_BALLOON_BLEED, SIEGE_BALLOON_DROP_WARNING,
                SIEGE_BALLOON_MAYDAY, SIEGE_BALLOON_UNTETHER, AVIATOR_AMBIENT, AVIATOR_HURT, AVIATOR_DEATH,
                SHARPSHOOTER_SMOKE_BOMB)) {
            Registry.register(BuiltInRegistries.SOUND_EVENT, sound.location(), sound);
        }
    }
}
