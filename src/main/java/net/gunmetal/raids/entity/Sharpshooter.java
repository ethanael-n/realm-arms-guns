package net.gunmetal.raids.entity;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.gunmetal.arms.entity.RealmBulletEntity;
import net.gunmetal.arms.guns.CustomGunner;
import net.gunmetal.arms.guns.GunItem;
import net.gunmetal.arms.guns.GunType;
import net.gunmetal.raids.ai.SharpshooterAttackGoal;
import net.gunmetal.raids.registry.RaidSounds;
import org.jetbrains.annotations.Nullable;

import static net.gunmetal.raids.RealmRaids.MOD_ID;

/**
 * Backline sniper with a steel rifle (drawn with its own model, gunmetal:sharpshooter_rifle).
 * Keeps 40-70 blocks from its target, preferring high ground, never backs off, and keeps 12 blocks
 * from other Bombardiers/Sharpshooters between shots (walking in the idle pose). Each shot: a 4 s
 * scope glint, tracking the target for 2.5 s, then a 1.5 s windup with the aim locked, then the shot, then
 * a 4.5-5.5 s reload standing still (one shot every ~9 s). No extra aim error at any range.
 * Smoke teleport (one charge, 40 s recharge): see TELEPORT_*.
 * Raid-spawned ones start invisible for 15 s: they pick a target and get into position, but only start
 * aiming once they are visible again. Its bullets are "ghosts": they only hit players, iron golems and
 * the target it fired at. Damage: SHOT_DAMAGE_PLAYERS to players, SHOT_DAMAGE_MOBS to anything else,
 * with armor and its enchantments only ARMOR_EFFECTIVENESS as effective (worked out here with vanilla's
 * formulas, then dealt as gunmetal:sharpshooter_shot, which skips armor and enchantments and doesn't
 * scale with difficulty). Shields, Resistance and absorption still work as usual.
 */
public class Sharpshooter extends RaidIllager implements CustomGunner {
    // ---- Tuning -------------------------------------------------------------------------
    public static final double MAX_HEALTH = 20.0;
    /** About 30% faster on the ground than the Bombardier's 0.2 (ground speed grows with the square of this). */
    public static final double MOVEMENT_SPEED = 0.23;
    public static final double FOLLOW_RANGE = 80.0;
    /** Preferred distance band: walks closer beyond the max, otherwise holds (and looks for high ground). */
    public static final float RANGE_MIN = 40.0f, RANGE_MAX = 70.0f;
    /** Fires at targets in view up to this far. */
    public static final float MAX_FIRE_RANGE = 80.0f;
    /** Scope glint before each shot (4 s); the windup is its last part (1.5 s) and the aim locks when the windup starts. */
    public static final int GLINT_TICKS = 80, WINDUP_TICKS = 30;
    /** Ticks after a shot before the next glint can start (4.5-5.5 s, standing still; a shot every 8.5-9.5 s, 9 on average). */
    public static final int COOLDOWN_MIN = 70, COOLDOWN_MAX = 90; // 1.0.3: 1 s faster (was 90-110)
    /**
     * Bullet damage before armor, against players and against everything else (any difficulty; no range
     * bonus, no mob multiplier). Replaces the rifle's damage.
     */
    public static final float SHOT_DAMAGE_PLAYERS = 21.0f, SHOT_DAMAGE_MOBS = 32.0f;
    /** Share of the normal armor + armor-enchantment reduction that still applies (0.25 = 75% less effective). */
    public static final float ARMOR_EFFECTIVENESS = 0.25f;
    /** Bullet speed: the steel rifle's times this. */
    public static final float SHOT_SPEED_MULTIPLIER = 1.5f;
    /** Knockback in arrow Punch levels. */
    public static final float KNOCKBACK = 2.0f;
    public static final float FIRE_VOLUME = 6.0f;
    /** Raid-spawned Sharpshooters start invisible for this long (15 s) and don't start aiming until it wears off. */
    public static final int RAID_INVISIBILITY_TICKS = 300;
    /** Keeps at least this far from other Bombardiers and Sharpshooters while not aiming. */
    public static final double SPACING = 12.0;
    /**
     * Smoke teleport: one charge, back this long after it's used (40 s). A melee hit sets it off after
     * TELEPORT_DELAY_TICKS; the first dodgeable projectile (anything the Artificer's pulse destroys or
     * stops, not raiders' own) sets it off at once and does no damage. Smoke round cloud at its feet, then
     * a jump in a random direction, TELEPORT_MIN to TELEPORT_RANGE blocks onto safe footing, then
     * invisibility for TELEPORT_INVISIBILITY_TICKS with no aiming (its aim is dropped; a reload carries on).
     */
    public static final int TELEPORT_RECHARGE_TICKS = 800, TELEPORT_DELAY_TICKS = 10, TELEPORT_INVISIBILITY_TICKS = 80;
    public static final double TELEPORT_RANGE = 32.0, TELEPORT_MIN = 2.0; // 1.0.3: range was 8
    /** How far up or down a teleport spot may be from where it stands (1.0.3: was 4). */
    public static final int TELEPORT_HEIGHT = 8;
    /** Smoke bomb: this many sculk soul particles, anywhere in a cube this many blocks across around it (1.0.3). */
    public static final int SMOKE_SOULS = 10;
    public static final double SMOKE_SOUL_CUBE = 4.0;
    private static final int TELEPORT_ATTEMPTS = 16;
    // -------------------------------------------------------------------------------------

    /**
     * Model states (hat.rz, see AnimSignalled):
     *   0 idle (no target, or walking between shots), 1 target acquired (standing and waiting / the first
     *   2 s of the glint, still tracking), 2 shot locked (the 2 s windup before the shot), 3 reloading
     *   (the cooldown after a shot, standing still).
     */
    public static final int STATE_IDLE = 0, STATE_ACQUIRED = 1, STATE_LOCKED = 2, STATE_RELOADING = 3;
    /** Held-item model tags by state (assets/gunmetal/items/sharpshooter_rifle.json). */
    private static final net.minecraft.world.item.component.CustomModelData[] HELD_TAGS = AnimSignalled.tags(
            "sharpshooter_idle", "sharpshooter_target_acquired", "sharpshooter_shot_locked", "sharpshooter_reloading");

    /** Bullet damage type: like gunmetal:bullet, but Protection enchantments don't reduce it. */
    public static final ResourceKey<DamageType> SHOT_DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "sharpshooter_shot"));

    /** Item model used to draw its rifle (assets/gunmetal/items/sharpshooter_rifle.json). */
    public static final Identifier RIFLE_MODEL = Identifier.fromNamespaceAndPath(MOD_ID, "sharpshooter_rifle");

    /** Synced: aiming (glint/windup), so the client can use a distinct arm pose (see getArmPose). */
    private static final EntityDataAccessor<Boolean> AIMING = SynchedEntityData.defineId(Sharpshooter.class, EntityDataSerializers.BOOLEAN);

    /** True while it still has its raid-spawn invisibility. */
    /** Ticks left of its raid-spawn stealth (no aiming meanwhile); its own clock, not the effect's. */
    private int stealthTicks;
    /** Smoke teleport: ticks until the charge is back (0 = ready), ticks until a melee-triggered jump (-1 = none). */
    private int teleportRecharge;
    private int pendingTeleport = -1;
    /** Set when a teleport drops its aim; read once by the attack goal. */
    private boolean aimInterrupted;
    private @Nullable Vec3 pendingDirection;
    private int pendingTargetId = -1;

    public Sharpshooter(EntityType<? extends AbstractIllager> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(AIMING, false);
    }

    public void setAiming(boolean aiming) {
        entityData.set(AIMING, aiming);
    }

    public boolean isAiming() {
        return entityData.get(AIMING);
    }

    @Override
    protected void addAttackGoals() {
        goalSelector.addGoal(2, new SharpshooterAttackGoal(this));
    }

    @Override
    protected boolean glowsAsLastRaider() {
        return true;
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                  EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
        // Raid.joinRaid sets the raid before calling finalizeSpawn, so this is only true for raid spawns.
        if (reason == EntitySpawnReason.EVENT && getCurrentRaid() != null) {
            addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, RAID_INVISIBILITY_TICKS, 0, false, false));
            stealthTicks = RAID_INVISIBILITY_TICKS;
        }
        return data;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        ItemStack rifle = realmArms("steel_rifle");
        if (!rifle.isEmpty()) {
            rifle.set(DataComponents.ITEM_MODEL, RIFLE_MODEL);
        }
        setItemSlot(EquipmentSlot.MAINHAND, rifle);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (stealthTicks > 0) {
            --stealthTicks; // counts down with its own invisibility; other invisibility doesn't stop it aiming
        }
        if (teleportRecharge > 0) {
            --teleportRecharge;
        }
        if (pendingTeleport > 0 && --pendingTeleport == 0) {
            pendingTeleport = -1;
            if (!smokeTeleport(level)) {
                teleportRecharge = 0; // nowhere to go: the charge isn't spent
            }
        }
    }

    public boolean hasTeleportCharge() {
        return teleportRecharge <= 0 && pendingTeleport < 0;
    }

    /** True once after a teleport dropped its aim (the attack goal cancels the glint). */
    public boolean consumeAimInterrupt() {
        boolean interrupted = aimInterrupted;
        aimInterrupted = false;
        return interrupted;
    }

    /**
     * Smoke cloud at its feet, then a jump to a random safe spot within TELEPORT_RANGE, then a short
     * invisibility with no aiming. False (nothing happens) if no spot is found.
     */
    private boolean smokeTeleport(ServerLevel level) {
        Vec3 from = position();
        RandomSource random = getRandom();
        for (int i = 0; i < TELEPORT_ATTEMPTS; ++i) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = TELEPORT_MIN + random.nextDouble() * (TELEPORT_RANGE - TELEPORT_MIN);
            double x = from.x + Math.cos(angle) * distance;
            double z = from.z + Math.sin(angle) * distance;
            double y = from.y + random.nextInt(TELEPORT_HEIGHT * 2 + 1) - TELEPORT_HEIGHT;
            if (!randomTeleport(x, y, z, false)) {
                continue;
            }
            double moved = position().distanceTo(from);
            if (moved > TELEPORT_RANGE || moved < TELEPORT_MIN) {
                teleportTo(from.x, from.y, from.z);
                continue;
            }
            SmokeRound.burstAt(level, from);
            for (int s = 0; s < SMOKE_SOULS; ++s) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                        from.x + (random.nextDouble() - 0.5) * SMOKE_SOUL_CUBE,
                        from.y + getBbHeight() / 2.0 + (random.nextDouble() - 0.5) * SMOKE_SOUL_CUBE,
                        from.z + (random.nextDouble() - 0.5) * SMOKE_SOUL_CUBE, 1, 0.0, 0.0, 0.0, 0.0);
            }
            teleportRecharge = TELEPORT_RECHARGE_TICKS;
            addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, TELEPORT_INVISIBILITY_TICKS, 0, false, false));
            stealthTicks = Math.max(stealthTicks, TELEPORT_INVISIBILITY_TICKS);
            aimInterrupted = true;
            return true;
        }
        return false;
    }

    /** The hit that sets off its smoke bomb (1.0.3; event entity.sharpshooter.smoke_bomb), where it stands. */
    private void playSmokeBombSound(ServerLevel level, Vec3 at) {
        level.playSound(null, at.x, at.y, at.z, RaidSounds.SHARPSHOOTER_SMOKE_BOMB, getSoundSource(), 1.5f, 1.0f);
    }

    /** A mob or player striking it directly (not projectiles, explosions, fire or the like; not raiders). */
    private static boolean isMeleeHit(DamageSource source) {
        return source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker
                && !source.is(DamageTypeTags.IS_PROJECTILE) && !source.is(DamageTypeTags.IS_EXPLOSION)
                && !source.is(DamageTypeTags.IS_FIRE) && !attacker.is(EntityTypeTags.RAIDERS);
    }

    /** A projectile it can dodge: one the Artificer's pulse destroys or stops, not shot by a raider. */
    private static boolean isDodgeable(DamageSource source) {
        if (!(source.getDirectEntity() instanceof Projectile projectile) || !Artificer.isPulseProjectile(projectile)) {
            return false;
        }
        Entity owner = projectile.getOwner();
        return owner == null || !owner.is(EntityTypeTags.RAIDERS);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        Vec3 hitAt = position();
        if (hasTeleportCharge() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && isDodgeable(source)
                && smokeTeleport(level)) {
            playSmokeBombSound(level, hitAt);
            return false; // dodged, like an enderman
        }
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive() && hasTeleportCharge() && isMeleeHit(source)) {
            pendingTeleport = TELEPORT_DELAY_TICKS;
            teleportRecharge = TELEPORT_RECHARGE_TICKS;
            playSmokeBombSound(level, hitAt);
        }
        return hurt;
    }

    /** Still in its raid-spawn invisibility: it may pick a target and move, but not aim. */
    public boolean isStealthed() {
        return stealthTicks > 0;
    }

    @Override
    public net.minecraft.world.item.component.@Nullable CustomModelData heldItemTag() {
        return AnimSignalled.tagFor(HELD_TAGS, animState());
    }

    @Override
    protected int animStateNow() {
        return getTarget() != null || isAiming() ? goalAnimState : STATE_IDLE;
    }

    /** Called by the attack goal right before firing: the exact direction (deviation included) and who it is aimed at. */
    public void prepareShot(Vec3 direction, int targetId) {
        this.pendingDirection = direction;
        this.pendingTargetId = targetId;
    }

    @Override
    public void customizeBullet(RealmBulletEntity bullet, ItemStack gun) {
        if (pendingDirection != null && gun.getItem() instanceof GunItem item) {
            bullet.setVelocity(item.bulletSpeed() * SHOT_SPEED_MULTIPLIER, pendingDirection);
        }
        bullet.setDropReduction(1.0f); // 1.0.3: no bullet drop
        bullet.damage = SHOT_DAMAGE_MOBS;
        bullet.damageModifier = Sharpshooter::shotDamage;
        bullet.mobDamageMultiplier = 1.0f;
        bullet.gunType = null; // no rifle range bonus
        bullet.knockback = KNOCKBACK;
        bullet.setGhostExcept(pendingTargetId);
        bullet.setVisuals(GunType.Projectile.ROUND, GunType.Trail.SNIPER);
        bullet.damageTypeOverride = SHOT_DAMAGE_TYPE;
    }

    /**
     * The shot's damage on this target: players or mobs base, minus ARMOR_EFFECTIVENESS of what armor and
     * its enchantments (Protection, Projectile Protection) would normally take off. Vanilla's own
     * formulas, so armor toughness and the like count as usual.
     */
    private static float shotDamage(Entity target, float damage, DamageSource source) {
        float raw = target instanceof Player ? SHOT_DAMAGE_PLAYERS : SHOT_DAMAGE_MOBS;
        if (!(target instanceof LivingEntity living) || !(target.level() instanceof ServerLevel level)) {
            return raw;
        }
        float afterArmor = CombatRules.getDamageAfterAbsorb(living, raw, source, living.getArmorValue(),
                (float) living.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        float protection = EnchantmentHelper.getDamageProtection(level, living, source);
        float normal = protection > 0f ? CombatRules.getDamageAfterMagicAbsorb(afterArmor, protection) : afterArmor;
        return raw - ARMOR_EFFECTIVENESS * Math.max(0f, raw - normal);
    }

    @Override
    public @Nullable SoundEvent gunFireSound(ItemStack gun) {
        return RaidSounds.SHARPSHOOTER_FIRE;
    }

    @Override
    public float gunFireVolume() {
        return FIRE_VOLUME;
    }

    @Override
    public IllagerArmPose getArmPose() {
        if (isCelebrating()) {
            return IllagerArmPose.CELEBRATING;
        }
        // Pose signals for custom models: BOW_AND_ARROW while aiming (glint + windup), CROSSBOW_HOLD otherwise.
        if (isAiming()) {
            return IllagerArmPose.BOW_AND_ARROW;
        }
        if (GunItem.getHoldingHand(this) != null) {
            return IllagerArmPose.CROSSBOW_HOLD;
        }
        return isAggressive() ? IllagerArmPose.ATTACKING : IllagerArmPose.NEUTRAL;
    }


    @Override
    protected SoundEvent getAmbientSound() {
        return RaidSounds.SHARPSHOOTER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return RaidSounds.SHARPSHOOTER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return RaidSounds.SHARPSHOOTER_DEATH;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("StealthTicks", stealthTicks);
        output.putInt("TeleportRecharge", teleportRecharge);
        output.putInt("PendingTeleport", pendingTeleport);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        stealthTicks = input.getIntOr("StealthTicks", 0);
        teleportRecharge = input.getIntOr("TeleportRecharge", 0);
        pendingTeleport = input.getIntOr("PendingTeleport", -1);
    }
}
