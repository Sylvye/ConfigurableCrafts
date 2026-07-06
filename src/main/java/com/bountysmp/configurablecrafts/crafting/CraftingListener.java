package com.bountysmp.configurablecrafts.crafting;

import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import org.bukkit.Keyed;
import org.bukkit.entity.Player;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

public final class CraftingListener implements Listener {
    private final ManagedRecipeRegistry registry;
    private final CraftLimitTracker limitTracker;

    public CraftingListener(ManagedRecipeRegistry registry, CraftLimitTracker limitTracker) {
        this.registry = registry;
        this.limitTracker = limitTracker;
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        validateCraft(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftCommit(CraftItemEvent event) {
        ManagedRecipe recipe = managedRecipe(event.getRecipe());
        if (recipe == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        limitTracker.consume(recipe, player.getUniqueId(), craftCount(event, recipe));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrafterCraft(CrafterCraftEvent event) {
        ManagedRecipe recipe = registry.byManagedKey(event.getRecipe().getKey());
        if (recipe != null && !recipe.allowCrafters()) {
            event.setCancelled(true);
            event.setResult(null);
        }
    }

    private void validateCraft(CraftItemEvent event) {
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
            return;
        }
        int craftCount = craftCount(event, recipe);
        String limitFailure = limitTracker.check(recipe, player.getUniqueId(), craftCount);
        if (limitFailure != null) {
            event.setCancelled(true);
            event.getInventory().setResult(null);
            player.sendMessage(limitFailure);
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

    private int craftCount(CraftItemEvent event, ManagedRecipe recipe) {
        if (!event.getClick().isShiftClick()) {
            return 1;
        }
        ItemStack result = recipe.result();
        if (result == null || result.getType().isAir() || result.getAmount() <= 0) {
            return 0;
        }
        return shiftCraftCount(event.getInventory().getMatrix(), event.getWhoClicked().getInventory(), result);
    }

    static int shiftCraftCount(ItemStack[] matrix, Inventory destination, ItemStack result) {
        if (result == null || result.getType().isAir() || result.getAmount() <= 0) {
            return 0;
        }
        int inputUses = maxInputUses(matrix);
        int inventoryUses = maxInventoryUses(destination, result);
        return Math.max(0, Math.min(inputUses, inventoryUses));
    }

    private static int maxInputUses(ItemStack[] matrix) {
        int uses = Integer.MAX_VALUE;
        for (ItemStack itemStack : matrix) {
            if (itemStack == null || itemStack.getType().isAir()) {
                continue;
            }
            uses = Math.min(uses, itemStack.getAmount());
        }
        return uses == Integer.MAX_VALUE ? 0 : uses;
    }

    private static int maxInventoryUses(Inventory inventory, ItemStack result) {
        int capacity = 0;
        int maxStackSize = Math.min(result.getMaxStackSize(), inventory.getMaxStackSize());
        for (ItemStack itemStack : inventory.getStorageContents()) {
            if (itemStack == null || itemStack.getType().isAir()) {
                capacity += maxStackSize;
            } else if (itemStack.isSimilar(result)) {
                capacity += Math.max(0, maxStackSize - itemStack.getAmount());
            }
        }
        return capacity / result.getAmount();
    }
}
