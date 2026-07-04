package com.bountysmp.configurablecrafts.crafting;

import com.bountysmp.configurablecrafts.model.IngredientSpec;
import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.RecipeKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewingStandFuelEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class BrewingRecipeService implements Listener {
    private static final int[] BOTTLE_SLOTS = {0, 1, 2};

    private final Plugin plugin;
    private final ManagedRecipeRegistry registry;
    private final CraftLimitTracker limitTracker;
    private final Map<String, ActiveBrew> activeBrews = new HashMap<>();
    private final Set<String> pendingEvaluations = new HashSet<>();
    private BukkitTask tickTask;

    public BrewingRecipeService(Plugin plugin, ManagedRecipeRegistry registry, CraftLimitTracker limitTracker) {
        this.plugin = plugin;
        this.registry = registry;
        this.limitTracker = limitTracker;
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        activeBrews.clear();
        pendingEvaluations.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory() instanceof BrewerInventory brewerInventory) {
            scheduleEvaluate(brewerInventory);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory() instanceof BrewerInventory brewerInventory) {
            scheduleEvaluate(brewerInventory);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        scheduleIfBrewer(event.getSource());
        scheduleIfBrewer(event.getDestination());
    }

    @EventHandler(ignoreCancelled = true)
    public void onFuel(BrewingStandFuelEvent event) {
        if (event.getBlock().getState() instanceof BrewingStand brewingStand) {
            Bukkit.getScheduler().runTask(plugin, () -> scheduleEvaluate(brewingStand.getInventory()));
        }
    }

    private void scheduleIfBrewer(Inventory inventory) {
        if (inventory instanceof BrewerInventory brewerInventory) {
            scheduleEvaluate(brewerInventory);
        }
    }

    private void scheduleEvaluate(BrewerInventory inventory) {
        BrewingStand stand = inventory.getHolder();
        if (stand == null) {
            return;
        }
        String key = locationKey(stand.getLocation());
        if (!pendingEvaluations.add(key)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingEvaluations.remove(key);
            evaluate(stand.getLocation());
        });
    }

    private void evaluate(Location location) {
        String key = locationKey(location);
        if (activeBrews.containsKey(key)) {
            return;
        }
        BrewingStand stand = currentStand(location);
        if (stand == null || !hasFuel(stand)) {
            resetProgress(stand);
            return;
        }
        BrewerInventory inventory = stand.getInventory();
        ItemStack ingredient = inventory.getIngredient();
        if (isEmpty(ingredient)) {
            resetProgress(stand);
            return;
        }
        Match match = findMatch(stand, inventory, ingredient);
        if (match == null) {
            return;
        }
        int[] slots = match.slots().stream().mapToInt(Integer::intValue).toArray();
        activeBrews.put(key, new ActiveBrew(
            location.clone(),
            match.recipe().id(),
            match.recipe().brewTimeTicks(),
            match.recipe().brewTimeTicks(),
            ingredient,
            bottleSnapshots(inventory, slots),
            slots
        ));
        stand.setRecipeBrewTime(match.recipe().brewTimeTicks());
        stand.setBrewingTime(match.recipe().brewTimeTicks());
        stand.update();
        ensureTicker();
    }

    private Match findMatch(BrewingStand stand, BrewerInventory inventory, ItemStack ingredient) {
        for (ManagedRecipe recipe : registry.recipes()) {
            if (recipe.kind() != RecipeKind.BREWING || !recipe.enabled()) {
                continue;
            }
            if (ConditionValidator.failureReason(recipe, stand.getLocation()) != null) {
                continue;
            }
            IngredientSpec ingredientSpec = recipe.ingredient(1);
            if (!IngredientMatcher.matches(ingredientSpec, ingredient)) {
                continue;
            }
            List<Integer> slots = matchingBottleSlots(recipe, inventory);
            if (!slots.isEmpty()) {
                return new Match(recipe, slots);
            }
        }
        return null;
    }

    static List<Integer> matchingBottleSlots(ManagedRecipe recipe, BrewerInventory inventory) {
        List<Integer> slots = new ArrayList<>();
        IngredientSpec bottleSpec = recipe.ingredient(0);
        for (int slot : BOTTLE_SLOTS) {
            if (IngredientMatcher.matches(bottleSpec, inventory.getItem(slot))) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private ItemStack[] bottleSnapshots(BrewerInventory inventory, int[] slots) {
        ItemStack[] snapshots = new ItemStack[slots.length];
        for (int i = 0; i < slots.length; i++) {
            ItemStack itemStack = inventory.getItem(slots[i]);
            snapshots[i] = itemStack == null ? null : itemStack.clone();
        }
        return snapshots;
    }

    private void ensureTicker() {
        if (tickTask != null) {
            return;
        }
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        List<String> finished = new ArrayList<>();
        for (Map.Entry<String, ActiveBrew> entry : activeBrews.entrySet()) {
            ActiveBrew brew = entry.getValue();
            BrewingStand stand = currentStand(brew.location());
            if (stand == null || !stillValid(brew, stand)) {
                resetProgress(stand);
                finished.add(entry.getKey());
                continue;
            }
            brew.remainingTicks--;
            stand.setRecipeBrewTime(brew.totalTicks());
            stand.setBrewingTime(Math.max(0, brew.remainingTicks()));
            stand.update();
            if (brew.remainingTicks() <= 0) {
                complete(brew, stand);
                finished.add(entry.getKey());
            }
        }
        for (String key : finished) {
            activeBrews.remove(key);
        }
        if (activeBrews.isEmpty() && tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    private boolean stillValid(ActiveBrew brew, BrewingStand stand) {
        BrewerInventory inventory = stand.getInventory();
        if (!hasFuel(stand) || !sameStack(brew.ingredient(), inventory.getIngredient())) {
            return false;
        }
        for (int i = 0; i < brew.bottleSlots().length; i++) {
            if (!sameStack(brew.bottleSnapshots()[i], inventory.getItem(brew.bottleSlots()[i]))) {
                return false;
            }
        }
        return true;
    }

    private void complete(ActiveBrew brew, BrewingStand stand) {
        ManagedRecipe recipe = registry.byId(brew.recipeId());
        if (recipe == null || !stillValid(brew, stand)) {
            resetProgress(stand);
            return;
        }
        String limitFailure = limitTracker.tryConsume(recipe, null, brew.bottleSlots().length);
        if (limitFailure != null) {
            resetProgress(stand);
            return;
        }
        BrewerInventory inventory = stand.getInventory();
        consumeOne(inventory.getIngredient(), inventory::setIngredient);
        consumeFuel(stand, inventory);
        ItemStack result = recipe.result();
        for (int slot : brew.bottleSlots()) {
            inventory.setItem(slot, result == null ? null : result.clone());
        }
        resetProgress(stand);
        stand.update();
        scheduleEvaluate(inventory);
    }

    private void consumeFuel(BrewingStand stand, BrewerInventory inventory) {
        if (stand.getFuelLevel() > 0) {
            stand.setFuelLevel(stand.getFuelLevel() - 1);
            return;
        }
        consumeOne(inventory.getFuel(), inventory::setFuel);
    }

    private void consumeOne(ItemStack itemStack, java.util.function.Consumer<ItemStack> setter) {
        if (isEmpty(itemStack)) {
            return;
        }
        ItemStack updated = itemStack.clone();
        updated.setAmount(updated.getAmount() - 1);
        setter.accept(updated.getAmount() <= 0 ? null : updated);
    }

    private boolean hasFuel(BrewingStand stand) {
        return stand.getFuelLevel() > 0 || !isEmpty(stand.getInventory().getFuel());
    }

    private void resetProgress(BrewingStand stand) {
        if (stand == null) {
            return;
        }
        stand.setBrewingTime(0);
        stand.setRecipeBrewTime(ManagedRecipe.DEFAULT_BREW_TIME_TICKS);
        stand.update();
    }

    private BrewingStand currentStand(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return null;
        }
        Block block = location.getBlock();
        return block.getState() instanceof BrewingStand brewingStand ? brewingStand : null;
    }

    private boolean sameStack(ItemStack expected, ItemStack actual) {
        if (isEmpty(expected) || isEmpty(actual)) {
            return isEmpty(expected) && isEmpty(actual);
        }
        return expected.getAmount() == actual.getAmount() && expected.isSimilar(actual);
    }

    private boolean isEmpty(ItemStack itemStack) {
        return itemStack == null || itemStack.getType().isAir();
    }

    private String locationKey(Location location) {
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private record Match(ManagedRecipe recipe, List<Integer> slots) {
    }

    private static final class ActiveBrew {
        private final Location location;
        private final String recipeId;
        private final int totalTicks;
        private int remainingTicks;
        private final ItemStack ingredient;
        private final ItemStack[] bottleSnapshots;
        private final int[] bottleSlots;

        private ActiveBrew(Location location, String recipeId, int totalTicks, int remainingTicks, ItemStack ingredient, ItemStack[] bottleSnapshots, int[] bottleSlots) {
            this.location = location;
            this.recipeId = recipeId;
            this.totalTicks = totalTicks;
            this.remainingTicks = remainingTicks;
            this.ingredient = ingredient == null ? null : ingredient.clone();
            this.bottleSnapshots = bottleSnapshots;
            this.bottleSlots = bottleSlots;
        }

        private Location location() {
            return location;
        }

        private String recipeId() {
            return recipeId;
        }

        private int totalTicks() {
            return totalTicks;
        }

        private int remainingTicks() {
            return remainingTicks;
        }

        private ItemStack ingredient() {
            return ingredient == null ? null : ingredient.clone();
        }

        private ItemStack[] bottleSnapshots() {
            return bottleSnapshots;
        }

        private int[] bottleSlots() {
            return bottleSlots;
        }
    }
}
