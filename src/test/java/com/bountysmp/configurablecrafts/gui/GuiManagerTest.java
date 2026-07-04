package com.bountysmp.configurablecrafts.gui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bountysmp.configurablecrafts.BukkitTest;
import com.bountysmp.configurablecrafts.crafting.ManagedRecipeRegistry;
import com.bountysmp.configurablecrafts.model.IngredientSpec;
import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.MatcherType;
import com.bountysmp.configurablecrafts.model.RecipeKind;
import com.bountysmp.configurablecrafts.model.RecipeListFilter;
import com.bountysmp.configurablecrafts.storage.RecipeRepository;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
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

    @Test
    void defaultMainMenuShowsDisabledRecipesToViewers() {
        Fixture fixture = newFixture();
        ManagedRecipe recipe = sampleRecipe();
        recipe.setEnabled(false);
        fixture.registry.upsert(recipe);
        PlayerMock player = MockBukkit.getMock().addPlayer();

        fixture.guiManager.openMain(player, 0, "");

        assertEquals(Material.DIAMOND, player.getOpenInventory().getTopInventory().getItem(18).getType());
    }

    @Test
    void hopperFilterCyclesBetweenCustomAndDisabled() {
        Fixture fixture = newFixture();
        ManagedRecipe enabled = sampleRecipe();
        enabled.setResult(new ItemStack(Material.EMERALD));
        fixture.registry.upsert(enabled);
        ManagedRecipe disabled = new ManagedRecipe("disabled", RecipeKind.SHAPED);
        disabled.setEnabled(false);
        disabled.setResult(new ItemStack(Material.DIAMOND));
        disabled.setIngredient(0, IngredientSpec.fromSample(new ItemStack(Material.STICK)));
        fixture.registry.upsert(disabled);
        PlayerMock player = MockBukkit.getMock().addPlayer();
        fixture.guiManager.openMain(player, 0, "");

        fixture.guiManager.onClick(click(player, 16, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        assertEquals(Material.EMERALD, player.getOpenInventory().getTopInventory().getItem(18).getType());

        fixture.guiManager.onClick(click(player, 16, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        assertEquals(Material.DIAMOND, player.getOpenInventory().getTopInventory().getItem(18).getType());
    }

    @Test
    void brewingPlaceholderOpensBrewingEditorLayout() {
        Fixture fixture = newFixture();
        PlayerMock player = MockBukkit.getMock().addPlayer();
        player.addAttachment(fixture.plugin, "configurablecrafts.admin", true);
        fixture.guiManager.openMain(player, 0, "");

        fixture.guiManager.onClick(click(player, 10, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        fixture.guiManager.onClick(click(player, 28, ClickType.LEFT, InventoryAction.PICKUP_ALL));

        assertEquals(Material.GRAY_STAINED_GLASS_PANE, player.getOpenInventory().getTopInventory().getItem(10).getType());
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, player.getOpenInventory().getTopInventory().getItem(11).getType());
        assertEquals(Material.BLACK_STAINED_GLASS_PANE, player.getOpenInventory().getTopInventory().getItem(12).getType());
        assertEquals(Material.CLOCK, player.getOpenInventory().getTopInventory().getItem(43).getType());
    }

    @Test
    void disabledRecipesBlinkBarrierInMainMenu() throws ReflectiveOperationException {
        Fixture fixture = newFixture();
        ManagedRecipe recipe = sampleRecipe();
        recipe.setEnabled(false);
        fixture.registry.upsert(recipe);
        PlayerMock player = MockBukkit.getMock().addPlayer();
        fixture.guiManager.openMain(player, 0, "", RecipeListFilter.DISABLED);

        invokeBlink(fixture.guiManager);

        assertEquals(Material.BARRIER, player.getOpenInventory().getTopInventory().getItem(18).getType());
    }

    @Test
    void staleEditorMenuIsDiscardedWhenTopInventoryChanges() throws ReflectiveOperationException {
        Fixture fixture = newFixture();
        PlayerMock player = MockBukkit.getMock().addPlayer();
        player.addAttachment(fixture.plugin, "configurablecrafts.admin", true);
        fixture.guiManager.openMain(player, 0, "");
        fixture.guiManager.onClick(click(player, 10, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        fixture.guiManager.onClick(click(player, 11, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        player.openInventory(Bukkit.createInventory(player, 54, "Other Inventory"));

        assertDoesNotThrow(() -> fixture.guiManager.onClick(click(player, 1, ClickType.LEFT, InventoryAction.PICKUP_ALL)));
        assertFalse(openMenus(fixture.guiManager).containsKey(player.getUniqueId()));
    }

    @Test
    void staleMainMenuIsDiscardedDuringBlink() throws ReflectiveOperationException {
        Fixture fixture = newFixture();
        ManagedRecipe recipe = sampleRecipe();
        recipe.setEnabled(false);
        fixture.registry.upsert(recipe);
        PlayerMock player = MockBukkit.getMock().addPlayer();
        fixture.guiManager.openMain(player, 0, "", RecipeListFilter.DISABLED);
        player.openInventory(Bukkit.createInventory(player, 54, "Other Inventory"));

        invokeBlink(fixture.guiManager);

        assertFalse(openMenus(fixture.guiManager).containsKey(player.getUniqueId()));
    }

    @Test
    void newlyPlacedIngredientDefaultsToExactMatcher() {
        EditorSession session = new EditorSession(new ManagedRecipe("new_exact", RecipeKind.SHAPED), false);

        session.setIngredient(0, new ItemStack(Material.STICK), true);

        assertTrue(session.ingredientSpec(0).hasMatcher(MatcherType.EXACT));
        assertFalse(session.ingredientSpec(0).hasMatcher(MatcherType.MATERIAL));
    }

    @Test
    void existingMaterialIngredientRemainsMaterialWhenUnchanged() {
        ManagedRecipe recipe = new ManagedRecipe("existing_material", RecipeKind.SHAPED);
        recipe.setIngredient(0, IngredientSpec.fromSample(new ItemStack(Material.STICK)));
        EditorSession session = new EditorSession(recipe, false);

        session.applyItemsToRecipe();

        assertTrue(session.recipe().ingredient(0).hasMatcher(MatcherType.MATERIAL));
        assertFalse(session.recipe().ingredient(0).hasMatcher(MatcherType.EXACT));
    }

    @Test
    void replacingExistingMaterialIngredientDefaultsReplacementToExact() {
        ManagedRecipe recipe = new ManagedRecipe("replace_material", RecipeKind.SHAPED);
        recipe.setIngredient(0, IngredientSpec.fromSample(new ItemStack(Material.STICK)));
        EditorSession session = new EditorSession(recipe, false);

        session.setIngredient(0, new ItemStack(Material.DIAMOND), true);

        assertTrue(session.ingredientSpec(0).hasMatcher(MatcherType.EXACT));
        assertFalse(session.ingredientSpec(0).hasMatcher(MatcherType.MATERIAL));
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

    private void invokeBlink(GuiManager guiManager) throws ReflectiveOperationException {
        Method method = GuiManager.class.getDeclaredMethod("tickBlink");
        method.setAccessible(true);
        try {
            method.invoke(guiManager);
        } catch (InvocationTargetException exception) {
            throw new AssertionError(exception.getCause());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<java.util.UUID, Object> openMenus(GuiManager guiManager) throws ReflectiveOperationException {
        Field field = GuiManager.class.getDeclaredField("openMenus");
        field.setAccessible(true);
        return (Map<java.util.UUID, Object>) field.get(guiManager);
    }

    private record Fixture(PluginMock plugin, ManagedRecipeRegistry registry, GuiManager guiManager) {
    }
}
