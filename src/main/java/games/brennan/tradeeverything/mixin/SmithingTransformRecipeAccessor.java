package games.brennan.tradeeverything.mixin;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes smithing ingredients so RecipeValues can price netherite gear. */
@Mixin(SmithingTransformRecipe.class)
public interface SmithingTransformRecipeAccessor {

    @Accessor("template")
    Ingredient tradeeverything$template();

    @Accessor("base")
    Ingredient tradeeverything$base();

    @Accessor("addition")
    Ingredient tradeeverything$addition();
}
