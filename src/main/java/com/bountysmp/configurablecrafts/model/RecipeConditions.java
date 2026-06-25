package com.bountysmp.configurablecrafts.model;

import java.util.LinkedHashSet;
import java.util.Set;

public final class RecipeConditions {
    private final Set<String> dimensions = new LinkedHashSet<>();
    private final Set<String> biomes = new LinkedHashSet<>();
    private WeatherMode weather = WeatherMode.ANY;
    private int minimumExperienceLevel = 0;

    public Set<String> dimensions() {
        return dimensions;
    }

    public Set<String> biomes() {
        return biomes;
    }

    public WeatherMode weather() {
        return weather;
    }

    public void setWeather(WeatherMode weather) {
        this.weather = weather == null ? WeatherMode.ANY : weather;
    }

    public int minimumExperienceLevel() {
        return minimumExperienceLevel;
    }

    public void setMinimumExperienceLevel(int minimumExperienceLevel) {
        this.minimumExperienceLevel = Math.max(0, minimumExperienceLevel);
    }

    public RecipeConditions copy() {
        RecipeConditions copy = new RecipeConditions();
        copy.dimensions.addAll(dimensions);
        copy.biomes.addAll(biomes);
        copy.weather = weather;
        copy.minimumExperienceLevel = minimumExperienceLevel;
        return copy;
    }
}
