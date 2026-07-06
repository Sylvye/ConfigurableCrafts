package com.bountysmp.configurablecrafts.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.bountysmp.configurablecrafts.BukkitTest;
import com.bountysmp.configurablecrafts.model.IngredientSpec;
import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.RecipeKind;
import com.bountysmp.configurablecrafts.model.WeatherMode;
import com.bountysmp.configurablecrafts.storage.RecipeRepository;
import io.papermc.paper.potion.PotionMix;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
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

    @Test
    void validateForSaveRejectsCrafterAccessWithConditions() {
        ManagedRecipeRegistry registry = registry();
        ManagedRecipe recipe = validRecipe();
        recipe.setAllowCrafters(true);
        recipe.conditions().setMinimumExperienceLevel(1);

        assertEquals("Crafters can only be allowed when dimension, weather, XP, biome, and crafting limit requirements are not configured.", registry.validateForSave(recipe));
    }

    @Test
    void validateForSaveRejectsCrafterAccessWithLimits() {
        ManagedRecipeRegistry registry = registry();
        ManagedRecipe recipe = validRecipe();
        recipe.setAllowCrafters(true);
        recipe.globalLimit().set(1, 60);

        assertEquals("Crafters can only be allowed when dimension, weather, XP, biome, and crafting limit requirements are not configured.", registry.validateForSave(recipe));
    }

    @Test
    void warningsForSaveWarnsWhenCraftersAreAllowed() {
        ManagedRecipeRegistry registry = registry();
        ManagedRecipe recipe = validRecipe();
        recipe.setAllowCrafters(true);

        assertEquals(List.of(ManagedRecipeRegistry.CRAFTER_BYPASS_WARNING), registry.warningsForSave(recipe));
    }

    @Test
    void validateForSaveRejectsBrewingPerPlayerLimits() {
        ManagedRecipeRegistry registry = registry();
        ManagedRecipe recipe = validBrewingRecipe();
        recipe.playerLimit().set(1, 60);

        assertEquals("Brewing recipes cannot use per-player limits.", registry.validateForSave(recipe));
    }

    @Test
    void validateForSaveRejectsBrewingExperienceRequirement() {
        ManagedRecipeRegistry registry = registry();
        ManagedRecipe recipe = validBrewingRecipe();
        recipe.conditions().setMinimumExperienceLevel(1);

        assertEquals("Brewing recipes cannot require player experience levels.", registry.validateForSave(recipe));
    }

    @Test
    void validateForSaveAcceptsArbitraryBrewingInput() {
        ManagedRecipeRegistry registry = registry();
        ManagedRecipe recipe = validBrewingRecipe();
        recipe.setIngredient(0, IngredientSpec.fromSample(new ItemStack(Material.DIAMOND)));
        recipe.setIngredient(1, IngredientSpec.fromSample(new ItemStack(Material.APPLE)));

        assertNull(registry.validateForSave(recipe));
    }

    @Test
    void validateForSaveRejectsBrewingGlobalLimits() {
        ManagedRecipeRegistry registry = registry();
        ManagedRecipe recipe = validBrewingRecipe();
        recipe.globalLimit().set(1, 60);

        assertEquals("Brewing recipes cannot use global limits.", registry.validateForSave(recipe));
    }

    @Test
    void validateForSaveRejectsBrewingConditions() {
        ManagedRecipeRegistry registry = registry();
        ManagedRecipe recipe = validBrewingRecipe();
        recipe.conditions().setWeather(WeatherMode.RAIN);

        assertEquals("Brewing recipes cannot use weather conditions.", registry.validateForSave(recipe));
    }

    @Test
    void validateForSaveRejectsDuplicateBrewingSignature() {
        ManagedRecipeRegistry registry = registry();
        ManagedRecipe existing = validBrewingRecipe();
        registry.upsert(existing);
        ManagedRecipe duplicate = validBrewingRecipe();
        duplicate.setId("duplicate");
        duplicate.setResult(new ItemStack(Material.SPLASH_POTION));

        assertEquals("This brewing recipe collides with valid_brew -> potion.", registry.validateForSave(duplicate));
    }

    @Test
    void validateForSaveAllowsDifferentExactPotionInputsWithSameBrewingIngredient() {
        ManagedRecipeRegistry registry = registry();
        ManagedRecipe invisibility = exactPotionBrewingRecipe("invisibility", PotionType.INVISIBILITY);
        ManagedRecipe nightVision = exactPotionBrewingRecipe("night_vision", PotionType.NIGHT_VISION);
        registry.upsert(invisibility);

        assertNull(registry.validateForSave(nightVision));
    }

    @Test
    void validateForSaveRejectsDuplicateExactPotionInputWithSameBrewingIngredient() {
        ManagedRecipeRegistry registry = registry();
        ManagedRecipe first = exactPotionBrewingRecipe("first", PotionType.INVISIBILITY);
        ManagedRecipe second = exactPotionBrewingRecipe("second", PotionType.INVISIBILITY);
        registry.upsert(first);

        assertEquals("This brewing recipe collides with first -> potion.", registry.validateForSave(second));
    }

    @Test
    void validateForSaveRejectsSharedBrewingIngredientWithDifferentBrewTime() {
        ManagedRecipeRegistry registry = registry();
        ManagedRecipe existing = validBrewingRecipe();
        registry.upsert(existing);
        ManagedRecipe conflicting = validBrewingRecipe();
        conflicting.setId("conflicting_time");
        conflicting.setIngredient(0, IngredientSpec.fromSample(new ItemStack(Material.DIAMOND)));
        conflicting.setBrewTimeTicks(80);

        assertEquals("Brewing recipes using the same ingredient must use the same brew time.", registry.validateForSave(conflicting));
    }

    @Test
    void warningsForSaveWarnsWhenBrewingMayShadowVanilla() {
        ManagedRecipeRegistry registry = registry();
        ManagedRecipe recipe = validBrewingRecipe();

        assertEquals(List.of("Warning: this brewing recipe may override a vanilla brewing mix."), registry.warningsForSave(recipe));
    }

    @Test
    void upsertRegistersAndReplacesPotionMixes() {
        FakePotionMixes potionMixes = new FakePotionMixes();
        ManagedRecipeRegistry registry = registry(potionMixes);
        ManagedRecipe recipe = validBrewingRecipe();

        registry.upsert(recipe);
        registry.upsert(recipe);

        assertEquals(2, potionMixes.added.size());
        assertEquals(3, potionMixes.removed.size());
        assertEquals(List.of("recipe/valid_brew", "recipe/valid_brew", "recipe/valid_brew"), potionMixes.removed.stream().map(NamespacedKey::getKey).toList());
    }

    @Test
    void removeOrRevertUnregistersPotionMix() {
        FakePotionMixes potionMixes = new FakePotionMixes();
        ManagedRecipeRegistry registry = registry(potionMixes);
        ManagedRecipe recipe = validBrewingRecipe();
        registry.upsert(recipe);

        registry.removeOrRevert(recipe.id());

        assertEquals(1, potionMixes.added.size());
        assertEquals(2, potionMixes.removed.size());
    }

    private ManagedRecipeRegistry registry() {
        return registry(new FakePotionMixes());
    }

    private ManagedRecipeRegistry registry(FakePotionMixes potionMixes) {
        return new ManagedRecipeRegistry(MockBukkit.createMockPlugin(), new RecipeRepository(new File(tempDir, "recipes.yml")), potionMixes);
    }

    private ManagedRecipe validRecipe() {
        ManagedRecipe recipe = new ManagedRecipe("valid", RecipeKind.SHAPED);
        recipe.setResult(new ItemStack(Material.DIAMOND));
        recipe.setIngredient(0, IngredientSpec.fromSample(new ItemStack(Material.STICK)));
        return recipe;
    }

    private ManagedRecipe validBrewingRecipe() {
        ManagedRecipe recipe = new ManagedRecipe("valid_brew", RecipeKind.BREWING);
        recipe.setResult(new ItemStack(Material.POTION));
        recipe.setIngredient(0, IngredientSpec.fromSample(new ItemStack(Material.POTION)));
        recipe.setIngredient(1, IngredientSpec.fromSample(new ItemStack(Material.REDSTONE)));
        return recipe;
    }

    private ManagedRecipe exactPotionBrewingRecipe(String id, PotionType potionType) {
        ManagedRecipe recipe = new ManagedRecipe(id, RecipeKind.BREWING);
        recipe.setResult(new ItemStack(Material.POTION));
        recipe.setIngredient(0, IngredientSpec.fromExactSample(potion(potionType)));
        recipe.setIngredient(1, IngredientSpec.fromExactSample(new ItemStack(Material.PITCHER_POD)));
        return recipe;
    }

    private ItemStack potion(PotionType potionType) {
        ItemStack itemStack = new ItemStack(Material.POTION);
        itemStack.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(potionType));
        return itemStack;
    }

    private static final class FakePotionMixes implements ManagedRecipeRegistry.PotionMixes {
        private final List<PotionMix> added = new ArrayList<>();
        private final List<NamespacedKey> removed = new ArrayList<>();

        @Override
        public void add(PotionMix potionMix) {
            added.add(potionMix);
        }

        @Override
        public void remove(NamespacedKey key) {
            removed.add(key);
        }
    }
}
