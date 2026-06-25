package com.bountysmp.configurablecrafts.crafting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bountysmp.configurablecrafts.BukkitTest;
import com.bountysmp.configurablecrafts.model.IngredientSpec;
import com.bountysmp.configurablecrafts.model.MatcherType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

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
}
