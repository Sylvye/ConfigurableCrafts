package com.bountysmp.configurablecrafts.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bountysmp.configurablecrafts.BukkitTest;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class GuiUtilTest extends BukkitTest {
    @Test
    void guiTextIsNotItalic() {
        ItemStack itemStack = GuiUtil.item(Material.PAPER, GuiUtil.Tone.SUCCESS, "Save Recipe", "Writes recipes.yml");

        assertEquals(TextDecoration.State.FALSE, itemStack.getItemMeta().displayName().decoration(TextDecoration.ITALIC));
        assertEquals(TextDecoration.State.FALSE, itemStack.getItemMeta().lore().getFirst().decoration(TextDecoration.ITALIC));
    }
}
