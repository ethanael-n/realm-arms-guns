package net.gunmetal.raids.client;

import net.minecraft.client.model.animal.bee.AdultBeeModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.state.BeeRenderState;

/**
 * The bee model (and the bee's own motion: hover bob, wing flap, leg sway) plus an empty part,
 * "level_signal", that carries the drone's level for a custom .jem (like the brass golem's
 * tank_signal): round(level_signal.rz) = 1, 2 or 3. It has no boxes, so it never draws anything.
 * Part tree of this layer: root > bone > body (stinger, left_antenna, right_antenna), right_wing,
 * left_wing, front_legs, middle_legs, back_legs; root > level_signal.
 */
public class DroneModel extends AdultBeeModel {
    public static final String SIGNAL = "level_signal";
    private final ModelPart signal;
    private final PartPose signalRest;

    public DroneModel(ModelPart root) {
        super(root);
        this.signal = root.getChild(SIGNAL);
        this.signalRest = signal.getInitialPose();
    }

    public static LayerDefinition createBodyLayer() {
        return AdultBeeModel.createBodyLayer().apply(mesh -> {
            mesh.getRoot().addOrReplaceChild(SIGNAL, CubeListBuilder.create(), PartPose.ZERO);
            return mesh;
        });
    }

    @Override
    public void setupAnim(BeeRenderState state) {
        float level = state instanceof DroneRenderer.DroneRenderState drone ? drone.droneLevel : 1f;
        signal.setInitialPose(PartPose.offsetAndRotation(signalRest.x(), signalRest.y(), signalRest.z(),
                signalRest.xRot(), signalRest.yRot(), level));
        super.setupAnim(state);
        signal.zRot = level;
    }
}
