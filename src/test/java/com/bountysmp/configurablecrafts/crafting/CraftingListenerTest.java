package com.bountysmp.configurablecrafts.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bountysmp.configurablecrafts.BukkitTest;
import com.bountysmp.configurablecrafts.model.IngredientSpec;
import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.RecipeKind;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class CraftingListenerTest extends BukkitTest {
    @Test
    void shiftCraftCountUsesIngredientAndInventoryCaps() {
        ItemStack[] matrix = new ItemStack[9];
        matrix[0] = new ItemStack(Material.STICK, 8);
        matrix[1] = new ItemStack(Material.DIAMOND, 3);
        Inventory inventory = Bukkit.createInventory(null, 9);

        assertEquals(3, CraftingListener.shiftCraftCount(matrix, inventory, new ItemStack(Material.EMERALD)));
    }

    @Test
    void shiftCraftCountCapsByDestinationCapacity() {
        ItemStack[] matrix = new ItemStack[9];
        matrix[0] = new ItemStack(Material.STICK, 10);
        Inventory inventory = Bukkit.createInventory(null, 9);
        for (int i = 1; i < inventory.getSize(); i++) {
            inventory.setItem(i, new ItemStack(Material.DIRT, 64));
        }
        inventory.setItem(0, new ItemStack(Material.EMERALD, 63));

        assertEquals(1, CraftingListener.shiftCraftCount(matrix, inventory, new ItemStack(Material.EMERALD)));
    }

    @Test
    void configuredRemaindersScaleAndSplitIntoStacks() {
        ManagedRecipe recipe = new ManagedRecipe("remainders", RecipeKind.SHAPELESS);
        IngredientSpec ingredient = IngredientSpec.fromSample(new ItemStack(Material.DIAMOND));
        ingredient.setRemainder(new ItemStack(Material.BOWL, 20));
        recipe.setIngredient(0, ingredient);

        var remainders = CraftingListener.configuredRemainders(recipe, 4);

        assertEquals(2, remainders.size());
        assertEquals(64, remainders.get(0).getAmount());
        assertEquals(16, remainders.get(1).getAmount());
    }

    @Test
    void configuredRemainderDoesNotDuplicateVanillaContainer() {
        ManagedRecipe recipe = new ManagedRecipe("milk", RecipeKind.SHAPELESS);
        IngredientSpec ingredient = IngredientSpec.fromSample(new ItemStack(Material.MILK_BUCKET));
        ingredient.setRemainder(new ItemStack(Material.BUCKET));
        recipe.setIngredient(0, ingredient);

        assertEquals(0, CraftingListener.configuredRemainders(recipe, 1).size());
    }
}
