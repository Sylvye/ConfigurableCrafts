package com.bountysmp.configurablecrafts.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bountysmp.configurablecrafts.BukkitTest;
import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import java.util.HashSet;
import org.bukkit.Material;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.Test;

class VanillaBrewingCatalogTest extends BukkitTest {
    @Test
    void catalogContainsEverySupportedTransitionWithStableUniqueKeys() {
        assertEquals(54, VanillaBrewingCatalog.entries().size());
        HashSet<String> keys = new HashSet<>();
        for (VanillaBrewingCatalog.Entry entry : VanillaBrewingCatalog.entries()) {
            assertTrue(entry.sourceKey().startsWith(VanillaBrewingCatalog.SOURCE_PREFIX));
            assertTrue(keys.add(entry.sourceKey()));
            assertEquals(entry, VanillaBrewingCatalog.bySourceKey(entry.sourceKey()));
        }
    }

    @Test
    void turtleMasterUsesVanillaHelmetAndCanBecomeScute() {
        VanillaBrewingCatalog.Entry entry = VanillaBrewingCatalog.bySourceKey("minecraft:brewing/turtle_master");
        assertNotNull(entry);
        assertEquals(Material.TURTLE_HELMET, entry.ingredient());

        ManagedRecipe override = entry.toManagedRecipe();
        override.setIngredient(1, com.bountysmp.configurablecrafts.model.IngredientSpec.fromSample(new org.bukkit.inventory.ItemStack(Material.TURTLE_SCUTE)));

        assertEquals(Material.TURTLE_SCUTE, override.ingredient(1).sample().getType());
        assertEquals(PotionType.TURTLE_MASTER, ((PotionMeta) override.result().getItemMeta()).getBasePotionType());
    }

    @Test
    void weaknessHasWaterAndFermentedSpiderEyeDefaults() {
        ManagedRecipe weakness = VanillaBrewingCatalog.bySourceKey("minecraft:brewing/weakness").toManagedRecipe();
        assertEquals(PotionType.WATER, ((PotionMeta) weakness.ingredient(0).sample().getItemMeta()).getBasePotionType());
        assertEquals(Material.FERMENTED_SPIDER_EYE, weakness.ingredient(1).sample().getType());
        assertEquals(PotionType.WEAKNESS, ((PotionMeta) weakness.result().getItemMeta()).getBasePotionType());
    }
}
