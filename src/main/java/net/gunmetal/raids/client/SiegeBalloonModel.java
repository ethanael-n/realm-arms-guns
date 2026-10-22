package net.gunmetal.raids.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.monster.ghast.GhastModel;
import net.minecraft.client.renderer.entity.state.GhastRenderState;

/**
 * The ghast model (vanilla part names: body, tentacle0 ... tentacle8) plus two empty parts carrying
 * the balloon's state for a custom .jem:
 *   round(balloon_signal.rx)  = rise state: 0 high, 1 mid, 2 low
 *   round(balloon_signal.ry)  = 1 once its pilot is lost (burning down), else 0
 *   round(balloon_signal.rz)  = 1 once it's cut loose (ravager dead / tether snapped), else 0
 *   round(balloon_signal2.rx) = damage stage: 0, 1 (under 70 health), 2 (under 25 health)
 *   balloon_signal2.ry        = seconds since its last cannon shot (capped at 100)
 *   balloon_signal2.rz        = seconds since its last crossbow burst (capped at 100)
 *   round(balloon_signal3.rx) = paratrooper drop phase: 0 nothing, 1 the 2 s before a drop (warning),
 *                               2 the 2 s after it
 *   round(balloon_signal3.ry) = 1 while no tether is drawn (cut loose, or never had one), else 0
 *   cannon_signal / crossbow_signal: .rx = aim pitch (radians, + up), .ry = aim yaw relative to the
 *                               balloon's facing (radians, + to its left), round(.rz) = 1 with a target
 *                               (0 and 0 at rest without one). Straight line from each muzzle to the
 *                               target's middle (the cannon's real shot arcs a little higher).
 * The balloon turns to face its target (rot_y); head_pitch is its aim pitch.
 */
public class SiegeBalloonModel extends GhastModel {
    public static final String SIGNAL = "balloon_signal", SIGNAL_2 = "balloon_signal2", SIGNAL_3 = "balloon_signal3",
            CANNON_SIGNAL = "cannon_signal", CROSSBOW_SIGNAL = "crossbow_signal";
    private final ModelPart signal, signal2, signal3, cannonSignal, crossbowSignal;
    private final PartPose signalRest, signal2Rest, signal3Rest, cannonRest, crossbowRest;

    public SiegeBalloonModel(ModelPart root) {
        super(root);
        this.signal = root.getChild(SIGNAL);
        this.signal2 = root.getChild(SIGNAL_2);
        this.signalRest = signal.getInitialPose();
        this.signal2Rest = signal2.getInitialPose();
        this.signal3 = root.getChild(SIGNAL_3);
        this.signal3Rest = signal3.getInitialPose();
        this.cannonSignal = root.getChild(CANNON_SIGNAL);
        this.cannonRest = cannonSignal.getInitialPose();
        this.crossbowSignal = root.getChild(CROSSBOW_SIGNAL);
        this.crossbowRest = crossbowSignal.getInitialPose();
    }

    public static LayerDefinition createBodyLayer() {
        return GhastModel.createBodyLayer().apply(mesh -> {
            mesh.getRoot().addOrReplaceChild(SIGNAL, CubeListBuilder.create(), PartPose.ZERO);
            mesh.getRoot().addOrReplaceChild(SIGNAL_2, CubeListBuilder.create(), PartPose.ZERO);
            mesh.getRoot().addOrReplaceChild(SIGNAL_3, CubeListBuilder.create(), PartPose.ZERO);
            mesh.getRoot().addOrReplaceChild(CANNON_SIGNAL, CubeListBuilder.create(), PartPose.ZERO);
            mesh.getRoot().addOrReplaceChild(CROSSBOW_SIGNAL, CubeListBuilder.create(), PartPose.ZERO);
            return mesh;
        });
    }

    @Override
    public void setupAnim(GhastRenderState state) {
        float rise = 0f, lost = 0f, loose = 0f, stage = 0f, cannon = 100f, crossbow = 100f, drop = 0f, untethered = 0f;
        float cPitch = 0f, cYaw = 0f, cOn = 0f, xPitch = 0f, xYaw = 0f, xOn = 0f;
        if (state instanceof SiegeBalloonRenderer.State balloon) {
            rise = balloon.riseState;
            lost = balloon.pilotLost ? 1f : 0f;
            loose = balloon.cutLoose ? 1f : 0f;
            stage = balloon.damageStage;
            cannon = balloon.secondsSinceCannon;
            crossbow = balloon.secondsSinceCrossbow;
            drop = balloon.dropPhase;
            untethered = balloon.hasTether ? 0f : 1f;
            cPitch = balloon.cannonPitch;
            cYaw = balloon.cannonYaw;
            cOn = balloon.cannonAiming ? 1f : 0f;
            xPitch = balloon.crossbowPitch;
            xYaw = balloon.crossbowYaw;
            xOn = balloon.crossbowAiming ? 1f : 0f;
        }
        // Also written into the initial poses, which is what EMF resets parts to before animating.
        signal.setInitialPose(PartPose.offsetAndRotation(signalRest.x(), signalRest.y(), signalRest.z(), rise, lost, loose));
        signal2.setInitialPose(PartPose.offsetAndRotation(signal2Rest.x(), signal2Rest.y(), signal2Rest.z(), stage, cannon, crossbow));
        signal3.setInitialPose(PartPose.offsetAndRotation(signal3Rest.x(), signal3Rest.y(), signal3Rest.z(), drop, untethered, 0f));
        cannonSignal.setInitialPose(PartPose.offsetAndRotation(cannonRest.x(), cannonRest.y(), cannonRest.z(), cPitch, cYaw, cOn));
        crossbowSignal.setInitialPose(PartPose.offsetAndRotation(crossbowRest.x(), crossbowRest.y(), crossbowRest.z(), xPitch, xYaw, xOn));
        super.setupAnim(state);
        signal.xRot = rise;
        signal.yRot = lost;
        signal.zRot = loose;
        signal2.xRot = stage;
        signal2.yRot = cannon;
        signal2.zRot = crossbow;
        signal3.xRot = drop;
        signal3.yRot = untethered;
        cannonSignal.xRot = cPitch;
        cannonSignal.yRot = cYaw;
        cannonSignal.zRot = cOn;
        crossbowSignal.xRot = xPitch;
        crossbowSignal.yRot = xYaw;
        crossbowSignal.zRot = xOn;
    }
}
