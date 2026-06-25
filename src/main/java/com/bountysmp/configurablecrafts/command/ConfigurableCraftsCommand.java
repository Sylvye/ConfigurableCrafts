package com.bountysmp.configurablecrafts.command;

import com.bountysmp.configurablecrafts.gui.GuiManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class ConfigurableCraftsCommand implements CommandExecutor {
    private final GuiManager guiManager;

    public ConfigurableCraftsCommand(GuiManager guiManager) {
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can open ConfigurableCrafts.");
            return true;
        }
        if (!player.hasPermission("configurablecrafts.view") && !player.hasPermission("configurablecrafts.admin")) {
            player.sendMessage("You do not have permission to use ConfigurableCrafts.");
            return true;
        }
        guiManager.openMain(player, 0, "");
        return true;
    }
}
