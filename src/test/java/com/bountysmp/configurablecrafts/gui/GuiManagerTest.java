package com.bountysmp.configurablecrafts.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bountysmp.configurablecrafts.BukkitTest;
import com.bountysmp.configurablecrafts.crafting.ManagedRecipeRegistry;
import com.bountysmp.configurablecrafts.model.IngredientSpec;
import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.RecipeKind;
import com.bountysmp.configurablecrafts.storage.RecipeRepository;
import java.io.File;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class GuiManagerTest extends BukkitTest {
    @TempDir
    File tempDir;

    @Test
    void collectToCursorIsCancelledWhileConfigurableCraftsGuiIsOpen() {
        Fixture fixture = newFixture();
        PlayerMock player = MockBukkit.getMock().addPlayer();
        fixture.guiManager.openMain(player, 0, "");

        InventoryClickEvent event = click(player, 54, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR);
        fixture.guiManager.onClick(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void readOnlyRecipeViewerCancelsBottomInventoryClicks() {
        Fixture fixture = newFixture();
        fixture.registry.upsert(sampleRecipe());
        PlayerMock player = MockBukkit.getMock().addPlayer();
        player.setOp(false);
        fixture.guiManager.openMain(player, 0, "");
        fixture.guiManager.onClick(click(player, 18, ClickType.LEFT, InventoryAction.PICKUP_ALL));

        InventoryClickEvent bottomClick = click(player, 54, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        fixture.guiManager.onClick(bottomClick);

        assertTrue(bottomClick.isCancelled());
    }

    @Test
    void editableAdminEditorAllowsNormalBottomInventoryClicks() {
        Fixture fixture = newFixture();
        PlayerMock player = MockBukkit.getMock().addPlayer();
        player.addAttachment(fixture.plugin, "configurablecrafts.admin", true);
        fixture.guiManager.openMain(player, 0, "");
        fixture.guiManager.onClick(click(player, 10, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        fixture.guiManager.onClick(click(player, 11, ClickType.LEFT, InventoryAction.PICKUP_ALL));

        InventoryClickEvent bottomClick = click(player, 54, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        fixture.guiManager.onClick(bottomClick);

        assertFalse(bottomClick.isCancelled());
    }

    private Fixture newFixture() {
        PluginMock plugin = MockBukkit.createMockPlugin();
        ManagedRecipeRegistry registry = new ManagedRecipeRegistry(plugin, new RecipeRepository(new File(tempDir, "recipes.yml")));
        ChatPromptManager prompts = new ChatPromptManager(plugin);
        return new Fixture(plugin, registry, new GuiManager(plugin, registry, prompts));
    }

    private ManagedRecipe sampleRecipe() {
        ManagedRecipe recipe = new ManagedRecipe("viewer_test", RecipeKind.SHAPED);
        recipe.setResult(new ItemStack(Material.DIAMOND));
        recipe.setIngredient(0, IngredientSpec.fromSample(new ItemStack(Material.STICK)));
        return recipe;
    }

    private InventoryClickEvent click(PlayerMock player, int rawSlot, ClickType click, InventoryAction action) {
        return new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, rawSlot, click, action);
    }

    private record Fixture(PluginMock plugin, ManagedRecipeRegistry registry, GuiManager guiManager) {
    }
}
