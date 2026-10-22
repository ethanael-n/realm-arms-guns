package net.gunmetal.raids.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.gunmetal.arms.entity.RealmBulletEntity;
import net.gunmetal.arms.guns.CustomGunner;
import net.gunmetal.arms.guns.GunItem;
import net.gunmetal.arms.registry.RealmParticles;
import net.gunmetal.raids.RealmRaids;
import net.gunmetal.raids.registry.RaidParticles;
import net.gunmetal.raids.registry.RaidSounds;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * The Artificer's flying drone (bee model and motion; one model and texture, parts shown by level). Hovers
 * above and around its target and fires a copper pistol at it (its bullets only hit players, iron
 * golems and its target). Lives until its Artificer dies (or can't be found for 2 s), then dies with
 * it; a drone without an Artificer (spawn egg) lives on. Counts as a raider for tags
 * (#minecraft:raiders: other raiders don't fight it, the Artificer's pulse doesn't stun it) but
 * doesn't join the raid. Gives off the Artificer's sparks (x0.5 / x0.75 / x1 by level).
 *
 * Levels (each drone summon raises every existing drone one level, max 3: new model parts, full health at
 * the new maximum, brass sparks, anvil sound, the summon marker):
 *   level    scale  health  shot every  spread  damage to a Hard player (before armor)
 *   1        1.0    4       6.7 s       3.5 deg 8
 *   2        1.0    8       2.2 s       3       8
 *   3        1.0    16      1 s         2.5     8; every 4th shot is a musketoon blast of 3 pellets x 6, spread 2
 * Loot by level: loot_table/entities/drone_1, drone_2, drone_3.
 */
public class ArtificerDrone extends Monster implements CustomGunner {
    // ---- Tuning -------------------------------------------------------------------------
    public static final int MAX_LEVEL = 3;
    /** Per level 1, 2, 3: max health, scale, ticks between shots, aim spread (degrees, std. dev.), spark multiplier. */
    private static final double[] LEVEL_HEALTH = {4.0, 8.0, 16.0};
    private static final double[] LEVEL_SCALE = {1.0, 1.0, 1.0}; // 1.4.7: no size growth (was 1 / 1.2 / 1.4)
    private static final int[] FIRE_INTERVAL = {134, 44, 20}; // 1.5.1: levels 1 and 2 fire 50% / 25% slower (was 67, 33)
    private static final float[] LEVEL_SPREAD = {3.5f, 3.0f, 2.5f};
    private static final float[] LEVEL_SPARKS = {0.5f, 0.75f, 1.0f};
    /** Damage a player on Hard takes from a bullet / a blast pellet, before armor (Hard multiplies by 1.5). */
    public static final float SHOT_DAMAGE_ON_HARD = 8.0f, PELLET_DAMAGE_ON_HARD = 6.0f;
    /** Level 3: after this many normal shots, the next is a blast of BLAST_PELLETS with BLAST_SPREAD. */
    public static final int SHOTS_BEFORE_BLAST = 3, BLAST_PELLETS = 3;
    public static final float BLAST_SPREAD = 2.0f;
    /** Without its Artificer for this long (ticks, checked every 10), it dies. */
    public static final int HOST_MISSING_GRACE = 40;
    /** Fires at targets in view up to this far. */
    public static final double FIRE_RANGE = 24.0;
    /** Hovers this far from its target (horizontally) and this high above its feet. */
    public static final double HOVER_DISTANCE_MIN = 3.0, HOVER_DISTANCE_MAX = 11.0, HOVER_HEIGHT_MIN = 2.5, HOVER_HEIGHT_MAX = 4.5;
    /**
     * Swoop: every 14 s (each drone on its own timer; the first 7-21 s after it gets a target) it dives for 1 s to about 1 block from the target,
     * at 60% of its height: within reach of a melee mob (player-sized guards included) or a player.
     */
    public static final int SWOOP_INTERVAL = 280, SWOOP_TICKS = 20;
    public static final double SWOOP_DISTANCE = 1.0;
    /** Flight speed (blocks/tick) and how quickly it turns toward where it wants to go. */
    public static final double FLY_SPEED = 0.3, STEERING = 0.25;
    /** Shot volume (heard out to 16 x this blocks), for both shot types. */
    public static final float FIRE_VOLUME = 1.25f;
    /** Debris pieces (min, max): on a direct hit, on death. */
    public static final int DEBRIS_HIT_MIN = 1, DEBRIS_HIT_MAX = 1, DEBRIS_DEATH_MIN = 2, DEBRIS_DEATH_MAX = 3;
    // -------------------------------------------------------------------------------------

    private static final EntityDataAccessor<Byte> LEVEL = SynchedEntityData.defineId(ArtificerDrone.class, EntityDataSerializers.BYTE);

    public final AnimationState flyAnimationState = new AnimationState();
    private final SummonSparks sparks = new SummonSparks();
    private @Nullable EntityReference<Mob> owner;
    private int hostMissingTicks;
    private final SummonFx.DelayedSound upgradeSound = new SummonFx.DelayedSound();
    /** Swoops: ticks until the next one, and ticks left of the current one. */
    private int swoopTimer = SWOOP_INTERVAL, swoopTicks;
    private int fireCooldown;
    private int shotsSinceBlast;
    private int pendingTargetId = -1;
    /** Set while the blast is being fired (for pelletCount/spreadOverride/customizeBullet). */
    private boolean firingBlast;
    private final ItemStack blastGun = RaidIllager.realmArms("copper_musketoon");

    public ArtificerDrone(EntityType<? extends ArtificerDrone> type, Level level) {
        super(type, level);
        this.moveControl = new DroneMoveControl(this);
        this.xpReward = 3;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, LEVEL_HEALTH[0])
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LEVEL, (byte) 1);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new DroneAttackGoal());
        goalSelector.addGoal(8, new DroneIdleGoal());
        targetSelector.addGoal(1, new HurtByTargetGoal(this, Raider.class).setAlertOthers());
        targetSelector.addGoal(2, new CopyOwnerTargetGoal(this));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, PathfinderMob.class, 10, true, false,
                (entity, level) -> RaidIllager.isGuard(entity)));
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.MAINHAND, RaidIllager.realmArms("copper_pistol"));
        setDropChance(EquipmentSlot.MAINHAND, 0.0f);
    }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
                                                                     DifficultyInstance difficulty,
                                                                     net.minecraft.world.entity.EntitySpawnReason reason,
                                                                     @Nullable net.minecraft.world.entity.SpawnGroupData groupData) {
        populateDefaultEquipmentSlots(level.getRandom(), difficulty);
        fireCooldown = 20 + random.nextInt(40);
        return super.finalizeSpawn(level, difficulty, reason, groupData);
    }

    // ---- Owner / lifetime -----------------------------------------------------------------

    public void setOwner(Mob owner) {
        this.owner = EntityReference.of(owner);
    }

    public @Nullable Mob getOwner() {
        return EntityReference.get(owner, level(), Mob.class);
    }

    public boolean isOwnedBy(Mob mob) {
        return owner != null && owner.matches(mob);
    }

    // ---- Level ------------------------------------------------------------------------------

    public int droneLevel() {
        return Mth.clamp(entityData.get(LEVEL), 1, MAX_LEVEL);
    }

    private void setDroneLevel(int level) {
        entityData.set(LEVEL, (byte) Mth.clamp(level, 1, MAX_LEVEL));
        int index = droneLevel() - 1;
        AttributeInstance scale = getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(LEVEL_SCALE[index]);
        }
        AttributeInstance health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(LEVEL_HEALTH[index]);
        }
    }

    /**
     * Up a level and back to full health (at the new maximum), with brass sparks, the anvil sound and
     * the summon marker. At level 3 it only heals.
     */
    public void upgrade() {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (droneLevel() >= MAX_LEVEL) {
            SummonFx.maxLevelRegen(this); // already at the top: Regeneration I for 5 s
            return;
        }
        setDroneLevel(droneLevel() + 1);
        setHealth(getMaxHealth());
        level.sendParticles(RealmParticles.SPARK_BRASS, getX(), getY(0.5), getZ(), 16,
                getBbWidth() * 0.5, getBbHeight() * 0.5, getBbWidth() * 0.5, 0.15);
        level.sendParticles(RaidParticles.SUMMON_MARKER, true, true, getX(), getY(0.5), getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        upgradeSound.schedule(RaidSounds.DRONE_UPGRADE, random);
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

    /** Loot by level: gunmetal:entities/drone_<level>. */
    @Override
    protected void dropFromLootTable(ServerLevel level, DamageSource source, boolean causedByPlayer) {
        dropFromLootTable(level, source, causedByPlayer, ResourceKey.create(Registries.LOOT_TABLE,
                Identifier.fromNamespaceAndPath(RealmRaids.MOD_ID, "entities/drone_" + droneLevel())));
    }

    public int fireInterval() {
        return FIRE_INTERVAL[droneLevel() - 1];
    }

    // ---- Ticking ----------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        setNoGravity(true);
        if (level().isClientSide()) {
            flyAnimationState.startIfStopped(tickCount);
            if (isAlive()) {
                sparks.tick(this, LEVEL_SPARKS[droneLevel() - 1]);
            }
        } else {
            upgradeSound.tick(this);
        }
        if (!level().isClientSide() && owner != null && tickCount % 10 == 0 && isAlive()) {
            Mob host = getOwner();
            if (host != null && host.isAlive()) {
                hostMissingTicks = 0;
            } else if (host != null || (hostMissingTicks += 10) >= HOST_MISSING_GRACE) {
                kill((ServerLevel) level()); // its Artificer died (or has been gone for 2 s)
            }
        }
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected boolean considersEntityAsAlly(Entity entity) {
        if (super.considersEntityAsAlly(entity)) {
            return true;
        }
        return (entity.is(EntityTypeTags.RAIDERS) || entity.is(EntityTypeTags.ILLAGER_FRIENDS) || entity instanceof BrassGolem
                || entity instanceof Puppeted)
                && getTeam() == null && entity.getTeam() == null;
    }

    // ---- Shooting -----------------------------------------------------------------------------

    /** A copper pistol shot, or (level 3, every 4th shot) a musketoon blast. Spread is applied per bullet (spreadOverride). */
    private void fireAt(LivingEntity target) {
        boolean blast = droneLevel() >= MAX_LEVEL && shotsSinceBlast >= SHOTS_BEFORE_BLAST && blastGun.getItem() instanceof GunItem;
        ItemStack stack = blast ? blastGun : getMainHandItem();
        if (!(stack.getItem() instanceof GunItem gun)) {
            return;
        }
        Vec3 direction = gun.aimAt(this, target);
        pendingTargetId = target.getId();
        firingBlast = blast;
        GunItem.setLoaded(stack, true);
        if (blast) {
            gun.mobUse(this, stack, direction, gun.smokeOffsetFor(this, InteractionHand.MAIN_HAND));
            shotsSinceBlast = 0;
        } else {
            gun.mobUse(this, InteractionHand.MAIN_HAND, direction);
            if (droneLevel() >= MAX_LEVEL) {
                ++shotsSinceBlast;
            }
        }
        firingBlast = false;
        swing(InteractionHand.MAIN_HAND);
    }

    @Override
    public int pelletCount(ItemStack gun) {
        return firingBlast ? BLAST_PELLETS : 1;
    }

    @Override
    public float spreadOverride(ItemStack gun) {
        return firingBlast ? BLAST_SPREAD : LEVEL_SPREAD[droneLevel() - 1];
    }

    /**
     * Fixed damage (no gun falloff): Hard multiplies bullet damage against players by 1.5, so the
     * bullet carries 2/3 of the Hard figure. A blast's pellets share the shot's damage, so the shot
     * carries pellets x per-pellet damage.
     */
    @Override
    public void customizeBullet(RealmBulletEntity bullet, ItemStack gun) {
        bullet.setGhostExcept(pendingTargetId);
        bullet.gunType = null;
        bullet.mobDamageMultiplier = 1.0f;
        bullet.damage = firingBlast ? PELLET_DAMAGE_ON_HARD / 1.5f * BLAST_PELLETS : SHOT_DAMAGE_ON_HARD / 1.5f;
    }

    @Override
    public float gunFireVolume() {
        return FIRE_VOLUME;
    }

    // ---- Movement -----------------------------------------------------------------------------

    /** Flies straight at the wanted position, turning smoothly; collides with blocks. */
    private static final class DroneMoveControl extends MoveControl {
        DroneMoveControl(ArtificerDrone drone) {
            super(drone);
        }

        @Override
        public void tick() {
            if (operation != Operation.MOVE_TO) {
                mob.setDeltaMovement(mob.getDeltaMovement().scale(0.9));
                return;
            }
            Vec3 offset = new Vec3(wantedX - mob.getX(), wantedY - mob.getY(), wantedZ - mob.getZ());
            double distance = offset.length();
            if (distance < 0.5) {
                operation = Operation.WAIT;
                return;
            }
            Vec3 desired = offset.scale(FLY_SPEED * speedModifier / distance);
            Vec3 velocity = mob.getDeltaMovement();
            mob.setDeltaMovement(velocity.add(desired.subtract(velocity).scale(STEERING)));
            if (mob.getTarget() == null) {
                float yaw = (float) (Mth.atan2(offset.z, offset.x) * Mth.RAD_TO_DEG) - 90.0f;
                mob.setYRot(rotlerp(mob.getYRot(), yaw, 20.0f));
                mob.yBodyRot = mob.getYRot();
            }
        }
    }

    private boolean canFlyTo(Vec3 pos) {
        if (!level().noCollision(this, getBoundingBox().move(pos.x - getX(), pos.y - getY(), pos.z - getZ()))) {
            return false;
        }
        return level().clip(new ClipContext(position(), pos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
                .getType() == HitResult.Type.MISS;
    }

    private void faceTarget(LivingEntity target) {
        getLookControl().setLookAt(target, 30.0f, 30.0f);
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** Circles above its target at a distance and shoots whenever its gun is ready and it can see the target. */
    private class DroneAttackGoal extends Goal {
        private int repositionTimer;

        DroneAttackGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public void start() {
            repositionTimer = 0;
            swoopTicks = 0;
            swoopTimer = SWOOP_INTERVAL / 2 + random.nextInt(SWOOP_INTERVAL); // drones summoned together don't dive together
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) {
                return;
            }
            faceTarget(target);
            if (swoopTicks > 0) {
                // Diving at the target: aim beside its head, re-aimed each tick as it moves.
                --swoopTicks;
                Vec3 away = new Vec3(getX() - target.getX(), 0, getZ() - target.getZ());
                away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
                getMoveControl().setWantedPosition(target.getX() + away.x * SWOOP_DISTANCE, target.getY(0.6),
                        target.getZ() + away.z * SWOOP_DISTANCE, 1.0);
                if (swoopTicks == 0) {
                    repositionTimer = 0; // back up to a hover spot
                }
            } else if (--swoopTimer <= 0) {
                swoopTimer = SWOOP_INTERVAL - SWOOP_TICKS; // one every 14 s, the swoop included
                swoopTicks = SWOOP_TICKS;
            } else if (--repositionTimer <= 0 || !getMoveControl().hasWanted()) {
                repositionTimer = 30 + random.nextInt(30);
                for (int attempt = 0; attempt < 8; ++attempt) {
                    double angle = random.nextDouble() * Math.PI * 2.0;
                    double distance = HOVER_DISTANCE_MIN + random.nextDouble() * (HOVER_DISTANCE_MAX - HOVER_DISTANCE_MIN);
                    double height = HOVER_HEIGHT_MIN + random.nextDouble() * (HOVER_HEIGHT_MAX - HOVER_HEIGHT_MIN);
                    Vec3 spot = new Vec3(target.getX() + Math.cos(angle) * distance, target.getY() + height,
                            target.getZ() + Math.sin(angle) * distance);
                    if (canFlyTo(spot)) {
                        getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 1.0);
                        break;
                    }
                    if (attempt == 7) {
                        // Boxed in: head straight for the air above the target.
                        getMoveControl().setWantedPosition(target.getX(), target.getY() + HOVER_HEIGHT_MIN, target.getZ(), 1.0);
                    }
                }
            }
            if (fireCooldown > 0) {
                --fireCooldown;
            } else if (distanceTo(target) <= FIRE_RANGE && getSensing().hasLineOfSight(target) && GunItem.canUse(ArtificerDrone.this)) {
                fireAt(target);
                fireCooldown = fireInterval();
            }
        }
    }

    /** No target: hover near the Artificer (or drift about). */
    private class DroneIdleGoal extends Goal {
        DroneIdleGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return getTarget() == null && !getMoveControl().hasWanted() && random.nextInt(reducedTickDelay(10)) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            Mob master = getOwner();
            Vec3 center = master != null ? master.position().add(0.0, 2.5, 0.0) : position();
            for (int attempt = 0; attempt < 4; ++attempt) {
                Vec3 spot = center.add((random.nextDouble() - 0.5) * 6.0, (random.nextDouble() - 0.5) * 2.0,
                        (random.nextDouble() - 0.5) * 6.0);
                if (canFlyTo(spot)) {
                    getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 0.5);
                    return;
                }
            }
        }
    }

    /** Takes its Artificer's target (vex behavior). */
    private static final class CopyOwnerTargetGoal extends TargetGoal {
        private final ArtificerDrone drone;
        private final TargetingConditions copyConditions = TargetingConditions.forNonCombat().ignoreLineOfSight().ignoreInvisibilityTesting();

        CopyOwnerTargetGoal(ArtificerDrone drone) {
            super(drone, false);
            this.drone = drone;
        }

        @Override
        public boolean canUse() {
            Mob master = drone.getOwner();
            return master != null && master.getTarget() != null && master.getTarget() != drone.getTarget()
                    && canAttack(master.getTarget(), copyConditions);
        }

        @Override
        public void start() {
            Mob master = drone.getOwner();
            if (master != null) {
                drone.setTarget(master.getTarget());
            }
            super.start();
        }
    }

    // ---- Sounds / save --------------------------------------------------------------------------

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return RaidSounds.DRONE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return RaidSounds.DRONE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return RaidSounds.DRONE_DEATH;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("DroneLevel", droneLevel());
        output.putInt("ShotsSinceBlast", shotsSinceBlast);
        EntityReference.store(owner, output, "Owner");
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        setDroneLevel(input.getIntOr("DroneLevel", 1)); // before super, so the saved health fits the level's maximum
        super.readAdditionalSaveData(input);
        shotsSinceBlast = input.getIntOr("ShotsSinceBlast", 0);
        owner = EntityReference.read(input, "Owner");
    }
}
