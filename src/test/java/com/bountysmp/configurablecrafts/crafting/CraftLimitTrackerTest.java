package com.bountysmp.configurablecrafts.crafting;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.bountysmp.configurablecrafts.BukkitTest;
import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.RecipeKind;
import java.io.File;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CraftLimitTrackerTest extends BukkitTest {
    @TempDir
    File tempDir;

    @Test
    void playerLimitBlocksOnlyThatPlayerUntilWindowExpires() {
        AtomicLong now = new AtomicLong(1_000L);
        CraftLimitTracker tracker = tracker(now);
        ManagedRecipe recipe = recipe();
        recipe.playerLimit().set(2, 10);
        UUID player = UUID.randomUUID();

        assertNull(tracker.tryConsume(recipe, player, 2));
        assertNotNull(tracker.tryConsume(recipe, player, 1));
        assertNull(tracker.tryConsume(recipe, UUID.randomUUID(), 1));

        now.addAndGet(10_000L);
        assertNull(tracker.tryConsume(recipe, player, 1));
    }

    @Test
    void globalLimitIsSharedAcrossPlayers() {
        CraftLimitTracker tracker = tracker(new AtomicLong(1_000L));
        ManagedRecipe recipe = recipe();
        recipe.globalLimit().set(2, 60);

        assertNull(tracker.tryConsume(recipe, UUID.randomUUID(), 1));
        assertNull(tracker.tryConsume(recipe, UUID.randomUUID(), 1));
        assertNotNull(tracker.tryConsume(recipe, UUID.randomUUID(), 1));
    }

    @Test
    void checkDoesNotConsumeLimit() {
        CraftLimitTracker tracker = tracker(new AtomicLong(1_000L));
        ManagedRecipe recipe = recipe();
        recipe.globalLimit().set(1, 60);

        assertNull(tracker.check(recipe, UUID.randomUUID(), 1));
        assertNull(tracker.tryConsume(recipe, UUID.randomUUID(), 1));
        assertNotNull(tracker.tryConsume(recipe, UUID.randomUUID(), 1));
    }

    @Test
    void usageWindowsReloadFromDisk() {
        AtomicLong now = new AtomicLong(1_000L);
        File file = new File(tempDir, "limit-usage.yml");
        ManagedRecipe recipe = recipe();
        recipe.playerLimit().set(1, 60);
        UUID player = UUID.randomUUID();
        CraftLimitTracker first = new CraftLimitTracker(null, file, now::get);

        assertNull(first.tryConsume(recipe, player, 1));

        CraftLimitTracker second = new CraftLimitTracker(null, file, now::get);
        second.load();
        assertNotNull(second.tryConsume(recipe, player, 1));
    }

    private CraftLimitTracker tracker(AtomicLong now) {
        return new CraftLimitTracker(null, new File(tempDir, "limit-usage.yml"), now::get);
    }

    private ManagedRecipe recipe() {
        return new ManagedRecipe("limited", RecipeKind.SHAPED);
    }
}
