package net.gunmetal.arms.guns;

/**
 * Rifle scope state for the local player. Plain fields only (no client classes), so the rifle
 * item can set the recoil on the client without the server ever loading client code.
 */
public final class ScopeState {
    /** View kick while scoped: ticks left and pitch per tick. */
    public static final int RECOIL_TICKS = 12;
    public static final float RECOIL_AMOUNT = 0.25f;

    public static boolean isScoping;
    public static int recoilTicks;

    private ScopeState() {
    }
}
