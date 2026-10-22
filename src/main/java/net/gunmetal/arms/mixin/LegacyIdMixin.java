package net.gunmetal.arms.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Map;

/**
 * Realm Arms migration (1.0.4): any registry lookup for a realmarms:<name> that doesn't exist is
 * answered with gunmetal:<name> when that exists. So a world saved with Realm Arms (items, stack data
 * like realmarms:loaded, bullets, damage types...) loads its things as Gunmetal's once Realm Arms is
 * removed. Costs one namespace check per lookup.
 */
@Mixin(MappedRegistry.class)
abstract class LegacyIdMixin<T> {
    private static final String OLD_NAMESPACE = "realmarms", NEW_NAMESPACE = "gunmetal";

    @Shadow
    @Final
    private Map<Identifier, Holder.Reference<T>> byLocation;

    @ModifyVariable(method = {
            "get(Lnet/minecraft/resources/Identifier;)Ljava/util/Optional;",
            "getValue(Lnet/minecraft/resources/Identifier;)Ljava/lang/Object;",
            "containsKey(Lnet/minecraft/resources/Identifier;)Z"
    }, at = @At("HEAD"), argsOnly = true)
    private Identifier gunmetal$legacyId(Identifier id) {
        if (id == null || !OLD_NAMESPACE.equals(id.getNamespace()) || byLocation.containsKey(id)) {
            return id;
        }
        Identifier renamed = Identifier.fromNamespaceAndPath(NEW_NAMESPACE, id.getPath());
        return byLocation.containsKey(renamed) ? renamed : id;
    }

    @ModifyVariable(method = {
            "get(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;",
            "getValue(Lnet/minecraft/resources/ResourceKey;)Ljava/lang/Object;",
            "containsKey(Lnet/minecraft/resources/ResourceKey;)Z"
    }, at = @At("HEAD"), argsOnly = true)
    private ResourceKey<T> gunmetal$legacyKey(ResourceKey<T> key) {
        if (key == null) {
            return null;
        }
        Identifier id = key.identifier();
        Identifier renamed = gunmetal$legacyId(id);
        return renamed == id ? key : ResourceKey.create(key.registryKey(), renamed);
    }
}
