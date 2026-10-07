package com.bountysmp.configurablecrafts.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** A plugin-owned output. Called on the server thread; ingredients remain GUI-editable. */
public interface OwnedRecipe {
    boolean matches(ItemStack result);
    ItemStack preview();
    /** Display-only output for this viewer; must not alter stored or crafted results. */
    default ItemStack preview(Player viewer) { return preview().clone(); }
    /** Whether the output remains globally obtainable; separate from per-player validation. */
    default boolean available() { return true; }
    /** Null means permitted. Must not change game state. */
    String failure(Player player);
    /** Create and durably record a unique result, before taking ingredients. */
    ItemStack create(Player player);
    /** Roll back a prepared output when persistence or the permanent allowance fails. */
    default void aborted(Player player, ItemStack result) {}
    void completed(Player player, ItemStack result);
}
