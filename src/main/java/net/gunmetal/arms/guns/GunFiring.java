package net.gunmetal.arms.guns;

import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.gunmetal.arms.ammo.Cartridge;
import net.gunmetal.arms.entity.RealmBulletEntity;
import net.gunmetal.arms.registry.RealmParticles;

/**
 * Firing for every Realm Arms gun: spawns the bullet entity (with per-gun visuals and cartridge
 * effects) and the muzzle effects.
 * Runs on the server for players, mobs and anything else that fires the gun.
 */
public final class GunFiring {
    // Muzzle effect tuning. Counts are particles per shot; speeds are blocks per tick.
    private static final double MUZZLE_DISTANCE = 1.1;
    /** The flash sits this much further out (one model pixel), so a player moving forward doesn't overtake it. */
    private static final double FLASH_EXTRA_DISTANCE = 1.0 / 16.0;
    private static final int SMOKE_SUBTLE = 6, SMOKE_MEDIUM = 12, SMOKE_HEAVY = 20;
    /** How widely smoke starts (blocks) and how much its drift varies (blocks per tick). */
    private static final double SMOKE_START_SPREAD = 0.16, SMOKE_DRIFT_SPREAD = 0.024;
    /** Aftersmoke spreads further sideways (x/z only): extra start offset (blocks) and sideways drift (blocks per tick). */
    private static final double AFTERSMOKE_EXTRA_START_XZ = 0.15, AFTERSMOKE_EXTRA_DRIFT_XZ = 0.015;
    /** Sparks come from the lock, this far along the barrel from the hand, and fly out to both sides. */
    private static final double LOCK_DISTANCE = 0.45;
    /** The hand cannon's upward sparks start this far above the lock (blocks). */
    private static final double SPARK_TOP_OFFSET = 0.1;
    private static final double SPARK_UP_SPEED_MIN = 0.15, SPARK_UP_SPEED_MAX = 0.30;
    private static final double SPARK_SIDE_SPEED_MIN = 0.10, SPARK_SIDE_SPEED_MAX = 0.25;

    private GunFiring() {
    }

    public static void fire(GunItem gun, RealmGun realm, LivingEntity shooter, ItemStack stack, Vec3 direction, Vec3 smokeOffset) {
        fire(gun, realm, shooter, stack, direction, smokeOffset, false);
    }

    /** @param spareShooter the shot's hand cannon burst never hurts the shooter (firelance point-blank shots) */
    public static void fire(GunItem gun, RealmGun realm, LivingEntity shooter, ItemStack stack, Vec3 direction, Vec3 smokeOffset,
                            boolean spareShooter) {
        if (!(shooter.level() instanceof ServerLevel level)) {
            return;
        }
        GunType type = realm.gunType();
        // Players and mobs both fire whatever was loaded (mobs load from their other hand, see GunItem#mobLoadAmmo).
        Cartridge cartridge = Cartridge.loadedIn(stack);
        // Shared by every pellet of the shot (and every shot of a burst), so Ice counts once per target.
        int shotId = level.getRandom().nextInt();

        // Unstable: one roll per trigger pull (so a whole volleygun burst misfires together).
        boolean misfire = cartridge == Cartridge.UNSTABLE && level.getRandom().nextFloat() < Cartridge.UNSTABLE_MISFIRE_CHANCE;

        // Pellets per shot and effects follow the gun's normal burst (the volleygun fires in a burst).
        int bursts = Math.max(1, type.burstShots());
        shoot(level, gun, realm, shooter, direction, smokeOffset, cartridge, shotId, Math.max(1, type.burstShots()), stack, misfire, spareShooter);
        if (misfire) {
            misfire(level, shooter, stack);
        }
        if (bursts > 1 && !stack.isEmpty()) {
            BurstFire.schedule(gun, realm, shooter, stack, cartridge, shotId, bursts - 1, misfire, spareShooter);
        }
    }

    /** Break sound, plus the extra durability (the gun's normal 1 per shot is taken in GunItem). */
    private static void misfire(ServerLevel level, LivingEntity shooter, ItemStack stack) {
        level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.ITEM_BREAK,
                shooter.getSoundSource(), 1.0f, 0.8f + level.getRandom().nextFloat() * 0.4f);
        // Only players' guns take the durability hit (mobs' guns never lose durability from shooting).
        if (shooter instanceof Player player && !player.getAbilities().instabuild && stack.isDamageableItem()) {
            EquipmentSlot slot = shooter.getOffhandItem() == stack ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
            stack.hurtAndBreak(Cartridge.UNSTABLE_MISFIRE_DURABILITY - 1, shooter, slot);
        }
    }

    /**
     * One shot: pellets / burstShots bullets plus muzzle effects. Each pellet still divides the gun's
     * damage by the gun's FULL pellet count, so a whole burst adds up to the same total damage.
     */
    static void shoot(ServerLevel level, GunItem gun, RealmGun realm, LivingEntity shooter, Vec3 direction,
                      Vec3 smokeOffset, Cartridge cartridge, int shotId, int burstShots, ItemStack stack, boolean misfire,
                      boolean spareShooter) {
        GunType type = realm.gunType();
        Vec3 origin = new Vec3(shooter.getX(), shooter.getEyeY(), shooter.getZ());
        CustomGunner custom = shooter instanceof CustomGunner c ? c : null;
        int totalPellets = custom != null && custom.pelletCount(stack) > 0 ? custom.pelletCount(stack) : gun.pelletCount();
        int pelletsThisShot = Math.max(1, totalPellets / burstShots);
        float damage = gun.shotDamage(stack, shooter) * (misfire ? Cartridge.UNSTABLE_MISFIRE_DAMAGE : cartridge.damageMultiplier);
        // The shooter's movement this tick: carried by the flash, and recorded for advancements.
        Vec3 shooterMotion = new Vec3(shooter.getX() - shooter.xo, shooter.getY() - shooter.yo, shooter.getZ() - shooter.zo);
        float speed = misfire ? Cartridge.UNSTABLE_MISFIRE_VELOCITY : gun.bulletSpeed();
        float spread = custom != null && custom.spreadOverride(stack) >= 0f ? custom.spreadOverride(stack)
                : gun.shotSpread(stack, shooter) * stanceSpreadMultiplier(shooter)
                + (misfire ? Cartridge.UNSTABLE_MISFIRE_EXTRA_SPREAD : 0f);
        float dropReduction = gun.bulletDropReduction();

        for (int i = 0; i < pelletsThisShot; ++i) {
            RealmBulletEntity bullet = new RealmBulletEntity(level);
            bullet.setOwner(shooter);
            bullet.setPos(origin);
            bullet.setVelocity(speed, gun.spreadShot(direction, shooter.getRandom(), spread, stack, shooter));
            bullet.damage = damage;
            bullet.setDropReduction(dropReduction);
            bullet.setPelletCount(totalPellets);
            bullet.setVisuals(type.projectile, type.trail);
            bullet.setCartridge(cartridge);
            bullet.shotId = shotId;
            bullet.explosionRadius = type.impactExplosionRadius();
            bullet.gunType = type;
            bullet.misfire = misfire;
            bullet.spareShooter = spareShooter;
            bullet.firedFrom = origin;
            bullet.shooterSpeed = shooterMotion.length() * 20.0;
            if (shooter instanceof CustomGunner customGunner) {
                customGunner.customizeBullet(bullet, stack);
            }
            level.addFreshEntity(bullet);
        }

        // Guns held lower in the arm: shift the muzzle effects "down" relative to the shooter's view.
        Vec3 viewDown = Vec3.directionFromRotation(shooter.getXRot() + 90.0f, shooter.getYRot());
        Vec3 barrel = origin.add(smokeOffset).add(viewDown.scale(type.muzzleDropPixels() / 16.0));
        emitMuzzleEffects(level, type, realm.gunTier(), barrel, direction, burstShots, shooterMotion, viewDown, cartridge);
    }

    private static void emitMuzzleEffects(ServerLevel level, GunType type, GunTier tier, Vec3 barrel, Vec3 dir, int burstShots,
                                          Vec3 shooterMotion, Vec3 viewDown, Cartridge cartridge) {
        RandomSource random = level.getRandom();
        Vec3 muzzle = barrel.add(dir.scale(MUZZLE_DISTANCE));

        // Muzzle flash: one large, brief particle at the barrel (random 90-degree rotation, set by the particle).
        SimpleParticleType flash = switch (type.flash) {
            case NONE -> null;
            case LIGHT -> RealmParticles.FLASH_LIGHT;
            case MEDIUM -> RealmParticles.FLASH_MEDIUM;
            case HEAVY -> RealmParticles.FLASH_HEAVY;
            case VOLLEYGUN -> RealmParticles.FLASH_VOLLEYGUN;
        };
        Vec3 flashPos = muzzle.add(dir.scale(FLASH_EXTRA_DISTANCE));
        if (flash != null) {
            send(level, flash, flashPos, shooterMotion);
        }
        // Accent flashes on top: one for special cartridges, one for the rifle.
        SimpleParticleType cartridgeFlash = switch (cartridge) {
            case STANDARD, UNSTABLE -> null;
            case BLAZE -> RealmParticles.FLASH_ACCENT_BLAZE;
            case ICE -> RealmParticles.FLASH_ACCENT_ICE;
            case NUCLEAR -> RealmParticles.FLASH_ACCENT_NUCLEAR;
            case VOID -> RealmParticles.FLASH_ACCENT_VOID;
            case SUNKEN -> RealmParticles.FLASH_ACCENT_SUNKEN;
            case POISON -> RealmParticles.FLASH_ACCENT_POISON;
        };
        if (cartridgeFlash != null) {
            send(level, cartridgeFlash, flashPos, shooterMotion);
        }
        if (type == GunType.RIFLE) {
            send(level, RealmParticles.FLASH_ACCENT_RIFLE, flashPos, shooterMotion);
        }

        // Firing smoke: drifts slowly outward and upward (the particle itself adds the rise),
        // followed by half as much longer-lasting aftersmoke.
        SimpleParticleType smoke;
        SimpleParticleType aftersmoke;
        int smokeCount;
        switch (type.smoke) {
            case SUBTLE -> { smoke = RealmParticles.SMOKE_SUBTLE; aftersmoke = RealmParticles.AFTERSMOKE_SUBTLE; smokeCount = SMOKE_SUBTLE; }
            case MEDIUM -> { smoke = RealmParticles.SMOKE_MEDIUM; aftersmoke = RealmParticles.AFTERSMOKE_MEDIUM; smokeCount = SMOKE_MEDIUM; }
            default -> { smoke = RealmParticles.SMOKE_HEAVY; aftersmoke = RealmParticles.AFTERSMOKE_HEAVY; smokeCount = SMOKE_HEAVY; }
        }
        // A burst spreads the gun's smoke and sparks across its shots instead of repeating them in full.
        smokeCount = perShot(smokeCount, burstShots);
        int aftersmokeCount = (smokeCount + 1) / 2;
        for (int i = 0; i < smokeCount + aftersmokeCount; ++i) {
            Vec3 velocity = dir.scale(0.02 + random.nextDouble() * 0.06).add(jitter(random, SMOKE_DRIFT_SPREAD)).add(0, 0.008, 0);
            Vec3 pos = muzzle.add(jitter(random, SMOKE_START_SPREAD));
            boolean after = i >= smokeCount;
            if (after) {
                pos = pos.add(jitterXZ(random, AFTERSMOKE_EXTRA_START_XZ));
                velocity = velocity.add(jitterXZ(random, AFTERSMOKE_EXTRA_DRIFT_XZ));
            }
            send(level, after ? aftersmoke : smoke, pos, velocity);
        }

        // Sparks from the lock mechanism near the hand, colored by gun material.
        SimpleParticleType spark = switch (tier) {
            case COPPER -> RealmParticles.SPARK_COPPER;
            case BRASS -> RealmParticles.SPARK_BRASS;
            case STEEL -> RealmParticles.SPARK_STEEL;
        };
        Vec3 lock = barrel.add(dir.scale(LOCK_DISTANCE));
        int perSide = perShot(type.sparksPerSide(), burstShots);
        if (perSide > 0) {
            // Out to the left and right, then falling.
            Vec3 side = dir.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
            for (int i = 0; i < perSide * 2; ++i) {
                double sign = (i % 2 == 0) ? 1 : -1;
                double speed = SPARK_SIDE_SPEED_MIN + random.nextDouble() * (SPARK_SIDE_SPEED_MAX - SPARK_SIDE_SPEED_MIN);
                Vec3 velocity = side.scale(sign * speed)
                        .add(0, 0.03 + random.nextDouble() * 0.05, 0)
                        .add(jitter(random, 0.04));
                send(level, spark, lock, velocity);
            }
        }
        int upward = perShot(type.sparksUpward(), burstShots);
        if (upward > 0) {
            // Up out of the top of the gun (the touch-hole).
            Vec3 up = viewDown.scale(-1);
            Vec3 top = lock.add(up.scale(SPARK_TOP_OFFSET));
            for (int i = 0; i < upward; ++i) {
                double speed = SPARK_UP_SPEED_MIN + random.nextDouble() * (SPARK_UP_SPEED_MAX - SPARK_UP_SPEED_MIN);
                send(level, spark, top, up.scale(speed).add(jitter(random, 0.05)));
            }
        }
    }

    /** Muzzle flash, smoke and sparks for a cartridge fired from a dispenser (pistol-sized). */
    public static void dispenserEffects(ServerLevel level, Vec3 muzzle, Vec3 direction) {
        Vec3 barrel = muzzle.subtract(direction.scale(MUZZLE_DISTANCE));
        emitMuzzleEffects(level, GunType.PISTOL, GunTier.COPPER, barrel, direction, 1, Vec3.ZERO, new Vec3(0, -1, 0), Cartridge.STANDARD);
    }

    private static int perShot(int perTrigger, int burstShots) {
        return perTrigger <= 0 ? 0 : (perTrigger + burstShots - 1) / burstShots;
    }

    private static Vec3 jitterXZ(RandomSource random, double amount) {
        return new Vec3((random.nextDouble() - 0.5) * 2 * amount, 0, (random.nextDouble() - 0.5) * 2 * amount);
    }

    private static Vec3 jitter(RandomSource random, double amount) {
        return new Vec3((random.nextDouble() - 0.5) * 2 * amount,
                (random.nextDouble() - 0.5) * 2 * amount,
                (random.nextDouble() - 0.5) * 2 * amount);
    }

    /**
     * Players only (1.0.6): sneaking tightens a shot's spread, sprinting widens it (GunConfig.SNEAK_SPREAD /
     * SPRINT_SPREAD). Mobs keep their own aim error. Fanned revolver rounds use their fixed cone instead.
     */
    static float stanceSpreadMultiplier(LivingEntity shooter) {
        if (!(shooter instanceof Player player)) {
            return 1.0f;
        }
        if (player.isCrouching()) {
            return GunConfig.SNEAK_SPREAD;
        }
        return player.isSprinting() ? GunConfig.SPRINT_SPREAD : 1.0f;
    }

    /** Count 0 makes the client use the given vector as the particle's exact velocity. */
    private static void send(ServerLevel level, SimpleParticleType type, Vec3 pos, Vec3 velocity) {
        level.sendParticles(type, pos.x, pos.y, pos.z, 0, velocity.x, velocity.y, velocity.z, 1.0);
    }
}
