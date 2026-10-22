package net.gunmetal.arms.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.gunmetal.arms.guns.ClipGunItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -> server, one per revolver / repeater round the local player fires (1 byte). The client
 * sends it from inside its own use() call, so it always arrives just before that click's use packet.
 * The server only fires a clip gun round for a click the client also fired, so the shooter's sound,
 * flash and crosshair (client side) always match a real bullet (Gunmetal 1.0.1 "ghost shot" fix).
 */
public record ClipShotPayload(boolean offhand) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClipShotPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("gunmetal", "clip_shot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClipShotPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, ClipShotPayload::offhand, ClipShotPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE,
                (payload, context) -> ClipGunItem.clientFired(context.player(), payload.offhand()));
    }
}
