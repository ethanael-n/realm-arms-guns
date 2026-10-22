package net.gunmetal.arms.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.gunmetal.arms.guns.GunConfig;
import org.jetbrains.annotations.Nullable;
import net.gunmetal.arms.guns.GunType;
import net.gunmetal.arms.registry.RealmSounds;

import java.util.Optional;

import static net.gunmetal.arms.RealmArmsWeapons.MOD_ID;

/**
 * Bullet flight, hits and damage (originally Musket Mod Reloaded's BulletEntity, MIT license,
 * by Lufurrius; ported so Realm Arms no longer depends on it). Flies in a straight line with drag
 * and gravity, slows sharply in water, deals damage x the energy it has left after water,
 * breaks glass and pops flower pots, and whistles past players it flies by.
 */
public class GunBullet extends AbstractHurtingProjectile {
    public static final EntityDataAccessor<Float> INITIAL_SPEED = SynchedEntityData.defineId(GunBullet.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> DROP_REDUCTION = SynchedEntityData.defineId(GunBullet.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Byte> PELLET_COUNT = SynchedEntityData.defineId(GunBullet.class, EntityDataSerializers.BYTE);
    /** Lifetime override in ticks (0 = normal), synced so the client keeps drawing long flights too. */
    public static final EntityDataAccessor<Integer> LIFETIME_TICKS = SynchedEntityData.defineId(GunBullet.class, EntityDataSerializers.INT);
    /**
     * "Ghost" bullets (synced, so the client draws them the same way): when set, the bullet only hits
     * players, iron golems and the entity with this id, and passes through everything else.
     * GHOST_OFF = normal bullet; -1 = no extra entity.
     */
    public static final EntityDataAccessor<Integer> GHOST_EXCEPT = SynchedEntityData.defineId(GunBullet.class, EntityDataSerializers.INT);
    public static final int GHOST_OFF = Integer.MIN_VALUE;
    /**
     * Synced: this bullet flies straight through entities in the entity type tag gunmetal:shell_pass_through
     * (and its burst spares them). Set for Bombardier shells, so they pass through Siege Balloons (1.0.3).
     */
    public static final EntityDataAccessor<Boolean> PASSES_TAGGED = SynchedEntityData.defineId(GunBullet.class, EntityDataSerializers.BOOLEAN);
    public static final TagKey<EntityType<?>> SHELL_PASS_THROUGH = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "shell_pass_through"));
    public static final TagKey<Block> DESTROYED_BY_BULLETS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, "destroyed_by_bullets"));
    public static final TagKey<Block> DROPPED_BY_BULLETS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, "dropped_by_bullets"));
    public static final ResourceKey<DamageType> BULLET_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "bullet"));
    public static final double GRAVITY = 0.05;
    public static final double AIR_FRICTION = 0.99;
    public static final double WATER_FRICTION = 0.6;
    public static final short LIFETIME = 100;
    public static final int HIT_PARTICLE_COUNT = 5;

    public float damage;
    /**
     * Server-side overrides for special shooters (e.g. Realm Raids illagers, set through
     * {@link net.gunmetal.arms.guns.CustomGunner}). Negative = the normal behavior.
     *   mobDamageMultiplier - replaces GunType.MOB_DAMAGE_MULTIPLIER for this bullet's direct hit
     *   knockback           - extra knockback on a direct hit, in arrow Punch levels (Punch II = 2)
     */
    public float mobDamageMultiplier = -1f;
    public float knockback = 0f;
    /** Server only: sets whatever it hits on fire for this long even if the bullet isn't burning (negative = off). */
    public float igniteOnHitSeconds = -1f;
    /** Server only: whether it shatters glass, lights TNT and so on (Siege Balloon shrapnel doesn't). */
    public boolean breaksBlocks = true;
    /** Server only: damage type for this bullet's direct hit instead of gunmetal:bullet (null = normal). */
    public @Nullable ResourceKey<DamageType> damageTypeOverride;
    /** Server only: extra behavior on hit / impact / expiry (see BulletPayload), or null. */
    public @Nullable BulletPayload payload;
    /**
     * Server only: reworks this bullet's direct-hit damage per target (after the PvP/mob and range
     * multipliers), or null. Used by add-ons for target-dependent damage (Realm Raids' Sharpshooter).
     */
    public @Nullable DamageModifier damageModifier;

    @FunctionalInterface
    public interface DamageModifier {
        float modify(Entity target, float damage, DamageSource source);
    }
    private boolean impacted;
    public boolean touchedWater;
    public float distanceTravelled;
    public short tickCounter;
    private boolean packetSpeedReceived;

    protected GunBullet(EntityType<? extends GunBullet> type, Level level) {
        super(type, level);
    }

    /** True before the bullet's first tick (the renderer skips that frame so it doesn't flash at the muzzle). */
    public boolean isFirstTick() {
        return tickCounter == 0;
    }

    public void discardOnNextTick() {
        tickCounter = Short.MAX_VALUE - 1;
    }

    /** Overrides the lifetime (ticks) and removes the distance limit; 0 restores the normal limits. */
    public void setLifetimeTicks(int ticks) {
        entityData.set(LIFETIME_TICKS, Math.max(0, ticks));
    }

    public int lifetimeTicks() {
        return entityData.get(LIFETIME_TICKS);
    }

    /** Makes this a ghost bullet that only hits players, iron golems and the given entity id (-1 = none). */
    public void setGhostExcept(int entityId) {
        entityData.set(GHOST_EXCEPT, entityId);
    }

    private static boolean isFinite(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    /** Remaining kinetic energy as a share of the starting energy (drops with drag and water). */
    public float calculateEnergyFraction() {
        double maxEnergy = Math.pow(entityData.get(INITIAL_SPEED), 2.0);
        double energy = getDeltaMovement().lengthSqr();
        if (maxEnergy < energy) {
            maxEnergy = energy;
        }
        return (float) (energy / maxEnergy);
    }

    public int pelletCount() {
        byte count = entityData.get(PELLET_COUNT);
        return count > 1 ? count : 1;
    }

    /** Pellet guns only play a share of the hit/fly-by sounds, so a blast isn't deafening. */
    public boolean soundEffectRoll() {
        int count = pelletCount();
        return count == 1 || random.nextFloat() < 1.5f / count;
    }

    public DamageSource getDamageSource() {
        Entity attacker = getOwner() != null ? getOwner() : this;
        return level().damageSources().source(damageTypeOverride != null ? damageTypeOverride : BULLET_DAMAGE, this, attacker);
    }

    /** @param bulletSpeed blocks per second */
    public void setVelocity(float bulletSpeed, Vec3 direction) {
        float tickSpeed = bulletSpeed / 20.0f;
        setInitialSpeed(tickSpeed);
        setDeltaMovement(direction.scale(tickSpeed));
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    /** Server only: whether a player fired this (worked out on the first tick, once the owner is known). */
    private @Nullable Boolean playerFired;

    /** Non-player bullets (mobs, guards, dispensers) expire sooner on the server; see GunConfig. */
    private boolean shortLived() {
        if (level().isClientSide()) {
            return false;
        }
        if (playerFired == null) {
            playerFired = getOwner() instanceof Player;
        }
        return !playerFired;
    }

    @Override
    public void tick() {
        ++tickCounter;
        boolean shortLived = shortLived();
        int lifetimeOverride = lifetimeTicks();
        int lifetime = lifetimeOverride > 0 ? lifetimeOverride : shortLived ? GunConfig.MOB_BULLET_LIFETIME : LIFETIME;
        float maxDistance = lifetimeOverride > 0 ? Float.MAX_VALUE
                : shortLived ? GunConfig.MOB_BULLET_MAX_DISTANCE : GunConfig.BULLET_MAX_DISTANCE;
        if (tickCounter > lifetime || distanceTravelled > maxDistance) {
            if (payload != null && !impacted && !level().isClientSide()) {
                payload.onExpire(this, position());
            }
            discard();
            return;
        }
        Level level = level();
        Vec3 velocity = getDeltaMovement();
        if (!isFinite(velocity) || velocity.lengthSqr() > 1.073741824E9) {
            discard();
            return;
        }
        Vec3 from = position();
        Vec3 to = from.add(velocity);
        if (!level.hasChunkAt(BlockPos.containing((Position) from)) || !level.hasChunkAt(BlockPos.containing((Position) to))) {
            discard();
            return;
        }

        Vec3 waterPos = Vec3.ZERO;
        updateFluidInteraction();
        if (wasTouchingWater) {
            waterPos = from;
            velocity = velocity.scale(WATER_FRICTION);
            to = from.add(velocity);
            setDeltaMovement(velocity);
        }

        HitResult hitResult = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hitResult.getType() != HitResult.Type.MISS) {
            to = hitResult.getLocation();
        }
        EntityHitResult entityHitResult = findHitEntity(from, to);
        if (entityHitResult != null) {
            hitResult = entityHitResult;
            to = hitResult.getLocation();
        }

        if (!wasTouchingWater) {
            BlockHitResult fluidHitResult = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
            if (fluidHitResult.getType() == HitResult.Type.BLOCK) {
                FluidState fluid = level.getFluidState(fluidHitResult.getBlockPos());
                double distanceToFluid = fluidHitResult.getLocation().subtract(from).length();
                double distanceToHit = to.subtract(from).length();
                if (fluid.is(FluidTags.WATER)) {
                    wasTouchingWater = true;
                    waterPos = fluidHitResult.getLocation();
                    double speed = velocity.length();
                    double timeInWater = 1.0 - distanceToFluid / speed;
                    double newSpeed = speed * (1.0 - timeInWater + timeInWater * Math.pow(WATER_FRICTION, timeInWater));
                    boolean splash = true;
                    if (hitResult.getType() != HitResult.Type.MISS) {
                        if (distanceToFluid < distanceToHit) {
                            if (distanceToHit < newSpeed) {
                                timeInWater = (distanceToHit - distanceToFluid) / speed;
                                newSpeed = speed * (1.0 - timeInWater + timeInWater * Math.pow(WATER_FRICTION, timeInWater));
                            } else {
                                hitResult = BlockHitResult.miss(null, null, null);
                            }
                        } else {
                            splash = false;
                        }
                    }
                    velocity = velocity.scale(newSpeed / speed);
                    to = from.add(velocity);
                    setDeltaMovement(velocity);
                    if (level.isClientSide() && splash) {
                        double yv = fluidHitResult.getDirection() == Direction.UP ? 0.02 : 0.0;
                        createHitParticles(ParticleTypes.SPLASH, waterPos, new Vec3(0.0, yv, 0.0));
                        playHitSound(RealmSounds.BULLET_WATER_HIT, waterPos);
                    }
                } else if (fluid.is(FluidTags.LAVA) && (hitResult.getType() == HitResult.Type.MISS || distanceToFluid < distanceToHit)) {
                    hitResult = fluidHitResult;
                    to = fluidHitResult.getLocation();
                }
            }
        }
        if (wasTouchingWater) {
            touchedWater = true;
            extinguishFire();
        }
        if (!level.isClientSide()) {
            setSharedFlagOnFire(getRemainingFireTicks() > 0);
        }

        if (hitResult.getType() != HitResult.Type.MISS) {
            if (touchedWater) {
                damage *= calculateEnergyFraction();
            }
            if (!level.isClientSide()) {
                onHit(hitResult);
                if (hitResult instanceof BlockHitResult blockHit && breaksBlocks) {
                    breakBlock(level, blockHit);
                }
                impacted = true;
                if (payload != null) {
                    payload.onImpact(this, hitResult.getLocation());
                }
                discardOnNextTick();
            } else if (hitResult instanceof BlockHitResult blockHit) {
                Vec3 pos = blockHit.getLocation();
                BlockState state = level.getBlockState(blockHit.getBlockPos());
                createHitParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos, Vec3.ZERO);
                if (isOnFire()) {
                    level.addParticle(ParticleTypes.DRIPPING_LAVA, pos.x, pos.y, pos.z, 0.0, 0.01, 0.0);
                }
                playHitSound(state.getSoundType().getBreakSound(), pos);
                discard();
            }
        } else if (level.isClientSide() && !wasTouchingWater && soundEffectRoll()) {
            playFlyBy(from, velocity);
        }

        if (level.isClientSide() && wasTouchingWater) {
            double length = velocity.length();
            if (length > 1.0E-6) {
                Vec3 step = velocity.scale(1.0 / length);
                Vec3 pos = waterPos.add(step.scale(0.5));
                float chance = 1.5f * calculateEnergyFraction() / pelletCount();
                for (; length > 0.5; length -= 1.0) {
                    pos = pos.add(step);
                    if (random.nextFloat() < chance) {
                        level.addParticle(ParticleTypes.BUBBLE, pos.x, pos.y, pos.z, 0.0, 0.0, 0.0);
                    }
                }
            }
        }

        if (!wasTouchingWater) {
            velocity = velocity.scale(AIR_FRICTION);
        }
        double gravity = GRAVITY * (1.0f - entityData.get(DROP_REDUCTION));
        setDeltaMovement(velocity.subtract(0.0, gravity, 0.0));
        setPos(to);
        distanceTravelled += (float) to.subtract(from).length();
    }

    /** Glass shatters, flower pots and chorus flowers drop, TNT is lit. Pellets are less likely to. */
    private void breakBlock(Level level, BlockHitResult hit) {
        float chance = calculateEnergyFraction();
        if (pelletCount() > 1) {
            chance = 1.5f * chance / pelletCount();
        }
        if (random.nextFloat() >= chance) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.TNT)) {
            TntBlock.prime(level, pos);
            level.removeBlock(pos, false);
        } else if (state.is(DESTROYED_BY_BULLETS)) {
            if (level.removeBlock(pos, false)) {
                state.getBlock().destroy(level, pos, state);
            }
        } else if (state.is(DROPPED_BY_BULLETS) && level.removeBlock(pos, false)) {
            Block.dropResources(state, level, pos);
        }
    }

    /** Client: the whiz of a bullet passing close to a player (not the shooter). */
    private void playFlyBy(Vec3 from, Vec3 velocity) {
        double length = velocity.length();
        if (length < 1.0E-6) {
            return;
        }
        Vec3 dir = velocity.scale(1.0 / length);
        float volume = calculateEnergyFraction();
        AABB area = getBoundingBox().expandTowards(velocity).inflate(8.0);
        for (Entity entity : level().getEntities(this, area, e -> e instanceof Player && !e.equals(getOwner()))) {
            Vec3 diff = new Vec3(entity.getX(), entity.getEyeY(), entity.getZ()).subtract(from);
            double along = dir.dot(diff);
            if (along > 0.0 && along < length) {
                Vec3 at = from.add(dir.scale(along));
                level().playLocalSound(at.x, at.y, at.z, RealmSounds.BULLET_FLY_BY, getSoundSource(), volume,
                        0.92f + 0.16f * random.nextFloat(), false);
            }
        }
    }

    /**
     * Damage on an entity hit: team and PvP rules, the PvP or mob multiplier, the range multiplier
     * (rifle), then the hit itself (pellets add up and land together at the end of the tick, see DeferredDamage).
     */
    @Override
    public void onHitEntity(EntityHitResult hitResult) {
        if (calculateEnergyFraction() < 0.05f) {
            return;
        }
        float multiplier = 1.0f;
        Entity target = hitResult.getEntity();
        Entity shooter = getOwner();
        if (shooter != null && shooter.getTeam() != null && shooter.getTeam().isAlliedTo(target.getTeam())
                && !shooter.getTeam().isAllowFriendlyFire()) {
            return;
        }
        if (shooter instanceof Player playerShooter) {
            if (target instanceof Player playerTarget) {
                multiplier = GunConfig.PVP_MULTIPLIER;
                if (!playerShooter.canHarmPlayer(playerTarget)) {
                    return;
                }
            }
        } else {
            multiplier = mobDamageMultiplier >= 0f ? mobDamageMultiplier : GunType.MOB_DAMAGE_MULTIPLIER;
            if (shooter instanceof net.minecraft.world.entity.raid.Raider && target instanceof net.minecraft.world.entity.raid.Raider) {
                multiplier *= GunConfig.RAIDER_FRIENDLY_FIRE; // raiders hitting each other (1.0.5)
            }
        }
        multiplier *= rangeDamageMultiplier(distanceAt(hitResult));
        if (payload != null && payload.onHitEntity(this, target)) {
            return;
        }
        DamageSource source = getDamageSource();
        float amount = damage * multiplier;
        if (damageModifier != null) {
            amount = damageModifier.modify(target, amount, source);
        }
        boolean ignite = (isOnFire() || igniteOnHitSeconds > 0f) && target.getType() != EntityType.ENDERMAN;
        if (pelletCount() == 1) {
            target.invulnerableTime = 0;
            boolean hurt = target.hurtOrSimulate(source, amount);
            if (hurt && knockback > 0f && target instanceof LivingEntity living) {
                applyKnockback(living);
            }
            if (ignite) {
                target.igniteForSeconds(igniteOnHitSeconds > 0f ? igniteOnHitSeconds : 5.0f);
            }
        } else {
            DeferredDamage.hurt(target, source, amount / pelletCount(), ignite ? 1.0f : 0.0f);
        }
    }

    /** Arrow-style Punch knockback along the bullet's horizontal direction (AbstractArrow#doKnockback). */
    private void applyKnockback(LivingEntity target) {
        double resistance = Math.max(0.0, 1.0 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        Vec3 push = getDeltaMovement().multiply(1.0, 0.0, 1.0).normalize().scale(knockback * 0.6 * resistance);
        if (push.lengthSqr() > 0.0) {
            target.push(push.x, 0.1, push.z);
            target.hurtMarked = true;
        }
    }

    /** Blocks flown when reaching this hit: so far, plus this tick's leg (hits are handled before the move). */
    protected float distanceAt(HitResult hitResult) {
        return distanceTravelled + (float) position().distanceTo(hitResult.getLocation());
    }

    /** Damage multiplier for a hit after this many blocks of flight; Realm Arms bullets use their gun's. */
    protected float rangeDamageMultiplier(float distance) {
        return 1.0f;
    }

    public EntityHitResult findHitEntity(Vec3 start, Vec3 end) {
        Entity resultEntity = null;
        Vec3 resultPos = null;
        double resultDist = 0.0;
        AABB area = getBoundingBox().expandTowards(getDeltaMovement()).inflate(0.5);
        for (Entity entity : level().getEntities(this, area, this::canHitEntity)) {
            AABB box = entity.getBoundingBox();
            Optional<Vec3> clip = box.clip(start, end);
            if (clip.isEmpty()) {
                // Also test where it was last tick, so fast-moving targets aren't skipped.
                box = box.move(entity.xOld - entity.getX(), entity.yOld - entity.getY(), entity.zOld - entity.getZ());
                clip = box.clip(start, end);
            }
            if (clip.isEmpty()) {
                continue;
            }
            double dist = start.distanceToSqr(clip.get());
            if (resultEntity == null || dist < resultDist) {
                resultEntity = entity;
                resultPos = clip.get();
                resultDist = dist;
            }
        }
        return resultEntity != null ? new EntityHitResult(resultEntity, resultPos) : null;
    }

    @Override
    public boolean canHitEntity(Entity entity) {
        if (passesTagged() && entity.typeHolder().is(SHELL_PASS_THROUGH)) {
            return false;
        }
        int ghostExcept = entityData.get(GHOST_EXCEPT);
        if (ghostExcept != GHOST_OFF && !(entity instanceof Player || entity instanceof IronGolem || entity.getId() == ghostExcept)) {
            return false;
        }
        return super.canHitEntity(entity) || (level().isClientSide() && entity instanceof LivingEntity);
    }

    public void createHitParticles(ParticleOptions particle, Vec3 position, Vec3 velocity) {
        float amount = HIT_PARTICLE_COUNT * calculateEnergyFraction() / pelletCount();
        int count = (int) amount + (random.nextFloat() < amount % 1.0f ? 1 : 0);
        for (int i = 0; i < count; ++i) {
            level().addParticle(particle, position.x, position.y, position.z,
                    velocity.x + 0.01 * random.nextGaussian(), velocity.y + 0.01 * random.nextGaussian(), velocity.z + 0.01 * random.nextGaussian());
        }
    }

    public void playHitSound(SoundEvent sound, Vec3 position) {
        if (soundEffectRoll()) {
            level().playLocalSound(position.x, position.y, position.z, sound, getSoundSource(), calculateEnergyFraction(),
                    0.95f + 0.1f * random.nextFloat(), false);
        }
    }

    public void setInitialSpeed(float speed) {
        entityData.set(INITIAL_SPEED, speed);
    }

    public void setDropReduction(float reduction) {
        entityData.set(DROP_REDUCTION, reduction);
    }

    public void setPelletCount(int count) {
        entityData.set(PELLET_COUNT, (byte) count);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(INITIAL_SPEED, 0.0f);
        builder.define(DROP_REDUCTION, 0.0f);
        builder.define(PELLET_COUNT, (byte) 1);
        builder.define(LIFETIME_TICKS, 0);
        builder.define(GHOST_EXCEPT, GHOST_OFF);
        builder.define(PASSES_TAGGED, false);
    }

    public void setPassesTagged(boolean passes) {
        entityData.set(PASSES_TAGGED, passes);
    }

    public boolean passesTagged() {
        return entityData.get(PASSES_TAGGED);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        damage = input.getFloatOr("damage", 0.0f);
        distanceTravelled = input.getFloatOr("distanceTravelled", 0.0f);
        entityData.set(DROP_REDUCTION, input.getFloatOr("dropReduction", 0.0f));
        entityData.set(PELLET_COUNT, input.getByteOr("pelletCount", (byte) 1));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putFloat("damage", damage);
        output.putFloat("distanceTravelled", distanceTravelled);
        output.putFloat("dropReduction", entityData.get(DROP_REDUCTION));
        output.putByte("pelletCount", entityData.get(PELLET_COUNT));
    }

    /**
     * The spawn packet's velocity field is limited in range, so the direction is sent scaled to a
     * fixed length and the real speed follows in the synced INITIAL_SPEED.
     */
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        Entity owner = getOwner();
        Vec3 position = entity.getPositionBase();
        return new ClientboundAddEntityPacket(getId(), getUUID(), position.x(), position.y(), position.z(),
                entity.getLastSentXRot(), entity.getLastSentYRot(), getType(), owner != null ? owner.getId() : 0,
                entity.getLastSentMovement().scale(3.9 / entityData.get(INITIAL_SPEED)), 0.0);
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        setDeltaMovement(packet.getMovement().scale(1.0 / 3.9));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (INITIAL_SPEED.equals(accessor) && level().isClientSide() && !packetSpeedReceived) {
            setDeltaMovement(getDeltaMovement().scale(entityData.get(INITIAL_SPEED)));
            packetSpeedReceived = true;
        }
    }
}
