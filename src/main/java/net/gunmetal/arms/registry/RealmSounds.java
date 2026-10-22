package net.gunmetal.arms.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.gunmetal.arms.guns.GunType;

import java.util.EnumMap;
import java.util.Map;

import static net.gunmetal.arms.RealmArmsWeapons.MOD_ID;

/**
 * Per gun type (e.g. gun.hand_cannon.fire):
 *   gunmetal:gun.<type>.fire        every shot
 *   gunmetal:gun.<type>.load_first  end of the first loading stage
 *   gunmetal:gun.<type>.ready       the gun becomes loaded
 *   gunmetal:gun.load1 / gun.load2 / gun.load0   shared by all guns (see guns/GunLoading)
 * The .ogg files and their variations are set in assets/gunmetal/sounds.json.
 */
public final class RealmSounds {
    private static final Map<GunType, SoundEvent> FIRE = new EnumMap<>(GunType.class);
    private static final Map<GunType, SoundEvent> LOAD_FIRST = new EnumMap<>(GunType.class);
    private static final Map<GunType, SoundEvent> READY = new EnumMap<>(GunType.class);
    /** Shared by every Realm Arms gun. */
    public static final SoundEvent LOAD_MIDDLE = event("gun.load1");
    public static final SoundEvent LOAD_LAST = event("gun.load2");
    public static final SoundEvent LOAD_RESUME = event("gun.load0");
    public static final SoundEvent DISPENSER_FIRE = event("dispenser_fire");
    public static final SoundEvent BULLET_FLY_BY = event("bullet.fly_by");
    public static final SoundEvent BULLET_WATER_HIT = event("bullet.water_hit");
    /**
     * Clip guns (revolver, repeater), each with its own set:
     *   gun.<id>.fire / load_first (end of stage 1) / round (a cartridge goes into the clip, every
     *   2nd stage) / ready (clip full). The other stages use the shared gun.load1.
     */
    public static final SoundEvent REVOLVER_FIRE = event("gun.revolver.fire");
    public static final SoundEvent REVOLVER_LOAD_FIRST = event("gun.revolver.load_first");
    public static final SoundEvent REVOLVER_ROUND = event("gun.revolver.round");
    public static final SoundEvent REVOLVER_READY = event("gun.revolver.ready");
    public static final SoundEvent REPEATER_FIRE = event("gun.repeater.fire");
    public static final SoundEvent REPEATER_LOAD_FIRST = event("gun.repeater.load_first");
    public static final SoundEvent REPEATER_ROUND = event("gun.repeater.round");
    public static final SoundEvent REPEATER_READY = event("gun.repeater.ready");

    static {
        for (GunType type : GunType.values()) {
            FIRE.put(type, event("gun." + type.id + ".fire"));
            LOAD_FIRST.put(type, event("gun." + type.id + ".load_first"));
            READY.put(type, event("gun." + type.id + ".ready"));
        }
    }

    private static SoundEvent event(String path) {
        return SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(MOD_ID, path));
    }

    private RealmSounds() {
    }

    public static SoundEvent fire(GunType type) {
        return FIRE.get(type);
    }

    public static SoundEvent loadFirst(GunType type) {
        return LOAD_FIRST.get(type);
    }

    public static SoundEvent ready(GunType type) {
        return READY.get(type);
    }

    public static void register() {
        for (SoundEvent sound : java.util.List.of(LOAD_MIDDLE, LOAD_LAST, LOAD_RESUME, DISPENSER_FIRE, BULLET_FLY_BY, BULLET_WATER_HIT,
                REVOLVER_FIRE, REVOLVER_LOAD_FIRST, REVOLVER_ROUND, REVOLVER_READY,
                REPEATER_FIRE, REPEATER_LOAD_FIRST, REPEATER_ROUND, REPEATER_READY)) {
            Registry.register(BuiltInRegistries.SOUND_EVENT, sound.location(), sound);
        }
        for (Map<GunType, SoundEvent> sounds : java.util.List.of(FIRE, LOAD_FIRST, READY)) {
            for (SoundEvent sound : sounds.values()) {
                Registry.register(BuiltInRegistries.SOUND_EVENT, sound.location(), sound);
            }
        }
    }
}
