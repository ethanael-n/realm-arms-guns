package net.gunmetal.raids.client;

import net.minecraft.client.model.animal.bee.BeeModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.BeeRenderState;
import net.minecraft.resources.Identifier;
import net.gunmetal.raids.entity.ArtificerDrone;

/**
 * Artificer drone: the bee model and its motion (always flying: hover bob, wing flap, leg sway;
 * never angry, stinger shown), with its own model layer (gunmetal:drone#main, EMF: drone.jem) and
 * one texture for all levels (textures/entity/drone/drone.png). The level reaches the model through
 * the empty part "level_signal" (DroneModel); the size per level comes from the drone's scale.
 */
public class DroneRenderer extends MobRenderer<ArtificerDrone, DroneRenderer.DroneRenderState, BeeModel> {
    private final Identifier texture;

    public DroneRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer, Identifier texture) {
        super(context, new DroneModel(context.bakeLayer(layer)), 0.25f);
        this.texture = texture;
    }

    public static class DroneRenderState extends BeeRenderState {
        public int droneLevel = 1;
    }

    @Override
    public void extractRenderState(ArtificerDrone drone, DroneRenderState state, float partialTick) {
        super.extractRenderState(drone, state, partialTick);
        state.rollAmount = 0f;
        state.hasStinger = true;
        state.isOnGround = false;
        state.isAngry = false;
        state.hasNectar = false;
        state.droneLevel = drone.droneLevel();
    }

    @Override
    public Identifier getTextureLocation(DroneRenderState state) {
        return texture;
    }

    @Override
    public DroneRenderState createRenderState() {
        return new DroneRenderState();
    }
}
