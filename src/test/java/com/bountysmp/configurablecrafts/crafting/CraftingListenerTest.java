package com.bountysmp.configurablecrafts.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bountysmp.configurablecrafts.BukkitTest;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
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
}
