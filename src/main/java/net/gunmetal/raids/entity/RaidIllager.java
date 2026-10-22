package net.gunmetal.raids.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

/**
 * Shared base for the Realm Raids illagers: vanilla raider behavior (joins raids, walks into the
 * village when it has no target, celebrates), pillager sounds, and targets players, villagers, iron
 * golems and Guard Villagers guards. Their weapons are part of the mob and never drop (the loot
 * tables in data/gunmetal/loot_table/entities/ decide what drops).
 */
public abstract class RaidIllager extends AbstractIllager implements AnimSignalled {
    /** Synced animation state (see AnimSignalled) and a shot counter (the client times its recoil from its changes). */
    private static final EntityDataAccessor<Byte> ANIM_STATE = SynchedEntityData.defineId(RaidIllager.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> SHOT_COUNT = SynchedEntityData.defineId(RaidIllager.class, EntityDataSerializers.INT);
    private static final Identifier GUARD = Identifier.fromNamespaceAndPath("guardvillagers", "guard");
    /** Backline mobs glow once this few raiders (or fewer) are left alive in the raid. */
    public static final int GLOW_WHEN_RAIDERS_LEFT = 3;

    /** Server: what the attack goal says it is doing (used by animStateNow while it has a target). */
    protected int goalAnimState;
    /** Client: tickCount when the last shot was seen. */
    private int clientLastShotTick = Integer.MIN_VALUE;

    protected RaidIllager(EntityType<? extends AbstractIllager> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ANIM_STATE, (byte) 0);
        builder.define(SHOT_COUNT, 0);
    }

    // ---- Animation signals (AnimSignalled) ----------------------------------------------------

    /** Server, every tick: the state the model should show now (0 = idle). */
    protected abstract int animStateNow();

    /** Called by attack goals. */
    public void setGoalAnimState(int state) {
        goalAnimState = state;
    }

    /** Server: a shot was just fired (restarts the model's shot timer). */
    public void markShot() {
        entityData.set(SHOT_COUNT, entityData.get(SHOT_COUNT) + 1);
    }

    @Override
    public int animState() {
        return entityData.get(ANIM_STATE);
    }

    @Override
    public float secondsSinceShot(float partialTick) {
        if (clientLastShotTick == Integer.MIN_VALUE) {
            return SHOT_TIMER_CAP;
        }
        return Math.min(SHOT_TIMER_CAP, (tickCount - clientLastShotTick + partialTick) / 20f);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        // tickCount > 0: not the initial sync when the mob comes into view.
        if (SHOT_COUNT.equals(key) && level().isClientSide() && tickCount > 0) {
            clientLastShotTick = tickCount;
        }
    }

    public static boolean isGuard(LivingEntity entity) {
        return GUARD.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    /** Players (not creative/spectator), iron golems and guards: what a Trooper counts as a threat. */
    public static boolean isThreat(LivingEntity entity) {
        if (entity instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        return entity instanceof IronGolem || isGuard(entity);
    }

    /** Whether targets have to be seen to be picked (false = artillery that knows where you are). */
    protected boolean targetsNeedSight() {
        return true;
    }

    /** Glows when the raid is down to its last few raiders, so it can't stall the wave unseen. */
    protected boolean glowsAsLastRaider() {
        return false;
    }

    /** Called from registerGoals (runs inside the constructor: don't rely on subclass fields here). */
    protected abstract void addAttackGoals();

    @Override
    protected void registerGoals() {
        super.registerGoals(); // raid goals: banner pickup, walk to the raid, move through village, celebrate
        goalSelector.addGoal(0, new FloatGoal(this));
        addAttackGoals();
        goalSelector.addGoal(8, new RandomStrollGoal(this, 0.6));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 15.0f, 1.0f));
        goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 15.0f));

        boolean sight = targetsNeedSight();
        targetSelector.addGoal(1, new HurtByTargetGoal(this, Raider.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, sight));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, sight));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, sight));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, PathfinderMob.class, 10, sight, false,
                (entity, level) -> isGuard(entity)));
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                  EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
        populateDefaultEquipmentSlots(level.getRandom(), difficulty);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0f);
        }
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
        setLeftHanded(false); // vanilla makes 5% left-handed; the custom poses assume the right hand
        return data;
    }

    /** A stack of a Realm Arms item by id (gunmetal:<path>), or empty if it isn't there. */
    public static ItemStack realmArms(String path) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("gunmetal", path));
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        entityData.set(ANIM_STATE, (byte) (isCelebrating() ? 0 : animStateNow()));
        if (glowsAsLastRaider() && tickCount % 20 == 0) {
            Raid raid = getCurrentRaid();
            if (raid != null && !raid.isOver() && raid.getTotalRaidersAlive() <= GLOW_WHEN_RAIDERS_LEFT) {
                addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
            }
        }
    }

    @Override
    public void applyRaidBuffs(ServerLevel level, int wave, boolean isCaptain) {
    }

    @Override
    public boolean canBeLeader() {
        return false;
    }

    @Override
    public SoundEvent getCelebrateSound() {
        return SoundEvents.PILLAGER_CELEBRATE;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PILLAGER_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PILLAGER_HURT;
    }
}
