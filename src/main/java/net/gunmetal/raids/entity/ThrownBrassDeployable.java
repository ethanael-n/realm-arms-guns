package net.gunmetal.raids.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.gunmetal.raids.item.GolemLoad;
import net.gunmetal.raids.registry.RaidEntities;
import net.gunmetal.raids.registry.RaidItems;

/**
 * A thrown Brass Deployable (drawn as its item). Flies like a snowball. Hitting a mob doesn't stop
 * it: it drops straight down from there. On a block it stops where it hit (floor, wall or ceiling),
 * waits 3 s, then becomes a deployed brass golem in the open block on that side. Water doesn't
 * stop it (it sinks to the bottom).
 */
public class ThrownBrassDeployable extends ThrowableItemProjectile {
    public static final int DEPLOY_DELAY_TICKS = 60;

    private static final EntityDataAccessor<Boolean> LANDED = SynchedEntityData.defineId(ThrownBrassDeployable.class, EntityDataSerializers.BOOLEAN);

    private int landedTicks;
    private BlockPos spawnPos = BlockPos.ZERO;
    private boolean droppedFromMob;

    public ThrownBrassDeployable(EntityType<? extends ThrownBrassDeployable> type, Level level) {
        super(type, level);
    }

    public ThrownBrassDeployable(ServerLevel level, LivingEntity thrower, ItemStack stack) {
        super(RaidEntities.THROWN_BRASS_DEPLOYABLE, thrower, level, stack);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LANDED, false);
    }

    @Override
    protected Item getDefaultItem() {
        return RaidItems.BRASS_DEPLOYABLE;
    }

    public boolean hasLanded() {
        return entityData.get(LANDED);
    }

    @Override
    public void tick() {
        if (!hasLanded()) {
            super.tick();
            return;
        }
        // Sitting where it landed: no movement, just the countdown.
        baseTick();
        setDeltaMovement(Vec3.ZERO);
        if (level() instanceof ServerLevel level && ++landedTicks >= DEPLOY_DELAY_TICKS) {
            deploy(level);
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return !droppedFromMob && super.canHitEntity(entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        // Doesn't hurt; falls straight down from here.
        droppedFromMob = true;
        Vec3 velocity = getDeltaMovement();
        setDeltaMovement(0.0, Math.min(0.0, velocity.y), 0.0);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        Vec3 at = result.getLocation();
        setPos(at.x, at.y, at.z);
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(true);
        spawnPos = result.getBlockPos().relative(result.getDirection());
        entityData.set(LANDED, true);
    }

    private void deploy(ServerLevel level) {
        DeployedGolem golem = RaidEntities.DEPLOYED_GOLEM.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (golem != null) {
            golem.snapTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, getYRot(), 0.0f);
            Entity owner = getOwner();
            golem.deploy(GolemLoad.of(getItem()), owner instanceof LivingEntity living ? living : null);
            level.addFreshEntity(golem);
        }
        discard();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Landed", hasLanded());
        output.putInt("LandedTicks", landedTicks);
        output.putLong("SpawnPos", spawnPos.asLong());
        output.putBoolean("DroppedFromMob", droppedFromMob);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(LANDED, input.getBooleanOr("Landed", false));
        landedTicks = input.getIntOr("LandedTicks", 0);
        spawnPos = BlockPos.of(input.getLongOr("SpawnPos", 0L));
        droppedFromMob = input.getBooleanOr("DroppedFromMob", false);
    }
}
