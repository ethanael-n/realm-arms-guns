package net.gunmetal.raids.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.gunmetal.raids.RealmRaids;
import net.gunmetal.raids.registry.RaidParticles;
import net.gunmetal.raids.registry.RaidSounds;
import net.gunmetal.arms.registry.RealmParticles;
import org.jetbrains.annotations.Nullable;

/**
 * The Artificer's Puppeted: a plain melee mob (a stripped-down zombie: walks up and hits, no
 * undead traits, no sunlight burning, no conversions, no egg trampling, no weapons). Normal size.
 *
 * Its state follows its health (max 38; summoned at 6; each of its Artificer's upgrade moves +16):
 *   incomplete (6 or less): zombified piglin model, speed 0.15, attack 1, no drops or XP;
 *   completed (7 or more): piglin model with its own look (one of two, picked when it appears),
 *     speed 0.25, attack 11, 5 XP; drops (loot table gunmetal:entities/puppeted) cogwheel 8% /
 *     large cogwheel 4% (player kill, +1% per Looting level) and 0-2 string.
 * Hurt back down to 6 it turns incomplete again; healed to 7 it completes again. Either way it dies
 * with its Artificer (or once it has been gone for 2 s); one without an Artificer (spawn egg) lives on.
 * Takes 20% of explosion damage and no knockback from explosions.
 *
 * Takes its Artificer's target; otherwise goes for players, villagers, iron golems and guards.
 * Gives off the Artificer's sparks (0.25x incomplete, 0.5x completed). Hits and deaths throw debris.
 * In #minecraft:raiders (raiders don't fight it, the pulse doesn't stun it); not an illager.
 */
public class Puppeted extends Monster {
    // ---- Tuning -------------------------------------------------------------------------
    public static final double MAX_HEALTH = 38.0;
    /** Health it appears with, health per upgrade, and the health from which it counts as completed. */
    public static final float SUMMON_HEALTH = 6.0f, UPGRADE_HEALTH = 16.0f, COMPLETE_FROM = 7.0f;
    public static final double INCOMPLETE_SPEED = 0.15, INCOMPLETE_DAMAGE = 1.0;
    public static final double COMPLETE_SPEED = 0.25, COMPLETE_DAMAGE = 11.0;
    /** Share of explosion damage it takes (80% resistance), and its explosion knockback resistance (1 = none). */
    public static final float EXPLOSION_DAMAGE_TAKEN = 0.2f;
    public static final double EXPLOSION_KNOCKBACK_RESISTANCE = 1.0;
    /** Ticks without its Artificer before it dies (as the drones). */
    public static final int HOST_MISSING_GRACE = 40;
    public static final double FOLLOW_RANGE = 32.0;
    /** Sparks (SummonSparks multiplier): half a level-1 drone's, then a level-1 drone's. */
    public static final float SPARKS_INCOMPLETE = 0.25f, SPARKS_COMPLETE = 0.5f;
    /** Completed looks (textures). */
    public static final int VARIANTS = 2;
    public static final int COMPLETE_XP = 5;
    /** Debris pieces (min, max): on a direct hit, on death. */
    public static final int DEBRIS_HIT_MIN = 1, DEBRIS_HIT_MAX = 3, DEBRIS_DEATH_MIN = 2, DEBRIS_DEATH_MAX = 5;
    // -------------------------------------------------------------------------------------

    public static final ResourceKey<net.minecraft.world.level.storage.loot.LootTable> COMPLETE_LOOT =
            ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(RealmRaids.MOD_ID, "entities/puppeted"));

    private static final EntityDataAccessor<Boolean> COMPLETED = SynchedEntityData.defineId(Puppeted.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> VARIANT = SynchedEntityData.defineId(Puppeted.class, EntityDataSerializers.BYTE);

    private final SummonSparks sparks = new SummonSparks();
    private final SummonFx.DelayedSound upgradeSound = new SummonFx.DelayedSound();
    private @Nullable EntityReference<Mob> owner;
    private int hostMissingTicks;

    public Puppeted(EntityType<? extends Puppeted> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, INCOMPLETE_SPEED)
                .add(Attributes.ATTACK_DAMAGE, INCOMPLETE_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE)
                .add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, EXPLOSION_KNOCKBACK_RESISTANCE);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return super.hurtServer(level, source, source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION) ? amount * EXPLOSION_DAMAGE_TAKEN : amount);
    }

    /** Appears incomplete, at 6 health, with its completed look already picked. */
    @Override
    public @Nullable net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty, net.minecraft.world.entity.EntitySpawnReason reason,
            @Nullable net.minecraft.world.entity.SpawnGroupData groupData) {
        entityData.set(VARIANT, (byte) level.getRandom().nextInt(VARIANTS));
        setHealth(SUMMON_HEALTH);
        updateState();
        return super.finalizeSpawn(level, difficulty, reason, groupData);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(COMPLETED, false);
        builder.define(VARIANT, (byte) 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new CopyOwnerTargetGoal(this));
        targetSelector.addGoal(2, new HurtByTargetGoal(this));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, PathfinderMob.class, 10, true, false,
                (entity, level) -> RaidIllager.isGuard(entity)));
    }

    // ---- State --------------------------------------------------------------------------------

    public boolean isCompleted() {
        return entityData.get(COMPLETED);
    }

    /** 0 or 1: which completed look. */
    public int variant() {
        return Math.floorMod(entityData.get(VARIANT), VARIANTS);
    }

    /**
     * Its Artificer's upgrade move: +16 health (up to 38), with the drone upgrade's sparks and
     * (slightly delayed) sound. Crossing 7 health completes it.
     */
    public void upgrade() {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        setHealth(getHealth() + UPGRADE_HEALTH);
        updateState();
        level.sendParticles(RealmParticles.SPARK_BRASS, getX(), getY(0.5), getZ(), 16,
                getBbWidth() * 0.5, getBbHeight() * 0.5, getBbWidth() * 0.5, 0.15);
        level.sendParticles(RaidParticles.SUMMON_MARKER, true, true, getX(), getY(0.5), getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        upgradeSound.schedule(RaidSounds.DRONE_UPGRADE, getRandom());
    }

    /** Server: the state follows the health (checked every tick and on upgrades; not while dying). */
    private void updateState() {
        if (level().isClientSide() || !isAlive()) {
            return;
        }
        boolean completed = getHealth() >= COMPLETE_FROM;
        if (completed != isCompleted()) {
            entityData.set(COMPLETED, completed);
            applyStats(completed);
        }
    }

    private void applyStats(boolean completed) {
        setBase(Attributes.MAX_HEALTH, MAX_HEALTH);
        setBase(Attributes.MOVEMENT_SPEED, completed ? COMPLETE_SPEED : INCOMPLETE_SPEED);
        setBase(Attributes.ATTACK_DAMAGE, completed ? COMPLETE_DAMAGE : INCOMPLETE_DAMAGE);
        setBase(Attributes.SCALE, 1.0); // older saves were 1.2x
        setBase(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, EXPLOSION_KNOCKBACK_RESISTANCE);
        xpReward = completed ? COMPLETE_XP : 0;
    }

    private void setBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    // ---- Owner --------------------------------------------------------------------------------

    public void setOwner(Mob owner) {
        this.owner = EntityReference.of(owner);
    }

    public @Nullable Mob getOwner() {
        return EntityReference.get(owner, level(), Mob.class);
    }

    public boolean isOwnedBy(Mob mob) {
        return owner != null && owner.matches(mob);
    }

    @Override
    protected boolean considersEntityAsAlly(Entity entity) {
        if (super.considersEntityAsAlly(entity)) {
            return true;
        }
        return (entity.is(EntityTypeTags.RAIDERS) || entity.is(EntityTypeTags.ILLAGER_FRIENDS) || entity instanceof BrassGolem)
                && getTeam() == null && entity.getTeam() == null;
    }

    /** Takes its Artificer's target. */
    private static final class CopyOwnerTargetGoal extends TargetGoal {
        private final Puppeted puppeted;
        private final TargetingConditions copyConditions = TargetingConditions.forNonCombat().ignoreLineOfSight().ignoreInvisibilityTesting();

        CopyOwnerTargetGoal(Puppeted puppeted) {
            super(puppeted, false);
            this.puppeted = puppeted;
            setFlags(java.util.EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            Mob master = puppeted.getOwner();
            return master != null && master.getTarget() != null && master.getTarget() != puppeted.getTarget()
                    && canAttack(master.getTarget(), copyConditions);
        }

        @Override
        public void start() {
            Mob master = puppeted.getOwner();
            if (master != null) {
                puppeted.setTarget(master.getTarget());
            }
            super.start();
        }
    }

    // ---- Ticking, loot, sounds ----------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            if (isAlive()) {
                sparks.tick(this, isCompleted() ? SPARKS_COMPLETE : SPARKS_INCOMPLETE);
            }
            return;
        }
        updateState();
        upgradeSound.tick(this);
        // Dies with its Artificer (or once it has been gone for 2 s), as the drones do; completed or not.
        if (owner != null && tickCount % 10 == 0 && isAlive()) {
            Mob host = getOwner();
            if (host != null && host.isAlive()) {
                hostMissingTicks = 0;
            } else if (host != null || (hostMissingTicks += 10) >= HOST_MISSING_GRACE) {
                kill((ServerLevel) level());
            }
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
        if (id == 3) { // death
            SummonFx.spawnDebris(this, DEBRIS_DEATH_MIN, DEBRIS_DEATH_MAX);
        }
        super.handleEntityEvent(id);
    }

    /** Advancement "String Technocracy": the player's 15th Puppeted kill (any state; the game's own kill count). */
    public static final int STRING_TECHNOCRACY_KILLS = 15;
    public static final Identifier STRING_TECHNOCRACY = Identifier.fromNamespaceAndPath(RealmRaids.MOD_ID, "adventure/string_technocracy");

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player && level() instanceof ServerLevel level
                && player.getStats().getValue(net.minecraft.stats.Stats.ENTITY_KILLED.get(getType())) >= STRING_TECHNOCRACY_KILLS) {
            net.minecraft.advancements.AdvancementHolder advancement = level.getServer().getAdvancements().get(STRING_TECHNOCRACY);
            if (advancement != null) {
                player.getAdvancements().award(advancement, "kills");
            }
        }
    }

    /** Only a completed Puppeted drops anything. */
    @Override
    protected void dropFromLootTable(ServerLevel level, DamageSource source, boolean causedByPlayer) {
        if (isCompleted()) {
            dropFromLootTable(level, source, causedByPlayer, COMPLETE_LOOT);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isCompleted() ? RaidSounds.PUPPETED_AMBIENT_COMPLETE : RaidSounds.PUPPETED_AMBIENT_INCOMPLETE;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return RaidSounds.PUPPETED_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return RaidSounds.PUPPETED_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(RaidSounds.PUPPETED_STEP, 0.15f, 1.0f);
    }

    // ---- Save ---------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Completed", isCompleted());
        output.putByte("Variant", entityData.get(VARIANT));
        EntityReference.store(owner, output, "Owner");
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        boolean completed = input.getBooleanOr("Completed", false);
        entityData.set(COMPLETED, completed);
        entityData.set(VARIANT, input.getByteOr("Variant", (byte) 0));
        applyStats(completed); // before super, so the saved health fits the maximum
        super.readAdditionalSaveData(input);
        owner = EntityReference.read(input, "Owner");
        applyStats(isCompleted()); // again: the save's own attribute values (older versions) replaced the bases
        updateState(); // older saves: the state follows the health from now on
    }
}
