package net.gunmetal.raids.registry;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.entity.Mob;
import net.gunmetal.raids.entity.Artificer;
import net.gunmetal.raids.entity.ArtificerDrone;
import net.gunmetal.raids.entity.Bombardier;
import net.gunmetal.raids.entity.BrassGolem;
import net.gunmetal.raids.entity.DeployedGolem;
import net.gunmetal.raids.entity.Puppeted;
import net.gunmetal.raids.entity.Aviator;
import net.gunmetal.raids.entity.SiegeBalloon;
import net.gunmetal.raids.entity.ThrownBrassDeployable;
import net.gunmetal.raids.entity.Sharpshooter;
import net.gunmetal.raids.entity.Trooper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static net.gunmetal.raids.RealmRaids.MOD_ID;

public final class RaidEntities {
    public static final List<Item> SPAWN_EGGS = new ArrayList<>();

    public static EntityType<Bombardier> BOMBARDIER;
    public static EntityType<Sharpshooter> SHARPSHOOTER;
    public static EntityType<Trooper> TROOPER;
    public static EntityType<Artificer> ARTIFICER;
    public static EntityType<ArtificerDrone> DRONE;
    public static EntityType<Puppeted> PUPPETED;
    public static EntityType<BrassGolem> BRASS_GOLEM;
    public static EntityType<DeployedGolem> DEPLOYED_GOLEM;
    public static EntityType<ThrownBrassDeployable> THROWN_BRASS_DEPLOYABLE;
    public static EntityType<SiegeBalloon> SIEGE_BALLOON;
    public static EntityType<Aviator> AVIATOR;
    public static EntityType<net.gunmetal.raids.entity.BalloonArrow> BALLOON_ARROW;

    private RaidEntities() {
    }

    public static void register() {
        // Tracking range is in chunks (8 = 128 blocks, like a pillager); the backline mobs get 10 so
        // players can see them at their firing range. It's still capped by the server view distance.
        BOMBARDIER = register("bombardier", EntityType.Builder.of(Bombardier::new, MobCategory.MONSTER)
                .sized(0.6f, 1.95f).clientTrackingRange(10), Bombardier::createAttributes);
        SHARPSHOOTER = register("sharpshooter", EntityType.Builder.of(Sharpshooter::new, MobCategory.MONSTER)
                .sized(0.6f, 1.95f).clientTrackingRange(10), Sharpshooter::createAttributes);
        TROOPER = register("trooper", EntityType.Builder.of(Trooper::new, MobCategory.MONSTER)
                .sized(0.6f, 1.95f).clientTrackingRange(8), Trooper::createAttributes);
        ARTIFICER = register("artificer", EntityType.Builder.of(Artificer::new, MobCategory.MONSTER)
                .sized(0.6f, 1.95f).clientTrackingRange(8), Artificer::createAttributes);
        // Bat-sized drone and copper-golem-sized golem (the drone's size grows with its level through the scale attribute).
        DRONE = register("drone", EntityType.Builder.of(ArtificerDrone::new, MobCategory.MONSTER)
                .sized(0.5f, 0.9f).eyeHeight(0.45f).clientTrackingRange(8), ArtificerDrone::createAttributes);
        BRASS_GOLEM = register("brass_golem", EntityType.Builder.of(BrassGolem::new, MobCategory.MONSTER)
                .sized(0.49f, 0.98f).eyeHeight(0.8125f).clientTrackingRange(8).notInPeaceful(), BrassGolem::createAttributes);
        // Zombified-piglin-sized; the 1.2x size comes from its scale attribute (0.72 x 2.34 in the world).
        PUPPETED = register("puppeted", EntityType.Builder.of(Puppeted::new, MobCategory.MONSTER)
                .sized(0.6f, 1.95f).eyeHeight(1.79f).clientTrackingRange(8).notInPeaceful(), Puppeted::createAttributes);
        // Siege Balloon: 6 wide x 4 tall, seen from far (balloons hang 16 blocks up); no loot table (35 XP, no drops).
        SIEGE_BALLOON = register("siege_balloon", EntityType.Builder.of(SiegeBalloon::new, MobCategory.MONSTER)
                .sized(SiegeBalloon.WIDTH, SiegeBalloon.HEIGHT).eyeHeight(SiegeBalloon.HEIGHT / 2f).clientTrackingRange(10)
                .noLootTable(), SiegeBalloon::createAttributes);
        // Aviator: pillager-sized airman (balloon pilot / paratrooper; fireproof, no drops).
        AVIATOR = register("aviator", EntityType.Builder.of(Aviator::new, MobCategory.MONSTER)
                .sized(0.6f, 1.95f).clientTrackingRange(10).fireImmune(), Aviator::createAttributes); // 1.0.3: loot table entities/aviator (salvage)
        // The Siege Balloon's crossbow arrows (an arrow that is gone 5 s after landing).
        BALLOON_ARROW = registerType("balloon_arrow", EntityType.Builder.<net.gunmetal.raids.entity.BalloonArrow>of(
                net.gunmetal.raids.entity.BalloonArrow::new, MobCategory.MISC).sized(0.5f, 0.5f).eyeHeight(0.13f)
                .clientTrackingRange(4).updateInterval(20).noLootTable());
        // The player's golem (from a Brass Deployable): no spawn egg, no loot table (its 50% deployable drop is in code).
        DEPLOYED_GOLEM = registerType("deployed_brass_golem", EntityType.Builder.of(DeployedGolem::new, MobCategory.MISC)
                .sized(0.49f, 0.98f).eyeHeight(0.8125f).clientTrackingRange(8).noLootTable());
        FabricDefaultAttributeRegistry.register(DEPLOYED_GOLEM, DeployedGolem.createAttributes());
        THROWN_BRASS_DEPLOYABLE = registerType("brass_deployable", EntityType.Builder.<ThrownBrassDeployable>of(ThrownBrassDeployable::new, MobCategory.MISC)
                .sized(0.25f, 0.25f).clientTrackingRange(4).updateInterval(10).noLootTable());
    }

    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> registerType(String name, EntityType.Builder<T> builder) {
        Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, name);
        EntityType<T> type = builder.build(ResourceKey.create(Registries.ENTITY_TYPE, id));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, id, type);
    }

    private static <T extends Mob> EntityType<T> register(String name, EntityType.Builder<T> builder,
                                                                  Supplier<AttributeSupplier.Builder> attributes) {
        Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, name);
        EntityType<T> type = builder.build(ResourceKey.create(Registries.ENTITY_TYPE, id));
        Registry.register(BuiltInRegistries.ENTITY_TYPE, id, type);
        FabricDefaultAttributeRegistry.register(type, attributes.get());

        Identifier eggId = id.withSuffix("_spawn_egg");
        Item egg = new SpawnEggItem(new Item.Properties().spawnEgg(type).setId(ResourceKey.create(Registries.ITEM, eggId)));
        Registry.register(BuiltInRegistries.ITEM, eggId, egg);
        SPAWN_EGGS.add(egg);
        return type;
    }
}
