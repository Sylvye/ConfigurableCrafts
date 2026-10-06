package com.bountysmp.configurablecrafts.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bountysmp.configurablecrafts.BukkitTest;
import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.RecipeKind;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

class GuiUtilTest extends BukkitTest {
    /** Supply Paper's effective name at the mock boundary; test the GUI's actual cloning/lore. */
    private static final class NamedStack extends ItemStack {
        NamedStack(Material material) { super(material); }
        NamedStack(ItemStack source) {
            super(source.getType(), source.getAmount());
            setItemMeta(source.getItemMeta());
        }
        @Override public Component effectiveName() {
            var meta = getItemMeta();
            if (meta.hasDisplayName()) return meta.displayName();
            if (meta.hasItemName()) return meta.itemName();
            return Component.translatable(getType().translationKey());
        }
        @Override public NamedStack clone() { return new NamedStack(this); }
    }
    @Test
    void recipePreviewPreservesEnchantedBookLoreAboveRecipeInfo() {
        ItemStack book = new NamedStack(Material.ENCHANTED_BOOK);
        Component originalLore = Component.text("A rare enchantment", NamedTextColor.GOLD)
            .decorate(TextDecoration.BOLD);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.lore(List.of(originalLore));
        meta.addStoredEnchant(Enchantment.SHARPNESS, 3, false);
        book.setItemMeta(meta);
        ManagedRecipe recipe = new ManagedRecipe("book", RecipeKind.SHAPELESS);
        recipe.setResult(book);

        ItemStack preview = new GuiManager(null, null, null).recipeIcon(recipe, true, false);
        recipe.setEnabled(false);
        ItemStack barrier = new GuiManager(null, null, null).recipeIcon(recipe, true, true);
        List<Component> lines = preview.getItemMeta().lore();

        assertEquals(originalLore, lines.get(0));
        assertEquals(3, ((EnchantmentStorageMeta) preview.getItemMeta()).getStoredEnchantLevel(Enchantment.SHARPNESS));
        assertTrue(!preview.getItemMeta().hasItemFlag(ItemFlag.HIDE_STORED_ENCHANTS));
        assertTrue(!preview.getItemMeta().hasItemFlag(ItemFlag.HIDE_ENCHANTS));
        assertEquals(Enchantment.SHARPNESS.displayName(3), barrier.getItemMeta().lore().get(0));
        assertEquals(originalLore, barrier.getItemMeta().lore().get(1));
        assertEquals(Component.empty(), barrier.getItemMeta().lore().get(2));
        assertEquals("Recipe info", plain(barrier.getItemMeta().lore().get(3)));
        assertEquals(Component.empty(), lines.get(1));
        assertEquals("Recipe info", plain(lines.get(2)));
        assertTrue(lines.stream().map(GuiUtilTest::plain).anyMatch("Shapeless Crafting"::equals));
        assertTrue(lines.stream().map(GuiUtilTest::plain).anyMatch("Custom recipe"::equals));
        assertTrue(lines.stream().map(GuiUtilTest::plain).anyMatch(line -> line.contains("Left-click edit")));
    }

    @Test
    void recipePreviewWithoutLoreAndDisabledBarrierRetainRecipeInfo() {
        ManagedRecipe recipe = new ManagedRecipe("disabled", RecipeKind.SHAPED);
        recipe.setResult(new NamedStack(Material.DIAMOND));
        recipe.setEnabled(false);
        GuiManager manager = new GuiManager(null, null, null);

        ItemStack normal = manager.recipeIcon(recipe, false, false);
        ItemStack barrier = manager.recipeIcon(recipe, false, true);

        assertEquals(Material.DIAMOND, normal.getType());
        assertEquals(Material.BARRIER, barrier.getType());
        assertEquals(normal.getItemMeta().lore(), barrier.getItemMeta().lore());
        assertEquals("Recipe info", plain(barrier.getItemMeta().lore().getFirst()));
        assertTrue(barrier.getItemMeta().lore().stream().map(GuiUtilTest::plain).anyMatch("Disabled - not craftable"::equals));
        assertTrue(barrier.getItemMeta().lore().stream().map(GuiUtilTest::plain).anyMatch("Left-click view."::equals));
        assertTrue(!normal.getItemMeta().hasItemFlag(ItemFlag.HIDE_ENCHANTS));
    }

    @Test
    void temporarilyUnavailableRecipeFlashesAndReturnsToNormalWhenAvailable() {
        ManagedRecipe recipe = new ManagedRecipe("limited", RecipeKind.SHAPED);
        recipe.setResult(new NamedStack(Material.DIAMOND));
        GuiManager manager = new GuiManager(null, null, null);
        String failure = "This recipe requires rain.";

        ItemStack normal = manager.recipeIcon(recipe, false, false, failure);
        ItemStack barrier = manager.recipeIcon(recipe, false, true, failure);
        assertEquals(Material.DIAMOND, normal.getType());
        assertEquals(Material.BARRIER, barrier.getType());
        assertEquals(normal.getItemMeta().displayName(), barrier.getItemMeta().displayName());
        assertEquals(normal.getItemMeta().lore(), barrier.getItemMeta().lore());
        assertTrue(barrier.getItemMeta().lore().stream().map(GuiUtilTest::plain).anyMatch(failure::equals));
        assertEquals(Material.DIAMOND, manager.recipeIcon(recipe, false, true, null).getType());
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
    @Test
    void guiTextIsNotItalic() {
        ItemStack itemStack = GuiUtil.item(Material.PAPER, GuiUtil.Tone.SUCCESS, "Save Recipe", "Writes recipes.yml");

        assertEquals(TextDecoration.State.FALSE, itemStack.getItemMeta().displayName().decoration(TextDecoration.ITALIC));
        assertEquals(TextDecoration.State.FALSE, itemStack.getItemMeta().lore().getFirst().decoration(TextDecoration.ITALIC));
    }

    @Test
    void namedCloneCanPreserveExactComponentName() {
        ItemStack source = new NamedStack(Material.DIAMOND_SWORD);
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
