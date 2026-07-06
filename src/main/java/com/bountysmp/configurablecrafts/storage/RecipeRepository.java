package com.bountysmp.configurablecrafts.storage;

import com.bountysmp.configurablecrafts.model.IngredientSpec;
import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.MatcherType;
import com.bountysmp.configurablecrafts.model.RecipeConditions;
import com.bountysmp.configurablecrafts.model.RecipeKind;
import com.bountysmp.configurablecrafts.model.RecipeLimit;
import com.bountysmp.configurablecrafts.model.WeatherMode;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

public final class RecipeRepository {
    private static final int SCHEMA_VERSION = 1;
    private final File file;

    public RecipeRepository(File file) {
        this.file = file;
    }

    public List<ManagedRecipe> load() {
        List<ManagedRecipe> recipes = new ArrayList<>();
        if (!file.exists()) {
            return recipes;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection recipesSection = yaml.getConfigurationSection("recipes");
        if (recipesSection == null) {
            return recipes;
        }
        for (String id : recipesSection.getKeys(false)) {
            ConfigurationSection section = recipesSection.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            ManagedRecipe recipe = readRecipe(id, section);
            if (recipe != null) {
                recipes.add(recipe);
            }
        }
        return recipes;
    }

    public void save(Iterable<ManagedRecipe> recipes) {
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", SCHEMA_VERSION);
        ConfigurationSection recipesSection = yaml.createSection("recipes");
        for (ManagedRecipe recipe : recipes) {
            writeRecipe(recipesSection.createSection(recipe.id()), recipe);
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save " + file.getAbsolutePath(), exception);
        }
    }

    private ManagedRecipe readRecipe(String id, ConfigurationSection section) {
        RecipeKind kind = RecipeKind.parse(section.getString("type"), RecipeKind.SHAPED);
        ManagedRecipe recipe = new ManagedRecipe(id, kind);
        recipe.setSourceKey(section.getString("source-key"));
        recipe.setEnabled(section.getBoolean("enabled", true));
        recipe.setResult(section.getItemStack("result"));

        ConfigurationSection ingredients = section.getConfigurationSection("ingredients");
        if (ingredients != null) {
            for (int i = 0; i < 9; i++) {
                ConfigurationSection ingredientSection = ingredients.getConfigurationSection(Integer.toString(i));
                if (ingredientSection != null) {
                    recipe.setIngredient(i, readIngredient(ingredientSection));
                }
            }
        }
        recipe.setConditions(readConditions(section.getConfigurationSection("conditions")));
        ConfigurationSection limits = section.getConfigurationSection("limits");
        recipe.setPlayerLimit(readLimit(limits == null ? null : limits.getConfigurationSection("player")));
        recipe.setGlobalLimit(readLimit(limits == null ? null : limits.getConfigurationSection("global")));
        recipe.setExperience((float) section.getDouble("experience", 0.0D));
        recipe.setCookTimeTicks(section.getInt("cook-time-ticks", ManagedRecipe.DEFAULT_COOK_TIME_TICKS));
        recipe.setBrewTimeTicks(section.getInt("brew-time-ticks", ManagedRecipe.DEFAULT_BREW_TIME_TICKS));
        recipe.setCopyDataComponents(section.getBoolean("copy-data-components", true));
        recipe.setAllowCrafters(section.getBoolean("allow-crafters", false));
        return recipe;
    }

    private IngredientSpec readIngredient(ConfigurationSection section) {
        ItemStack sample = section.getItemStack("sample");
        IngredientSpec spec = new IngredientSpec();
        spec.setSample(sample);
        spec.setLogic(section.getString("logic", "all"));
        for (String matcher : section.getStringList("matchers")) {
            try {
                spec.matchers().add(MatcherType.valueOf(matcher.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Ignore unknown future matcher names.
            }
        }
        if (spec.matchers().isEmpty()) {
            spec.matchers().add(MatcherType.MATERIAL);
        }
        spec.setLoreContains(section.getString("lore-contains", ""));
        spec.setTagKey(section.getString("tag", ""));
        spec.normalizeMatchers();
        if (spec.isEmpty()) {
            return null;
        }
        ConfigurationSection enchantments = section.getConfigurationSection("enchantments");
        if (enchantments != null) {
            for (String key : enchantments.getKeys(false)) {
                spec.enchantments().put(key.toLowerCase(Locale.ROOT), Math.max(1, enchantments.getInt(key, 1)));
            }
        }
        return spec;
    }

    private RecipeConditions readConditions(ConfigurationSection section) {
        RecipeConditions conditions = new RecipeConditions();
        if (section == null) {
            return conditions;
        }
        conditions.dimensions().addAll(section.getStringList("dimensions"));
        conditions.biomes().addAll(section.getStringList("biomes"));
        try {
            conditions.setWeather(WeatherMode.valueOf(section.getString("weather", "ANY").toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            conditions.setWeather(WeatherMode.ANY);
        }
        conditions.setMinimumExperienceLevel(section.getInt("minimum-experience-level", 0));
        return conditions;
    }

    private RecipeLimit readLimit(ConfigurationSection section) {
        if (section == null) {
            return new RecipeLimit();
        }
        return new RecipeLimit(section.getInt("crafts", 0), section.getLong("window-seconds", 0L));
    }

    private void writeRecipe(ConfigurationSection section, ManagedRecipe recipe) {
        section.set("type", recipe.kind().name());
        section.set("source-key", recipe.sourceKey());
        section.set("enabled", recipe.enabled());
        section.set("allow-crafters", recipe.allowCrafters());
        section.set("result", recipe.result());
        ConfigurationSection ingredients = section.createSection("ingredients");
        for (int i = 0; i < 9; i++) {
            IngredientSpec spec = recipe.ingredient(i);
            if (spec != null && !spec.isEmpty()) {
                writeIngredient(ingredients.createSection(Integer.toString(i)), spec);
            }
        }
        writeConditions(section.createSection("conditions"), recipe.conditions());
        ConfigurationSection limits = section.createSection("limits");
        writeLimit(limits.createSection("player"), recipe.playerLimit());
        writeLimit(limits.createSection("global"), recipe.globalLimit());
        if (recipe.kind().isCooking()) {
            section.set("experience", recipe.experience());
            section.set("cook-time-ticks", recipe.cookTimeTicks());
        }
        if (recipe.kind() == RecipeKind.BREWING) {
            section.set("brew-time-ticks", recipe.brewTimeTicks());
        }
        if (recipe.kind() == RecipeKind.SMITHING) {
            section.set("copy-data-components", recipe.copyDataComponents());
        }
    }

    private void writeIngredient(ConfigurationSection section, IngredientSpec spec) {
        if (!spec.isTagOnly()) {
            section.set("sample", spec.sample());
        }
        section.set("logic", spec.logic());
        section.set("matchers", spec.matchers().stream().map(Enum::name).toList());
        section.set("lore-contains", spec.loreContains());
        section.set("tag", spec.tagKey());
        ConfigurationSection enchantments = section.createSection("enchantments");
        for (Map.Entry<String, Integer> entry : spec.enchantments().entrySet()) {
            enchantments.set(entry.getKey(), entry.getValue());
        }
    }

    private void writeConditions(ConfigurationSection section, RecipeConditions conditions) {
        section.set("dimensions", new ArrayList<>(conditions.dimensions()));
        section.set("biomes", new ArrayList<>(conditions.biomes()));
        section.set("weather", conditions.weather().name());
        section.set("minimum-experience-level", conditions.minimumExperienceLevel());
    }

    private void writeLimit(ConfigurationSection section, RecipeLimit limit) {
        section.set("crafts", limit.crafts());
        section.set("window-seconds", limit.windowSeconds());
    }
}
