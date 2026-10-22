package net.gunmetal.raids.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.gunmetal.raids.registry.RaidEntities;
import net.gunmetal.raids.registry.RaidParticles;
import net.gunmetal.raids.registry.RaidSounds;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Evoker-style tinkerer (evoker model, animations and spell arm-raise). Two things:
 *
 * Summons (arms raised 2.5 s, the summon 1 s in; one move every 7 s +-1 s while it has a target).
 * Moves alternate A, B, A, B...:
 *   A: two or three summons (even odds; three only if three fit), each a random kind still under its
 *      cap (drones 2, brass golem 1, Puppeted 4; the same kind may come more than once). If fewer
 *      than 2 fit, A is skipped and B happens instead.
 *   B: every summon it owns is upgraded (drones and golems up a level, or Regeneration I for 5 s at
 *      level 3; Puppeted +16 health), then one random summon joins (not upgraded this move).
 *   The move is chosen when the cast starts and shown to the model (summon_signal / upgrade_signal,
 *   see RaidIllagerModel); caps are checked again when the summons appear. Its golems arrive with
 *   a fuse count of 30. None of them count toward the raid (the wave doesn't wait for them). When it
 *   dies (or can't be found for 2 s), all of them go with it: drones and Puppeted (completed ones too)
 *   die, and brass golems jump to a full fuse count and blow up 1.5 s later.
 *
 * Pulse (no arm-raise), every 5.5 s while it has a target: 2 s before, a warning sound and sparks
 * from it; then a wave travels out to 5 blocks (0.2 blocks/tick, ~1.25 s) and stuns everything it
 * reaches except raiders and brass golems (alexscaves:stunned I for 2 s; Slowness IV without Alex's
 * Caves). It deals no damage.
 * Three flat rings show at its feet (top and middle 1.5 s, bottom 1 s), and for 1.5 s projectiles
 * that come within 5 blocks of it are destroyed or stopped (see DESTROYED / STOPPED; not raiders' own).
 */
public class Artificer extends SpellcasterIllager implements AnimSignalled {
    // ---- Tuning -------------------------------------------------------------------------
    public static final double MAX_HEALTH = 24.0, MOVEMENT_SPEED = 0.5, FOLLOW_RANGE = 24.0;
    /** Pulse: one every PULSE_INTERVAL ticks (5.5 s), warned PULSE_WARNING ticks (2 s) before. */
    public static final int PULSE_INTERVAL = 110, PULSE_WARNING = 40;
    /** The pulse destroys these projectiles: #minecraft:arrows, Realm Arms bullets and the ids below (ids of mods that aren't installed are simply never matched). */
    private static final Identifier REALM_ARMS_BULLET = Identifier.fromNamespaceAndPath("gunmetal", "bullet");
    private static final java.util.Set<Identifier> DESTROYED = java.util.Set.of(
            Identifier.fromNamespaceAndPath("alexsmobs", "shark_tooth_arrow"),
            Identifier.fromNamespaceAndPath("alexscaves", "seeking_arrow"),
            Identifier.fromNamespaceAndPath("alexscaves", "dark_arrow"),
            Identifier.fromNamespaceAndPath("alexscaves", "burrowing_arrow"),
            Identifier.fromNamespaceAndPath("frostiful", "packed_snowball"),
            Identifier.fromNamespaceAndPath("minecraft", "firework_rocket"));
    /** ...and stops these dead instead (they fall, land and can be picked up; potions shatter where they land). */
    private static final java.util.Set<Identifier> STOPPED = java.util.Set.of(
            Identifier.fromNamespaceAndPath("minecraft", "trident"),
            Identifier.fromNamespaceAndPath("legacies_and_legends", "boomerang"),
            Identifier.fromNamespaceAndPath("kitchenprojectiles", "knife"),
            Identifier.fromNamespaceAndPath("minecraft", "splash_potion"),
            Identifier.fromNamespaceAndPath("minecraft", "lingering_potion"));
    /** A stopped projectile only sparks and sounds when it was moving faster than this (blocks/tick), not each time it's re-stopped as it sinks. */
    private static final double STOP_EFFECT_SPEED = 0.5;
    /** Pulse reach (blocks, horizontal) and how fast the wave travels out (blocks per tick: 0.5 = 8 ticks to the edge). */
    public static final double PULSE_RADIUS = 5.0, PULSE_WAVE_SPEED = 0.2;
    /** Mobs above/below its feet by more than this aren't reached (the pulse runs along the ground). */
    public static final double PULSE_HEIGHT_BELOW = 1.5, PULSE_HEIGHT_ABOVE = 2.5;
    /** How long the rings show (2.5 s); projectiles are destroyed for this long. */
    public static final int PULSE_VISIBLE_TICKS = 30;
    /** Stun: level I (amplifier 0) for 2 s. */
    public static final int STUN_TICKS = 40, STUN_AMPLIFIER = 0;
    public static final Identifier STUN_EFFECT = Identifier.fromNamespaceAndPath("alexscaves", "stunned");
    /** Summon spell timing (evoker vex summon values): warmup, arms raised, time between summons. */
    /** Interval: 7 s +- 1 s (random each move). */
    public static final int SUMMON_WARMUP = 20, SUMMON_CAST_TICKS = 50, SUMMON_INTERVAL = 140, SUMMON_INTERVAL_JITTER = 20;
    public static final int DRONE_CAP = 2, GOLEM_CAP = 1, PUPPETED_CAP = 4;
    /** Move A: two summons, or (this often, when three fit) three. */
    public static final int MOVE_A_MIN = 2, MOVE_A_MAX = 3;
    public static final float MOVE_A_MAX_CHANCE = 0.5f;
    /** Its brass golems arrive with this fuse count (of 60). */
    public static final float GOLEM_START_FUSE = 30f;
    /** Its summons count toward the caps while within this many blocks of it. */
    public static final double OWNED_SEARCH = 48.0;
    // -------------------------------------------------------------------------------------

    /**
     * Model states (hat.rz, see AnimSignalled); abs(hat.rx) = seconds since the last pulse.
     * The summon arm-raise is the vanilla evoker spellcasting pose.
     *   0 idle (no target), 1 has a target, 2 pulse warning (the 2 s before), 3 pulse showing (2.5 s)
     */
    public static final int STATE_IDLE = 0, STATE_TARGET = 1, STATE_PULSE_WARNING = 2, STATE_PULSE = 3;

    private static final EntityDataAccessor<Byte> ANIM_STATE = SynchedEntityData.defineId(Artificer.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> PULSE_COUNT = SynchedEntityData.defineId(Artificer.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SUMMON_PLAN = SynchedEntityData.defineId(Artificer.class, EntityDataSerializers.INT);
    private static final SimpleParticleType[] RINGS = {RaidParticles.PULSE_RING_LOW, RaidParticles.PULSE_RING_MID, RaidParticles.PULSE_RING_TOP};
    /** Height of the lowest ring above its feet, and the gap between rings. */
    private static final double RING_BASE = 0.05, RING_GAP = 0.2;
    /** Projectiles up to this fast (blocks/tick) are caught before they cross the pulse. */
    private static final double ZAP_LOOKAHEAD = 20.0;

    /** Ticks to the next pulse (counts down while it has a target, or once the warning has started). */
    private int pulseTimer = PULSE_INTERVAL;
    private int pulseVisibleTicks;
    private int waveTicks;
    private final Set<Integer> stunnedThisPulse = new HashSet<>();
    /** The next summon move is B (upgrade + one summon); otherwise A (two summons). */
    private boolean nextMoveB;
    /** The planned move (shown to the model from the start of the cast). */
    private int plannedDrones, plannedGolems, plannedPuppeted;
    private boolean plannedUpgrade;
    private int clientLastPulseTick = Integer.MIN_VALUE;

    public Artificer(EntityType<? extends SpellcasterIllager> type, Level level) {
        super(type, level);
        this.xpReward = 10;
    }

    @Override
    public net.minecraft.world.entity.@Nullable SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty, EntitySpawnReason reason,
            net.minecraft.world.entity.@Nullable SpawnGroupData groupData) {
        net.minecraft.world.entity.SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
        setLeftHanded(false); // vanilla makes 5% left-handed; the custom poses assume the right hand
        return data;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ANIM_STATE, (byte) 0);
        builder.define(PULSE_COUNT, 0);
        builder.define(SUMMON_PLAN, 0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new SpellcasterCastingSpellGoal());
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 8.0f, 0.6, 1.0));
        goalSelector.addGoal(4, new SummonGoal());
        goalSelector.addGoal(8, new RandomStrollGoal(this, 0.6));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 3.0f, 1.0f));
        goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, Raider.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true).setUnseenMemoryTicks(300));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false).setUnseenMemoryTicks(300));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, false));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, PathfinderMob.class, 10, true, false,
                (entity, level) -> RaidIllager.isGuard(entity)));
    }

    // ---- Animation signals ------------------------------------------------------------------

    @Override
    public int animState() {
        return entityData.get(ANIM_STATE);
    }

    @Override
    public float secondsSinceShot(float partialTick) {
        if (clientLastPulseTick == Integer.MIN_VALUE) {
            return SHOT_TIMER_CAP;
        }
        return Math.min(SHOT_TIMER_CAP, (tickCount - clientLastPulseTick + partialTick) / 20f);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (PULSE_COUNT.equals(key) && level().isClientSide() && tickCount > 0) {
            clientLastPulseTick = tickCount;
        }
    }

    /** Warning sparks per tick (4.5 every 2 ticks), and the share of that shown across the pulse area while it is active. */
    public static final float WARNING_SPARKS_PER_TICK = 2.25f, PULSE_AREA_SPARK_SHARE = 0.4f;
    /** The pulse-area sparks appear from this far below to this far above its feet. */
    public static final double PULSE_SPARKS_BELOW = 1.0, PULSE_SPARKS_ABOVE = 3.0;
    private float sparksOwed;

    /**
     * Client: warning sparks around it before a pulse, and a few sparks scattered through the pulse
     * area while it is active (1.5 s). Drawn by each player's game from the synced state (no packets).
     */
    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() || !isAlive()) {
            return;
        }
        int state = animState();
        if (state == STATE_PULSE_WARNING) {
            sparksOwed += WARNING_SPARKS_PER_TICK;
            while (sparksOwed >= 1f) {
                sparksOwed -= 1f;
                double w = getBbWidth() * 0.6, h = getBbHeight() * 0.4;
                level().addParticle(RaidParticles.ARTIFICER_SPARK, getX() + random.nextGaussian() * w, getY(0.6) + random.nextGaussian() * h,
                        getZ() + random.nextGaussian() * w, random.nextGaussian() * 0.05, random.nextGaussian() * 0.05, random.nextGaussian() * 0.05);
            }
        } else if (state == STATE_PULSE) {
            sparksOwed += WARNING_SPARKS_PER_TICK * PULSE_AREA_SPARK_SHARE;
            while (sparksOwed >= 1f) {
                sparksOwed -= 1f;
                double angle = random.nextDouble() * Math.PI * 2.0;
                double r = PULSE_RADIUS * Math.sqrt(random.nextDouble());
                double y = getY() - PULSE_SPARKS_BELOW + random.nextDouble() * (PULSE_SPARKS_BELOW + PULSE_SPARKS_ABOVE);
                level().addParticle(RaidParticles.ARTIFICER_SPARK, getX() + Math.cos(angle) * r, y, getZ() + Math.sin(angle) * r,
                        random.nextGaussian() * 0.05, random.nextGaussian() * 0.05, random.nextGaussian() * 0.05);
            }
        } else {
            sparksOwed = 0f;
        }
    }

    private int animStateNow() {
        if (pulseVisibleTicks > 0) {
            return STATE_PULSE;
        }
        if (pulseTimer <= PULSE_WARNING) {
            return STATE_PULSE_WARNING;
        }
        return getTarget() != null && !isCelebrating() ? STATE_TARGET : STATE_IDLE;
    }

    // ---- Pulse ----------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (plannedUpgrade && !isCastingSpell()) {
            clearPlan(); // the upgrade cast is over: the wrench goes away
        }
        boolean warning = pulseTimer <= PULSE_WARNING;
        if (getTarget() != null || warning) {
            --pulseTimer;
            if (pulseTimer == PULSE_WARNING) {
                level.playSound(null, getX(), getY(), getZ(), RaidSounds.ARTIFICER_PULSE_WARNING, getSoundSource(), 1.5f, 1.0f);
            }
            if (pulseTimer <= 0) {
                startPulse(level);
                pulseTimer = PULSE_INTERVAL;
            }
        } else {
            pulseTimer = PULSE_INTERVAL;
        }
        if (pulseVisibleTicks > 0) {
            --pulseVisibleTicks;
            ++waveTicks;
            double radius = Math.min(PULSE_RADIUS, waveTicks * PULSE_WAVE_SPEED);
            if (radius > (waveTicks - 1) * PULSE_WAVE_SPEED || waveTicks == 1) {
                stunWithin(level, radius);
            }
            zapProjectiles(level);
        }
        entityData.set(ANIM_STATE, (byte) animStateNow());
    }

    private void startPulse(ServerLevel level) {
        pulseVisibleTicks = PULSE_VISIBLE_TICKS;
        waveTicks = 0;
        stunnedThisPulse.clear();
        entityData.set(PULSE_COUNT, entityData.get(PULSE_COUNT) + 1);
        level.playSound(null, getX(), getY(), getZ(), RaidSounds.ARTIFICER_PULSE, getSoundSource(), 2.0f, 1.0f);
        for (int i = 0; i < RINGS.length; ++i) {
            level.sendParticles(RINGS[i], true, true, getX(), getY() + RING_BASE + RING_GAP * i, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Raiders (#minecraft:raiders, which includes its drones) and brass golems (both kinds) are never stunned. */
    private static boolean immuneToPulse(LivingEntity entity) {
        if (entity.is(EntityTypeTags.RAIDERS) || entity instanceof AbstractBrassGolem) {
            return true;
        }
        return entity instanceof Player player && (player.isCreative() || player.isSpectator());
    }

    private void stunWithin(ServerLevel level, double radius) {
        AABB area = new AABB(getX() - radius, getY() - PULSE_HEIGHT_BELOW, getZ() - radius,
                getX() + radius, getY() + PULSE_HEIGHT_ABOVE, getZ() + radius);
        List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, area, entity -> entity != this
                && entity.isAlive() && !immuneToPulse(entity) && !stunnedThisPulse.contains(entity.getId())
                && horizontalDistanceTo(entity) <= radius);
        for (LivingEntity entity : hit) {
            stunnedThisPulse.add(entity.getId());
            entity.addEffect(stunEffect(STUN_TICKS), this);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), RaidSounds.ARTIFICER_ELECTRIFIED,
                    SoundSource.HOSTILE, 1.0f, 0.9f + random.nextFloat() * 0.2f);
            level.sendParticles(RaidParticles.ARTIFICER_SPARK, entity.getX(), entity.getY(0.5), entity.getZ(), 12,
                    entity.getBbWidth() * 0.5, entity.getBbHeight() * 0.4, entity.getBbWidth() * 0.5, 0.08);
        }
    }

    private double horizontalDistanceTo(Entity entity) {
        double dx = entity.getX() - getX();
        double dz = entity.getZ() - getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Alex's Caves' Stunned I for this long (Slowness IV without Alex's Caves). Also used by brass golem blasts. */
    public static MobEffectInstance stunEffect(int ticks) {
        Holder<MobEffect> stun = BuiltInRegistries.MOB_EFFECT.get(STUN_EFFECT).map(h -> (Holder<MobEffect>) h).orElse(null);
        if (stun == null) {
            return new MobEffectInstance(MobEffects.SLOWNESS, ticks, 3);
        }
        return new MobEffectInstance(stun, ticks, STUN_AMPLIFIER);
    }

    /** While the rings show: projectiles heading within PULSE_RADIUS of it this tick are destroyed (not raiders' own). */
    private static boolean zappable(Projectile projectile) {
        if (projectile.is(EntityTypeTags.ARROWS)) {
            return true;
        }
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType());
        return REALM_ARMS_BULLET.equals(id) || DESTROYED.contains(id);
    }

    private static boolean stoppable(Projectile projectile) {
        return STOPPED.contains(BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()));
    }

    /** Any projectile the pulse destroys or stops (also what the Sharpshooter dodges). */
    public static boolean isPulseProjectile(Projectile projectile) {
        return zappable(projectile) || stoppable(projectile);
    }

    private void zapProjectiles(ServerLevel level) {
        Vec3 center = new Vec3(getX(), getY(0.5), getZ());
        List<Projectile> nearby = level.getEntitiesOfClass(Projectile.class, getBoundingBox().inflate(PULSE_RADIUS + ZAP_LOOKAHEAD),
                Entity::isAlive);
        for (Projectile projectile : nearby) {
            boolean stop = stoppable(projectile);
            if (!stop && !zappable(projectile)) {
                continue;
            }
            Vec3 velocity = projectile.getDeltaMovement();
            if (velocity.lengthSqr() < 0.01) {
                continue; // stuck in the ground, or floating
            }
            Entity owner = projectile.getOwner();
            if (owner != null && (owner == this || owner.is(EntityTypeTags.RAIDERS) || isAlliedTo(owner))) {
                continue;
            }
            Vec3 from = projectile.position();
            if (distanceToSegment(center, from, from.add(velocity)) > PULSE_RADIUS) {
                continue;
            }
            Vec3 at = from.distanceToSqr(center) <= PULSE_RADIUS * PULSE_RADIUS ? from : closestOnSegment(center, from, from.add(velocity));
            if (stop) {
                stopDead(level, projectile, at, velocity);
            } else {
                zap(level, projectile, at);
            }
        }
    }

    private void zap(ServerLevel level, Projectile projectile, Vec3 at) {
        projectile.discard();
        level.playSound(null, at.x, at.y, at.z, RaidSounds.ARTIFICER_ZAP, SoundSource.HOSTILE, 1.0f, 0.9f + random.nextFloat() * 0.3f);
        level.sendParticles(RaidParticles.ARTIFICER_ZAP, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.1);
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 3, 0.05, 0.05, 0.05, 0.01);
    }

    /** Takes all its speed where it meets the pulse; it then falls (and is stopped again while it's still inside). */
    private void stopDead(ServerLevel level, Projectile projectile, Vec3 at, Vec3 velocity) {
        projectile.setPos(at.x, at.y, at.z);
        projectile.setDeltaMovement(Vec3.ZERO);
        projectile.hurtMarked = true; // send the new speed to players at once
        projectile.needsSync = true;  // and the new position
        if (velocity.lengthSqr() > STOP_EFFECT_SPEED * STOP_EFFECT_SPEED) {
            level.playSound(null, at.x, at.y, at.z, RaidSounds.ARTIFICER_ZAP, SoundSource.HOSTILE, 1.0f, 0.9f + random.nextFloat() * 0.3f);
            level.sendParticles(RaidParticles.ARTIFICER_ZAP, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.1);
        }
    }

    private static Vec3 closestOnSegment(Vec3 point, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double lengthSqr = ab.lengthSqr();
        double t = lengthSqr < 1.0E-9 ? 0.0 : Math.max(0.0, Math.min(1.0, point.subtract(a).dot(ab) / lengthSqr));
        return a.add(ab.scale(t));
    }

    private static double distanceToSegment(Vec3 point, Vec3 a, Vec3 b) {
        return closestOnSegment(point, a, b).distanceTo(point);
    }

    /** Projectiles that still reach it while the rings show are zapped instead of hurting it. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (pulseVisibleTicks > 0 && source.getDirectEntity() instanceof Projectile projectile
                && !(projectile.getOwner() != null && projectile.getOwner().is(EntityTypeTags.RAIDERS))) {
            if (zappable(projectile)) {
                zap(level, projectile, projectile.position());
                return false;
            }
            if (stoppable(projectile)) {
                return false; // blocked: it bounces off and drops
            }
        }
        return super.hurtServer(level, source, amount);
    }

    // ---- Summons --------------------------------------------------------------------------

    private List<ArtificerDrone> ownedDrones(ServerLevel level) {
        return level.getEntitiesOfClass(ArtificerDrone.class, getBoundingBox().inflate(OWNED_SEARCH),
                drone -> drone.isAlive() && drone.isOwnedBy(this));
    }

    private int ownedGolems(ServerLevel level) {
        return level.getEntitiesOfClass(BrassGolem.class, getBoundingBox().inflate(OWNED_SEARCH),
                golem -> golem.isAlive() && golem.isOwnedBy(this)).size();
    }

    private List<Puppeted> ownedPuppeted(ServerLevel level) {
        return level.getEntitiesOfClass(Puppeted.class, getBoundingBox().inflate(OWNED_SEARCH),
                puppeted -> puppeted.isAlive() && puppeted.isOwnedBy(this));
    }

    // Summon moves alternate A (two summons) and B (upgrade everything it owns, then one summon). The
    // move is planned when the cast starts (so the model can show it) and carried out a second later.
    private static final int KIND_DRONE = 0, KIND_GOLEM = 1, KIND_PUPPETED = 2;

    /** Picks the next move: A (2-3 summons) if two summons fit under the caps, otherwise (and every other time) B. */
    private void planSummon(ServerLevel level) {
        int[] free = {DRONE_CAP - ownedDrones(level).size(), GOLEM_CAP - ownedGolems(level), PUPPETED_CAP - ownedPuppeted(level).size()};
        int[] picks = new int[3];
        boolean upgrade = nextMoveB || free[0] + free[1] + free[2] < MOVE_A_MIN;
        int count = upgrade ? 1 : (random.nextFloat() < MOVE_A_MAX_CHANCE ? MOVE_A_MAX : MOVE_A_MIN); // fewer if the caps run out
        for (int i = 0; i < count; ++i) {
            int kind = pickFree(free);
            if (kind < 0) {
                break;
            }
            --free[kind];
            ++picks[kind];
        }
        plannedDrones = picks[KIND_DRONE];
        plannedGolems = picks[KIND_GOLEM];
        plannedPuppeted = picks[KIND_PUPPETED];
        plannedUpgrade = upgrade;
        syncPlan();
    }

    /** A random kind that still has room (each equally likely), or -1. */
    private int pickFree(int[] free) {
        int kinds = 0;
        for (int f : free) {
            kinds += f > 0 ? 1 : 0;
        }
        if (kinds == 0) {
            return -1;
        }
        int n = random.nextInt(kinds);
        for (int kind = 0; kind < free.length; ++kind) {
            if (free[kind] > 0 && n-- == 0) {
                return kind;
            }
        }
        return -1;
    }

    /** Carries out the planned move: upgrades first (B), then the summons (so new ones aren't upgraded). Caps are checked again. */
    private void performSummon(ServerLevel level) {
        nextMoveB = !plannedUpgrade; // A is followed by B, B by A (an interrupted cast doesn't count)
        if (plannedUpgrade) {
            for (ArtificerDrone drone : ownedDrones(level)) {
                drone.upgrade();
            }
            for (BrassGolem golem : level.getEntitiesOfClass(BrassGolem.class, getBoundingBox().inflate(OWNED_SEARCH),
                    golem -> golem.isAlive() && golem.isOwnedBy(this))) {
                golem.upgrade();
            }
            for (Puppeted puppeted : ownedPuppeted(level)) {
                puppeted.upgrade();
            }
        }
        for (int i = 0; i < plannedDrones && ownedDrones(level).size() < DRONE_CAP; ++i) {
            summonDrone(level);
        }
        for (int i = 0; i < plannedGolems && ownedGolems(level) < GOLEM_CAP; ++i) {
            summonGolem(level);
        }
        for (int i = 0; i < plannedPuppeted && ownedPuppeted(level).size() < PUPPETED_CAP; ++i) {
            summonPuppeted(level);
        }
        // The summons are out; the upgrade flag stays up (the wrench) until the cast ends.
        plannedDrones = plannedGolems = plannedPuppeted = 0;
        syncPlan();
    }

    private void clearPlan() {
        plannedDrones = plannedGolems = plannedPuppeted = 0;
        plannedUpgrade = false;
        syncPlan();
    }

    /** Synced plan: drones + 4 x golems + 16 x puppeted + 64 if it's an upgrade move (0 = nothing planned; each count 0-3). */
    public static final int PLAN_BASE = 4, PLAN_UPGRADE = 64;

    private void syncPlan() {
        entityData.set(SUMMON_PLAN, plannedDrones + PLAN_BASE * plannedGolems + PLAN_BASE * PLAN_BASE * plannedPuppeted
                + (plannedUpgrade ? PLAN_UPGRADE : 0));
    }

    @Override
    public int summonPlan() {
        return entityData.get(SUMMON_PLAN);
    }

    private void summonPuppeted(ServerLevel level) {
        Puppeted puppeted = RaidEntities.PUPPETED.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (puppeted == null) {
            return;
        }
        placeNear(level, puppeted);
        finishSummon(level, puppeted);
        level.sendParticles(RaidParticles.SUMMON_MARKER, true, true, puppeted.getX(), puppeted.getY(0.5), puppeted.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
    }

    /** A free spot within 1.5 blocks of it (its own spot if none of a few tries is free). */
    private void placeNear(ServerLevel level, Mob summoned) {
        for (int attempt = 0; attempt < 6; ++attempt) {
            double x = getX() + (random.nextDouble() - 0.5) * 3.0;
            double z = getZ() + (random.nextDouble() - 0.5) * 3.0;
            summoned.snapTo(x, getY(), z, getYRot(), 0.0f);
            if (level.noCollision(summoned)) {
                return;
            }
        }
        summoned.snapTo(getX(), getY(), getZ(), getYRot(), 0.0f);
    }

    private void summonDrone(ServerLevel level) {
        ArtificerDrone drone = RaidEntities.DRONE.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (drone != null) {
            Vec3 pos = new Vec3(getX() + (random.nextDouble() - 0.5) * 2.0, getY() + 1.5 + random.nextDouble() * 0.5,
                    getZ() + (random.nextDouble() - 0.5) * 2.0);
            drone.snapTo(pos.x, pos.y, pos.z, getYRot(), 0.0f);
            finishSummon(level, drone);
        }
    }

    private void summonGolem(ServerLevel level) {
        BrassGolem golem = RaidEntities.BRASS_GOLEM.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (golem == null) {
            return;
        }
        placeNear(level, golem);
        golem.startFuseAt(GOLEM_START_FUSE);
        finishSummon(level, golem);
        level.sendParticles(RaidParticles.SUMMON_MARKER, true, true, golem.getX(), golem.getY(0.5), golem.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
    }

    private void finishSummon(ServerLevel level, Mob summoned) {
        BlockPos pos = summoned.blockPosition();
        summoned.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.MOB_SUMMONED, null);
        if (summoned instanceof ArtificerDrone drone) {
            drone.setOwner(this);
        } else if (summoned instanceof BrassGolem golem) {
            golem.setOwner(this);
        } else if (summoned instanceof Puppeted puppeted) {
            puppeted.setOwner(this);
        }
        if (getTarget() != null) {
            summoned.setTarget(getTarget());
        }
        PlayerTeam team = getTeam();
        if (team != null) {
            level.getScoreboard().addPlayerToTeam(summoned.getScoreboardName(), team);
        }
        level.addFreshEntity(summoned);
        level.sendParticles(ParticleTypes.POOF, summoned.getX(), summoned.getY(0.5), summoned.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
    }

    /** The evoker's vex-summon spell, summoning drones / golems instead. */
    private class SummonGoal extends SpellcasterUseSpellGoal {
        @Override
        public void start() {
            super.start();
            if (level() instanceof ServerLevel level) {
                planSummon(level);
            }
        }

        @Override
        protected void performSpellCasting() {
            if (level() instanceof ServerLevel level) {
                performSummon(level);
            }
        }

        @Override
        public void stop() {
            super.stop();
            // Interrupted before the summon (lost its target): drop the plan's summons.
            plannedDrones = plannedGolems = plannedPuppeted = 0;
            syncPlan();
        }

        @Override
        protected int getCastWarmupTime() {
            return SUMMON_WARMUP;
        }

        @Override
        protected int getCastingTime() {
            return SUMMON_CAST_TICKS;
        }

        @Override
        protected int getCastingInterval() {
            return SUMMON_INTERVAL - SUMMON_INTERVAL_JITTER + random.nextInt(SUMMON_INTERVAL_JITTER * 2 + 1);
        }

        @Override
        protected SoundEvent getSpellPrepareSound() {
            return RaidSounds.ARTIFICER_SUMMON;
        }

        @Override
        protected IllagerSpell getSpell() {
            return IllagerSpell.SUMMON_VEX;
        }
    }

    // ---- Misc -----------------------------------------------------------------------------

    @Override
    protected boolean considersEntityAsAlly(Entity entity) {
        if (super.considersEntityAsAlly(entity)) {
            return true;
        }
        return (entity instanceof ArtificerDrone || entity instanceof BrassGolem || entity instanceof Puppeted)
                && getTeam() == null && entity.getTeam() == null;
    }

    @Override
    public void applyRaidBuffs(ServerLevel level, int wave, boolean isCaptain) {
    }

    @Override
    public boolean canBeLeader() {
        return false;
    }

    @Override
    protected SoundEvent getCastingSoundEvent() {
        return SoundEvents.EVOKER_CAST_SPELL;
    }

    @Override
    public SoundEvent getCelebrateSound() {
        return SoundEvents.EVOKER_CELEBRATE;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return RaidSounds.ARTIFICER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return RaidSounds.ARTIFICER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return RaidSounds.ARTIFICER_DEATH;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("NextSummonMoveB", nextMoveB);
        output.putInt("PulseTimer", pulseTimer);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        nextMoveB = input.getBooleanOr("NextSummonMoveB", false);
        pulseTimer = input.getIntOr("PulseTimer", PULSE_INTERVAL);
    }
}
