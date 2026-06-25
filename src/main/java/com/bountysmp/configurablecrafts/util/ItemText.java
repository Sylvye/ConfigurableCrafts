package com.bountysmp.configurablecrafts.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemText {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ItemText() {
    }

    public static String displayName(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return "Empty";
        }
        String custom = customName(itemStack);
        if (custom != null && !custom.isBlank()) {
            return custom;
        }
        return humanize(itemStack.getType().name());
    }

    public static String customName(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (!meta.hasDisplayName()) {
            return null;
        }
        Component name = meta.displayName();
        return name == null ? null : PLAIN.serialize(name);
    }

    public static List<String> lore(ItemStack itemStack) {
        List<String> lines = new ArrayList<>();
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return lines;
        }
        ItemMeta meta = itemStack.getItemMeta();
        List<Component> lore = meta.lore();
        if (lore == null) {
            return lines;
        }
        for (Component line : lore) {
            lines.add(PLAIN.serialize(line));
        }
        return lines;
    }

    public static String humanize(String enumName) {
        String[] parts = enumName.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }
}
