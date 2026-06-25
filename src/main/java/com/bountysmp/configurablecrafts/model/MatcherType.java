package com.bountysmp.configurablecrafts.model;

public enum MatcherType {
    MATERIAL("Material"),
    EXACT("Exact"),
    ITEM_NAME("Item Name"),
    LORE_CONTAINS("Lore Contains"),
    TAG("Tag"),
    ENCHANTMENTS("Enchantments");

    private final String displayName;

    MatcherType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
