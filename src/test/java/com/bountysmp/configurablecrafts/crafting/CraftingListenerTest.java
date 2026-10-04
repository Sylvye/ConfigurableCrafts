package com.bountysmp.configurablecrafts.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bountysmp.configurablecrafts.BukkitTest;
import com.bountysmp.configurablecrafts.model.IngredientSpec;
import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.RecipeKind;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.junit.jupiter.api.Test;

class CraftingListenerTest extends BukkitTest {
    @Test
    void shiftCraftCountUsesIngredientAndInventoryCaps() {
        ItemStack[] matrix = new ItemStack[9];
        matrix[0] = new ItemStack(Material.STICK, 8);
        matrix[1] = new ItemStack(Material.DIAMOND, 3);
        Inventory inventory = Bukkit.createInventory(null, 9);

        assertEquals(3, CraftingListener.shiftCraftCount(matrix, inventory, new ItemStack(Material.EMERALD)));
    }

    @Test
    void shiftCraftCountCapsByDestinationCapacity() {
        ItemStack[] matrix = new ItemStack[9];
        matrix[0] = new ItemStack(Material.STICK, 10);
        Inventory inventory = Bukkit.createInventory(null, 9);
        for (int i = 1; i < inventory.getSize(); i++) {
            inventory.setItem(i, new ItemStack(Material.DIRT, 64));
        }
        inventory.setItem(0, new ItemStack(Material.EMERALD, 63));

        assertEquals(1, CraftingListener.shiftCraftCount(matrix, inventory, new ItemStack(Material.EMERALD)));
    }

    @Test
    void matchingIngredientsMapsShapelessRemaindersToInputSlots() {
        ManagedRecipe recipe = new ManagedRecipe("remainders", RecipeKind.SHAPELESS);
        IngredientSpec diamond = IngredientSpec.fromSample(new ItemStack(Material.DIAMOND));
        diamond.setRemainder(new ItemStack(Material.BOWL));
        recipe.setIngredient(0, diamond);
        IngredientSpec stick = IngredientSpec.fromSample(new ItemStack(Material.STICK));
        recipe.setIngredient(1, stick);
        ItemStack[] matrix = new ItemStack[9];
        matrix[2] = new ItemStack(Material.STICK);
        matrix[7] = new ItemStack(Material.DIAMOND);

        IngredientSpec[] matched = RecipePattern.matchingIngredients(recipe, matrix);

        assertEquals(Material.STICK, matched[2].sample().getType());
        assertEquals(Material.DIAMOND, matched[7].sample().getType());
    }

    @Test
    void matchingIngredientsMapsOffsetShapedRecipeToInputSlots() {
        ManagedRecipe recipe = new ManagedRecipe("offset", RecipeKind.SHAPED);
        IngredientSpec diamond = IngredientSpec.fromSample(new ItemStack(Material.DIAMOND));
        recipe.setIngredient(0, diamond);
        IngredientSpec stick = IngredientSpec.fromSample(new ItemStack(Material.STICK));
        recipe.setIngredient(3, stick);
        ItemStack[] matrix = new ItemStack[9];
        matrix[4] = new ItemStack(Material.DIAMOND);
        matrix[7] = new ItemStack(Material.STICK);

        IngredientSpec[] matched = RecipePattern.matchingIngredients(recipe, matrix);

        assertEquals(Material.DIAMOND, matched[4].sample().getType());
        assertEquals(Material.STICK, matched[7].sample().getType());
    }

    @Test
    void configuredRemainderEquivalentToNativeIsNotAddedAgain() {
        ManagedRecipe recipe = new ManagedRecipe("milk", RecipeKind.SHAPELESS);
        IngredientSpec milk = IngredientSpec.fromSample(new ItemStack(Material.MILK_BUCKET));
        milk.setRemainder(new ItemStack(Material.BUCKET));
        recipe.setIngredient(0, milk);
        recipe.setResult(new ItemStack(Material.CAKE));
        ItemStack[] matrix = new ItemStack[9];
        matrix[0] = new ItemStack(Material.MILK_BUCKET);
        // MockBukkit does not expose native crafting remainders; use the equivalent component.
        matrix[0].setData(io.papermc.paper.datacomponent.DataComponentTypes.USE_REMAINDER,
            io.papermc.paper.datacomponent.item.UseRemainder.useRemainder(new ItemStack(Material.BUCKET)));
        Inventory inventory = Bukkit.createInventory(null, 9);

        assertEquals(0, CraftingListener.configuredRemainders(recipe, matrix, inventory, 1).size());
    }

    @Test
    void remainderUsesGridForFinalCraftAndInventoryForShiftCraftOverflow() {
        var player = MockBukkit.getMock().addPlayer();
        var crafting = (org.bukkit.inventory.CraftingInventory) Bukkit.createInventory(null, InventoryType.WORKBENCH);
        CraftingListener.RemainderPlacement placement = new CraftingListener.RemainderPlacement(
            0, new ItemStack(Material.BOWL), null, 0, new ItemStack(Material.CAKE), 3,
            new ItemStack(Material.STONE, 3));

        CraftingListener.applyRemainders(crafting, player, java.util.List.of(placement));

        assertEquals(Material.BOWL, crafting.getMatrix()[0].getType());
        assertEquals(1, crafting.getMatrix()[0].getAmount());
        assertEquals(2, player.getInventory().all(Material.BOWL).values().stream().mapToInt(ItemStack::getAmount).sum());
    }

    @Test
    void configuredRemainderReplacesNativeRemainderInsteadOfDuplicatingIt() {
        var player = MockBukkit.getMock().addPlayer();
        var crafting = (org.bukkit.inventory.CraftingInventory) Bukkit.createInventory(null, InventoryType.WORKBENCH);
        ItemStack[] postCraft = new ItemStack[9];
        postCraft[0] = new ItemStack(Material.BUCKET);
        crafting.setMatrix(postCraft);
        CraftingListener.RemainderPlacement placement = new CraftingListener.RemainderPlacement(
            0, new ItemStack(Material.BOWL), new ItemStack(Material.BUCKET), 0, new ItemStack(Material.CAKE), 1,
            new ItemStack(Material.MILK_BUCKET));

        CraftingListener.applyRemainders(crafting, player, java.util.List.of(placement));

        assertEquals(Material.BOWL, crafting.getMatrix()[0].getType());
        assertEquals(0, player.getInventory().all(Material.BUCKET).size());
    }

    @Test
    void occupiedGridAndFullInventoryDoNotOverwriteOrDuplicateRemainder() {
        var player = MockBukkit.getMock().addPlayer();
        var crafting = (org.bukkit.inventory.CraftingInventory) Bukkit.createInventory(null, InventoryType.WORKBENCH);
        ItemStack[] postCraft = new ItemStack[9];
        postCraft[0] = new ItemStack(Material.DIAMOND, 2);
        crafting.setMatrix(postCraft);
        for (int slot = 0; slot < player.getInventory().getStorageContents().length; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
        }
        CraftingListener.RemainderPlacement placement = new CraftingListener.RemainderPlacement(
            0, new ItemStack(Material.BOWL), null, 0, new ItemStack(Material.CAKE), 1,
            new ItemStack(Material.DIAMOND, 3));

        CraftingListener.applyRemainders(crafting, player, java.util.List.of(placement));

        assertEquals(Material.DIAMOND, crafting.getMatrix()[0].getType());
        assertEquals(0, player.getInventory().all(Material.BOWL).size());
    }

    @Test
    void unchangedInputDoesNotProduceConfiguredRemainder() {
        var player = MockBukkit.getMock().addPlayer();
        var crafting = (org.bukkit.inventory.CraftingInventory) Bukkit.createInventory(null, InventoryType.WORKBENCH);
        crafting.setMatrix(new ItemStack[] {new ItemStack(Material.DIAMOND, 3), null, null, null, null, null, null, null, null});
        CraftingListener.RemainderPlacement placement = new CraftingListener.RemainderPlacement(
            0, new ItemStack(Material.BOWL), null, 0, new ItemStack(Material.CAKE), 1,
            new ItemStack(Material.DIAMOND, 3));

        CraftingListener.applyRemainders(crafting, player, java.util.List.of(placement));

        assertEquals(Material.DIAMOND, crafting.getMatrix()[0].getType());
        assertEquals(0, player.getInventory().all(Material.BOWL).size());
    }

    @Test
    void missingNativeRemainderDoesNotProduceConfiguredRemainder() {
        var player = MockBukkit.getMock().addPlayer();
        var crafting = (org.bukkit.inventory.CraftingInventory) Bukkit.createInventory(null, InventoryType.WORKBENCH);
        CraftingListener.RemainderPlacement placement = new CraftingListener.RemainderPlacement(
            0, new ItemStack(Material.BOWL), new ItemStack(Material.BUCKET), 0,
            new ItemStack(Material.CAKE), 1, new ItemStack(Material.MILK_BUCKET));

        CraftingListener.applyRemainders(crafting, player, java.util.List.of(placement));

        assertEquals(0, player.getInventory().all(Material.BOWL).size());
    }
}
