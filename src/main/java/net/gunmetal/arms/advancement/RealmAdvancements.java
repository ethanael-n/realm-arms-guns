package net.gunmetal.arms.advancement;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.item.ItemStack;
import net.gunmetal.arms.entity.RealmBulletEntity;
import net.gunmetal.arms.guns.GunType;
import net.gunmetal.arms.guns.RealmGun;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import static net.gunmetal.arms.RealmArmsWeapons.MOD_ID;

/**
 * Awards the Realm Arms advancements that need game logic (data/gunmetal/advancement/*.json use
 * an "impossible" criterion named "code" for these). Gun Mettle and Highlander are plain data.
 * Only players' shots count; dispensers and mobs never earn anything.
 */
public final class RealmAdvancements {
    // ---- Tuning -------------------------------------------------------------------------
    public static final double SNIPE_DISTANCE = 100.0;
    public static final double MAGGOTS_DISTANCE = 50.0;
    public static final int CYCLOPS_KILLS = 5;
    public static final int VOLLEY_KILLS = 5;
    public static final int IGNITED_MOBS = 5;
    /** Blocks per second (= m/s) the shooter must have been moving when the shot was fired. */
    public static final double FAST_SPEED = 30.0;
    // -------------------------------------------------------------------------------------

    /** Set on a pillager the first time it ever targets anything (see mixin/MobMixin). */
    public static final String AGGROED_TAG = "gunmetal.aggroed";

    /** Per player and counter: the shot being counted and what it has done so far. */
    private static final class ShotTally {
        int shotId;
        final Set<Integer> entities = new HashSet<>();

        /** Adds this entity for this shot and returns how many distinct entities the shot has now. */
        int add(int shot, Entity entity) {
            if (shot != shotId) {
                shotId = shot;
                entities.clear();
            }
            entities.add(entity.getId());
            return entities.size();
        }
    }

    private static final Map<ServerPlayer, ShotTally> CYCLOPS = new WeakHashMap<>();
    private static final Map<ServerPlayer, ShotTally> VOLLEY = new WeakHashMap<>();
    private static final Map<ServerPlayer, ShotTally> IGNITED = new WeakHashMap<>();

    private RealmAdvancements() {
    }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(RealmAdvancements::onDeath);
    }

    private static void onDeath(LivingEntity victim, DamageSource source) {
        if (!(source.getDirectEntity() instanceof RealmBulletEntity bullet) || bullet.gunType == null) {
            return;
        }
        Entity killer = source.getEntity();
        if (!(killer instanceof ServerPlayer player)) {
            return;
        }
        GunType gun = bullet.gunType;

        if (victim == player) {
            if (gun == GunType.HAND_CANNON && source.is(DamageTypeTags.IS_EXPLOSION)) {
                award(player, "bid_farewell_cruel_world");
            }
            return;
        }
        boolean monster = victim instanceof Enemy;

        if (monster && gun == GunType.RIFLE && bullet.firedFrom != null
                && bullet.firedFrom.distanceTo(victim.position()) > SNIPE_DISTANCE) {
            award(player, "sniping_a_good_job_mate");
        }
        if (monster && gun == GunType.HAND_CANNON && bullet.firedFrom != null
                && bullet.firedFrom.distanceTo(victim.position()) >= MAGGOTS_DISTANCE) {
            award(player, "you_are_all_maggots");
        }
        if (monster && gun == GunType.HAND_CANNON
                && tally(CYCLOPS, player).add(bullet.shotId, victim) >= CYCLOPS_KILLS) {
            award(player, "black_scottish_cyclops");
        }
        if (monster && gun == GunType.VOLLEYGUN
                && tally(VOLLEY, player).add(bullet.shotId, victim) >= VOLLEY_KILLS) {
            award(player, "cry_some_more");
        }
        if (monster && bullet.misfire) {
            award(player, "random_crits");
        }
        if (gun == GunType.PISTOL && victim instanceof Pillager && !victim.entityTags().contains(AGGROED_TAG)) {
            award(player, "intruder_alert");
        }
        if (gun == GunType.RIFLE && (holdsRifle(victim.getMainHandItem()) || holdsRifle(victim.getOffhandItem()))) {
            award(player, "second_scoper");
        }
        if (victim instanceof Mob && bullet.shooterSpeed > FAST_SPEED) {
            award(player, "runnin_circles");
        }
    }

    /** Called when a Blaze cartridge sets something on fire. */
    public static void onBlazeIgnite(@Nullable Entity shooter, Entity target, int shotId) {
        if (shooter instanceof ServerPlayer player && target instanceof Mob
                && tally(IGNITED, player).add(shotId, target) >= IGNITED_MOBS) {
            award(player, "w_m1");
        }
    }

    /** Realm Arms rifles only. */
    private static boolean holdsRifle(ItemStack stack) {
        return stack.getItem() instanceof RealmGun gun && gun.gunType() == GunType.RIFLE;
    }

    private static ShotTally tally(Map<ServerPlayer, ShotTally> map, ServerPlayer player) {
        return map.computeIfAbsent(player, p -> new ShotTally());
    }

    private static void award(ServerPlayer player, String name) {
        AdvancementHolder advancement = player.level().getServer().getAdvancements()
                .get(Identifier.fromNamespaceAndPath(MOD_ID, name));
        if (advancement != null) {
            player.getAdvancements().award(advancement, "code");
        }
    }
}
