package net.gunmetal.raids.entity;

import net.minecraft.core.Holder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.gunmetal.raids.registry.RaidSounds;

/**
 * Shared by both brass golems (the Artificer's hostile one and the player's deployed one): copper
 * golem model, steps up 1.8 blocks and paths over fences, and a fuse count.
 *
 * Fuse count: rises 1 per second, +5 for every damaging hit (fire ticks and explosions included).
 * Reaching its threshold (60; deployed golems get more from copper patina) lights a creeper-length
 * fuse (1.5 s): it hisses, swells and flashes and carries on at half speed, then blows up (no blocks
 * broken; what the blast hurts is also stunned 1 s; a visual-only pulse and debris go with it). The
 * hostile golem also lights it on reaching its target. A lit fuse can't be put out. Killing a golem
 * before it blows up defuses it: no blast. Hits and deaths throw debris (SummonFx).
 *
 * Client side (each player's own game): ticking that speeds up from 1 to 8 per second as the count
 * nears the threshold (silent over a lit fuse: the fuse sound takes over), and sparks that thicken
 * (on to their peak over a lit fuse).
 */
public abstract class AbstractBrassGolem extends PathfinderMob {
    // ---- Tuning -------------------------------------------------------------------------
    /** Both golems: health, speed (a copper golem's), knockback resistance (1 = never knocked back), step height. */
    public static final double MAX_HEALTH = 24.0, MOVEMENT_SPEED = 0.2, KNOCKBACK_RESISTANCE = 1.0, STEP_HEIGHT = 1.8;
    /** Fuse threshold before load bonuses, gain per tick (1/s) and per damaging hit. */
    public static final float BASE_FUSE_THRESHOLD = 60f, FUSE_PER_TICK = 0.05f, FUSE_PER_HIT = 5f;
    /** The lit fuse (creeper length, 1.5 s): hiss, stand still, swell and flash, then the blast. */
    public static final int LIT_FUSE_TICKS = 30;
    /** Ticking: TICK_RATE_START * TICK_RATE_GROWTH^progress per second (1/s -> 8/s); none over a lit fuse. */
    public static final float TICK_RATE_START = 1f, TICK_RATE_GROWTH = 8f;
    /** Real ground speed while the fuse burns (x normal; speed settings act squared, so the setting gets the root). */
    public static final double LIT_FUSE_SPEED = 0.5;
    /** Its blast stuns what it hurts for this long (ticks; Alex's Caves' Stunned, or Slowness IV). */
    public static final int BLAST_STUN_TICKS = 20;
    /** Ticking volume, the fuse sound's (1.0: heard to 16 blocks; above 1 only adds range and slows the fade with distance). */
    public static final float TICK_SOUND_VOLUME = 1.0f;
    /** Sparks (SummonSparks multiplier) at no progress and at a full fuse, linear between. */
    public static final float SPARKS_START = 0.2f, SPARKS_FULL = 1.5f;
    /**
     * The count is only sent to players when it has moved this much since the last send (so the
     * passive gain sends every 10 ticks, hits at once); in between each player's game
     * adds the passive gain itself.
     */
    public static final float FUSE_SYNC_STEP = 0.5f;
    /** Debris pieces (min, max): on a direct hit, when killed, when it blows up (both golems). */
    public static final int DEBRIS_HIT_MIN = 1, DEBRIS_HIT_MAX = 4, DEBRIS_DEATH_MIN = 1, DEBRIS_DEATH_MAX = 4,
            DEBRIS_BLAST_MIN = 4, DEBRIS_BLAST_MAX = 12;
    // -------------------------------------------------------------------------------------

    private static final EntityDataAccessor<Float> FUSE = SynchedEntityData.defineId(AbstractBrassGolem.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> THRESHOLD = SynchedEntityData.defineId(AbstractBrassGolem.class, EntityDataSerializers.FLOAT);
    /** Ticks the fuse has been lit (NOT_LIT = not lit), counted by the golem's own ticks (sent each tick only while lit, 30 at most). */
    private static final EntityDataAccessor<Integer> LIT_TICKS = SynchedEntityData.defineId(AbstractBrassGolem.class, EntityDataSerializers.INT);
    private static final int NOT_LIT = -1;

    public final AnimationState idleAnimationState = new AnimationState();
    private final SummonSparks sparks = new SummonSparks();
    /** Server: the real fuse count (FUSE is its last sent value). */
    private float serverFuse;
    /** Client: fuse count last tick and this tick (for smooth drawing), and ticking owed. */
    private float clientOldFuse, clientFuse;
    private boolean clientFuseSeen;
    private float tickSoundOwed;
    /** Client: lit ticks last tick and this tick (for a smooth swell). */
    private int clientOldLit = NOT_LIT, clientLit = NOT_LIT;

    protected AbstractBrassGolem(EntityType<? extends AbstractBrassGolem> type, Level level) {
        super(type, level);
        // Fences, walls and closed fence gates: no detour (it steps over them, step height 1.8).
        setPathfindingMalus(PathType.FENCE, 0.0f);
        getNavigation().getNodeEvaluator().setCanWalkOverFences(true);
        tickSoundOwed = level.getRandom().nextFloat(); // golems placed together don't tick in step
    }

    /** Attributes both golems share, on top of the given base. */
    protected static AttributeSupplier.Builder golemAttributes(AttributeSupplier.Builder builder) {
        return builder.add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
                .add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE)
                .add(Attributes.STEP_HEIGHT, STEP_HEIGHT);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FUSE, 0f);
        builder.define(THRESHOLD, BASE_FUSE_THRESHOLD);
        builder.define(LIT_TICKS, NOT_LIT);
    }

    // ---- Fuse ---------------------------------------------------------------------------------

    /** The fuse count (on the client: the last synced value plus the passive gain since). */
    public float getFuse() {
        return level().isClientSide() ? clientFuse : serverFuse;
    }

    public float getFuseThreshold() {
        return entityData.get(THRESHOLD);
    }

    protected void setFuse(float fuse) {
        serverFuse = Math.max(0f, fuse);
        if (Math.abs(serverFuse - entityData.get(FUSE)) >= FUSE_SYNC_STEP) {
            entityData.set(FUSE, serverFuse);
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (FUSE.equals(accessor) && level().isClientSide()) {
            clientFuse = entityData.get(FUSE);
            if (!clientFuseSeen) {
                clientOldFuse = clientFuse;
                clientFuseSeen = true;
            }
        }
    }

    protected void addFuse(float amount) {
        setFuse(getFuse() + amount);
    }

    protected void setFuseThreshold(float threshold) {
        entityData.set(THRESHOLD, Math.max(1f, threshold));
    }

    /** 0..1: fuse count over threshold (smoothed between ticks on the client). */
    public float getFuseProgress(float partialTick) {
        float fuse = level().isClientSide() ? Mth.lerp(partialTick, clientOldFuse, clientFuse) : getFuse();
        return Mth.clamp(fuse / getFuseThreshold(), 0f, 1f);
    }

    public boolean isFuseLit() {
        return entityData.get(LIT_TICKS) != NOT_LIT;
    }

    /** Server: lights the 1.5 s fuse (once; it can't be put out). */
    protected void lightFuse() {
        if (!isFuseLit()) {
            entityData.set(LIT_TICKS, 0);
            playSound(RaidSounds.BRASS_GOLEM_FUSE, 1.0f, 1.0f);
            gameEvent(GameEvent.PRIME_FUSE);
        }
    }

    /** Ticks the fuse has been lit (with the partial tick), or -1 when not lit. */
    private float litTicks(float partialTick) {
        if (!level().isClientSide()) {
            return entityData.get(LIT_TICKS);
        }
        if (clientLit == NOT_LIT) {
            return -1f;
        }
        return clientOldLit == NOT_LIT ? clientLit : Mth.lerp(partialTick, clientOldLit, clientLit);
    }

    /** 0..1: how swollen it is drawn (creeper-style swell and white flash): over the lit fuse. */
    public float getSwelling(float partialTick) {
        if (!isAlive()) {
            return 0f; // killed: defused, no swell or flash through the death animation
        }
        float lit = litTicks(partialTick);
        return lit < 0f ? 0f : Mth.clamp(lit / LIT_FUSE_TICKS, 0f, 1f);
    }

    /** 0..1 for the sparks: the fuse progress, carried on to 1 over a lit fuse. */
    private float effectProgress(float partialTick) {
        float progress = getFuseProgress(partialTick);
        return progress + (1f - progress) * getSwelling(partialTick);
    }

    /** Half ground speed while the fuse burns (it keeps going: chasing or wandering). */
    @Override
    public void setSpeed(float speed) {
        super.setSpeed(isFuseLit() ? speed * (float) Math.sqrt(LIT_FUSE_SPEED) : speed);
    }

    /** Server: per-tick work while alive and not blowing up (after the fuse has risen). */
    protected void serverTick(ServerLevel level) {
    }

    /** Server: blows up at the end of the fuse (call explode). */
    protected abstract void blowUp(ServerLevel level);

    /** Damage it actually takes from a hit (the deployed golem cuts it down). */
    protected float scaleDamage(DamageSource source, float amount) {
        return amount;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, scaleDamage(source, amount));
        // Only damaging hits count (not snowballs or eggs); a hit that kills it defuses it.
        if (hurt && amount > 0f && isAlive()) {
            addFuse(FUSE_PER_HIT);
        }
        return hurt;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level) {
            if (!isAlive()) {
                return;
            }
            if (isFuseLit()) {
                int lit = entityData.get(LIT_TICKS) + 1;
                entityData.set(LIT_TICKS, lit);
                if (lit >= LIT_FUSE_TICKS) {
                    blowUp(level);
                }
                return;
            }
            addFuse(FUSE_PER_TICK);
            if (getFuse() >= getFuseThreshold()) {
                lightFuse();
            }
            serverTick(level); // also on the tick it lights (a deployed golem's pulse on appearing)
            return;
        }
        // Client: carry on the passive gain between syncs.
        clientOldFuse = clientFuse;
        clientOldLit = clientLit;
        clientLit = entityData.get(LIT_TICKS);
        if (isAlive() && !isFuseLit()) {
            clientFuse += FUSE_PER_TICK;
        }
        idleAnimationState.animateWhen(walkAnimation.speed() < 0.1f, tickCount);
        if (isAlive()) {
            float progress = effectProgress(1f);
            // No ticking over the lit fuse: the fuse sound carries it.
            if (!isFuseLit()) {
                float perSecond = TICK_RATE_START * (float) Math.pow(TICK_RATE_GROWTH, progress);
                tickSoundOwed += perSecond / 20f;
                if (tickSoundOwed >= 1f) {
                    tickSoundOwed -= 1f;
                    level().playLocalSound(getX(), getY(), getZ(), RaidSounds.BRASS_GOLEM_TICK, getSoundSource(),
                            TICK_SOUND_VOLUME, 1.0f, false);
                }
            }
            sparks.tick(this, SPARKS_START + (SPARKS_FULL - SPARKS_START) * progress);
        }
    }

    /** Sets an attribute's base value if the mob has that attribute (used to update older saves). */
    protected void setBase(Holder<Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /** Blows up (no blocks broken) and is removed, without dying (so no loot). */
    protected void explode(ServerLevel level, float radius, ExplosionDamageCalculator calculator, DamageSource source) {
        // Debris (each player's game sprays it on this event) and a visual-only pulse: the top and bottom rings.
        level.broadcastEntityEvent(this, SummonFx.DEBRIS_EVENT);
        level.sendParticles(net.gunmetal.raids.registry.RaidParticles.PULSE_RING_LOW, true, true, getX(), getY() + 0.05, getZ(),
                0, 1.0, 0.0, 0.0, 1.0);
        level.sendParticles(net.gunmetal.raids.registry.RaidParticles.PULSE_RING_TOP, true, true, getX(), getY() + 0.45, getZ(),
                0, 1.0, 0.0, 0.0, 1.0);
        dead = true;
        level.explode(this, source, calculator, getX(), getY(), getZ(), radius, false, Level.ExplosionInteraction.NONE);
        triggerOnDeathMobEffects(level, RemovalReason.KILLED);
        discard();
    }

    // ---- Save ---------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putFloat("FuseCount", getFuse());
        output.putInt("FuseLitTicks", (int) litTicks(0f));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setFuse(input.getFloatOr("FuseCount", 0f));
        entityData.set(LIT_TICKS, Math.max(NOT_LIT, input.getIntOr("FuseLitTicks", NOT_LIT)));
        // Saves keep attribute base values: bring golems saved by older versions up to the current tuning.
        setBase(Attributes.MAX_HEALTH, MAX_HEALTH);
        setBase(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED);
        setBase(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE);
        setBase(Attributes.STEP_HEIGHT, STEP_HEIGHT);
    }

    /** For the blasts' damage calculators: stun what the blast reaches (call once per entity it hurts). */
    protected static void blastStun(Entity entity, float exposure) {
        if (exposure > 0f && entity instanceof net.minecraft.world.entity.LivingEntity living && living.isAlive()
                && !(living instanceof net.minecraft.world.entity.player.Player player && (player.isCreative() || player.isSpectator()))) {
            living.addEffect(Artificer.stunEffect(BLAST_STUN_TICKS));
        }
    }

    // ---- Debris (client) ----------------------------------------------------------------------

    @Override
    public void handleDamageEvent(DamageSource source) {
        super.handleDamageEvent(source);
        SummonFx.onDamageEvent(this, source, DEBRIS_HIT_MIN, DEBRIS_HIT_MAX);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == SummonFx.DEBRIS_EVENT) { // blew up
            SummonFx.spawnDebris(this, DEBRIS_BLAST_MIN, DEBRIS_BLAST_MAX);
            return;
        }
        if (id == 3) { // killed
            SummonFx.spawnDebris(this, DEBRIS_DEATH_MIN, DEBRIS_DEATH_MAX);
        }
        super.handleEntityEvent(id);
    }

    // ---- Sounds -------------------------------------------------------------------------------

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return RaidSounds.BRASS_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return RaidSounds.BRASS_GOLEM_DEATH;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(RaidSounds.BRASS_GOLEM_STEP, 0.4f, 1.0f);
    }
}
