package com.bountysmp.configurablecrafts.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bountysmp.configurablecrafts.BukkitTest;
import com.bountysmp.configurablecrafts.model.IngredientSpec;
import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.RecipeKind;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.io.File;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.DragType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import com.bountysmp.configurablecrafts.storage.RecipeRepository;
import io.papermc.paper.potion.PotionMix;
import org.bukkit.NamespacedKey;

class BrewingRecipeServiceTest extends BukkitTest {
    @TempDir
    File tempDir;

    @Test
    void matchingBrewTimeReturnsCustomRecipeTime() {
        ManagedRecipe recipe = brewingRecipe("brew", Material.DIAMOND, Material.APPLE, 80);
        BrewerInventory inventory = brewerInventory();
        inventory.setItem(0, new ItemStack(Material.DIAMOND));

        OptionalInt brewTime = BrewingRecipeService.matchingBrewTime(List.of(recipe), inventory, new ItemStack(Material.APPLE));

        assertTrue(brewTime.isPresent());
        assertEquals(80, brewTime.getAsInt());
    }

    @Test
    void matchingBrewTimeAllowsMixedRecipesWithSameReagentAndTime() {
        ManagedRecipe first = brewingRecipe("first", Material.DIAMOND, Material.APPLE, 80);
        ManagedRecipe second = brewingRecipe("second", Material.EMERALD, Material.APPLE, 80);
        BrewerInventory inventory = brewerInventory();
        inventory.setItem(0, new ItemStack(Material.DIAMOND));
        inventory.setItem(1, new ItemStack(Material.EMERALD));

        OptionalInt brewTime = BrewingRecipeService.matchingBrewTime(List.of(first, second), inventory, new ItemStack(Material.APPLE));

        assertTrue(brewTime.isPresent());
        assertEquals(80, brewTime.getAsInt());
    }

    @Test
    void matchingBrewTimeReturnsEmptyWhenNoCustomRecipeMatches() {
        ManagedRecipe recipe = brewingRecipe("brew", Material.DIAMOND, Material.APPLE, 80);
        BrewerInventory inventory = brewerInventory();
        inventory.setItem(0, new ItemStack(Material.EMERALD));

        OptionalInt brewTime = BrewingRecipeService.matchingBrewTime(List.of(recipe), inventory, new ItemStack(Material.APPLE));

        assertTrue(brewTime.isEmpty());
    }

    @Test
    void directIngredientPlacementRejectsObsoleteVanillaRoute() {
        TestContext context = turtleOverrideContext();
        context.inventory.setItem(0, potion(PotionType.AWKWARD));
        context.player.setItemOnCursor(new ItemStack(Material.TURTLE_HELMET));
        InventoryClickEvent event = new InventoryClickEvent(context.player.getOpenInventory(),
            InventoryType.SlotType.CRAFTING, 3, ClickType.LEFT, InventoryAction.PLACE_ALL);

        context.service.onInventoryClick(event);

        assertTrue(event.isCancelled());
        assertEquals(Material.TURTLE_HELMET, context.player.getItemOnCursor().getType());
        assertTrue(context.inventory.getItem(3) == null);
    }

    @Test
    void potionPlacementRejectsWhenObsoleteIngredientWasInsertedFirst() {
        TestContext context = turtleOverrideContext();
        context.inventory.setIngredient(new ItemStack(Material.TURTLE_HELMET));
        context.player.setItemOnCursor(potion(PotionType.AWKWARD));
        InventoryClickEvent event = new InventoryClickEvent(context.player.getOpenInventory(),
            InventoryType.SlotType.CRAFTING, 0, ClickType.LEFT, InventoryAction.PLACE_ALL);

        context.service.onInventoryClick(event);

        assertTrue(event.isCancelled());
        assertEquals(PotionType.AWKWARD, ((PotionMeta) context.player.getItemOnCursor().getItemMeta()).getBasePotionType());
        assertTrue(context.inventory.getItem(0) == null);
    }

    @Test
    void emptyStandAcceptsIngredientAndReplacementRouteIsAccepted() {
        TestContext empty = turtleOverrideContext();
        empty.player.setItemOnCursor(new ItemStack(Material.TURTLE_HELMET));
        InventoryClickEvent emptyEvent = new InventoryClickEvent(empty.player.getOpenInventory(),
            InventoryType.SlotType.CRAFTING, 3, ClickType.LEFT, InventoryAction.PLACE_ALL);
        empty.service.onInventoryClick(emptyEvent);
        assertFalse(emptyEvent.isCancelled());

        TestContext valid = turtleOverrideContext();
        valid.inventory.setItem(0, potion(PotionType.AWKWARD));
        valid.player.setItemOnCursor(new ItemStack(Material.TURTLE_SCUTE));
        InventoryClickEvent validEvent = new InventoryClickEvent(valid.player.getOpenInventory(),
            InventoryType.SlotType.CRAFTING, 3, ClickType.LEFT, InventoryAction.PLACE_ALL);
        valid.service.onInventoryClick(validEvent);
        assertFalse(validEvent.isCancelled());
    }

    @Test
    void dragAndHopperInsertionAreRejectedAtomically() {
        TestContext drag = turtleOverrideContext();
        drag.inventory.setItem(0, potion(PotionType.AWKWARD));
        ItemStack helmet = new ItemStack(Material.TURTLE_HELMET);
        InventoryDragEvent dragEvent = new InventoryDragEvent(drag.player.getOpenInventory(), helmet, helmet,
            false, Map.of(3, helmet));
        drag.service.onInventoryDrag(dragEvent);
        assertTrue(dragEvent.isCancelled());

        TestContext hopper = turtleOverrideContext();
        hopper.inventory.setItem(0, potion(PotionType.AWKWARD));
        Inventory source = Bukkit.createInventory(null, InventoryType.HOPPER);
        source.setItem(0, helmet);
        InventoryMoveItemEvent move = new InventoryMoveItemEvent(source, helmet, hopper.inventory, true);
        hopper.service.onInventoryMove(move);
        assertTrue(move.isCancelled());
        assertEquals(Material.TURTLE_HELMET, source.getItem(0).getType());
        assertTrue(hopper.inventory.getItem(3) == null);
    }

    @Test
    void rightClickHotbarAndOffhandPlacementsAreRejected() {
        for (InventoryAction action : List.of(InventoryAction.PLACE_ONE, InventoryAction.HOTBAR_SWAP)) {
            TestContext context = turtleOverrideContext();
            context.inventory.setItem(0, potion(PotionType.AWKWARD));
            if (action == InventoryAction.PLACE_ONE) {
                context.player.setItemOnCursor(new ItemStack(Material.TURTLE_HELMET));
            } else {
                context.player.getInventory().setItem(2, new ItemStack(Material.TURTLE_HELMET));
            }
            InventoryClickEvent event = new InventoryClickEvent(context.player.getOpenInventory(),
                InventoryType.SlotType.CRAFTING, 3,
                action == InventoryAction.PLACE_ONE ? ClickType.RIGHT : ClickType.NUMBER_KEY, action,
                action == InventoryAction.PLACE_ONE ? -1 : 2);
            context.service.onInventoryClick(event);
            assertTrue(event.isCancelled());
        }

        TestContext offhand = turtleOverrideContext();
        offhand.inventory.setItem(0, potion(PotionType.AWKWARD));
        offhand.player.getInventory().setItemInOffHand(new ItemStack(Material.TURTLE_HELMET));
        InventoryClickEvent event = new InventoryClickEvent(offhand.player.getOpenInventory(),
            InventoryType.SlotType.CRAFTING, 3, ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP, -1);
        offhand.service.onInventoryClick(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void shiftClickIsRejectedAndMixedValidBrewingIsAllowed() {
        TestContext shifted = turtleOverrideContext();
        shifted.inventory.setItem(0, potion(PotionType.AWKWARD));
        int rawPlayerSlot = shifted.player.getOpenInventory().getTopInventory().getSize();
        InventoryClickEvent shift = new InventoryClickEvent(shifted.player.getOpenInventory(),
            InventoryType.SlotType.CONTAINER, rawPlayerSlot, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        shift.setCurrentItem(new ItemStack(Material.TURTLE_HELMET));
        shifted.service.onInventoryClick(shift);
        assertTrue(shift.isCancelled());

        TestContext mixed = weaknessOverrideContext();
        mixed.inventory.setItem(0, potion(PotionType.WATER));
        mixed.inventory.setItem(1, potion(PotionType.HEALING));
        mixed.player.setItemOnCursor(new ItemStack(Material.FERMENTED_SPIDER_EYE));
        InventoryClickEvent mixedEvent = new InventoryClickEvent(mixed.player.getOpenInventory(),
            InventoryType.SlotType.CRAFTING, 3, ClickType.LEFT, InventoryAction.PLACE_ALL);
        mixed.service.onInventoryClick(mixedEvent);
        assertFalse(mixedEvent.isCancelled());
    }

    @Test
    void disabledOverrideStillRejectsItsVanillaRoute() {
        TestContext context = turtleOverrideContext(false);
        context.inventory.setItem(0, potion(PotionType.AWKWARD));
        context.player.setItemOnCursor(new ItemStack(Material.TURTLE_HELMET));
        InventoryClickEvent event = new InventoryClickEvent(context.player.getOpenInventory(),
            InventoryType.SlotType.CRAFTING, 3, ClickType.LEFT, InventoryAction.PLACE_ALL);
        context.service.onInventoryClick(event);
        assertTrue(event.isCancelled());
    }

    private TestContext turtleOverrideContext() {
        return turtleOverrideContext(true);
    }

    private TestContext turtleOverrideContext(boolean enabled) {
        ManagedRecipeRegistry registry = new ManagedRecipeRegistry(MockBukkit.createMockPlugin(),
            new RecipeRepository(new File(tempDir, "recipes.yml")), new NoOpPotionMixes());
        ManagedRecipe override = VanillaBrewingCatalog.bySourceKey("minecraft:brewing/turtle_master").toManagedRecipe();
        override.setIngredient(1, IngredientSpec.fromSample(new ItemStack(Material.TURTLE_SCUTE)));
        override.setEnabled(enabled);
        registry.upsert(override);
        return context(registry);
    }

    private TestContext weaknessOverrideContext() {
        ManagedRecipeRegistry registry = new ManagedRecipeRegistry(MockBukkit.createMockPlugin(),
            new RecipeRepository(new File(tempDir, "weakness.yml")), new NoOpPotionMixes());
        ManagedRecipe override = VanillaBrewingCatalog.bySourceKey("minecraft:brewing/weakness").toManagedRecipe();
        override.setIngredient(0, IngredientSpec.fromExactSample(potion(PotionType.STRENGTH)));
        registry.upsert(override);
        return context(registry);
    }

    private TestContext context(ManagedRecipeRegistry registry) {
        BrewingRecipeService service = new BrewingRecipeService(registry);
        BrewerInventory inventory = brewerInventory();
        PlayerMock player = MockBukkit.getMock().addPlayer();
        player.openInventory(inventory);
        return new TestContext(service, inventory, player);
    }

    private ItemStack potion(PotionType type) {
        ItemStack stack = new ItemStack(Material.POTION);
        stack.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(type));
        return stack;
    }

    private ManagedRecipe brewingRecipe(String id, Material input, Material ingredient, int brewTimeTicks) {
        ManagedRecipe recipe = new ManagedRecipe(id, RecipeKind.BREWING);
        recipe.setResult(new ItemStack(Material.POTION));
        recipe.setIngredient(0, IngredientSpec.fromSample(new ItemStack(input)));
        recipe.setIngredient(1, IngredientSpec.fromSample(new ItemStack(ingredient)));
        recipe.setBrewTimeTicks(brewTimeTicks);
        return recipe;
    }

    private BrewerInventory brewerInventory() {
        return (BrewerInventory) Bukkit.createInventory(null, InventoryType.BREWING);
    }

    private record TestContext(BrewingRecipeService service, BrewerInventory inventory, PlayerMock player) {
    }

    private static final class NoOpPotionMixes implements ManagedRecipeRegistry.PotionMixes {
        @Override
        public void add(PotionMix potionMix) {
        }

        @Override
        public void remove(NamespacedKey key) {
        }
    }
}
