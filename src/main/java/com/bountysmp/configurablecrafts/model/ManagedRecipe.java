package com.bountysmp.configurablecrafts.model;

import java.util.Locale;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class ManagedRecipe {
    private String id;
    private String sourceKey;
    private RecipeKind kind;
    private boolean enabled = true;
    private ItemStack result;
    private final IngredientSpec[] ingredients = new IngredientSpec[9];
    private RecipeConditions conditions = new RecipeConditions();

    public ManagedRecipe(String id, RecipeKind kind) {
        this.id = sanitizeId(id == null || id.isBlank() ? "recipe_" + UUID.randomUUID() : id);
        this.kind = kind == null ? RecipeKind.SHAPED : kind;
    }

    public static ManagedRecipe createCustom(RecipeKind kind) {
        return new ManagedRecipe("recipe_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8), kind);
    }

    public String id() {
        return id;
    }

    public void setId(String id) {
        this.id = sanitizeId(id);
    }

    public String sourceKey() {
        return sourceKey;
    }

    public void setSourceKey(String sourceKey) {
        this.sourceKey = sourceKey == null || sourceKey.isBlank() ? null : sourceKey.toLowerCase(Locale.ROOT);
    }

    public RecipeKind kind() {
        return kind;
    }

    public void setKind(RecipeKind kind) {
        this.kind = kind == null ? RecipeKind.SHAPED : kind;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ItemStack result() {
        return result == null ? null : result.clone();
    }

    public void setResult(ItemStack result) {
        this.result = result == null ? null : result.clone();
    }

    public IngredientSpec[] ingredients() {
        return ingredients;
    }

    public IngredientSpec ingredient(int index) {
        return ingredients[index];
    }

    public void setIngredient(int index, IngredientSpec ingredient) {
        ingredients[index] = ingredient == null || ingredient.isEmpty() ? null : ingredient.copy();
    }

    public RecipeConditions conditions() {
        return conditions;
    }

    public void setConditions(RecipeConditions conditions) {
        this.conditions = conditions == null ? new RecipeConditions() : conditions.copy();
    }

    public boolean isOverride() {
        return sourceKey != null;
    }

    public NamespacedKey managedKey(Plugin plugin) {
        return new NamespacedKey(plugin, "recipe/" + id);
    }

    public String displayLabel() {
        ItemStack stack = result();
        String type = stack == null ? "Unfinished" : stack.getType().name().toLowerCase(Locale.ROOT);
        return sourceKey == null ? id + " -> " + type : sourceKey + " -> " + type;
    }

    public ManagedRecipe copy() {
        ManagedRecipe copy = new ManagedRecipe(id, kind);
        copy.sourceKey = sourceKey;
        copy.enabled = enabled;
        copy.setResult(result);
        for (int i = 0; i < ingredients.length; i++) {
            copy.setIngredient(i, ingredients[i]);
        }
        copy.conditions = conditions.copy();
        return copy;
    }

    public static String sanitizeId(String input) {
        String sanitized = (input == null ? "recipe" : input.toLowerCase(Locale.ROOT))
            .replace(':', '_')
            .replace('/', '_')
            .replaceAll("[^a-z0-9._-]", "_")
            .replaceAll("_+", "_");
        return sanitized.isBlank() ? "recipe" : sanitized;
    }
}
