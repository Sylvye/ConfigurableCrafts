package com.bountysmp.configurablecrafts.gui;

import com.bountysmp.configurablecrafts.model.IngredientSpec;
import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.MatcherType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class EditorSession {
    private final ManagedRecipe recipe;
    private final ItemStack[] ingredients = new ItemStack[9];
    private final boolean[] ownedIngredients = new boolean[9];
    private ItemStack result;
    private boolean ownedResult;
    private int selectedSlot = -1;
    private boolean readOnly;
    private boolean suspended;
    private boolean blink;
    private int tagCycle;

    EditorSession(ManagedRecipe recipe, boolean readOnly) {
        this.recipe = recipe.copy();
        this.readOnly = readOnly;
        for (int i = 0; i < 9; i++) {
            IngredientSpec spec = this.recipe.ingredient(i);
            if (spec != null) {
                ingredients[i] = spec.sample();
            }
        }
        result = this.recipe.result();
    }

    ManagedRecipe recipe() {
        return recipe;
    }

    ItemStack ingredient(int index) {
        return ingredients[index] == null ? null : ingredients[index].clone();
    }

    void setIngredient(int index, ItemStack itemStack, boolean owned) {
        ingredients[index] = itemStack == null ? null : itemStack.clone();
        ownedIngredients[index] = !GuiUtil.isEmpty(itemStack) && owned;
        if (GuiUtil.isEmpty(itemStack)) {
            recipe.setIngredient(index, null);
        } else {
            recipe.setIngredient(index, IngredientSpec.fromExactSample(itemStack));
        }
        if (GuiUtil.isEmpty(itemStack) && selectedSlot == index) {
            selectedSlot = -1;
        }
    }

    IngredientSpec ingredientSpec(int index) {
        IngredientSpec spec = recipe.ingredient(index);
        return spec == null ? null : spec.copy();
    }

    boolean ownsIngredient(int index) {
        return ownedIngredients[index];
    }

    ItemStack result() {
        return result == null ? null : result.clone();
    }

    void setResult(ItemStack result, boolean owned) {
        this.result = result == null ? null : result.clone();
        this.ownedResult = !GuiUtil.isEmpty(result) && owned;
    }

    boolean ownsResult() {
        return ownedResult;
    }

    int selectedSlot() {
        return selectedSlot;
    }

    void setSelectedSlot(int selectedSlot) {
        this.selectedSlot = selectedSlot;
    }

    boolean readOnly() {
        return readOnly;
    }

    boolean suspended() {
        return suspended;
    }

    void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }

    boolean flipBlink() {
        blink = !blink;
        return blink;
    }

    int advanceTagCycle() {
        return tagCycle++;
    }

    void applyItemsToRecipe() {
        for (int i = 0; i < 9; i++) {
            ItemStack item = ingredients[i];
            IngredientSpec previous = recipe.ingredient(i);
            if (GuiUtil.isEmpty(item)) {
                recipe.setIngredient(i, previous != null && previous.isTagOnly() ? previous : null);
                continue;
            }
            IngredientSpec spec = previous == null || previous.isTagOnly() ? IngredientSpec.fromExactSample(item) : previous.copy();
            spec.setSample(item);
            if (spec.matchers().isEmpty()) {
                spec.setMatcher(MatcherType.EXACT, true);
            }
            recipe.setIngredient(i, spec);
        }
        recipe.setResult(result);
    }

    void releaseOwnedItems(Player player) {
        for (int i = 0; i < 9; i++) {
            if (ownedIngredients[i] && !GuiUtil.isEmpty(ingredients[i])) {
                give(player, ingredients[i]);
            }
            ownedIngredients[i] = false;
        }
        if (ownedResult && !GuiUtil.isEmpty(result)) {
            give(player, result);
        }
        ownedResult = false;
    }

    private void give(Player player, ItemStack itemStack) {
        player.getInventory().addItem(itemStack.clone()).values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }
}
