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
        recipe.setAllowCrafters(true);
        recipe.setResult(new ItemStack(Material.GOLDEN_APPLE));
        recipe.setIngredient(0, IngredientSpec.fromSample(new ItemStack(Material.APPLE)));
        recipe.conditions().dimensions().add("minecraft:overworld");
        recipe.conditions().setMinimumExperienceLevel(7);
        recipe.playerLimit().set(5, 600);
        recipe.globalLimit().set(20, 3600);

        repository.save(List.of(recipe));
        List<ManagedRecipe> loaded = repository.load();

        assertEquals(1, loaded.size());
        ManagedRecipe copy = loaded.getFirst();
        assertEquals("roundtrip", copy.id());
        assertEquals("minecraft:golden_apple", copy.sourceKey());
        assertEquals(RecipeKind.SHAPELESS, copy.kind());
        assertTrue(!copy.enabled());
        assertTrue(copy.allowCrafters());
        assertEquals(Material.GOLDEN_APPLE, copy.result().getType());
        assertEquals(Material.APPLE, copy.ingredient(0).sample().getType());
        assertEquals(7, copy.conditions().minimumExperienceLevel());
        assertEquals(5, copy.playerLimit().crafts());
        assertEquals(600, copy.playerLimit().windowSeconds());
        assertEquals(20, copy.globalLimit().crafts());
        assertEquals(3600, copy.globalLimit().windowSeconds());
    }

    @Test
    void yamlLoadDefaultsAllowCraftersToFalse() {
        RecipeRepository repository = new RecipeRepository(new File(tempDir, "recipes.yml"));
        ManagedRecipe recipe = new ManagedRecipe("default_crafter", RecipeKind.SHAPED);
        recipe.setResult(new ItemStack(Material.CHEST));
        recipe.setIngredient(0, IngredientSpec.fromSample(new ItemStack(Material.OAK_PLANKS)));

        repository.save(List.of(recipe));

        assertTrue(!repository.load().getFirst().allowCrafters());
    }

    @Test
    void yamlRoundTripPreservesTagOnlyIngredient() {
        RecipeRepository repository = new RecipeRepository(new File(tempDir, "recipes.yml"));
        ManagedRecipe recipe = new ManagedRecipe("tag_only", RecipeKind.SHAPED);
        recipe.setResult(new ItemStack(Material.CHEST));
        recipe.setIngredient(4, IngredientSpec.fromTag("minecraft:logs"));

        repository.save(List.of(recipe));
        List<ManagedRecipe> loaded = repository.load();

        IngredientSpec spec = loaded.getFirst().ingredient(4);
        assertTrue(spec.isTagOnly());
        assertEquals("minecraft:logs", spec.tagKey());
    }

    @Test
    void yamlRoundTripPreservesWorkstationFields() {
        RecipeRepository repository = new RecipeRepository(new File(tempDir, "recipes.yml"));
        ManagedRecipe cooking = new ManagedRecipe("smoke", RecipeKind.SMOKING);
        cooking.setResult(new ItemStack(Material.COOKED_BEEF));
        cooking.setIngredient(0, IngredientSpec.fromSample(new ItemStack(Material.BEEF)));
        cooking.setExperience(0.35F);
        cooking.setCookTimeTicks(120);
        ManagedRecipe brewing = new ManagedRecipe("brew", RecipeKind.BREWING);
        brewing.setResult(new ItemStack(Material.POTION));
        brewing.setIngredient(0, IngredientSpec.fromSample(new ItemStack(Material.POTION)));
        brewing.setIngredient(1, IngredientSpec.fromSample(new ItemStack(Material.REDSTONE)));
        brewing.setBrewTimeTicks(80);
        ManagedRecipe smithing = new ManagedRecipe("smith", RecipeKind.SMITHING);
        smithing.setResult(new ItemStack(Material.DIAMOND_SWORD));
        smithing.setCopyDataComponents(false);

        repository.save(List.of(cooking, brewing, smithing));
        List<ManagedRecipe> loaded = repository.load();

        assertEquals(0.35F, loaded.get(0).experience());
        assertEquals(120, loaded.get(0).cookTimeTicks());
        assertEquals(80, loaded.get(1).brewTimeTicks());
        assertTrue(!loaded.get(2).copyDataComponents());
    }
}
