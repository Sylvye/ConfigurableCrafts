package com.bountysmp.configurablecrafts.crafting;

import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.RecipeConditions;
import com.bountysmp.configurablecrafts.model.WeatherMode;
import org.bukkit.Location;
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
        String environmentFailure = locationFailure(recipe, player.getLocation(), world, conditions);
        if (environmentFailure != null) {
            return environmentFailure;
        }
        if (player.getLevel() < conditions.minimumExperienceLevel()) {
            return "This recipe requires level " + conditions.minimumExperienceLevel() + ".";
        }
        return null;
    }

    public static String failureReason(ManagedRecipe recipe, Location location) {
        if (!recipe.enabled()) {
            return "This recipe is disabled.";
        }
        World world = location == null ? null : location.getWorld();
        if (world == null) {
            return "This recipe cannot be used here.";
        }
        return locationFailure(recipe, location, world, recipe.conditions());
    }

    private static String locationFailure(ManagedRecipe recipe, Location location, World world, RecipeConditions conditions) {
        String dimension = world.getKey().toString();
        if (!conditions.dimensions().isEmpty() && !conditions.dimensions().contains(dimension)) {
            return "This recipe cannot be crafted in this dimension.";
        }
        Biome biome = location.getBlock().getBiome();
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
        return null;
    }
}
