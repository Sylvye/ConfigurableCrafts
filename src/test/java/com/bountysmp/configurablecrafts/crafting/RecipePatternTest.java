package com.bountysmp.configurablecrafts.crafting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bountysmp.configurablecrafts.BukkitTest;
import com.bountysmp.configurablecrafts.model.IngredientSpec;
import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.RecipeKind;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class RecipePatternTest extends BukkitTest {
    @Test
    void shapedRecipesMatchByTrimmedBounds() {
        ManagedRecipe recipe = new ManagedRecipe("test", RecipeKind.SHAPED);
        recipe.setIngredient(4, IngredientSpec.fromSample(new ItemStack(Material.DIAMOND)));
        recipe.setIngredient(7, IngredientSpec.fromSample(new ItemStack(Material.STICK)));

        ItemStack[] shifted = new ItemStack[9];
        shifted[0] = new ItemStack(Material.DIAMOND);
        shifted[3] = new ItemStack(Material.STICK);

        assertTrue(RecipePattern.matches(recipe, shifted));
    }

    @Test
    void shapedRecipesRejectExtraItems() {
        ManagedRecipe recipe = new ManagedRecipe("test", RecipeKind.SHAPED);
        recipe.setIngredient(0, IngredientSpec.fromSample(new ItemStack(Material.DIAMOND)));

        ItemStack[] matrix = new ItemStack[9];
        matrix[0] = new ItemStack(Material.DIAMOND);
        matrix[8] = new ItemStack(Material.STICK);

        assertFalse(RecipePattern.matches(recipe, matrix));
    }

    @Test
    void shapelessRecipesBacktrackDuplicateMaterials() {
        ManagedRecipe recipe = new ManagedRecipe("test", RecipeKind.SHAPELESS);
        recipe.setIngredient(0, IngredientSpec.fromSample(new ItemStack(Material.STRING)));
        recipe.setIngredient(1, IngredientSpec.fromSample(new ItemStack(Material.STRING)));
        recipe.setIngredient(2, IngredientSpec.fromSample(new ItemStack(Material.SLIME_BALL)));

        ItemStack[] matrix = new ItemStack[9];
        matrix[4] = new ItemStack(Material.SLIME_BALL);
        matrix[7] = new ItemStack(Material.STRING);
        matrix[8] = new ItemStack(Material.STRING);

        assertTrue(RecipePattern.matches(recipe, matrix));
    }
}
