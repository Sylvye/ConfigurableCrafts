package com.bountysmp.configurablecrafts.crafting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bountysmp.configurablecrafts.BukkitTest;
import com.bountysmp.configurablecrafts.model.IngredientSpec;
import com.bountysmp.configurablecrafts.model.MatcherType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class IngredientMatcherTest extends BukkitTest {
    @Test
    void materialMatcherRequiresSameMaterial() {
        IngredientSpec spec = IngredientSpec.fromSample(new ItemStack(Material.PLAYER_HEAD));

        assertTrue(IngredientMatcher.matches(spec, new ItemStack(Material.PLAYER_HEAD)));
        assertFalse(IngredientMatcher.matches(spec, new ItemStack(Material.ZOMBIE_HEAD)));
    }

    @Test
    void andMatchersAllNeedToPass() {
        ItemStack sample = new ItemStack(Material.DIAMOND_SWORD);
        IngredientSpec spec = IngredientSpec.fromSample(sample);
        spec.setMatcher(MatcherType.EXACT, true);

        assertTrue(IngredientMatcher.matches(spec, new ItemStack(Material.DIAMOND_SWORD)));
        assertFalse(IngredientMatcher.matches(spec, new ItemStack(Material.IRON_SWORD)));
    }

    @Test
    void exactMatcherIsExclusive() {
        IngredientSpec spec = IngredientSpec.fromSample(new ItemStack(Material.DIAMOND_SWORD));
        spec.setMatcher(MatcherType.ITEM_NAME, true);
        spec.setMatcher(MatcherType.EXACT, true);

        assertTrue(spec.hasMatcher(MatcherType.EXACT));
        assertFalse(spec.hasMatcher(MatcherType.MATERIAL));
        assertFalse(spec.hasMatcher(MatcherType.ITEM_NAME));

        spec.setMatcher(MatcherType.ENCHANTMENTS, true);

        assertFalse(spec.hasMatcher(MatcherType.EXACT));
        assertTrue(spec.hasMatcher(MatcherType.ENCHANTMENTS));
    }

    @Test
    void tagOnlyMatcherUsesRegisteredItemTag() {
        MockBukkit.getMock().createMaterialTag(NamespacedKey.minecraft("test_logs"), Tag.REGISTRY_ITEMS, Material.OAK_LOG, Material.SPRUCE_LOG);
        IngredientSpec spec = IngredientSpec.fromTag("minecraft:test_logs");

        assertFalse(spec.isEmpty());
        assertTrue(spec.isTagOnly());
        assertTrue(IngredientMatcher.matches(spec, new ItemStack(Material.OAK_LOG)));
        assertFalse(IngredientMatcher.matches(spec, new ItemStack(Material.STONE)));
        assertInstanceOf(RecipeChoice.MaterialChoice.class, IngredientMatcher.toRecipeChoice(spec));
    }
}
