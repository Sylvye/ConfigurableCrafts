package com.bountysmp.configurablecrafts.crafting;

import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.IngredientSpec;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.UseRemainder;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.Plugin;

public final class CraftingListener implements Listener {
    private final ManagedRecipeRegistry registry;
    private final CraftLimitTracker limitTracker;
    private final Plugin plugin;

    public CraftingListener(Plugin plugin, ManagedRecipeRegistry registry, CraftLimitTracker limitTracker) {
        this.plugin = plugin;
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
        if (recipe == null || !(event.getWhoClicked() instanceof Player player) || !takesCraftResult(event)) {
            return;
        }
        if (registry.owner(recipe.id()) != null) return;
        int craftCount = craftCount(event, recipe);
        limitTracker.consume(recipe, player.getUniqueId(), craftCount);
        List<RemainderPlacement> placements = configuredRemainders(recipe, event.getInventory().getMatrix(),
            player.getInventory(), craftCount);
        if (!placements.isEmpty()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> applyRemainders(event.getInventory(), player, placements));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrafterCraft(CrafterCraftEvent event) {
        ManagedRecipe recipe = registry.byManagedKey(event.getRecipe().getKey());
        if (recipe != null && !recipe.allowCrafters()) {
            event.setCancelled(true);
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOwnedCraft(CraftItemEvent event) {
        ManagedRecipe recipe = managedRecipe(event.getRecipe());
        if (recipe == null || registry.owner(recipe.id()) == null) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        var policy = registry.owner(recipe.id());
        String failure = policy.failure(player);
        if (failure == null && !valid(recipe, player, event.getInventory().getMatrix())) failure = "This mythic cannot be crafted now.";
        boolean shift = event.isShiftClick();
        boolean pickup = event.getAction() == InventoryAction.PICKUP_ALL || event.getAction() == InventoryAction.PICKUP_HALF
            || event.getAction() == InventoryAction.PICKUP_ONE || event.getAction() == InventoryAction.PICKUP_SOME;
        if (!shift && (!pickup || (event.getCursor() != null && !event.getCursor().getType().isAir()))) failure = "Use an empty cursor or shift-click to craft.";
        if (shift && java.util.Arrays.stream(player.getInventory().getStorageContents()).noneMatch(stack -> stack == null || stack.getType().isAir())) failure = "Make room in your inventory first.";
        if (failure != null) { player.sendMessage(failure); return; }
        var gate = new com.bountysmp.configurablecrafts.api.OwnedCraftEvent(player, recipe.id());
        plugin.getServer().getPluginManager().callEvent(gate);
        if (gate.isCancelled()) return;
        ItemStack output = null;
        try {
            output = policy.create(player);
            String limit = limitTracker.tryConsume(recipe, player.getUniqueId(), 1);
            if (limit != null) { policy.aborted(player, output); player.sendMessage(limit); return; }
        } catch (RuntimeException ex) { if (output != null) policy.aborted(player, output); plugin.getLogger().log(java.util.logging.Level.SEVERE, "Owned craft persistence failed", ex); return; }
        ItemStack[] matrix = event.getInventory().getMatrix();
        IngredientSpec[] matched = RecipePattern.matchingIngredients(recipe, matrix);
        for (int i = 0; i < matrix.length; i++) if (matrix[i] != null && !matrix[i].getType().isAir()) {
            ItemStack remainder = matched[i] != null && matched[i].remainder() != null ? matched[i].remainder() : nativeRemainder(matrix[i]);
            if (matrix[i].getAmount() == 1) matrix[i] = remainder;
            else {
                matrix[i].subtract(1);
                if (remainder != null) addToInventoryOrDrop(remainder, player);
            }
        }
        event.getInventory().setMatrix(matrix);
        event.getInventory().setResult(null);
        if (shift) player.getInventory().addItem(output); else player.setItemOnCursor(output);
        policy.completed(player, output);
        plugin.getServer().getScheduler().runTask(plugin, player::updateInventory);
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
        if (!takesCraftResult(event)) {
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
        int craftCount = registry.owner(recipe.id()) != null ? 1 : craftCount(event, recipe);
        String limitFailure = limitTracker.check(recipe, player.getUniqueId(), craftCount);
        if (limitFailure != null) {
            event.setCancelled(true);
            event.getInventory().setResult(null);
            player.sendMessage(limitFailure);
        }
    }

    private boolean valid(ManagedRecipe recipe, Player player, ItemStack[] matrix) {
        return ConditionValidator.failureReason(recipe, player) == null && RecipePattern.matches(recipe, matrix) && limitTracker.check(recipe, player.getUniqueId(), 1) == null && (registry.owner(recipe.id()) == null || registry.owner(recipe.id()).failure(player) == null);
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

    static List<RemainderPlacement> configuredRemainders(ManagedRecipe recipe, ItemStack[] matrix,
                                                          Inventory playerInventory, int craftCount) {
        List<RemainderPlacement> placements = new ArrayList<>();
        if (craftCount <= 0) {
            return placements;
        }
        IngredientSpec[] matched = RecipePattern.matchingIngredients(recipe, matrix);
        for (int slot = 0; slot < matrix.length; slot++) {
            IngredientSpec spec = matched[slot];
            ItemStack input = matrix[slot];
            if (spec == null || spec.remainder() == null || input == null || input.getType().isAir()) {
                continue;
            }
            ItemStack configured = spec.remainder();
            ItemStack nativeRemainder = nativeRemainder(input);
            if (nativeRemainder != null && configured.isSimilar(nativeRemainder)
                && configured.getAmount() == nativeRemainder.getAmount()) {
                continue;
            }
            int nativeInventoryBefore = nativeRemainder == null ? 0 : countSimilar(playerInventory, nativeRemainder);
            placements.add(new RemainderPlacement(slot, configured, nativeRemainder, nativeInventoryBefore,
                recipe.result(), craftCount, input.clone()));
        }
        return placements;
    }

    private static ItemStack nativeRemainder(ItemStack input) {
        UseRemainder component = input.getData(DataComponentTypes.USE_REMAINDER);
        if (component != null) {
            return component.transformInto().clone();
        }
        Material material = input.getType().getCraftingRemainingItem();
        return material == null ? null : new ItemStack(material);
    }

    static void applyRemainders(CraftingInventory inventory, Player player, List<RemainderPlacement> placements) {
        ItemStack[] matrix = inventory.getMatrix();
        for (RemainderPlacement placement : placements) {
            int remainingCrafts = placement.craftCount();
            if (placement.nativeRemainder() != null) {
                int nativeInSlot = isSimilar(matrix[placement.slot()], placement.nativeRemainder())
                    ? matrix[placement.slot()].getAmount() : 0;
                int expectedResultItems = isSimilar(placement.result(), placement.nativeRemainder())
                    ? placement.result().getAmount() * placement.craftCount() : 0;
                int nativeOverflow = Math.max(0, countSimilar(player.getInventory(), placement.nativeRemainder())
                    - placement.nativeInventoryBefore() - expectedResultItems);
                int expectedNative = placement.nativeRemainder().getAmount() * placement.craftCount();
                if (nativeInSlot + nativeOverflow < expectedNative) {
                    continue;
                }
                ItemStack current = matrix[placement.slot()];
                if (isSimilar(current, placement.nativeRemainder())) {
                    matrix[placement.slot()] = null;
                    placeInSlotOrInventory(matrix, placement.slot(), placement.configuredRemainder(), player);
                    remainingCrafts--;
                }
                int removeCount = Math.min(nativeOverflow,
                    Math.max(0, remainingCrafts * placement.nativeRemainder().getAmount()));
                removeSimilar(player.getInventory(), placement.nativeRemainder(), removeCount);
                remainingCrafts -= removeCount / placement.nativeRemainder().getAmount();
            } else if (!inputWasConsumed(matrix[placement.slot()], placement.inputBefore(), placement.craftCount())) {
                continue;
            }
            if (remainingCrafts > 0 && (matrix[placement.slot()] == null || matrix[placement.slot()].getType().isAir())) {
                matrix[placement.slot()] = placement.configuredRemainder().clone();
                remainingCrafts--;
            }
            for (int i = 0; i < remainingCrafts; i++) {
                addToInventoryOrDrop(placement.configuredRemainder(), player);
            }
        }
        inventory.setMatrix(matrix);
    }

    private static void placeInSlotOrInventory(ItemStack[] matrix, int slot, ItemStack remainder, Player player) {
        ItemStack current = matrix[slot];
        if (current == null || current.getType().isAir()) {
            matrix[slot] = remainder.clone();
            return;
        }
        if (current.isSimilar(remainder) && current.getAmount() + remainder.getAmount() <= current.getMaxStackSize()) {
            current.setAmount(current.getAmount() + remainder.getAmount());
            return;
        }
        addToInventoryOrDrop(remainder, player);
    }

    private static void addToInventoryOrDrop(ItemStack remainder, Player player) {
        player.getInventory().addItem(remainder.clone()).values().forEach(leftover ->
            player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private static int countSimilar(Inventory inventory, ItemStack sample) {
        int count = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (isSimilar(item, sample)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private static void removeSimilar(Inventory inventory, ItemStack sample, int amount) {
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length && amount > 0; slot++) {
            ItemStack item = contents[slot];
            if (!isSimilar(item, sample)) {
                continue;
            }
            int removed = Math.min(amount, item.getAmount());
            item.setAmount(item.getAmount() - removed);
            if (item.getAmount() <= 0) {
                contents[slot] = null;
            }
            amount -= removed;
        }
        inventory.setStorageContents(contents);
    }

    private static boolean isSimilar(ItemStack left, ItemStack right) {
        return left != null && right != null && !left.getType().isAir() && left.isSimilar(right);
    }

    private static boolean inputWasConsumed(ItemStack current, ItemStack before, int craftCount) {
        int expected = before.getAmount() - craftCount;
        if (expected <= 0) {
            return current == null || current.getType().isAir();
        }
        return isSimilar(current, before) && current.getAmount() == expected;
    }

    private static boolean takesCraftResult(CraftItemEvent event) {
        return switch (event.getAction()) {
            case PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE,
                 MOVE_TO_OTHER_INVENTORY, HOTBAR_MOVE_AND_READD, HOTBAR_SWAP,
                 DROP_ALL_SLOT, DROP_ONE_SLOT -> true;
            default -> false;
        };
    }

    record RemainderPlacement(int slot, ItemStack configuredRemainder, ItemStack nativeRemainder,
                              int nativeInventoryBefore, ItemStack result, int craftCount, ItemStack inputBefore) {}

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
