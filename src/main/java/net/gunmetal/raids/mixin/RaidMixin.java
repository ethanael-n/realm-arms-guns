package net.gunmetal.raids.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import net.gunmetal.raids.raid.RaidSpawns;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the Realm Raids illagers to each wave: runs at the end of Raid#spawnGroup, just before the wave
 * counter goes up (so the wave number is groupsSpawned + 1) and before the raid bar is refreshed.
 */
@Mixin(Raid.class)
abstract class RaidMixin {
    @Shadow
    private int groupsSpawned;

    @Inject(method = "spawnGroup", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/raid/Raid;groupsSpawned:I",
            opcode = Opcodes.PUTFIELD))
    private void gunmetal$spawnExtras(ServerLevel level, BlockPos pos, CallbackInfo ci) {
        RaidSpawns.spawnExtras((Raid) (Object) this, level, groupsSpawned + 1, pos);
    }
}
