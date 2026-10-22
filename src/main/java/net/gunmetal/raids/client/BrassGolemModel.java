package net.gunmetal.raids.client;

import net.minecraft.client.model.animal.golem.CopperGolemModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.state.CopperGolemRenderState;

/**
 * The copper golem model plus an empty part, "tank_signal", that carries the golem's tank contents
 * for a custom .jem (like the illagers' hat): round(tank_signal.rz) = 0 gunpowder (also empty
 * deployables), 1 blaze, 2 patina, 3 redstone, 4 mixed, 5 glowstone, 6 sulfur, 7 the hostile golem. It has no boxes, so it never
 * draws anything.
 */
public class BrassGolemModel extends CopperGolemModel {
    public static final String SIGNAL = "tank_signal";
    private final ModelPart signal;
    private final PartPose signalRest;

    public BrassGolemModel(ModelPart root) {
        super(root);
        this.signal = root.getChild(SIGNAL);
        this.signalRest = signal.getInitialPose();
    }

    public static LayerDefinition createBodyLayer() {
        return CopperGolemModel.createBodyLayer().apply(mesh -> {
            mesh.getRoot().addOrReplaceChild(SIGNAL, CubeListBuilder.create(), PartPose.ZERO);
            return mesh;
        });
    }

    @Override
    public void setupAnim(CopperGolemRenderState state) {
        float tank = state instanceof BrassGolemRenderer.BrassGolemRenderState golem ? golem.tank.ordinal() : 0f;
        signal.setInitialPose(PartPose.offsetAndRotation(signalRest.x(), signalRest.y(), signalRest.z(),
                signalRest.xRot(), signalRest.yRot(), tank));
        super.setupAnim(state);
        signal.zRot = tank;
    }
}
