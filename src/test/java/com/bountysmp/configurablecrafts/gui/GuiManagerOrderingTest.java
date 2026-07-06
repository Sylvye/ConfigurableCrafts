package com.bountysmp.configurablecrafts.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bountysmp.configurablecrafts.BukkitTest;
import com.bountysmp.configurablecrafts.model.IngredientSpec;
import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.RecipeKind;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class GuiManagerOrderingTest extends BukkitTest {
    @Test
    void mainRecipeComparatorOrdersNbtSpecificMatchesBeforeBroadMatches() {
        ManagedRecipe broad = recipe("a_broad", IngredientSpec.fromSample(new ItemStack(Material.DIAMOND)));
        ManagedRecipe exact = recipe("z_exact", IngredientSpec.fromExactSample(new ItemStack(Material.DIAMOND)));
        List<ManagedRecipe> recipes = new ArrayList<>(List.of(broad, exact));

        recipes.sort(GuiManager.mainRecipeComparator());

        assertEquals(List.of("z_exact", "a_broad"), recipes.stream().map(ManagedRecipe::id).toList());
    }

    @Test
    void mainRecipeComparatorOrdersRecipeTypeBeforeNbtSpecificity() {
        ManagedRecipe shapeless = recipe("z_shapeless_broad", RecipeKind.SHAPELESS, IngredientSpec.fromSample(new ItemStack(Material.DIAMOND)));
        ManagedRecipe brewing = recipe("a_brewing_exact", RecipeKind.BREWING, IngredientSpec.fromExactSample(new ItemStack(Material.DIAMOND)));
        List<ManagedRecipe> recipes = new ArrayList<>(List.of(brewing, shapeless));

        recipes.sort(GuiManager.mainRecipeComparator());

        assertEquals(List.of("z_shapeless_broad", "a_brewing_exact"), recipes.stream().map(ManagedRecipe::id).toList());
    }

    @Test
    void mainRecipeComparatorUsesStateAfterTypeAndNbtSpecificity() {
        ManagedRecipe enabled = recipe("z_enabled_broad", IngredientSpec.fromSample(new ItemStack(Material.DIAMOND)));
        ManagedRecipe disabled = recipe("a_disabled_broad", IngredientSpec.fromSample(new ItemStack(Material.DIAMOND)));
        disabled.setEnabled(false);
        List<ManagedRecipe> recipes = new ArrayList<>(List.of(disabled, enabled));

        recipes.sort(GuiManager.mainRecipeComparator());

        assertEquals(List.of("z_enabled_broad", "a_disabled_broad"), recipes.stream().map(ManagedRecipe::id).toList());
    }

    private static ManagedRecipe recipe(String id, IngredientSpec ingredient) {
        return recipe(id, RecipeKind.SHAPELESS, ingredient);
    }

    private static ManagedRecipe recipe(String id, RecipeKind kind, IngredientSpec ingredient) {
        ManagedRecipe recipe = new ManagedRecipe(id, kind);
        recipe.setIngredient(0, ingredient);
        recipe.setResult(new ItemStack(Material.STONE));
        return recipe;
    }
}
