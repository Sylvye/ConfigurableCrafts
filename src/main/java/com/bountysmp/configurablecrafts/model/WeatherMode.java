package com.bountysmp.configurablecrafts.model;

public enum WeatherMode {
    ANY("Any Weather"),
    CLEAR("Clear"),
    RAIN("Rain"),
    THUNDER("Thunder");

    private final String displayName;

    WeatherMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public WeatherMode next() {
        WeatherMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
