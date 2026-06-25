package com.bountysmp.configurablecrafts.crafting;

import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.RecipeConditions;
import com.bountysmp.configurablecrafts.model.WeatherMode;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;

public final class ConditionValidator {
    private ConditionValidator() {
    }

    public static String failureReason(ManagedRecipe recipe, Player player) {
        if (!recipe.enabled()) {
            return "This recipe is disabled.";
        }
        RecipeConditions conditions = recipe.conditions();
        World world = player.getWorld();
        String dimension = world.getKey().toString();
        if (!conditions.dimensions().isEmpty() && !conditions.dimensions().contains(dimension)) {
            return "This recipe cannot be crafted in this dimension.";
        }
        Biome biome = player.getLocation().getBlock().getBiome();
        String biomeKey = biome.getKey().toString();
        if (!conditions.biomes().isEmpty() && !conditions.biomes().contains(biomeKey)) {
            return "This recipe cannot be crafted in this biome.";
        }
        WeatherMode weather = conditions.weather();
        if (weather == WeatherMode.CLEAR && (world.hasStorm() || world.isThundering())) {
            return "This recipe requires clear weather.";
        }
        if (weather == WeatherMode.RAIN && !world.hasStorm()) {
            return "This recipe requires rain.";
        }
        if (weather == WeatherMode.THUNDER && !world.isThundering()) {
            return "This recipe requires thunder.";
        }
        if (player.getLevel() < conditions.minimumExperienceLevel()) {
            return "This recipe requires level " + conditions.minimumExperienceLevel() + ".";
        }
        return null;
    }
}
