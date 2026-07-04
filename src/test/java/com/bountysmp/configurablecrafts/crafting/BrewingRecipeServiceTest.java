package com.bountysmp.configurablecrafts.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bountysmp.configurablecrafts.BukkitTest;
import com.bountysmp.configurablecrafts.model.IngredientSpec;
import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.RecipeKind;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class BrewingRecipeServiceTest extends BukkitTest {
    @Test
    void matchingBottleSlotsReturnsAllMatchingBottleSlots() {
        ManagedRecipe recipe = new ManagedRecipe("brew", RecipeKind.BREWING);
        recipe.setIngredient(0, IngredientSpec.fromSample(new ItemStack(Material.POTION)));
        BrewerInventory inventory = (BrewerInventory) Bukkit.createInventory(null, InventoryType.BREWING);
        inventory.setItem(0, new ItemStack(Material.POTION));
        inventory.setItem(1, new ItemStack(Material.POTION));
        inventory.setItem(2, new ItemStack(Material.GLASS_BOTTLE));

        assertEquals(List.of(0, 1), BrewingRecipeService.matchingBottleSlots(recipe, inventory));
    }
}
