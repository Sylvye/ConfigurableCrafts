package com.bountysmp.configurablecrafts.gui;

import com.bountysmp.configurablecrafts.crafting.IngredientMatcher;
import com.bountysmp.configurablecrafts.crafting.ManagedRecipeRegistry;
import com.bountysmp.configurablecrafts.model.IngredientSpec;
import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.MatcherType;
import com.bountysmp.configurablecrafts.model.RecipeConditions;
import com.bountysmp.configurablecrafts.model.RecipeKind;
import com.bountysmp.configurablecrafts.util.ItemText;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class GuiManager implements Listener {
    private static final int[] MAIN_LIST_SLOTS = {
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35,
        36, 37, 38, 39, 40, 41, 42, 43, 44
    };
    private static final int[] VANILLA_LIST_SLOTS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8,
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35,
        36, 37, 38, 39, 40, 41, 42, 43, 44
    };
    private static final int[] GRID_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int RESULT_SLOT = 24;

    private final Plugin plugin;
    private final ManagedRecipeRegistry registry;
    private final ChatPromptManager prompts;
    private final Map<UUID, OpenMenu> openMenus = new ConcurrentHashMap<>();
    private final Map<UUID, EditorSession> editorSessions = new ConcurrentHashMap<>();
    private BukkitTask blinkTask;

    public GuiManager(Plugin plugin, ManagedRecipeRegistry registry, ChatPromptManager prompts) {
        this.plugin = plugin;
        this.registry = registry;
        this.prompts = prompts;
    }

    public void openMain(Player player, int page, String query) {
        boolean admin = isAdmin(player);
        Inventory inventory = Bukkit.createInventory(player, 54, admin ? "ConfigurableCrafts" : "Custom Recipes");
        fill(inventory);
        if (admin) {
            inventory.setItem(10, GuiUtil.item(Material.LIME_CONCRETE, GuiUtil.Tone.SUCCESS, "Create New Recipe", "Start a shaped or shapeless custom recipe."));
            inventory.setItem(12, GuiUtil.item(Material.CRAFTING_TABLE, GuiUtil.Tone.INFO, "Edit Vanilla Recipe", "Browse and override vanilla crafting recipes."));
            inventory.setItem(14, GuiUtil.item(Material.OAK_SIGN, GuiUtil.Tone.WARNING, "Search Modified Recipes", query.isBlank() ? "No search active." : "Search: " + query));
        } else {
            inventory.setItem(13, GuiUtil.item(Material.BOOK, GuiUtil.Tone.INFO, "Modified Recipes", "Enabled custom and modified recipes."));
        }

        List<ManagedRecipe> recipes = filteredManagedRecipes(player, query);
        int maxPage = maxPage(recipes.size(), MAIN_LIST_SLOTS.length);
        int safePage = clampPage(page, maxPage);
        for (int i = 0; i < MAIN_LIST_SLOTS.length; i++) {
            int index = safePage * MAIN_LIST_SLOTS.length + i;
            if (index >= recipes.size()) {
                break;
            }
            ManagedRecipe recipe = recipes.get(index);
            inventory.setItem(MAIN_LIST_SLOTS[i], recipeIcon(recipe, admin));
        }
        inventory.setItem(45, GuiUtil.item(Material.ARROW, GuiUtil.Tone.WARNING, "Previous Page", "Page " + (safePage + 1) + " / " + (maxPage + 1)));
        inventory.setItem(49, GuiUtil.item(Material.PAPER, GuiUtil.Tone.NEUTRAL, "Page " + (safePage + 1) + " / " + (maxPage + 1)));
        inventory.setItem(53, GuiUtil.item(Material.ARROW, GuiUtil.Tone.WARNING, "Next Page", "Page " + (safePage + 1) + " / " + (maxPage + 1)));
        player.openInventory(inventory);
        openMenus.put(player.getUniqueId(), new OpenMenu(Screen.MAIN, safePage, query, null, null));
    }

    public void startBlinkTask() {
        blinkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickBlink, 10L, 10L);
    }

    public void shutdown() {
        if (blinkTask != null) {
            blinkTask.cancel();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            releaseEditor(player);
        }
        openMenus.clear();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        OpenMenu open = openMenus.get(player.getUniqueId());
        if (open == null) {
            return;
        }
        if (event.getClick() == ClickType.DOUBLE_CLICK || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        boolean topClick = event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize();
        if (!topClick) {
            if (!canUseBottomInventory(player, open, event.getClick())) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        switch (open.screen) {
            case MAIN -> handleMainClick(player, open, event.getRawSlot(), event.getClick());
            case TYPE_PICKER -> handleTypePickerClick(player, event.getRawSlot());
            case VANILLA_LIST -> handleVanillaClick(player, open, event.getRawSlot(), event.getClick());
            case EDITOR -> handleEditorClick(player, event, event.getRawSlot());
            case CONFIRM_REMOVE -> handleConfirmClick(player, open, event.getRawSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        OpenMenu open = openMenus.get(player.getUniqueId());
        if (open == null) {
            return;
        }
        if (!canUseBottomInventory(player, open, ClickType.LEFT)) {
            event.setCancelled(true);
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        OpenMenu open = openMenus.remove(player.getUniqueId());
        if (open != null && open.screen == Screen.EDITOR) {
            EditorSession session = editorSessions.get(player.getUniqueId());
            if (session != null && !session.suspended()) {
                releaseEditor(player);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        releaseEditor(event.getPlayer());
        openMenus.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        releaseEditor(event.getPlayer());
        openMenus.remove(event.getPlayer().getUniqueId());
    }

    private void handleMainClick(Player player, OpenMenu open, int slot, ClickType click) {
        boolean admin = isAdmin(player);
        if (admin && slot == 10) {
            openTypePicker(player);
            return;
        }
        if (admin && slot == 12) {
            openVanillaList(player, 0, "");
            return;
        }
        if (admin && slot == 14) {
            promptMainSearch(player);
            return;
        }
        if (slot == 45) {
            openMain(player, open.page - 1, open.query);
            return;
        }
        if (slot == 53) {
            openMain(player, open.page + 1, open.query);
            return;
        }
        int listIndex = indexOf(MAIN_LIST_SLOTS, slot);
        if (listIndex < 0) {
            return;
        }
        List<ManagedRecipe> recipes = filteredManagedRecipes(player, open.query);
        int recipeIndex = open.page * MAIN_LIST_SLOTS.length + listIndex;
        if (recipeIndex >= recipes.size()) {
            return;
        }
        ManagedRecipe recipe = recipes.get(recipeIndex);
        if (admin && click.isShiftClick() && click.isRightClick()) {
            openConfirmRemove(player, recipe.id());
            return;
        }
        openEditor(player, new EditorSession(recipe, !admin));
    }

    private void handleTypePickerClick(Player player, int slot) {
        if (slot == 11) {
            openEditor(player, new EditorSession(ManagedRecipe.createCustom(RecipeKind.SHAPED), false));
            return;
        }
        if (slot == 15) {
            openEditor(player, new EditorSession(ManagedRecipe.createCustom(RecipeKind.SHAPELESS), false));
            return;
        }
        if (slot == 26) {
            openMain(player, 0, "");
        }
    }

    private void handleVanillaClick(Player player, OpenMenu open, int slot, ClickType click) {
        if (slot == 45) {
            openMain(player, 0, "");
            return;
        }
        if (slot == 48) {
            openVanillaList(player, open.page - 1, open.query);
            return;
        }
        if (slot == 49) {
            promptVanillaSearch(player, open.page);
            return;
        }
        if (slot == 50) {
            openVanillaList(player, open.page + 1, open.query);
            return;
        }
        int listIndex = indexOf(VANILLA_LIST_SLOTS, slot);
        if (listIndex < 0 || !click.isLeftClick()) {
            return;
        }
        List<Recipe> recipes = filteredVanilla(open.query);
        int recipeIndex = open.page * VANILLA_LIST_SLOTS.length + listIndex;
        if (recipeIndex >= recipes.size()) {
            return;
        }
        Recipe vanilla = recipes.get(recipeIndex);
        NamespacedKey sourceKey = registry.keyOf(vanilla);
        ManagedRecipe existing = findOverride(sourceKey);
        openEditor(player, new EditorSession(existing == null ? registry.fromVanilla(vanilla) : existing, false));
    }

    private void handleConfirmClick(Player player, OpenMenu open, int slot) {
        if (slot == 11 && open.recipeId != null) {
            registry.removeOrRevert(open.recipeId);
            player.sendMessage("Recipe removed or reverted.");
            openMain(player, 0, "");
            return;
        }
        if (slot == 15) {
            openMain(player, 0, "");
        }
    }

    private void handleEditorClick(Player player, InventoryClickEvent event, int slot) {
        EditorSession session = editorSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        int ingredientIndex = ingredientIndex(slot);
        if (ingredientIndex >= 0) {
            handleEditorItemClick(player, event, session, ingredientIndex, false);
            return;
        }
        if (slot == RESULT_SLOT) {
            handleEditorItemClick(player, event, session, -1, true);
            return;
        }
        if (slot == 46 && !session.readOnly()) {
            saveEditor(player, session);
            return;
        }
        if (slot == 49 || slot == 52) {
            releaseEditor(player);
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> openMain(player, 0, ""));
            return;
        }
        if (session.readOnly()) {
            return;
        }
        if (session.selectedSlot() >= 0) {
            handleMatcherClick(player, session, slot);
        } else {
            handleConditionClick(player, session, slot);
        }
        renderEditor(player, session, player.getOpenInventory().getTopInventory());
    }

    private void handleEditorItemClick(Player player, InventoryClickEvent event, EditorSession session, int ingredientIndex, boolean resultSlot) {
        ItemStack cursor = event.getCursor();
        ItemStack slotItem = resultSlot ? session.result() : session.ingredient(ingredientIndex);
        if (session.readOnly()) {
            return;
        }
        if (!resultSlot && event.getClick().isRightClick() && GuiUtil.isEmpty(cursor) && !GuiUtil.isEmpty(slotItem)) {
            session.setSelectedSlot(ingredientIndex);
            renderEditor(player, session, event.getView().getTopInventory());
            return;
        }

        boolean owned = resultSlot ? session.ownsResult() : session.ownsIngredient(ingredientIndex);
        if (GuiUtil.isEmpty(cursor)) {
            if (GuiUtil.isEmpty(slotItem)) {
                return;
            }
            if (owned) {
                player.setItemOnCursor(slotItem);
            } else {
                player.sendMessage("Removed template item. It was not added to your inventory.");
            }
            setSessionSlot(session, ingredientIndex, resultSlot, null, false);
            renderEditor(player, session, event.getView().getTopInventory());
            return;
        }

        if (GuiUtil.isEmpty(slotItem)) {
            ItemStack placed = cursor.clone();
            ItemStack newCursor = null;
            if (event.getClick().isRightClick()) {
                placed.setAmount(1);
                newCursor = cursor.clone();
                newCursor.setAmount(cursor.getAmount() - 1);
                if (newCursor.getAmount() <= 0) {
                    newCursor = null;
                }
            }
            setSessionSlot(session, ingredientIndex, resultSlot, placed, true);
            player.setItemOnCursor(newCursor);
            renderEditor(player, session, event.getView().getTopInventory());
            return;
        }

        if (event.getClick().isRightClick() && owned && slotItem.isSimilar(cursor) && slotItem.getAmount() < slotItem.getMaxStackSize()) {
            ItemStack updated = slotItem.clone();
            updated.setAmount(updated.getAmount() + 1);
            ItemStack newCursor = cursor.clone();
            newCursor.setAmount(cursor.getAmount() - 1);
            setSessionSlot(session, ingredientIndex, resultSlot, updated, true);
            player.setItemOnCursor(newCursor.getAmount() <= 0 ? null : newCursor);
            renderEditor(player, session, event.getView().getTopInventory());
            return;
        }

        if (event.getClick().isLeftClick()) {
            if (owned) {
                player.setItemOnCursor(slotItem);
            } else {
                player.setItemOnCursor(null);
                player.sendMessage("Replaced template item. It was not added to your inventory.");
            }
            setSessionSlot(session, ingredientIndex, resultSlot, cursor, true);
            renderEditor(player, session, event.getView().getTopInventory());
        }
    }

    private void setSessionSlot(EditorSession session, int ingredientIndex, boolean resultSlot, ItemStack itemStack, boolean owned) {
        if (resultSlot) {
            session.setResult(itemStack, owned);
        } else {
            session.setIngredient(ingredientIndex, itemStack, owned);
        }
    }

    private void handleMatcherClick(Player player, EditorSession session, int slot) {
        int selected = session.selectedSlot();
        IngredientSpec spec = ensureSelectedSpec(session);
        if (spec == null) {
            player.sendMessage("Select a filled ingredient slot first.");
            return;
        }
        if (slot == 14) {
            toggle(spec, MatcherType.MATERIAL);
        } else if (slot == 15) {
            toggle(spec, MatcherType.EXACT);
        } else if (slot == 16) {
            toggle(spec, MatcherType.ITEM_NAME);
        } else if (slot == 23) {
            promptEditor(player, session, "Type lore text to require, or blank to clear.", text -> {
                if (!text.equalsIgnoreCase("cancel")) {
                    spec.setLoreContains(text.isBlank() ? "" : text);
                    spec.setMatcher(MatcherType.LORE_CONTAINS, !text.isBlank());
                }
            });
        } else if (slot == 32) {
            promptEditor(player, session, "Type an item tag like minecraft:logs, or blank to clear.", text -> {
                if (text.equalsIgnoreCase("cancel")) {
                    return;
                }
                if (text.isBlank()) {
                    spec.setTagKey("");
                    spec.setMatcher(MatcherType.TAG, false);
                    return;
                }
                Tag<Material> tag = IngredientMatcher.resolveTag(text);
                if (tag == null) {
                    player.sendMessage("Unknown item tag: " + text);
                    return;
                }
                spec.setTagKey(text);
                spec.setMatcher(MatcherType.TAG, true);
            });
        } else if (slot == 33) {
            IngredientMatcher.captureEnchantments(spec);
            if (spec.enchantments().isEmpty()) {
                player.sendMessage("Selected item has no enchantments to capture.");
                spec.setMatcher(MatcherType.ENCHANTMENTS, false);
            } else {
                spec.setMatcher(MatcherType.ENCHANTMENTS, !spec.hasMatcher(MatcherType.ENCHANTMENTS));
            }
        } else if (slot == 41) {
            session.setSelectedSlot(-1);
        }
        session.recipe().setIngredient(selected, spec);
    }

    private void handleConditionClick(Player player, EditorSession session, int slot) {
        RecipeConditions conditions = session.recipe().conditions();
        if (slot == 14) {
            session.recipe().setEnabled(!session.recipe().enabled());
        } else if (slot == 15) {
            promptEditor(player, session, "Type comma-separated dimensions, e.g. minecraft:overworld. Blank means any.", text -> {
                if (!text.equalsIgnoreCase("cancel")) {
                    conditions.dimensions().clear();
                    conditions.dimensions().addAll(parseList(text));
                }
            });
        } else if (slot == 16) {
            promptEditor(player, session, "Type comma-separated biomes, e.g. minecraft:plains. Blank means any.", text -> {
                if (!text.equalsIgnoreCase("cancel")) {
                    conditions.biomes().clear();
                    conditions.biomes().addAll(parseList(text));
                }
            });
        } else if (slot == 23) {
            conditions.setWeather(conditions.weather().next());
        } else if (slot == 32) {
            promptEditor(player, session, "Type minimum experience level. Blank means 0.", text -> {
                if (text.equalsIgnoreCase("cancel")) {
                    return;
                }
                try {
                    conditions.setMinimumExperienceLevel(text.isBlank() ? 0 : Integer.parseInt(text.trim()));
                } catch (NumberFormatException exception) {
                    player.sendMessage("Invalid level: " + text);
                }
            });
        }
    }

    private void saveEditor(Player player, EditorSession session) {
        session.applyItemsToRecipe();
        String error = registry.validateForSave(session.recipe());
        if (error != null) {
            player.sendMessage(error);
            return;
        }
        session.releaseOwnedItems(player);
        editorSessions.remove(player.getUniqueId());
        openMenus.remove(player.getUniqueId());
        registry.upsert(session.recipe());
        player.sendMessage("Recipe saved.");
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> openMain(player, 0, ""));
    }

    private void openTypePicker(Player player) {
        Inventory inventory = Bukkit.createInventory(player, 27, "Select Recipe Type");
        fill(inventory);
        inventory.setItem(11, GuiUtil.item(Material.CRAFTING_TABLE, GuiUtil.Tone.SUCCESS, "Shaped Crafting", "Enabled in v0."));
        inventory.setItem(15, GuiUtil.item(Material.CHEST, GuiUtil.Tone.SUCCESS, "Shapeless Crafting", "Enabled in v0."));
        inventory.setItem(20, GuiUtil.item(Material.BREWING_STAND, GuiUtil.Tone.MUTED, "Brewing", "Placeholder for a future version."));
        inventory.setItem(21, GuiUtil.item(Material.FURNACE, GuiUtil.Tone.MUTED, "Smelting", "Placeholder for a future version."));
        inventory.setItem(22, GuiUtil.item(Material.CAMPFIRE, GuiUtil.Tone.MUTED, "Cooking", "Placeholder for a future version."));
        inventory.setItem(23, GuiUtil.item(Material.BLAST_FURNACE, GuiUtil.Tone.MUTED, "Blasting", "Placeholder for a future version."));
        inventory.setItem(24, GuiUtil.item(Material.SMITHING_TABLE, GuiUtil.Tone.MUTED, "Smithing", "Placeholder for a future version."));
        inventory.setItem(26, GuiUtil.item(Material.BARRIER, GuiUtil.Tone.DANGER, "Back"));
        player.openInventory(inventory);
        openMenus.put(player.getUniqueId(), new OpenMenu(Screen.TYPE_PICKER, 0, "", null, null));
    }

    private void openVanillaList(Player player, int page, String query) {
        Inventory inventory = Bukkit.createInventory(player, 54, "Vanilla Crafting Recipes");
        fill(inventory);
        List<Recipe> recipes = filteredVanilla(query);
        int maxPage = maxPage(recipes.size(), VANILLA_LIST_SLOTS.length);
        int safePage = clampPage(page, maxPage);
        for (int i = 0; i < VANILLA_LIST_SLOTS.length; i++) {
            int index = safePage * VANILLA_LIST_SLOTS.length + i;
            if (index >= recipes.size()) {
                break;
            }
            Recipe recipe = recipes.get(index);
            NamespacedKey key = registry.keyOf(recipe);
            inventory.setItem(VANILLA_LIST_SLOTS[i], GuiUtil.namedClone(recipe.getResult(), ItemText.displayName(recipe.getResult()), GuiUtil.Tone.INFO, List.of(
                key == null ? "Unknown key" : key.toString(),
                recipe.getClass().getSimpleName(),
                "Left-click to edit."
            )));
        }
        inventory.setItem(45, GuiUtil.item(Material.BARRIER, GuiUtil.Tone.DANGER, "Back"));
        inventory.setItem(48, GuiUtil.item(Material.ARROW, GuiUtil.Tone.WARNING, "Previous Page"));
        inventory.setItem(49, GuiUtil.item(Material.OAK_SIGN, GuiUtil.Tone.WARNING, "Search", query.isBlank() ? "No search active." : "Search: " + query));
        inventory.setItem(50, GuiUtil.item(Material.ARROW, GuiUtil.Tone.WARNING, "Next Page"));
        inventory.setItem(53, GuiUtil.item(Material.PAPER, GuiUtil.Tone.NEUTRAL, "Page " + (safePage + 1) + " / " + (maxPage + 1)));
        player.openInventory(inventory);
        openMenus.put(player.getUniqueId(), new OpenMenu(Screen.VANILLA_LIST, safePage, query, null, null));
    }

    private void openConfirmRemove(Player player, String recipeId) {
        Inventory inventory = Bukkit.createInventory(player, 27, "Confirm Remove/Revert");
        fill(inventory);
        inventory.setItem(11, GuiUtil.item(Material.RED_CONCRETE, GuiUtil.Tone.DANGER, "Confirm Remove", "Delete custom recipe or revert vanilla override."));
        inventory.setItem(15, GuiUtil.item(Material.LIME_CONCRETE, GuiUtil.Tone.SUCCESS, "Cancel"));
        player.openInventory(inventory);
        openMenus.put(player.getUniqueId(), new OpenMenu(Screen.CONFIRM_REMOVE, 0, "", recipeId, null));
    }

    private void openEditor(Player player, EditorSession session) {
        editorSessions.put(player.getUniqueId(), session);
        Inventory inventory = Bukkit.createInventory(player, 54, session.readOnly() ? "View Recipe" : "Edit Recipe");
        renderEditor(player, session, inventory);
        player.openInventory(inventory);
        openMenus.put(player.getUniqueId(), new OpenMenu(Screen.EDITOR, 0, "", session.recipe().id(), null));
    }

    private void renderEditor(Player player, EditorSession session, Inventory inventory) {
        fill(inventory);
        for (int i = 0; i < GRID_SLOTS.length; i++) {
            ItemStack ingredient = session.ingredient(i);
            inventory.setItem(GRID_SLOTS[i], GuiUtil.isEmpty(ingredient) ? GuiUtil.emptySlot() : displayIngredient(session, ingredient));
        }
        ItemStack result = session.result();
        inventory.setItem(RESULT_SLOT, GuiUtil.isEmpty(result) ? GuiUtil.item(Material.RED_STAINED_GLASS_PANE, GuiUtil.Tone.DANGER, "Result Slot", "Place the output item here.") : displayResult(session, result));
        inventory.setItem(4, GuiUtil.item(Material.BOOK, GuiUtil.Tone.INFO, session.recipe().kind().displayName(), session.recipe().enabled() ? "Enabled" : "Disabled"));
        if (session.selectedSlot() >= 0) {
            renderMatcherControls(inventory, session);
        } else {
            renderRecipeControls(inventory, session);
        }
        if (!session.readOnly()) {
            inventory.setItem(46, GuiUtil.item(Material.LIME_CONCRETE, GuiUtil.Tone.SUCCESS, "Save Recipe"));
        }
        inventory.setItem(49, GuiUtil.item(Material.BARRIER, GuiUtil.Tone.DANGER, session.readOnly() ? "Back" : "Cancel"));
        inventory.setItem(52, GuiUtil.item(Material.ARROW, GuiUtil.Tone.WARNING, "Back to Menu"));
    }

    private ItemStack displayIngredient(EditorSession session, ItemStack ingredient) {
        return session.readOnly() ? GuiUtil.displayClone(ingredient, "Ingredient sample") : ingredient;
    }

    private ItemStack displayResult(EditorSession session, ItemStack result) {
        return session.readOnly() ? GuiUtil.displayClone(result, "Recipe result") : result;
    }

    private void renderRecipeControls(Inventory inventory, EditorSession session) {
        RecipeConditions conditions = session.recipe().conditions();
        inventory.setItem(14, GuiUtil.item(session.recipe().enabled() ? Material.LIME_DYE : Material.GRAY_DYE, session.recipe().enabled() ? GuiUtil.Tone.SUCCESS : GuiUtil.Tone.MUTED, "Recipe Enabled: " + session.recipe().enabled(), "Click to toggle."));
        inventory.setItem(15, GuiUtil.item(Material.ENDER_EYE, GuiUtil.Tone.WARNING, "Allowed Dimensions", conditions.dimensions().isEmpty() ? "Any dimension." : String.join(", ", conditions.dimensions())));
        inventory.setItem(16, GuiUtil.item(Material.GRASS_BLOCK, GuiUtil.Tone.WARNING, "Allowed Biomes", conditions.biomes().isEmpty() ? "Any biome." : String.join(", ", conditions.biomes())));
        inventory.setItem(23, GuiUtil.item(Material.WATER_BUCKET, GuiUtil.Tone.WARNING, "Weather: " + conditions.weather().displayName(), "Click to cycle."));
        inventory.setItem(32, GuiUtil.item(Material.EXPERIENCE_BOTTLE, GuiUtil.Tone.WARNING, "Minimum XP Level: " + conditions.minimumExperienceLevel(), "Click to edit."));
    }

    private void renderMatcherControls(Inventory inventory, EditorSession session) {
        IngredientSpec spec = ensureSelectedSpec(session);
        if (spec == null) {
            session.setSelectedSlot(-1);
            renderRecipeControls(inventory, session);
            return;
        }
        inventory.setItem(14, matcherItem(spec, MatcherType.MATERIAL));
        inventory.setItem(15, matcherItem(spec, MatcherType.EXACT));
        inventory.setItem(16, matcherItem(spec, MatcherType.ITEM_NAME));
        inventory.setItem(23, matcherItem(spec, MatcherType.LORE_CONTAINS, spec.loreContains().isBlank() ? "No lore text set." : spec.loreContains()));
        inventory.setItem(32, matcherItem(spec, MatcherType.TAG, spec.tagKey().isBlank() ? "No tag set." : spec.tagKey()));
        inventory.setItem(33, matcherItem(spec, MatcherType.ENCHANTMENTS, spec.enchantments().isEmpty() ? "No enchantments captured." : spec.enchantments().toString()));
        inventory.setItem(41, GuiUtil.item(Material.ARROW, GuiUtil.Tone.WARNING, "Clear Selection"));
    }

    private ItemStack matcherItem(IngredientSpec spec, MatcherType matcherType, String... extraLore) {
        List<String> lore = new ArrayList<>();
        lore.add(spec.hasMatcher(matcherType) ? "Enabled" : "Disabled");
        lore.addAll(Arrays.asList(extraLore));
        lore.add("Click to toggle or edit.");
        return GuiUtil.item(spec.hasMatcher(matcherType) ? Material.LIME_DYE : Material.GRAY_DYE, spec.hasMatcher(matcherType) ? GuiUtil.Tone.SUCCESS : GuiUtil.Tone.MUTED, matcherType.displayName(), lore);
    }

    private void tickBlink() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            OpenMenu open = openMenus.get(player.getUniqueId());
            EditorSession session = editorSessions.get(player.getUniqueId());
            if (open == null || open.screen != Screen.EDITOR || session == null || session.selectedSlot() < 0) {
                continue;
            }
            ItemStack ingredient = session.ingredient(session.selectedSlot());
            if (GuiUtil.isEmpty(ingredient)) {
                session.setSelectedSlot(-1);
                continue;
            }
            int slot = GRID_SLOTS[session.selectedSlot()];
            player.getOpenInventory().getTopInventory().setItem(slot, session.flipBlink()
                ? GuiUtil.item(Material.LIME_STAINED_GLASS_PANE, GuiUtil.Tone.SUCCESS, "Selected Ingredient")
                : ingredient);
        }
    }

    private void promptMainSearch(Player player) {
        openMenus.remove(player.getUniqueId());
        prompts.prompt(player, "Type a recipe search query. Blank clears search.", text -> openMain(player, 0, text.equalsIgnoreCase("cancel") ? "" : text));
    }

    private void promptVanillaSearch(Player player, int page) {
        openMenus.remove(player.getUniqueId());
        prompts.prompt(player, "Type a vanilla recipe search query. Blank clears search.", text -> openVanillaList(player, text.equalsIgnoreCase("cancel") ? page : 0, text.equalsIgnoreCase("cancel") ? "" : text));
    }

    private void promptEditor(Player player, EditorSession session, String message, java.util.function.Consumer<String> consumer) {
        session.setSuspended(true);
        openMenus.remove(player.getUniqueId());
        prompts.prompt(player, message, text -> {
            consumer.accept(text);
            session.setSuspended(false);
            openEditor(player, session);
        });
    }

    private IngredientSpec ensureSelectedSpec(EditorSession session) {
        int selected = session.selectedSlot();
        if (selected < 0) {
            return null;
        }
        ItemStack item = session.ingredient(selected);
        if (GuiUtil.isEmpty(item)) {
            return null;
        }
        IngredientSpec spec = session.recipe().ingredient(selected);
        if (spec == null) {
            spec = IngredientSpec.fromSample(item);
            session.recipe().setIngredient(selected, spec);
        }
        return spec;
    }

    private void toggle(IngredientSpec spec, MatcherType matcherType) {
        spec.setMatcher(matcherType, !spec.hasMatcher(matcherType));
        if (spec.matchers().isEmpty()) {
            spec.matchers().add(MatcherType.MATERIAL);
        }
    }

    private List<String> parseList(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .map(value -> value.toLowerCase(Locale.ROOT))
            .toList();
    }

    private List<ManagedRecipe> filteredManagedRecipes(Player player, String query) {
        boolean admin = isAdmin(player);
        String lower = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return registry.sortedRecipes().stream()
            .filter(recipe -> admin || recipe.enabled())
            .filter(recipe -> lower.isBlank()
                || recipe.displayLabel().toLowerCase(Locale.ROOT).contains(lower)
                || ItemText.displayName(recipe.result()).toLowerCase(Locale.ROOT).contains(lower))
            .toList();
    }

    private List<Recipe> filteredVanilla(String query) {
        String lower = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return registry.vanillaRecipeList().stream()
            .filter(recipe -> {
                NamespacedKey key = registry.keyOf(recipe);
                return lower.isBlank()
                    || (key != null && key.toString().toLowerCase(Locale.ROOT).contains(lower))
                    || recipe.getResult().getType().name().toLowerCase(Locale.ROOT).contains(lower)
                    || ItemText.displayName(recipe.getResult()).toLowerCase(Locale.ROOT).contains(lower)
                    || recipe.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains(lower);
            })
            .toList();
    }

    private ManagedRecipe findOverride(NamespacedKey sourceKey) {
        if (sourceKey == null) {
            return null;
        }
        for (ManagedRecipe recipe : registry.recipes()) {
            if (sourceKey.toString().equals(recipe.sourceKey())) {
                return recipe;
            }
        }
        return null;
    }

    private ItemStack recipeIcon(ManagedRecipe recipe, boolean admin) {
        List<String> lore = new ArrayList<>();
        lore.add(recipe.kind().displayName());
        lore.add(recipe.enabled() ? "Enabled" : "Disabled");
        lore.add(recipe.isOverride() ? "Overrides " + recipe.sourceKey() : "Custom recipe");
        lore.add(admin ? "Left-click edit. Shift-right-click delete/revert." : "Left-click view.");
        ItemStack result = recipe.result();
        return GuiUtil.namedClone(result, ItemText.displayName(result), recipe.enabled() ? GuiUtil.Tone.INFO : GuiUtil.Tone.MUTED, lore);
    }

    private void fill(Inventory inventory) {
        ItemStack filler = GuiUtil.filler();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    private void releaseEditor(Player player) {
        EditorSession session = editorSessions.remove(player.getUniqueId());
        if (session != null) {
            session.releaseOwnedItems(player);
        }
    }

    private boolean canUseBottomInventory(Player player, OpenMenu open, ClickType click) {
        if (click == null || click.isShiftClick() || click == ClickType.DOUBLE_CLICK) {
            return false;
        }
        if (open.screen != Screen.EDITOR) {
            return false;
        }
        EditorSession session = editorSessions.get(player.getUniqueId());
        return session != null && !session.readOnly() && (click.isLeftClick() || click.isRightClick());
    }

    private boolean isAdmin(Player player) {
        return player.hasPermission("configurablecrafts.admin");
    }

    private int ingredientIndex(int rawSlot) {
        return indexOf(GRID_SLOTS, rawSlot);
    }

    private int indexOf(int[] slots, int rawSlot) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == rawSlot) {
                return i;
            }
        }
        return -1;
    }

    private int maxPage(int size, int pageSize) {
        return Math.max(0, (int) Math.ceil(size / (double) pageSize) - 1);
    }

    private int clampPage(int page, int maxPage) {
        return Math.max(0, Math.min(page, maxPage));
    }

    private enum Screen {
        MAIN,
        TYPE_PICKER,
        VANILLA_LIST,
        EDITOR,
        CONFIRM_REMOVE
    }

    private record OpenMenu(Screen screen, int page, String query, String recipeId, String vanillaKey) {
    }
}
