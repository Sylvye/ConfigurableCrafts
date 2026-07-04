package com.bountysmp.configurablecrafts.crafting;

import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import io.papermc.paper.event.player.PlayerStonecutterRecipeSelectEvent;
import org.bukkit.Keyed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.StonecutterInventory;
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
        if (player == null || ConditionValidator.failureReason(recipe, player) != null) {
            event.setResult(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSmith(SmithItemEvent event) {
        ManagedRecipe recipe = managedRecipe(event.getInventory().getRecipe());
        if (recipe == null) {
            return;
        }
        Player player = event.getWhoClicked() instanceof Player p ? p : null;
        if (player == null) {
            event.setCancelled(true);
            return;
        }
        String failure = ConditionValidator.failureReason(recipe, player);
        if (failure == null) {
            failure = limitTracker.tryConsume(recipe, player.getUniqueId(), 1);
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
        if (failure != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(failure);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onStonecutterTake(InventoryClickEvent event) {
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
        if (failure == null && takesResult(event)) {
            int crafts = event.getClick().isShiftClick() ? maxStonecuttingCrafts(view.getTopInventory(), event.getCurrentItem()) : 1;
            failure = limitTracker.tryConsume(recipe, player.getUniqueId(), crafts);
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
        return event.getClick().isLeftClick()
            || event.getClick().isRightClick()
            || event.getClick() == ClickType.SHIFT_LEFT
            || event.getClick() == ClickType.SHIFT_RIGHT
            || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY;
    }

    private int maxStonecuttingCrafts(StonecutterInventory inventory, ItemStack result) {
        ItemStack input = inventory.getInputItem();
        if (input == null || input.getType().isAir() || result == null || result.getType().isAir()) {
            return 0;
        }
        return input.getAmount();
    }

    private ManagedRecipe managedRecipe(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) {
            return null;
        }
        return registry.byManagedKey(keyed.getKey());
    }
}
