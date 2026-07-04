package com.bountysmp.configurablecrafts.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bountysmp.configurablecrafts.BukkitTest;
import com.bountysmp.configurablecrafts.model.IngredientSpec;
import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.RecipeKind;
import java.util.List;
import java.util.OptionalInt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class BrewingRecipeServiceTest extends BukkitTest {
    @Test
    void matchingBrewTimeReturnsCustomRecipeTime() {
        ManagedRecipe recipe = brewingRecipe("brew", Material.DIAMOND, Material.APPLE, 80);
        BrewerInventory inventory = brewerInventory();
        inventory.setItem(0, new ItemStack(Material.DIAMOND));

        OptionalInt brewTime = BrewingRecipeService.matchingBrewTime(List.of(recipe), inventory, new ItemStack(Material.APPLE));

        assertTrue(brewTime.isPresent());
        assertEquals(80, brewTime.getAsInt());
    }

    @Test
    void matchingBrewTimeAllowsMixedRecipesWithSameReagentAndTime() {
        ManagedRecipe first = brewingRecipe("first", Material.DIAMOND, Material.APPLE, 80);
        ManagedRecipe second = brewingRecipe("second", Material.EMERALD, Material.APPLE, 80);
        BrewerInventory inventory = brewerInventory();
        inventory.setItem(0, new ItemStack(Material.DIAMOND));
        inventory.setItem(1, new ItemStack(Material.EMERALD));

        OptionalInt brewTime = BrewingRecipeService.matchingBrewTime(List.of(first, second), inventory, new ItemStack(Material.APPLE));

        assertTrue(brewTime.isPresent());
        assertEquals(80, brewTime.getAsInt());
    }

    @Test
    void matchingBrewTimeReturnsEmptyWhenNoCustomRecipeMatches() {
        ManagedRecipe recipe = brewingRecipe("brew", Material.DIAMOND, Material.APPLE, 80);
        BrewerInventory inventory = brewerInventory();
        inventory.setItem(0, new ItemStack(Material.EMERALD));

        OptionalInt brewTime = BrewingRecipeService.matchingBrewTime(List.of(recipe), inventory, new ItemStack(Material.APPLE));

        assertTrue(brewTime.isEmpty());
    }

    private ManagedRecipe brewingRecipe(String id, Material input, Material ingredient, int brewTimeTicks) {
        ManagedRecipe recipe = new ManagedRecipe(id, RecipeKind.BREWING);
        recipe.setResult(new ItemStack(Material.POTION));
        recipe.setIngredient(0, IngredientSpec.fromSample(new ItemStack(input)));
        recipe.setIngredient(1, IngredientSpec.fromSample(new ItemStack(ingredient)));
        recipe.setBrewTimeTicks(brewTimeTicks);
        return recipe;
    }

    private BrewerInventory brewerInventory() {
        return (BrewerInventory) Bukkit.createInventory(null, InventoryType.BREWING);
    }
}
