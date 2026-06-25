package com.bountysmp.configurablecrafts.model;

import java.util.Locale;

public enum RecipeKind {
    SHAPED(true, "Shaped Crafting"),
    SHAPELESS(true, "Shapeless Crafting"),
    BREWING(false, "Brewing"),
    SMELTING(false, "Smelting"),
    COOKING(false, "Cooking"),
    BLASTING(false, "Blasting"),
    SMITHING(false, "Smithing");

    private final boolean supported;
    private final String displayName;

    RecipeKind(boolean supported, String displayName) {
        this.supported = supported;
        this.displayName = displayName;
    }

    public boolean isSupported() {
        return supported;
    }

    public String displayName() {
        return displayName;
    }

    public static RecipeKind parse(String value, RecipeKind fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return RecipeKind.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
