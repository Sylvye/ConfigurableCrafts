package com.bountysmp.configurablecrafts.crafting;

import com.bountysmp.configurablecrafts.model.IngredientSpec;
import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.RecipeKind;
import com.bountysmp.configurablecrafts.model.WeatherMode;
import com.bountysmp.configurablecrafts.storage.RecipeRepository;
import io.papermc.paper.potion.PotionMix;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.plugin.Plugin;

public final class ManagedRecipeRegistry {
    private final java.util.Map<String, com.bountysmp.configurablecrafts.api.OwnedRecipe> owned = new java.util.HashMap<>();
    public com.bountysmp.configurablecrafts.api.OwnedRecipe owner(String id) { return owned.get(id); }
    /** Refresh presentation only; ingredient registration and discovery remain untouched. */
    public void refreshOwnedPreview(String id, com.bountysmp.configurablecrafts.api.OwnedRecipe policy) {
        if (owner(id) != policy) throw new IllegalArgumentException("Recipe owner mismatch");
        ManagedRecipe recipe = byId(id);
        if (recipe != null) recipe.setResult(policy.preview());
    }
    public void registerOwned(ManagedRecipe defaults, com.bountysmp.configurablecrafts.api.OwnedRecipe policy) {
        owned.put(defaults.id(), policy);
        ManagedRecipe recipe = byId(defaults.id());
        upsert(recipe == null ? defaults : recipe);
    }
    private void enforceOwned(ManagedRecipe recipe) {
        var policy = owner(recipe.id());
        if (policy == null) return;
        recipe.setKind(com.bountysmp.configurablecrafts.model.RecipeKind.SHAPED);
        recipe.setSourceKey(null);
        recipe.setResult(policy.preview());
        recipe.setAllowCrafters(false);
        recipe.setGlobalLimit(new com.bountysmp.configurablecrafts.model.RecipeLimit(1, 0));
    }

    public static final String CRAFTER_BYPASS_WARNING = "Warning: crafters bypass crafting conditions and limits. Only enable them on recipes without dimension, weather, XP, biome, per-player limit, or global limit requirements.";
    private static final char[] INGREDIENT_KEYS = "ABCDEFGHI".toCharArray();
    private static final Set<Material> VANILLA_BREWING_INGREDIENTS = Set.of(
        Material.NETHER_WART,
        Material.REDSTONE,
        Material.GLOWSTONE_DUST,
        Material.FERMENTED_SPIDER_EYE,
        Material.GUNPOWDER,
        Material.DRAGON_BREATH,
        Material.SUGAR,
        Material.RABBIT_FOOT,
        Material.GLISTERING_MELON_SLICE,
        Material.SPIDER_EYE,
        Material.PUFFERFISH,
        Material.MAGMA_CREAM,
        Material.GOLDEN_CARROT,
        Material.BLAZE_POWDER,
        Material.GHAST_TEAR,
        Material.TURTLE_HELMET,
        Material.PHANTOM_MEMBRANE
    );

    private final Plugin plugin;
    private final RecipeRepository repository;
    private final PotionMixes potionMixes;
    private final Map<String, ManagedRecipe> recipes = new LinkedHashMap<>();
    private final Map<NamespacedKey, Recipe> vanillaRecipes = new LinkedHashMap<>();
    private final Map<NamespacedKey, String> managedKeys = new HashMap<>();
    private final Set<String> exhausted = new java.util.HashSet<>();
    private java.util.function.Predicate<ManagedRecipe> allowance = recipe -> true;

    public void allowance(java.util.function.Predicate<ManagedRecipe> predicate) {
        allowance = java.util.Objects.requireNonNull(predicate);
    }

    private boolean available(ManagedRecipe recipe) {
        var policy = owner(recipe.id());
        return allowance.test(recipe) && (policy == null || policy.available());
    }

    /** Remove exhausted registrations, retaining edits and enabled state for a later reset. */
    public void refreshAvailability() {
        boolean changed = false;
        for (ManagedRecipe recipe : recipes.values()) {
            if (!recipe.enabled()) continue;
            boolean unavailable = !available(recipe);
            if (unavailable != exhausted.contains(recipe.id())) {
                apply(recipe);
                changed = true;
            }
        }
        if (changed) refreshPlayers();
    }

    public ManagedRecipeRegistry(Plugin plugin, RecipeRepository repository) {
        this(plugin, repository, new BukkitPotionMixes());
    }

    ManagedRecipeRegistry(Plugin plugin, RecipeRepository repository, PotionMixes potionMixes) {
        this.plugin = plugin;
        this.repository = repository;
        this.potionMixes = potionMixes;
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
            if (isVanillaEditable(recipe)) {
                vanillaRecipes.put(key, recipe);
            }
        }
    }

    public void load() {
        recipes.clear();
        exhausted.clear();
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
        List<NamespacedKey> restoredSources = new ArrayList<>();
        for (ManagedRecipe recipe : new ArrayList<>(recipes.values())) {
            unregisterManaged(recipe);
            NamespacedKey restoredSource = restoreSource(recipe);
            if (restoredSource != null) {
                restoredSources.add(restoredSource);
            }
        }
        refreshPlayers(restoredSources.toArray(NamespacedKey[]::new));
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

    public List<VanillaBrewingCatalog.Entry> vanillaBrewingRecipes() {
        return VanillaBrewingCatalog.entries();
    }

    public ManagedRecipe vanillaBrewingOverride(String sourceKey) {
        for (ManagedRecipe recipe : recipes.values()) {
            if (sourceKey != null && sourceKey.equals(recipe.sourceKey())) {
                return recipe;
            }
        }
        return null;
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
        } else if (recipe instanceof CookingRecipe<?> cookingRecipe) {
            managed.setIngredient(0, specFromChoice(cookingRecipe.getInputChoice()));
            managed.setExperience(cookingRecipe.getExperience());
            managed.setCookTimeTicks(cookingRecipe.getCookingTime());
        } else if (recipe instanceof StonecuttingRecipe stonecuttingRecipe) {
            managed.setIngredient(0, specFromChoice(stonecuttingRecipe.getInputChoice()));
        } else if (recipe instanceof SmithingTransformRecipe smithingRecipe) {
            managed.setIngredient(0, specFromChoice(smithingRecipe.getTemplate()));
            managed.setIngredient(1, specFromChoice(smithingRecipe.getBase()));
            managed.setIngredient(2, specFromChoice(smithingRecipe.getAddition()));
            managed.setCopyDataComponents(smithingRecipe.willCopyDataComponents());
        }
        return managed;
    }

    public String validateForSave(ManagedRecipe recipe) {
        for (var entry : owned.entrySet()) {
            if (!entry.getKey().equals(recipe.id()) && entry.getValue().matches(recipe.result()))
                return "That output belongs to another plugin's permanent recipe.";
        }
        enforceOwned(recipe);
        if (!recipe.kind().isSupported()) {
            return "That recipe type is not editable yet.";
        }
        ItemStack result = recipe.result();
        if (result == null || result.getType().isAir()) {
            return "Set a result item before saving.";
        }
        String invalidShape = invalidShape(recipe);
        if (invalidShape != null) {
            return invalidShape;
        }
        String invalidConditions = invalidConditions(recipe);
        if (invalidConditions != null) {
            return invalidConditions;
        }
        String invalidCrafterAccess = invalidCrafterAccess(recipe);
        if (invalidCrafterAccess != null) {
            return invalidCrafterAccess;
        }
        String invalidIngredients = invalidIngredients(recipe);
        if (invalidIngredients != null) {
            return invalidIngredients;
        }
        String conflict = conflictDescription(recipe);
        if (conflict != null) {
            return conflict;
        }
        String brewingConflict = brewingConflictDescription(recipe);
        if (brewingConflict != null) {
            return brewingConflict;
        }
        return null;
    }

    public List<String> warningsForSave(ManagedRecipe recipe) {
        List<String> warnings = new ArrayList<>();
        if (recipe.allowCrafters()) {
            warnings.add(CRAFTER_BYPASS_WARNING);
        }
        if (recipe.kind().canonical() == RecipeKind.BREWING && likelyShadowsVanillaBrewing(recipe)) {
            warnings.add("Warning: this brewing recipe may override a vanilla brewing mix.");
        }
        return warnings;
    }

    public void upsert(ManagedRecipe recipe) {
        enforceOwned(recipe);
        ManagedRecipe previous = recipes.get(recipe.id());
        NamespacedKey restoredSource = null;
        if (previous != null) {
            unregisterManaged(previous);
            restoredSource = restoreSource(previous);
        }
        recipes.put(recipe.id(), recipe.copy());
        apply(recipe);
        save();
        refreshPlayers(restoredSource);
    }

    public void removeOrRevert(String id) {
        if (owner(id) != null) {
            ManagedRecipe existing = byId(id);
            if (existing != null) { existing.setEnabled(false); upsert(existing); }
            return;
        }
        ManagedRecipe recipe = recipes.remove(id);
        exhausted.remove(id);
        if (recipe == null) {
            return;
        }
        unregisterManaged(recipe);
        NamespacedKey restoredSource = restoreSource(recipe);
        save();
        refreshPlayers(restoredSource);
    }

    private void apply(ManagedRecipe recipe) {
        unregisterManaged(recipe);
        exhausted.remove(recipe.id());
        if (!recipe.enabled()) {
            if (recipe.sourceKey() != null) {
                NamespacedKey sourceKey = NamespacedKey.fromString(recipe.sourceKey());
                if (sourceKey != null) {
                    unregisterBukkitRecipe(sourceKey);
                }
            }
            return;
        }
        if (!available(recipe)) {
            exhausted.add(recipe.id());
            // Cached craft events must still reach limit validation after unregistration.
            managedKeys.put(recipe.managedKey(plugin), recipe.id());
            if (recipe.sourceKey() != null) {
                NamespacedKey sourceKey = NamespacedKey.fromString(recipe.sourceKey());
                if (sourceKey != null) unregisterBukkitRecipe(sourceKey);
            }
            return;
        }
        if (!recipe.kind().isSupported()
            || recipe.result() == null
            || recipe.result().getType().isAir()
            || nonEmptyIngredients(recipe).isEmpty()
            || invalidShape(recipe) != null
            || invalidConditions(recipe) != null
            || invalidIngredients(recipe) != null
            || brewingConflictDescription(recipe) != null) {
            plugin.getLogger().warning("Skipping invalid recipe " + recipe.id() + ".");
            return;
        }
        if (recipe.kind().canonical() == RecipeKind.BREWING) {
            registerPotionMixes(recipe);
            managedKeys.put(recipe.managedKey(plugin), recipe.id());
            return;
        }
        if (recipe.sourceKey() != null) {
            NamespacedKey sourceKey = NamespacedKey.fromString(recipe.sourceKey());
            if (sourceKey != null) {
                unregisterBukkitRecipe(sourceKey);
            }
        }
        Recipe bukkitRecipe = createBukkitRecipe(recipe);
        if (bukkitRecipe != null && Bukkit.addRecipe(bukkitRecipe, true)) {
            managedKeys.put(recipe.managedKey(plugin), recipe.id());
        }
    }

    private void unregisterManaged(ManagedRecipe recipe) {
        NamespacedKey key = recipe.managedKey(plugin);
        unregisterBukkitRecipe(key);
        potionMixes.remove(key);
        if (VanillaBrewingCatalog.isOverride(recipe)) {
            for (Material material : potionContainers()) {
                NamespacedKey mixKey = potionMixKey(recipe, material);
                potionMixes.remove(mixKey);
                managedKeys.remove(mixKey);
            }
        }
        managedKeys.remove(key);
    }

    private void unregisterBukkitRecipe(NamespacedKey key) {
        if (Bukkit.getRecipe(key) != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.undiscoverRecipe(key);
            }
        }
        Bukkit.removeRecipe(key, true);
    }

    private void registerPotionMixes(ManagedRecipe recipe) {
        VanillaBrewingCatalog.Entry vanilla = VanillaBrewingCatalog.bySourceKey(recipe.sourceKey());
        if (vanilla == null) {
            potionMixes.add(new PotionMix(
                recipe.managedKey(plugin), recipe.result(),
                PotionMix.createPredicateChoice(input -> IngredientMatcher.matches(recipe.ingredient(0), input)),
                PotionMix.createPredicateChoice(ingredient -> IngredientMatcher.matches(recipe.ingredient(1), ingredient))
            ));
            return;
        }
        for (Material container : potionContainers()) {
            if (vanilla.containerConversion() && container != vanilla.inputMaterial()) {
                continue;
            }
            ItemStack result = resultForContainer(recipe.result(), vanilla, container);
            NamespacedKey key = potionMixKey(recipe, container);
            potionMixes.add(new PotionMix(
                key, result,
                PotionMix.createPredicateChoice(input -> input.getType() == container
                    && VanillaBrewingCatalog.matchesPotionInput(recipe.ingredient(0), input)),
                PotionMix.createPredicateChoice(ingredient -> IngredientMatcher.matches(recipe.ingredient(1), ingredient))
            ));
            managedKeys.put(key, recipe.id());
        }
    }

    private ItemStack resultForContainer(ItemStack configuredResult, VanillaBrewingCatalog.Entry vanilla, Material inputContainer) {
        ItemStack result = configuredResult.clone();
        result.setType(vanilla.containerConversion() ? vanilla.resultMaterial() : inputContainer);
        return result;
    }

    private List<Material> potionContainers() {
        return List.of(Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION);
    }

    private NamespacedKey potionMixKey(ManagedRecipe recipe, Material material) {
        return new NamespacedKey(plugin, "recipe/" + recipe.id() + "/" + material.name().toLowerCase(Locale.ROOT));
    }

    private NamespacedKey restoreSource(ManagedRecipe recipe) {
        if (recipe.sourceKey() == null) {
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(recipe.sourceKey());
        Recipe original = key == null ? null : vanillaRecipes.get(key);
        if (original != null) {
            unregisterBukkitRecipe(key);
            if (Bukkit.addRecipe(original, true)) {
                return key;
            }
        }
        return null;
    }

    private Recipe createBukkitRecipe(ManagedRecipe recipe) {
        RecipeKind kind = recipe.kind().canonical();
        if (kind == RecipeKind.SHAPELESS) {
            ShapelessRecipe shapelessRecipe = new ShapelessRecipe(recipe.managedKey(plugin), recipe.result());
            for (IngredientSpec spec : nonEmptyIngredients(recipe)) {
                shapelessRecipe.addIngredient(IngredientMatcher.toRecipeChoice(spec));
            }
            return shapelessRecipe;
        }
        if (kind == RecipeKind.SMELTING) {
            return new FurnaceRecipe(recipe.managedKey(plugin), recipe.result(), IngredientMatcher.toRecipeChoice(recipe.ingredient(0)), recipe.experience(), recipe.cookTimeTicks());
        }
        if (kind == RecipeKind.SMOKING) {
            return new SmokingRecipe(recipe.managedKey(plugin), recipe.result(), IngredientMatcher.toRecipeChoice(recipe.ingredient(0)), recipe.experience(), recipe.cookTimeTicks());
        }
        if (kind == RecipeKind.BLASTING) {
            return new BlastingRecipe(recipe.managedKey(plugin), recipe.result(), IngredientMatcher.toRecipeChoice(recipe.ingredient(0)), recipe.experience(), recipe.cookTimeTicks());
        }
        if (kind == RecipeKind.CAMPFIRE) {
            return new CampfireRecipe(recipe.managedKey(plugin), recipe.result(), IngredientMatcher.toRecipeChoice(recipe.ingredient(0)), recipe.experience(), recipe.cookTimeTicks());
        }
        if (kind == RecipeKind.STONECUTTING) {
            return new StonecuttingRecipe(recipe.managedKey(plugin), recipe.result(), IngredientMatcher.toRecipeChoice(recipe.ingredient(0)));
        }
        if (kind == RecipeKind.SMITHING) {
            return new SmithingTransformRecipe(
                recipe.managedKey(plugin),
                recipe.result(),
                IngredientMatcher.toRecipeChoice(recipe.ingredient(0)),
                IngredientMatcher.toRecipeChoice(recipe.ingredient(1)),
                IngredientMatcher.toRecipeChoice(recipe.ingredient(2)),
                recipe.copyDataComponents()
            );
        }
        if (kind == RecipeKind.BREWING) {
            return null;
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
        if (!recipe.kind().isCraftingTable()) {
            return null;
        }
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
        if (recipe.kind().isBlockDriven()) {
            if (recipe.playerLimit().enabled()) {
                return recipe.kind().displayName() + " recipes cannot use per-player limits.";
            }
            if (recipe.kind() == RecipeKind.BREWING && recipe.globalLimit().enabled()) {
                return "Brewing recipes cannot use global limits.";
            }
            if (recipe.conditions().minimumExperienceLevel() > 0) {
                return recipe.kind().displayName() + " recipes cannot require player experience levels.";
            }
            if (recipe.kind() == RecipeKind.BREWING && !recipe.conditions().dimensions().isEmpty()) {
                return "Brewing recipes cannot use dimension conditions.";
            }
            if (recipe.kind() == RecipeKind.BREWING && !recipe.conditions().biomes().isEmpty()) {
                return "Brewing recipes cannot use biome conditions.";
            }
            if (recipe.kind() == RecipeKind.BREWING && recipe.conditions().weather() != WeatherMode.ANY) {
                return "Brewing recipes cannot use weather conditions.";
            }
        }
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

    private String invalidCrafterAccess(ManagedRecipe recipe) {
        if (!recipe.allowCrafters()) {
            return null;
        }
        if (!recipe.kind().isCraftingTable()) {
            return "Only shaped and shapeless recipes can allow crafters.";
        }
        if (hasCrafterBypassedRequirements(recipe)) {
            return "Crafters can only be allowed when dimension, weather, XP, biome, and crafting limit requirements are not configured.";
        }
        return null;
    }

    public static boolean hasCrafterBypassedRequirements(ManagedRecipe recipe) {
        return !recipe.conditions().dimensions().isEmpty()
            || !recipe.conditions().biomes().isEmpty()
            || recipe.conditions().weather() != WeatherMode.ANY
            || recipe.conditions().minimumExperienceLevel() > 0
            || recipe.playerLimit().enabled()
            || recipe.globalLimit().enabled();
    }

    private String brewingConflictDescription(ManagedRecipe recipe) {
        if (recipe.kind().canonical() != RecipeKind.BREWING) {
            return null;
        }
        String signature = brewingSignature(recipe);
        String reagentSignature = IngredientMatcher.signatureToken(recipe.ingredient(1));
        for (ManagedRecipe other : recipes.values()) {
            if (other.id().equals(recipe.id()) || other.kind().canonical() != RecipeKind.BREWING) {
                continue;
            }
            if (brewingSignature(other).equals(signature)) {
                return "This brewing recipe collides with " + other.displayLabel() + ".";
            }
            if (IngredientMatcher.signatureToken(other.ingredient(1)).equals(reagentSignature)
                && other.brewTimeTicks() != recipe.brewTimeTicks()) {
                return "Brewing recipes using the same ingredient must use the same brew time.";
            }
        }
        return null;
    }

    private String brewingSignature(ManagedRecipe recipe) {
        return IngredientMatcher.signatureToken(recipe.ingredient(0)) + " + " + IngredientMatcher.signatureToken(recipe.ingredient(1));
    }

    private boolean likelyShadowsVanillaBrewing(ManagedRecipe recipe) {
        IngredientSpec input = recipe.ingredient(0);
        IngredientSpec ingredient = recipe.ingredient(1);
        ItemStack inputSample = input == null ? null : input.sample();
        ItemStack ingredientSample = ingredient == null ? null : ingredient.sample();
        return inputSample != null
            && isPotionLike(inputSample.getType())
            && ingredientSample != null
            && VANILLA_BREWING_INGREDIENTS.contains(ingredientSample.getType());
    }

    private boolean isPotionLike(Material material) {
        return material == Material.POTION
            || material == Material.SPLASH_POTION
            || material == Material.LINGERING_POTION;
    }

    private String invalidShape(ManagedRecipe recipe) {
        RecipeKind kind = recipe.kind().canonical();
        if (kind == RecipeKind.SHAPED || kind == RecipeKind.SHAPELESS) {
            return nonEmptyIngredients(recipe).isEmpty() ? "Set at least one ingredient before saving." : null;
        }
        if (kind == RecipeKind.BREWING) {
            if (isEmptyIngredient(recipe, 0)) {
                return "Set a potion input before saving.";
            }
            if (isEmptyIngredient(recipe, 1)) {
                return "Set a brewing ingredient before saving.";
            }
            if (VanillaBrewingCatalog.isOverride(recipe)) {
                ItemStack input = recipe.ingredient(0).sample();
                if (input == null || !isPotionLike(input.getType())) {
                    return "Vanilla brewing overrides must use a potion input.";
                }
            }
            return null;
        }
        if (kind.isCooking() || kind == RecipeKind.STONECUTTING) {
            return isEmptyIngredient(recipe, 0) ? "Set an input item before saving." : null;
        }
        if (kind == RecipeKind.SMITHING) {
            if (isEmptyIngredient(recipe, 0)) {
                return "Set a smithing template before saving.";
            }
            if (isEmptyIngredient(recipe, 1)) {
                return "Set smithing equipment before saving.";
            }
            if (isEmptyIngredient(recipe, 2)) {
                return "Set a smithing addition before saving.";
            }
        }
        return null;
    }

    private boolean isEmptyIngredient(ManagedRecipe recipe, int index) {
        IngredientSpec spec = recipe.ingredient(index);
        return spec == null || spec.isEmpty();
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
        if (recipe instanceof ShapelessRecipe) {
            return RecipeKind.SHAPELESS;
        }
        if (recipe instanceof FurnaceRecipe) {
            return RecipeKind.SMELTING;
        }
        if (recipe instanceof SmokingRecipe) {
            return RecipeKind.SMOKING;
        }
        if (recipe instanceof BlastingRecipe) {
            return RecipeKind.BLASTING;
        }
        if (recipe instanceof CampfireRecipe) {
            return RecipeKind.CAMPFIRE;
        }
        if (recipe instanceof StonecuttingRecipe) {
            return RecipeKind.STONECUTTING;
        }
        if (recipe instanceof SmithingTransformRecipe) {
            return RecipeKind.SMITHING;
        }
        return RecipeKind.SHAPED;
    }

    private boolean isVanillaEditable(Recipe recipe) {
        return recipe instanceof ShapedRecipe
            || recipe instanceof ShapelessRecipe
            || recipe instanceof CookingRecipe<?>
            || recipe instanceof StonecuttingRecipe
            || recipe instanceof SmithingTransformRecipe;
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
            return IngredientSpec.fromExactSample(exactChoice.getChoices().getFirst());
        }
        if (choice instanceof RecipeChoice.MaterialChoice materialChoice && !materialChoice.getChoices().isEmpty()) {
            Material material = materialChoice.getChoices().getFirst();
            return IngredientSpec.fromSample(new ItemStack(material));
        }
        return null;
    }

    public void syncPlayerRecipes(Player player) {
        List<NamespacedKey> keys = managedKeys.entrySet().stream()
            .filter(entry -> {
                ManagedRecipe recipe = recipes.get(entry.getValue());
                return recipe != null && recipe.enabled() && recipe.kind().canonical() != RecipeKind.BREWING;
            })
            .map(Map.Entry::getKey)
            .filter(key -> Bukkit.getRecipe(key) != null)
            .toList();
        if (!keys.isEmpty()) {
            player.discoverRecipes(keys);
        }
    }

    private void refreshPlayers(NamespacedKey... restoredSources) {
        Bukkit.updateRecipes();
        List<NamespacedKey> restoredKeys = new ArrayList<>();
        for (NamespacedKey key : restoredSources) {
            if (key != null && Bukkit.getRecipe(key) != null) {
                restoredKeys.add(key);
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            syncPlayerRecipes(player);
            if (!restoredKeys.isEmpty()) {
                player.discoverRecipes(restoredKeys);
            }
        }
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

    interface PotionMixes {
        void add(PotionMix potionMix);

        void remove(NamespacedKey key);
    }

    private static final class BukkitPotionMixes implements PotionMixes {
        @Override
        public void add(PotionMix potionMix) {
            Bukkit.getPotionBrewer().addPotionMix(potionMix);
        }

        @Override
        public void remove(NamespacedKey key) {
            Bukkit.getPotionBrewer().removePotionMix(key);
        }
    }
}
