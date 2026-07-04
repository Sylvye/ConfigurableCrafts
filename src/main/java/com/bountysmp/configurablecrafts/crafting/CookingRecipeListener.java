package com.bountysmp.configurablecrafts.crafting;

import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import org.bukkit.Keyed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
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
        if (ConditionValidator.failureReason(recipe, event.getBlock().getLocation()) != null) {
            event.setTotalCookTime(Integer.MAX_VALUE);
        } else {
            event.setTotalCookTime(recipe.cookTimeTicks());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSmelt(FurnaceSmeltEvent event) {
        ManagedRecipe recipe = managedRecipe(event.getRecipe());
        if (recipe == null) {
            return;
        }
        String failure = ConditionValidator.failureReason(recipe, event.getBlock().getLocation());
        if (failure == null) {
            failure = limitTracker.tryConsume(recipe, null, 1);
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
