package net.gunmetal.raids.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IllagerRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.gunmetal.raids.entity.AnimSignalled;

/**
 * Illager renderer with its own model layer and texture; held items optional. Unlike vanilla, held
 * items are hidden while the mob is invisible (raid-spawned Sharpshooters). Passes the mob's
 * animation signals to the model (RaidIllagerModel), and draws the main-hand item with the mob's
 * state name as its custom_model_data (AnimSignalled.heldItemTag) so the pack can place it per state.
 */
public class RaidIllagerRenderer<T extends AbstractIllager & AnimSignalled> extends IllagerRenderer<T, RaidIllagerRenderState> {
    /** Picked by AnimSignalled.textureVariant (Trooper officers: 1; Aviators: 0 pilot, 1-3 airmen). */
    private final Identifier[] textures;

    public RaidIllagerRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer, Identifier texture,
                               boolean drawHeldItems) {
        this(context, layer, drawHeldItems, texture);
    }

    public RaidIllagerRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer, Identifier texture,
                               Identifier altTexture, boolean drawHeldItems) {
        this(context, layer, drawHeldItems, texture, altTexture);
    }

    public RaidIllagerRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer, boolean drawHeldItems,
                               Identifier... textures) {
        super(context, new RaidIllagerModel(context.bakeLayer(layer)), 0.5f);
        this.textures = textures;
        if (drawHeldItems) {
            addLayer(new VisibleItemInHandLayer(this));
        }
    }

    /** The vanilla held-item layer, skipped while the mob is invisible. */
    private static final class VisibleItemInHandLayer extends ItemInHandLayer<RaidIllagerRenderState, IllagerModel<RaidIllagerRenderState>> {
        VisibleItemInHandLayer(RenderLayerParent<RaidIllagerRenderState, IllagerModel<RaidIllagerRenderState>> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, RaidIllagerRenderState state,
                           float yRot, float xRot) {
            if (!state.isInvisible) {
                super.submit(poseStack, collector, light, state, yRot, xRot);
            }
        }
    }

    @Override
    public void extractRenderState(T entity, RaidIllagerRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.animState = entity.animState();
        state.secondsSinceShot = entity.secondsSinceShot(partialTick);
        state.extraSignal = entity.extraSignal();
        state.summonPlan = entity.summonPlan();
        state.textureVariant = entity.textureVariant();
        state.drawOffset = entity instanceof net.gunmetal.raids.entity.Aviator aviator ? aviator.drawOffset(partialTick)
                : net.minecraft.world.phys.Vec3.ZERO;
        CustomModelData tag = entity.heldItemTag();
        if (tag != null) {
            ItemStack held = entity.getMainHandItem();
            if (!held.isEmpty()) {
                // A throwaway copy for drawing only (vanilla copies the held stacks every frame too).
                ItemStack tagged = held.copy();
                tagged.set(DataComponents.CUSTOM_MODEL_DATA, tag);
                boolean right = entity.getMainArm() == HumanoidArm.RIGHT;
                itemModelResolver.updateForLiving(right ? state.rightHandItemState : state.leftHandItemState, tagged,
                        right ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                        entity);
            }
        }
    }

    /** Aviators aboard a balloon: drawn at the true spot (see Aviator.drawOffset). */
    @Override
    public net.minecraft.world.phys.Vec3 getRenderOffset(RaidIllagerRenderState state) {
        return super.getRenderOffset(state).add(state.drawOffset);
    }

    @Override
    public Identifier getTextureLocation(RaidIllagerRenderState state) {
        int variant = state.textureVariant;
        return textures[variant >= 0 && variant < textures.length ? variant : 0];
    }

    @Override
    public RaidIllagerRenderState createRenderState() {
        return new RaidIllagerRenderState();
    }
}
