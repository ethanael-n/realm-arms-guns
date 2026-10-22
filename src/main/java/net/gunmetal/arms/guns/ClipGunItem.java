package net.gunmetal.arms.guns;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.gunmetal.arms.ammo.Cartridge;
import net.gunmetal.arms.registry.RealmSounds;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Clip guns ("revolving weapons"): the revolver and the repeater. Untiered, unrepairable, no attachments.
 *
 * Loading: from empty, {@link #STAGES} stages of {@link GunType#LOADING_STAGE_SECONDS}. The first and
 * last stages load nothing; each stage in between puts one cartridge into the clip ({@link #CLIP}
 * rounds). The first cartridge decides the kind for the whole clip: later stages only take that same
 * kind, and loading stops (keeping its progress) when there's none left. A partly filled clip can't
 * fire, and a gun with rounds left can't be reloaded: right-click fires while it has rounds. Each
 * round needs its own click.
 *
 * Fan the hammer (revolver only): right-click while crouching with it loaded. One trigger pull empties
 * the clip: the first round at once, the rest every fanInterval ticks, whatever the player does next
 * (only the gun leaving that hand, or the player dying, stops it). Every fanned round lands anywhere in
 * a hard cone of fanCone degrees instead of the normal spread. Mobs fan when they say so
 * ({@link CustomGunner#isFanning}; their AI paces the shots). First person: the gun is held
 * FAN_DROP_PIXELS lower while crouching with it loaded and during a fan (see GunClient).
 *
 * State on the stack: gunmetal:loaded (rounds left), gunmetal:loading_stage (as for every gun) and
 * gunmetal:clip_rounds (cartridges in the clip while loading; rounds left once loaded; doesn't
 * trigger the hand's swap animation). The time of the last shot is kept per shooter and hand on each
 * side separately (never on the stack), so the client and server never disagree about it.
 */
public class ClipGunItem extends RealmGunItem {
    public static final DataComponentType<Byte> CLIP_ROUNDS = new DataComponentType.Builder<Byte>()
            .persistent(Codec.BYTE).networkSynchronized(ByteBufCodecs.BYTE).ignoreSwapAnimation().build();
    /** Set on the stack while a fan is running (not saved; synced for the crosshair and the gun pose). */
    public static final DataComponentType<net.minecraft.util.Unit> FANNING = new DataComponentType.Builder<net.minecraft.util.Unit>()
            .networkSynchronized(net.minecraft.network.codec.StreamCodec.unit(net.minecraft.util.Unit.INSTANCE))
            .ignoreSwapAnimation().build();
    /** 2.18.0 only; kept registered so guns saved with it still load (it is stripped when they tick). */
    public static final DataComponentType<Long> LAST_SHOT = new DataComponentType.Builder<Long>()
            .persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG).ignoreSwapAnimation().build();

    // ---- Tuning -------------------------------------------------------------------------
    /** Rounds per clip and loading stages to fill it (stage 1 and the last stage load nothing). */
    public static final int CLIP = 6, STAGES = 8;
    /** Durability of both clip guns (one point per shot). */
    public static final int DURABILITY = 1351;
    /** First person: the gun drops this many model pixels while fanning. */
    public static final float FAN_DROP_PIXELS = 2.0f;
    /** Fanned rounds (players' and mobs') do this much less damage than the gun's own (1.0.2). */
    public static final float FAN_DAMAGE_PENALTY = 4.0f;
    /** The server lets a shot through this many ticks early (packet timing). */
    private static final int SERVER_SLACK_TICKS = 2;
    /** How long (ticks) a "client fired" note waits for its use packet (they normally arrive together). */
    private static final int CLIENT_SHOT_WINDOW_TICKS = 1;
    // -------------------------------------------------------------------------------------

    public enum Kind {
        //        id          gun type        mobs fight like  damage spread interval fanInterval fanCone
        REVOLVER("revolver", GunType.PISTOL, GunType.PISTOL, 15f, 1.6f, 10, 3, 8.0f),
        REPEATER("repeater", GunType.CARBINE, GunType.ARQUEBUS, 18f, 0.8f, 15, 0, 0.0f);

        public final String id;
        public final GunType type;
        public final GunType tactics;
        /** Damage before the mob multiplier and difficulty (falls off to 0.9x at 32 blocks, like the pistol/carbine). */
        public final float damage;
        /** Normal spread (standard deviation, degrees). */
        public final float spread;
        /** Fewest ticks between two shots from the same hand; ticks between fanned rounds (0 = can't fan). */
        public final int shotInterval, fanInterval;
        /** Fanning: every shot lands within this many degrees of the aim (hard cone). */
        public final float fanCone;

        Kind(String id, GunType type, GunType tactics, float damage, float spread, int shotInterval, int fanInterval,
             float fanCone) {
            this.id = id;
            this.type = type;
            this.tactics = tactics;
            this.damage = damage;
            this.spread = spread;
            this.shotInterval = shotInterval;
            this.fanInterval = fanInterval;
            this.fanCone = fanCone;
        }

        public boolean canFan() {
            return fanInterval > 0;
        }
    }

    /** Last shot (that side's game time) per shooter, main hand [0] / offhand [1]. One map per side (thread). */
    private static final Map<Entity, long[]> LAST_SHOT_SERVER = new WeakHashMap<>();
    private static final Map<Entity, long[]> LAST_SHOT_CLIENT = new WeakHashMap<>();
    /** Server: when the player's client last said it fired a round, main hand [0] / offhand [1]. */
    private static final Map<Entity, long[]> CLIENT_SHOTS = new WeakHashMap<>();

    public final Kind kind;

    public ClipGunItem(Properties properties, Kind kind) {
        super(properties, kind.type, GunTier.BRASS);
        this.kind = kind;
    }

    // ---- Stats ---------------------------------------------------------------------------

    @Override
    public float damage() {
        return kind.damage;
    }

    @Override
    public float bulletStdDev() {
        return kind.spread;
    }

    /** Fanning: anywhere in the hard cone; otherwise the gun's normal spread. */
    @Override
    public Vec3 spreadShot(Vec3 direction, RandomSource random, float spread, ItemStack stack, LivingEntity shooter) {
        if (isFanning(shooter, stack)) {
            return addUniformSpread(direction, random, kind.fanCone);
        }
        return super.spreadShot(direction, random, spread, stack, shooter);
    }

    /** Fanned rounds hit FAN_DAMAGE_PENALTY softer. */
    @Override
    public float shotDamage(ItemStack stack, LivingEntity shooter) {
        float base = super.shotDamage(stack, shooter);
        return isFanning(shooter, stack) ? Math.max(0f, base - FAN_DAMAGE_PENALTY) : base;
    }

    /** This round is a fanned one: a player's fan in progress, or a mob that says so. */
    public static boolean isFanning(LivingEntity shooter, ItemStack stack) {
        if (!(stack.getItem() instanceof ClipGunItem clip) || !clip.kind.canFan() || !isLoaded(stack)) {
            return false;
        }
        if (shooter instanceof Player) {
            return stack.has(FANNING);
        }
        return shooter instanceof CustomGunner custom && custom.isFanning(stack);
    }

    /** Client visuals (fan crosshair, gun held low): a fan running, or crouching with a loaded revolver. */
    public static boolean showsFanPose(Player player, ItemStack stack) {
        if (!(stack.getItem() instanceof ClipGunItem clip) || !clip.kind.canFan()) {
            return false;
        }
        return stack.has(FANNING) || (player.isCrouching() && isLoaded(stack));
    }

    @Override
    public boolean twoHanded() {
        return kind == Kind.REPEATER;
    }

    @Override
    public boolean hasBayonetJab() {
        return false;
    }

    @Override
    public int loadingStages(ItemStack stack) {
        return STAGES;
    }

    @Override
    public GunType.Tactics mobTactics() {
        return kind.tactics.mobTactics();
    }

    /** Fewest ticks between a player's trigger pulls with this gun. */
    public int shotInterval(LivingEntity shooter, ItemStack stack) {
        return kind.shotInterval;
    }

    @Override
    public SoundEvent fireSound(ItemStack stack) {
        return kind == Kind.REVOLVER ? RealmSounds.REVOLVER_FIRE : RealmSounds.REPEATER_FIRE;
    }

    private SoundEvent loadFirstSound() {
        return kind == Kind.REVOLVER ? RealmSounds.REVOLVER_LOAD_FIRST : RealmSounds.REPEATER_LOAD_FIRST;
    }

    private SoundEvent roundSound() {
        return kind == Kind.REVOLVER ? RealmSounds.REVOLVER_ROUND : RealmSounds.REPEATER_ROUND;
    }

    private SoundEvent readySound() {
        return kind == Kind.REVOLVER ? RealmSounds.REVOLVER_READY : RealmSounds.REPEATER_READY;
    }

    // ---- Clip state ----------------------------------------------------------------------

    public static int rounds(ItemStack stack) {
        Byte rounds = stack.get(CLIP_ROUNDS);
        return rounds != null ? rounds : 0;
    }

    public static void setRounds(ItemStack stack, int rounds) {
        if (rounds > 0) {
            stack.set(CLIP_ROUNDS, (byte) rounds);
        } else {
            stack.remove(CLIP_ROUNDS);
        }
    }

    /** Rounds in the clip once this many stages are done (stage 1 loads nothing; the last stage neither). */
    private static int roundsAfter(int finishedStages) {
        return Math.min(CLIP, Math.max(0, finishedStages - 1));
    }

    /** Whether the stage after this many finished ones puts a cartridge in. */
    private static boolean nextStageLoads(int finishedStages) {
        int next = finishedStages + 1;
        return next >= 2 && next <= STAGES - 1;
    }

    private static long[] lastShots(LivingEntity shooter) {
        Map<Entity, long[]> map = shooter.level().isClientSide() ? LAST_SHOT_CLIENT : LAST_SHOT_SERVER;
        return map.computeIfAbsent(shooter, e -> new long[]{Long.MIN_VALUE / 2, Long.MIN_VALUE / 2});
    }

    private static int handIndex(LivingEntity shooter, ItemStack stack) {
        return shooter.getOffhandItem() == stack ? 1 : 0;
    }

    /** Server: the player's client fired a round from this hand (ClipShotPayload, arrives just before the use packet). */
    public static void clientFired(Player player, boolean offhand) {
        CLIENT_SHOTS.computeIfAbsent(player, e -> new long[]{Long.MIN_VALUE / 2, Long.MIN_VALUE / 2})
                [offhand ? 1 : 0] = player.level().getGameTime();
    }

    /** Server: whether the client fired this click (and uses the note up). */
    private static boolean takeClientShot(Player player, ItemStack stack) {
        long[] shots = CLIENT_SHOTS.get(player);
        if (shots == null) {
            return false;
        }
        int hand = handIndex(player, stack);
        boolean fired = player.level().getGameTime() - shots[hand] <= CLIENT_SHOT_WINDOW_TICKS;
        shots[hand] = Long.MIN_VALUE / 2;
        return fired;
    }

    /** True if this hand has waited long enough since its last shot (checked on each side with that side's clock). */
    private boolean readyToFire(LivingEntity shooter, ItemStack stack) {
        long since = shooter.level().getGameTime() - lastShots(shooter)[handIndex(shooter, stack)];
        int slack = shooter.level().isClientSide() ? 0 : SERVER_SLACK_TICKS;
        return since >= shotInterval(shooter, stack) - slack;
    }

    /** Counts a round off; an empty clip is unloaded and ready to reload. */
    @Override
    public void onShotFired(LivingEntity entity, ItemStack stack) {
        lastShots(entity)[handIndex(entity, stack)] = entity.level().getGameTime();
        int left = rounds(stack) - 1;
        setRounds(stack, left);
        if (left <= 0) {
            setLoaded(stack, false);
            setLoadingStage(stack, 0);
            stack.remove(FANNING);
        }
    }

    private void finishLoad(Level level, LivingEntity entity, ItemStack stack) {
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), readySound(), entity.getSoundSource(), 0.8f, 1.0f);
        if (!(entity instanceof Player)) {
            mobLoadAmmo(entity, stack);
        }
        setRounds(stack, CLIP);
        setLoaded(stack, true);
    }

    // ---- Cartridges ----------------------------------------------------------------------

    /** Offhand first, then main hand, then the rest of the inventory: a cartridge of this kind. */
    private static ItemStack findAmmo(Player player, Cartridge kind) {
        ItemStack stack = player.getItemBySlot(EquipmentSlot.OFFHAND);
        if (isAmmo(stack) && Cartridge.fromAmmo(stack) == kind) {
            return stack;
        }
        stack = player.getItemBySlot(EquipmentSlot.MAINHAND);
        if (isAmmo(stack) && Cartridge.fromAmmo(stack) == kind) {
            return stack;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); ++i) {
            stack = player.getInventory().getItem(i);
            if (isAmmo(stack) && Cartridge.fromAmmo(stack) == kind) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Whether the next cartridge for this clip is available (any kind for the first). */
    public static boolean hasRound(Player player, ItemStack gun) {
        if (rounds(gun) >= CLIP) {
            return false;
        }
        if (player.getAbilities().instabuild) {
            return true;
        }
        return rounds(gun) == 0 ? !findAmmo(player).isEmpty() : !findAmmo(player, Cartridge.loadedIn(gun)).isEmpty();
    }

    /** Puts one cartridge into the clip; the first one sets the clip's kind. */
    private static boolean takeRound(Player player, ItemStack gun) {
        if (rounds(gun) == 0) {
            if (findAmmo(player).isEmpty() && !player.getAbilities().instabuild) {
                return false;
            }
            consumeAmmo(player, gun);
            return true;
        }
        if (player.getAbilities().instabuild) {
            return true;
        }
        ItemStack ammo = findAmmo(player, Cartridge.loadedIn(gun));
        if (ammo.isEmpty()) {
            return false;
        }
        ammo.shrink(1);
        if (ammo.isEmpty()) {
            player.getInventory().removeItem(ammo);
        }
        return true;
    }

    /**
     * Server, players: puts in every cartridge owed for the stages finished so far. Returns the stage
     * to continue from: unchanged, or pulled back to the start of the stage whose cartridge ran out.
     */
    private static int settle(Player player, ItemStack gun, int stage) {
        int owed = roundsAfter(stage - 1);
        while (rounds(gun) < owed) {
            if (!takeRound(player, gun)) {
                return rounds(gun) + 2; // the cartridge stage that couldn't finish
            }
            setRounds(gun, rounds(gun) + 1);
        }
        return stage;
    }

    // ---- Player use ----------------------------------------------------------------------

    @Override
    public InteractionResult baseUse(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canUse(player) || !canUseFrom(player, hand)) {
            return InteractionResult.PASS;
        }
        if (isLoaded(stack)) {
            // The client decides which clicks fire (it plays the shot's sound and effects itself); the
            // server fires exactly those, checked against its own clock with a little slack. Before
            // 1.0.1 each side decided alone, so a click the client refused could still fire on the
            // server: a "ghost shot" with no sound or flash for the shooter.
            boolean clientFired = level.isClientSide() || takeClientShot(player, stack);
            if (stack.has(FANNING) || !clientFired || !readyToFire(player, stack)) {
                return InteractionResult.FAIL; // a fan is still emptying the clip, too soon, or the client didn't fire
            }
            boolean fan = kind.canFan() && player.isCrouching();
            if (fan) {
                stack.set(FANNING, net.minecraft.util.Unit.INSTANCE);
            }
            if (!level.isClientSide()) {
                Vec3 direction = Vec3.directionFromRotation(player.getXRot(), player.getYRot());
                fire(player, stack, direction, smokeOffsetFor(player, hand));
            }
            player.playSound(fireSound(stack), 3.5f, type.randomFirePitch());
            onShotFired(player, stack);
            if (fan && !level.isClientSide() && isLoaded(stack)) {
                FAN_BURSTS.add(new FanBurst(this, player, stack));
            }
            stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            player.releaseUsingItem();
            if (level.isClientSide()) {
                setActiveStack(hand, stack);
                FireHooks.clientClipShot();
                FireHooks.clientClipShotSent(hand == InteractionHand.OFF_HAND); // tells the server this click fired
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
        // A full clip only needs its closing stage (no cartridge): don't ask for ammo then. (2.18.3 fix:
        // letting go after the last cartridge went in used to leave the gun unable to reload or fire.)
        if (rounds(stack) < CLIP && !hasRound(player, stack)) {
            return InteractionResult.FAIL;
        }
        if (getLoadingStage(stack) == 0) {
            setLoadingStage(stack, 1);
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksLeft) {
        if (isLoaded(stack)) {
            return;
        }
        int ticksPerStage = getLoadingDuration(stack).getRight();
        int usingTicks = getUseDuration(stack, entity) - ticksLeft;
        int stage = getLoadingStage(stack) + usingTicks / ticksPerStage;
        boolean stageDone = usingTicks > 0 && usingTicks % ticksPerStage == 0;
        int finished = stage - 1;

        if (stageDone && finished < STAGES) {
            entity.playSound(finished == 1 ? loadFirstSound() : roundSound(), 0.8f, 1.0f);
        }
        if (level.isClientSide()) {
            if (entity instanceof Player) {
                setActiveStack(entity.getUsedItemHand(), stack);
            }
            return;
        }
        if (!stageDone) {
            return;
        }
        if (entity instanceof Player player) {
            int settled = settle(player, stack, stage);
            if (settled != stage) {
                // A cartridge of the clip's kind ran out: that stage starts over.
                setLoadingStage(stack, settled);
                player.stopUsingItem();
                return;
            }
            if (finished < STAGES && nextStageLoads(finished) && !hasRound(player, stack)) {
                // Nothing to put in next: stop here, keeping the progress.
                setLoadingStage(stack, stage);
                player.stopUsingItem();
                return;
            }
        } else {
            setRounds(stack, roundsAfter(finished)); // mobs load free rounds
        }
        if (finished >= STAGES) {
            finishLoad(level, entity, stack);
        }
    }

    /** Letting go keeps every finished stage (and the cartridges they put in); an unfinished first stage is undone. */
    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int ticksLeft) {
        if (isLoaded(stack)) {
            setLoadingStage(stack, 0);
            return true;
        }
        int usingTicks = getUseDuration(stack, entity) - ticksLeft;
        int ticksPerStage = getLoadingDuration(stack).getRight();
        int stage = getLoadingStage(stack) + usingTicks / ticksPerStage;
        if (stage <= 1 && rounds(stack) == 0) {
            setLoadingStage(stack, 0);
            return true;
        }
        if (!level.isClientSide() && entity instanceof Player player) {
            stage = settle(player, stack, stage);
            if (stage - 1 >= STAGES && rounds(stack) >= CLIP) {
                finishLoad(level, entity, stack);
                setLoadingStage(stack, 0);
                return true;
            }
        }
        setLoadingStage(stack, Math.min(stage, STAGES));
        return true;
    }

    /** Strips the 2.18.0 last-shot stamp (it made the hand dip on every shot) and any fan marker left without a fan. */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        if (stack.has(LAST_SHOT)) {
            stack.remove(LAST_SHOT);
        }
        if (stack.has(FANNING) && FAN_BURSTS.stream().noneMatch(b -> b.stack == stack)) {
            stack.remove(FANNING);
        }
    }

    // ---- Fan the hammer (server) ------------------------------------------------------------

    private static final java.util.List<FanBurst> FAN_BURSTS = new java.util.ArrayList<>();

    private static final class FanBurst {
        final ClipGunItem gun;
        final Player player;
        final ItemStack stack;
        int delay;

        FanBurst(ClipGunItem gun, Player player, ItemStack stack) {
            this.gun = gun;
            this.player = player;
            this.stack = stack;
            this.delay = gun.kind.fanInterval;
        }
    }

    /** Fires the rest of each running fan, one round every fanInterval ticks, where the player is looking. */
    public static void registerFanTicks() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            java.util.Iterator<FanBurst> it = FAN_BURSTS.iterator();
            while (it.hasNext()) {
                FanBurst fan = it.next();
                if (--fan.delay > 0) {
                    continue;
                }
                Player player = fan.player;
                ItemStack stack = fan.stack;
                InteractionHand hand = player.getMainHandItem() == stack ? InteractionHand.MAIN_HAND
                        : player.getOffhandItem() == stack ? InteractionHand.OFF_HAND : null;
                if (hand == null || !player.isAlive() || player.isRemoved() || stack.isEmpty() || !isLoaded(stack)
                        || !(player.level() instanceof ServerLevel level)) {
                    stack.remove(FANNING);
                    it.remove();
                    continue;
                }
                Vec3 direction = Vec3.directionFromRotation(player.getXRot(), player.getYRot());
                fan.gun.fire(player, stack, direction, fan.gun.smokeOffsetFor(player, hand));
                // Heard by everyone, the shooter too (their game didn't predict this round).
                level.playSound(null, player.getX(), player.getY(), player.getZ(), fan.gun.fireSound(stack),
                        player.getSoundSource(), 3.5f, fan.gun.type.randomFirePitch());
                fan.gun.onShotFired(player, stack);
                stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                if (stack.isEmpty() || !isLoaded(stack)) {
                    stack.remove(FANNING);
                    it.remove();
                } else {
                    fan.delay = fan.gun.kind.fanInterval;
                }
            }
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable(isLoaded(stack) ? "tooltip.gunmetal.clip.rounds" : "tooltip.gunmetal.clip.loading",
                rounds(stack), CLIP).withStyle(net.minecraft.ChatFormatting.GRAY));
        Cartridge loaded = Cartridge.loadedIn(stack);
        if (rounds(stack) > 0 && loaded != Cartridge.STANDARD && loaded.item != null) {
            tooltip.accept(Component.translatable("tooltip.gunmetal.clip.kind", new ItemStack(loaded.item).getHoverName())
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }
}
