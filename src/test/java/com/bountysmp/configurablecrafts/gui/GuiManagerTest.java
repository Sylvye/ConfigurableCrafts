package com.bountysmp.configurablecrafts.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bountysmp.configurablecrafts.BukkitTest;
import com.bountysmp.configurablecrafts.crafting.ManagedRecipeRegistry;
import com.bountysmp.configurablecrafts.model.IngredientSpec;
import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.RecipeKind;
import com.bountysmp.configurablecrafts.storage.RecipeRepository;
import java.io.File;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
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

    @Test
    void rightClickEmptyIngredientSlotRequestsTag() {
        Fixture fixture = newFixture();
        PlayerMock player = MockBukkit.getMock().addPlayer();
        player.addAttachment(fixture.plugin, "configurablecrafts.admin", true);
        fixture.guiManager.openMain(player, 0, "");
        fixture.guiManager.onClick(click(player, 10, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        fixture.guiManager.onClick(click(player, 11, ClickType.LEFT, InventoryAction.PICKUP_ALL));

        assertEquals(Material.EXPERIENCE_BOTTLE, player.getOpenInventory().getTopInventory().getItem(32).getType());

        fixture.guiManager.onClick(click(player, 10, ClickType.RIGHT, InventoryAction.PICKUP_HALF));

        assertEquals("Type an item tag like minecraft:logs.", PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage()));
    }

    @Test
    void rightClickTagOnlyIngredientSlotClearsTag() {
        Fixture fixture = newFixture();
        MockBukkit.getMock().createMaterialTag(NamespacedKey.minecraft("test_logs"), Tag.REGISTRY_ITEMS, Material.OAK_LOG);
        fixture.registry.upsert(tagRecipe());
        PlayerMock player = MockBukkit.getMock().addPlayer();
        player.addAttachment(fixture.plugin, "configurablecrafts.admin", true);
        fixture.guiManager.openMain(player, 0, "");
        fixture.guiManager.onClick(click(player, 18, ClickType.LEFT, InventoryAction.PICKUP_ALL));

        assertEquals(Material.OAK_LOG, player.getOpenInventory().getTopInventory().getItem(10).getType());

        fixture.guiManager.onClick(click(player, 10, ClickType.RIGHT, InventoryAction.PICKUP_HALF));

        assertEquals(Material.GRAY_STAINED_GLASS_PANE, player.getOpenInventory().getTopInventory().getItem(10).getType());
    }

    @Test
    void selectedTagOnlyIngredientShowsClearSelectionWithRecipeControls() {
        Fixture fixture = newFixture();
        MockBukkit.getMock().createMaterialTag(NamespacedKey.minecraft("test_logs"), Tag.REGISTRY_ITEMS, Material.OAK_LOG);
        PlayerMock player = MockBukkit.getMock().addPlayer();
        player.addAttachment(fixture.plugin, "configurablecrafts.admin", true);
        fixture.guiManager.openMain(player, 0, "");
        fixture.guiManager.onClick(click(player, 10, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        fixture.guiManager.onClick(click(player, 11, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        fixture.guiManager.onClick(click(player, 10, ClickType.RIGHT, InventoryAction.PICKUP_HALF));
        player.nextComponentMessage();
        player.nextComponentMessage();

        player.chat("minecraft:test_logs");
        MockBukkit.getMock().getScheduler().waitAsyncEventsFinished();
        MockBukkit.getMock().getScheduler().performOneTick();

        assertEquals(Material.OAK_LOG, player.getOpenInventory().getTopInventory().getItem(10).getType());
        assertEquals(Material.EXPERIENCE_BOTTLE, player.getOpenInventory().getTopInventory().getItem(32).getType());
        assertEquals(Material.ARROW, player.getOpenInventory().getTopInventory().getItem(41).getType());
    }

    private Fixture newFixture() {
        PluginMock plugin = MockBukkit.createMockPlugin();
        ManagedRecipeRegistry registry = new ManagedRecipeRegistry(plugin, new RecipeRepository(new File(tempDir, "recipes.yml")));
        ChatPromptManager prompts = new ChatPromptManager(plugin);
        MockBukkit.getMock().getPluginManager().registerEvents(prompts, plugin);
        return new Fixture(plugin, registry, new GuiManager(plugin, registry, prompts));
    }

    private ManagedRecipe sampleRecipe() {
        ManagedRecipe recipe = new ManagedRecipe("viewer_test", RecipeKind.SHAPED);
        recipe.setResult(new ItemStack(Material.DIAMOND));
        recipe.setIngredient(0, IngredientSpec.fromSample(new ItemStack(Material.STICK)));
        return recipe;
    }

    private ManagedRecipe tagRecipe() {
        ManagedRecipe recipe = new ManagedRecipe("tag_test", RecipeKind.SHAPED);
        recipe.setResult(new ItemStack(Material.CHEST));
        recipe.setIngredient(0, IngredientSpec.fromTag("minecraft:test_logs"));
        return recipe;
    }

    private InventoryClickEvent click(PlayerMock player, int rawSlot, ClickType click, InventoryAction action) {
        return new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, rawSlot, click, action);
    }

    private record Fixture(PluginMock plugin, ManagedRecipeRegistry registry, GuiManager guiManager) {
    }
}
