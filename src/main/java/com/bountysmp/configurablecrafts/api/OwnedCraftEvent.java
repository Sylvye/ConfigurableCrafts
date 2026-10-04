package com.bountysmp.configurablecrafts.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Final cancellable validation before a plugin-owned craft commits. */
public final class OwnedCraftEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String recipeId;
    private boolean cancelled;
    public OwnedCraftEvent(Player player, String recipeId) { this.player = player; this.recipeId = recipeId; }
    public Player getPlayer() { return player; }
    public String getRecipeId() { return recipeId; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean value) { cancelled = value; }
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
