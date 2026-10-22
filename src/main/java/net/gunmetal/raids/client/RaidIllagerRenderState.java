package net.gunmetal.raids.client;

import net.minecraft.client.renderer.entity.state.IllagerRenderState;

/** Illager render state plus the animation signals (see AnimSignalled). */
public class RaidIllagerRenderState extends IllagerRenderState {
    public int animState;
    public float secondsSinceShot = 100f;
    public float extraSignal;
    public int summonPlan;
    public int textureVariant;
    /** Model offset from the hitbox (Aviators aboard a balloon). */
    public net.minecraft.world.phys.Vec3 drawOffset = net.minecraft.world.phys.Vec3.ZERO;
}
