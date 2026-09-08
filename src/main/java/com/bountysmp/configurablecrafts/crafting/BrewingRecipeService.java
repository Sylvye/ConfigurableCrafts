package com.bountysmp.configurablecrafts.crafting;

import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.RecipeKind;
import com.bountysmp.configurablecrafts.model.WeatherMode;
import java.util.Collection;
import java.util.OptionalInt;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.BrewingStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BrewingStartEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.entity.Player;

public final class BrewingRecipeService implements Listener {
    private static final int[] BOTTLE_SLOTS = {0, 1, 2};

    private final ManagedRecipeRegistry registry;

    public BrewingRecipeService(ManagedRecipeRegistry registry) {
        this.registry = registry;
    }

    public void shutdown() {
    }

    @EventHandler(ignoreCancelled = true)
    public void onBrewingStart(BrewingStartEvent event) {
        if (!(event.getBlock().getState() instanceof BrewingStand brewingStand)) {
            return;
        }
        BlockedRoute blocked = exclusivelyBlockedRoute(brewingStand.getInventory(), event.getSource());
        if (blocked != null) {
            rejectIngredient(brewingStand, blocked);
            return;
        }
        OptionalInt brewTime = matchingBrewTime(registry.recipes(), brewingStand.getInventory(), event.getSource());
        if (brewTime.isEmpty()) {
            return;
        }
        event.setRecipeBrewTime(brewTime.getAsInt());
        event.setBrewingTime(brewTime.getAsInt());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        ItemStack ingredient = event.getContents().getIngredient();
        for (int i = 0; i < BOTTLE_SLOTS.length && i < event.getResults().size(); i++) {
            ItemStack input = event.getContents().getItem(BOTTLE_SLOTS[i]);
            ManagedRecipe replacement = matchingVanillaReplacement(input, ingredient);
            if (replacement != null) {
                VanillaBrewingCatalog.Entry entry = VanillaBrewingCatalog.bySourceKey(replacement.sourceKey());
                if (entry != null && entry.containerConversion()) {
                    ItemStack converted = input.clone();
                    converted.setType(entry.resultMaterial());
                    event.getResults().set(i, converted);
                }
                continue;
            }
            if (blockedOriginal(input, ingredient) != null) {
                event.getResults().set(i, input == null ? null : input.clone());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (!(event.getDestination() instanceof BrewerInventory inventory)) {
            return;
        }
        BrewingContents projected = BrewingContents.from(inventory);
        if (!projected.insertAutomatically(event.getItem())) {
            return;
        }
        if (exclusivelyBlockedRoute(projected) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory() instanceof BrewerInventory inventory)) {
            return;
        }
        BrewingContents projected = BrewingContents.from(inventory);
        if (!projectClick(event, projected)) {
            return;
        }
        BlockedRoute blocked = exclusivelyBlockedRoute(projected);
        if (blocked == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) {
            player.sendActionBar(Component.text(blockedMessage(blocked)));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory() instanceof BrewerInventory inventory)) {
            return;
        }
        BrewingContents projected = BrewingContents.from(inventory);
        boolean changed = false;
        for (var entry : event.getNewItems().entrySet()) {
            int rawSlot = entry.getKey();
            if (rawSlot >= 0 && rawSlot <= 3) {
                projected.set(rawSlot, entry.getValue());
                changed = true;
            }
        }
        if (!changed) {
            return;
        }
        BlockedRoute blocked = exclusivelyBlockedRoute(projected);
        if (blocked != null) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendActionBar(Component.text(blockedMessage(blocked)));
            }
        }
    }

    private boolean projectClick(InventoryClickEvent event, BrewingContents projected) {
        int topSize = event.getView().getTopInventory().getSize();
        boolean topClick = event.getRawSlot() >= 0 && event.getRawSlot() < topSize;
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            return !topClick && projected.insertAutomatically(event.getCurrentItem());
        }
        if (!topClick || event.getRawSlot() > 3) {
            return false;
        }
        ItemStack incoming = switch (event.getAction()) {
            case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR -> event.getCursor();
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> hotbarItem(event);
            default -> null;
        };
        if (incoming == null || incoming.getType().isAir()) {
            return false;
        }
        projected.set(event.getRawSlot(), incoming);
        return true;
    }

    private ItemStack hotbarItem(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return null;
        }
        int button = event.getHotbarButton();
        return button >= 0 ? player.getInventory().getItem(button) : player.getInventory().getItemInOffHand();
    }

    private BlockedRoute exclusivelyBlockedRoute(BrewerInventory inventory, ItemStack ingredient) {
        return exclusivelyBlockedRoute(BrewingContents.from(inventory).withIngredient(ingredient));
    }

    private BlockedRoute exclusivelyBlockedRoute(BrewingContents contents) {
        BlockedRoute found = null;
        boolean anythingCanBrew = false;
        for (int slot : BOTTLE_SLOTS) {
            ItemStack input = contents.bottles[slot];
            if (canBrew(input, contents.ingredient)) {
                anythingCanBrew = true;
            }
            BlockedRoute blocked = blockedOriginal(input, contents.ingredient);
            if (blocked != null) {
                found = blocked;
            }
        }
        return found != null && !anythingCanBrew ? found : null;
    }

    private boolean canBrew(ItemStack input, ItemStack ingredient) {
        if (input == null || ingredient == null) {
            return false;
        }
        for (ManagedRecipe recipe : registry.recipes()) {
            if (!isUsableBrewingRecipe(recipe) || !IngredientMatcher.matches(recipe.ingredient(1), ingredient)) {
                continue;
            }
            boolean inputMatches = VanillaBrewingCatalog.isOverride(recipe)
                ? matchesConfiguredInput(recipe, input)
                : IngredientMatcher.matches(recipe.ingredient(0), input);
            if (inputMatches) {
                return true;
            }
        }
        for (VanillaBrewingCatalog.Entry entry : registry.vanillaBrewingRecipes()) {
            if (!matchesOriginal(entry, input, ingredient)) {
                continue;
            }
            ManagedRecipe override = registry.vanillaBrewingOverride(entry.sourceKey());
            if (override == null) {
                return true;
            }
            return override.enabled() && matchesConfiguredInput(override, input)
                && IngredientMatcher.matches(override.ingredient(1), ingredient);
        }
        return false;
    }

    private ManagedRecipe matchingVanillaReplacement(ItemStack input, ItemStack ingredient) {
        for (ManagedRecipe recipe : registry.recipes()) {
            if (!recipe.enabled() || !VanillaBrewingCatalog.isOverride(recipe)) {
                continue;
            }
            if (matchesConfiguredInput(recipe, input) && IngredientMatcher.matches(recipe.ingredient(1), ingredient)) {
                return recipe;
            }
        }
        return null;
    }

    private BlockedRoute blockedOriginal(ItemStack input, ItemStack ingredient) {
        if (input == null || ingredient == null) {
            return null;
        }
        for (VanillaBrewingCatalog.Entry entry : registry.vanillaBrewingRecipes()) {
            ManagedRecipe override = registry.vanillaBrewingOverride(entry.sourceKey());
            if (override == null || !matchesOriginal(entry, input, ingredient)) {
                continue;
            }
            if (!override.enabled() || !matchesConfiguredInput(override, input)
                || !IngredientMatcher.matches(override.ingredient(1), ingredient)) {
                return new BlockedRoute(entry, override);
            }
        }
        return null;
    }

    private boolean matchesOriginal(VanillaBrewingCatalog.Entry entry, ItemStack input, ItemStack ingredient) {
        if (ingredient.getType() != entry.ingredient()) {
            return false;
        }
        if (entry.containerConversion()) {
            return input.getType() == entry.inputMaterial();
        }
        if (!isPotionContainer(input.getType()) || !(input.getItemMeta() instanceof PotionMeta meta)) {
            return false;
        }
        return meta.getBasePotionType() == entry.inputPotion();
    }

    private boolean matchesConfiguredInput(ManagedRecipe recipe, ItemStack input) {
        return VanillaBrewingCatalog.matchesPotionInput(recipe.ingredient(0), input);
    }

    private boolean isPotionContainer(Material material) {
        return material == Material.POTION || material == Material.SPLASH_POTION || material == Material.LINGERING_POTION;
    }

    private void rejectIngredient(BrewingStand stand, BlockedRoute blocked) {
        BrewerInventory inventory = stand.getInventory();
        ItemStack ingredient = inventory.getIngredient();
        if (ingredient == null || ingredient.getType().isAir()) {
            return;
        }
        ItemStack returned = ingredient.clone();
        returned.setAmount(1);
        if (ingredient.getAmount() == 1) {
            inventory.setIngredient(null);
        } else {
            ingredient.setAmount(ingredient.getAmount() - 1);
            inventory.setIngredient(ingredient);
        }
        stand.setFuelLevel(Math.min(20, stand.getFuelLevel() + 1));
        stand.update(true);
        stand.getWorld().dropItemNaturally(stand.getLocation().add(0.5, 1.0, 0.5), returned);
        String message = blockedMessage(blocked);
        for (var viewer : inventory.getViewers()) {
            viewer.sendActionBar(Component.text(message));
        }
    }

    private String blockedMessage(BlockedRoute blocked) {
        return blocked.override().enabled()
            ? blocked.entry().displayName() + " changed — use "
                + com.bountysmp.configurablecrafts.util.ItemText.displayName(blocked.override().ingredient(1).sample()) + "."
            : blocked.entry().displayName() + " brewing is disabled.";
    }

    private static final class BrewingContents {
        private final ItemStack[] bottles = new ItemStack[3];
        private ItemStack ingredient;

        static BrewingContents from(BrewerInventory inventory) {
            BrewingContents contents = new BrewingContents();
            for (int slot : BOTTLE_SLOTS) {
                contents.bottles[slot] = cloneOrNull(inventory.getItem(slot));
            }
            contents.ingredient = cloneOrNull(inventory.getItem(3));
            return contents;
        }

        BrewingContents withIngredient(ItemStack item) {
            ingredient = cloneOrNull(item);
            return this;
        }

        void set(int slot, ItemStack item) {
            if (slot >= 0 && slot < bottles.length) {
                bottles[slot] = cloneOrNull(item);
            } else if (slot == 3) {
                ingredient = cloneOrNull(item);
            }
        }

        boolean insertAutomatically(ItemStack item) {
            if (item == null || item.getType().isAir()) {
                return false;
            }
            if (isPotion(item)) {
                for (int slot = 0; slot < bottles.length; slot++) {
                    if (bottles[slot] == null) {
                        bottles[slot] = item.clone();
                        return true;
                    }
                }
                return false;
            }
            if (ingredient == null) {
                ingredient = item.clone();
                return true;
            }
            return false;
        }

        private static boolean isPotion(ItemStack item) {
            return item.getType() == Material.POTION || item.getType() == Material.SPLASH_POTION
                || item.getType() == Material.LINGERING_POTION;
        }

        private static ItemStack cloneOrNull(ItemStack item) {
            return item == null || item.getType().isAir() ? null : item.clone();
        }
    }

    private record BlockedRoute(VanillaBrewingCatalog.Entry entry, ManagedRecipe override) {
    }

    static OptionalInt matchingBrewTime(Collection<ManagedRecipe> recipes, BrewerInventory inventory, ItemStack ingredient) {
        int brewTime = -1;
        for (ManagedRecipe recipe : recipes) {
            if (!isUsableBrewingRecipe(recipe) || !IngredientMatcher.matches(recipe.ingredient(1), ingredient)) {
                continue;
            }
            if (!hasMatchingInput(recipe, inventory)) {
                continue;
            }
            if (brewTime < 0) {
                brewTime = recipe.brewTimeTicks();
            } else if (brewTime != recipe.brewTimeTicks()) {
                return OptionalInt.empty();
            }
        }
        return brewTime < 0 ? OptionalInt.empty() : OptionalInt.of(brewTime);
    }

    private static boolean hasMatchingInput(ManagedRecipe recipe, BrewerInventory inventory) {
        for (int slot : BOTTLE_SLOTS) {
            if (IngredientMatcher.matches(recipe.ingredient(0), inventory.getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUsableBrewingRecipe(ManagedRecipe recipe) {
        return recipe.kind() == RecipeKind.BREWING
            && recipe.enabled()
            && !recipe.playerLimit().enabled()
            && !recipe.globalLimit().enabled()
            && recipe.conditions().minimumExperienceLevel() == 0
            && recipe.conditions().dimensions().isEmpty()
            && recipe.conditions().biomes().isEmpty()
            && recipe.conditions().weather() == WeatherMode.ANY;
    }
}
