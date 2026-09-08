package com.bountysmp.configurablecrafts.crafting;

import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import io.papermc.paper.event.player.PlayerStonecutterRecipeSelectEvent;
import org.bukkit.Keyed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.StonecutterInventory;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.view.StonecutterView;

public final class WorkstationUseListener implements Listener {
    private final ManagedRecipeRegistry registry;
    private final CraftLimitTracker limitTracker;

    public WorkstationUseListener(ManagedRecipeRegistry registry, CraftLimitTracker limitTracker) {
        this.registry = registry;
        this.limitTracker = limitTracker;
    }

    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        ManagedRecipe recipe = managedRecipe(event.getInventory().getRecipe());
        if (recipe == null) {
            return;
        }
        Player player = event.getView().getPlayer() instanceof Player p ? p : null;
        if (player == null || ConditionValidator.failureReason(recipe, player) != null
            || !matchesSmithingInputs(recipe, event.getInventory())) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSmith(SmithItemEvent event) {
        validateSmith(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmithCommit(SmithItemEvent event) {
        ManagedRecipe recipe = managedRecipe(event.getInventory().getRecipe());
        if (recipe == null || !(event.getWhoClicked() instanceof Player player) || !takesResult(event)) {
            return;
        }
        limitTracker.consume(recipe, player.getUniqueId(), 1);
    }

    private void validateSmith(SmithItemEvent event) {
        ManagedRecipe recipe = managedRecipe(event.getInventory().getRecipe());
        if (recipe == null) {
            return;
        }
        Player player = event.getWhoClicked() instanceof Player p ? p : null;
        if (player == null) {
            event.setCancelled(true);
            return;
        }
        if (!takesResult(event)) {
            event.setCancelled(true);
            return;
        }
        String failure = ConditionValidator.failureReason(recipe, player);
        if (failure == null && !matchesSmithingInputs(recipe, event.getInventory())) {
            failure = "This recipe does not match the configured ingredients.";
        }
        if (failure == null) {
            failure = limitTracker.check(recipe, player.getUniqueId(), 1);
        }
        if (failure != null) {
            event.setCancelled(true);
            event.getInventory().setResult(null);
            player.sendMessage(failure);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onStonecutterSelect(PlayerStonecutterRecipeSelectEvent event) {
        ManagedRecipe recipe = managedRecipe(event.getStonecuttingRecipe());
        if (recipe == null) {
            return;
        }
        String failure = ConditionValidator.failureReason(recipe, event.getPlayer());
        if (failure == null && !IngredientMatcher.matches(recipe.ingredient(0),
            event.getStonecutterInventory().getInputItem())) {
            failure = "This recipe does not match the configured ingredient.";
        }
        if (failure != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(failure);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStonecutterTake(InventoryClickEvent event) {
        validateStonecutterTake(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStonecutterTakeCommit(InventoryClickEvent event) {
        if (event.getView().getType() != InventoryType.STONECUTTER || event.getRawSlot() != 1 || !takesResult(event)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView() instanceof StonecutterView view)) {
            return;
        }
        ManagedRecipe recipe = managedRecipe(selectedStonecutterRecipe(view));
        if (recipe == null) {
            return;
        }
        int crafts = event.getClick().isShiftClick()
            ? maxStonecuttingCrafts(view.getTopInventory(), player, event.getCurrentItem()) : 1;
        limitTracker.consume(recipe, player.getUniqueId(), crafts);
    }

    private void validateStonecutterTake(InventoryClickEvent event) {
        if (event.getView().getType() != InventoryType.STONECUTTER || event.getRawSlot() != 1) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView() instanceof StonecutterView view)) {
            return;
        }
        Recipe bukkitRecipe = selectedStonecutterRecipe(view);
        ManagedRecipe recipe = managedRecipe(bukkitRecipe);
        if (recipe == null) {
            return;
        }
        String failure = ConditionValidator.failureReason(recipe, player);
        if (failure == null && !IngredientMatcher.matches(recipe.ingredient(0), view.getTopInventory().getInputItem())) {
            failure = "This recipe does not match the configured ingredient.";
        }
        if (failure == null && takesResult(event)) {
            int crafts = event.getClick().isShiftClick()
                ? maxStonecuttingCrafts(view.getTopInventory(), player, event.getCurrentItem()) : 1;
            failure = limitTracker.check(recipe, player.getUniqueId(), crafts);
        }
        if (failure != null) {
            event.setCancelled(true);
            player.sendMessage(failure);
        }
    }

    private Recipe selectedStonecutterRecipe(StonecutterView view) {
        int index = view.getSelectedRecipeIndex();
        return index >= 0 && index < view.getRecipes().size() ? view.getRecipes().get(index) : null;
    }

    private boolean takesResult(InventoryClickEvent event) {
        return switch (event.getAction()) {
            case PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE,
                 MOVE_TO_OTHER_INVENTORY, HOTBAR_MOVE_AND_READD, HOTBAR_SWAP,
                 DROP_ALL_SLOT, DROP_ONE_SLOT -> true;
            default -> false;
        };
    }

    private int maxStonecuttingCrafts(StonecutterInventory inventory, Player player, ItemStack result) {
        ItemStack input = inventory.getInputItem();
        if (input == null || input.getType().isAir() || result == null || result.getType().isAir()) {
            return 0;
        }
        int capacity = 0;
        int maxStack = Math.min(result.getMaxStackSize(), player.getInventory().getMaxStackSize());
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                capacity += maxStack;
            } else if (item.isSimilar(result)) {
                capacity += Math.max(0, maxStack - item.getAmount());
            }
        }
        return Math.min(input.getAmount(), capacity / result.getAmount());
    }

    private boolean matchesSmithingInputs(ManagedRecipe recipe, SmithingInventory inventory) {
        return IngredientMatcher.matches(recipe.ingredient(0), inventory.getInputTemplate())
            && IngredientMatcher.matches(recipe.ingredient(1), inventory.getInputEquipment())
            && IngredientMatcher.matches(recipe.ingredient(2), inventory.getInputMineral());
    }

    private ManagedRecipe managedRecipe(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) {
            return null;
        }
        return registry.byManagedKey(keyed.getKey());
    }
}
