package net.gunmetal.arms.guns;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.gunmetal.arms.ammo.Cartridge;
import net.gunmetal.arms.ammo.RealmArmsAmmo;
import net.gunmetal.arms.legacy.LegacyMusket;
import net.gunmetal.arms.registry.RealmSounds;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;


/**
 * Base class of every Realm Arms gun: loading in stages with cartridges, firing, mob use and
 * aiming. The loading/firing flow is ported from Musket Mod Reloaded's GunItem (MIT license, by
 * Lufurrius), with Realm Arms' changes built in: per-gun stage counts, cartridge types, per-gun
 * loading sounds, pitch variation and the attachment hooks.
 *
 * Loading state lives on the stack: gunmetal:loading_stage (1..stages while loading, stages+1
 * when the last stage is done) and gunmetal:loaded.
 */
public abstract class GunItem extends Item implements RealmGun {
    public static final DataComponentType<Boolean> LOADED = new DataComponentType.Builder<Boolean>()
            .persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build();
    public static final DataComponentType<Byte> LOADING_STAGE = new DataComponentType.Builder<Byte>()
            .persistent(Codec.BYTE).networkSynchronized(ByteBufCodecs.BYTE).build();

    /** Client: the gun stack the local player last fired or loaded, per hand (for the equip animation). */
    private static @Nullable ItemStack activeMainHandStack;
    private static @Nullable ItemStack activeOffhandStack;

    protected final GunType type;
    protected final GunTier tier;

    protected GunItem(Properties properties, GunType type, GunTier tier) {
        super(properties);
        this.type = type;
        this.tier = tier;
    }

    // ---- Stats ---------------------------------------------------------------------------

    @Override
    public GunType gunType() {
        return type;
    }

    @Override
    public GunTier gunTier() {
        return tier;
    }

    /** Shot spread (standard deviation, degrees). */
    public float bulletStdDev() {
        return type.spreadFor(tier);
    }

    /** Muzzle velocity, blocks per second. */
    public float bulletSpeed() {
        return type.velocity;
    }

    public float damage() {
        return type.damageFor(tier);
    }

    /** Damage for one shot by this shooter with this stack (before multipliers); a hook for per-shot changes. */
    public float shotDamage(ItemStack stack, LivingEntity shooter) {
        return damage();
    }

    public int pelletCount() {
        return type.pellets;
    }

    public boolean twoHanded() {
        return !type.oneHanded;
    }

    /** Spread (standard deviation, degrees) for a shot fired now; the revolver's grows when fanned. */
    public float shotSpread(ItemStack stack, LivingEntity shooter) {
        return bulletStdDev();
    }

    /** One bullet's direction: the gun's spread applied (Gaussian; clip guns use a hard cone while fanning). */
    public Vec3 spreadShot(Vec3 direction, RandomSource random, float spread, ItemStack stack, LivingEntity shooter) {
        return addSpread(direction, random, spread);
    }

    /** Loading stages for this stack (clip guns override it). */
    public int loadingStages(ItemStack stack) {
        return type.stages;
    }

    /** How mobs fight with this gun (see {@link GunType#mobTactics()}). */
    public GunType.Tactics mobTactics() {
        return type.mobTactics();
    }

    /** First-person melee swing is the carbine's bayonet thrust. */
    public boolean hasBayonetJab() {
        return type == GunType.CARBINE;
    }

    /** After a shot: single-shot guns are empty again (clip guns count down their rounds). */
    public void onShotFired(LivingEntity entity, ItemStack stack) {
        setLoaded(stack, false);
    }

    /** Share of normal bullet drop removed (0 = full drop). */
    public float bulletDropReduction() {
        return 0.0f;
    }

    /** Durability lost per melee hit or block broken with the gun. */
    public int hitDurabilityDamage() {
        return 1;
    }

    public SoundEvent fireSound(ItemStack stack) {
        return RealmSounds.fire(type);
    }

    // ---- Who can use it ------------------------------------------------------------------

    /** Guns don't work with your head underwater or in lava (creative players excepted). */
    public static boolean canUse(LivingEntity entity) {
        if (entity instanceof Player player && player.getAbilities().instabuild) {
            return true;
        }
        return !entity.isEyeInFluid(FluidTags.WATER) && !entity.isEyeInFluid(FluidTags.LAVA);
    }

    /** Two-handed guns only work from the main hand; one-handed ones from the offhand too, unless the main hand holds a two-handed gun. */
    public boolean canUseFrom(LivingEntity entity, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            return true;
        }
        if (twoHanded()) {
            return false;
        }
        ItemStack main = entity.getMainHandItem();
        return !(main.getItem() instanceof GunItem mainGun) || !mainGun.twoHanded();
    }

    public static boolean isInHand(LivingEntity entity, InteractionHand hand) {
        ItemStack stack = entity.getItemInHand(hand);
        return !stack.isEmpty() && stack.getItem() instanceof GunItem gun && gun.canUseFrom(entity, hand);
    }

    public static boolean isHoldingGun(LivingEntity entity) {
        return getHoldingHand(entity) != null;
    }

    public static @Nullable InteractionHand getHoldingHand(LivingEntity entity) {
        if (isInHand(entity, InteractionHand.MAIN_HAND)) {
            return InteractionHand.MAIN_HAND;
        }
        if (isInHand(entity, InteractionHand.OFF_HAND)) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    /** Where the muzzle is relative to the eyes: a little to the gun's side and down. */
    public Vec3 smokeOffsetFor(LivingEntity entity, InteractionHand hand) {
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? entity.getMainArm() : entity.getMainArm().getOpposite();
        return smokeOffsetFor(entity, arm);
    }

    public Vec3 smokeOffsetFor(LivingEntity entity, HumanoidArm arm) {
        boolean right = arm == HumanoidArm.RIGHT;
        Vec3 side = Vec3.directionFromRotation(0.0f, entity.getYRot() + (right ? 90 : -90));
        Vec3 down = Vec3.directionFromRotation(entity.getXRot() + 90.0f, entity.getYRot());
        return side.add(down).scale(0.15);
    }

    // ---- Loading -------------------------------------------------------------------------

    /** (stages, ticks per stage) for this gun stack. */
    public static Pair<Integer, Integer> getLoadingDuration(ItemStack stack) {
        int stages = stack.getItem() instanceof GunItem gun ? gun.loadingStages(stack) : 1;
        return Pair.of(stages, Math.round(20f * GunType.LOADING_STAGE_SECONDS));
    }

    /** Ticks of loading left for this stack. */
    public static int reloadDuration(ItemStack stack) {
        Pair<Integer, Integer> duration = getLoadingDuration(stack);
        int remaining = 1 + duration.getLeft() - getLoadingStage(stack);
        return Math.max(0, remaining) * duration.getRight();
    }

    public static boolean isAmmo(ItemStack stack) {
        return Cartridge.isCartridge(stack);
    }

    /** Offhand first, then main hand, then the rest of the inventory. */
    public static ItemStack findAmmo(Player player) {
        ItemStack stack = player.getItemBySlot(EquipmentSlot.OFFHAND);
        if (isAmmo(stack)) {
            return stack;
        }
        stack = player.getItemBySlot(EquipmentSlot.MAINHAND);
        if (isAmmo(stack)) {
            return stack;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); ++i) {
            stack = player.getInventory().getItem(i);
            if (isAmmo(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public static boolean checkAmmo(Player player, ItemStack gun) {
        return player.getAbilities().instabuild || !findAmmo(player).isEmpty();
    }

    /** Uses up one cartridge, remembering on the gun which kind it was. */
    public static void consumeAmmo(Player player, ItemStack gun) {
        ItemStack ammo = findAmmo(player);
        // Stamped in creative too, so a held special cartridge still picks the round.
        RealmArmsAmmo.stampLoadedCartridge(gun, ammo);
        if (player.getAbilities().instabuild) {
            return;
        }
        ammo.shrink(1);
        if (ammo.isEmpty()) {
            player.getInventory().removeItem(ammo);
        }
    }

    /**
     * Mobs (pillagers, guards): when a load finishes, a special cartridge in the hand that isn't
     * holding the gun is loaded and one is spent. Otherwise they load a free standard round, as
     * before (mobs never need standard ammo).
     */
    public static void mobLoadAmmo(LivingEntity entity, ItemStack gun) {
        ItemStack other = entity.getMainHandItem() == gun ? entity.getOffhandItem() : entity.getMainHandItem();
        Cartridge cartridge = Cartridge.fromAmmo(other);
        if (cartridge != Cartridge.STANDARD) {
            RealmArmsAmmo.stampLoadedCartridge(gun, other);
            other.shrink(1);
        } else {
            RealmArmsAmmo.stampLoadedCartridge(gun, ItemStack.EMPTY);
        }
    }

    public static boolean isReady(ItemStack stack) {
        return isLoaded(stack) && getLoadingStage(stack) == 0;
    }

    public static boolean isLoaded(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(LOADED));
    }

    public static void setLoaded(ItemStack stack, boolean loaded) {
        if (loaded) {
            stack.set(LOADED, true);
        } else {
            stack.remove(LOADED);
        }
    }

    public static int getLoadingStage(ItemStack stack) {
        Byte stage = stack.get(LOADING_STAGE);
        return stage != null ? stage : 0;
    }

    public static void setLoadingStage(ItemStack stack, int stage) {
        if (stage > 0) {
            stack.set(LOADING_STAGE, (byte) stage);
        } else {
            stack.remove(LOADING_STAGE);
        }
    }

    // ---- Player use ----------------------------------------------------------------------

    /** Right-click: {@link #baseUse}. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return baseUse(level, player, hand);
    }

    /** Fire if loaded; otherwise start (or continue) loading. */
    public InteractionResult baseUse(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canUse(player) || !canUseFrom(player, hand)) {
            return InteractionResult.PASS;
        }
        if (isLoaded(stack)) {
            if (!level.isClientSide()) {
                Vec3 direction = Vec3.directionFromRotation(player.getXRot(), player.getYRot());
                fire(player, stack, direction, smokeOffsetFor(player, hand));
            }
            player.playSound(fireSound(stack), 3.5f, type.randomFirePitch());
            setLoaded(stack, false);
            stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            player.releaseUsingItem();
            if (level.isClientSide()) {
                setActiveStack(hand, stack);
            }
            return InteractionResult.CONSUME;
        }
        // A loaded one-handed gun in the offhand fires first.
        if (hand == InteractionHand.MAIN_HAND) {
            ItemStack offhand = player.getOffhandItem();
            if (offhand.getItem() instanceof GunItem offhandGun && isLoaded(offhand)
                    && offhandGun.canUseFrom(player, InteractionHand.OFF_HAND)) {
                return InteractionResult.PASS;
            }
        }
        if (getLoadingStage(stack) == 0) {
            if (!checkAmmo(player, stack)) {
                return InteractionResult.FAIL;
            }
            setLoadingStage(stack, 1);
        } else {
            int stages = getLoadingDuration(stack).getLeft();
            if (getLoadingStage(stack) == stages) {
                setLoadingStage(stack, stages + 1);
            }
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.CROSSBOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    /** Loading ticks (sounds, finishing the load): see GunLoading. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksLeft) {
        GunLoading.onUseTick(this, type, level, entity, stack, ticksLeft);
    }

    /** Letting go keeps every finished stage; an unfinished first stage is undone. */
    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int ticksLeft) {
        if (isLoaded(stack)) {
            setLoadingStage(stack, 0);
        } else {
            int usingTicks = getUseDuration(stack, entity) - ticksLeft;
            int ticksPerStage = getLoadingDuration(stack).getRight();
            int previous = getLoadingStage(stack);
            int stage = previous + usingTicks / ticksPerStage;
            if (previous == 1) {
                if (stage == 1) {
                    setLoadingStage(stack, 0);
                } else if (entity instanceof Player player) {
                    consumeAmmo(player, stack);
                }
            }
            setLoadingStage(stack, stage);
        }
        return true;
    }

    /** Server: spawns the shot (see GunFiring). */
    public void fire(LivingEntity entity, ItemStack stack, Vec3 direction, Vec3 smokeOffset) {
        GunFiring.fire(this, this, entity, stack, direction, smokeOffset);
    }

    // ---- Mob use -------------------------------------------------------------------------

    /** Direction to fire at a target, allowing for bullet drop. */
    public Vec3 aimAt(LivingEntity entity, LivingEntity target) {
        double dist = entity.distanceTo(target);
        double ticks = 20.0 * dist / bulletSpeed();
        double drop = 0.5 * ticks * ticks * 0.05 * (1.0 - bulletDropReduction());
        Vec3 pos = new Vec3(target.getX(), 0.5 * (target.getEyeY() + target.getY(0.5)), target.getZ());
        return new Vec3(pos.x - entity.getX(), pos.y + drop - entity.getEyeY(), pos.z - entity.getZ()).normalize();
    }

    /** Mobs load instantly into stage 1 and then use the item like a player holding right-click. */
    public static void mobReload(LivingEntity entity, InteractionHand hand) {
        if (entity.isUsingItem() || entity.level().isClientSide()) {
            return;
        }
        ItemStack stack = entity.getItemInHand(hand);
        if (isLoaded(stack)) {
            return;
        }
        setLoadingStage(stack, 1);
        entity.startUsingItem(hand);
    }

    public void mobUse(LivingEntity entity, InteractionHand hand, Vec3 direction) {
        ItemStack stack = entity.getItemInHand(hand);
        HumanoidArm arm = hand == InteractionHand.OFF_HAND ? entity.getMainArm().getOpposite() : entity.getMainArm();
        mobUse(entity, stack, direction, smokeOffsetFor(entity, arm));
    }

    public void mobUse(LivingEntity entity, ItemStack stack, Vec3 direction, Vec3 smokeOffset) {
        if (entity.level().isClientSide() || !isLoaded(stack)) {
            return;
        }
        fire(entity, stack, direction, smokeOffset);
        SoundEvent sound = entity instanceof CustomGunner custom ? custom.gunFireSound(stack) : null;
        float volume = entity instanceof CustomGunner custom ? custom.gunFireVolume() : 3.5f;
        entity.playSound(sound != null ? sound : fireSound(stack), volume, type.randomFirePitch());
        onShotFired(entity, stack);
    }

    // ---- Spread --------------------------------------------------------------------------

    /** Gaussian spread: most shots near the aim point, capped at 4 standard deviations. */
    public static Vec3 addSpread(Vec3 direction, RandomSource random, float spreadStdDev) {
        float gaussian = Math.min(4.0f, Math.abs((float) random.nextGaussian()));
        return applyError(direction, random, (float) Math.toRadians(spreadStdDev) * gaussian);
    }

    /** Uniform spread up to the given angle (degrees). */
    public static Vec3 addUniformSpread(Vec3 direction, RandomSource random, float spread) {
        return applyError(direction, random, (float) Math.toRadians(spread) * random.nextFloat());
    }

    public static Vec3 applyError(Vec3 direction, RandomSource random, float coneAngle) {
        Vec3 n1;
        Vec3 n2;
        if (Math.abs(direction.x) < 1.0E-5 && Math.abs(direction.z) < 1.0E-5) {
            n1 = new Vec3(1.0, 0.0, 0.0);
            n2 = new Vec3(0.0, 0.0, 1.0);
        } else {
            n1 = new Vec3(-direction.z, 0.0, direction.x).normalize();
            n2 = direction.cross(n1);
        }
        float angle = (float) Math.PI * 2 * random.nextFloat();
        return direction.scale(Mth.cos(coneAngle))
                .add(n1.scale(Mth.sin(coneAngle) * Mth.sin(angle)))
                .add(n2.scale(Mth.sin(coneAngle) * Mth.cos(angle)));
    }

    // ---- Melee, durability, inventory --------------------------------------------------

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(hitDurabilityDamage(), attacker, EquipmentSlot.MAINHAND);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entity) {
        if (state.getDestroySpeed(level, pos) != 0.0f) {
            stack.hurtAndBreak(hitDurabilityDamage(), entity, EquipmentSlot.MAINHAND);
        }
        return false;
    }

    /** Converts guns saved with Musket Mod-era loading data (see LegacyMusket). */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        LegacyMusket.migrateGun(stack);
    }

    // ---- Client bookkeeping --------------------------------------------------------------

    public static @Nullable ItemStack getActiveStack(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? activeMainHandStack : activeOffhandStack;
    }

    public static void setActiveStack(InteractionHand hand, @Nullable ItemStack stack) {
        if (hand == InteractionHand.MAIN_HAND) {
            activeMainHandStack = stack;
        } else {
            activeOffhandStack = stack;
        }
    }
}
