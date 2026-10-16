package games.brennan.tradeeverything.mixin;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes smithing ingredients so RecipeValues can price netherite gear. */
@Mixin(SmithingTransformRecipe.class)
public interface SmithingTransformRecipeAccessor {

    // 1.21.2+ exposes these publicly (templateIngredient() etc.) and RecipeValues uses
    // those; the accessor's types still have to match the fields or it fails to apply.
    //? if >=1.21.11 {
    /*@Accessor("template")
    java.util.Optional<Ingredient> tradeeverything$template();

    @Accessor("base")
    Ingredient tradeeverything$base();

    @Accessor("addition")
    java.util.Optional<Ingredient> tradeeverything$addition();
    *///?} else {
    @Accessor("template")
    Ingredient tradeeverything$template();

    @Accessor("base")
    Ingredient tradeeverything$base();

    @Accessor("addition")
    Ingredient tradeeverything$addition();
    //?}
}
