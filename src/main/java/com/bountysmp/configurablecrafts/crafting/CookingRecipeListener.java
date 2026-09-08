package com.bountysmp.configurablecrafts.crafting;

import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import org.bukkit.Keyed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.block.CampfireStartEvent;
import org.bukkit.inventory.Recipe;

public final class CookingRecipeListener implements Listener {
    private final ManagedRecipeRegistry registry;
    private final CraftLimitTracker limitTracker;

    public CookingRecipeListener(ManagedRecipeRegistry registry, CraftLimitTracker limitTracker) {
        this.registry = registry;
        this.limitTracker = limitTracker;
    }

    @EventHandler(ignoreCancelled = true)
    public void onStartSmelt(FurnaceStartSmeltEvent event) {
        ManagedRecipe recipe = managedRecipe(event.getRecipe());
        if (recipe == null) {
            return;
        }
        if (ConditionValidator.failureReason(recipe, event.getBlock().getLocation()) != null
            || !IngredientMatcher.matches(recipe.ingredient(0), event.getSource())) {
            event.setTotalCookTime(Integer.MAX_VALUE);
        } else {
            event.setTotalCookTime(recipe.cookTimeTicks());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onStartCampfire(CampfireStartEvent event) {
        ManagedRecipe recipe = managedRecipe(event.getRecipe());
        if (recipe == null) {
            return;
        }
        if (ConditionValidator.failureReason(recipe, event.getBlock().getLocation()) != null
            || !IngredientMatcher.matches(recipe.ingredient(0), event.getSource())) {
            event.setTotalCookTime(Integer.MAX_VALUE);
        } else {
            event.setTotalCookTime(recipe.cookTimeTicks());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSmelt(BlockCookEvent event) {
        validateCook(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmeltCommit(BlockCookEvent event) {
        ManagedRecipe recipe = managedRecipe(event.getRecipe());
        if (recipe != null) {
            limitTracker.consume(recipe, null, 1);
        }
    }

    private void validateCook(BlockCookEvent event) {
        ManagedRecipe recipe = managedRecipe(event.getRecipe());
        if (recipe == null) {
            return;
        }
        String failure = ConditionValidator.failureReason(recipe, event.getBlock().getLocation());
        if (failure == null && !IngredientMatcher.matches(recipe.ingredient(0), event.getSource())) {
            failure = "This recipe does not match the configured ingredient.";
        }
        if (failure == null) {
            failure = limitTracker.check(recipe, null, 1);
        }
        if (failure != null) {
            event.setCancelled(true);
        }
    }

    private ManagedRecipe managedRecipe(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) {
            return null;
        }
        return registry.byManagedKey(keyed.getKey());
    }
}
