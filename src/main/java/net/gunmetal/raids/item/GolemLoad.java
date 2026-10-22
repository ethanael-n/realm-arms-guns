package net.gunmetal.raids.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * What a Brass Deployable was crafted with (the six side slots of its recipe), carried by the item
 * (in its custom_data, under "RealmRaidsLoad"), the thrown deployable and the golem it becomes.
 *   gunpowder:      blast size: the n-th powder adds (15 + 2n)%
 *   blaze powder:   counts as half a powder for blast size; victims burn 3 s per blaze powder
 *   sulfur dust:    (Alex's Caves) counts as half a powder for blast size; victims get Irradiated 6 s per sulfur
 *   copper patina:  +10 fuse threshold each (the golem lasts longer)
 *   redstone:       the fuse count starts 10 higher each (6 = explodes on appearing)
 *   glowstone dust: +1.5 aggro pulse radius each (+3 across)
 */
public record GolemLoad(int gunpowder, int blaze, int patina, int redstone, int glowstone, int sulfur) {
    public static final GolemLoad EMPTY = new GolemLoad(0, 0, 0, 0, 0, 0);
    private static final String KEY = "RealmRaidsLoad";

    // ---- Tuning -------------------------------------------------------------------------
    /** Each blaze powder / sulfur counts as this many powders for blast size. */
    public static final float HALF_POWDER = 0.5f;
    public static final int BURN_SECONDS_PER_BLAZE = 3, IRRADIATED_SECONDS_PER_SULFUR = 6;
    public static final float THRESHOLD_PER_PATINA = 10f, START_FUSE_PER_REDSTONE = 10f;
    public static final float PULSE_RADIUS_PER_GLOWSTONE = 1.5f;
    // -------------------------------------------------------------------------------------

    /**
     * Which texture the golem shows. Empty shows the gunpowder one. New entries go at the end: the
     * position (ordinal) is synced and passed to .jem files as tank_signal.rz.
     */
    public enum Tank { GUNPOWDER, BLAZE, PATINA, REDSTONE, MIXED, GLOWSTONE, SULFUR, HOSTILE }

    public boolean isEmpty() {
        return gunpowder + blaze + patina + redstone + glowstone + sulfur == 0;
    }

    /** Powders for blast size: gunpowder counts 1, blaze powder and sulfur half each. */
    public float powders() {
        return gunpowder + HALF_POWDER * (blaze + sulfur);
    }

    /** Blast size multiplier: 1 + sum over the powders of (15 + 2n)% (the closed form also covers half powders). */
    public float blastMultiplier() {
        float n = powders();
        return 1f + (15f * n + n * (n + 1f)) / 100f;
    }

    /** Seconds victims burn (0 = no fire). */
    public int burnSeconds() {
        return BURN_SECONDS_PER_BLAZE * blaze;
    }

    /** Seconds of Irradiated on victims (0 = none). */
    public int irradiatedSeconds() {
        return IRRADIATED_SECONDS_PER_SULFUR * sulfur;
    }

    /** Extra fuse threshold over the base. */
    public float extraThreshold() {
        return THRESHOLD_PER_PATINA * patina;
    }

    /** Fuse count the golem starts at. */
    public float startFuse() {
        return START_FUSE_PER_REDSTONE * redstone;
    }

    /** Extra aggro pulse radius. */
    public float extraPulseRadius() {
        return PULSE_RADIUS_PER_GLOWSTONE * glowstone;
    }

    public Tank tank() {
        int kinds = (gunpowder > 0 ? 1 : 0) + (blaze > 0 ? 1 : 0) + (patina > 0 ? 1 : 0) + (redstone > 0 ? 1 : 0)
                + (glowstone > 0 ? 1 : 0) + (sulfur > 0 ? 1 : 0);
        if (kinds > 1) {
            return Tank.MIXED;
        }
        if (blaze > 0) {
            return Tank.BLAZE;
        }
        if (patina > 0) {
            return Tank.PATINA;
        }
        if (redstone > 0) {
            return Tank.REDSTONE;
        }
        if (glowstone > 0) {
            return Tank.GLOWSTONE;
        }
        return sulfur > 0 ? Tank.SULFUR : Tank.GUNPOWDER;
    }

    // ---- Item stacks --------------------------------------------------------------------------

    public static GolemLoad of(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return EMPTY;
        }
        CompoundTag tag = data.copyTag().getCompoundOrEmpty(KEY);
        return new GolemLoad(tag.getIntOr("Gunpowder", 0), tag.getIntOr("Blaze", 0), tag.getIntOr("Patina", 0),
                tag.getIntOr("Redstone", 0), tag.getIntOr("Glowstone", 0), tag.getIntOr("Sulfur", 0));
    }

    /** Stores this load on the stack (nothing for an empty load, so plain deployables stack together). */
    public void applyTo(ItemStack stack) {
        if (isEmpty()) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("Gunpowder", gunpowder);
        tag.putInt("Blaze", blaze);
        tag.putInt("Patina", patina);
        tag.putInt("Redstone", redstone);
        tag.putInt("Glowstone", glowstone);
        tag.putInt("Sulfur", sulfur);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.put(KEY, tag));
    }

    // ---- Entities -----------------------------------------------------------------------------

    public void save(ValueOutput output) {
        output.putInt("LoadGunpowder", gunpowder);
        output.putInt("LoadBlaze", blaze);
        output.putInt("LoadPatina", patina);
        output.putInt("LoadRedstone", redstone);
        output.putInt("LoadGlowstone", glowstone);
        output.putInt("LoadSulfur", sulfur);
    }

    public static GolemLoad read(ValueInput input) {
        return new GolemLoad(input.getIntOr("LoadGunpowder", 0), input.getIntOr("LoadBlaze", 0),
                input.getIntOr("LoadPatina", 0), input.getIntOr("LoadRedstone", 0),
                input.getIntOr("LoadGlowstone", 0), input.getIntOr("LoadSulfur", 0));
    }
}
