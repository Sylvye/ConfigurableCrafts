package com.bountysmp.configurablecrafts.crafting;

import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.RecipeKind;
import com.bountysmp.configurablecrafts.model.WeatherMode;
import java.util.Collection;
import java.util.OptionalInt;
import org.bukkit.block.BrewingStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BrewingStartEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;

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
        OptionalInt brewTime = matchingBrewTime(registry.recipes(), brewingStand.getInventory(), event.getSource());
        if (brewTime.isEmpty()) {
            return;
        }
        event.setRecipeBrewTime(brewTime.getAsInt());
        event.setBrewingTime(brewTime.getAsInt());
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
