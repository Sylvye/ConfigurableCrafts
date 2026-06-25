package com.bountysmp.configurablecrafts.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bountysmp.configurablecrafts.BukkitTest;
import com.bountysmp.configurablecrafts.model.IngredientSpec;
import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.RecipeKind;
import java.io.File;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecipeRepositoryTest extends BukkitTest {
    @TempDir
    File tempDir;

    @Test
    void yamlRoundTripPreservesRecipeFields() {
        RecipeRepository repository = new RecipeRepository(new File(tempDir, "recipes.yml"));
        ManagedRecipe recipe = new ManagedRecipe("roundtrip", RecipeKind.SHAPELESS);
        recipe.setSourceKey("minecraft:golden_apple");
        recipe.setEnabled(false);
        recipe.setResult(new ItemStack(Material.GOLDEN_APPLE));
        recipe.setIngredient(0, IngredientSpec.fromSample(new ItemStack(Material.APPLE)));
        recipe.conditions().dimensions().add("minecraft:overworld");
        recipe.conditions().setMinimumExperienceLevel(7);

        repository.save(List.of(recipe));
        List<ManagedRecipe> loaded = repository.load();

        assertEquals(1, loaded.size());
        ManagedRecipe copy = loaded.getFirst();
        assertEquals("roundtrip", copy.id());
        assertEquals("minecraft:golden_apple", copy.sourceKey());
        assertEquals(RecipeKind.SHAPELESS, copy.kind());
        assertTrue(!copy.enabled());
        assertEquals(Material.GOLDEN_APPLE, copy.result().getType());
        assertEquals(Material.APPLE, copy.ingredient(0).sample().getType());
        assertEquals(7, copy.conditions().minimumExperienceLevel());
    }
}
