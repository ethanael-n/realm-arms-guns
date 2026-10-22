package net.gunmetal.raids.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.gunmetal.arms.entity.RealmBulletEntity;
import net.gunmetal.arms.guns.CustomGunner;
import net.gunmetal.arms.guns.GunType;
import net.gunmetal.raids.ai.BombardierAttackGoal;
import net.gunmetal.raids.registry.RaidSounds;
import org.jetbrains.annotations.Nullable;

/**
 * Backline artillery. Carries a hand cannon (drawn invisible: the cannon is meant to be part of the
 * mob's own model) and fires it like a mortar: within 16 blocks with a clear view it shoots straight,
 * otherwise it lobs shells in a high arc at targets it doesn't need to see. Shells land within 5 blocks
 * of where the target was standing (no leading). Every shot's burst is bigger than the last.
 */
public class Bombardier extends RaidIllager implements CustomGunner {
    // ---- Tuning -------------------------------------------------------------------------
    public static final double MAX_HEALTH = 30.0;
    public static final double MOVEMENT_SPEED = 0.2;
    /** Also how far away it notices targets (it doesn't need to see them). */
    public static final double FOLLOW_RANGE = 96.0;
    /** Walks toward its target until this close, then stands and fires. Never backs off. */
    public static final float IDEAL_RANGE = 75.0f;
    /** Fires at targets up to this far (about the furthest a high arc reaches at this shell speed). */
    public static final float MAX_FIRE_RANGE = 90.0f;
    /** Straight shots only within this distance (and with line of sight); otherwise it lobs. */
    public static final float DIRECT_FIRE_RANGE = 16.0f;
    /**
     * Ramp-up: everything below moves evenly from its first-shot value to its peak over the first
     * RAMP_SHOTS shots it fires, and stays at the peak after that (saved with the mob). The longer a
     * Bombardier is left alive, the more dangerous it gets.
     */
    public static final int RAMP_SHOTS = 24;
    /** Reload (seconds between shots): first shot / peak. */
    public static final float RELOAD_FIRST = 10.0f, RELOAD_PEAK = 4.8f;
    /** Burst radius, rolled uniformly between min and max each shot: first shot / peak (the min grows 0.1 a shot). */
    public static final float RADIUS_MIN_FIRST = 0.9f, RADIUS_MIN_PEAK = 3.3f;
    public static final float RADIUS_MAX_FIRST = 1.8f, RADIUS_MAX_PEAK = 4.0f;
    /** Lobbed shells land within this many blocks of where the target stood: first shot, minus this per shot, down to the floor (reached on shot 17). */
    public static final float MISS_MARGIN_FIRST = 20.0f, MISS_MARGIN_PER_SHOT = 1.0f, MISS_MARGIN_FLOOR = 4.0f;
    /** It stands still for this long before each shot ("loading"); it can reposition the rest of the time. */
    public static final int LOADING_STANCE_TICKS = 60;
    /** Delay before its first shot at a new target (it stands still for it). */
    public static final int FIRST_SHOT_DELAY_MIN = 60, FIRST_SHOT_DELAY_MAX = 100;
    /** Keeps at least this far from other Bombardiers and Sharpshooters while not loading. */
    public static final double SPACING = 8.0;
    /** Shell speed in blocks per second (60 = 3 blocks/tick; a high arc reaches ~90 blocks, 75 at ~66 degrees). */
    public static final float SHELL_SPEED = 60.0f;
    /** Shells (and the muzzle smoke) start this far to its right of its eyes, beside its head. */
    public static final double MUZZLE_RIGHT = 0.5;
    /** Shells live this long (10 s; a 75-block lob takes ~4.7 s). */
    public static final int SHELL_LIFETIME_TICKS = 200;
    /** Shells deal this share of the hand cannon's direct-hit damage, and of the normal burst damage (to everyone). */
    public static final float DIRECT_DAMAGE_MULTIPLIER = 0.2f, BURST_DAMAGE_MULTIPLIER = 0.5f;
    /** Fire sound volume: heard out to 16 x this blocks. */
    public static final float FIRE_VOLUME = 6.0f;
    /**
     * Weakness on players its shells hit (direct hit or burst), by shot number (1 = its first shot):
     * from WEAKNESS_FROM_SHOT[i] on, WEAKNESS_TICKS[i] of Weakness level WEAKNESS_AMPLIFIER[i] + 1.
     */
    public static final int[] WEAKNESS_FROM_SHOT = {6, 12, 18};
    public static final int[] WEAKNESS_TICKS = {120, 240, 240};
    public static final int[] WEAKNESS_AMPLIFIER = {0, 0, 1};
    /** Entity tag on a shell carrying its weakness tier (index into the arrays above). */
    private static final String WEAKNESS_TAG = "gunmetal.bombardier_weakness_";
    /** Illagers caught in a shell's burst take this share of its damage. */
    public static final float ILLAGER_BURST_DAMAGE = 0.5f;
    // -------------------------------------------------------------------------------------

    /**
     * Model states (hat.rz, see AnimSignalled):
     *   0 idle (no target), 1 reloading (has a target, between shots, free to move),
     *   2 aiming a lob (standing still for the 3 s before a high-arc shot),
     *   3 aiming direct fire (the same 3 s, target within 16 blocks and in view).
     */
    public static final int STATE_IDLE = 0, STATE_RELOADING = 1, STATE_AIM_LOB = 2, STATE_AIM_DIRECT = 3;

    private int shotsFired;
    /** Ticks until the next shot (counts down while it has a target; saved). */
    private int reloadTicks;
    /** Set just before each shot for {@link #customizeBullet}: the exact launch direction (null = keep the gun's spread). */
    private @Nullable Vec3 pendingDirection;
    /** Set just before each shot: where the shell starts (beside its head, to its right). */
    private @Nullable Vec3 pendingOrigin;
    private float pendingRadius;

    /** Size (1.0.3): model and hitbox 1.1x (hitbox 0.66 x 2.15), about an Illager Invasion inquisitor. Vanilla's scale attribute. */
    public static final double SCALE = 1.1;

    public Bombardier(EntityType<? extends AbstractIllager> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE)
                .add(Attributes.SCALE, SCALE);
    }

    @Override
    protected void addAttackGoals() {
        goalSelector.addGoal(2, new BombardierAttackGoal(this));
    }

    @Override
    protected boolean targetsNeedSight() {
        return false;
    }

    @Override
    protected boolean glowsAsLastRaider() {
        return true;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.MAINHAND, realmArms("hand_cannon"));
    }

    /** 0 on its first shot, 1 once it has fired RAMP_SHOTS shots. */
    public float rampProgress() {
        return Mth.clamp(shotsFired / (float) RAMP_SHOTS, 0f, 1f);
    }

    public int reloadTicksNow() {
        return Math.round(20f * Mth.lerp(rampProgress(), RELOAD_FIRST, RELOAD_PEAK));
    }

    public float missMarginNow() {
        return Math.max(MISS_MARGIN_FLOOR, MISS_MARGIN_FIRST - MISS_MARGIN_PER_SHOT * shotsFired);
    }

    /** A burst radius for the next shot, rolled between the current min and max. */
    public float rollBurstRadius() {
        float p = rampProgress();
        float min = Mth.lerp(p, RADIUS_MIN_FIRST, RADIUS_MIN_PEAK);
        float max = Mth.lerp(p, RADIUS_MAX_FIRST, RADIUS_MAX_PEAK);
        return min + getRandom().nextFloat() * (max - min);
    }

    public int reloadTicks() {
        return reloadTicks;
    }

    public void setReloadTicks(int ticks) {
        reloadTicks = ticks;
    }

    /** Called by the attack goal right before firing. */
    public void prepareShot(@Nullable Vec3 exactDirection, @Nullable Vec3 origin) {
        this.pendingDirection = exactDirection;
        this.pendingOrigin = origin;
        this.pendingRadius = rollBurstRadius();
    }

    public void onShotFired() {
        markShot();
        ++shotsFired;
        pendingDirection = null;
        pendingOrigin = null;
        reloadTicks = reloadTicksNow();
    }

    @Override
    public void customizeBullet(RealmBulletEntity bullet, ItemStack gun) {
        Vec3 direction = pendingDirection != null ? pendingDirection : bullet.getDeltaMovement().normalize();
        if (pendingOrigin != null) {
            bullet.setPos(pendingOrigin);
            bullet.firedFrom = pendingOrigin;
        }
        bullet.setDropReduction(0f);
        bullet.setVelocity(SHELL_SPEED, direction);
        bullet.setPassesTagged(true); // flies through Siege Balloons, and its burst spares them (1.0.3)
        bullet.setVisuals(GunType.Projectile.CANNONBALL, GunType.Trail.CANNONBALL);
        bullet.setLifetimeTicks(SHELL_LIFETIME_TICKS);
        // No gun type: the burst uses the fixed radius below instead of the hand cannon's distance scaling.
        bullet.gunType = null;
        bullet.explosionRadius = pendingRadius;
        bullet.mobDamageMultiplier = DIRECT_DAMAGE_MULTIPLIER;
        bullet.burstDamageScale = BURST_DAMAGE_MULTIPLIER;
        bullet.spareShooter = false;
        bullet.illagerBurstScale = ILLAGER_BURST_DAMAGE;
        int tier = weaknessTier(shotsFired + 1);
        if (tier >= 0) {
            bullet.addTag(WEAKNESS_TAG + tier);
        }
    }

    /** Index into the WEAKNESS_ arrays for this shot number, or -1 for none. */
    private static int weaknessTier(int shotNumber) {
        int tier = -1;
        for (int i = 0; i < WEAKNESS_FROM_SHOT.length; ++i) {
            if (shotNumber >= WEAKNESS_FROM_SHOT[i]) {
                tier = i;
            }
        }
        return tier;
    }

    /** Registered once: a player hurt by a tagged shell (direct hit or its burst) gets that shell's Weakness. */
    public static void registerEvents() {
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register(
                (entity, source, baseDamage, damageTaken, blocked) -> {
                    if (blocked || damageTaken <= 0f || !(entity instanceof net.minecraft.world.entity.player.Player)
                            || source.getDirectEntity() == null) {
                        return;
                    }
                    for (int i = WEAKNESS_FROM_SHOT.length - 1; i >= 0; --i) {
                        if (source.getDirectEntity().entityTags().contains(WEAKNESS_TAG + i)) {
                            entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                    net.minecraft.world.effect.MobEffects.WEAKNESS, WEAKNESS_TICKS[i], WEAKNESS_AMPLIFIER[i]),
                                    source.getEntity());
                            return;
                        }
                    }
                });
    }

    @Override
    protected int animStateNow() {
        return getTarget() != null && isAggressive() ? goalAnimState : STATE_IDLE;
    }

    @Override
    public @Nullable SoundEvent gunFireSound(ItemStack gun) {
        return RaidSounds.BOMBARDIER_FIRE;
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
        // Never CROSSED: that pose hides the separate arms, which Fresh Animations-style models need.
        return isAggressive() ? IllagerArmPose.CROSSBOW_HOLD : IllagerArmPose.NEUTRAL;
    }


    @Override
    protected SoundEvent getAmbientSound() {
        return RaidSounds.BOMBARDIER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return RaidSounds.BOMBARDIER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return RaidSounds.BOMBARDIER_DEATH;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("ShotsFired", shotsFired);
        output.putInt("ReloadTicks", reloadTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        shotsFired = input.getIntOr("ShotsFired", 0);
        reloadTicks = input.getIntOr("ReloadTicks", 0);
    }
}
