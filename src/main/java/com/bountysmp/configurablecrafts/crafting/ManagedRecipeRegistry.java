package com.bountysmp.configurablecrafts.crafting;

import com.bountysmp.configurablecrafts.model.IngredientSpec;
import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.RecipeKind;
import com.bountysmp.configurablecrafts.storage.RecipeRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;

public final class ManagedRecipeRegistry {
    private static final char[] INGREDIENT_KEYS = "ABCDEFGHI".toCharArray();

    private final Plugin plugin;
    private final RecipeRepository repository;
    private final Map<String, ManagedRecipe> recipes = new LinkedHashMap<>();
    private final Map<NamespacedKey, Recipe> vanillaRecipes = new LinkedHashMap<>();
    private final Map<NamespacedKey, String> managedKeys = new HashMap<>();

    public ManagedRecipeRegistry(Plugin plugin, RecipeRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public void cacheVanillaRecipes() {
        vanillaRecipes.clear();
        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (!(recipe instanceof Keyed keyed)) {
                continue;
            }
            NamespacedKey key = keyed.getKey();
            if (!NamespacedKey.MINECRAFT.equals(key.getNamespace())) {
                continue;
            }
            if (recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe) {
                vanillaRecipes.put(key, recipe);
            }
        }
    }

    public void load() {
        recipes.clear();
        for (ManagedRecipe recipe : repository.load()) {
            recipes.put(recipe.id(), recipe);
        }
    }

    public void save() {
        repository.save(recipes.values());
    }

    public void applyAll() {
        for (ManagedRecipe recipe : recipes.values()) {
            apply(recipe);
        }
        refreshPlayers();
    }

    public void shutdown() {
        for (ManagedRecipe recipe : new ArrayList<>(recipes.values())) {
            unregisterManaged(recipe);
            restoreSource(recipe);
        }
        refreshPlayers();
    }

    public Collection<ManagedRecipe> recipes() {
        return recipes.values();
    }

    public List<ManagedRecipe> sortedRecipes() {
        return recipes.values().stream()
            .sorted(Comparator.comparing(ManagedRecipe::displayLabel))
            .toList();
    }

    public ManagedRecipe byId(String id) {
        return recipes.get(id);
    }

    public ManagedRecipe byManagedKey(NamespacedKey key) {
        String id = managedKeys.get(key);
        return id == null ? null : recipes.get(id);
    }

    public List<Recipe> vanillaRecipeList() {
        return new ArrayList<>(vanillaRecipes.values());
    }

    public NamespacedKey keyOf(Recipe recipe) {
        return recipe instanceof Keyed keyed ? keyed.getKey() : null;
    }

    public ManagedRecipe fromVanilla(Recipe recipe) {
        NamespacedKey source = keyOf(recipe);
        if (source == null) {
            throw new IllegalArgumentException("Vanilla recipe is not keyed.");
        }
        ManagedRecipe managed = new ManagedRecipe("override_" + source.getNamespace() + "_" + source.getKey().replace('/', '_'), kindOf(recipe));
        managed.setSourceKey(source.toString());
        managed.setEnabled(true);
        managed.setResult(recipe.getResult());
        if (recipe instanceof ShapedRecipe shapedRecipe) {
            readShapedIngredients(managed, shapedRecipe);
        } else if (recipe instanceof ShapelessRecipe shapelessRecipe) {
            readShapelessIngredients(managed, shapelessRecipe);
        }
        return managed;
    }

    public String validateForSave(ManagedRecipe recipe) {
        if (!recipe.kind().isSupported()) {
            return "That recipe type is not editable yet.";
        }
        ItemStack result = recipe.result();
        if (result == null || result.getType().isAir()) {
            return "Set a result item before saving.";
        }
        if (nonEmptyIngredients(recipe).isEmpty()) {
            return "Set at least one ingredient before saving.";
        }
        String invalidConditions = invalidConditions(recipe);
        if (invalidConditions != null) {
            return invalidConditions;
        }
        String invalidIngredients = invalidIngredients(recipe);
        if (invalidIngredients != null) {
            return invalidIngredients;
        }
        String conflict = conflictDescription(recipe);
        if (conflict != null) {
            return conflict;
        }
        return null;
    }

    public void upsert(ManagedRecipe recipe) {
        ManagedRecipe previous = recipes.get(recipe.id());
        if (previous != null) {
            unregisterManaged(previous);
            restoreSource(previous);
        }
        recipes.put(recipe.id(), recipe.copy());
        apply(recipe);
        save();
        refreshPlayers();
    }

    public void removeOrRevert(String id) {
        ManagedRecipe recipe = recipes.remove(id);
        if (recipe == null) {
            return;
        }
        unregisterManaged(recipe);
        restoreSource(recipe);
        save();
        refreshPlayers();
    }

    private void apply(ManagedRecipe recipe) {
        unregisterManaged(recipe);
        if (!recipe.enabled()) {
            if (recipe.sourceKey() != null) {
                NamespacedKey sourceKey = NamespacedKey.fromString(recipe.sourceKey());
                if (sourceKey != null) {
                    Bukkit.removeRecipe(sourceKey, true);
                }
            }
            return;
        }
        if (!recipe.kind().isSupported()
            || recipe.result() == null
            || recipe.result().getType().isAir()
            || nonEmptyIngredients(recipe).isEmpty()
            || invalidConditions(recipe) != null
            || invalidIngredients(recipe) != null) {
            plugin.getLogger().warning("Skipping invalid recipe " + recipe.id() + ".");
            return;
        }
        if (recipe.sourceKey() != null) {
            NamespacedKey sourceKey = NamespacedKey.fromString(recipe.sourceKey());
            if (sourceKey != null) {
                Bukkit.removeRecipe(sourceKey, true);
            }
        }
        Recipe bukkitRecipe = createBukkitRecipe(recipe);
        if (bukkitRecipe != null) {
            Bukkit.addRecipe(bukkitRecipe, true);
            managedKeys.put(recipe.managedKey(plugin), recipe.id());
        }
    }

    private void unregisterManaged(ManagedRecipe recipe) {
        NamespacedKey key = recipe.managedKey(plugin);
        Bukkit.removeRecipe(key, true);
        managedKeys.remove(key);
    }

    private void restoreSource(ManagedRecipe recipe) {
        if (recipe.sourceKey() == null) {
            return;
        }
        NamespacedKey key = NamespacedKey.fromString(recipe.sourceKey());
        Recipe original = key == null ? null : vanillaRecipes.get(key);
        if (original != null) {
            Bukkit.removeRecipe(key, true);
            Bukkit.addRecipe(original, true);
        }
    }

    private Recipe createBukkitRecipe(ManagedRecipe recipe) {
        if (recipe.kind() == RecipeKind.SHAPELESS) {
            ShapelessRecipe shapelessRecipe = new ShapelessRecipe(recipe.managedKey(plugin), recipe.result());
            for (IngredientSpec spec : nonEmptyIngredients(recipe)) {
                shapelessRecipe.addIngredient(IngredientMatcher.toRecipeChoice(spec));
            }
            return shapelessRecipe;
        }
        return createShapedRecipe(recipe);
    }

    private Recipe createShapedRecipe(ManagedRecipe recipe) {
        Bounds bounds = bounds(recipe);
        if (bounds.empty()) {
            return null;
        }
        ShapedRecipe shapedRecipe = new ShapedRecipe(recipe.managedKey(plugin), recipe.result());
        Map<Character, RecipeChoice> choices = new LinkedHashMap<>();
        List<String> shape = new ArrayList<>();
        int nextKey = 0;
        for (int row = bounds.minRow; row <= bounds.maxRow; row++) {
            StringBuilder line = new StringBuilder();
            for (int col = bounds.minCol; col <= bounds.maxCol; col++) {
                IngredientSpec spec = recipe.ingredient(row * 3 + col);
                if (spec == null || spec.isEmpty()) {
                    line.append(' ');
                    continue;
                }
                char key = INGREDIENT_KEYS[nextKey++];
                line.append(key);
                choices.put(key, IngredientMatcher.toRecipeChoice(spec));
            }
            shape.add(line.toString());
        }
        shapedRecipe.shape(shape.toArray(String[]::new));
        for (Map.Entry<Character, RecipeChoice> entry : choices.entrySet()) {
            shapedRecipe.setIngredient(entry.getKey(), entry.getValue());
        }
        return shapedRecipe;
    }

    private String conflictDescription(ManagedRecipe recipe) {
        String signature = RecipePattern.signature(recipe);
        for (ManagedRecipe other : recipes.values()) {
            if (other.id().equals(recipe.id())) {
                continue;
            }
            if (RecipePattern.signature(other).equals(signature)) {
                return "This recipe collides with " + other.displayLabel() + ".";
            }
        }
        for (Map.Entry<NamespacedKey, Recipe> entry : vanillaRecipes.entrySet()) {
            if (recipe.sourceKey() != null && recipe.sourceKey().equals(entry.getKey().toString())) {
                continue;
            }
            if (RecipePattern.signature(entry.getValue()).equals(signature)) {
                return "This recipe collides with vanilla recipe " + entry.getKey() + ". Edit that recipe instead.";
            }
        }
        return null;
    }

    private String invalidConditions(ManagedRecipe recipe) {
        List<String> invalidDimensions = invalidDimensions(recipe.conditions().dimensions());
        if (!invalidDimensions.isEmpty()) {
            return "Unknown dimension(s): " + String.join(", ", invalidDimensions);
        }
        List<String> invalidBiomes = invalidBiomes(recipe.conditions().biomes());
        if (!invalidBiomes.isEmpty()) {
            return "Unknown biome(s): " + String.join(", ", invalidBiomes);
        }
        return null;
    }

    private String invalidIngredients(ManagedRecipe recipe) {
        for (IngredientSpec spec : recipe.ingredients()) {
            if (spec == null || !spec.hasUsableTag()) {
                continue;
            }
            if (!spec.isTagOnly()) {
                return "Item tags can only be used on empty ingredient slots.";
            }
            if (IngredientMatcher.usableTagValues(spec.tagKey()).isEmpty()) {
                return "Unknown item tag: " + spec.tagKey();
            }
        }
        return null;
    }

    public static List<String> invalidDimensions(Collection<String> dimensions) {
        List<String> invalid = new ArrayList<>();
        for (String dimension : dimensions) {
            NamespacedKey key = NamespacedKey.fromString(dimension == null ? "" : dimension);
            if (key == null || Bukkit.getWorld(key) == null) {
                invalid.add(dimension);
            }
        }
        return invalid;
    }

    public static List<String> invalidBiomes(Collection<String> biomes) {
        List<String> invalid = new ArrayList<>();
        for (String biome : biomes) {
            NamespacedKey key = NamespacedKey.fromString(biome == null ? "" : biome);
            if (key == null || Registry.BIOME.get(key) == null) {
                invalid.add(biome);
            }
        }
        return invalid;
    }

    private List<IngredientSpec> nonEmptyIngredients(ManagedRecipe recipe) {
        List<IngredientSpec> specs = new ArrayList<>();
        for (IngredientSpec spec : recipe.ingredients()) {
            if (spec != null && !spec.isEmpty()) {
                specs.add(spec);
            }
        }
        return specs;
    }

    private RecipeKind kindOf(Recipe recipe) {
        return recipe instanceof ShapelessRecipe ? RecipeKind.SHAPELESS : RecipeKind.SHAPED;
    }

    private void readShapedIngredients(ManagedRecipe managed, ShapedRecipe shapedRecipe) {
        String[] shape = shapedRecipe.getShape();
        Map<Character, RecipeChoice> choices = shapedRecipe.getChoiceMap();
        for (int row = 0; row < shape.length && row < 3; row++) {
            String line = shape[row];
            for (int col = 0; col < line.length() && col < 3; col++) {
                IngredientSpec spec = specFromChoice(choices.get(line.charAt(col)));
                managed.setIngredient(row * 3 + col, spec);
            }
        }
    }

    private void readShapelessIngredients(ManagedRecipe managed, ShapelessRecipe shapelessRecipe) {
        List<RecipeChoice> choices = shapelessRecipe.getChoiceList();
        for (int i = 0; i < choices.size() && i < 9; i++) {
            managed.setIngredient(i, specFromChoice(choices.get(i)));
        }
    }

    private IngredientSpec specFromChoice(RecipeChoice choice) {
        if (choice instanceof RecipeChoice.ExactChoice exactChoice && !exactChoice.getChoices().isEmpty()) {
            return IngredientSpec.fromSample(exactChoice.getChoices().getFirst());
        }
        if (choice instanceof RecipeChoice.MaterialChoice materialChoice && !materialChoice.getChoices().isEmpty()) {
            Material material = materialChoice.getChoices().getFirst();
            return IngredientSpec.fromSample(new ItemStack(material));
        }
        return null;
    }

    private void refreshPlayers() {
        Bukkit.updateRecipes();
    }

    private Bounds bounds(ManagedRecipe recipe) {
        Bounds bounds = new Bounds();
        for (int i = 0; i < 9; i++) {
            IngredientSpec spec = recipe.ingredient(i);
            if (spec != null && !spec.isEmpty()) {
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
    }
}
