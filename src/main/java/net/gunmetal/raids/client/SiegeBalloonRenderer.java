package net.gunmetal.raids.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.GhastRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.monster.ghast.GhastModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.gunmetal.raids.entity.SiegeBalloon;

/**
 * Siege Balloon: ghast geometry (model layer gunmetal:siege_balloon, EMF: siege_balloon.jem),
 * texture by damage stage (textures/entity/siege_balloon/siege_balloon.png, siege_balloon_damaged.png
 * under 70 health, siege_balloon_burning.png under 25; a .jem texture line would override these), and its tether drawn as a sagging chain
 * (textures/entity/siege_balloon/tether.png: one tile = TILE_LENGTH blocks of chain, along the
 * texture's height; drawn as two crossed strips). Not drawn once dead (the blast replaces it).
 * The tether is drawn as three chains (visual only; the pull works from the bottom-centre): one from
 * above the pilot's spot, two from the rear (SiegeBalloon.*_TIE_*), all turning with
 * the balloon and meeting at the ravager's back. Each sags by its own slack against the tether length.
 */
public class SiegeBalloonRenderer extends MobRenderer<SiegeBalloon, SiegeBalloonRenderer.State, GhastModel> {
    /** Chain: blocks of chain per texture tile, half-width of each strip (blocks), most sag when slack. */
    public static final float TILE_LENGTH = 0.5f, HALF_WIDTH = 0.12f, MAX_SAG = 4.0f;

    /** By damage stage: 0, 1, 2. */
    private final Identifier[] textures;
    private final Identifier tetherTexture;

    public SiegeBalloonRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer, Identifier[] textures,
                                Identifier tetherTexture) {
        super(context, new SiegeBalloonModel(context.bakeLayer(layer)), 0.0f);
        this.textures = textures;
        this.tetherTexture = tetherTexture;
    }

    public static class State extends GhastRenderState {
        public int riseState;
        public boolean pilotLost, cutLoose;
        public int damageStage;
        public float secondsSinceCannon = 100f, secondsSinceCrossbow = 100f;
        public int dropPhase;
        /** Aim signals, radians: pitch (up +), yaw relative to the balloon's facing, and whether there's a target. */
        public float cannonPitch, cannonYaw, crossbowPitch, crossbowYaw;
        public boolean cannonAiming, crossbowAiming;
        public boolean hasTether;
        public double tetherLength = SiegeBalloon.TETHER_LENGTH;
        /** Tether end (the ravager's back) relative to the balloon's position. */
        public double tetherX, tetherY, tetherZ;
    }

    @Override
    public void extractRenderState(SiegeBalloon balloon, State state, float partialTick) {
        super.extractRenderState(balloon, state, partialTick);
        state.riseState = balloon.riseState();
        state.pilotLost = balloon.pilotLost();
        state.cutLoose = balloon.cutLoose();
        state.damageStage = balloon.damageStage();
        state.secondsSinceCannon = balloon.secondsSinceCannon(partialTick);
        state.secondsSinceCrossbow = balloon.secondsSinceCrossbow(partialTick);
        state.dropPhase = balloon.dropPhase();
        float yaw = net.minecraft.util.Mth.rotLerp(partialTick, balloon.yRotO, balloon.getYRot());
        Vec3 base = balloon.getPosition(partialTick);
        float[] aim = aim(base, yaw, SiegeBalloon.cannonMuzzleLocal(yaw), balloon.cannonTargetEntity(), partialTick);
        state.cannonAiming = aim != null;
        state.cannonPitch = aim != null ? aim[0] : 0f;
        state.cannonYaw = aim != null ? aim[1] : 0f;
        aim = aim(base, yaw, SiegeBalloon.crossbowMuzzleLocal(yaw), balloon.crossbowTargetEntity(), partialTick);
        state.crossbowAiming = aim != null;
        state.crossbowPitch = aim != null ? aim[0] : 0f;
        state.crossbowYaw = aim != null ? aim[1] : 0f;
        Entity anchor = balloon.isAlive() ? balloon.tetherEntity() : null;
        state.hasTether = anchor != null;
        state.tetherLength = balloon.tetherLength();
        if (anchor != null) {
            Vec3 end = SiegeBalloon.anchorPoint(anchor, partialTick).subtract(balloon.getPosition(partialTick));
            state.tetherX = end.x;
            state.tetherY = end.y;
            state.tetherZ = end.z;
        }
    }

    /**
     * {pitch, yaw} in radians from a muzzle (balloon frame: right, up, forward) to the target's middle:
     * pitch positive = up; yaw relative to where the balloon faces, positive = to its left (counter-
     * clockwise seen from above), within +-pi. Null without a target.
     */
    private static float @org.jetbrains.annotations.Nullable [] aim(Vec3 base, float yawDegrees, Vec3 local, @org.jetbrains.annotations.Nullable Entity target,
                                                                 float partialTick) {
        if (target == null) {
            return null;
        }
        Vec3 muzzle = base.add(new Vec3(local.x, local.y, local.z).yRot(-yawDegrees * net.minecraft.util.Mth.DEG_TO_RAD));
        Vec3 aimAt = target.getPosition(partialTick).add(0.0, target.getBbHeight() / 2.0, 0.0);
        Vec3 d = aimAt.subtract(muzzle);
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        float pitch = (float) Math.atan2(d.y, flat);
        float targetYaw = (float) (net.minecraft.util.Mth.atan2(-d.x, d.z) * net.minecraft.util.Mth.RAD_TO_DEG);
        float relative = net.minecraft.util.Mth.wrapDegrees(yawDegrees - targetYaw) * net.minecraft.util.Mth.DEG_TO_RAD;
        return new float[]{pitch, relative};
    }

    /** Also drawn while only its tether is on screen. */
    @Override
    public boolean shouldRender(SiegeBalloon balloon, Frustum frustum, double camX, double camY, double camZ) {
        if (super.shouldRender(balloon, frustum, camX, camY, camZ)) {
            return true;
        }
        AABB box = balloon.getBoundingBox();
        if (frustum.isVisible(box)) {
            return true;
        }
        Entity anchor = balloon.tetherEntity();
        return anchor != null && frustum.isVisible(box.minmax(anchor.getBoundingBox()));
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.deathTime > 0f) {
            return;
        }
        super.submit(state, poseStack, collector, camera);
        if (state.hasTether) {
            submitTether(state, poseStack, collector);
        }
    }

    /** Chain ends on the balloon, in its own frame (right, up, forward) from its bottom-centre. */
    private static final Vec3[] TIE_POINTS = {
            new Vec3(0.0, SiegeBalloon.FRONT_TIE_HEIGHT, SiegeBalloon.pilotDistanceStraight(0.3)), // above the pilot's spot
            new Vec3(SiegeBalloon.REAR_TIE_SIDE, SiegeBalloon.REAR_TIE_HEIGHT, -SiegeBalloon.REAR_TIE_BACK), // rear pair
            new Vec3(-SiegeBalloon.REAR_TIE_SIDE, SiegeBalloon.REAR_TIE_HEIGHT, -SiegeBalloon.REAR_TIE_BACK)};

    private void submitTether(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        Vec3 end = new Vec3(state.tetherX, state.tetherY, state.tetherZ);
        float yaw = state.bodyRot * net.minecraft.util.Mth.DEG_TO_RAD;
        int light = state.lightCoords;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(tetherTexture), (pose, builder) -> {
            for (Vec3 tie : TIE_POINTS) {
                // (right, up, forward) -> world: right is -x at yaw 0, forward +z, turned by the balloon's yaw.
                Vec3 start = new Vec3(-tie.x, tie.y, tie.z).yRot(-yaw);
                chain(builder, pose, start, end, state.tetherLength, light);
            }
        });
    }

    private static void chain(VertexConsumer builder, PoseStack.Pose pose, Vec3 start, Vec3 end, double length, int light) {
        double straight = end.subtract(start).length();
        if (straight < 0.05) {
            return;
        }
        double sag = Math.min(MAX_SAG, Math.max(0.15, (length - straight) * 0.4));
        int segments = Math.max(1, (int) Math.ceil(straight / TILE_LENGTH));
        Vec3 previous = start;
        for (int i = 1; i <= segments; ++i) {
            double t = i / (double) segments;
            Vec3 point = start.lerp(end, t).add(0.0, -sag * 4.0 * t * (1.0 - t), 0.0);
            link(builder, pose, previous, point, light);
            previous = point;
        }
    }

    /** One tile of chain from a to b: two crossed strips. */
    private static void link(VertexConsumer builder, PoseStack.Pose pose, Vec3 a, Vec3 b, int light) {
        Vec3 axis = b.subtract(a);
        if (axis.lengthSqr() < 1.0E-8) {
            return;
        }
        axis = axis.normalize();
        Vec3 side = axis.cross(new Vec3(0.0, 1.0, 0.0));
        if (side.lengthSqr() < 1.0E-6) {
            side = new Vec3(1.0, 0.0, 0.0);
        }
        side = side.normalize().scale(HALF_WIDTH);
        Vec3 side2 = axis.cross(side).normalize().scale(HALF_WIDTH);
        strip(builder, pose, a, b, side, light);
        strip(builder, pose, a, b, side2, light);
    }

    private static void strip(VertexConsumer builder, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 side, int light) {
        Vec3 normal = side.cross(b.subtract(a)).normalize();
        vertex(builder, pose, a.subtract(side), 0f, 0f, normal, light);
        vertex(builder, pose, a.add(side), 1f, 0f, normal, light);
        vertex(builder, pose, b.add(side), 1f, 1f, normal, light);
        vertex(builder, pose, b.subtract(side), 0f, 1f, normal, light);
    }

    private static void vertex(VertexConsumer builder, PoseStack.Pose pose, Vec3 p, float u, float v, Vec3 normal, int light) {
        builder.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return textures[Math.max(0, Math.min(textures.length - 1, state.damageStage))];
    }

    @Override
    public State createRenderState() {
        return new State();
    }
}
