package net.gunmetal.raids.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.gunmetal.arms.entity.RealmBulletEntity;
import net.gunmetal.arms.guns.CustomGunner;
import net.gunmetal.arms.guns.GunItem;
import net.gunmetal.raids.ai.Ballistics;
import net.gunmetal.raids.registry.RaidEntities;
import net.gunmetal.raids.registry.RaidParticles;
import net.gunmetal.raids.registry.RaidSounds;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Siege Balloon: a 6 wide, 4 tall floating raider warship with no pathfinding, tethered to its own
 * ravager and crewed by an Aviator (its pilot). It always arrives as a package (spawn egg, /summon or a raid
 * wave): balloon + ravager + pilot, all three counted as raiders in a raid.
 *
 * Layout (relative to the balloon's position, the bottom-centre of its hitbox): the pilot stands
 * outside the bottom-front face, feet level with the bottom (slid further out at diagonal headings so
 * its upright hitbox always stays outside the balloon's; its model is drawn at the true spot). The
 * cannon fires from under the bottom 0.6 behind the centre, the crossbow from two barrels 3 apart at the
 * pilot's spot, 0.7 above the bottom; paratroopers drop under the hull, and the tether pulls from the
 * bottom-centre (its chains are drawn from 2.5 above the bottom).
 *
 * Height ("ground" = first solid block or liquid straight below; rises at 1 block/s): high rise
 * (keeps rising; the tether holds it) by default; mid rise (bottom 11 above the ground) once damaged
 * (sinking 1 block/s); low rise (bottom 2.7 above) once burning (1.5 blocks/s), once the pilot is gone
 * (1 block/s) or once the ravager is gone with the pilot aboard (0.3 blocks/s); see SINK_*. It drifts
 * at 1 block/s in a random clear direction (re-picked every 4-10 s or when blocked) and, while it has
 * a pilot, 1 block/s toward its target until within 16 blocks of it. It turns to face its target (the
 * crossbow's, otherwise the cannon's).
 *
 * Tether: 15-21 blocks (rolled per balloon) from its bottom to the ravager's back; past that it is
 * pulled back. Can't be sheared, cut or used. Failsafe snap past 30 blocks or when the ravager has been
 * missing for 2 s. Drawn as three chains (see SiegeBalloonRenderer).
 *
 * Weapons (any direction; each needs a clear view; a 1.5 s "ready" sound before every shot or burst,
 * held until there's something to shoot at), only while the pilot is aboard:
 *   Cannon: a hand cannon round (33 to a player on Hard before armor) every 4.5 s +-1 s, within 48
 *     blocks; 2 blocks/tick, normal gravity, fixed 1.3 blast. Prefers a different target than the
 *     crossbow, and takes the crossbow's only when nothing else is in reach.
 *   Crossbow: a burst of 10 arrows, right and left barrel in turn, 3 ticks apart (4 degrees of spread;
 *     6 to a player on Hard, half to raiders, ravagers included) 2.5 s after the last burst, within 36
 *     blocks; firework launch sounds, firework spark trails. Arrows vanish 5 s after landing.
 *   Paratroopers: a 16 s countdown that only runs while one of its targets is within 36 blocks
 *     (pausing otherwise) drops two Aviators at once, one at each side (each with a random weapon, see Aviator) with 3 s
 *     of Slow Falling; 5 per balloon. In a raid they join it. Any still aboard when it dies all drop
 *     at once, with no Slow Falling (up to 3 blocks higher, stunned 2 s), into the blast, which sets everything it
 *     reaches on fire for 6 s.
 *
 * Losing health on its own (no flash or flinch; a creak each second): 5 a second once both the ravager
 * and the pilot are gone. With only one of them gone it keeps its health. At 0 health (any cause) it
 * blows up while the pilot is still aboard (it takes half), then throws the pilot out: radius 2.6, no
 * block damage, credited to nobody, scattering 36 envelope tatters and 18 burning pieces, and flinging
 * 30 pieces of shrapnel (see SHRAPNEL_*). Damage stages under 70 and under 25 health (a sound and 10
 * tatters each; flames from the second). The tether snapping plays its own sound. 120 health, 10 armor,
 * 35 XP, no drops.
 */
public class SiegeBalloon extends Raider implements CustomGunner {
    // ---- Tuning -------------------------------------------------------------------------
    public static final double MAX_HEALTH = 120.0, FOLLOW_RANGE = 32.0; // 1.0.2: was 100
    public static final int XP = 35;
    /** Built-in armor points (cuts small hits by up to ~35%, big ones by ~8%). */
    public static final double ARMOR = 10.0;
    /** Hitbox: width (both sides) and height; the pilot's feet above the balloon's bottom; gap kept between the two hitboxes. */
    public static final float WIDTH = 6.0f, HEIGHT = 4.0f;
    public static final double PILOT_FEET_Y = 0.0, PILOT_GAP = 0.01;
    /** The pilot's seat this much further forward than the hitbox edge (1.0.2; crossbow and front tether don't move). */
    public static final double PILOT_SEAT_FORWARD = 0.3;
    /**
     * Cannon muzzle: below the balloon's bottom and behind the centre. Crossbow barrels: their height
     * above the bottom (under the pilot, who sits just outside the front face).
     */
    public static final double MUZZLE_BELOW = 0.3, CANNON_BEHIND = 0.6, CROSSBOW_MUZZLE_Y = 0.7;
    /**
     * Tether chain ends (visual only), from the bottom-centre: the front one above the pilot's spot,
     * FRONT_TIE_HEIGHT up; the two rear ones REAR_TIE_HEIGHT up, REAR_TIE_SIDE to each side and
     * REAR_TIE_BACK behind the centre (1.0.1: front 2.5 -> 0.5 up; rear corners 3 -> 1.5 out, 3 -> 3.5 back).
     */
    public static final double FRONT_TIE_HEIGHT = 0.5, REAR_TIE_HEIGHT = 2.5, REAR_TIE_SIDE = 1.5, REAR_TIE_BACK = 3.5;
    /** Balloon-bottom heights above the ground for mid and low rise (1.0.2: low 3 -> 2.7, an iron golem's height, so golems can hit it). */
    public static final double MID_HEIGHT = 11.0, LOW_HEIGHT = 2.7;
    /** Rise speed, random drift and drift toward the target (blocks per tick: 0.05 = 1 block/s). */
    public static final double VERTICAL_SPEED = 0.05, DRIFT_SPEED = 0.05, TARGET_DRIFT_SPEED = 0.05;
    /**
     * Sink speeds (blocks per tick), 1.0.2. Each cause pulls toward its own height; while several pull
     * it below where it is, the fastest wins:
     *   damaged (stage 1)             -> mid rise at SINK_DAMAGED (1 block/s)
     *   burning (stage 2)             -> low rise at SINK_BURNING (1.5 blocks/s)
     *   pilot lost (tethered or not)  -> low rise at SINK_PILOT_LOST (1 block/s)
     *   ravager lost, pilot aboard    -> low rise at SINK_UNTETHERED (0.3 blocks/s)
     */
    public static final double SINK_DAMAGED = 0.05, SINK_BURNING = 0.075, SINK_PILOT_LOST = 0.05, SINK_UNTETHERED = 0.015;
    /** It stops closing in on its target this close (horizontally). */
    public static final double APPROACH_RANGE = 16.0;
    public static final int DRIFT_MIN_TICKS = 80, DRIFT_MAX_TICKS = 200;
    /**
     * Tether: free length (each balloon rolls TETHER_LENGTH +- TETHER_VARIATION in whole blocks, evenly),
     * failsafe snap length, pull per block over the length (blocks/tick), max pull.
     */
    public static final double TETHER_LENGTH = 18.0, TETHER_SNAP = 30.0, TETHER_PULL = 0.2, TETHER_MAX_PULL = 0.6;
    public static final int TETHER_VARIATION = 3;
    public static final int ANCHOR_MISSING_GRACE = 40;
    /** Cannon: range, ticks between shots (+- jitter), shell speed (m/s), shell lifetime (ticks), blast radius. */
    public static final double CANNON_RANGE = 48.0;
    public static final int CANNON_INTERVAL = 90, CANNON_JITTER = 20, SHELL_LIFETIME = 200;
    public static final float SHELL_SPEED = 40.0f, CANNON_BLAST = 1.3f;
    /** Realm Arms bullet gravity with no drop reduction (blocks/tick^2), and the hand cannon's spread (degrees). */
    private static final double SHELL_GRAVITY = 0.05;
    private static final float SHELL_SPREAD = 3.0f;
    /**
     * Crossbow: range, reload after a burst (ticks), arrows per burst (alternating right and left barrel),
     * ticks between arrows, barrel spacing (blocks), spread (degrees), arrow speed (blocks/tick).
     */
    public static final double CROSSBOW_RANGE = 36.0, BARREL_SPACING = 3.0;
    public static final int CROSSBOW_RELOAD = 60, BURST_ARROWS = 10, BURST_GAP = 3;
    public static final float ARROW_SPREAD = 4.0f, ARROW_SPEED = 3.15f;
    /** Arrow damage before difficulty (6 to a player on Hard); raiders (ravagers too) take this share. */
    public static final double ARROW_DAMAGE = 4.0, ARROW_RAIDER_DAMAGE = 0.5;
    /** The ready sound plays this long before each shot / burst. */
    public static final int READY_TICKS = 30;
    /**
     * Paratroopers: per balloon, per drop (one at each side), countdown (ticks, only while a target is in
     * range), range, Slow Falling (ticks), how far out to each side they drop. Paratroopers are always
     * Aviators (each with a random weapon, see Aviator).
     */
    public static final int PARATROOPERS = 6, PER_DROP = 2, PARATROOPER_INTERVAL = 320, SLOW_FALL_TICKS = 60;
    public static final double PARATROOPER_RANGE = 36.0, DROP_SIDE = 1.5;
    /** A target out of view (or reach) for this long is dropped; targets are looked for this often (ticks). */
    public static final int TARGET_LOST_TICKS = 60, TARGET_SEARCH_INTERVAL = 10;
    public static final float TURN_SPEED = 3.0f;
    /** Health lost each second once pilot and ravager are both gone (1.0.2: none while the pilot is aboard). */
    public static final float BLEED_AMOUNT = 5.0f;
    public static final int BLEED_INTERVAL = 20;
    /** "Mayday" every this many ticks while it loses health on its own or is at its last damage stage. */
    public static final int MAYDAY_INTERVAL = 60;
    public static final float MAYDAY_VOLUME = 3.0f;
    /** Paratroopers: the drop warning sound plays this long before a drop; the model's drop phase lasts this long after. */
    public static final int DROP_WARNING_TICKS = 40, DROP_AFTER_TICKS = 40;
    /** Model signal (balloon_signal3.rx): 0 nothing, 1 the 2 s before a drop, 2 the 2 s after. */
    public static final int DROP_PHASE_NONE = 0, DROP_PHASE_WARNING = 1, DROP_PHASE_AFTER = 2;
    /** Damage stages: below these health values (1.0.2: damaged under 70, burning under 25; were 50 / 20 or less). */
    public static final float STAGE_1_HEALTH = 70.0f, STAGE_2_HEALTH = 25.0f;
    /** Envelope tatters thrown out at each damage stage change (the death burst uses DEATH_DEBRIS). */
    public static final int STAGE_TATTERS = 10;
    /** Entity event: a damage stage change (client throws STAGE_TATTERS envelope tatters). */
    private static final byte EVENT_STAGE_TATTERS = 102;
    public static final float DEATH_BLAST_RADIUS = 2.6f;
    /** Paratroopers that bail out at its death: up to this much higher than a normal drop (random), stunned this long. */
    public static final double BAIL_OUT_EXTRA_HEIGHT = 3.0;
    public static final int BAIL_OUT_STUN_TICKS = 40;
    /**
     * Shrapnel (1.0.2): pieces flung out at its death, each in a random direction at or above the horizon,
     * at a random speed between SHRAPNEL_SPEED_MIN and _MAX (blocks per second); each does SHRAPNEL_DAMAGE
     * (flat: no difficulty scaling, credited to nobody, hits anyone) and sets what it hits on fire for
     * SHRAPNEL_BURN_SECONDS. Normal bullet gravity and drag; gone after SHRAPNEL_LIFETIME ticks. No smoke
     * or flash; vanilla flame trail; sprite textures/entity/bullet_shrapnel.png. Never breaks blocks.
     */
    public static final int SHRAPNEL_COUNT = 30, SHRAPNEL_LIFETIME = 200;
    public static final float SHRAPNEL_SPEED_MIN = 2.0f, SHRAPNEL_SPEED_MAX = 24.0f; // 1.0.3: was 10-70
    public static final float SHRAPNEL_DAMAGE = 7.0f, SHRAPNEL_BURN_SECONDS = 4.0f; // 1.0.3: damage was 4
    /** The death blast sets everything it reaches on fire for this long (never places fire). */
    public static final float DEATH_BURN_SECONDS = 6.0f;
    /** Death visuals (client): the ring's start and end size (blocks across) and how long it grows; the smoke puffs. */
    public static final int DEATH_SMOKE_PUFFS = 8;
    public static final double DEATH_SMOKE_RADIUS = 6.0; // 1.0.1: was 4
    public static final int DEATH_DEBRIS = 36, DEATH_FLAMES = 18;
    /**
     * Death burst speeds, as multipliers of the normal debris throw (SummonFx): sideways and upward, for
     * the bits and the flames (1.5.5: about half the old speed overall, thrown more steeply upward).
     */
    public static final double DEATH_DEBRIS_SIDE = 0.9, DEATH_FLAMES_SIDE = 1.2, DEATH_UP = 0.75;
    public static final int GROUND_SCAN_DEPTH = 64, GROUND_SCAN_INTERVAL = 5;
    // -------------------------------------------------------------------------------------

    public static final int RISE_HIGH = 0, RISE_MID = 1, RISE_LOW = 2;
    private static final int FLAG_PILOT_LOST = 1, FLAG_CUT_LOOSE = 2;

    private static final EntityDataAccessor<Byte> RISE = SynchedEntityData.defineId(SiegeBalloon.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> FLAGS = SynchedEntityData.defineId(SiegeBalloon.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> TETHER_ID = SynchedEntityData.defineId(SiegeBalloon.class, EntityDataSerializers.INT);
    /** This balloon's tether length (blocks). */
    private static final EntityDataAccessor<Byte> TETHER_LENGTH_DATA = SynchedEntityData.defineId(SiegeBalloon.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> CANNON_SHOTS = SynchedEntityData.defineId(SiegeBalloon.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DROP_PHASE = SynchedEntityData.defineId(SiegeBalloon.class, EntityDataSerializers.BYTE);
    /** Each weapon's current target (entity id, -1 none), for the model's aim signals. */
    private static final EntityDataAccessor<Integer> CANNON_TARGET_ID = SynchedEntityData.defineId(SiegeBalloon.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CROSSBOW_TARGET_ID = SynchedEntityData.defineId(SiegeBalloon.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CROSSBOW_BURSTS = SynchedEntityData.defineId(SiegeBalloon.class, EntityDataSerializers.INT);

    private @Nullable EntityReference<Mob> anchor;
    private int anchorMissingTicks;
    private double groundY = Double.NaN;
    private float driftYaw;
    private int driftTimer;
    /** Server: each weapon's target, how long it has been out of reach, and its countdown. */
    private @Nullable LivingEntity cannonTarget, crossbowTarget;
    private int cannonBadTicks, crossbowBadTicks;
    private int cannonTimer = READY_TICKS + 40, crossbowTimer = READY_TICKS + 20;
    private int burstLeft, burstGap;
    private int paratrooperTimer = PARATROOPER_INTERVAL, paratroopersDropped;
    private int lastStage;
    /** Server: ticks left of the after-drop model phase; ticks to the next mayday. */
    private int dropAfterTicks;
    private int maydayTimer;
    /** Its raid at the moment it dies (for the paratroopers it drops then). */
    private @Nullable Raid dyingRaid;
    private int clientCannonTick = Integer.MIN_VALUE, clientCrossbowTick = Integer.MIN_VALUE;

    public SiegeBalloon(EntityType<? extends SiegeBalloon> type, Level level) {
        super(type, level);
        setNoGravity(true);
        this.xpReward = XP;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, ARMOR);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(RISE, (byte) RISE_HIGH);
        builder.define(FLAGS, (byte) 0);
        builder.define(TETHER_ID, -1);
        builder.define(TETHER_LENGTH_DATA, (byte) TETHER_LENGTH);
        builder.define(CANNON_SHOTS, 0);
        builder.define(DROP_PHASE, (byte) DROP_PHASE_NONE);
        builder.define(CANNON_TARGET_ID, -1);
        builder.define(CROSSBOW_TARGET_ID, -1);
        builder.define(CROSSBOW_BURSTS, 0);
    }

    /** No goals: it doesn't walk, patrol, path to the raid or pick anything up. */
    @Override
    protected void registerGoals() {
    }

    // ---- The package ----------------------------------------------------------------------

    /** Brings its own ravager (tethered) and pilot (aboard); in a raid, both join the same wave. */
    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                  EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
        setItemSlot(EquipmentSlot.MAINHAND, RaidIllager.realmArms("hand_cannon"));
        setDropChance(EquipmentSlot.MAINHAND, 0.0f);
        driftYaw = getRandom().nextFloat() * 360f;
        entityData.set(TETHER_LENGTH_DATA, (byte) (TETHER_LENGTH - TETHER_VARIATION + getRandom().nextInt(TETHER_VARIATION * 2 + 1)));
        driftTimer = DRIFT_MIN_TICKS + getRandom().nextInt(DRIFT_MAX_TICKS - DRIFT_MIN_TICKS + 1);

        ServerLevel serverLevel = level.getLevel();
        double groundX = getX(), groundYPos = getY(), groundZ = getZ();
        Ravager ravager = EntityType.RAVAGER.create(serverLevel, reason);
        Aviator pilot = RaidEntities.AVIATOR.create(serverLevel, reason);
        if (ravager == null || pilot == null) {
            return data;
        }
        ravager.snapTo(groundX, groundYPos, groundZ, getYRot(), 0.0f);
        ravager.finalizeSpawn(level, difficulty, reason, null);
        // Start a block above the spot it was spawned on (it rises from there).
        setPos(groundX, groundYPos + 1.0, groundZ);
        pilot.snapTo(groundX, groundYPos + 1.0 + PILOT_FEET_Y, groundZ, getYRot(), 0.0f);
        pilot.finalizeSpawn(level, difficulty, reason, null);
        pilot.makeBalloonPilot();
        pilot.startRiding(this, true, false);

        Raid raid = getCurrentRaid();
        if (raid != null) {
            raid.joinRaid(serverLevel, getWave(), ravager, null, true);
            raid.joinRaid(serverLevel, getWave(), pilot, null, true);
        } else {
            // Outside a raid: keep the package together (no despawning one part of it).
            setPersistenceRequired();
            ravager.setPersistenceRequired();
            pilot.setPersistenceRequired();
        }
        level.addFreshEntity(ravager);
        anchor = EntityReference.of(ravager);
        entityData.set(TETHER_ID, ravager.getId());
        return data;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof Aviator && getPassengers().isEmpty();
    }

    /**
     * How far out (from the centre line) the pilot stands at this heading: just outside the balloon's
     * hitbox, which never turns, so further out at diagonals (up to x1.41 at 45 degrees).
     */
    public static double pilotDistance(float yawDegrees, double pilotHalfWidth) {
        double rad = yawDegrees * Mth.DEG_TO_RAD;
        double edge = Math.max(Math.abs(Math.sin(rad)), Math.abs(Math.cos(rad)));
        return (WIDTH / 2.0 + pilotHalfWidth + PILOT_GAP) / edge;
    }

    /** Distance at a straight-on heading (where the model shows the pilot). */
    /** This balloon's tether length (blocks; TETHER_LENGTH +- TETHER_VARIATION). */
    public double tetherLength() {
        return entityData.get(TETHER_LENGTH_DATA);
    }

    public static double pilotDistanceStraight(double pilotHalfWidth) {
        return WIDTH / 2.0 + pilotHalfWidth + PILOT_GAP;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, net.minecraft.world.entity.EntityDimensions dimensions, float scale) {
        double out = pilotDistance(getYRot(), passenger.getBbWidth() / 2.0) + PILOT_SEAT_FORWARD;
        return new Vec3(0.0, PILOT_FEET_Y, out).yRot(-getYRot() * Mth.DEG_TO_RAD);
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return null;
    }

    @Override
    public boolean dismountsUnderwater() {
        return false;
    }

    /** A pilot thrown out leaves from where it stands (and falls). */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return passenger.position();
    }

    public @Nullable Aviator pilot() {
        return getFirstPassenger() instanceof Aviator pilot && pilot.isAlive() ? pilot : null;
    }

    public @Nullable Mob anchorEntity() {
        return anchor == null ? null : EntityReference.get(anchor, level(), Mob.class);
    }

    // ---- Synced state (model signals, tether drawing) --------------------------------------

    public int riseState() {
        return entityData.get(RISE);
    }

    public boolean pilotLost() {
        return (entityData.get(FLAGS) & FLAG_PILOT_LOST) != 0;
    }

    public boolean cutLoose() {
        return (entityData.get(FLAGS) & FLAG_CUT_LOOSE) != 0;
    }

    private void setFlag(int flag, boolean on) {
        int flags = entityData.get(FLAGS);
        entityData.set(FLAGS, (byte) (on ? flags | flag : flags & ~flag));
    }

    /** 0 undamaged, 1 under 70 health, 2 under 25 health (works on both sides: health is synced). */
    public int damageStage() {
        float health = getHealth();
        return health < STAGE_2_HEALTH ? 2 : health < STAGE_1_HEALTH ? 1 : 0;
    }

    /** Paratrooper drop phase for the model (see DROP_PHASE_*). */
    public int dropPhase() {
        return entityData.get(DROP_PHASE);
    }

    private void setDropPhase(int phase) {
        if (entityData.get(DROP_PHASE) != phase) {
            entityData.set(DROP_PHASE, (byte) phase);
        }
    }

    public @Nullable Entity tetherEntity() {
        int id = entityData.get(TETHER_ID);
        return id < 0 ? null : level().getEntity(id);
    }

    private float secondsSince(int clientTick, float partialTick) {
        if (clientTick == Integer.MIN_VALUE) {
            return AnimSignalled.SHOT_TIMER_CAP;
        }
        return Math.min(AnimSignalled.SHOT_TIMER_CAP, (tickCount - clientTick + partialTick) / 20f);
    }

    public float secondsSinceCannon(float partialTick) {
        return secondsSince(clientCannonTick, partialTick);
    }

    public float secondsSinceCrossbow(float partialTick) {
        return secondsSince(clientCrossbowTick, partialTick);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (level().isClientSide() && tickCount > 0) {
            if (CANNON_SHOTS.equals(key)) {
                clientCannonTick = tickCount;
            } else if (CROSSBOW_BURSTS.equals(key)) {
                clientCrossbowTick = tickCount;
            }
        }
    }

    // ---- Muzzles and tether ----------------------------------------------------------------

    private Vec3 local(double right, double up, double forward) {
        return position().add(new Vec3(right, up, forward).yRot(-getYRot() * Mth.DEG_TO_RAD));
    }

    private Vec3 local(Vec3 rightUpForward) {
        return local(rightUpForward.x, rightUpForward.y, rightUpForward.z);
    }

    /** Muzzles in the balloon's frame (right, up, forward) from its bottom-centre, at this heading (both sides). */
    public static Vec3 cannonMuzzleLocal(float yawDegrees) {
        return new Vec3(0.0, -MUZZLE_BELOW, -CANNON_BEHIND);
    }

    /** Midway between the two crossbow barrels. */
    public static Vec3 crossbowMuzzleLocal(float yawDegrees) {
        return new Vec3(0.0, CROSSBOW_MUZZLE_Y, pilotDistance(yawDegrees, 0.3));
    }

    /** Client: each weapon's current target (null when it has none). */
    public @Nullable Entity cannonTargetEntity() {
        int id = entityData.get(CANNON_TARGET_ID);
        return id < 0 ? null : level().getEntity(id);
    }

    public @Nullable Entity crossbowTargetEntity() {
        int id = entityData.get(CROSSBOW_TARGET_ID);
        return id < 0 ? null : level().getEntity(id);
    }

    /** Server: sends the weapons' targets to the client (only when they change). */
    private void syncTargets() {
        int cannon = cannonTarget != null && !pilotLost() ? cannonTarget.getId() : -1;
        int crossbow = crossbowTarget != null && !pilotLost() ? crossbowTarget.getId() : -1;
        if (entityData.get(CANNON_TARGET_ID) != cannon) {
            entityData.set(CANNON_TARGET_ID, cannon);
        }
        if (entityData.get(CROSSBOW_TARGET_ID) != crossbow) {
            entityData.set(CROSSBOW_TARGET_ID, crossbow);
        }
    }

    /** Under the rear. */
    public Vec3 cannonMuzzle() {
        return local(cannonMuzzleLocal(getYRot()));
    }

    /** Under the pilot (between the two barrels: used for reach and view checks). */
    public Vec3 crossbowMuzzle() {
        return crossbowBarrel(0);
    }

    /** -1 left barrel, 1 right barrel, 0 between them; BARREL_SPACING apart under the pilot. */
    public Vec3 crossbowBarrel(int side) {
        return local(-side * BARREL_SPACING / 2.0, CROSSBOW_MUZZLE_Y, pilotDistance(getYRot(), 0.3));
    }

    /** Where the tether is tied on the ravager (its back). */
    public static Vec3 anchorPoint(Entity anchor, float partialTick) {
        return anchor.getPosition(partialTick).add(0.0, anchor.getBbHeight(), 0.0);
    }

    // ---- Server AI ------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!isAlive()) {
            return;
        }
        Aviator pilot = pilot();
        if (pilot == null && !pilotLost()) {
            setFlag(FLAG_PILOT_LOST, true); // dead or off the balloon: for good
            cannonTarget = crossbowTarget = null;
            burstLeft = 0;
        }
        if (pilot != null) {
            updateTargets(level);
        }
        syncTargets();
        LivingEntity main = crossbowTarget != null ? crossbowTarget : cannonTarget;
        setTarget(main);
        setAggressive(main != null);

        updateTether(level);
        if (tickCount % GROUND_SCAN_INTERVAL == 0 || Double.isNaN(groundY)) {
            groundY = scanGround(level);
        }
        updateRiseState();
        turnToward(main);
        moveBalloon(level, main);

        if (pilot != null) {
            tickCannon(level, pilot);
            tickCrossbow(level, pilot);
            tickParatroopers(level);
        } else if (dropPhase() == DROP_PHASE_WARNING) {
            setDropPhase(DROP_PHASE_NONE); // pilot lost mid-warning: no drop comes
        }
        if (dropAfterTicks > 0 && --dropAfterTicks == 0 && dropPhase() == DROP_PHASE_AFTER) {
            setDropPhase(DROP_PHASE_NONE);
        }
        if (cutLoose() || damageStage() >= 2) {
            if (--maydayTimer <= 0) {
                playSound(RaidSounds.SIEGE_BALLOON_MAYDAY, MAYDAY_VOLUME, 1.0f);
                maydayTimer = MAYDAY_INTERVAL;
            }
        } else {
            maydayTimer = 0; // the first mayday comes at once
        }
        if (cutLoose() && pilot == null && tickCount % BLEED_INTERVAL == 0) {
            bleed(level, BLEED_AMOUNT);
        }
        int stage = damageStage();
        if (stage > lastStage) {
            playSound(stage == 2 ? RaidSounds.SIEGE_BALLOON_STAGE_2 : RaidSounds.SIEGE_BALLOON_STAGE_1, 2.0f, 1.0f);
            level.broadcastEntityEvent(this, EVENT_STAGE_TATTERS); // envelope tatters (client)
        }
        lastStage = stage;
    }

    /**
     * Once its ravager and its pilot are both gone: 5 health a second (no flash or flinch; a creak
     * each time). Blows up at 0, credited to nobody.
     */
    private void bleed(ServerLevel level, float amount) {
        if (getHealth() > amount) {
            setHealth(getHealth() - amount);
            playSound(RaidSounds.SIEGE_BALLOON_BLEED, 1.5f, 0.9f + getRandom().nextFloat() * 0.2f);
        } else {
            hurtServer(level, damageSources().genericKill(), Float.MAX_VALUE);
        }
    }

    // ---- Targets --------------------------------------------------------------------------

    private static boolean isCandidate(LivingEntity entity) {
        if (entity instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        return entity instanceof AbstractVillager || entity instanceof IronGolem || RaidIllager.isGuard(entity);
    }

    private static boolean usable(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved()
                && !(target instanceof Player player && (player.isCreative() || player.isSpectator()));
    }

    private boolean inReach(LivingEntity target, Vec3 muzzle, double range) {
        return target.level() == level() && target.position().add(0.0, target.getBbHeight() * 0.5, 0.0).distanceToSqr(muzzle) <= range * range;
    }

    private boolean inView(LivingEntity target, Vec3 muzzle) {
        HitResult hit = level().clip(new ClipContext(muzzle, target.getEyePosition(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS;
    }

    private boolean canFire(@Nullable LivingEntity target, Vec3 muzzle, double range) {
        return usable(target) && inReach(target, muzzle, range) && inView(target, muzzle);
    }

    /**
     * Each weapon keeps its target while it can still shoot it (dropped after 3 s out of reach or
     * view). Every 0.5 s one shared list of nearby candidates (nearest first) refills them: the crossbow
     * takes the nearest it can shoot; the cannon the nearest other one, sharing the crossbow's only
     * when nothing else is in reach (and moving off it as soon as something else is).
     */
    private void updateTargets(ServerLevel level) {
        boolean recheck = (tickCount + getId()) % 5 == 0;
        if (!usable(crossbowTarget)) {
            crossbowTarget = null;
        } else if (recheck) {
            crossbowBadTicks = canFire(crossbowTarget, crossbowMuzzle(), CROSSBOW_RANGE) ? 0 : crossbowBadTicks + 5;
            if (crossbowBadTicks > TARGET_LOST_TICKS) {
                crossbowTarget = null;
            }
        }
        if (!usable(cannonTarget)) {
            cannonTarget = null;
        } else if (recheck) {
            cannonBadTicks = canFire(cannonTarget, cannonMuzzle(), CANNON_RANGE) ? 0 : cannonBadTicks + 5;
            if (cannonBadTicks > TARGET_LOST_TICKS) {
                cannonTarget = null;
            }
        }
        boolean cannonShares = cannonTarget != null && cannonTarget == crossbowTarget;
        if ((tickCount + getId()) % TARGET_SEARCH_INTERVAL != 0 || (crossbowTarget != null && cannonTarget != null && !cannonShares)) {
            return;
        }
        Vec3 centre = position().add(0.0, HEIGHT / 2.0, 0.0);
        List<LivingEntity> candidates = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(CANNON_RANGE + 2.0), entity -> entity != this && isCandidate(entity) && usable(entity)));
        if (candidates.isEmpty()) {
            return;
        }
        candidates.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(centre)));
        if (crossbowTarget == null) {
            Vec3 muzzle = crossbowMuzzle();
            for (LivingEntity entity : candidates) {
                if (inReach(entity, muzzle, CROSSBOW_RANGE) && inView(entity, muzzle)) {
                    crossbowTarget = entity;
                    crossbowBadTicks = 0;
                    break;
                }
            }
        }
        if (cannonTarget == null || cannonTarget == crossbowTarget) {
            Vec3 muzzle = cannonMuzzle();
            LivingEntity other = null;
            for (LivingEntity entity : candidates) {
                if (entity != crossbowTarget && inReach(entity, muzzle, CANNON_RANGE) && inView(entity, muzzle)) {
                    other = entity;
                    break;
                }
            }
            if (other != null) {
                cannonTarget = other;
                cannonBadTicks = 0;
            } else if (cannonTarget == null && crossbowTarget != null && canFire(crossbowTarget, muzzle, CANNON_RANGE)) {
                cannonTarget = crossbowTarget;
                cannonBadTicks = 0;
            }
        }
    }

    /** Whatever hurts it becomes a weapon's target if that weapon has none. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive() && pilot() != null && source.getEntity() instanceof LivingEntity attacker
                && attacker != this && !isAlliedTo(attacker) && !(attacker instanceof Raider) && usable(attacker)) {
            if (crossbowTarget == null && inReach(attacker, crossbowMuzzle(), CROSSBOW_RANGE)) {
                crossbowTarget = attacker;
                crossbowBadTicks = 0;
            } else if (cannonTarget == null && inReach(attacker, cannonMuzzle(), CANNON_RANGE)) {
                cannonTarget = attacker;
                cannonBadTicks = 0;
            }
        }
        return hurt;
    }

    // ---- Height, tether, drift ------------------------------------------------------------

    /**
     * High rise by default; mid rise once damaged; low rise once burning, once the pilot is gone, or
     * once the ravager is gone (see the SINK_* speeds for how fast each gets it there).
     */
    private void updateRiseState() {
        int state;
        if (pilotLost() || cutLoose() || damageStage() >= 2) {
            state = RISE_LOW;
        } else if (damageStage() >= 1) {
            state = RISE_MID;
        } else {
            state = RISE_HIGH;
        }
        if (state != riseState()) {
            entityData.set(RISE, (byte) state);
            playSound(RaidSounds.SIEGE_BALLOON_RISE_CHANGE, 2.0f, 1.0f);
        }
    }

    private double horizontalDistanceTo(Entity entity) {
        double dx = entity.getX() - getX(), dz = entity.getZ() - getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Top of the first solid block (or liquid) straight below the balloon. */
    private double scanGround(ServerLevel level) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(getBlockX(), Mth.floor(getY()) - 1, getBlockZ());
        int minY = Math.max(level.getMinY(), pos.getY() - GROUND_SCAN_DEPTH);
        for (; pos.getY() >= minY; pos.move(0, -1, 0)) {
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) {
                return pos.getY() + 1.0;
            }
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                return pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
            }
        }
        return minY;
    }

    private void updateTether(ServerLevel level) {
        if (anchor == null) {
            entityData.set(TETHER_ID, -1);
            return;
        }
        Mob ravager = EntityReference.get(anchor, level, Mob.class);
        if (ravager == null || !ravager.isAlive()) {
            if (ravager != null || ++anchorMissingTicks > ANCHOR_MISSING_GRACE) {
                cutTether();
            }
            return;
        }
        anchorMissingTicks = 0;
        if (entityData.get(TETHER_ID) != ravager.getId()) {
            entityData.set(TETHER_ID, ravager.getId());
        }
        if (position().distanceTo(anchorPoint(ravager, 1f)) > TETHER_SNAP) {
            cutTether(); // failsafe
        }
    }

    private void cutTether() {
        if (!cutLoose()) {
            playSound(RaidSounds.SIEGE_BALLOON_UNTETHER, 2.0f, 1.0f); // 1.0.2 (default: a lead breaking)
        }
        anchor = null;
        entityData.set(TETHER_ID, -1);
        setFlag(FLAG_CUT_LOOSE, true);
    }

    private void moveBalloon(ServerLevel level, @Nullable LivingEntity main) {
        double ground = Double.isNaN(groundY) ? getY() : groundY;
        double vy;
        Vec3 drift = Vec3.ZERO;
        int state = riseState();
        if (state == RISE_HIGH && !cutLoose()) {
            vy = VERTICAL_SPEED; // keeps rising: the tether holds it
        } else {
            double height = state == RISE_LOW ? LOW_HEIGHT : MID_HEIGHT;
            double gap = ground + height - getY();
            vy = gap >= 0.0 ? Math.min(gap, VERTICAL_SPEED) : Math.max(gap, -sinkSpeed(ground));
        }
        // Random drift always; drift toward the target while it has a pilot (tethered or not).
        if (--driftTimer <= 0 || horizontalCollision) {
            pickDrift(level);
        }
        drift = Vec3.directionFromRotation(0.0f, driftYaw).scale(DRIFT_SPEED);
        if (main != null && pilot() != null && horizontalDistanceTo(main) > APPROACH_RANGE) {
            Vec3 toward = new Vec3(main.getX() - getX(), 0.0, main.getZ() - getZ()).normalize();
            drift = drift.add(toward.scale(TARGET_DRIFT_SPEED));
        }
        Vec3 pull = Vec3.ZERO;
        Mob ravager = anchorEntity();
        if (ravager != null && !cutLoose()) {
            Vec3 toAnchor = anchorPoint(ravager, 1f).subtract(position());
            double dist = toAnchor.length();
            if (dist > tetherLength()) {
                pull = toAnchor.scale(Math.min(TETHER_MAX_PULL, (dist - tetherLength()) * TETHER_PULL) / dist);
            }
        }
        Vec3 velocity = new Vec3(drift.x + pull.x, vy + pull.y, drift.z + pull.z);
        if (getY() + velocity.y > level.getMaxY() - HEIGHT) {
            velocity = new Vec3(velocity.x, Math.min(0.0, velocity.y), velocity.z);
        }
        setDeltaMovement(velocity);
        move(MoverType.SELF, velocity);
    }

    /** The fastest sink among the causes still pulling it below its current height (blocks per tick). */
    private double sinkSpeed(double ground) {
        double above = getY() - ground;
        double speed = 0.0;
        if (damageStage() >= 1 && above > MID_HEIGHT) {
            speed = Math.max(speed, SINK_DAMAGED);
        }
        if (above > LOW_HEIGHT) {
            if (damageStage() >= 2) {
                speed = Math.max(speed, SINK_BURNING);
            }
            if (pilotLost()) {
                speed = Math.max(speed, SINK_PILOT_LOST);
            }
            if (cutLoose()) {
                speed = Math.max(speed, SINK_UNTETHERED);
            }
        }
        return speed > 0.0 ? speed : VERTICAL_SPEED;
    }

    /** A random direction with room for the balloon a few blocks ahead. */
    private void pickDrift(ServerLevel level) {
        driftTimer = DRIFT_MIN_TICKS + getRandom().nextInt(DRIFT_MAX_TICKS - DRIFT_MIN_TICKS + 1);
        AABB box = getBoundingBox();
        for (int attempt = 0; attempt < 8; ++attempt) {
            float yaw = getRandom().nextFloat() * 360f;
            if (level.noCollision(this, box.move(Vec3.directionFromRotation(0.0f, yaw).scale(3.0)))) {
                driftYaw = yaw;
                return;
            }
        }
        driftYaw = getRandom().nextFloat() * 360f;
    }

    private void turnToward(@Nullable LivingEntity target) {
        if (target == null) {
            setXRot(Mth.approach(getXRot(), 0.0f, TURN_SPEED));
        } else {
            double dx = target.getX() - getX(), dz = target.getZ() - getZ();
            double dy = target.getY(0.5) - getY();
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
            float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
            setYRot(Mth.approachDegrees(getYRot(), yaw, TURN_SPEED));
            setXRot(Mth.approach(getXRot(), pitch, TURN_SPEED * 2f));
        }
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    @Override
    public void travel(Vec3 input) {
    }

    // ---- Weapons --------------------------------------------------------------------------

    /**
     * Shared countdown: runs down to the ready point, waits there for something to shoot at, plays
     * the ready sound, then runs out; at 0 it fires once the weapon can (true = fire now).
     */
    private boolean countdown(int timer, boolean hasTarget, SoundEvent ready, IntSetter set) {
        if (timer > READY_TICKS) {
            set.set(timer - 1);
        } else if (timer == READY_TICKS) {
            if (hasTarget) {
                playSound(ready, 2.5f, 1.0f);
                set.set(timer - 1);
            }
        } else if (timer > 0) {
            set.set(timer - 1);
        } else {
            return true;
        }
        return false;
    }

    private interface IntSetter {
        void set(int value);
    }

    private void tickCannon(ServerLevel level, Aviator pilot) {
        LivingEntity target = cannonTarget;
        Vec3 muzzle = cannonMuzzle();
        boolean reachable = usable(target) && inReach(target, muzzle, CANNON_RANGE);
        if (countdown(cannonTimer, reachable, RaidSounds.SIEGE_BALLOON_CANNON_READY, v -> cannonTimer = v)
                && reachable && inView(target, muzzle)) {
            fireCannon(target, muzzle, pilot);
            cannonTimer = CANNON_INTERVAL - CANNON_JITTER + getRandom().nextInt(CANNON_JITTER * 2 + 1);
        }
    }

    private void fireCannon(LivingEntity target, Vec3 muzzle, Aviator pilot) {
        ItemStack gun = getMainHandItem();
        if (!(gun.getItem() instanceof GunItem)) {
            gun = RaidIllager.realmArms("hand_cannon");
            if (!(gun.getItem() instanceof GunItem)) {
                return;
            }
            setItemSlot(EquipmentSlot.MAINHAND, gun);
        }
        GunItem item = (GunItem) gun.getItem();
        Vec3 aim = new Vec3(target.getX(), target.getY(0.5), target.getZ());
        Vec3 direction = Ballistics.solve(muzzle, aim, SHELL_SPEED / 20.0, SHELL_GRAVITY, false);
        if (direction == null) {
            direction = aim.subtract(muzzle).normalize();
        }
        pendingMuzzle = muzzle;
        pendingDirection = direction;
        GunItem.setLoaded(gun, true);
        // Muzzle smoke at the muzzle (the gun works from the eye, at the hitbox centre).
        item.mobUse(this, gun, direction, muzzle.subtract(getEyePosition()));
        pendingMuzzle = null;
        pendingDirection = null;
        entityData.set(CANNON_SHOTS, entityData.get(CANNON_SHOTS) + 1);
        pilot.markShot();
    }

    private @Nullable Vec3 pendingMuzzle, pendingDirection;

    /** Cannon rounds: from the muzzle, at 2 blocks/tick, normal gravity, a fixed CANNON_BLAST blast. */
    @Override
    public void customizeBullet(RealmBulletEntity bullet, ItemStack gun) {
        if (pendingMuzzle == null || pendingDirection == null) {
            return;
        }
        Vec3 spread = GunItem.addSpread(pendingDirection, getRandom(), SHELL_SPREAD);
        bullet.setPos(pendingMuzzle);
        bullet.firedFrom = pendingMuzzle;
        bullet.setDropReduction(0f);
        bullet.setVelocity(SHELL_SPEED, spread);
        bullet.setLifetimeTicks(SHELL_LIFETIME);
        bullet.gunType = null; // fixed blast instead of the hand cannon's distance scaling
        bullet.explosionRadius = CANNON_BLAST;
    }

    private void tickCrossbow(ServerLevel level, Aviator pilot) {
        LivingEntity target = crossbowTarget;
        Vec3 muzzle = crossbowMuzzle();
        if (burstLeft > 0) {
            if (--burstGap <= 0) {
                if (usable(target) && inReach(target, muzzle, CROSSBOW_RANGE)) {
                    fireArrow(level, target, crossbowBarrel(barrelFor(BURST_ARROWS - burstLeft)));
                }
                --burstLeft;
                burstGap = BURST_GAP;
            }
            return;
        }
        boolean reachable = usable(target) && inReach(target, muzzle, CROSSBOW_RANGE);
        if (countdown(crossbowTimer, reachable, RaidSounds.SIEGE_BALLOON_CROSSBOW_READY, v -> crossbowTimer = v)
                && reachable && inView(target, muzzle)) {
            burstLeft = BURST_ARROWS;
            burstGap = 0;
            crossbowTimer = CROSSBOW_RELOAD + BURST_ARROWS * BURST_GAP;
            entityData.set(CROSSBOW_BURSTS, entityData.get(CROSSBOW_BURSTS) + 1);
            pilot.markShot();
            // The first arrow (right barrel) goes this tick.
            fireArrow(level, target, crossbowBarrel(barrelFor(0)));
            --burstLeft;
            burstGap = BURST_GAP;
        }
    }

    /** Arrows alternate right, left, right... */
    private static int barrelFor(int arrowIndex) {
        return arrowIndex % 2 == 0 ? 1 : -1;
    }

    private void fireArrow(ServerLevel level, LivingEntity target, Vec3 muzzle) {
        BalloonArrow arrow = new BalloonArrow(RaidEntities.BALLOON_ARROW, level);
        arrow.setOwner(this);
        arrow.setPos(muzzle);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.setBaseDamage(ARROW_DAMAGE / ARROW_SPEED);
        arrow.setCritArrow(false);
        Vec3 aim = new Vec3(target.getX(), target.getY(0.5), target.getZ());
        Vec3 direction = Ballistics.solve(muzzle, aim, ARROW_SPEED, 0.05, false);
        if (direction == null) {
            direction = aim.subtract(muzzle).normalize();
        }
        direction = GunItem.addSpread(direction, getRandom(), ARROW_SPREAD);
        arrow.shoot(direction.x, direction.y, direction.z, ARROW_SPEED, 0.0f);
        level.addFreshEntity(arrow);
        // The pistol's muzzle flash at the barrel (1.0.3; was the volleygun's), carried along with the balloon's motion.
        Vec3 motion = getDeltaMovement();
        level.sendParticles(net.gunmetal.arms.registry.RealmParticles.FLASH_LIGHT, muzzle.x, muzzle.y, muzzle.z, 0,
                motion.x, motion.y, motion.z, 1.0);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, RaidSounds.SIEGE_BALLOON_CROSSBOW_FIRE, getSoundSource(), 2.5f,
                0.9f + getRandom().nextFloat() * 0.2f);
    }

    // ---- Paratroopers -----------------------------------------------------------------------

    private void tickParatroopers(ServerLevel level) {
        if (paratroopersDropped >= PARATROOPERS) {
            return;
        }
        Vec3 drop = position();
        boolean inRange = (usable(crossbowTarget) && crossbowTarget.position().distanceTo(drop) <= PARATROOPER_RANGE)
                || (usable(cannonTarget) && cannonTarget.position().distanceTo(drop) <= PARATROOPER_RANGE);
        // Countdown: pauses out of range; holds at DROP_WARNING_TICKS until a target is in range, then
        // plays the drop warning and runs out (out of range or not) into the drop.
        if (paratrooperTimer > DROP_WARNING_TICKS) {
            if (inRange) {
                --paratrooperTimer;
            }
            return;
        }
        if (paratrooperTimer == DROP_WARNING_TICKS) {
            if (inRange) {
                playSound(RaidSounds.SIEGE_BALLOON_DROP_WARNING, 2.5f, 1.0f);
                setDropPhase(DROP_PHASE_WARNING);
                --paratrooperTimer;
            }
            return;
        }
        if (--paratrooperTimer > 0) {
            return;
        }
        int dropped = 0;
        for (int i = 0; i < PER_DROP && paratroopersDropped < PARATROOPERS; ++i) {
            if (dropParatrooper(level, i % 2 == 0 ? 1 : -1, 0.0, false)) {
                ++paratroopersDropped;
                ++dropped;
            }
        }
        if (dropped > 0) {
            playSound(RaidSounds.SIEGE_BALLOON_DEPLOY, 2.5f, 1.0f);
            paratrooperTimer = PARATROOPER_INTERVAL;
            setDropPhase(DROP_PHASE_AFTER);
            dropAfterTicks = DROP_AFTER_TICKS;
        } else {
            paratrooperTimer = 10; // no room beside it: try again shortly
        }
    }

    /** One raider beside the balloon (side -1 left / 1 right, shifted along its length), just under its bottom. */
    /** bailOut: at its death (no Slow Falling, a random 0-3 blocks higher, stunned 2 s); otherwise a normal drop. */
    private boolean dropParatrooper(ServerLevel level, int side, double along, boolean bailOut) {
        Aviator mob = RaidEntities.AVIATOR.create(level, EntitySpawnReason.EVENT);
        if (mob == null) {
            return false;
        }
        Vec3 spot = local(-side * DROP_SIDE, 0.0, along);
        double lift = bailOut ? getRandom().nextDouble() * BAIL_OUT_EXTRA_HEIGHT : 0.0;
        mob.snapTo(spot.x, getY() - mob.getBbHeight() - 0.1 + lift, spot.z, getYRot(), 0.0f);
        if (!level.noCollision(mob)) {
            mob.discard();
            return false;
        }
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.EVENT, null);
        Raid raid = getCurrentRaid() != null ? getCurrentRaid() : dyingRaid;
        if (raid != null && mob instanceof Raider raider) {
            raid.joinRaid(level, getWave(), raider, null, true);
        }
        if (bailOut) {
            mob.addEffect(Artificer.stunEffect(BAIL_OUT_STUN_TICKS)); // alexscaves:stunned I (Slowness IV without it)
        } else {
            mob.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL_TICKS, 0));
        }
        LivingEntity target = crossbowTarget != null ? crossbowTarget : cannonTarget;
        if (target != null) {
            mob.setTarget(target);
        }
        level.addFreshEntity(mob);
        return true;
    }

    // ---- Client effects -----------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() && isAlive() && damageStage() >= 2 && getRandom().nextBoolean()) {
            RandomSource random = getRandom();
            AABB box = getBoundingBox();
            level().addParticle(ParticleTypes.FLAME, box.minX + random.nextDouble() * WIDTH, box.minY + random.nextDouble() * HEIGHT,
                    box.minZ + random.nextDouble() * WIDTH, 0.0, 0.02, 0.0);
        }
    }

    /** Death (3): the burst of envelope tatters and burning pieces. A damage stage change: a few tatters. */
    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_STAGE_TATTERS) {
            SummonFx.spawnDebrisBurst(this, RaidParticles.ENVELOPE_TATTERS, STAGE_TATTERS, DEATH_DEBRIS_SIDE, DEATH_UP);
            return;
        }
        if (id == 3) {
            SummonFx.spawnDebrisBurst(this, RaidParticles.ENVELOPE_TATTERS, DEATH_DEBRIS, DEATH_DEBRIS_SIDE, DEATH_UP);
            SummonFx.spawnDebrisBurst(this, RaidParticles.BURNING_DEBRIS, DEATH_FLAMES, DEATH_FLAMES_SIDE, DEATH_UP);
            // The blast ring (faces the camera, grows 6 -> 18 blocks in 0.5 s) and the smoke left hanging.
            double cx = getX(), cy = getY() + HEIGHT / 2.0, cz = getZ();
            level().addParticle(RaidParticles.BALLOON_DEATH_RING, true, true, cx, cy, cz, 0.0, 0.0, 0.0);
            RandomSource random = getRandom();
            for (int i = 0; i < DEATH_SMOKE_PUFFS; ++i) {
                Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
                dir = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : dir.normalize();
                double r = DEATH_SMOKE_RADIUS * Math.cbrt(random.nextDouble()); // even through the ball
                level().addParticle(RaidParticles.BALLOON_DEATH_SMOKE, true, true,
                        cx + dir.x * r, cy + dir.y * r, cz + dir.z * r, 0.0, 0.0, 0.0);
            }
        }
        super.handleEntityEvent(id);
    }

    // ---- Death ------------------------------------------------------------------------------

    /** Blows up (radius 6, no block damage, credited to nobody) and drops its pilot. */
    @Override
    public void die(DamageSource source) {
        boolean first = !dead && !isRemoved();
        dyingRaid = getCurrentRaid(); // leaving the raid on death clears it; its last paratroopers still join
        super.die(source);
        if (first && level() instanceof ServerLevel level) {
            // Paratroopers it still carried all bail out at once (no Slow Falling), into the blast.
            for (int i = 0; paratroopersDropped < PARATROOPERS && i < PARATROOPERS * 2; ++i) {
                double along = (i / 2) * 1.5 - 1.5;
                if (dropParatrooper(level, i % 2 == 0 ? 1 : -1, along, true)) {
                    ++paratroopersDropped;
                }
            }
            // The blast while the pilot is still aboard (it takes half), then it's thrown out. Everything it
            // reaches (in range and in view of the centre, as the blast works it out) also catches fire.
            Vec3 centre = new Vec3(getX(), getY() + HEIGHT / 2.0, getZ());
            double reach = DEATH_BLAST_RADIUS * 2.0;
            List<Entity> burning = level.getEntities(this, new AABB(centre, centre).inflate(reach),
                    e -> e.isAlive() && !e.isSpectator() && Math.sqrt(e.distanceToSqr(centre)) <= reach
                            && net.minecraft.world.level.ServerExplosion.getSeenPercent(centre, e) > 0f);
            level.explode(this, damageSources().explosion(this, null), new ExplosionDamageCalculator(),
                    centre.x, centre.y, centre.z, DEATH_BLAST_RADIUS, false, Level.ExplosionInteraction.NONE);
            for (Entity entity : burning) {
                if (entity.isAlive() && entity instanceof LivingEntity) {
                    entity.igniteForSeconds(DEATH_BURN_SECONDS);
                }
            }
            throwShrapnel(level, centre);
            ejectPassengers();
            anchor = null;
            entityData.set(TETHER_ID, -1);
        }
    }

    /** SHRAPNEL_COUNT pieces from the centre, each upward of the horizon (even over the hemisphere). */
    private void throwShrapnel(ServerLevel level, Vec3 centre) {
        RandomSource random = getRandom();
        for (int i = 0; i < SHRAPNEL_COUNT; ++i) {
            double yaw = random.nextDouble() * Math.PI * 2.0;
            double y = random.nextDouble(); // uniform height = uniform over the hemisphere's surface
            double flat = Math.sqrt(1.0 - y * y);
            Vec3 dir = new Vec3(Math.cos(yaw) * flat, y, Math.sin(yaw) * flat);
            float speed = SHRAPNEL_SPEED_MIN + random.nextFloat() * (SHRAPNEL_SPEED_MAX - SHRAPNEL_SPEED_MIN);
            RealmBulletEntity piece = new RealmBulletEntity(level);
            piece.setPos(centre);
            piece.setVelocity(speed, dir);
            piece.damage = SHRAPNEL_DAMAGE;
            piece.mobDamageMultiplier = 1.0f;
            piece.igniteOnHitSeconds = SHRAPNEL_BURN_SECONDS;
            piece.breaksBlocks = false;
            piece.setLifetimeTicks(SHRAPNEL_LIFETIME);
            piece.setVisuals(net.gunmetal.arms.guns.GunType.Projectile.SHRAPNEL, net.gunmetal.arms.guns.GunType.Trail.FLAME);
            level.addFreshEntity(piece);
        }
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        return XP;
    }

    // ---- Misc ---------------------------------------------------------------------------------

    @Override
    public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean onClimbable() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
        return false;
    }

    @Override
    protected void pickUpItem(ServerLevel level, ItemEntity item) {
    }

    @Override
    public boolean canBeLeader() {
        return false;
    }

    @Override
    public void applyRaidBuffs(ServerLevel level, int wave, boolean isCaptain) {
    }

    @Override
    public SoundEvent getCelebrateSound() {
        return SoundEvents.PILLAGER_CELEBRATE;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return RaidSounds.SIEGE_BALLOON_AMBIENT;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return RaidSounds.SIEGE_BALLOON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return RaidSounds.SIEGE_BALLOON_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 2.0f;
    }

    // ---- Save ---------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        EntityReference.store(anchor, output, "Tether");
        output.putByte("RiseState", entityData.get(RISE));
        output.putBoolean("PilotLost", pilotLost());
        output.putBoolean("CutLoose", cutLoose());
        output.putFloat("DriftYaw", driftYaw);
        output.putInt("CannonTimer", cannonTimer);
        output.putInt("CrossbowTimer", crossbowTimer);
        output.putInt("ParatrooperTimer", paratrooperTimer);
        output.putInt("ParatroopersDropped", paratroopersDropped);
        output.putInt("TetherLength", (int) tetherLength());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        anchor = EntityReference.read(input, "Tether");
        entityData.set(RISE, (byte) Mth.clamp(input.getByteOr("RiseState", (byte) RISE_HIGH), RISE_HIGH, RISE_LOW));
        setFlag(FLAG_PILOT_LOST, input.getBooleanOr("PilotLost", false));
        setFlag(FLAG_CUT_LOOSE, input.getBooleanOr("CutLoose", false));
        driftYaw = input.getFloatOr("DriftYaw", 0f);
        cannonTimer = input.getIntOr("CannonTimer", READY_TICKS + 40);
        crossbowTimer = input.getIntOr("CrossbowTimer", READY_TICKS + 20);
        paratrooperTimer = input.getIntOr("ParatrooperTimer", PARATROOPER_INTERVAL);
        paratroopersDropped = input.getIntOr("ParatroopersDropped", 0);
        entityData.set(TETHER_LENGTH_DATA, (byte) Mth.clamp(input.getIntOr("TetherLength", (int) TETHER_LENGTH), 1, 64));
        lastStage = damageStage();
        setNoGravity(true);
    }
}
