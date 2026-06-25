package com.bountysmp.configurablecrafts.crafting;

import com.bountysmp.configurablecrafts.model.IngredientSpec;
import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.RecipeKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

public final class RecipePattern {
    private RecipePattern() {
    }

    public static boolean matches(ManagedRecipe recipe, ItemStack[] matrix) {
        if (recipe.kind() == RecipeKind.SHAPELESS) {
            return matchesShapeless(recipe, matrix);
        }
        return matchesShaped(recipe, matrix);
    }

    public static String signature(ManagedRecipe recipe) {
        if (recipe.kind() == RecipeKind.SHAPELESS) {
            List<String> tokens = new ArrayList<>();
            for (IngredientSpec spec : recipe.ingredients()) {
                if (spec != null && !spec.isEmpty()) {
                    tokens.add(IngredientMatcher.signatureToken(spec));
                }
            }
            Collections.sort(tokens);
            return "shapeless:" + tokens;
        }
        Bounds bounds = bounds(recipe.ingredients());
        if (bounds.empty()) {
            return "shaped:empty";
        }
        StringBuilder builder = new StringBuilder("shaped:");
        for (int row = bounds.minRow; row <= bounds.maxRow; row++) {
            if (row > bounds.minRow) {
                builder.append('/');
            }
            for (int col = bounds.minCol; col <= bounds.maxCol; col++) {
                if (col > bounds.minCol) {
                    builder.append(',');
                }
                builder.append(IngredientMatcher.signatureToken(recipe.ingredient(row * 3 + col)));
            }
        }
        return builder.toString();
    }

    public static String signature(Recipe recipe) {
        if (recipe instanceof ShapedRecipe shapedRecipe) {
            StringBuilder builder = new StringBuilder("shaped:");
            String[] shape = shapedRecipe.getShape();
            for (int row = 0; row < shape.length; row++) {
                if (row > 0) {
                    builder.append('/');
                }
                String line = shape[row];
                for (int col = 0; col < line.length(); col++) {
                    if (col > 0) {
                        builder.append(',');
                    }
                    builder.append(IngredientMatcher.tokenForChoice(shapedRecipe.getChoiceMap().get(line.charAt(col))));
                }
            }
            return builder.toString();
        }
        if (recipe instanceof ShapelessRecipe shapelessRecipe) {
            List<String> tokens = shapelessRecipe.getChoiceList().stream()
                .map(IngredientMatcher::tokenForChoice)
                .sorted()
                .toList();
            return "shapeless:" + tokens;
        }
        return "";
    }

    private static boolean matchesShaped(ManagedRecipe recipe, ItemStack[] matrix) {
        Bounds recipeBounds = bounds(recipe.ingredients());
        Bounds inputBounds = bounds(matrix);
        if (recipeBounds.empty() || inputBounds.empty()) {
            return false;
        }
        if (recipeBounds.height() != inputBounds.height() || recipeBounds.width() != inputBounds.width()) {
            return false;
        }
        for (int rowOffset = 0; rowOffset < recipeBounds.height(); rowOffset++) {
            for (int colOffset = 0; colOffset < recipeBounds.width(); colOffset++) {
                IngredientSpec spec = recipe.ingredient((recipeBounds.minRow + rowOffset) * 3 + recipeBounds.minCol + colOffset);
                ItemStack input = matrix[(inputBounds.minRow + rowOffset) * 3 + inputBounds.minCol + colOffset];
                if (!IngredientMatcher.matches(spec, input)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean matchesShapeless(ManagedRecipe recipe, ItemStack[] matrix) {
        List<IngredientSpec> specs = new ArrayList<>();
        List<ItemStack> inputs = new ArrayList<>();
        for (IngredientSpec spec : recipe.ingredients()) {
            if (spec != null && !spec.isEmpty()) {
                specs.add(spec);
            }
        }
        for (ItemStack itemStack : matrix) {
            if (itemStack != null && !itemStack.getType().isAir()) {
                inputs.add(itemStack);
            }
        }
        if (specs.size() != inputs.size()) {
            return false;
        }
        boolean[] used = new boolean[inputs.size()];
        return backtrack(specs, inputs, used, 0);
    }

    private static boolean backtrack(List<IngredientSpec> specs, List<ItemStack> inputs, boolean[] used, int index) {
        if (index == specs.size()) {
            return true;
        }
        IngredientSpec spec = specs.get(index);
        for (int i = 0; i < inputs.size(); i++) {
            if (!used[i] && IngredientMatcher.matches(spec, inputs.get(i))) {
                used[i] = true;
                if (backtrack(specs, inputs, used, index + 1)) {
                    return true;
                }
                used[i] = false;
            }
        }
        return false;
    }

    private static Bounds bounds(IngredientSpec[] specs) {
        Bounds bounds = new Bounds();
        for (int i = 0; i < specs.length; i++) {
            IngredientSpec spec = specs[i];
            if (spec != null && !spec.isEmpty()) {
                bounds.include(i / 3, i % 3);
            }
        }
        return bounds;
    }

    private static Bounds bounds(ItemStack[] matrix) {
        Bounds bounds = new Bounds();
        for (int i = 0; i < Math.min(9, matrix.length); i++) {
            ItemStack itemStack = matrix[i];
            if (itemStack != null && !itemStack.getType().isAir()) {
                bounds.include(i / 3, i % 3);
            }
        }
        return bounds;
    }

    private static final class Bounds {
        private int minRow = 3;
        private int minCol = 3;
        private int maxRow = -1;
        private int maxCol = -1;

        private void include(int row, int col) {
            minRow = Math.min(minRow, row);
            minCol = Math.min(minCol, col);
            maxRow = Math.max(maxRow, row);
            maxCol = Math.max(maxCol, col);
        }

        private boolean empty() {
            return maxRow < minRow || maxCol < minCol;
        }

        private int height() {
            return maxRow - minRow + 1;
        }

        private int width() {
            return maxCol - minCol + 1;
        }
    }
}
