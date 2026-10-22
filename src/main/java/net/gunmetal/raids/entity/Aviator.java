package net.gunmetal.raids.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.gunmetal.arms.ai.PillagerGunGoal;
import net.gunmetal.arms.guns.CustomGunner;
import net.gunmetal.arms.guns.ClipGunItem;
import net.gunmetal.arms.guns.GunItem;
import org.jetbrains.annotations.Nullable;

/**
 * Aviator (was the Raid Pilot): a pillager-shaped airman, 22 health, speed 0.35, fully fireproof.
 *
 * Balloon pilot: rides a Siege Balloon at its bottom-front and crews its weapons (nothing in hand
 * there; half damage while aboard). Its hitbox is slid out at diagonal headings (hitboxes never
 * turn) while its model is drawn at the true spot (drawOffset). Thrown out of the balloon (or
 * spawned from its egg) it always draws a revolver.
 *
 * Paratroopers (the only mob a balloon drops) and Aviators joining raid waves on their own carry one
 * of WEAPONS at random: guns fight like Realm Arms gun pillagers, the axe and sword in melee.
 *
 * Textures (textures/entity/illager/): aviator_pilot.png for balloon pilots and spawn-egg Aviators;
 * every other Aviator picks aviator_airman_a / _b / _c at random. Model layer gunmetal:aviator.
 *
 * Model state (hat.rz): 0 aboard, no target; 1 aboard, aiming at the balloon's target; 2 on foot;
 * 3 on foot with a target (gun); 4 on foot with a target (melee weapon); 5 fanning the hammer (revolver
 * or repeater, target within 4 blocks); 6 reloading a gun (on foot). hat.ry = weapon group (see extraSignal).
 * Melee swings: the vanilla attack swing (swing_progress).
 * abs(hat.rx) = seconds since the balloon's last shot (aboard) or its own.
 */
public class Aviator extends RaidIllager implements CustomGunner {
    // ---- Tuning -------------------------------------------------------------------------
    public static final double MAX_HEALTH = 22.0, MOVEMENT_SPEED = 0.35, FOLLOW_RANGE = 32.0;
    /** Base melee damage before the weapon's own bonus (1 = a player's: iron sword 6, iron axe 9; x1.5 on Hard). */
    public static final double ATTACK_DAMAGE = 1.0;
    /**
     * Paratroopers and raid Aviators (not pilots): one of these, picked evenly (1.0.4). The hatchet is
     * Illager Invasion's platinum infused hatchet, or a copper axe without that mod; it's only swung
     * (never thrown).
     */
    private static final String[] WEAPONS = {"gunmetal:revolver", "gunmetal:repeater", "hatchet"};
    private static final String HATCHET = "illagerinvasion:platinum_infused_hatchet", HATCHET_FALLBACK = "minecraft:copper_axe";
    /**
     * Hatchet hits: this much damage before difficulty (x1.5 on Hard), whatever the hatchet's own
     * damage (the Aviator's base attack damage is set to make up the rest; the item stays untouched).
     */
    public static final double HATCHET_DAMAGE = 10.0;
    /**
     * Each weapon (revolver, repeater, hatchet, the pilot's revolver) drops with this chance on a player
     * kill, randomly damaged; Looting adds 1% a level (vanilla's rule for mob gear). 1.0.4.
     */
    public static final float WEAPON_DROP_CHANCE = 0.08f;
    /** The pilot's revolver: a normal revolver renamed "Pilot Revolver" with more durability (1.0.4). */
    public static final int PILOT_REVOLVER_DURABILITY = 1796;
    /** Off the balloon, a pilot draws this. */
    private static final String PILOT_GUN = "revolver";
    /** Revolver: ticks of aim before each aimed shot (min to max). */
    public static final int REVOLVER_AIM_MIN = 25, REVOLVER_AIM_MAX = 30;
    /**
     * Revolver: with its target this close it fans the hammer after its first aimed shot of the clip (the
     * rest at the revolver's fan rate, every 4 ticks, in its 8 degree cone). The repeater can't fan.
     */
    public static final double FAN_RANGE = 4.0;
    /** Share of damage it takes while aboard a balloon (anything but /kill and the void). */
    public static final float SEATED_DAMAGE_TAKEN = 0.5f;
    // -------------------------------------------------------------------------------------

    public static final int STATE_ABOARD = 0, STATE_ABOARD_AIMING = 1, STATE_ON_FOOT = 2, STATE_ON_FOOT_TARGET = 3,
            STATE_ON_FOOT_MELEE = 4, STATE_FANNING = 5, STATE_RELOADING = 6;
    /** Weapon group for the model (hat.ry): its idle arm pose. */
    public static final int GROUP_NONE = 0, GROUP_MELEE = 1, GROUP_SIDEARM = 2, GROUP_LONG_GUN = 3;
    /** Texture variants: 0 pilot, 1-3 airman a-c. */
    public static final int VARIANT_PILOT = 0, AIRMAN_VARIANTS = 3;

    private static final EntityDataAccessor<Byte> VARIANT = SynchedEntityData.defineId(Aviator.class, EntityDataSerializers.BYTE);

    /** Server: fanning the hammer with its revolver / repeater (set between shots, see mobShotDelay). */
    private boolean fanning;

    public Aviator(EntityType<? extends AbstractIllager> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE)
                .add(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VARIANT, (byte) VARIANT_PILOT);
    }

    @Override
    protected void addAttackGoals() {
        goalSelector.addGoal(2, new PillagerGunGoal<>(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false) {
            @Override
            public boolean canUse() {
                return holdsMeleeWeapon() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return holdsMeleeWeapon() && super.canContinueToUse();
            }
        });
    }

    /** Something in hand that isn't a gun (axe, sword); never while aboard. */
    public boolean holdsMeleeWeapon() {
        return !isPassenger() && !getMainHandItem().isEmpty() && !(getMainHandItem().getItem() instanceof GunItem);
    }

    /** Equipment is picked in finalizeSpawn (it depends on how the Aviator was spawned). */
    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
    }

    /**
     * Spawn egg: a pilot (pilot texture, empty-handed, so it draws its revolver). Anything else
     * (raids, balloon drops, /summon): an airman with a random weapon. The balloon then makes its own
     * pilot with {@link #makeBalloonPilot()}.
     */
    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                  EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
        if (reason == EntitySpawnReason.SPAWN_ITEM_USE) {
            entityData.set(VARIANT, (byte) VARIANT_PILOT);
        } else {
            RandomSource random = level.getRandom();
            entityData.set(VARIANT, (byte) (1 + random.nextInt(AIRMAN_VARIANTS)));
            equip(weapon(WEAPONS[random.nextInt(WEAPONS.length)]));
            fitMeleeDamage();
        }
        return data;
    }

    /** Crew for a Siege Balloon: pilot texture, nothing in hand. */
    public void makeBalloonPilot() {
        entityData.set(VARIANT, (byte) VARIANT_PILOT);
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    private void equip(ItemStack stack) {
        if (!stack.isEmpty()) {
            setItemSlot(EquipmentSlot.MAINHAND, stack);
            setDropChance(EquipmentSlot.MAINHAND, WEAPON_DROP_CHANCE);
        }
    }

    /** With a melee weapon, its hits total HATCHET_DAMAGE (base attack damage + the weapon's own bonus). */
    private void fitMeleeDamage() {
        ItemStack held = getMainHandItem();
        var attribute = getAttribute(Attributes.ATTACK_DAMAGE);
        if (attribute == null || held.isEmpty() || held.getItem() instanceof GunItem) {
            return;
        }
        double[] bonus = {0.0};
        held.forEachModifier(EquipmentSlot.MAINHAND, (holder, modifier) -> {
            if (holder.equals(Attributes.ATTACK_DAMAGE)
                    && modifier.operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) {
                bonus[0] += modifier.amount();
            }
        });
        attribute.setBaseValue(Math.max(0.0, HATCHET_DAMAGE - bonus[0]));
    }

    /** The pilot's revolver: "Pilot Revolver", PILOT_REVOLVER_DURABILITY (data on a normal revolver). */
    public static ItemStack pilotRevolver() {
        ItemStack stack = RaidIllager.realmArms(PILOT_GUN);
        if (!stack.isEmpty()) {
            stack.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                    net.minecraft.network.chat.Component.translatable("item.gunmetal.pilot_revolver"));
            stack.set(net.minecraft.core.component.DataComponents.MAX_DAMAGE, PILOT_REVOLVER_DURABILITY);
        }
        return stack;
    }

    /** The weapon's stack ("hatchet": Illager Invasion's, or a copper axe without it). */
    private static ItemStack weapon(String id) {
        if (id.equals("hatchet")) {
            ItemStack hatchet = stackOf(HATCHET);
            return hatchet.isEmpty() ? stackOf(HATCHET_FALLBACK) : hatchet;
        }
        return stackOf(id);
    }

    private static ItemStack stackOf(String id) {
        Identifier key = Identifier.tryParse(id);
        if (key == null) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.getOptional(key).map(Item::getDefaultInstance).orElse(ItemStack.EMPTY);
    }

    public int variant() {
        return entityData.get(VARIANT);
    }

    @Override
    public int textureVariant() {
        return variant();
    }

    /**
     * hat.ry for the model: the held weapon's group, for its idle arm pose. 0 nothing in hand (a pilot
     * aboard), 1 axe / sword / musketoon, 2 pistol / revolver, 3 carbine / repeater (and any other gun).
     * Works on the client (it reads the synced held item).
     */
    @Override
    public float extraSignal() {
        return weaponGroup(getMainHandItem());
    }

    public static int weaponGroup(ItemStack held) {
        if (held.isEmpty()) {
            return GROUP_NONE;
        }
        if (!(held.getItem() instanceof GunItem gun)) {
            return GROUP_MELEE;
        }
        return switch (gun.gunType()) {
            case PISTOL -> GROUP_SIDEARM;
            case MUSKETOON -> GROUP_MELEE;
            default -> GROUP_LONG_GUN;
        };
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Variant", variant());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        int variant = input.getIntOr("Variant", VARIANT_PILOT);
        entityData.set(VARIANT, (byte) Math.max(0, Math.min(AIRMAN_VARIANTS, variant)));
    }

    public SiegeBalloon balloon() {
        return getVehicle() instanceof SiegeBalloon balloon ? balloon : null;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        SiegeBalloon balloon = balloon();
        if (balloon != null) {
            // Operating the cannon: look where the balloon aims (the balloon itself turns to face it).
            LivingEntity target = balloon.getTarget();
            if (target != null) {
                getLookControl().setLookAt(target, 30.0f, 90.0f);
            }
        } else if (getMainHandItem().isEmpty() && tickCount > 1) {
            equip(variant() == VARIANT_PILOT ? pilotRevolver() : RaidIllager.realmArms(PILOT_GUN));
        }
        if (fanning && (!targetInFanRange() || !GunItem.isLoaded(getMainHandItem()))) {
            fanning = false;
        }
    }

    /**
     * Aim before the next shot. Revolver / repeater: the first shot of a clip is always aimed; after it,
     * with the target within FAN_RANGE it fans (the gun's fan interval), otherwise it aims again.
     * Aimed revolver shots take 25-30 ticks; other aimed shots the normal pillager aim.
     */
    @Override
    public int mobShotDelay(ItemStack gun) {
        if (!(gun.getItem() instanceof ClipGunItem clip)) {
            fanning = false;
            return -1;
        }
        if (!clip.kind.canFan()) {
            fanning = false; // the repeater can't fan (2.18.3): normal aim every round
            return -1;
        }
        boolean freshClip = ClipGunItem.rounds(gun) >= ClipGunItem.CLIP;
        fanning = !freshClip && targetInFanRange();
        if (fanning) {
            return clip.kind.fanInterval - 1; // the gun goal fires the tick after the delay runs out
        }
        return clip.kind == ClipGunItem.Kind.REVOLVER
                ? REVOLVER_AIM_MIN + getRandom().nextInt(REVOLVER_AIM_MAX - REVOLVER_AIM_MIN + 1) : -1;
    }

    /** Fanning: only while the target is still in range when the shot goes off. */
    @Override
    public boolean isFanning(ItemStack gun) {
        return fanning && targetInFanRange();
    }

    private boolean targetInFanRange() {
        LivingEntity target = getTarget();
        return target != null && target.isAlive() && distanceTo(target) <= FAN_RANGE;
    }

    @Override
    protected int animStateNow() {
        SiegeBalloon balloon = balloon();
        if (balloon != null) {
            return balloon.getTarget() != null && !balloon.pilotLost() ? STATE_ABOARD_AIMING : STATE_ABOARD;
        }
        if (isUsingItem() && GunItem.getHoldingHand(this) != null) {
            return STATE_RELOADING;
        }
        if (getTarget() == null) {
            return STATE_ON_FOOT;
        }
        if (holdsMeleeWeapon()) {
            return STATE_ON_FOOT_MELEE;
        }
        return fanning ? STATE_FANNING : STATE_ON_FOOT_TARGET;
    }

    /** Aboard: no suffocation in leaves or walls the basket brushes through. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isPassenger() && (source.is(DamageTypes.IN_WALL) || source.is(DamageTypeTags.IS_FALL))) {
            return false;
        }
        boolean seated = balloon() != null && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        return super.hurtServer(level, source, seated ? amount * SEATED_DAMAGE_TAKEN : amount);
    }

    @Override
    public IllagerArmPose getArmPose() {
        if (isCelebrating()) {
            return IllagerArmPose.CELEBRATING;
        }
        if (isPassenger()) {
            return IllagerArmPose.NEUTRAL;
        }
        if (GunItem.getHoldingHand(this) != null) {
            return isUsingItem() ? IllagerArmPose.CROSSBOW_CHARGE : isAggressive() ? IllagerArmPose.CROSSBOW_HOLD : IllagerArmPose.NEUTRAL;
        }
        if (holdsMeleeWeapon() && isAggressive()) {
            return IllagerArmPose.ATTACKING;
        }
        return IllagerArmPose.NEUTRAL;
    }

    /**
     * Client: where to draw the model relative to its hitbox. Aboard, the hitbox stands further out at
     * diagonal headings; the model is drawn back in at the straight-on distance, turning with the balloon.
     */
    public net.minecraft.world.phys.Vec3 drawOffset(float partialTick) {
        if (!(getVehicle() instanceof SiegeBalloon balloon)) {
            return net.minecraft.world.phys.Vec3.ZERO;
        }
        float yaw = net.minecraft.util.Mth.rotLerp(partialTick, balloon.yRotO, balloon.getYRot());
        double half = getBbWidth() / 2.0;
        double in = SiegeBalloon.pilotDistanceStraight(half) - SiegeBalloon.pilotDistance(yaw, half);
        return new net.minecraft.world.phys.Vec3(0.0, 0.0, in).yRot(-yaw * net.minecraft.util.Mth.DEG_TO_RAD);
    }

    /** Never steers the balloon. */
    @Override
    public boolean canControlVehicle() {
        return false;
    }

    // ---- Sounds (1.0.2: their own events, defaulting to the pillager's; celebrating stays the pillager's) ----

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return net.gunmetal.raids.registry.RaidSounds.AVIATOR_AMBIENT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return net.gunmetal.raids.registry.RaidSounds.AVIATOR_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return net.gunmetal.raids.registry.RaidSounds.AVIATOR_DEATH;
    }
}
