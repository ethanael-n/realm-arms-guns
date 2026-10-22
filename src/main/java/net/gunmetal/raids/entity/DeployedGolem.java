package net.gunmetal.raids.entity;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.gunmetal.raids.item.GolemLoad;
import net.gunmetal.raids.registry.RaidItems;
import org.jetbrains.annotations.Nullable;

/**
 * The player's brass golem, from a thrown Brass Deployable. Passive: wanders and ticks. Takes only
 * 20% of all damage (not /kill or the void). Its fuse count (AbstractBrassGolem) rises 1 per second
 * and 5 per damaging hit; at its threshold (60, +10 per copper patina) it lights a 1.5 s fuse (keeps
 * wandering at half speed, swells) and blows up, stunning everything it hurts for 1 s. The blast spares
 * no one, deals full damage to illagers and counts as the thrower's.
 *
 * Aggro pulses: when it appears, and when its fuse count passes 15, 30 and 45 (marks below its
 * starting count are skipped), every hostile mob within 12 blocks (+1.5 per glowstone dust) targets it
 * (not bosses, the Warden, Elder Guardians or neutral mobs).
 *
 * Killing it before it blows up defuses it: no blast, and a 50% chance to drop a plain Brass
 * Deployable. Not an Enemy: iron golems and guards leave it alone.
 */
public class DeployedGolem extends AbstractBrassGolem {
    // ---- Tuning -------------------------------------------------------------------------
    /** Base blast radius (same as the hostile golem's) before the load's bonus. */
    public static final float BLAST_RADIUS = BrassGolem.BLAST_RADIUS;
    /** Share of damage it takes (80% resistance). Damage that bypasses invulnerability (/kill, the void) is untouched. */
    public static final float DAMAGE_TAKEN = 0.2f;
    /** Aggro pulse radius before glowstone, and the fuse counts that set off a pulse (after the one on appearing). */
    public static final float PULSE_RADIUS = 12.0f;
    public static final float[] PULSE_MARKS = {15f, 30f, 45f};
    /** Chance that killing it drops a plain Brass Deployable. */
    public static final float DEPLOYABLE_DROP_CHANCE = 0.5f;
    // -------------------------------------------------------------------------------------

    public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("c", "bosses"));
    /** Alex's Caves' Irradiated effect (sulfur loads); skipped if the mod isn't installed. */
    public static final Identifier IRRADIATED = Identifier.fromNamespaceAndPath("alexscaves", "irradiated");

    private static final EntityDataAccessor<Byte> TANK = SynchedEntityData.defineId(DeployedGolem.class, EntityDataSerializers.BYTE);

    private GolemLoad load = GolemLoad.EMPTY;
    private @Nullable EntityReference<LivingEntity> thrower;
    /** Pulses done: -1 = the one on appearing is still to come, then the number of PULSE_MARKS passed. */
    private int pulseStage = -1;

    public DeployedGolem(EntityType<? extends DeployedGolem> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return golemAttributes(Mob.createMobAttributes())
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TANK, (byte) GolemLoad.Tank.GUNPOWDER.ordinal());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    /** Called once when it appears from a thrown deployable. */
    public void deploy(GolemLoad load, @Nullable LivingEntity thrower) {
        this.thrower = thrower != null ? EntityReference.of(thrower) : null;
        applyLoad(load);
        setFuse(load.startFuse());
        pulseStage = -1;
        setPersistenceRequired();
    }

    private void applyLoad(GolemLoad load) {
        this.load = load;
        entityData.set(TANK, (byte) load.tank().ordinal());
        setFuseThreshold(BASE_FUSE_THRESHOLD + load.extraThreshold());
    }

    public GolemLoad.Tank tank() {
        int index = entityData.get(TANK);
        GolemLoad.Tank[] tanks = GolemLoad.Tank.values();
        return index >= 0 && index < tanks.length ? tanks[index] : GolemLoad.Tank.GUNPOWDER;
    }

    // ---- Aggro pulses -------------------------------------------------------------------------

    @Override
    protected void serverTick(ServerLevel level) {
        int passed = marksPassed();
        if (pulseStage < 0) {
            // On appearing: marks already under the starting count are skipped.
            pulseStage = passed;
            pulse(level);
        } else if (passed > pulseStage) {
            // One pulse even if a big jump passed several marks at once.
            pulseStage = passed;
            pulse(level);
        }
    }

    /** How many PULSE_MARKS the fuse count has reached. */
    private int marksPassed() {
        float fuse = getFuse();
        int passed = 0;
        while (passed < PULSE_MARKS.length && fuse >= PULSE_MARKS[passed]) {
            ++passed;
        }
        return passed;
    }

    private static boolean canLure(Mob mob) {
        return mob instanceof Enemy && !(mob instanceof NeutralMob) && !(mob instanceof Warden) && !(mob instanceof ElderGuardian)
                && !(mob instanceof WitherBoss) && !mob.is(BOSSES);
    }

    public float pulseRadius() {
        return PULSE_RADIUS + load.extraPulseRadius();
    }

    /** Every eligible mob in range targets it (again, if it had moved on). */
    private void pulse(ServerLevel level) {
        double radius = pulseRadius();
        double radiusSqr = radius * radius;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(radius),
                mob -> mob != this && mob.isAlive() && canLure(mob) && distanceToSqr(mob) <= radiusSqr)) {
            mob.setTarget(this);
        }
    }

    // ---- Damage, death and the blast ----------------------------------------------------------

    @Override
    protected float scaleDamage(DamageSource source, float amount) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) ? amount : amount * DAMAGE_TAKEN;
    }

    /** Killed (not blown up): defused, with a chance to drop a plain deployable. */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        if (getRandom().nextFloat() < DEPLOYABLE_DROP_CHANCE) {
            spawnAtLocation(level, new ItemStack(RaidItems.BRASS_DEPLOYABLE));
        }
    }

    @Override
    protected void blowUp(ServerLevel level) {
        LivingEntity owner = EntityReference.get(thrower, level, LivingEntity.class);
        float radius = BLAST_RADIUS * load.blastMultiplier();
        explode(level, radius, new LoadExplosion(load.burnSeconds(), load.irradiatedSeconds(), owner),
                damageSources().explosion(this, owner != null ? owner : this));
    }

    /** Vanilla explosion damage (everyone, full), plus fire (blaze powder) and Irradiated (sulfur) on whoever it hurts. */
    private static final class LoadExplosion extends ExplosionDamageCalculator {
        private final int burnSeconds, irradiatedSeconds;
        private final @Nullable Entity owner;

        LoadExplosion(int burnSeconds, int irradiatedSeconds, @Nullable Entity owner) {
            this.burnSeconds = burnSeconds;
            this.irradiatedSeconds = irradiatedSeconds;
            this.owner = owner;
        }

        @Override
        public float getEntityDamageAmount(Explosion explosion, Entity entity, float exposure) {
            float damage = super.getEntityDamageAmount(explosion, entity, exposure);
            if (damage > 0f) {
                blastStun(entity, exposure); // everyone, the thrower too
            }
            // Only what the blast actually reaches (vanilla deals at least 1 damage even through walls).
            if (damage > 0f && exposure > 0f) {
                if (burnSeconds > 0) {
                    entity.igniteForSeconds(burnSeconds);
                }
                if (irradiatedSeconds > 0 && entity instanceof LivingEntity living) {
                    Holder<MobEffect> irradiated = BuiltInRegistries.MOB_EFFECT.get(IRRADIATED).orElse(null);
                    if (irradiated != null) {
                        living.addEffect(new MobEffectInstance(irradiated, irradiatedSeconds * 20), owner);
                    }
                }
            }
            return damage;
        }
    }

    // ---- Save ---------------------------------------------------------------------------------

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        load.save(output);
        EntityReference.store(thrower, output, "Thrower");
        output.putInt("PulseStage", pulseStage);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        applyLoad(GolemLoad.read(input));
        setBase(Attributes.ARMOR, 0.0); // older versions gave patina loads armor
        thrower = EntityReference.read(input, "Thrower");
        // Golems saved by older versions (timed fuse, no count): start the count from their age, near
        // the end if their old fuse was lit, and don't pulse on load.
        if (input.getFloatOr("FuseCount", -1f) < 0f) {
            float fuse = load.startFuse() + input.getIntOr("Age", 0) * FUSE_PER_TICK;
            if (input.getIntOr("FuseLeft", -1) >= 0) {
                fuse = getFuseThreshold(); // lights on its first tick
            }
            setFuse(fuse);
            pulseStage = marksPassed();
        } else {
            pulseStage = input.getIntOr("PulseStage", -1);
        }
    }
}
