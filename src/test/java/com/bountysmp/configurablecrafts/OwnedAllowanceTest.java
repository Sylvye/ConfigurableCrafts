package com.bountysmp.configurablecrafts;

import static org.junit.jupiter.api.Assertions.*;
import com.bountysmp.configurablecrafts.api.OwnedRecipe;
import com.bountysmp.configurablecrafts.model.*;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class OwnedAllowanceTest extends BukkitTest {
    @Override
    @org.junit.jupiter.api.BeforeEach
    void setUpBukkit() {
        MockBukkit.mock(new org.mockbukkit.mockbukkit.ServerMock() {
            private final org.bukkit.potion.PotionBrewer brewer = (org.bukkit.potion.PotionBrewer)
                java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {org.bukkit.potion.PotionBrewer.class}, (proxy, method, args) -> null);
            @Override public org.bukkit.potion.PotionBrewer getPotionBrewer() { return brewer; }
        });
    }

    private OwnedRecipe owner() {
        return new OwnedRecipe() {
            public boolean matches(ItemStack item) { return item.getType() == Material.DIAMOND; }
            public ItemStack preview() { return new ItemStack(Material.DIAMOND); }
            public String failure(Player player) { return null; }
            public ItemStack create(Player player) { return preview(); }
            public void completed(Player player, ItemStack item) {}
        };
    }

    @Test void claimRequiresTheRegisteredOwnerAndConsumesExactlyOneDurableAllowance() {
        var plugin = MockBukkit.load(ConfigurableCraftsPlugin.class);
        var owner = owner();
        var recipe = new ManagedRecipe("mythic_test", RecipeKind.SHAPED);
        recipe.setIngredient(4, IngredientSpec.fromSample(new ItemStack(Material.STONE)));
        plugin.registry().registerOwned(recipe, owner);
        var key = recipe.managedKey(plugin);
        assertNotNull(org.bukkit.Bukkit.getRecipe(key));
        UUID target = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> plugin.consumeOwnedAllowance(recipe.id(), null, target));
        assertThrows(IllegalArgumentException.class, () -> plugin.consumeOwnedAllowance(recipe.id(), owner(), target));
        assertThrows(IllegalArgumentException.class, () -> plugin.consumeOwnedAllowance("unknown", owner, target));
        assertNull(plugin.consumeOwnedAllowance(recipe.id(), owner, target));
        assertNotNull(plugin.consumeOwnedAllowance(recipe.id(), owner, target));
        MockBukkit.getMock().getScheduler().performOneTick();
        assertNull(org.bukkit.Bukkit.getRecipe(key));
        assertNotNull(plugin.registry().byManagedKey(key));
        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(plugin.getDataFolder(), "limit-usage.yml"));
        assertEquals(1, yaml.getInt("global.mythic_test.used"));
        plugin.resetOwnedAllowance(recipe.id(), owner);
        assertNotNull(org.bukkit.Bukkit.getRecipe(key));
        assertNull(plugin.consumeOwnedAllowance(recipe.id(), owner, target));
    }
}
