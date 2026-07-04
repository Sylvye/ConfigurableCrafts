package com.bountysmp.configurablecrafts;

import com.bountysmp.configurablecrafts.command.ConfigurableCraftsCommand;
import com.bountysmp.configurablecrafts.crafting.BrewingRecipeService;
import com.bountysmp.configurablecrafts.crafting.CookingRecipeListener;
import com.bountysmp.configurablecrafts.crafting.CraftLimitTracker;
import com.bountysmp.configurablecrafts.crafting.CraftingListener;
import com.bountysmp.configurablecrafts.crafting.ManagedRecipeRegistry;
import com.bountysmp.configurablecrafts.crafting.WorkstationUseListener;
import com.bountysmp.configurablecrafts.gui.ChatPromptManager;
import com.bountysmp.configurablecrafts.gui.GuiManager;
import com.bountysmp.configurablecrafts.storage.RecipeRepository;
import java.io.File;
import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigurableCraftsPlugin extends JavaPlugin {
    private RecipeRepository repository;
    private ManagedRecipeRegistry recipeRegistry;
    private CraftLimitTracker craftLimitTracker;
    private BrewingRecipeService brewingRecipeService;
    private ChatPromptManager chatPromptManager;
    private GuiManager guiManager;

    @Override
    public void onEnable() {
        this.repository = new RecipeRepository(new File(getDataFolder(), "recipes.yml"));
        this.recipeRegistry = new ManagedRecipeRegistry(this, repository);
        this.craftLimitTracker = new CraftLimitTracker(this, new File(getDataFolder(), "limit-usage.yml"));
        this.brewingRecipeService = new BrewingRecipeService(recipeRegistry);
        this.chatPromptManager = new ChatPromptManager(this);
        this.guiManager = new GuiManager(this, recipeRegistry, chatPromptManager);

        craftLimitTracker.load();
        recipeRegistry.cacheVanillaRecipes();
        recipeRegistry.load();
        recipeRegistry.applyAll();

        ConfigurableCraftsCommand commandExecutor = new ConfigurableCraftsCommand(guiManager);
        PluginCommand command = Objects.requireNonNull(getCommand("configurablecrafts"), "configurablecrafts command");
        command.setExecutor(commandExecutor);

        getServer().getPluginManager().registerEvents(chatPromptManager, this);
        getServer().getPluginManager().registerEvents(guiManager, this);
        getServer().getPluginManager().registerEvents(new CraftingListener(recipeRegistry, craftLimitTracker), this);
        getServer().getPluginManager().registerEvents(new CookingRecipeListener(recipeRegistry, craftLimitTracker), this);
        getServer().getPluginManager().registerEvents(new WorkstationUseListener(recipeRegistry, craftLimitTracker), this);
        getServer().getPluginManager().registerEvents(brewingRecipeService, this);
        guiManager.startBlinkTask();
    }

    @Override
    public void onDisable() {
        if (guiManager != null) {
            guiManager.shutdown();
        }
        if (brewingRecipeService != null) {
            brewingRecipeService.shutdown();
        }
        if (recipeRegistry != null) {
            recipeRegistry.shutdown();
        }
        if (craftLimitTracker != null) {
            craftLimitTracker.shutdown();
        }
    }
}
