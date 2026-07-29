package com.bountysmp.configurablecrafts.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bountysmp.configurablecrafts.BukkitTest;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

class GuiUtilTest extends BukkitTest {
    @Test
    void guiTextIsNotItalic() {
        ItemStack itemStack = GuiUtil.item(Material.PAPER, GuiUtil.Tone.SUCCESS, "Save Recipe", "Writes recipes.yml");

        assertEquals(TextDecoration.State.FALSE, itemStack.getItemMeta().displayName().decoration(TextDecoration.ITALIC));
        assertEquals(TextDecoration.State.FALSE, itemStack.getItemMeta().lore().getFirst().decoration(TextDecoration.ITALIC));
    }

    @Test
    void namedCloneCanPreserveExactComponentName() {
        ItemStack source = new ItemStack(Material.DIAMOND_SWORD);
        Component heldName = Component.text("Royal Blade", NamedTextColor.GOLD)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false);
        ItemMeta meta = source.getItemMeta();
        meta.displayName(heldName);
        source.setItemMeta(meta);

        ItemStack clone = GuiUtil.namedClone(source, source.effectiveName(), List.of("Recipe"));

        assertEquals(source.effectiveName(), clone.getItemMeta().displayName());
    }

    @Test
    void namedCloneMakesUnformattedComponentNamesNonItalic() {
        ItemStack clone = GuiUtil.namedClone(new ItemStack(Material.DIAMOND), Component.text("Diamond"), List.of());

        assertEquals(TextDecoration.State.FALSE, clone.getItemMeta().displayName().decoration(TextDecoration.ITALIC));
    }

    @Test
    void namedClonePreservesExplicitItalicComponentNames() {
        Component italicName = Component.text("Ancient Relic").decorate(TextDecoration.ITALIC);

        ItemStack clone = GuiUtil.namedClone(new ItemStack(Material.DIAMOND), italicName, List.of());

        assertEquals(TextDecoration.State.TRUE, clone.getItemMeta().displayName().decoration(TextDecoration.ITALIC));
    }
}
