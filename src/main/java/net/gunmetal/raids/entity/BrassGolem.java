package net.gunmetal.raids.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;


/**
 * The Artificer's walking bomb (hostile). Chases its target like a creeper; its fuse count
 * (AbstractBrassGolem: 1 per second, 5 per damaging hit) starts at 30 when its Artificer summons it.
 * Reaching its target (in sight within 3 blocks) lights its 1.5 s fuse at once, as does a full count
 * (60); a lit fuse can't be put out, and it keeps chasing at half speed while it burns. The blast is
 * 2 blocks smaller across than a creeper's at level 1 (each upgrade level adds 2 gunpowder's worth),
 * breaks no blocks, deals only half damage to illagers and stuns everything it hurts but raiders. Killing it defuses it
 * (no blast; its loot drops). Has its own texture. Gives off the Artificer's sparks. Iron golems and
 * guards attack it on sight (it is an Enemy); illagers count it as an ally.
 */
public class BrassGolem extends AbstractBrassGolem implements Enemy {
    // ---- Tuning -------------------------------------------------------------------------
    /** Blast radius (creeper 3 = 6 across; this is 4 across). */
    public static final float BLAST_RADIUS = 2.0f;
    /** Lights its fuse when its target is in sight within this distance. */
    public static final double LIGHT_DISTANCE = 3.0;
    /** Illagers caught in the blast take this share of the damage. */
    public static final float ILLAGER_BLAST_DAMAGE = 0.5f;
    /** Levels (its Artificer's upgrade moves): each level past 1 adds the blast of 2 more gunpowder in a deployable. */
    public static final int MAX_LEVEL = 3, GUNPOWDER_PER_LEVEL = 2;
    /** Ticks without its Artificer (checked every 10) before it lights its fuse, as the drones die. */
    public static final int HOST_MISSING_GRACE = 40;
    // -------------------------------------------------------------------------------------

    private @Nullable EntityReference<Mob> owner;
    private int golemLevel = 1;
    private int hostMissingTicks;
    private final SummonFx.DelayedSound upgradeSound = new SummonFx.DelayedSound();

    public BrassGolem(EntityType<? extends BrassGolem> type, Level level) {
        super(type, level);
        this.xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return golemAttributes(Monster.createMonsterAttributes())
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new CopyOwnerTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, PathfinderMob.class, 10, true, false,
                (entity, level) -> RaidIllager.isGuard(entity)));
    }

    // What Monster would give it (it can't extend Monster: the deployed golem shares its base class).
    // Unlike a Monster it doesn't stop players sleeping nearby (vanilla only checks Monsters).
    @Override
    public net.minecraft.sounds.SoundSource getSoundSource() {
        return net.minecraft.sounds.SoundSource.HOSTILE;
    }

    @Override
    public void aiStep() {
        updateSwingTime();
        super.aiStep();
    }

    // ---- Owner ------------------------------------------------------------------------------

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
        return (entity.is(EntityTypeTags.RAIDERS) || entity.is(EntityTypeTags.ILLAGER_FRIENDS) || entity instanceof BrassGolem
                || entity instanceof Puppeted)
                && getTeam() == null && entity.getTeam() == null;
    }

    // ---- Fuse & blast -------------------------------------------------------------------------

    /**
     * Reached its target (alive, in sight, within LIGHT_DISTANCE): light the fuse. Its Artificer dead
     * (or gone for 2 s): the fuse count jumps to full and the fuse lights. One without an Artificer
     * (spawn egg) is unaffected.
     */
    @Override
    protected void serverTick(ServerLevel level) {
        if (owner != null && tickCount % 10 == 0) {
            Mob host = getOwner();
            if (host != null && host.isAlive()) {
                hostMissingTicks = 0;
            } else if (host != null || (hostMissingTicks += 10) >= HOST_MISSING_GRACE) {
                owner = null;
                setFuse(getFuseThreshold());
                lightFuse();
                return;
            }
        }
        LivingEntity target = getTarget();
        if (target != null && target.isAlive() && distanceToSqr(target) <= LIGHT_DISTANCE * LIGHT_DISTANCE
                && getSensing().hasLineOfSight(target)) {
            lightFuse();
        }
    }

    @Override
    protected void blowUp(ServerLevel level) {
        float radius = BLAST_RADIUS * new net.gunmetal.raids.item.GolemLoad(GUNPOWDER_PER_LEVEL * (golemLevel - 1), 0, 0, 0, 0, 0).blastMultiplier();
        explode(level, radius, new IllagerSafeExplosion(), damageSources().explosion(this, this));
    }

    /** Server: arrives with this fuse count (its Artificer's golems start part-way). */
    public void startFuseAt(float fuse) {
        setFuse(fuse);
    }

    public int golemLevel() {
        return golemLevel;
    }

    /**
     * Its Artificer's upgrade move: up a level (bigger blast; same look), with the drone upgrade's sparks
     * and (slightly delayed) sound. At level 3: Regeneration I for 5 s instead.
     */
    public void upgrade() {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (golemLevel >= MAX_LEVEL) {
            SummonFx.maxLevelRegen(this);
            return;
        }
        ++golemLevel;
        level.sendParticles(net.gunmetal.arms.registry.RealmParticles.SPARK_BRASS, getX(), getY(0.5), getZ(), 16,
                getBbWidth() * 0.5, getBbHeight() * 0.5, getBbWidth() * 0.5, 0.15);
        level.sendParticles(net.gunmetal.raids.registry.RaidParticles.SUMMON_MARKER, true, true, getX(), getY(0.5), getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        upgradeSound.schedule(net.gunmetal.raids.registry.RaidSounds.DRONE_UPGRADE, getRandom());
    }

    @Override
    public void tick() {
        super.tick();
        upgradeSound.tick(this);
    }

    /** Normal explosion damage, halved for illagers; stuns what it hurts except raiders. */
    private static final class IllagerSafeExplosion extends ExplosionDamageCalculator {
        @Override
        public float getEntityDamageAmount(Explosion explosion, Entity entity, float exposure) {
            float damage = super.getEntityDamageAmount(explosion, entity, exposure);
            if (damage > 0f && !entity.is(EntityTypeTags.RAIDERS)) {
                blastStun(entity, exposure);
            }
            return entity.is(EntityTypeTags.ILLAGER) ? damage * ILLAGER_BLAST_DAMAGE : damage;
        }
    }

    /** Takes its Artificer's target. */
    private static final class CopyOwnerTargetGoal extends TargetGoal {
        private final BrassGolem golem;
        private final TargetingConditions copyConditions = TargetingConditions.forNonCombat().ignoreLineOfSight().ignoreInvisibilityTesting();

        CopyOwnerTargetGoal(BrassGolem golem) {
            super(golem, false);
            this.golem = golem;
            setFlags(java.util.EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            Mob master = golem.getOwner();
            return master != null && master.getTarget() != null && master.getTarget() != golem.getTarget()
                    && canAttack(master.getTarget(), copyConditions);
        }

        @Override
        public void start() {
            Mob master = golem.getOwner();
            if (master != null) {
                golem.setTarget(master.getTarget());
            }
            super.start();
        }
    }

    // ---- Save -------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        EntityReference.store(owner, output, "Owner");
        output.putInt("GolemLevel", golemLevel);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        owner = EntityReference.read(input, "Owner");
        golemLevel = Math.max(1, Math.min(MAX_LEVEL, input.getIntOr("GolemLevel", 1)));
        // Golems saved mid-fuse by older versions (FuseLocked) light their fuse on their first tick.
        if (input.getBooleanOr("FuseLocked", false)) {
            setFuse(getFuseThreshold());
        }
    }
}
