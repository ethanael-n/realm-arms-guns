package net.gunmetal.arms.ammo;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.gunmetal.arms.advancement.RealmAdvancements;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Cartridge types and what they do on hit.
 *
 * Effects apply per pellet. Spread guns get 75% of the duration (SPREAD_GUN_DURATION). Status effects don't stack their
 * duration, so several pellets on one target = one application. Ice is the exception: it adds
 * frozen ticks once per target per shot, and those DO stack across shots.
 *
 * Effects from other mods are looked up by ID when hit, so if that mod isn't installed the
 * cartridge simply does nothing extra.
 */
public enum Cartridge {
    // item id, effect id, single-shot duration (ticks), damage multiplier.
    /** The standard cartridge, gunmetal:cartridge. */
    STANDARD("cartridge", null, 0, 1.0f),
    /** Sets targets on fire (6 s). Half damage. */
    BLAZE("blaze_cartridge", null, 6 * 20, 0.5f),
    ICE("ice_cartridge", null, 0, 1.0f),
    /** Irradiated I; a target that already has Irradiated I is upgraded to Irradiated II (see NUCLEAR_*). */
    NUCLEAR("nuclear_cartridge", "alexscaves:irradiated", 17 * 20, 1.0f),
    VOID("void_cartridge", "enderscape:void_corruption", 8 * 20, 1.0f),
    SUNKEN("sunken_cartridge", "bigger_fish:leeched", 10 * 20, 1.0f),
    POISON("poison_cartridge", "minecraft:poison", 4 * 20, 1.0f),
    /** 130% damage on every shot, but each trigger pull has a chance to misfire (see UNSTABLE_*). */
    UNSTABLE("unstable_cartridge", null, 0, 1.3f);
    // New cartridges go at the END: guns store the loaded cartridge by position in this list.

    /** Unstable: chance per trigger pull (a whole volleygun burst counts as one) to misfire. */
    public static final float UNSTABLE_MISFIRE_CHANCE = 0.5f;
    /** A misfire adds this much spread (degrees), fires at this speed (blocks/second)... */
    public static final float UNSTABLE_MISFIRE_EXTRA_SPREAD = 12.0f;
    public static final float UNSTABLE_MISFIRE_VELOCITY = 30.0f;
    /** A misfired shot deals this share of the gun's normal damage (instead of the usual 130%). */
    public static final float UNSTABLE_MISFIRE_DAMAGE = 0.4f;
    /** ...and costs this much durability in total (the normal 1 per shot included). */
    public static final int UNSTABLE_MISFIRE_DURABILITY = 10;
    /**
     * Nuclear on a target that already has Irradiated I: replaced by Irradiated II (amplifier 1) for
     * this long (x SPREAD_GUN_DURATION for spread guns, like every cartridge effect).
     */
    public static final int NUCLEAR_UPGRADE_AMPLIFIER = 1;
    public static final int NUCLEAR_UPGRADE_TICKS = 5 * 20;

    /** Spread guns (musketoon, blunderbuss, volleygun) apply effects for this share of the normal duration. */
    public static final float SPREAD_GUN_DURATION = 0.75f;

    /** Frozen ticks added per shot (140 = fully frozen, where freeze damage starts). */
    public static final int ICE_FROZEN_TICKS_PER_SHOT = 120;
    /** Stacking limit, so fast guns can push past 140 but not freeze something for a minute. */
    public static final int ICE_MAX_FROZEN_TICKS = 300;
    /**
     * With Frostiful installed, vanilla frozen ticks are replaced by Thermoo temperature, where
     * "fully frozen" is the target's minimum temperature (-6300 for most mobs, not 140). Each Ice shot
     * then cools the target by this fraction of the way to fully frozen (120/140 = the same share of
     * the bar that 120 ticks is in vanilla). Frost resistance reduces it; Frostiful decides how fast
     * the target warms back up. Stacking stops at fully frozen.
     */
    public static final float ICE_FROSTIFUL_FRACTION_PER_SHOT = 120f / 140f;
    private static final boolean FROSTIFUL = FabricLoader.getInstance().isModLoaded("frostiful");
    /** Effect level for every status effect a cartridge gives (0 = level I). */
    public static final int EFFECT_AMPLIFIER = 0;

    public final @Nullable String itemId;
    private final @Nullable String effectId;
    private final int durationTicks;
    /** Bullet damage multiplier (applied to the gun's damage; also to dispensers). */
    public final float damageMultiplier;
    public @Nullable Item item;

    Cartridge(@Nullable String itemId, @Nullable String effectId, int durationTicks, float damageMultiplier) {
        this.itemId = itemId;
        this.effectId = effectId;
        this.durationTicks = durationTicks;
        this.damageMultiplier = damageMultiplier;
    }

    private static final Map<Entity, Integer> LAST_ICE_SHOT = new WeakHashMap<>();
    private static final Map<Entity, Integer> LAST_NUCLEAR_SHOT = new WeakHashMap<>();

    /** Any cartridge a gun can load: the standard one or a special one. */
    public static boolean isCartridge(ItemStack stack) {
        for (Cartridge cartridge : values()) {
            if (cartridge.item != null && stack.is(cartridge.item)) {
                return true;
            }
        }
        return false;
    }

    public static Cartridge fromAmmo(ItemStack ammo) {
        for (Cartridge cartridge : values()) {
            if (cartridge.item != null && ammo.is(cartridge.item)) {
                return cartridge;
            }
        }
        return STANDARD;
    }

    public static Cartridge loadedIn(ItemStack gun) {
        Byte loaded = gun.get(RealmArmsAmmo.LOADED_CARTRIDGE);
        if (loaded == null || loaded < 0 || loaded >= values().length) {
            return STANDARD;
        }
        return values()[loaded];
    }

    public static Cartridge byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : STANDARD;
    }

    /** Server side, called for every pellet that hits. */
    public void applyOnHit(Entity target, @Nullable Entity shooter, boolean spreadGun, int shotId) {
        int duration = spreadGun ? Math.round(durationTicks * SPREAD_GUN_DURATION) : durationTicks;
        switch (this) {
            case STANDARD, UNSTABLE -> {
            }
            case BLAZE -> {
                if (target.getType() != EntityType.ENDERMAN) {
                    target.igniteForSeconds(duration / 20f);
                    RealmAdvancements.onBlazeIgnite(shooter, target, shotId);
                }
            }
            case ICE -> {
                if (target.canFreeze()) {
                    Integer last = LAST_ICE_SHOT.get(target);
                    if (last == null || last != shotId) {
                        LAST_ICE_SHOT.put(target, shotId);
                        if (FROSTIFUL && target instanceof LivingEntity living) {
                            applyFrostifulCold(living);
                        } else {
                            int frozen = Math.min(ICE_MAX_FROZEN_TICKS, target.getTicksFrozen() + ICE_FROZEN_TICKS_PER_SHOT);
                            target.setTicksFrozen(Math.max(target.getTicksFrozen(), frozen));
                        }
                    }
                }
            }
            case NUCLEAR -> {
                // Once per target per shot (a whole pellet spread or burst counts once), so a
                // single shotgun blast doesn't apply level I and upgrade it in the same instant.
                Integer last = LAST_NUCLEAR_SHOT.get(target);
                if (last != null && last == shotId) {
                    break;
                }
                LAST_NUCLEAR_SHOT.put(target, shotId);
                applyNuclear(target, shooter, duration, spreadGun ? Math.round(NUCLEAR_UPGRADE_TICKS * SPREAD_GUN_DURATION) : NUCLEAR_UPGRADE_TICKS);
            }
            default -> addEffect(target, shooter, duration);
        }
    }

    private void applyNuclear(Entity target, @Nullable Entity shooter, int duration, int upgradeDuration) {
        if (!(target instanceof LivingEntity living) || effectId == null) {
            return;
        }
        effect(effectId).ifPresent(effect -> {
            MobEffectInstance current = living.getEffect(effect);
            if (current != null && current.getAmplifier() == EFFECT_AMPLIFIER) {
                living.removeEffect(effect);
                living.addEffect(new MobEffectInstance(effect, upgradeDuration, NUCLEAR_UPGRADE_AMPLIFIER), shooter);
            } else {
                living.addEffect(new MobEffectInstance(effect, duration, EFFECT_AMPLIFIER), shooter);
            }
        });
    }

    private void addEffect(Entity target, @Nullable Entity shooter, int duration) {
        if (target instanceof LivingEntity living && effectId != null) {
            effect(effectId).ifPresent(effect ->
                    living.addEffect(new MobEffectInstance(effect, duration, EFFECT_AMPLIFIER), shooter));
        }
    }

    /**
     * Frostiful turns any increase in frozen ticks into an equal drop in Thermoo temperature (after
     * frost resistance). So we ask for an increase sized to the target's own temperature range.
     * Uses only the attribute id, so there's no compile-time dependency on Thermoo.
     */
    private static void applyFrostifulCold(LivingEntity living) {
        Optional<Holder.Reference<Attribute>> minTemperature =
                BuiltInRegistries.ATTRIBUTE.get(Identifier.fromNamespaceAndPath("thermoo", "min_temperature"));
        if (minTemperature.isEmpty() || !living.getAttributes().hasAttribute(minTemperature.get())) {
            return;
        }
        // Thermoo: 1 point of min_temperature = 140 temperature units of cold capacity
        int fullyFrozen = (int) (living.getAttributeValue(minTemperature.get()) * 140);
        int change = Math.round(fullyFrozen * ICE_FROSTIFUL_FRACTION_PER_SHOT);
        if (change > 0) {
            living.setTicksFrozen(living.getTicksFrozen() + change);
        }
    }

    private static Optional<Holder<MobEffect>> effect(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) {
            return Optional.empty();
        }
        return BuiltInRegistries.MOB_EFFECT.get(identifier).map(reference -> reference);
    }
}
