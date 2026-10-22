package net.gunmetal.raids.entity;

import net.minecraft.world.item.component.CustomModelData;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A mob that tells custom (EMF) models what it is doing. The renderer writes these into the invisible
 * vanilla "hat" part every frame, where a .jem/.jpm can read them:
 *   hat.rz       = animState()                    (whole number; see each mob's STATE_* constants)
 *   abs(hat.rx)  = secondsSinceShot(partialTick)  (smooth, capped at 100; for recoil)
 *   hat.ry       = extraSignal()                  (mob-specific; Trooper: shield hits left, 0-3)
 * Both are client-side values synced from the server.
 *
 * heldItemTag() names the current state for the held gun's item model: the renderer draws the main-hand
 * item with that string as its custom_model_data (client-side copy only; the real item is untouched), so
 * the pack's item definition can pick a model, and with it a hand position, per state.
 */
public interface AnimSignalled {
    /** Seconds since the last shot are capped at this (also the value before the first shot). */
    float SHOT_TIMER_CAP = 100f;

    int animState();

    /** A third mob-specific value for the model (hat.ry); 0 if the mob has none. */
    default float extraSignal() {
        return 0f;
    }

    /**
     * The Artificer's planned summon move, for the model's "summon_signal" / "upgrade_signal" parts:
     * drones + 4 x golems + 16 x puppeted + 64 if it's an upgrade move (each count 0-3); 0 = nothing planned.
     */
    default int summonPlan() {
        return 0;
    }

    /** Whether the renderer should use the mob's alternate texture (Trooper officers). */
    default boolean altTexture() {
        return false;
    }

    /** Which of the renderer's textures to draw (0 = the first); by default 1 for the alternate texture. */
    default int textureVariant() {
        return altTexture() ? 1 : 0;
    }

    float secondsSinceShot(float partialTick);

    /** The state name given to the held item's model, or null to draw it as it is. */
    default @Nullable CustomModelData heldItemTag() {
        return null;
    }

    /** One custom_model_data per state name (made once, reused every frame). */
    static CustomModelData[] tags(String... names) {
        CustomModelData[] out = new CustomModelData[names.length];
        for (int i = 0; i < names.length; i++) {
            out[i] = new CustomModelData(List.of(), List.of(), List.of(names[i]), List.of());
        }
        return out;
    }

    /** The tag for a state, or null if the state is out of range. */
    static @Nullable CustomModelData tagFor(CustomModelData[] tags, int state) {
        return state >= 0 && state < tags.length ? tags[state] : null;
    }
}
