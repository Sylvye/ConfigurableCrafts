package com.bountysmp.configurablecrafts.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bountysmp.configurablecrafts.model.IngredientSpec;
import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.RecipeKind;
import com.bountysmp.configurablecrafts.storage.RecipeRepository;
import io.papermc.paper.potion.PotionMix;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class RecipeDiscoveryTest {
    @TempDir
    File tempDir;

    private RecipeServerMock server;
    private Plugin plugin;
    private RecipeRepository repository;
    private ManagedRecipeRegistry registry;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock(new RecipeServerMock());
        plugin = MockBukkit.createMockPlugin();
        repository = new RecipeRepository(new File(tempDir, "recipes.yml"));
        registry = new ManagedRecipeRegistry(plugin, repository, new NoOpPotionMixes());
        Bukkit.getPluginManager().registerEvents(new RecipeDiscoveryListener(plugin, registry), plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void firstJoinDiscoversAllRecipesOnTheNextTick() {
        ManagedRecipe first = recipe("first", RecipeKind.SHAPED);
        ManagedRecipe second = recipe("second", RecipeKind.SHAPELESS);
        registry.upsert(first);
        registry.upsert(second);

        PlayerMock player = server.addPlayer();
        assertTrue(player.getDiscoveredRecipes().isEmpty());
        server.getScheduler().performOneTick();

        assertEquals(Set.of(key(first), key(second)), player.getDiscoveredRecipes());
    }

    @ParameterizedTest
    @EnumSource(value = RecipeKind.class, names = {"SHAPED", "SHAPELESS"})
    void customItemRecipesKeepExactSignaturesAfterReloadAndDiscoverWithoutBaseItems(RecipeKind kind) {
        ItemStack prism = new ItemStack(Material.AMETHYST_SHARD);
        prism.editMeta(meta -> {
            meta.displayName(Component.text("Onyx Prism"));
            meta.lore(List.of(Component.text("A dark crystal.")));
        });
        ManagedRecipe managed = recipe("onyx_recipe", kind);
        managed.setIngredient(0, IngredientSpec.fromExactSample(prism));
        registry.upsert(managed);
        registry.load();
        registry.applyAll();

        PlayerMock player = server.addPlayer();
        server.getScheduler().performOneTick();
        assertTrue(player.getDiscoveredRecipes().contains(key(managed)));
        player.getInventory().addItem(prism);
        assertFalse(player.getInventory().containsAtLeast(new ItemStack(prism.getType()), 1));

        ManagedRecipe loaded = registry.byId(managed.id());
        Recipe registered = Bukkit.getRecipe(key(managed));
        assertEquals(RecipePattern.signature(loaded), RecipePattern.signature(registered));
        RecipeChoice choice = registered instanceof ShapedRecipe shaped
            ? shaped.getChoiceMap().get('A') : ((ShapelessRecipe) registered).getChoiceList().getFirst();
        assertTrue(choice.test(prism));
        assertFalse(choice.test(new ItemStack(prism.getType())));
        assertTrue(RecipePattern.matches(loaded, new ItemStack[] {prism, null, null, null}));
        assertFalse(RecipePattern.matches(loaded,
            new ItemStack[] {new ItemStack(prism.getType()), null, null, null}));
    }

    @Test
    void returningPlayerReceivesRecipesCreatedWhileOffline() {
        ManagedRecipe first = recipe("first", RecipeKind.SHAPED);
        registry.upsert(first);
        PlayerMock player = server.addPlayer();
        server.getScheduler().performOneTick();
        assertTrue(player.disconnect());
        ManagedRecipe second = recipe("second", RecipeKind.SHAPELESS);
        registry.upsert(second);
        assertFalse(player.hasDiscoveredRecipe(key(second)));

        assertTrue(player.reconnect());
        server.getScheduler().performOneTick();

        assertEquals(Set.of(key(first), key(second)), player.getDiscoveredRecipes());
    }

    @Test
    void joinTaskSkipsDisconnectedPlayers() {
        ManagedRecipe recipe = recipe("offline", RecipeKind.SHAPED);
        registry.upsert(recipe);
        PlayerMock player = server.addPlayer();
        player.disconnect();

        server.getScheduler().performOneTick();

        assertFalse(player.hasDiscoveredRecipe(key(recipe)));
    }

    @Test
    void joinTaskUsesCurrentRecipesRatherThanAJoinTimeSnapshot() {
        ManagedRecipe recipe = recipe("removed", RecipeKind.SHAPED);
        registry.upsert(recipe);
        PlayerMock player = server.addPlayer();
        registry.removeOrRevert(recipe.id());

        server.getScheduler().performOneTick();

        assertTrue(player.getDiscoveredRecipes().isEmpty());
    }

    @Test
    void startupAppliesPersistedRecipesToAllOnlinePlayers() {
        ManagedRecipe recipe = recipe("persisted", RecipeKind.SHAPED);
        repository.save(List.of(recipe));
        PlayerMock first = server.addPlayer();
        PlayerMock second = server.addPlayer();

        registry.load();
        registry.applyAll();

        assertTrue(first.hasDiscoveredRecipe(key(recipe)));
        assertTrue(second.hasDiscoveredRecipe(key(recipe)));
    }

    @ParameterizedTest
    @EnumSource(value = RecipeKind.class, names = {"BREWING"}, mode = EnumSource.Mode.EXCLUDE)
    void newRecipesReachEveryOnlinePlayer(RecipeKind kind) {
        PlayerMock first = server.addPlayer();
        PlayerMock second = server.addPlayer();
        ManagedRecipe recipe = recipe("online", kind);

        registry.upsert(recipe);

        assertTrue(first.hasDiscoveredRecipe(key(recipe)));
        assertTrue(second.hasDiscoveredRecipe(key(recipe)));
    }

    @Test
    void editsRediscoverTheSameKeyAfterRecipeDataIsRefreshed() {
        RecordingPlayer player = new RecordingPlayer(server);
        server.addPlayer(player);
        PlayerMock second = server.addPlayer();
        ManagedRecipe recipe = recipe("edited", RecipeKind.SHAPED);
        registry.upsert(recipe);
        int previousRefreshes = server.refreshes;
        recipe.setResult(new ItemStack(Material.EMERALD));
        recipe.setIngredient(0, IngredientSpec.fromSample(new ItemStack(Material.DIAMOND)));

        registry.upsert(recipe);

        assertEquals(2, player.newDiscoveries);
        assertTrue(player.lastDiscoveryRefresh > previousRefreshes);
        assertTrue(second.hasDiscoveredRecipe(key(recipe)));
        assertEquals(Material.EMERALD, Bukkit.getRecipe(key(recipe)).getResult().getType());
    }

    @Test
    void disabledAndDeletedRecipesAreRemovedAndCanBeReenabled() {
        PlayerMock first = server.addPlayer();
        PlayerMock second = server.addPlayer();
        ManagedRecipe recipe = recipe("toggle", RecipeKind.SHAPED);
        registry.upsert(recipe);

        recipe.setEnabled(false);
        registry.upsert(recipe);
        assertNull(Bukkit.getRecipe(key(recipe)));
        assertFalse(first.hasDiscoveredRecipe(key(recipe)));
        assertFalse(second.hasDiscoveredRecipe(key(recipe)));

        recipe.setEnabled(true);
        registry.upsert(recipe);
        assertTrue(first.hasDiscoveredRecipe(key(recipe)));
        assertTrue(second.hasDiscoveredRecipe(key(recipe)));

        registry.removeOrRevert(recipe.id());
        assertNull(Bukkit.getRecipe(key(recipe)));
        assertFalse(first.hasDiscoveredRecipe(key(recipe)));
        assertFalse(second.hasDiscoveredRecipe(key(recipe)));
    }

    @Test
    void requirementsDoNotHideRecipesAndSynchronizationIsIdempotent() {
        RecordingPlayer player = new RecordingPlayer(server);
        server.addPlayer(player);
        ManagedRecipe recipe = recipe("restricted", RecipeKind.SHAPED);
        recipe.conditions().setMinimumExperienceLevel(100);
        recipe.playerLimit().set(1, 60);
        registry.upsert(recipe);

        registry.syncPlayerRecipes(player);
        registry.syncPlayerRecipes(player);

        assertEquals(Set.of(key(recipe)), player.getDiscoveredRecipes());
        assertEquals(1, player.newDiscoveries);
        assertNotNull(ConditionValidator.failureReason(recipe, player));
    }

    @Test
    void invalidDisabledAndBrewingRecipesAreNotDiscovered() {
        PlayerMock player = server.addPlayer();
        ManagedRecipe invalid = recipe("invalid", RecipeKind.SHAPED);
        invalid.setResult(null);
        ManagedRecipe disabled = recipe("disabled", RecipeKind.SHAPELESS);
        disabled.setEnabled(false);
        ManagedRecipe brewing = recipe("brew", RecipeKind.BREWING);

        registry.upsert(invalid);
        registry.upsert(disabled);
        registry.upsert(brewing);
        registry.syncPlayerRecipes(player);

        assertTrue(player.getDiscoveredRecipes().isEmpty());
        assertNull(registry.byManagedKey(key(invalid)));
        assertNotNull(registry.byManagedKey(key(disabled)), "disabled cached events retain their validation tombstone");
        assertFalse(registry.byManagedKey(key(disabled)).enabled());
        assertNotNull(registry.byManagedKey(key(brewing)));
    }

    @Test
    void rejectedRegistrationsAreNotManagedOrDiscovered() {
        PlayerMock player = server.addPlayer();
        ManagedRecipe recipe = recipe("rejected", RecipeKind.SHAPED);
        server.rejectRecipes = true;

        registry.upsert(recipe);

        assertNull(Bukkit.getRecipe(key(recipe)));
        assertNull(registry.byManagedKey(key(recipe)));
        assertFalse(player.hasDiscoveredRecipe(key(recipe)));
    }

    @Test
    void revertingAnOverrideDiscoversItsRestoredVanillaSource() {
        ShapelessRecipe original = vanillaRecipe();
        registry.cacheVanillaRecipes();
        PlayerMock first = server.addPlayer();
        PlayerMock second = server.addPlayer();
        first.discoverRecipe(original.getKey());
        ManagedRecipe override = registry.fromVanilla(original);
        override.setResult(new ItemStack(Material.EMERALD));
        registry.upsert(override);
        assertFalse(first.hasDiscoveredRecipe(original.getKey()));
        assertTrue(first.hasDiscoveredRecipe(key(override)));

        registry.removeOrRevert(override.id());

        assertFalse(first.hasDiscoveredRecipe(key(override)));
        assertFalse(second.hasDiscoveredRecipe(key(override)));
        assertTrue(first.hasDiscoveredRecipe(original.getKey()));
        assertTrue(second.hasDiscoveredRecipe(original.getKey()));
        assertEquals(Material.DIAMOND, Bukkit.getRecipe(original.getKey()).getResult().getType());
    }

    @Test
    void editingAnOverrideDoesNotRediscoverItsRemovedSource() {
        ShapelessRecipe original = vanillaRecipe();
        registry.cacheVanillaRecipes();
        PlayerMock player = server.addPlayer();
        ManagedRecipe override = registry.fromVanilla(original);
        registry.upsert(override);
        override.setResult(new ItemStack(Material.EMERALD));

        registry.upsert(override);

        assertEquals(Set.of(key(override)), player.getDiscoveredRecipes());
        assertNull(Bukkit.getRecipe(original.getKey()));
    }

    @Test
    void shutdownRestoresVanillaWithoutRediscoveringCustomRecipes() {
        ShapelessRecipe original = vanillaRecipe();
        registry.cacheVanillaRecipes();
        PlayerMock player = server.addPlayer();
        ManagedRecipe override = registry.fromVanilla(original);
        ManagedRecipe custom = recipe("custom", RecipeKind.SHAPED);
        registry.upsert(override);
        registry.upsert(custom);

        registry.shutdown();
        registry.syncPlayerRecipes(player);

        assertEquals(Set.of(original.getKey()), player.getDiscoveredRecipes());
        assertNull(Bukkit.getRecipe(key(custom)));
        assertNull(Bukkit.getRecipe(key(override)));
    }

    private ManagedRecipe recipe(String id, RecipeKind kind) {
        ManagedRecipe recipe = new ManagedRecipe(id, kind);
        recipe.setResult(new ItemStack(Material.DIAMOND));
        recipe.setIngredient(0, IngredientSpec.fromSample(new ItemStack(Material.STICK)));
        if (kind == RecipeKind.SMITHING) {
            recipe.setIngredient(1, IngredientSpec.fromSample(new ItemStack(Material.DIAMOND_SWORD)));
            recipe.setIngredient(2, IngredientSpec.fromSample(new ItemStack(Material.NETHERITE_INGOT)));
        } else if (kind == RecipeKind.BREWING) {
            recipe.setIngredient(1, IngredientSpec.fromSample(new ItemStack(Material.REDSTONE)));
        }
        return recipe;
    }

    private NamespacedKey key(ManagedRecipe recipe) {
        return recipe.managedKey(plugin);
    }

    private ShapelessRecipe vanillaRecipe() {
        ShapelessRecipe recipe = new ShapelessRecipe(NamespacedKey.minecraft("discovery_test"), new ItemStack(Material.DIAMOND));
        recipe.addIngredient(Material.STICK);
        Bukkit.addRecipe(recipe);
        return recipe;
    }

    private static final class NoOpPotionMixes implements ManagedRecipeRegistry.PotionMixes {
        @Override
        public void add(PotionMix potionMix) {}

        @Override
        public void remove(NamespacedKey key) {}
    }

    private static final class RecipeServerMock extends ServerMock {
        private boolean rejectRecipes;
        private int refreshes;

        @Override
        public boolean addRecipe(Recipe recipe, boolean resendRecipes) {
            return !rejectRecipes && super.addRecipe(recipe, resendRecipes);
        }

        @Override
        public void updateRecipes() {
            refreshes++;
            super.updateRecipes();
        }
    }

    private static final class RecordingPlayer extends PlayerMock {
        private final RecipeServerMock server;
        private int newDiscoveries;
        private int lastDiscoveryRefresh;

        private RecordingPlayer(RecipeServerMock server) {
            super(server, "RecordingPlayer");
            this.server = server;
        }

        @Override
        public int discoverRecipes(Collection<NamespacedKey> recipes) {
            int discovered = super.discoverRecipes(recipes);
            newDiscoveries += discovered;
            lastDiscoveryRefresh = server.refreshes;
            return discovered;
        }

        @Override
        public boolean undiscoverRecipe(NamespacedKey recipe) {
            assertNotNull(Bukkit.getRecipe(recipe), "Forget recipes before unregistering them");
            return super.undiscoverRecipe(recipe);
        }
    }
}
