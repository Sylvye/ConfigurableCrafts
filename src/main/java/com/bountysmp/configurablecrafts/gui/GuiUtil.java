package com.bountysmp.configurablecrafts.gui;

import com.bountysmp.configurablecrafts.util.ItemText;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class GuiUtil {
    enum Tone {
        NEUTRAL(NamedTextColor.WHITE),
        INFO(NamedTextColor.AQUA),
        SUCCESS(NamedTextColor.GREEN),
        WARNING(NamedTextColor.YELLOW),
        DANGER(NamedTextColor.RED),
        MUTED(NamedTextColor.GRAY);

        private final NamedTextColor color;

        Tone(NamedTextColor color) {
            this.color = color;
        }
    }

    private GuiUtil() {
    }

    static ItemStack item(Material material, String name, String... lore) {
        return item(material, name, List.of(lore));
    }

    static ItemStack item(Material material, String name, List<String> lore) {
        return item(material, Tone.INFO, name, lore);
    }

    static ItemStack item(Material material, Tone tone, String name, String... lore) {
        return item(material, tone, name, List.of(lore));
    }

    static ItemStack item(Material material, Tone tone, String name, List<String> lore) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(text(name, tone));
        if (!lore.isEmpty()) {
            List<Component> lines = new ArrayList<>(lore.size());
            for (String line : lore) {
                lines.add(lore(line));
            }
            meta.lore(lines);
        }
        meta.addItemFlags(ItemFlag.values());
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    static ItemStack namedClone(ItemStack source, String fallbackName, List<String> lore) {
        return namedClone(source, fallbackName, Tone.INFO, lore);
    }

    static ItemStack namedClone(ItemStack source, String fallbackName, Tone tone, List<String> lore) {
        ItemStack itemStack = source == null || source.getType().isAir() ? item(Material.BARRIER, fallbackName) : source.clone();
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(text(fallbackName == null ? ItemText.displayName(source) : fallbackName, tone));
        List<Component> lines = new ArrayList<>(lore.size());
        for (String line : lore) {
            lines.add(lore(line));
        }
        meta.lore(lines);
        meta.addItemFlags(ItemFlag.values());
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    static ItemStack displayClone(ItemStack source, String role) {
        if (source == null || source.getType().isAir()) {
            return emptySlot();
        }
        List<String> lore = new ArrayList<>();
        lore.add(role);
        lore.add("Display copy. Cannot be moved.");
        return namedClone(source, ItemText.displayName(source), Tone.NEUTRAL, lore);
    }

    static ItemStack filler() {
        return item(Material.BLACK_STAINED_GLASS_PANE, Tone.MUTED, " ");
    }

    static ItemStack emptySlot() {
        return item(Material.GRAY_STAINED_GLASS_PANE, Tone.MUTED, "Empty Slot", "Place an item here.");
    }

    static boolean isEmpty(ItemStack itemStack) {
        return itemStack == null || itemStack.getType().isAir();
    }

    private static Component text(String value, Tone tone) {
        return Component.text(value == null ? "" : value, tone.color).decoration(TextDecoration.ITALIC, false);
    }

    private static Component lore(String value) {
        return Component.text(value == null ? "" : value, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }
}
