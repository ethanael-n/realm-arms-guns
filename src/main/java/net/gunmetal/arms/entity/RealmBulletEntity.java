package net.gunmetal.arms.entity;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.gunmetal.arms.ammo.Cartridge;
import net.gunmetal.arms.guns.GunType;
import net.gunmetal.arms.registry.RealmParticles;
import org.jetbrains.annotations.Nullable;

import static net.gunmetal.arms.RealmArmsWeapons.MOD_ID;

/**
 * The bullet (flight, damage, pellets, block breaking are in GunBullet), plus:
 * which projectile texture and trail to show, and which cartridge effect to apply on hit.
 */
public class RealmBulletEntity extends GunBullet {
    private static final EntityDataAccessor<Byte> VISUALS =
            SynchedEntityData.defineId(RealmBulletEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> CARTRIDGE =
            SynchedEntityData.defineId(RealmBulletEntity.class, EntityDataSerializers.BYTE);
    /** Server game time when the bullet was fired, so a stale bullet can be recognised on both sides. */
    private static final EntityDataAccessor<Integer> SPAWN_TIME =
            SynchedEntityData.defineId(RealmBulletEntity.class, EntityDataSerializers.INT);
    private static final int SPAWN_TIME_UNKNOWN = Integer.MIN_VALUE;
    /**
     * A bullet older than this is removed on sight. Bullets that fly past the simulation distance stop
     * ticking on the server and just hang there; without this, when you later come back in range the
     * server re-sends them and your client replays them as "ghost" bullets from odd places.
     * A bullet's normal lifetime is GunBullet.LIFETIME ticks; the extra 20 covers client/server clock drift.
     */
    private static final int MAX_AGE_EXTRA_TICKS = 20;

    // Trail tuning (client only).
    /** Trail starts this far from the muzzle, so it doesn't cover the shooter's own view. */
    private static final double TRAIL_START_DISTANCE = 2.0;
    /** For pellet guns, each pellet emits roughly this many pellets' worth of trail, spread across all of them. */
    private static final float PELLET_TRAIL_SHARE = 2.0f;
    /** A cartridge accent particle every Nth trail point. */
    private static final int ACCENT_EVERY = 3;
    /** Speed range (blocks/tick) for poison and nuclear accents drifting off in a random direction. */
    private static final double ACCENT_DRIFT_SPEED_MIN = 0.03, ACCENT_DRIFT_SPEED_MAX = 0.07;

    public static EntityType<RealmBulletEntity> ENTITY_TYPE;

    /** Same for every pellet of one shot, so Ice only counts once per target per shot. */
    public int shotId;
    /** Server only: largest radius of the non-destructive burst on impact (hand cannon), 0 = none. */
    public float explosionRadius;
    // Server only, for advancements: which gun fired it, whether it was a misfire, where the
    // shooter was, and how fast the shooter was moving (blocks/second). Null gun = dispenser.
    public @Nullable GunType gunType;
    public boolean misfire;
    /** The burst never hurts the shooter (firelance). */
    public boolean spareShooter;
    /** Replaces the burst's damage scale (mob shots normally use GunType.MOB_DAMAGE_MULTIPLIER); negative = normal. */
    public float burstDamageScale = -1f;
    /** Extra burst damage factor for entities in the #minecraft:illager tag (Realm Raids shells use 0.5). */
    public float illagerBurstScale = 1f;
    public @Nullable Vec3 firedFrom;
    public double shooterSpeed;
    private int trailPoints;
    /** Client: distance flown at which the next sniper ring goes down (-1 = not started). */
    private float nextRingAt = -1f;
    /** Blocks between sniper rings (the trail itself is every 0.35). */
    private static final float SNIPER_RING_SPACING = 3.0f;

    public static void register() {
        Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, "bullet");
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id);
        EntityType.Builder<RealmBulletEntity> builder = EntityType.Builder.<RealmBulletEntity>of(RealmBulletEntity::new, MobCategory.MISC)
                .sized(0.5f, 0.5f)
                .clientTrackingRange(64)
                .updateInterval(20);
        // Clients simulate the flight themselves; no velocity packets after the spawn one.
        builder.alwaysUpdateVelocity(false);
        ENTITY_TYPE = builder.build(key);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, id, ENTITY_TYPE);
    }

    public RealmBulletEntity(EntityType<RealmBulletEntity> type, Level level) {
        super(type, level);
        if (!level.isClientSide()) {
            entityData.set(SPAWN_TIME, (int) level.getGameTime());
        }
    }

    public RealmBulletEntity(Level level) {
        this(ENTITY_TYPE, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VISUALS, (byte) 0);
        builder.define(CARTRIDGE, (byte) 0);
        builder.define(SPAWN_TIME, SPAWN_TIME_UNKNOWN);
    }

    public void setVisuals(GunType.Projectile projectile, GunType.Trail trail) {
        entityData.set(VISUALS, (byte) (projectile.ordinal() << 4 | trail.ordinal()));
    }

    public GunType.Projectile projectile() {
        int index = (entityData.get(VISUALS) >> 4) & 0xF;
        GunType.Projectile[] values = GunType.Projectile.values();
        return index < values.length ? values[index] : GunType.Projectile.ROUND;
    }

    public GunType.Trail trail() {
        int index = entityData.get(VISUALS) & 0xF;
        GunType.Trail[] values = GunType.Trail.values();
        return index < values.length ? values[index] : GunType.Trail.STANDARD;
    }

    public void setCartridge(Cartridge cartridge) {
        entityData.set(CARTRIDGE, (byte) cartridge.ordinal());
    }

    public Cartridge cartridge() {
        return Cartridge.byOrdinal(entityData.get(CARTRIDGE));
    }

    @Override
    public void tick() {
        if (isStale()) {
            discard();
            return;
        }
        Vec3 from = position();
        float travelledBefore = distanceTravelled;
        super.tick();
        if (level().isClientSide() && !touchedWater) {
            emitTrail(from, position(), travelledBefore);
        }
    }

    /** Client side: particles along the path this tick, plus cartridge accents. */
    private void emitTrail(Vec3 from, Vec3 to, float travelledBefore) {
        Vec3 path = to.subtract(from);
        double length = path.length();
        if (length < 1.0E-4) {
            return;
        }
        GunType.Trail trail = trail();
        Cartridge cartridge = cartridge();
        double spacing = switch (trail) {
            case SMALL -> 0.6;
            case STANDARD -> 0.4;
            case DENSE, BLACK -> 0.25;
            case CANNONBALL -> 0.3;
            case SNIPER -> 0.35;
            case FLAME -> 0.5;
        };
        SimpleParticleType trailParticle = switch (trail) {
            case SMALL -> RealmParticles.TRAIL_SMALL;
            case STANDARD -> RealmParticles.TRAIL_STANDARD;
            case DENSE -> RealmParticles.TRAIL_DENSE;
            case BLACK -> RealmParticles.TRAIL_BLACK;
            case CANNONBALL -> RealmParticles.TRAIL_CANNONBALL;
            case SNIPER -> RealmParticles.TRAIL_SNIPER;
            case FLAME -> net.minecraft.core.particles.ParticleTypes.FLAME; // Siege Balloon shrapnel (1.0.2)
        };
        SimpleParticleType accent = accentFor(cartridge);
        int pellets = pelletCount();
        float chance = pellets > 1 ? Math.min(1f, PELLET_TRAIL_SHARE / pellets) : 1f;

        Vec3 dir = path.scale(1.0 / length);
        for (double t = random.nextDouble() * spacing; t < length; t += spacing) {
            if (travelledBefore + t < TRAIL_START_DISTANCE || random.nextFloat() >= chance) {
                continue;
            }
            Vec3 p = from.add(dir.scale(t));
            level().addParticle(trailParticle, p.x, p.y, p.z,
                    (random.nextDouble() - 0.5) * 0.01, 0.004, (random.nextDouble() - 0.5) * 0.01);
            if (accent != null && ++trailPoints % ACCENT_EVERY == 0) {
                Vec3 v = accentVelocity(cartridge);
                level().addParticle(accent, p.x, p.y, p.z, v.x, v.y, v.z);
            }
        }
        if (trail == GunType.Trail.SNIPER) {
            // Rings every few blocks; the particle takes the shot's direction from its "velocity" and stays put.
            if (nextRingAt < 0f) {
                nextRingAt = (float) Math.max(TRAIL_START_DISTANCE, travelledBefore);
            }
            while (nextRingAt < travelledBefore + length) {
                double t = nextRingAt - travelledBefore;
                Vec3 p = from.add(dir.scale(Math.max(0.0, t)));
                level().addParticle(RealmParticles.TRAIL_SNIPER_RING, p.x, p.y, p.z, dir.x, dir.y, dir.z);
                nextRingAt += SNIPER_RING_SPACING;
            }
        }
    }

    private boolean isStale() {
        int spawnTime = entityData.get(SPAWN_TIME);
        int maxAge = Math.max(GunBullet.LIFETIME, lifetimeTicks()) + MAX_AGE_EXTRA_TICKS;
        return spawnTime != SPAWN_TIME_UNKNOWN && (int) level().getGameTime() - spawnTime > maxAge;
    }

    /**
     * Starting motion of accent particles: poison/nuclear drift off in a random direction, ice spreads
     * outward and sinks, sunken sinks, blaze bursts outward, void barely moves.
     */
    private Vec3 accentVelocity(Cartridge cartridge) {
        return switch (cartridge) {
            case NUCLEAR, POISON -> {
                Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
                yield dir.lengthSqr() < 1.0E-6 ? Vec3.ZERO : dir.normalize().scale(ACCENT_DRIFT_SPEED_MIN
                        + random.nextDouble() * (ACCENT_DRIFT_SPEED_MAX - ACCENT_DRIFT_SPEED_MIN));
            }
            case ICE -> new Vec3((random.nextDouble() - 0.5) * 0.08, 0, (random.nextDouble() - 0.5) * 0.08);
            case SUNKEN -> new Vec3((random.nextDouble() - 0.5) * 0.02, 0, (random.nextDouble() - 0.5) * 0.02);
            case BLAZE -> new Vec3((random.nextDouble() - 0.5) * 0.09, (random.nextDouble() - 0.5) * 0.09, (random.nextDouble() - 0.5) * 0.09);
            default -> new Vec3((random.nextDouble() - 0.5) * 0.03, (random.nextDouble() - 0.5) * 0.03, (random.nextDouble() - 0.5) * 0.03);
        };
    }

    private static @Nullable SimpleParticleType accentFor(Cartridge cartridge) {
        return switch (cartridge) {
            case STANDARD, UNSTABLE -> null;
            case BLAZE -> RealmParticles.ACCENT_BLAZE;
            case ICE -> RealmParticles.ACCENT_ICE;
            case NUCLEAR -> RealmParticles.ACCENT_NUCLEAR;
            case VOID -> RealmParticles.ACCENT_VOID;
            case SUNKEN -> RealmParticles.ACCENT_SUNKEN;
            case POISON -> RealmParticles.ACCENT_POISON;
        };
    }

    /**
     * After the normal hit (damage and cartridge effect on a direct hit), explosive balls burst.
     * The burst never breaks blocks and never applies cartridge effects; it damages and knocks back
     * whatever is near the impact point, credited to the shooter.
     */
    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (explosionRadius > 0f && !level().isClientSide() && hitResult.getType() != HitResult.Type.MISS) {
            Vec3 at = hitResult.getLocation();
            // A mob's burst is scaled by the mob damage multiplier, like its bullets.
            Entity owner = getOwner();
            float scale = burstDamageScale >= 0f ? burstDamageScale
                    : owner != null && !(owner instanceof Player) ? GunType.MOB_DAMAGE_MULTIPLIER : 1.0f;
            // The radius grows with distance flown (GunType#impactExplosionRadius(float)).
            float radius = gunType != null ? gunType.impactExplosionRadius(distanceAt(hitResult)) : explosionRadius;
            level().explode(this, damageSources().explosion(this, owner), new ScaledExplosion(scale, illagerBurstScale, spareShooter ? owner : null, passesTagged()),
                    at.x, at.y, at.z, radius, false, Level.ExplosionInteraction.NONE);
        }
    }

    /** Vanilla explosion damage x a factor (another for illagers; knockback unchanged), optionally sparing one entity. */
    private static final class ScaledExplosion extends ExplosionDamageCalculator {
        private final float scale;
        private final float illagerScale;
        private final @Nullable Entity spared;
        private final boolean sparesTagged;

        ScaledExplosion(float scale, float illagerScale, @Nullable Entity spared, boolean sparesTagged) {
            this.scale = scale;
            this.illagerScale = illagerScale;
            this.spared = spared;
            this.sparesTagged = sparesTagged;
        }

        @Override
        public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
            if (sparesTagged && entity.typeHolder().is(GunBullet.SHELL_PASS_THROUGH)) {
                return false;
            }
            return entity != spared && super.shouldDamageEntity(explosion, entity);
        }

        @Override
        public float getEntityDamageAmount(Explosion explosion, Entity entity, float exposure) {
            float factor = illagerScale != 1f && entity.typeHolder().is(EntityTypeTags.ILLAGER) ? scale * illagerScale : scale;
            return super.getEntityDamageAmount(explosion, entity, exposure) * factor;
        }
    }

    @Override
    public void onHitEntity(EntityHitResult hitResult) {
        Entity target = hitResult.getEntity();
        // Checked before super, using the same rules GunBullet uses to decide whether a hit deals damage.
        boolean deals = dealsDamageTo(target);
        super.onHitEntity(hitResult);
        Cartridge cartridge = cartridge();
        if (deals && cartridge != Cartridge.STANDARD && !level().isClientSide()) {
            cartridge.applyOnHit(target, getOwner(), pelletCount() > 1, shotId);
        }
    }

    private boolean dealsDamageTo(Entity target) {
        if (calculateEnergyFraction() < 0.05f) {
            return false;
        }
        Entity shooter = getOwner();
        if (shooter != null && shooter.getTeam() != null
                && shooter.getTeam().isAlliedTo(target.getTeam())
                && !shooter.getTeam().isAllowFriendlyFire()) {
            return false;
        }
        if (shooter instanceof Player playerShooter && target instanceof Player playerTarget) {
            return playerShooter.canHarmPlayer(playerTarget);
        }
        return true;
    }

    @Override
    protected float rangeDamageMultiplier(float distance) {
        return gunType != null ? gunType.rangeDamageMultiplier(distance) : super.rangeDamageMultiplier(distance);
    }
}
