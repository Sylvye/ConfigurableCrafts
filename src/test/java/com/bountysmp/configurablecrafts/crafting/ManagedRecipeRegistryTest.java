package com.bountysmp.configurablecrafts.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bountysmp.configurablecrafts.BukkitTest;
import com.bountysmp.configurablecrafts.model.IngredientSpec;
import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.RecipeKind;
import com.bountysmp.configurablecrafts.storage.RecipeRepository;
import java.io.File;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

class ManagedRecipeRegistryTest extends BukkitTest {
    @TempDir
    File tempDir;

    @Test
    void validateForSaveRejectsUnknownDimensions() {
        ManagedRecipeRegistry registry = registry();
        ManagedRecipe recipe = validRecipe();
        recipe.conditions().dimensions().add("minecraft:not_loaded");

        assertEquals("Unknown dimension(s): minecraft:not_loaded", registry.validateForSave(recipe));
    }

    @Test
    void validateForSaveRejectsUnknownBiomes() {
        ManagedRecipeRegistry registry = registry();
        ManagedRecipe recipe = validRecipe();
        recipe.conditions().biomes().add("minecraft:not_a_biome");

        assertEquals("Unknown biome(s): minecraft:not_a_biome", registry.validateForSave(recipe));
    }

    @Test
    void validateForSaveRejectsUnknownTags() {
        ManagedRecipeRegistry registry = registry();
        ManagedRecipe recipe = new ManagedRecipe("invalid_tag", RecipeKind.SHAPED);
        recipe.setResult(new ItemStack(Material.CHEST));
        recipe.setIngredient(0, IngredientSpec.fromTag("minecraft:not_a_tag"));

        assertEquals("Unknown item tag: minecraft:not_a_tag", registry.validateForSave(recipe));
    }

    private ManagedRecipeRegistry registry() {
        return new ManagedRecipeRegistry(MockBukkit.createMockPlugin(), new RecipeRepository(new File(tempDir, "recipes.yml")));
    }

    private ManagedRecipe validRecipe() {
        ManagedRecipe recipe = new ManagedRecipe("valid", RecipeKind.SHAPED);
        recipe.setResult(new ItemStack(Material.DIAMOND));
        recipe.setIngredient(0, IngredientSpec.fromSample(new ItemStack(Material.STICK)));
        return recipe;
    }
}
