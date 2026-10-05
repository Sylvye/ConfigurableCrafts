package com.bountysmp.configurablecrafts.crafting;

import static org.junit.jupiter.api.Assertions.*;

import com.bountysmp.configurablecrafts.BukkitTest;
import com.bountysmp.configurablecrafts.model.*;
import com.bountysmp.configurablecrafts.storage.RecipeRepository;
import java.io.File;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

class ExhaustedRecipeTest extends BukkitTest {
  @TempDir File folder;
  private final AtomicLong now = new AtomicLong(1000);

  private ManagedRecipeRegistry registry(
      org.bukkit.plugin.Plugin plugin, CraftLimitTracker limits) {
    var registry =
        new ManagedRecipeRegistry(
            plugin,
            new RecipeRepository(new File(folder, "recipes.yml")),
            new ManagedRecipeRegistry.PotionMixes() {
              public void add(io.papermc.paper.potion.PotionMix mix) {}

              public void remove(NamespacedKey key) {}
            });
    registry.allowance(limits::globallyAvailable);
    limits.onChange(registry::refreshAvailability);
    return registry;
  }

  private ManagedRecipe recipe(long window) {
    var recipe = new ManagedRecipe("limited", RecipeKind.SHAPED);
    recipe.setResult(new ItemStack(Material.DIAMOND));
    recipe.setIngredient(0, IngredientSpec.fromSample(new ItemStack(Material.STICK)));
    recipe.globalLimit().set(1, window);
    return recipe;
  }

  @Test
  void exhaustedRecipeIsUndiscoveredAndResetRestoresSavedEdits() {
    var plugin = MockBukkit.createMockPlugin();
    var limits = new CraftLimitTracker(null, new File(folder, "limits.yml"), now::get);
    var registry = registry(plugin, limits);
    var recipe = recipe(0);
    var player = MockBukkit.getMock().addPlayer();
    registry.upsert(recipe);
    var key = recipe.managedKey(plugin);
    assertTrue(player.hasDiscoveredRecipe(key));
    assertNull(limits.tryConsume(recipe, player.getUniqueId(), 1));
    assertNull(Bukkit.getRecipe(key));
    assertFalse(player.hasDiscoveredRecipe(key));
    assertNotNull(registry.byManagedKey(key), "cached craft events must still be validated");
    var edited = registry.byId(recipe.id()).copy();
    edited.setIngredient(0, IngredientSpec.fromSample(new ItemStack(Material.GOLD_INGOT)));
    registry.upsert(edited);
    assertNull(Bukkit.getRecipe(key), "editing must not resurrect an exhausted registration");
    limits.resetPermanentGlobal(recipe.id());
    assertNotNull(Bukkit.getRecipe(key));
    assertTrue(player.hasDiscoveredRecipe(key));
    assertEquals(Material.GOLD_INGOT, registry.byId(recipe.id()).ingredient(0).sample().getType());
  }

  @Test
  void restartKeepsAnExhaustedRecipeHidden() {
    var plugin = MockBukkit.createMockPlugin();
    var file = new File(folder, "limits.yml");
    var limits = new CraftLimitTracker(null, file, now::get);
    var registry = registry(plugin, limits);
    var recipe = recipe(0);
    registry.upsert(recipe);
    limits.tryConsume(recipe, UUID.randomUUID(), 1);
    registry.shutdown();
    var reloadedLimits = new CraftLimitTracker(null, file, now::get);
    reloadedLimits.load();
    var reloaded = registry(plugin, reloadedLimits);
    reloaded.load();
    reloaded.applyAll();
    assertTrue(reloaded.byId(recipe.id()).enabled());
    assertNull(Bukkit.getRecipe(recipe.managedKey(plugin)));
    assertNotNull(reloadedLimits.check(recipe, UUID.randomUUID(), 1));
  }

  @Test
  void timedLimitsReappearAfterExpiryButDisabledRecipesStayDisabled() {
    var plugin = MockBukkit.createMockPlugin();
    var limits = new CraftLimitTracker(null, new File(folder, "limits.yml"), now::get);
    var registry = registry(plugin, limits);
    var recipe = recipe(10);
    registry.upsert(recipe);
    limits.tryConsume(recipe, UUID.randomUUID(), 1);
    assertNull(Bukkit.getRecipe(recipe.managedKey(plugin)));
    now.set(11000);
    registry.refreshAvailability();
    assertNotNull(Bukkit.getRecipe(recipe.managedKey(plugin)));
    recipe.setEnabled(false);
    registry.upsert(recipe);
    limits.resetPermanentGlobal(recipe.id());
    assertNull(Bukkit.getRecipe(recipe.managedKey(plugin)));
  }
}
