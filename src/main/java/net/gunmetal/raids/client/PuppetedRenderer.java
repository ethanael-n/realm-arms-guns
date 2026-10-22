package net.gunmetal.raids.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.UndeadRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.gunmetal.raids.entity.Puppeted;

/**
 * The Puppeted: the incomplete model (zombified piglin) or the completed one (piglin), swapped per
 * Puppeted, and one of three textures (textures/entity/puppeted/):
 *   incomplete.png     incomplete
 *   complete_a.png     completed, first look
 *   complete_b.png     completed, second look
 * (Letters, not numbers: Entity Texture Features treats name1.png, name2.png... as random variants.)
 * The 1.2x size comes from the mob's scale. A plain MobRenderer (HumanoidMobRenderer would pick its
 * model by age every frame, undoing the swap); it never holds or wears anything, so no item layers.
 */
public class PuppetedRenderer extends MobRenderer<Puppeted, PuppetedRenderer.PuppetedRenderState, PuppetedModel> {
    private final PuppetedModel incompleteModel, completeModel;
    private final Identifier incompleteTexture;
    private final Identifier[] completeTextures;

    public PuppetedRenderer(EntityRendererProvider.Context context, ModelLayerLocation incompleteLayer, ModelLayerLocation completeLayer) {
        super(context, new PuppetedModel(context.bakeLayer(incompleteLayer), true), 0.5f);
        this.incompleteModel = this.model;
        this.completeModel = new PuppetedModel(context.bakeLayer(completeLayer), false);
        this.incompleteTexture = texture("incomplete");
        this.completeTextures = new Identifier[]{texture("complete_a"), texture("complete_b")};
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath("gunmetal", "textures/entity/puppeted/" + name + ".png");
    }

    public static class PuppetedRenderState extends UndeadRenderState {
        public boolean isAggressive;
        public boolean completed;
        public int variant;
    }

    @Override
    public void extractRenderState(Puppeted puppeted, PuppetedRenderState state, float partialTick) {
        super.extractRenderState(puppeted, state, partialTick);
        HumanoidMobRenderer.extractHumanoidRenderState(puppeted, state, partialTick, itemModelResolver);
        state.isAggressive = puppeted.isAggressive();
        state.completed = puppeted.isCompleted();
        state.variant = puppeted.variant();
    }

    /** Picks this Puppeted's model before drawing (each draw keeps the model it was given). */
    @Override
    public void submit(PuppetedRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        this.model = state.completed ? completeModel : incompleteModel;
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    public Identifier getTextureLocation(PuppetedRenderState state) {
        return state.completed ? completeTextures[Math.floorMod(state.variant, completeTextures.length)] : incompleteTexture;
    }

    @Override
    public PuppetedRenderState createRenderState() {
        return new PuppetedRenderState();
    }
}
