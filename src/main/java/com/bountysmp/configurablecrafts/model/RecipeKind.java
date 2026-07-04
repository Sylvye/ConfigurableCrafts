package com.bountysmp.configurablecrafts.model;

import java.util.Locale;

public enum RecipeKind {
    SHAPED(true, "Shaped Crafting"),
    SHAPELESS(true, "Shapeless Crafting"),
    BREWING(true, "Brewing"),
    SMELTING(true, "Smelting"),
    SMOKING(true, "Smoking"),
    BLASTING(true, "Blasting"),
    CAMPFIRE(true, "Campfire Cooking"),
    STONECUTTING(true, "Stonecutting"),
    SMITHING(true, "Smithing"),
    COOKING(true, "Smoking");

    private final boolean supported;
    private final String displayName;

    RecipeKind(boolean supported, String displayName) {
        this.supported = supported;
        this.displayName = displayName;
    }

    public boolean isSupported() {
        return supported;
    }

    public boolean isCraftingTable() {
        return this == SHAPED || this == SHAPELESS;
    }

    public boolean isCooking() {
        return this == SMELTING || this == SMOKING || this == COOKING || this == BLASTING || this == CAMPFIRE;
    }

    public boolean isBlockDriven() {
        return isCooking() || this == BREWING;
    }

    public RecipeKind canonical() {
        return this == COOKING ? SMOKING : this;
    }

    public String displayName() {
        return displayName;
    }

    public static RecipeKind parse(String value, RecipeKind fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            RecipeKind parsed = RecipeKind.valueOf(value.trim().toUpperCase(Locale.ROOT));
            return parsed.canonical();
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
