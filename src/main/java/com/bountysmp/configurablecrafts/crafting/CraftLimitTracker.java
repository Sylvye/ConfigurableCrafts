package com.bountysmp.configurablecrafts.crafting;

import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.RecipeLimit;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class CraftLimitTracker {
    private static final long SAVE_DELAY_TICKS = 100L;

    private final Plugin plugin;
    private final File file;
    private final LongSupplier clock;
    private final Map<String, UsageWindow> globalUsage = new HashMap<>();
    private final Map<String, Map<UUID, UsageWindow>> playerUsage = new HashMap<>();
    private BukkitTask pendingSave;

    public CraftLimitTracker(Plugin plugin, File file) {
        this(plugin, file, System::currentTimeMillis);
    }

    CraftLimitTracker(Plugin plugin, File file, LongSupplier clock) {
        this.plugin = plugin;
        this.file = file;
        this.clock = clock;
    }

    public void load() {
        globalUsage.clear();
        playerUsage.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        readGlobal(yaml.getConfigurationSection("global"));
        readPlayers(yaml.getConfigurationSection("players"));
        cleanupExpired();
    }

    public void shutdown() {
        if (pendingSave != null) {
            pendingSave.cancel();
            pendingSave = null;
        }
        saveNow();
    }

    public String tryConsume(ManagedRecipe recipe, UUID playerId, int crafts) {
        String failure = check(recipe, playerId, crafts);
        if (failure != null || crafts <= 0) {
            return failure;
        }
        consume(recipe, playerId, crafts);
        return null;
    }

    public String check(ManagedRecipe recipe, UUID playerId, int crafts) {
        if (crafts <= 0) {
            return null;
        }
        long now = clock.getAsLong();
        RecipeLimit globalLimit = recipe.globalLimit();
        RecipeLimit playerLimit = recipe.playerLimit();
        UsageWindow globalWindow = window(globalUsage, recipe.id(), globalLimit, now);
        UsageWindow playerWindow = playerId == null ? null : window(playerUsage.computeIfAbsent(recipe.id(), ignored -> new HashMap<>()), playerId, playerLimit, now);

        String globalFailure = failure("global", globalLimit, globalWindow, crafts, now);
        if (globalFailure != null) {
            return globalFailure;
        }
        String playerFailure = failure("player", playerLimit, playerWindow, crafts, now);
        if (playerFailure != null) {
            return playerFailure;
        }
        return null;
    }

    public void consume(ManagedRecipe recipe, UUID playerId, int crafts) {
        if (crafts <= 0) {
            return;
        }
        long now = clock.getAsLong();
        RecipeLimit globalLimit = recipe.globalLimit();
        RecipeLimit playerLimit = recipe.playerLimit();
        UsageWindow globalWindow = window(globalUsage, recipe.id(), globalLimit, now);
        UsageWindow playerWindow = playerId == null ? null : window(playerUsage.computeIfAbsent(recipe.id(), ignored -> new HashMap<>()), playerId, playerLimit, now);

        boolean changed = false;
        if (globalLimit.enabled()) {
            globalWindow.used += crafts;
            changed = true;
        }
        if (playerLimit.enabled() && playerWindow != null) {
            playerWindow.used += crafts;
            changed = true;
        }
        if (changed) {
            scheduleSave();
        }
    }

    private String failure(String label, RecipeLimit limit, UsageWindow window, int crafts, long now) {
        if (!limit.enabled() || window == null || window.used + crafts <= limit.crafts()) {
            return null;
        }
        long remaining = Math.max(1L, ((window.startedAtMillis + limit.windowSeconds() * 1000L) - now + 999L) / 1000L);
        return "This recipe has reached its " + label + " crafting limit. Try again in " + formatDuration(remaining) + ".";
    }

    private void scheduleSave() {
        if (plugin == null) {
            saveNow();
            return;
        }
        if (pendingSave != null) {
            return;
        }
        pendingSave = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingSave = null;
            saveNow();
        }, SAVE_DELAY_TICKS);
    }

    void saveNow() {
        cleanupExpired();
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection global = yaml.createSection("global");
        for (Map.Entry<String, UsageWindow> entry : globalUsage.entrySet()) {
            writeWindow(global.createSection(entry.getKey()), entry.getValue());
        }
        ConfigurationSection players = yaml.createSection("players");
        for (Map.Entry<String, Map<UUID, UsageWindow>> recipeEntry : playerUsage.entrySet()) {
            ConfigurationSection recipeSection = players.createSection(recipeEntry.getKey());
            for (Map.Entry<UUID, UsageWindow> playerEntry : recipeEntry.getValue().entrySet()) {
                writeWindow(recipeSection.createSection(playerEntry.getKey().toString()), playerEntry.getValue());
            }
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save " + file.getAbsolutePath(), exception);
        }
    }

    private void readGlobal(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String recipeId : section.getKeys(false)) {
            UsageWindow window = readWindow(section.getConfigurationSection(recipeId));
            if (window != null) {
                globalUsage.put(recipeId, window);
            }
        }
    }

    private void readPlayers(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String recipeId : section.getKeys(false)) {
            ConfigurationSection recipeSection = section.getConfigurationSection(recipeId);
            if (recipeSection == null) {
                continue;
            }
            Map<UUID, UsageWindow> recipeUsage = new HashMap<>();
            for (String uuidText : recipeSection.getKeys(false)) {
                try {
                    UsageWindow window = readWindow(recipeSection.getConfigurationSection(uuidText));
                    if (window != null) {
                        recipeUsage.put(UUID.fromString(uuidText), window);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Ignore invalid stored UUIDs.
                }
            }
            if (!recipeUsage.isEmpty()) {
                playerUsage.put(recipeId, recipeUsage);
            }
        }
    }

    private UsageWindow readWindow(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        int used = section.getInt("used", 0);
        long startedAtMillis = section.getLong("started-at-millis", 0L);
        long windowSeconds = section.getLong("window-seconds", 0L);
        if (used <= 0 || startedAtMillis <= 0L || windowSeconds <= 0L) {
            return null;
        }
        return new UsageWindow(used, startedAtMillis, windowSeconds);
    }

    private void writeWindow(ConfigurationSection section, UsageWindow window) {
        section.set("used", window.used);
        section.set("started-at-millis", window.startedAtMillis);
        section.set("window-seconds", window.windowSeconds);
    }

    private <K> UsageWindow window(Map<K, UsageWindow> usage, K key, RecipeLimit limit, long now) {
        if (!limit.enabled()) {
            return null;
        }
        UsageWindow window = usage.get(key);
        if (window == null || window.expired(now) || window.windowSeconds != limit.windowSeconds()) {
            window = new UsageWindow(0, now, limit.windowSeconds());
            usage.put(key, window);
        }
        return window;
    }

    private void cleanupExpired() {
        long now = clock.getAsLong();
        globalUsage.values().removeIf(window -> window.expired(now));
        Iterator<Map.Entry<String, Map<UUID, UsageWindow>>> recipeIterator = playerUsage.entrySet().iterator();
        while (recipeIterator.hasNext()) {
            Map<UUID, UsageWindow> usage = recipeIterator.next().getValue();
            usage.values().removeIf(window -> window.expired(now));
            if (usage.isEmpty()) {
                recipeIterator.remove();
            }
        }
    }

    private String formatDuration(long seconds) {
        if (seconds >= 3600L) {
            long hours = seconds / 3600L;
            long minutes = (seconds % 3600L) / 60L;
            return minutes == 0L ? hours + "h" : hours + "h " + minutes + "m";
        }
        if (seconds >= 60L) {
            long minutes = seconds / 60L;
            long remainder = seconds % 60L;
            return remainder == 0L ? minutes + "m" : minutes + "m " + remainder + "s";
        }
        return seconds + "s";
    }

    private static final class UsageWindow {
        private int used;
        private final long startedAtMillis;
        private final long windowSeconds;

        private UsageWindow(int used, long startedAtMillis, long windowSeconds) {
            this.used = used;
            this.startedAtMillis = startedAtMillis;
            this.windowSeconds = windowSeconds;
        }

        private boolean expired(long now) {
            return now >= startedAtMillis + windowSeconds * 1000L;
        }
    }
}
