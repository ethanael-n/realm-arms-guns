package net.gunmetal.raids.item;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.gunmetal.raids.registry.RaidItems;
import org.jetbrains.annotations.Nullable;

/**
 * Loaded Brass Deployables. The plain recipe (redstone torch over carved pumpkin over brass block, one
 * column) is an ordinary shaped recipe (data/gunmetal/recipe/brass_deployable.json, shown in the
 * recipe book). This special recipe takes the same column with 1-6 extra items in the other slots,
 * any mix of gunpowder, blaze powder, copper patina (Additional Additions), redstone, glowstone dust
 * and sulfur dust (Alex's Caves), and writes them onto the result as its load (GolemLoad). Not shown in the recipe book.
 */
public class BrassDeployableRecipe extends CustomRecipe {
    public static final BrassDeployableRecipe INSTANCE = new BrassDeployableRecipe();
    public static final MapCodec<BrassDeployableRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, BrassDeployableRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    public static final RecipeSerializer<BrassDeployableRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private static final Identifier BRASS_BLOCK = Identifier.fromNamespaceAndPath("create", "brass_block");
    private static final Identifier COPPER_PATINA = Identifier.fromNamespaceAndPath("additionaladditions", "copper_patina");
    private static final Identifier SULFUR_DUST = Identifier.fromNamespaceAndPath("alexscaves", "sulfur_dust");

    private static @Nullable Item byId(Identifier id) {
        return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    /** The load in this grid, or null if it isn't a valid loaded deployable recipe. */
    private static @Nullable GolemLoad loadOf(CraftingInput input) {
        if (input.height() != 3 || input.width() > 3) {
            return null;
        }
        Item brassBlock = byId(BRASS_BLOCK);
        Item patinaItem = byId(COPPER_PATINA);
        Item sulfurItem = byId(SULFUR_DUST);
        if (brassBlock == null) {
            return null;
        }
        // Find the redstone torch / pumpkin / brass block column.
        int column = -1;
        for (int x = 0; x < input.width(); ++x) {
            if (input.getItem(x, 0).is(Items.REDSTONE_TORCH) && input.getItem(x, 1).is(Items.CARVED_PUMPKIN)
                    && input.getItem(x, 2).is(brassBlock)) {
                column = x;
                break;
            }
        }
        if (column < 0) {
            return null;
        }
        int gunpowder = 0, blaze = 0, patina = 0, redstone = 0, glowstone = 0, sulfur = 0;
        for (int y = 0; y < 3; ++y) {
            for (int x = 0; x < input.width(); ++x) {
                if (x == column) {
                    continue;
                }
                ItemStack stack = input.getItem(x, y);
                if (stack.isEmpty()) {
                    continue;
                }
                if (stack.is(Items.GUNPOWDER)) {
                    ++gunpowder;
                } else if (stack.is(Items.BLAZE_POWDER)) {
                    ++blaze;
                } else if (patinaItem != null && stack.is(patinaItem)) {
                    ++patina;
                } else if (stack.is(Items.REDSTONE)) {
                    ++redstone;
                } else if (stack.is(Items.GLOWSTONE_DUST)) {
                    ++glowstone;
                } else if (sulfurItem != null && stack.is(sulfurItem)) {
                    ++sulfur;
                } else {
                    return null;
                }
            }
        }
        GolemLoad load = new GolemLoad(gunpowder, blaze, patina, redstone, glowstone, sulfur);
        return load.isEmpty() ? null : load; // the plain one is the shaped recipe
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return loadOf(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        GolemLoad load = loadOf(input);
        if (load == null) {
            return ItemStack.EMPTY;
        }
        ItemStack result = new ItemStack(RaidItems.BRASS_DEPLOYABLE);
        load.applyTo(result);
        return result;
    }

    @Override
    public RecipeSerializer<BrassDeployableRecipe> getSerializer() {
        return SERIALIZER;
    }
}
