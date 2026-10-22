package net.gunmetal.raids.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.monster.illager.IllagerModel;

/**
 * The vanilla illager model, which also writes the mob's animation signals into the invisible "hat"
 * part (a custom .jem reads them: hat.rz = state, abs(hat.rx) = seconds since the last shot, hat.ry =
 * the mob's extra signal, e.g. the Trooper's shield hits left). The hat
 * is never drawn by vanilla, and a .jem that has no boxes in it shows nothing either.
 * The values go in both as the hat's rest pose (so anything that reads the parts right after vanilla
 * posing sees them) and directly afterwards.
 *
 * The Artificer's model also has two more empty parts for its summon telegraph (see addSummonSignals):
 *   summon_signal.rx / .ry / .rz = drones / golems / puppeted about to be summoned (0-3 each),
 *   upgrade_signal.rz             = 1 during an upgrade move's cast (0 otherwise).
 */
public class RaidIllagerModel extends IllagerModel<RaidIllagerRenderState> {
    public static final String SUMMON_SIGNAL = "summon_signal", UPGRADE_SIGNAL = "upgrade_signal";
    private final ModelPart signal;
    private final PartPose signalRest;
    private final @org.jetbrains.annotations.Nullable ModelPart summonSignal, upgradeSignal;

    public RaidIllagerModel(ModelPart root) {
        super(root);
        this.signal = getHat();
        this.signalRest = signal.getInitialPose();
        this.summonSignal = root.hasChild(SUMMON_SIGNAL) ? root.getChild(SUMMON_SIGNAL) : null;
        this.upgradeSignal = root.hasChild(UPGRADE_SIGNAL) ? root.getChild(UPGRADE_SIGNAL) : null;
    }

    /** Adds the Artificer's two empty telegraph parts to a layer. */
    public static net.minecraft.client.model.geom.builders.LayerDefinition addSummonSignals(
            net.minecraft.client.model.geom.builders.LayerDefinition layer) {
        return layer.apply(mesh -> {
            mesh.getRoot().addOrReplaceChild(SUMMON_SIGNAL, net.minecraft.client.model.geom.builders.CubeListBuilder.create(), PartPose.ZERO);
            mesh.getRoot().addOrReplaceChild(UPGRADE_SIGNAL, net.minecraft.client.model.geom.builders.CubeListBuilder.create(), PartPose.ZERO);
            return mesh;
        });
    }

    private static void writeSignal(@org.jetbrains.annotations.Nullable ModelPart part, float rx, float ry, float rz) {
        if (part != null) {
            part.setInitialPose(PartPose.offsetAndRotation(0f, 0f, 0f, rx, ry, rz));
            part.xRot = rx;
            part.yRot = ry;
            part.zRot = rz;
        }
    }

    @Override
    public void setupAnim(RaidIllagerRenderState state) {
        float rx = state.secondsSinceShot;
        float rz = state.animState;
        float ry = state.extraSignal;
        signal.setInitialPose(PartPose.offsetAndRotation(signalRest.x(), signalRest.y(), signalRest.z(), rx, ry, rz));
        int plan = state.summonPlan;
        int base = net.gunmetal.raids.entity.Artificer.PLAN_BASE;
        writeSignal(summonSignal, plan % base, (plan / base) % base, (plan / (base * base)) % base);
        writeSignal(upgradeSignal, 0f, 0f, plan >= net.gunmetal.raids.entity.Artificer.PLAN_UPGRADE ? 1f : 0f);
        super.setupAnim(state);
        signal.xRot = rx;
        signal.yRot = ry;
        signal.zRot = rz;
    }
}
