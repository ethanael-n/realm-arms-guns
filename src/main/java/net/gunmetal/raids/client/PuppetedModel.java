package net.gunmetal.raids.client;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.monster.piglin.AdultPiglinModel;

/**
 * The Puppeted's two models, both on vanilla's piglin geometry (head with ears, body, arms, legs;
 * model layers gunmetal:puppeted_incomplete#main and gunmetal:puppeted_complete#main, EMF:
 * puppeted_incomplete.jem / puppeted_complete.jem):
 *   incomplete: arms raised forward while aggressive, like a zombified piglin;
 *   completed:  plain humanoid arms (swings when it hits), like an unarmed piglin.
 */
public class PuppetedModel extends HumanoidModel<PuppetedRenderer.PuppetedRenderState> {
    private final boolean zombieArms;

    public PuppetedModel(ModelPart root, boolean zombieArms) {
        super(root);
        this.zombieArms = zombieArms;
    }

    /** Vanilla's adult piglin geometry (the zombified piglin uses the same). */
    public static LayerDefinition createBodyLayer() {
        return AdultPiglinModel.createBodyLayer();
    }

    @Override
    public void setupAnim(PuppetedRenderer.PuppetedRenderState state) {
        super.setupAnim(state);
        if (zombieArms) {
            AnimationUtils.animateZombieArms(leftArm, rightArm, state.isAggressive, state);
        }
    }
}
