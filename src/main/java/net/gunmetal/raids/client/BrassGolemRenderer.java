package net.gunmetal.raids.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.animal.golem.CopperGolemModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.CopperGolemRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.golem.CopperGolemState;
import net.minecraft.world.level.block.WeatheringCopper;
import net.gunmetal.raids.entity.AbstractBrassGolem;
import net.gunmetal.raids.entity.DeployedGolem;
import net.gunmetal.raids.item.GolemLoad;

import java.util.EnumMap;
import java.util.Map;

/**
 * Both brass golems: the copper golem model and animations (one model layer, gunmetal:brass_golem
 * #main, so one .jem covers both), swelling and flashing white while a fuse burns, like a creeper.
 * The texture shows the golem's load (textures/entity/brass_golem/):
 *   brass_golem_hostile.png   the Artificer's hostile golem
 *   brass_golem.png           deployed, empty or only gunpowder
 *   brass_golem_blaze.png     only blaze powder
 *   brass_golem_patina.png    only copper patina
 *   brass_golem_redstone.png  only redstone
 *   brass_golem_glowstone.png only glowstone dust
 *   brass_golem_sulfur.png    only sulfur dust
 *   brass_golem_mixed.png     any mix of two or more kinds
 * (Entity Texture Features draws a matching _e.png on top as the glowing parts.)
 * The model also carries the load as a signal (BrassGolemModel). A .jem "texture" key overrides
 * all of these.
 */
public class BrassGolemRenderer<T extends AbstractBrassGolem> extends MobRenderer<T, BrassGolemRenderer.BrassGolemRenderState, CopperGolemModel> {
    private final Map<GolemLoad.Tank, Identifier> textures = new EnumMap<>(GolemLoad.Tank.class);

    public BrassGolemRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer) {
        super(context, new BrassGolemModel(context.bakeLayer(layer)), 0.4f);
        for (GolemLoad.Tank tank : GolemLoad.Tank.values()) {
            String suffix = tank == GolemLoad.Tank.GUNPOWDER ? "" : "_" + tank.name().toLowerCase(java.util.Locale.ROOT);
            textures.put(tank, Identifier.fromNamespaceAndPath("gunmetal", "textures/entity/brass_golem/brass_golem" + suffix + ".png"));
        }
    }

    public static class BrassGolemRenderState extends CopperGolemRenderState {
        public float swelling;
        public GolemLoad.Tank tank = GolemLoad.Tank.GUNPOWDER;
    }

    @Override
    public void extractRenderState(T golem, BrassGolemRenderState state, float partialTick) {
        super.extractRenderState(golem, state, partialTick);
        state.weathering = WeatheringCopper.WeatherState.UNAFFECTED;
        state.copperGolemState = CopperGolemState.IDLE;
        state.idleAnimationState.copyFrom(golem.idleAnimationState);
        state.swelling = golem.getSwelling(partialTick);
        state.tank = golem instanceof DeployedGolem deployed ? deployed.tank() : GolemLoad.Tank.HOSTILE;
    }

    /** Creeper swell. */
    @Override
    protected void scale(BrassGolemRenderState state, PoseStack poseStack) {
        float swelling = state.swelling;
        float wobble = 1.0f + Mth.sin(swelling * 100.0f) * swelling * 0.01f;
        swelling = Mth.clamp(swelling, 0.0f, 1.0f);
        swelling *= swelling;
        swelling *= swelling;
        float xz = (1.0f + swelling * 0.4f) * wobble;
        float y = (1.0f + swelling * 0.1f) / wobble;
        poseStack.scale(xz, y, xz);
    }

    /** Creeper flash. */
    @Override
    protected float getWhiteOverlayProgress(BrassGolemRenderState state) {
        float swelling = state.swelling;
        return (int) (swelling * 10.0f) % 2 == 0 ? 0.0f : Mth.clamp(swelling, 0.5f, 1.0f);
    }

    @Override
    public Identifier getTextureLocation(BrassGolemRenderState state) {
        return textures.get(state.tank);
    }

    @Override
    public BrassGolemRenderState createRenderState() {
        return new BrassGolemRenderState();
    }
}
