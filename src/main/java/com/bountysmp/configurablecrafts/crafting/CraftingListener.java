package com.bountysmp.configurablecrafts.crafting;

import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import org.bukkit.Keyed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

public final class CraftingListener implements Listener {
    private final ManagedRecipeRegistry registry;

    public CraftingListener(ManagedRecipeRegistry registry) {
        this.registry = registry;
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ManagedRecipe recipe = managedRecipe(event.getRecipe());
        if (recipe == null) {
            return;
        }
        Player player = event.getView().getPlayer() instanceof Player p ? p : null;
        CraftingInventory inventory = event.getInventory();
        if (player == null || !valid(recipe, player, inventory.getMatrix())) {
            inventory.setResult(null);
            return;
        }
        inventory.setResult(recipe.result());
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        ManagedRecipe recipe = managedRecipe(event.getRecipe());
        if (recipe == null) {
            return;
        }
        Player player = event.getWhoClicked() instanceof Player p ? p : null;
        if (player == null) {
            event.setCancelled(true);
            return;
        }
        String conditionFailure = ConditionValidator.failureReason(recipe, player);
        boolean patternMatches = RecipePattern.matches(recipe, event.getInventory().getMatrix());
        if (conditionFailure != null || !patternMatches) {
            event.setCancelled(true);
            event.getInventory().setResult(null);
            player.sendMessage(conditionFailure == null ? "This recipe does not match the configured ingredients." : conditionFailure);
        }
    }

    private boolean valid(ManagedRecipe recipe, Player player, ItemStack[] matrix) {
        return ConditionValidator.failureReason(recipe, player) == null && RecipePattern.matches(recipe, matrix);
    }

    private ManagedRecipe managedRecipe(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) {
            return null;
        }
        return registry.byManagedKey(keyed.getKey());
    }
}
