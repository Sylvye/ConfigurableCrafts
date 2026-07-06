package com.bountysmp.configurablecrafts.gui;

import com.bountysmp.configurablecrafts.crafting.IngredientMatcher;
import com.bountysmp.configurablecrafts.crafting.ManagedRecipeRegistry;
import com.bountysmp.configurablecrafts.model.IngredientSpec;
import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.MatcherType;
import com.bountysmp.configurablecrafts.model.RecipeConditions;
import com.bountysmp.configurablecrafts.model.RecipeKind;
import com.bountysmp.configurablecrafts.model.RecipeLimit;
import com.bountysmp.configurablecrafts.model.RecipeListFilter;
import com.bountysmp.configurablecrafts.model.WeatherMode;
import com.bountysmp.configurablecrafts.util.ItemText;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
    private static final int MAIN_FILTER_SLOT = 16;
    private static final int TYPE_PICKER_BACK_SLOT = 49;
    private static final WorkstationOption[] WORKSTATION_OPTIONS = {
        new WorkstationOption(28, Material.BREWING_STAND, RecipeKind.BREWING, "Brewing", "Potion ingredient recipes."),
        new WorkstationOption(29, Material.FURNACE, RecipeKind.SMELTING, "Smelting", "Furnace input and output recipes."),
        new WorkstationOption(30, Material.SMOKER, RecipeKind.SMOKING, "Smoking", "Food cooking recipes."),
        new WorkstationOption(31, Material.BLAST_FURNACE, RecipeKind.BLASTING, "Blasting", "Ore and metal processing recipes."),
        new WorkstationOption(32, Material.CAMPFIRE, RecipeKind.CAMPFIRE, "Campfire Cooking", "Campfire cooking recipes."),
        new WorkstationOption(33, Material.STONECUTTER, RecipeKind.STONECUTTING, "Stonecutting", "Stonecutter conversion recipes."),
        new WorkstationOption(34, Material.SMITHING_TABLE, RecipeKind.SMITHING, "Smithing", "Template and upgrade recipes.")
    };

    private final Plugin plugin;
    private final ManagedRecipeRegistry registry;
    private final ChatPromptManager prompts;
    private final Map<UUID, OpenMenu> openMenus = new ConcurrentHashMap<>();
    private final Map<UUID, EditorSession> editorSessions = new ConcurrentHashMap<>();
    private BukkitTask blinkTask;
    private boolean disabledBlink;

    public GuiManager(Plugin plugin, ManagedRecipeRegistry registry, ChatPromptManager prompts) {
        this.plugin = plugin;
        this.registry = registry;
        this.prompts = prompts;
    }

    public void openMain(Player player, int page, String query) {
        openMain(player, page, query, RecipeListFilter.ALL);
    }

    public void openMain(Player player, int page, String query, RecipeListFilter filter) {
        boolean admin = isAdmin(player);
        Inventory inventory = Bukkit.createInventory(player, 54, admin ? "ConfigurableCrafts" : "Custom Recipes");
        fill(inventory);
        if (admin) {
            inventory.setItem(10, GuiUtil.item(Material.LIME_CONCRETE, GuiUtil.Tone.SUCCESS, "Create New Recipe", "Start a shaped or shapeless custom recipe."));
            inventory.setItem(12, GuiUtil.item(Material.CRAFTING_TABLE, GuiUtil.Tone.INFO, "Edit Vanilla Recipe", "Browse and override vanilla crafting recipes."));
            inventory.setItem(14, GuiUtil.item(Material.OAK_SIGN, GuiUtil.Tone.WARNING, "Search Modified Recipes", query.isBlank() ? "No search active." : "Search: " + query));
        } else {
            inventory.setItem(13, GuiUtil.item(Material.BOOK, GuiUtil.Tone.INFO, "Recipes", "Custom, modified, and disabled recipes."));
        }
        inventory.setItem(MAIN_FILTER_SLOT, filterItem(filter));

        List<ManagedRecipe> recipes = filteredManagedRecipes(query, filter);
        int maxPage = maxPage(recipes.size(), MAIN_LIST_SLOTS.length);
        int safePage = clampPage(page, maxPage);
        for (int i = 0; i < MAIN_LIST_SLOTS.length; i++) {
            int index = safePage * MAIN_LIST_SLOTS.length + i;
            if (index >= recipes.size()) {
                break;
            }
            ManagedRecipe recipe = recipes.get(index);
            inventory.setItem(MAIN_LIST_SLOTS[i], recipeIcon(recipe, admin, disabledBlink));
        }
        inventory.setItem(45, GuiUtil.item(Material.ARROW, GuiUtil.Tone.WARNING, "Previous Page", "Page " + (safePage + 1) + " / " + (maxPage + 1)));
        inventory.setItem(49, GuiUtil.item(Material.PAPER, GuiUtil.Tone.NEUTRAL, "Page " + (safePage + 1) + " / " + (maxPage + 1)));
        inventory.setItem(53, GuiUtil.item(Material.ARROW, GuiUtil.Tone.WARNING, "Next Page", "Page " + (safePage + 1) + " / " + (maxPage + 1)));
        player.openInventory(inventory);
        openMenus.put(player.getUniqueId(), new OpenMenu(Screen.MAIN, safePage, query, null, filter, inventory));
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
        if (!isCurrentMenu(event.getView().getTopInventory(), open)) {
            discardStaleMenu(player, open);
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
            promptMainSearch(player, open.filter);
            return;
        }
        if (slot == MAIN_FILTER_SLOT) {
            openMain(player, 0, open.query, open.filter.next());
            return;
        }
        if (slot == 45) {
            openMain(player, open.page - 1, open.query, open.filter);
            return;
        }
        if (slot == 53) {
            openMain(player, open.page + 1, open.query, open.filter);
            return;
        }
        int listIndex = indexOf(MAIN_LIST_SLOTS, slot);
        if (listIndex < 0) {
            return;
        }
        List<ManagedRecipe> recipes = filteredManagedRecipes(open.query, open.filter);
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
        WorkstationOption workstation = workstationOption(slot);
        if (workstation != null) {
            openEditor(player, new EditorSession(ManagedRecipe.createCustom(workstation.kind()), false));
            return;
        }
        if (slot == TYPE_PICKER_BACK_SLOT) {
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
        int ingredientIndex = ingredientIndex(session.recipe(), slot);
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
        if (hasSelectedTagIngredient(session) && slot == 41) {
            session.setSelectedSlot(-1);
            renderEditor(player, session, player.getOpenInventory().getTopInventory());
            return;
        }
        if (hasSelectedItemIngredient(session)) {
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
        if (!resultSlot && event.getClick().isRightClick() && GuiUtil.isEmpty(cursor)) {
            if (GuiUtil.isEmpty(slotItem)) {
                IngredientSpec spec = session.recipe().ingredient(ingredientIndex);
                if (spec != null && spec.isTagOnly()) {
                    session.recipe().setIngredient(ingredientIndex, null);
                    session.setSelectedSlot(-1);
                    renderEditor(player, session, event.getView().getTopInventory());
                } else {
                    promptTagEditor(player, session, ingredientIndex, new IngredientSpec());
                }
                return;
            }
            session.setSelectedSlot(ingredientIndex);
            renderEditor(player, session, event.getView().getTopInventory());
            return;
        }

        boolean owned = resultSlot ? session.ownsResult() : session.ownsIngredient(ingredientIndex);
        if (GuiUtil.isEmpty(cursor)) {
            if (GuiUtil.isEmpty(slotItem)) {
                return;
            }
            player.setItemOnCursor(slotItem);
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
            player.setItemOnCursor(slotItem);
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
            player.sendMessage("Select an ingredient slot first.");
            return;
        }
        if (slot == 14) {
            toggle(spec, MatcherType.MATERIAL);
        } else if (slot == 15) {
            toggle(spec, MatcherType.EXACT);
        } else if (slot == 16) {
            toggle(spec, MatcherType.ITEM_NAME);
        } else if (slot == 23) {
            promptEditor(player, session, "Type lore text to require. Type clear to remove the lore requirement.", text -> {
                if (!text.equalsIgnoreCase("cancel")) {
                    boolean clear = text.equalsIgnoreCase("clear");
                    spec.setLoreContains(clear ? "" : text);
                    spec.setMatcher(MatcherType.LORE_CONTAINS, !clear);
                }
            });
        } else if (slot == 32) {
            player.sendMessage("Item tags can only be used on empty ingredient slots.");
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

    private void promptTagEditor(Player player, EditorSession session, int selected, IngredientSpec spec) {
        session.setSelectedSlot(-1);
        promptEditor(player, session, "Type an item tag like minecraft:logs.", text -> {
            if (text.equalsIgnoreCase("cancel")) {
                return;
            }
            if (IngredientMatcher.usableTagValues(text).isEmpty()) {
                player.sendMessage("Unknown item tag: " + text);
                return;
            }
            spec.setTagKey(text);
            spec.setMatcher(MatcherType.TAG, true);
            spec.normalizeMatchers();
            session.recipe().setIngredient(selected, spec);
            session.setSelectedSlot(selected);
        });
    }

    private void handleConditionClick(Player player, EditorSession session, int slot) {
        RecipeConditions conditions = session.recipe().conditions();
        if (slot == 14) {
            session.recipe().setEnabled(!session.recipe().enabled());
        } else if (slot == 15) {
            promptEditor(player, session, "Type comma-separated dimensions, e.g. minecraft:overworld. Type clear to allow any dimension.", text -> {
                if (!text.equalsIgnoreCase("cancel")) {
                    if (text.equalsIgnoreCase("clear")) {
                        conditions.dimensions().clear();
                        return;
                    }
                    List<String> values = parseList(text);
                    List<String> invalid = ManagedRecipeRegistry.invalidDimensions(values);
                    if (!invalid.isEmpty()) {
                        player.sendMessage("Unknown dimension(s): " + String.join(", ", invalid));
                        return;
                    }
                    conditions.dimensions().clear();
                    conditions.dimensions().addAll(values);
                }
            });
        } else if (slot == 16) {
            promptEditor(player, session, "Type comma-separated biomes, e.g. minecraft:plains. Type clear to allow any biome.", text -> {
                if (!text.equalsIgnoreCase("cancel")) {
                    if (text.equalsIgnoreCase("clear")) {
                        conditions.biomes().clear();
                        return;
                    }
                    List<String> values = parseList(text);
                    List<String> invalid = ManagedRecipeRegistry.invalidBiomes(values);
                    if (!invalid.isEmpty()) {
                        player.sendMessage("Unknown biome(s): " + String.join(", ", invalid));
                        return;
                    }
                    conditions.biomes().clear();
                    conditions.biomes().addAll(values);
                }
            });
        } else if (slot == 23) {
            conditions.setWeather(conditions.weather().next());
        } else if (slot == 25) {
            promptLimit(player, session, session.recipe().playerLimit(), "per-player");
        } else if (slot == 34) {
            promptLimit(player, session, session.recipe().globalLimit(), "global");
        } else if (slot == 32) {
            promptEditor(player, session, "Type minimum experience level. Type 0 for no minimum level.", text -> {
                if (text.equalsIgnoreCase("cancel")) {
                    return;
                }
                try {
                    conditions.setMinimumExperienceLevel(Integer.parseInt(text.trim()));
                } catch (NumberFormatException exception) {
                    player.sendMessage("Invalid level: " + text);
                }
            });
        } else if (slot == 43 && session.recipe().kind().isCooking()) {
            promptPositiveInt(player, session, "Type cook time in ticks.", session.recipe()::setCookTimeTicks);
        } else if (slot == 44 && session.recipe().kind().isCooking()) {
            promptExperience(player, session);
        } else if (slot == 43 && session.recipe().kind() == RecipeKind.BREWING) {
            promptPositiveInt(player, session, "Type brew time in ticks.", session.recipe()::setBrewTimeTicks);
        } else if (slot == 43 && session.recipe().kind() == RecipeKind.SMITHING) {
            session.recipe().setCopyDataComponents(!session.recipe().copyDataComponents());
        } else if (slot == 41 && session.recipe().kind().isCraftingTable()) {
            if (!session.recipe().allowCrafters() && ManagedRecipeRegistry.hasCrafterBypassedRequirements(session.recipe())) {
                player.sendMessage("Crafters can only be allowed when dimension, weather, XP, biome, and crafting limit requirements are not configured.");
                return;
            }
            session.recipe().setAllowCrafters(!session.recipe().allowCrafters());
            if (session.recipe().allowCrafters()) {
                player.sendMessage(ManagedRecipeRegistry.CRAFTER_BYPASS_WARNING);
            }
        }
    }

    private void promptPositiveInt(Player player, EditorSession session, String message, java.util.function.IntConsumer consumer) {
        promptEditor(player, session, message, text -> {
            if (text.equalsIgnoreCase("cancel")) {
                return;
            }
            try {
                int value = Integer.parseInt(text.trim());
                if (value <= 0) {
                    player.sendMessage("Value must be greater than 0.");
                    return;
                }
                consumer.accept(value);
            } catch (NumberFormatException exception) {
                player.sendMessage("Invalid number: " + text);
            }
        });
    }

    private void promptExperience(Player player, EditorSession session) {
        promptEditor(player, session, "Type experience reward, e.g. 0.35.", text -> {
            if (text.equalsIgnoreCase("cancel")) {
                return;
            }
            try {
                float value = Float.parseFloat(text.trim());
                if (value < 0.0F) {
                    player.sendMessage("Experience cannot be negative.");
                    return;
                }
                session.recipe().setExperience(value);
            } catch (NumberFormatException exception) {
                player.sendMessage("Invalid experience: " + text);
            }
        });
    }

    private void promptLimit(Player player, EditorSession session, RecipeLimit limit, String label) {
        promptEditor(player, session, "Type " + label + " limit as '<crafts> <time>', e.g. 5 10m, 20 1h. Type clear to disable.", text -> {
            if (text.equalsIgnoreCase("cancel")) {
                return;
            }
            if (text.equalsIgnoreCase("clear")) {
                limit.clear();
                return;
            }
            String[] parts = text.trim().split("\\s+");
            if (parts.length != 2) {
                player.sendMessage("Invalid limit. Use '<crafts> <time>', e.g. 5 10m.");
                return;
            }
            try {
                int crafts = Integer.parseInt(parts[0]);
                long seconds = parseDurationSeconds(parts[1]);
                if (crafts <= 0 || seconds <= 0L) {
                    player.sendMessage("Crafts and time must be greater than 0.");
                    return;
                }
                limit.set(crafts, seconds);
            } catch (NumberFormatException exception) {
                player.sendMessage("Invalid limit. Use '<crafts> <time>', e.g. 5 10m.");
            }
        });
    }

    private void saveEditor(Player player, EditorSession session) {
        session.applyItemsToRecipe();
        normalizeActiveIngredients(session.recipe());
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
        for (String warning : registry.warningsForSave(session.recipe())) {
            player.sendMessage(warning);
        }
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> openMain(player, 0, ""));
    }

    private void openTypePicker(Player player) {
        Inventory inventory = Bukkit.createInventory(player, 54, "Select Recipe Type");
        fill(inventory);
        inventory.setItem(4, GuiUtil.item(Material.WRITABLE_BOOK, GuiUtil.Tone.INFO, "Recipe Type", "Choose a supported crafting type."));
        inventory.setItem(11, GuiUtil.item(Material.CRAFTING_TABLE, GuiUtil.Tone.SUCCESS, "Shaped Crafting", "Supported", "Uses the exact grid layout."));
        inventory.setItem(15, GuiUtil.item(Material.CHEST, GuiUtil.Tone.SUCCESS, "Shapeless Crafting", "Supported", "Ingredients can be placed in any order."));
        inventory.setItem(22, GuiUtil.item(Material.COPPER_BULB, GuiUtil.Tone.SUCCESS, "Workstation Recipes", "Supported workstation recipe types."));
        for (WorkstationOption option : WORKSTATION_OPTIONS) {
            inventory.setItem(option.slot(), workstationItem(option));
        }
        inventory.setItem(TYPE_PICKER_BACK_SLOT, GuiUtil.item(Material.BARRIER, GuiUtil.Tone.DANGER, "Back"));
        player.openInventory(inventory);
        openMenus.put(player.getUniqueId(), new OpenMenu(Screen.TYPE_PICKER, 0, "", null, null, inventory));
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
        openMenus.put(player.getUniqueId(), new OpenMenu(Screen.VANILLA_LIST, safePage, query, null, null, inventory));
    }

    private void openConfirmRemove(Player player, String recipeId) {
        Inventory inventory = Bukkit.createInventory(player, 27, "Confirm Remove/Revert");
        fill(inventory);
        inventory.setItem(11, GuiUtil.item(Material.RED_CONCRETE, GuiUtil.Tone.DANGER, "Confirm Remove", "Delete custom recipe or revert vanilla override."));
        inventory.setItem(15, GuiUtil.item(Material.LIME_CONCRETE, GuiUtil.Tone.SUCCESS, "Cancel"));
        player.openInventory(inventory);
        openMenus.put(player.getUniqueId(), new OpenMenu(Screen.CONFIRM_REMOVE, 0, "", recipeId, null, inventory));
    }

    private void openEditor(Player player, EditorSession session) {
        editorSessions.put(player.getUniqueId(), session);
        Inventory inventory = Bukkit.createInventory(player, 54, session.readOnly() ? "View Recipe" : "Edit Recipe");
        renderEditor(player, session, inventory);
        player.openInventory(inventory);
        openMenus.put(player.getUniqueId(), new OpenMenu(Screen.EDITOR, 0, "", session.recipe().id(), null, inventory));
    }

    private void renderEditor(Player player, EditorSession session, Inventory inventory) {
        fill(inventory);
        for (int i = 0; i < GRID_SLOTS.length; i++) {
            if (!isActiveIngredient(session.recipe(), i)) {
                inventory.setItem(GRID_SLOTS[i], GuiUtil.filler());
                continue;
            }
            ItemStack ingredient = session.ingredient(i);
            IngredientSpec spec = session.ingredientSpec(i);
            if (GuiUtil.isEmpty(ingredient) && spec != null && spec.isTagOnly()) {
                inventory.setItem(GRID_SLOTS[i], displayTagIngredient(spec, 0));
            } else {
                inventory.setItem(GRID_SLOTS[i], GuiUtil.isEmpty(ingredient) ? ingredientPlaceholder(session.recipe(), i) : displayIngredient(session, ingredient));
            }
        }
        ItemStack result = session.result();
        inventory.setItem(RESULT_SLOT, GuiUtil.isEmpty(result) ? GuiUtil.item(Material.RED_STAINED_GLASS_PANE, GuiUtil.Tone.DANGER, "Result Slot", "Place the output item here.") : displayResult(session, result));
        inventory.setItem(4, GuiUtil.item(Material.BOOK, GuiUtil.Tone.INFO, session.recipe().kind().displayName(), session.recipe().enabled() ? "Enabled" : "Disabled"));
        if (hasSelectedItemIngredient(session)) {
            renderMatcherControls(inventory, session);
        } else {
            renderRecipeControls(inventory, session);
            if (hasSelectedTagIngredient(session)) {
                inventory.setItem(41, GuiUtil.item(Material.ARROW, GuiUtil.Tone.WARNING, "Clear Selection"));
            }
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

    private ItemStack displayTagIngredient(IngredientSpec spec, int cycleStep) {
        List<Material> materials = IngredientMatcher.usableTagValues(spec.tagKey());
        if (materials.isEmpty()) {
            return GuiUtil.item(Material.BARRIER, GuiUtil.Tone.DANGER, "#" + spec.tagKey(), "Tag ingredient", "Unknown or empty item tag.");
        }
        Material material = materials.get(Math.floorMod(cycleStep, materials.size()));
        return GuiUtil.namedClone(new ItemStack(material), "#" + spec.tagKey(), GuiUtil.Tone.INFO, List.of(
            "Tag ingredient",
            "Matches any item in this tag."
        ));
    }

    private ItemStack ingredientPlaceholder(ManagedRecipe recipe, int index) {
        String name = switch (recipe.kind().canonical()) {
            case BREWING -> index == 0 ? "Potion Input" : "Brewing Ingredient";
            case SMITHING -> switch (index) {
                case 0 -> "Smithing Template";
                case 1 -> "Equipment";
                default -> "Addition";
            };
            case SMELTING, SMOKING, BLASTING, CAMPFIRE, STONECUTTING, COOKING -> "Input Item";
            default -> "Ingredient";
        };
        return GuiUtil.item(Material.GRAY_STAINED_GLASS_PANE, GuiUtil.Tone.MUTED, name, "Place an item here.", "Right-click with an empty hand to set an item tag.");
    }

    private void renderRecipeControls(Inventory inventory, EditorSession session) {
        if (session.readOnly()) {
            renderRecipeDetails(inventory, session.recipe());
            return;
        }
        RecipeConditions conditions = session.recipe().conditions();
        inventory.setItem(14, GuiUtil.item(session.recipe().enabled() ? Material.LIME_DYE : Material.GRAY_DYE, session.recipe().enabled() ? GuiUtil.Tone.SUCCESS : GuiUtil.Tone.MUTED, "Recipe Enabled: " + session.recipe().enabled(), "Click to toggle."));
        inventory.setItem(15, GuiUtil.item(Material.ENDER_EYE, GuiUtil.Tone.WARNING, "Allowed Dimensions", conditions.dimensions().isEmpty() ? "Any dimension." : String.join(", ", conditions.dimensions())));
        inventory.setItem(16, GuiUtil.item(Material.GRASS_BLOCK, GuiUtil.Tone.WARNING, "Allowed Biomes", conditions.biomes().isEmpty() ? "Any biome." : String.join(", ", conditions.biomes())));
        inventory.setItem(23, GuiUtil.item(Material.WATER_BUCKET, GuiUtil.Tone.WARNING, "Weather: " + conditions.weather().displayName(), "Click to cycle."));
        inventory.setItem(25, limitItem("Per-Player Limit", session.recipe().playerLimit()));
        inventory.setItem(32, GuiUtil.item(Material.EXPERIENCE_BOTTLE, GuiUtil.Tone.WARNING, "Minimum XP Level: " + conditions.minimumExperienceLevel(), "Click to edit."));
        inventory.setItem(34, limitItem("Global Limit", session.recipe().globalLimit()));
        if (session.recipe().kind().isCraftingTable()) {
            inventory.setItem(41, allowCraftersItem(session.recipe()));
        }
        renderWorkstationControls(inventory, session.recipe());
    }

    private void renderRecipeDetails(Inventory inventory, ManagedRecipe recipe) {
        RecipeConditions conditions = recipe.conditions();

        inventory.setItem(14, GuiUtil.item(recipe.enabled() ? Material.LIME_DYE : Material.GRAY_DYE, recipe.enabled() ? GuiUtil.Tone.SUCCESS : GuiUtil.Tone.MUTED, "Recipe Enabled: " + recipe.enabled()));
        inventory.setItem(15, conditions.dimensions().isEmpty()
            ? noRequirementItem("No dimension requirement!")
            : GuiUtil.item(Material.ENDER_EYE, GuiUtil.Tone.WARNING, "Allowed Dimensions", String.join(", ", conditions.dimensions())));
        inventory.setItem(16, conditions.biomes().isEmpty()
            ? noRequirementItem("No biome requirement!")
            : GuiUtil.item(Material.GRASS_BLOCK, GuiUtil.Tone.WARNING, "Allowed Biomes", String.join(", ", conditions.biomes())));
        inventory.setItem(23, conditions.weather() == WeatherMode.ANY
            ? noRequirementItem("No weather requirement!")
            : GuiUtil.item(Material.WATER_BUCKET, GuiUtil.Tone.WARNING, "Weather: " + conditions.weather().displayName()));
        inventory.setItem(25, recipe.playerLimit().enabled()
            ? limitDetailItem("Per-Player Limit", recipe.playerLimit())
            : noRequirementItem("No per-player limit!"));
        inventory.setItem(32, conditions.minimumExperienceLevel() == 0
            ? noRequirementItem("No XP requirement!")
            : GuiUtil.item(Material.EXPERIENCE_BOTTLE, GuiUtil.Tone.WARNING, "Minimum XP Level: " + conditions.minimumExperienceLevel()));
        inventory.setItem(34, recipe.globalLimit().enabled()
            ? limitDetailItem("Global Limit", recipe.globalLimit())
            : noRequirementItem("No global limit!"));
        renderWorkstationControls(inventory, recipe);
    }

    private void renderWorkstationControls(Inventory inventory, ManagedRecipe recipe) {
        if (recipe.kind().isCooking()) {
            inventory.setItem(43, GuiUtil.item(Material.CLOCK, GuiUtil.Tone.WARNING, "Cook Time: " + recipe.cookTimeTicks() + " ticks", "Click to edit."));
            inventory.setItem(44, GuiUtil.item(Material.EXPERIENCE_BOTTLE, GuiUtil.Tone.WARNING, "Experience: " + recipe.experience(), "Click to edit."));
        } else if (recipe.kind() == RecipeKind.BREWING) {
            inventory.setItem(43, GuiUtil.item(Material.CLOCK, GuiUtil.Tone.WARNING, "Brew Time: " + recipe.brewTimeTicks() + " ticks", "Click to edit."));
        } else if (recipe.kind() == RecipeKind.SMITHING) {
            inventory.setItem(43, GuiUtil.item(recipe.copyDataComponents() ? Material.LIME_DYE : Material.GRAY_DYE, recipe.copyDataComponents() ? GuiUtil.Tone.SUCCESS : GuiUtil.Tone.MUTED, "Copy Data Components: " + recipe.copyDataComponents(), "Click to toggle."));
        }
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
        disabledBlink = !disabledBlink;
        for (Player player : Bukkit.getOnlinePlayers()) {
            OpenMenu open = openMenus.get(player.getUniqueId());
            EditorSession session = editorSessions.get(player.getUniqueId());
            if (open == null) {
                continue;
            }
            Inventory inventory = player.getOpenInventory().getTopInventory();
            if (open.screen == Screen.MAIN) {
                if (!isCurrentMenu(inventory, open)) {
                    discardStaleMenu(player, open);
                    continue;
                }
                renderMainRecipeIcons(player, open, inventory);
                continue;
            }
            if (open.screen != Screen.EDITOR || session == null) {
                continue;
            }
            if (!isCurrentMenu(inventory, open)) {
                discardStaleMenu(player, open);
                continue;
            }
            int tagCycle = session.advanceTagCycle();
            for (int i = 0; i < GRID_SLOTS.length; i++) {
                IngredientSpec spec = session.ingredientSpec(i);
                if (spec != null && spec.isTagOnly() && GuiUtil.isEmpty(session.ingredient(i))) {
                    inventory.setItem(GRID_SLOTS[i], displayTagIngredient(spec, tagCycle + i));
                }
            }
            if (session.selectedSlot() < 0) {
                continue;
            }
            ItemStack ingredient = session.ingredient(session.selectedSlot());
            IngredientSpec selectedSpec = session.ingredientSpec(session.selectedSlot());
            if (GuiUtil.isEmpty(ingredient)) {
                if (selectedSpec == null || !selectedSpec.isTagOnly()) {
                    session.setSelectedSlot(-1);
                } else {
                    int slot = GRID_SLOTS[session.selectedSlot()];
                    inventory.setItem(slot, session.flipBlink()
                        ? GuiUtil.item(Material.LIME_STAINED_GLASS_PANE, GuiUtil.Tone.SUCCESS, "Selected Ingredient")
                        : displayTagIngredient(selectedSpec, tagCycle + session.selectedSlot()));
                }
                continue;
            }
            int slot = GRID_SLOTS[session.selectedSlot()];
            inventory.setItem(slot, session.flipBlink()
                ? GuiUtil.item(Material.LIME_STAINED_GLASS_PANE, GuiUtil.Tone.SUCCESS, "Selected Ingredient")
                : ingredient);
        }
    }

    private void promptMainSearch(Player player, RecipeListFilter filter) {
        openMenus.remove(player.getUniqueId());
        prompts.prompt(player, "Type a recipe search query. Type clear to clear search.", text -> openMain(player, 0, text.equalsIgnoreCase("cancel") || text.equalsIgnoreCase("clear") ? "" : text, filter));
    }

    private void promptVanillaSearch(Player player, int page) {
        openMenus.remove(player.getUniqueId());
        prompts.prompt(player, "Type a vanilla recipe search query. Type clear to clear search.", text -> openVanillaList(player, text.equalsIgnoreCase("cancel") ? page : 0, text.equalsIgnoreCase("cancel") || text.equalsIgnoreCase("clear") ? "" : text));
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
        IngredientSpec spec = session.recipe().ingredient(selected);
        if (GuiUtil.isEmpty(item)) {
            return spec == null ? new IngredientSpec() : spec.copy();
        }
        if (spec == null || spec.isTagOnly()) {
            spec = IngredientSpec.fromSample(item);
            session.recipe().setIngredient(selected, spec);
        }
        return spec;
    }

    private boolean hasSelectedItemIngredient(EditorSession session) {
        int selected = session.selectedSlot();
        return selected >= 0 && !GuiUtil.isEmpty(session.ingredient(selected));
    }

    private boolean hasSelectedTagIngredient(EditorSession session) {
        int selected = session.selectedSlot();
        IngredientSpec spec = selected < 0 ? null : session.ingredientSpec(selected);
        return spec != null && spec.isTagOnly() && GuiUtil.isEmpty(session.ingredient(selected));
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

    private List<ManagedRecipe> filteredManagedRecipes(String query, RecipeListFilter filter) {
        String lower = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return registry.recipes().stream()
            .filter(recipe -> switch (filter) {
                case ALL -> true;
                case CUSTOM -> recipe.enabled();
                case DISABLED -> !recipe.enabled();
            })
            .filter(recipe -> lower.isBlank()
                || recipe.displayLabel().toLowerCase(Locale.ROOT).contains(lower)
                || ItemText.displayName(recipe.result()).toLowerCase(Locale.ROOT).contains(lower))
            .sorted(mainRecipeComparator())
            .toList();
    }

    static Comparator<ManagedRecipe> mainRecipeComparator() {
        return Comparator.comparingInt(GuiManager::recipeKindRank)
            .thenComparing(Comparator.comparingInt(GuiManager::nbtMatcherRank).reversed())
            .thenComparingInt(GuiManager::recipeStateRank)
            .thenComparing(ManagedRecipe::displayLabel);
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

    private ItemStack recipeIcon(ManagedRecipe recipe, boolean admin, boolean blinkBarrier) {
        List<String> lore = new ArrayList<>();
        lore.add(recipe.kind().displayName());
        lore.add(recipe.enabled() ? "Enabled" : "Disabled - not craftable");
        lore.add(recipe.isOverride() ? "Overrides " + recipe.sourceKey() : "Custom recipe");
        if (recipe.allowCrafters()) {
            lore.add("Crafters allowed");
        }
        addLimitLore(lore, "Per-player", recipe.playerLimit());
        addLimitLore(lore, "Global", recipe.globalLimit());
        lore.add(admin ? "Left-click edit. Shift-right-click delete/revert." : "Left-click view.");
        if (!recipe.enabled() && blinkBarrier) {
            return GuiUtil.item(Material.BARRIER, GuiUtil.Tone.DANGER, ItemText.displayName(recipe.result()), lore);
        }
        ItemStack result = recipe.result();
        return GuiUtil.namedClone(result, ItemText.displayName(result), recipe.enabled() ? GuiUtil.Tone.INFO : GuiUtil.Tone.MUTED, lore);
    }

    private void renderMainRecipeIcons(Player player, OpenMenu open, Inventory inventory) {
        List<ManagedRecipe> recipes = filteredManagedRecipes(open.query, open.filter);
        boolean admin = isAdmin(player);
        for (int i = 0; i < MAIN_LIST_SLOTS.length; i++) {
            int index = open.page * MAIN_LIST_SLOTS.length + i;
            if (index >= recipes.size()) {
                break;
            }
            ManagedRecipe recipe = recipes.get(index);
            if (!recipe.enabled()) {
                inventory.setItem(MAIN_LIST_SLOTS[i], recipeIcon(recipe, admin, disabledBlink));
            }
        }
    }

    private ItemStack filterItem(RecipeListFilter filter) {
        return GuiUtil.item(Material.HOPPER, GuiUtil.Tone.WARNING, "Filter: " + filter.displayName(), "Click to cycle.", "All, Custom, Disabled");
    }

    private ItemStack limitItem(String name, RecipeLimit limit) {
        return GuiUtil.item(Material.CLOCK, limit.enabled() ? GuiUtil.Tone.WARNING : GuiUtil.Tone.MUTED, name, limitDescription(limit), "Click to edit.");
    }

    private ItemStack limitDetailItem(String name, RecipeLimit limit) {
        return GuiUtil.item(Material.CLOCK, GuiUtil.Tone.WARNING, name, limitDescription(limit));
    }

    private ItemStack noRequirementItem(String name) {
        return GuiUtil.item(Material.LIME_STAINED_GLASS_PANE, GuiUtil.Tone.SUCCESS, name);
    }

    private ItemStack allowCraftersItem(ManagedRecipe recipe) {
        List<String> lore = new ArrayList<>();
        lore.add(recipe.allowCrafters() ? "Enabled" : "Disabled");
        lore.add("Crafters bypass dimension, weather, XP, biome, and crafting limits.");
        lore.add("Only enable when those requirements are not configured.");
        lore.add("Click to toggle.");
        return GuiUtil.item(Material.CRAFTER, recipe.allowCrafters() ? GuiUtil.Tone.SUCCESS : GuiUtil.Tone.MUTED, "Allow Crafters: " + recipe.allowCrafters(), lore);
    }

    private void addLimitLore(List<String> lore, String label, RecipeLimit limit) {
        if (limit.enabled()) {
            lore.add(label + " limit: " + limit.crafts() + " per " + formatDuration(limit.windowSeconds()));
        }
    }

    private String limitDescription(RecipeLimit limit) {
        return limit.enabled() ? limit.crafts() + " crafts per " + formatDuration(limit.windowSeconds()) : "No limit.";
    }

    private static int recipeKindRank(ManagedRecipe recipe) {
        return recipe.kind().canonical().ordinal();
    }

    private static int recipeStateRank(ManagedRecipe recipe) {
        if (!recipe.enabled()) {
            return 2;
        }
        return recipe.isOverride() ? 1 : 0;
    }

    private static int nbtMatcherRank(ManagedRecipe recipe) {
        int rank = 0;
        for (IngredientSpec spec : recipe.ingredients()) {
            if (spec == null || spec.isEmpty()) {
                continue;
            }
            for (MatcherType matcher : spec.matchers()) {
                rank += nbtMatcherWeight(matcher);
            }
        }
        return rank;
    }

    private static int nbtMatcherWeight(MatcherType matcher) {
        return switch (matcher) {
            case EXACT -> 100;
            case ITEM_NAME, LORE_CONTAINS, ENCHANTMENTS -> 10;
            case MATERIAL, TAG -> 0;
        };
    }

    private long parseDurationSeconds(String input) {
        String value = input.trim().toLowerCase(Locale.ROOT);
        long multiplier = 1L;
        if (value.endsWith("s")) {
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("m")) {
            value = value.substring(0, value.length() - 1);
            multiplier = 60L;
        } else if (value.endsWith("h")) {
            value = value.substring(0, value.length() - 1);
            multiplier = 3600L;
        } else if (value.endsWith("d")) {
            value = value.substring(0, value.length() - 1);
            multiplier = 86400L;
        }
        return Long.parseLong(value) * multiplier;
    }

    private String formatDuration(long seconds) {
        if (seconds % 86400L == 0L && seconds >= 86400L) {
            return (seconds / 86400L) + "d";
        }
        if (seconds % 3600L == 0L && seconds >= 3600L) {
            return (seconds / 3600L) + "h";
        }
        if (seconds % 60L == 0L && seconds >= 60L) {
            return (seconds / 60L) + "m";
        }
        return seconds + "s";
    }

    private void fill(Inventory inventory) {
        ItemStack filler = GuiUtil.filler();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    private ItemStack workstationItem(WorkstationOption option) {
        return GuiUtil.item(option.material(), GuiUtil.Tone.SUCCESS, option.name(), "Supported", option.description());
    }

    private WorkstationOption workstationOption(int slot) {
        for (WorkstationOption option : WORKSTATION_OPTIONS) {
            if (option.slot() == slot) {
                return option;
            }
        }
        return null;
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

    private boolean isCurrentMenu(Inventory inventory, OpenMenu open) {
        return inventory == open.inventory();
    }

    private void discardStaleMenu(Player player, OpenMenu open) {
        openMenus.remove(player.getUniqueId(), open);
        if (open.screen() == Screen.EDITOR) {
            releaseEditor(player);
        }
    }

    private boolean isAdmin(Player player) {
        return player.hasPermission("configurablecrafts.admin");
    }

    private int ingredientIndex(ManagedRecipe recipe, int rawSlot) {
        int index = indexOf(GRID_SLOTS, rawSlot);
        return index >= 0 && isActiveIngredient(recipe, index) ? index : -1;
    }

    private boolean isActiveIngredient(ManagedRecipe recipe, int index) {
        RecipeKind kind = recipe.kind().canonical();
        if (kind == RecipeKind.SHAPED || kind == RecipeKind.SHAPELESS) {
            return index >= 0 && index < 9;
        }
        if (kind == RecipeKind.BREWING) {
            return index == 0 || index == 1;
        }
        if (kind == RecipeKind.SMITHING) {
            return index >= 0 && index <= 2;
        }
        if (kind.isCooking() || kind == RecipeKind.STONECUTTING) {
            return index == 0;
        }
        return false;
    }

    private void normalizeActiveIngredients(ManagedRecipe recipe) {
        for (int i = 0; i < 9; i++) {
            if (!isActiveIngredient(recipe, i)) {
                recipe.setIngredient(i, null);
            }
        }
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

    private record OpenMenu(Screen screen, int page, String query, String recipeId, RecipeListFilter filter, Inventory inventory) {
    }

    private record WorkstationOption(int slot, Material material, RecipeKind kind, String name, String description) {
    }
}
