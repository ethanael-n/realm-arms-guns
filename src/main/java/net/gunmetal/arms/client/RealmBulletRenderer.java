package net.gunmetal.arms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.gunmetal.arms.entity.RealmBulletEntity;
import net.gunmetal.arms.guns.GunType;

import static net.gunmetal.arms.RealmArmsWeapons.MOD_ID;

/**
 * Draws the bullet as a camera-facing sprite, with a texture per
 * projectile type: textures/entity/bullet_round.png, bullet_jagged.png, bullet_heavy.png.
 */
public class RealmBulletRenderer extends EntityRenderer<RealmBulletEntity, RealmBulletRenderer.State> {
    private static final Identifier ROUND = texture("bullet_round");
    private static final Identifier JAGGED = texture("bullet_jagged");
    private static final Identifier HEAVY = texture("bullet_heavy");
    private static final Identifier CANNONBALL = texture("bullet_cannonball");
    private static final Identifier SHRAPNEL = texture("bullet_shrapnel");

    /** On-screen size of each projectile type (half-width in blocks). */
    private static final float ROUND_SIZE = 0.1f;
    private static final float JAGGED_SIZE = 0.06f;
    private static final float HEAVY_SIZE = 0.18f;
    private static final float CANNONBALL_SIZE = 0.6f;
    private static final float SHRAPNEL_SIZE = 0.08f;

    public RealmBulletRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(MOD_ID, "textures/entity/" + name + ".png");
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RealmBulletEntity bullet, State state, float partialTick) {
        super.extractRenderState(bullet, state, partialTick);
        state.isFirstTick = bullet.isFirstTick();
        state.projectile = bullet.projectile();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.isFirstTick) {
            return;
        }
        int light = state.lightCoords;
        float size;
        Identifier texture;
        switch (state.projectile) {
            case JAGGED -> { size = JAGGED_SIZE; texture = JAGGED; }
            case HEAVY -> { size = HEAVY_SIZE; texture = HEAVY; }
            case CANNONBALL -> { size = CANNONBALL_SIZE; texture = CANNONBALL; }
            case SHRAPNEL -> { size = SHRAPNEL_SIZE; texture = SHRAPNEL; }
            default -> { size = ROUND_SIZE; texture = ROUND; }
        }
        poseStack.pushPose();
        poseStack.scale(size, size, size);
        poseStack.mulPose(camera.orientation);
        poseStack.mulPose(Axis.YP.rotationDegrees(180f));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture), (pose, builder) -> {
            vertex(builder, pose, -1f, -1f, 0f, 1f, light);
            vertex(builder, pose, 1f, -1f, 1f, 1f, light);
            vertex(builder, pose, 1f, 1f, 1f, 0f, light);
            vertex(builder, pose, -1f, 1f, 0f, 0f, light);
        });
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    private static void vertex(VertexConsumer builder, PoseStack.Pose pose, float x, float y, float u, float v, int light) {
        builder.addVertex(pose, x, y, 0f)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0f, 1f, 0f);
    }

    public static class State extends EntityRenderState {
        public boolean isFirstTick;
        public GunType.Projectile projectile = GunType.Projectile.ROUND;
    }
}
