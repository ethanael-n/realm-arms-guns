package net.gunmetal.arms.guns;

import net.minecraft.world.InteractionHand;

import java.util.function.BiConsumer;

/**
 * Bridge from the (shared) gun items to client-only effects. The client sets the listener at
 * startup; on a dedicated server it stays empty, so no client classes are ever loaded there.
 */
public final class FireHooks {
    private static BiConsumer<GunType, InteractionHand> clientListener = (type, hand) -> {
    };

    private static Runnable clipShotListener = () -> {
    };
    private static java.util.function.Consumer<Boolean> clipShotSentListener = offhand -> {
    };

    private FireHooks() {
    }

    public static void setClipShotListener(Runnable listener) {
        clipShotListener = listener;
    }

    public static void setClipShotSentListener(java.util.function.Consumer<Boolean> listener) {
        clipShotSentListener = listener;
    }

    /** Called on the client right after a clip gun round is fired locally: sends ClipShotPayload. */
    public static void clientClipShotSent(boolean offhand) {
        clipShotSentListener.accept(offhand);
    }

    /** Called on the client when the local player fires a clip gun (each round needs its own click). */
    public static void clientClipShot() {
        clipShotListener.run();
    }

    public static void setClientListener(BiConsumer<GunType, InteractionHand> listener) {
        clientListener = listener;
    }



    /** Called on the client when the local player fires a Realm Arms gun. */
    public static void clientFired(GunType type, InteractionHand hand) {
        clientListener.accept(type, hand);
    }
}
