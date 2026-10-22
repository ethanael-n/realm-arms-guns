package net.gunmetal.arms.client;

import net.gunmetal.arms.guns.GunItem;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.gunmetal.arms.guns.ClipGunItem;
import net.gunmetal.arms.guns.RealmGun;
import org.apache.commons.lang3.tuple.Pair;

import static net.gunmetal.arms.RealmArmsWeapons.MOD_ID;

/**
 * Replaces the vanilla crosshair while a Realm Arms gun is in the main hand. The texture shows how
 * far the gun is loaded, rounded DOWN to 0/20/40/60/80%; the 100% texture only appears once the
 * gun is loaded and will fire on the next click.
 * Textures: assets/gunmetal/textures/gui/sprites/hud/crosshair_<0|20|40|60|80|100>.png
 */
public final class GunCrosshair {
    /**
     * Drawn size in GUI pixels (vanilla's crosshair is 15). Textures can be any square size; they're
     * scaled to this. At 64 with 64x64 textures, one texture pixel = one GUI pixel.
     */
    private static final int SIZE = 70;
    private static final Identifier[] STAGES = new Identifier[6];
    /**
     * Clip guns (revolver, repeater): one texture per round in the clip (counts up as cartridges go in,
     * down with each shot), one for an empty clip, one while fanning the hammer.
     * Textures: hud/clip_crosshair_1 ... _6, hud/clip_crosshair_empty, hud/clip_crosshair_fan.
     */
    private static final Identifier[] CLIP_ROUNDS = new Identifier[ClipGunItem.CLIP + 1];
    private static final Identifier CLIP_FAN = Identifier.fromNamespaceAndPath(MOD_ID, "hud/clip_crosshair_fan");

    static {
        for (int i = 0; i <= 5; i++) {
            STAGES[i] = Identifier.fromNamespaceAndPath(MOD_ID, "hud/crosshair_" + (i * 20));
        }
        CLIP_ROUNDS[0] = Identifier.fromNamespaceAndPath(MOD_ID, "hud/clip_crosshair_empty");
        for (int i = 1; i <= ClipGunItem.CLIP; i++) {
            CLIP_ROUNDS[i] = Identifier.fromNamespaceAndPath(MOD_ID, "hud/clip_crosshair_" + i);
        }
    }

    private GunCrosshair() {
    }

    /** Wraps the vanilla crosshair element. */
    public static HudElement wrap(HudElement vanilla) {
        return (graphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            LocalPlayer player = client.player;
            if (player == null || !(player.getMainHandItem().getItem() instanceof RealmGun)) {
                vanilla.extractRenderState(graphics, deltaTracker);
                return;
            }
            // Also shown while scoped with the rifle, since the scope overlay has no centre mark.
            if (!client.options.getCameraType().isFirstPerson() || player.isSpectator()) {
                return;
            }
            draw(graphics, player, deltaTracker);
        };
    }

    private static void draw(GuiGraphicsExtractor graphics, LocalPlayer player, DeltaTracker deltaTracker) {
        ItemStack stack = player.getMainHandItem();
        Identifier sprite;
        if (stack.getItem() instanceof ClipGunItem) {
            sprite = ClipGunItem.showsFanPose(player, stack) ? CLIP_FAN
                    : CLIP_ROUNDS[Math.max(0, Math.min(ClipGunItem.CLIP, ClipGunItem.rounds(stack)))];
        } else {
            sprite = STAGES[loadStage(player, stack)];
        }
        int x = (graphics.guiWidth() - SIZE) / 2;
        int y = (graphics.guiHeight() - SIZE) / 2;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, SIZE, SIZE);
    }

    /** 0..5 = 0%, 20%, 40%, 60%, 80%, 100%. */
    static int loadStage(LocalPlayer player, ItemStack stack) {
        if (GunItem.isLoaded(stack)) {
            return 5;
        }
        Pair<Integer, Integer> duration = GunItem.getLoadingDuration(stack);
        int stages = duration.getLeft();
        int ticksPerStage = Math.max(1, duration.getRight());
        int storedStage = GunItem.getLoadingStage(stack);
        float completedStages = Math.max(0, storedStage - 1);
        if (player.isUsingItem() && ItemStack.isSameItem(player.getUseItem(), stack) && storedStage > 0) {
            completedStages += (float) player.getTicksUsingItem() / ticksPerStage;
        }
        float fraction = Math.min(0.999f, completedStages / stages);
        return Math.min(4, (int) Math.floor(fraction * 5));
    }
}
