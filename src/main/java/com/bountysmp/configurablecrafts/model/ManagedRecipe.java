package com.bountysmp.configurablecrafts.model;

import java.util.Locale;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class ManagedRecipe {
    public static final int DEFAULT_COOK_TIME_TICKS = 200;
    public static final int DEFAULT_BREW_TIME_TICKS = 400;

    private String id;
    private String sourceKey;
    private RecipeKind kind;
    private boolean enabled = true;
    private ItemStack result;
    private final IngredientSpec[] ingredients = new IngredientSpec[9];
    private RecipeConditions conditions = new RecipeConditions();
    private RecipeLimit playerLimit = new RecipeLimit();
    private RecipeLimit globalLimit = new RecipeLimit();
    private float experience;
    private int cookTimeTicks = DEFAULT_COOK_TIME_TICKS;
    private int brewTimeTicks = DEFAULT_BREW_TIME_TICKS;
    private boolean copyDataComponents = true;
    private boolean allowCrafters;

    public ManagedRecipe(String id, RecipeKind kind) {
        this.id = sanitizeId(id == null || id.isBlank() ? "recipe_" + UUID.randomUUID() : id);
        setKind(kind);
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
        this.kind = kind == null ? RecipeKind.SHAPED : kind.canonical();
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

    public RecipeLimit playerLimit() {
        return playerLimit;
    }

    public void setPlayerLimit(RecipeLimit playerLimit) {
        this.playerLimit = playerLimit == null ? new RecipeLimit() : playerLimit.copy();
    }

    public RecipeLimit globalLimit() {
        return globalLimit;
    }

    public void setGlobalLimit(RecipeLimit globalLimit) {
        this.globalLimit = globalLimit == null ? new RecipeLimit() : globalLimit.copy();
    }

    public float experience() {
        return experience;
    }

    public void setExperience(float experience) {
        this.experience = Math.max(0.0F, experience);
    }

    public int cookTimeTicks() {
        return cookTimeTicks;
    }

    public void setCookTimeTicks(int cookTimeTicks) {
        this.cookTimeTicks = Math.max(1, cookTimeTicks);
    }

    public int brewTimeTicks() {
        return brewTimeTicks;
    }

    public void setBrewTimeTicks(int brewTimeTicks) {
        this.brewTimeTicks = Math.max(1, brewTimeTicks);
    }

    public boolean copyDataComponents() {
        return copyDataComponents;
    }

    public void setCopyDataComponents(boolean copyDataComponents) {
        this.copyDataComponents = copyDataComponents;
    }

    public boolean allowCrafters() {
        return allowCrafters;
    }

    public void setAllowCrafters(boolean allowCrafters) {
        this.allowCrafters = allowCrafters;
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
        copy.playerLimit = playerLimit.copy();
        copy.globalLimit = globalLimit.copy();
        copy.experience = experience;
        copy.cookTimeTicks = cookTimeTicks;
        copy.brewTimeTicks = brewTimeTicks;
        copy.copyDataComponents = copyDataComponents;
        copy.allowCrafters = allowCrafters;
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
