package net.gunmetal.raids.entity;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.SpearUseGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.gunmetal.arms.entity.RealmBulletEntity;
import net.gunmetal.arms.guns.CustomGunner;
import net.gunmetal.arms.guns.GunItem;
import net.gunmetal.arms.guns.GunType;
import net.gunmetal.raids.ai.Ballistics;
import net.gunmetal.raids.ai.FixBayonetGoal;
import net.gunmetal.raids.ai.TrooperGunGoal;
import net.gunmetal.raids.registry.RaidSounds;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static net.gunmetal.raids.RealmRaids.MOD_ID;

/**
 * Line infantry. Steel arquebus with 7 shots (officers 6; 20% less damage than a pillager's), fired like a
 * pillager arquebusier but 0.5 s slower. Its last shot is always a smoke round. When its shots are
 * spent it fixes its bayonet (1.5 s, standing still) and fights like a vanilla spear mob (charge
 * through, back off, charge again) with stone spear stats: 1.2x normal ground speed, 1.75x while
 * charging, 30% less damage taken while charging. It also fixes the bayonet at once, skipping its
 * remaining shots and the smoke round, when it drops below 10 health for any reason (melee hits alone no
 * longer trigger it).
 *
 * Rally: a Trooper below 20 health when it fires its smoke round calls a rally. Officers (18% of
 * Troopers, drawn with textures/entity/illager/trooper_officer.png) call one whenever they fix their
 * bayonet for any reason, except when they were themselves called by a rally. A rally: it blows a horn
 * (once, heard ~128 blocks) and every Trooper within 32 blocks that still has shots fixes bayonets too and gets
 * Resistance I for 12 s, as does every mob in #gunmetal:rally_followers in that range (vindicators,
 * ravagers, Illager Invasion's bashers and inquisitors). It and every Trooper reached yell a charge,
 * each 0-5 s later (heard to 24 blocks). Troopers called this way never rally others. While charging,
 * a Trooper takes no damage from other Troopers' bayonets.
 *
 * It spawns with an unloaded gun (its first move is a reload).
 *
 * Shield: it spawns with a shield that cuts all damage it takes by 80% and breaks after 3 hits of any
 * kind (each fire, poison or other damage tick counts, so damage over time strips it fast). Damage
 * caused by a raider (#minecraft:raiders) is reduced but doesn't count toward breaking it; /kill and
 * the void ignore it. The shield is a part of the .jem (on the left arm): hat.ry = hits left (0 = gone).
 * Knockback resistance 0.75 while its shield holds, none once it breaks.
 *
 * Arm pose signals for custom models: CROSSBOW_HOLD / CROSSBOW_CHARGE with the gun (also while
 * fixing the bayonet), NEUTRAL with the bayonet, BOW_AND_ARROW while charging.
 */
public class Trooper extends RaidIllager implements CustomGunner {
    // ---- Tuning -------------------------------------------------------------------------
    public static final double MAX_HEALTH = 28.0;
    public static final double MOVEMENT_SPEED = 0.35;
    /** Base attack damage (pillager/vindicator value). Spear charges add speed-based damage on top. */
    public static final double ATTACK_DAMAGE = 5.0;
    /** Knockback resistance while the shield holds (none once it breaks). */
    public static final double KNOCKBACK_RESISTANCE = 0.75;
    /** Shots before it fixes its bayonet: rank-and-file / officers. */
    public static final int SHOTS = 7, OFFICER_SHOTS = 6;
    /** Ticks added before every shot compared with a pillager arquebusier (0.5 s). */
    public static final int EXTRA_AIM_TICKS = 10;
    /** Its gun's damage is multiplied by this (before the usual mob multiplier). */
    public static final float RANGED_DAMAGE_MULTIPLIER = 0.8f;
    /** Below this much health (for any reason) it gives up its remaining shots and fixes the bayonet. */
    public static final float LOW_HEALTH_BAYONET = 10.0f;
    /** Share of Troopers that spawn as officers (rally whenever they fix their bayonet). */
    public static final float OFFICER_CHANCE = 0.18f;
    /** Shield: damage taken is multiplied by this (80% resistance) until it has taken this many hits. */
    public static final float SHIELD_DAMAGE_TAKEN = 0.2f;
    public static final int SHIELD_HITS = 3;
    public static final int FIX_BAYONET_TICKS = 30;
    /**
     * Real ground speed with the bayonet (x normal) and while charging (x normal). A mob's ground speed
     * grows with the square of its speed setting, so the code uses the square roots of these.
     */
    public static final double BAYONET_SPEED = 1.2, CHARGE_SPEED = 1.75;
    /** Damage taken while charging is multiplied by this (30% resistance). */
    public static final float CHARGE_DAMAGE_TAKEN = 0.7f;
    /** Smoke round: speed (blocks/second) and lifetime (ticks); it bursts on impact or when it runs out. */
    public static final float SMOKE_SPEED = 20.0f;
    public static final int SMOKE_LIFETIME_TICKS = 20;
    /** Rally (non-officers): below this much health when it fires its smoke round; Troopers within this range join. */
    public static final float RALLY_HEALTH = 20.0f;
    /** Each Trooper in a rally yells after a random delay up to this (ticks, 5 s), heard to 16 x the volume in blocks. */
    public static final int RALLY_YELL_MAX_DELAY = 100;
    public static final float RALLY_YELL_VOLUME = 1.5f;
    /** Other mobs that get the rally's Resistance (entity type tag). */
    public static final TagKey<EntityType<?>> RALLY_FOLLOWERS = TagKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(MOD_ID, "rally_followers"));
    public static final double RALLY_RANGE = 32.0;
    /** Horn volume (heard out to 16 x this blocks). */
    public static final float HORN_VOLUME = 8.0f;
    /** Troopers answering a rally get Resistance (amplifier 0 = level I) for this long (12 s). */
    public static final int RALLY_RESISTANCE_TICKS = 240, RALLY_RESISTANCE_AMPLIFIER = 0;
    // -------------------------------------------------------------------------------------

    /**
     * Model states (hat.rz, see AnimSignalled). The bayonet jab is the normal attack swing (swing_progress).
     *   0 idle (no target)            1 gun: aiming                  2 gun: smoke round chambered (aiming it)
     *   3 fixing the bayonet (1 s)    4 bayonet fixed, not charging  5 charging
     *   6 gun: reloading (loading any shot, the smoke round included)
     */
    public static final int STATE_IDLE = 0, STATE_GUN = 1, STATE_SMOKE_CHAMBERED = 2, STATE_FIXING = 3,
            STATE_BAYONET = 4, STATE_CHARGING = 5, STATE_GUN_RELOADING = 6;
    /** Held-item model tags by state (assets/gunmetal/items/trooper_arquebus.json / trooper_bayonet.json). */
    private static final net.minecraft.world.item.component.CustomModelData[] HELD_TAGS = AnimSignalled.tags(
            "trooper_idle", "trooper_aiming", "trooper_smoke_chambered", "trooper_fixing",
            "trooper_bayonet_fixed", "trooper_charging", "trooper_reloading");

    public static final Identifier ARQUEBUS_MODEL = Identifier.fromNamespaceAndPath(MOD_ID, "trooper_arquebus");
    public static final Identifier BAYONET_MODEL = Identifier.fromNamespaceAndPath(MOD_ID, "trooper_bayonet");
    /** The helmet item model of Troopers saved before 1.4.5 (removed from their heads on load). */
    public static final Identifier HELMET_MODEL = Identifier.fromNamespaceAndPath(MOD_ID, "trooper_helmet");
    private static final Identifier BAYONET_SPEED_ID = Identifier.fromNamespaceAndPath(MOD_ID, "bayonet_speed");
    private static final Identifier BAYONET_KNOCKBACK_ID = Identifier.fromNamespaceAndPath(MOD_ID, "bayonet_knockback_resistance");
    /** Synced so the client knows the phase (arm poses). */
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(Trooper.class, EntityDataSerializers.BYTE);
    /** Synced for the model (hat.ry) and the texture. */
    private static final EntityDataAccessor<Byte> SHIELD = SynchedEntityData.defineId(Trooper.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> OFFICER = SynchedEntityData.defineId(Trooper.class, EntityDataSerializers.BOOLEAN);

    public enum Phase { GUN, FIXING, BAYONET }

    private int shots;
    private int fixTicks;
    /** It has called its rally (a Trooper calls at most one). */
    private boolean calledRally;
    /** Set for the shot being fired when it is the smoke round. */
    private boolean firingSmoke;
    private boolean rallied;
    /** Ticks until its rally yell (-1 = none pending). */
    private int rallyYellTicks = -1;

    public Trooper(EntityType<? extends AbstractIllager> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
                .add(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE, (byte) Phase.GUN.ordinal());
        builder.define(SHIELD, (byte) 0);
        builder.define(OFFICER, false);
    }

    @Override
    protected void addAttackGoals() {
        goalSelector.addGoal(1, new FixBayonetGoal(this));
        goalSelector.addGoal(2, new TrooperGunGoal(this, EXTRA_AIM_TICKS));
        // The bayonet speed is applied as an attribute modifier; the charge adds the rest on top of it.
        goalSelector.addGoal(2, new SpearUseGoal<>(this, Math.sqrt(CHARGE_SPEED / BAYONET_SPEED), 1.0, 10.0f, 2.0f));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, false));
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                  EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
        entityData.set(OFFICER, level.getRandom().nextFloat() < OFFICER_CHANCE);
        shots = isOfficer() ? OFFICER_SHOTS : SHOTS;
        setPhase(Phase.GUN);
        return super.finalizeSpawn(level, difficulty, reason, groupData);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        ItemStack gun = realmArms("steel_arquebus");
        if (!gun.isEmpty()) {
            gun.set(DataComponents.ITEM_MODEL, ARQUEBUS_MODEL);
        }
        setItemSlot(EquipmentSlot.MAINHAND, gun);
        entityData.set(SHIELD, (byte) SHIELD_HITS);
        updateKnockbackResistance();
    }

    /** Knockback resistance only while the shield holds (none with or without the bayonet otherwise). */
    private void updateKnockbackResistance() {
        AttributeInstance knockback = getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockback != null) {
            knockback.removeModifier(BAYONET_KNOCKBACK_ID); // Troopers saved before 1.5.0
            knockback.setBaseValue(shieldHitsLeft() > 0 ? KNOCKBACK_RESISTANCE : 0.0);
        }
    }

    public boolean isOfficer() {
        return entityData.get(OFFICER);
    }

    public int shieldHitsLeft() {
        return entityData.get(SHIELD);
    }

    @Override
    public float extraSignal() {
        return shieldHitsLeft();
    }

    @Override
    public boolean altTexture() {
        return isOfficer();
    }

    // ---- Phase ------------------------------------------------------------------------------

    public Phase phase() {
        int index = entityData.get(PHASE);
        return index >= 0 && index < Phase.values().length ? Phase.values()[index] : Phase.GUN;
    }

    private void setPhase(Phase phase) {
        entityData.set(PHASE, (byte) phase.ordinal());
    }

    public boolean inGunPhase() {
        return phase() == Phase.GUN && shots > 0;
    }

    public boolean isFixingBayonet() {
        return phase() == Phase.FIXING;
    }

    /** Charging with the bayonet (the spear is lowered). Works on both sides. */
    public boolean isCharging() {
        return phase() == Phase.BAYONET && isUsingItem() && getUseItem().has(DataComponents.KINETIC_WEAPON);
    }

    /** Called by the gun goal just before a shot: is this the smoke round (its last shot)? */
    public void prepareShot() {
        firingSmoke = shots == 1;
    }

    /** Called by the gun goal after each shot. */
    public void onShotFired() {
        markShot();
        boolean smoke = firingSmoke;
        firingSmoke = false;
        if (--shots > 0) {
            return;
        }
        if (smoke && !rallied && !calledRally && getHealth() < RALLY_HEALTH && level() instanceof ServerLevel level) {
            rally(level);
        }
        startFixingBayonet(); // (an officer rallies from here)
    }

    /** Horn, and every Trooper nearby that still has shots fixes bayonets (without rallying in turn). */
    private void rally(ServerLevel level) {
        calledRally = true;
        level.playSound(null, getX(), getY(), getZ(), RaidSounds.TROOPER_HORN, SoundSource.HOSTILE, HORN_VOLUME, 1.0f);
        List<Trooper> nearby = level.getEntitiesOfClass(Trooper.class, getBoundingBox().inflate(RALLY_RANGE),
                other -> other != this && other.isAlive() && other.distanceToSqr(this) <= RALLY_RANGE * RALLY_RANGE);
        for (Trooper other : nearby) {
            other.answerRally();
        }
        List<Mob> followers = level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(RALLY_RANGE),
                mob -> mob.isAlive() && mob.is(RALLY_FOLLOWERS) && mob.distanceToSqr(this) <= RALLY_RANGE * RALLY_RANGE);
        for (Mob follower : followers) {
            follower.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, RALLY_RESISTANCE_TICKS, RALLY_RESISTANCE_AMPLIFIER));
        }
        scheduleRallyYell();
    }

    private void scheduleRallyYell() {
        rallyYellTicks = getRandom().nextInt(RALLY_YELL_MAX_DELAY + 1);
    }

    private void answerRally() {
        if (!rallied && !calledRally) {
            scheduleRallyYell(); // each Trooper yells once, at its first rally (officers can call several per fight)
        }
        rallied = true;
        addEffect(new MobEffectInstance(MobEffects.RESISTANCE, RALLY_RESISTANCE_TICKS, RALLY_RESISTANCE_AMPLIFIER));
        startFixingBayonet();
    }

    private void startFixingBayonet() {
        shots = 0;
        if (phase() != Phase.GUN) {
            return;
        }
        setPhase(Phase.FIXING);
        fixTicks = FIX_BAYONET_TICKS;
        // Officers rally whenever they fix the bayonet, unless a rally called them.
        if (isOfficer() && !rallied && !calledRally && level() instanceof ServerLevel level) {
            rally(level);
        }
        if (isUsingItem()) {
            stopUsingItem();
        }
        getNavigation().stop();
        playSound(RaidSounds.TROOPER_FIX_BAYONET, 1.0f, 0.9f + getRandom().nextFloat() * 0.2f);
    }

    private void equipBayonet() {
        ItemStack spear = new ItemStack(Items.STONE_SPEAR);
        spear.set(DataComponents.ITEM_MODEL, BAYONET_MODEL);
        spear.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        setItemSlot(EquipmentSlot.MAINHAND, spear);
        setDropChance(EquipmentSlot.MAINHAND, 0.0f);
        setPhase(Phase.BAYONET);
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(BAYONET_SPEED_ID)) {
            speed.addPermanentModifier(new AttributeModifier(BAYONET_SPEED_ID, Math.sqrt(BAYONET_SPEED) - 1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        playSound(RaidSounds.TROOPER_EQUIP_BAYONET, 1.0f, 0.9f + getRandom().nextFloat() * 0.2f);
    }

    // ---- Shots ------------------------------------------------------------------------------

    @Override
    public void customizeBullet(RealmBulletEntity bullet, ItemStack gun) {
        if (!firingSmoke) {
            bullet.damage *= RANGED_DAMAGE_MULTIPLIER;
            return;
        }
        // Smoke round: slow, short-lived, no damage; bursts into a smoke cloud (SmokeRound).
        Vec3 origin = bullet.position();
        Vec3 direction = bullet.getDeltaMovement().normalize();
        LivingEntity target = getTarget();
        if (target != null) {
            Vec3 aim = new Vec3(target.getX(), target.getY(0.5), target.getZ());
            Vec3 solved = Ballistics.solve(origin, aim, SMOKE_SPEED / 20.0, 0.05, false);
            if (solved != null) {
                direction = solved;
            } else {
                // Out of reach: send it toward the target at 15 degrees; it bursts where it runs out.
                Vec3 flat = new Vec3(aim.x - origin.x, 0.0, aim.z - origin.z).normalize();
                double up = Math.toRadians(15);
                direction = new Vec3(flat.x * Math.cos(up), Math.sin(up), flat.z * Math.cos(up));
            }
        }
        bullet.setDropReduction(0f);
        bullet.setVelocity(SMOKE_SPEED, direction);
        bullet.setLifetimeTicks(SMOKE_LIFETIME_TICKS);
        bullet.damage = 0f;
        bullet.gunType = null;
        bullet.setVisuals(GunType.Projectile.ROUND, GunType.Trail.SMALL);
        bullet.payload = SmokeRound.INSTANCE;
    }

    // ---- Ticking ----------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (rallyYellTicks >= 0 && rallyYellTicks-- == 0) {
            playSound(RaidSounds.TROOPER_RALLY_YELL, RALLY_YELL_VOLUME, getVoicePitch());
        }
        Phase phase = phase();
        if (phase == Phase.GUN) {
            if (shots <= 0) {
                startFixingBayonet();
            }
        } else if (phase == Phase.FIXING) {
            getNavigation().stop();
            if (--fixTicks <= 0) {
                equipBayonet();
            }
        }
    }

    @Override
    public net.minecraft.world.item.component.@Nullable CustomModelData heldItemTag() {
        return AnimSignalled.tagFor(HELD_TAGS, animState());
    }

    @Override
    protected int animStateNow() {
        Phase phase = phase();
        if (phase == Phase.FIXING) {
            return STATE_FIXING;
        }
        if (getTarget() == null) {
            return STATE_IDLE;
        }
        if (phase == Phase.BAYONET) {
            return isCharging() ? STATE_CHARGING : STATE_BAYONET;
        }
        if (isUsingItem()) {
            return STATE_GUN_RELOADING; // loading the next shot (also the smoke round)
        }
        return shots == 1 ? STATE_SMOKE_CHAMBERED : STATE_GUN;
    }


    // ---- Damage -----------------------------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // Charging Troopers are never hurt by other Troopers' bayonets (direct hits from a Trooper, not its bullets).
        if (isCharging() && source.getDirectEntity() instanceof Trooper other && other != this) {
            return false;
        }
        // The shield cuts everything but /kill, the void and the like; raider-caused hits don't wear it down.
        boolean shielded = shieldHitsLeft() > 0 && amount > 0f && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        float dealt = isCharging() ? amount * CHARGE_DAMAGE_TAKEN : amount;
        if (shielded) {
            dealt *= SHIELD_DAMAGE_TAKEN;
        }
        boolean hurt = super.hurtServer(level, source, dealt);
        if (!hurt) {
            return false;
        }
        if (shielded && !causedByRaider(source)) {
            int left = shieldHitsLeft() - 1;
            entityData.set(SHIELD, (byte) Math.max(0, left));
            if (left <= 0) {
                breakShield(level);
            }
        }
        if (isAlive() && phase() == Phase.GUN && getHealth() < LOW_HEALTH_BAYONET) {
            startFixingBayonet();
        }
        return true;
    }

    private static boolean causedByRaider(DamageSource source) {
        return source.getEntity() != null && source.getEntity().is(EntityTypeTags.RAIDERS);
    }

    private void breakShield(ServerLevel level) {
        entityData.set(SHIELD, (byte) 0);
        updateKnockbackResistance();
        level.playSound(null, getX(), getY(), getZ(), RaidSounds.TROOPER_SHELL_BREAK, getSoundSource(),
                1.0f, 0.9f + getRandom().nextFloat() * 0.2f);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.SHIELD), getX(), getY(0.6), getZ(),
                12, 0.3, 0.3, 0.3, 0.08);
    }

    // ---- Looks & sounds ---------------------------------------------------------------------

    @Override
    public IllagerArmPose getArmPose() {
        if (isCelebrating()) {
            return IllagerArmPose.CELEBRATING;
        }
        Phase phase = phase();
        if (phase == Phase.BAYONET) {
            return isCharging() ? IllagerArmPose.BOW_AND_ARROW : IllagerArmPose.NEUTRAL;
        }
        if (GunItem.getHoldingHand(this) != null) {
            return phase == Phase.GUN && isUsingItem() ? IllagerArmPose.CROSSBOW_CHARGE : IllagerArmPose.CROSSBOW_HOLD;
        }
        return isAggressive() ? IllagerArmPose.ATTACKING : IllagerArmPose.NEUTRAL;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return RaidSounds.TROOPER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        // While the shield is up a hit sounds like the shield (the hit that breaks it too; shell_break follows).
        return shieldHitsLeft() > 0 ? RaidSounds.TROOPER_SHIELD_HIT : RaidSounds.TROOPER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return RaidSounds.TROOPER_DEATH;
    }

    // ---- Save -------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Shots", shots);
        output.putInt("Phase", phase().ordinal());
        output.putInt("FixTicks", fixTicks);
        output.putInt("ShieldHits", shieldHitsLeft());
        output.putBoolean("Officer", isOfficer());
        output.putBoolean("Rallied", rallied);
        output.putBoolean("CalledRally", calledRally);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        shots = input.getIntOr("Shots", SHOTS);
        int index = input.getIntOr("Phase", 0);
        setPhase(index >= 0 && index < Phase.values().length ? Phase.values()[index] : Phase.GUN);
        fixTicks = input.getIntOr("FixTicks", FIX_BAYONET_TICKS);
        // Saved before 1.4.5: an intact helmet becomes a full shield, and the helmet item comes off its head.
        int shield = input.getIntOr("ShieldHits", input.getBooleanOr("Helmet", false) ? SHIELD_HITS : 0);
        entityData.set(SHIELD, (byte) Math.max(0, Math.min(SHIELD_HITS, shield)));
        updateKnockbackResistance();
        ItemStack head = getItemBySlot(EquipmentSlot.HEAD);
        if (HELMET_MODEL.equals(head.get(DataComponents.ITEM_MODEL))) {
            setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
        entityData.set(OFFICER, input.getBooleanOr("Officer", false));
        rallied = input.getBooleanOr("Rallied", false);
        calledRally = input.getBooleanOr("CalledRally", false);
    }
}
