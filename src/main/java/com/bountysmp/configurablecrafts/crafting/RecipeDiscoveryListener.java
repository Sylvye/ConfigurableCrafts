package com.bountysmp.configurablecrafts.crafting;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

public final class RecipeDiscoveryListener implements Listener {
    private final Plugin plugin;
    private final ManagedRecipeRegistry registry;

    public RecipeDiscoveryListener(Plugin plugin, ManagedRecipeRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                registry.syncPlayerRecipes(player);
            }
        });
    }
}
