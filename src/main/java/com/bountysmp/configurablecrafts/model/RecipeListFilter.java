package com.bountysmp.configurablecrafts.model;

import java.util.Locale;

public enum RecipeListFilter {
    ALL("All"),
    CUSTOM("Custom"),
    DISABLED("Disabled");

    private final String displayName;

    RecipeListFilter(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public RecipeListFilter next() {
        RecipeListFilter[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static RecipeListFilter parse(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ALL;
        }
    }
}
