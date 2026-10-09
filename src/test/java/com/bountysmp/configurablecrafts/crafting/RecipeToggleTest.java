package com.bountysmp.configurablecrafts.crafting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.bountysmp.configurablecrafts.BukkitTest;
import com.bountysmp.configurablecrafts.api.OwnedRecipe;
import com.bountysmp.configurablecrafts.gui.*;
import com.bountysmp.configurablecrafts.model.*;
import com.bountysmp.configurablecrafts.storage.RecipeRepository;
import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;

class RecipeToggleTest extends BukkitTest {
  @TempDir File folder;
  org.bukkit.plugin.Plugin plugin;
  ManagedRecipeRegistry registry;
  GuiManager gui;
  CraftLimitTracker limits;

  // Supply Paper's effectiveName at the MockBukkit boundary, including cloned results.
  static final class NamedStack extends ItemStack {
    NamedStack(Material material) {
      super(material);
    }

    NamedStack(ItemStack source) {
      super(source.getType(), source.getAmount());
      setItemMeta(source.getItemMeta());
    }

    @Override
    public net.kyori.adventure.text.Component effectiveName() {
      var meta = getItemMeta();
      return meta.hasDisplayName()
          ? meta.displayName()
          : net.kyori.adventure.text.Component.translatable(getType().translationKey());
    }

    @Override
    public NamedStack clone() {
      return new NamedStack(this);
    }
  }

  @BeforeEach
  void setupRecipes() {
    plugin = MockBukkit.createMockPlugin();
    registry =
        new ManagedRecipeRegistry(
            plugin,
            new RecipeRepository(new File(folder, "recipes.yml")),
            new ManagedRecipeRegistry.PotionMixes() {
              public void add(io.papermc.paper.potion.PotionMix mix) {}

              public void remove(NamespacedKey key) {}
            });
    limits = new CraftLimitTracker(null, new File(folder, "limits.yml"), () -> 1000);
    gui = new GuiManager(plugin, registry, new ChatPromptManager(plugin), limits);
  }

  ManagedRecipe recipe(String id, RecipeKind kind) {
    var r = new ManagedRecipe(id, kind);
    r.setResult(new ItemStack(Material.DIAMOND));
    r.setIngredient(0, IngredientSpec.fromSample(new ItemStack(Material.STICK)));
    return r;
  }

  void click(Player player, int slot, ClickType click) {
    gui.onClick(
        new InventoryClickEvent(
            player.getOpenInventory(),
            InventoryType.SlotType.CONTAINER,
            slot,
            click,
            InventoryAction.PICKUP_ALL));
  }

  @Test
  void rightClickTogglesAndOnlyAdminDisabledFilterCanFindRecipes() {
    var r = recipe("toggle", RecipeKind.SHAPELESS);
    r.setResult(new NamedStack(Material.DIAMOND));
    registry.upsert(r);
    var admin = MockBukkit.getMock().addPlayer();
    admin.addAttachment(plugin, "configurablecrafts.admin", true);
    var ordinary = MockBukkit.getMock().addPlayer();
    gui.openMain(admin, 0, "");
    gui.openMain(ordinary, 0, "toggle");
    assertEquals(
        Material.DIAMOND, ordinary.getOpenInventory().getTopInventory().getItem(18).getType());
    click(admin, 18, ClickType.RIGHT);
    assertFalse(registry.byId(r.id()).enabled());
    MockBukkit.getMock().getScheduler().performOneTick();
    assertNotEquals(
        Material.DIAMOND, ordinary.getOpenInventory().getTopInventory().getItem(18).getType());
    gui.openMain(ordinary, 0, "", RecipeListFilter.DISABLED);
    assertNotEquals(
        Material.DIAMOND, ordinary.getOpenInventory().getTopInventory().getItem(18).getType());
    gui.openRecipe(ordinary, r.id());
    assertEquals("Custom Recipes", ordinary.getOpenInventory().getTitle());
    gui.openMain(admin, 0, "toggle", RecipeListFilter.DISABLED);
    assertNotNull(admin.getOpenInventory().getTopInventory().getItem(18));
    click(admin, 18, ClickType.RIGHT);
    assertTrue(registry.byId(r.id()).enabled());
    MockBukkit.getMock().getScheduler().performOneTick();
    gui.openMain(admin, 0, "");
    click(admin, 18, ClickType.SHIFT_RIGHT);
    assertEquals("Confirm Remove/Revert", admin.getOpenInventory().getTitle());
    assertTrue(registry.byId(r.id()).enabled());
    gui.openMain(ordinary, 0, "");
    click(ordinary, 18, ClickType.RIGHT);
    assertTrue(registry.byId(r.id()).enabled());
  }

  @Test
  void staleEditorCannotUndoExternalToggleAndOwnedCallbacksRunOnce() {
    var r = recipe("owned", RecipeKind.SHAPED);
    AtomicInteger changes = new AtomicInteger();
    registry.registerOwned(
        r,
        new OwnedRecipe() {
          public boolean matches(ItemStack result) {
            return result.getType() == Material.DIAMOND;
          }

          public ItemStack preview() {
            return new NamedStack(Material.DIAMOND);
          }

          public String failure(Player player) {
            return null;
          }

          public ItemStack create(Player player) {
            return preview();
          }

          public void completed(Player player, ItemStack result) {}

          public void enabledChanged(boolean enabled) {
            changes.incrementAndGet();
          }
        });
    var admin = MockBukkit.getMock().addPlayer();
    admin.addAttachment(plugin, "configurablecrafts.admin", true);
    gui.openRecipe(admin, r.id());
    registry.setEnabled(r.id(), false);
    // Saving an already-open ingredient editor must preserve the new disabled state.
    click(admin, 46, ClickType.LEFT);
    assertFalse(registry.byId(r.id()).enabled());
    assertEquals(1, changes.get());
    registry.setEnabled(r.id(), true);
    registry.removeOrRevert(r.id());
    assertFalse(registry.byId(r.id()).enabled());
    assertEquals(3, changes.get());
  }

  @Test
  void staleListClickCannotToggleAnAdjacentRecipe() {
    var first = recipe("a", RecipeKind.SHAPELESS);
    var second = recipe("b", RecipeKind.SHAPELESS);
    first.setResult(new NamedStack(Material.DIAMOND));
    second.setResult(new NamedStack(Material.DIAMOND));
    registry.upsert(first);
    registry.upsert(second);
    var admin = MockBukkit.getMock().addPlayer();
    admin.addAttachment(plugin, "configurablecrafts.admin", true);
    gui.openMain(admin, 0, "");
    registry.setEnabled(first.id(), false);
    click(admin, 18, ClickType.RIGHT);
    assertFalse(registry.byId(first.id()).enabled());
    assertTrue(registry.byId(second.id()).enabled());
  }

  @Test
  void cachedSmithingAndStonecutterSelectionsAreRejected() {
    var player = MockBukkit.getMock().addPlayer();
    var smith = recipe("smith", RecipeKind.SMITHING);
    smith.setIngredient(
        0, IngredientSpec.fromSample(new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE)));
    smith.setIngredient(1, IngredientSpec.fromSample(new ItemStack(Material.DIAMOND_SWORD)));
    smith.setIngredient(2, IngredientSpec.fromSample(new ItemStack(Material.NETHERITE_INGOT)));
    smith.setResult(new ItemStack(Material.NETHERITE_SWORD));
    registry.upsert(smith);
    var cached = Bukkit.getRecipe(smith.managedKey(plugin));
    registry.setEnabled(smith.id(), false);
    var inventory = mock(SmithingInventory.class);
    doReturn(cached).when(inventory).getRecipe();
    var event = mock(SmithItemEvent.class);
    when(event.getInventory()).thenReturn(inventory);
    when(event.getWhoClicked()).thenReturn(player);
    when(event.getAction()).thenReturn(InventoryAction.PICKUP_ALL);
    var listener = new WorkstationUseListener(registry, limits);
    listener.onSmith(event);
    verify(event).setCancelled(true);
    var stone = recipe("stone", RecipeKind.STONECUTTING);
    registry.upsert(stone);
    var cutter = (StonecuttingRecipe) Bukkit.getRecipe(stone.managedKey(plugin));
    registry.setEnabled(stone.id(), false);
    var select = mock(io.papermc.paper.event.player.PlayerStonecutterRecipeSelectEvent.class);
    when(select.getStonecuttingRecipe()).thenReturn(cutter);
    when(select.getPlayer()).thenReturn(player);
    listener.onStonecutterSelect(select);
    verify(select).setCancelled(true);
  }

  @Test
  void persistedDisableRetainsIngredientsAndUnregistersDiscovery() {
    var r = recipe("restart", RecipeKind.SHAPED);
    r.setIngredient(0, IngredientSpec.fromSample(new ItemStack(Material.GOLD_INGOT)));
    var player = MockBukkit.getMock().addPlayer();
    registry.upsert(r);
    assertTrue(player.hasDiscoveredRecipe(r.managedKey(plugin)));
    registry.setEnabled(r.id(), false);
    assertFalse(player.hasDiscoveredRecipe(r.managedKey(plugin)));
    assertNotNull(registry.byManagedKey(r.managedKey(plugin)));
    registry.load();
    registry.applyAll();
    assertFalse(registry.byId(r.id()).enabled());
    assertNull(Bukkit.getRecipe(r.managedKey(plugin)));
    registry.setEnabled(r.id(), true);
    assertEquals(Material.GOLD_INGOT, registry.byId(r.id()).ingredient(0).sample().getType());
    assertTrue(player.hasDiscoveredRecipe(r.managedKey(plugin)));
  }

  @ParameterizedTest
  @EnumSource(
      value = RecipeKind.class,
      names = {"SMELTING", "SMOKING", "BLASTING", "CAMPFIRE"})
  void inProgressCookingIsCancelledAfterDisable(RecipeKind kind) {
    var r = recipe("cook", kind);
    registry.upsert(r);
    var cached = Bukkit.getRecipe(r.managedKey(plugin));
    assertNotNull(cached);
    registry.setEnabled(r.id(), false);
    var event = mock(BlockCookEvent.class);
    doReturn(cached).when(event).getRecipe();
    when(event.getBlock())
        .thenReturn(MockBukkit.getMock().addSimpleWorld("cook").getBlockAt(0, 64, 0));
    new CookingRecipeListener(registry, limits).onSmelt(event);
    verify(event).setCancelled(true);
  }

  @Test
  void cachedCraftAndCrafterAreRejectedEvenWhenCrafterWasAllowed() {
    var r = recipe("craft", RecipeKind.SHAPED);
    r.setAllowCrafters(true);
    registry.upsert(r);
    var cached = Bukkit.getRecipe(r.managedKey(plugin));
    registry.setEnabled(r.id(), false);
    var player = MockBukkit.getMock().addPlayer();
    var event = mock(CraftItemEvent.class);
    when(event.getRecipe()).thenReturn(cached);
    when(event.getWhoClicked()).thenReturn(player);
    when(event.getRawSlot()).thenReturn(0);
    when(event.getAction()).thenReturn(InventoryAction.PICKUP_ALL);
    var inventory = mock(CraftingInventory.class);
    when(event.getInventory()).thenReturn(inventory);
    when(inventory.getMatrix()).thenReturn(new ItemStack[9]);
    var listener = new CraftingListener(plugin, registry, limits);
    listener.onCraft(event);
    verify(event).setCancelled(true);
    var crafter = new org.bukkit.event.block.CrafterCraftEvent(
        player.getLocation().getBlock(), (CraftingRecipe) cached, r.result());
    assertDoesNotThrow(() -> listener.onCrafterCraft(crafter));
    assertTrue(crafter.isCancelled());
    assertTrue(crafter.getResult().getType().isAir());
  }

  @Test
  void cachedCustomBrewingResultCannotCompleteAfterDisable() {
    var r = recipe("brew", RecipeKind.BREWING);
    var input = new ItemStack(Material.POTION);
    r.setIngredient(0, IngredientSpec.fromSample(input));
    r.setIngredient(1, IngredientSpec.fromSample(new ItemStack(Material.APPLE)));
    registry.upsert(r);
    registry.setEnabled(r.id(), false);
    var inventory = mock(BrewerInventory.class);
    when(inventory.getIngredient()).thenReturn(new ItemStack(Material.APPLE));
    when(inventory.getItem(0)).thenReturn(input);
    var event = mock(BrewEvent.class);
    when(event.getContents()).thenReturn(inventory);
    var results = new ArrayList<ItemStack>(List.of(r.result()));
    when(event.getResults()).thenReturn(results);
    new BrewingRecipeService(registry).onBrew(event);
    assertEquals(input, results.getFirst());
  }

  @Test
  void failedPersistenceKeepsBothDisableAndEnableBlocked() throws Exception {
    var r = recipe("failure", RecipeKind.SHAPELESS);
    registry.upsert(r);
    var file = new File(folder, "recipes.yml").toPath();
    java.nio.file.Files.delete(file);
    java.nio.file.Files.createDirectory(file);
    assertThrows(IllegalStateException.class, () -> registry.setEnabled(r.id(), false));
    assertFalse(registry.byId(r.id()).enabled());
    assertThrows(IllegalStateException.class, () -> registry.setEnabled(r.id(), true));
    assertFalse(registry.byId(r.id()).enabled());
    assertNull(Bukkit.getRecipe(r.managedKey(plugin)));
    java.nio.file.Files.delete(file);
    registry.setEnabled(r.id(), true);
    assertTrue(registry.byId(r.id()).enabled());
  }

  @Test
  void disabledVanillaOverrideBlocksCachedSourceAndCanBeRestored() {
    var source =
        new ShapelessRecipe(
            NamespacedKey.minecraft("toggle_source"), new ItemStack(Material.EMERALD));
    source.addIngredient(Material.STICK);
    Bukkit.addRecipe(source);
    registry.cacheVanillaRecipes();
    var override = registry.fromVanilla(source);
    registry.upsert(override);
    registry.setEnabled(override.id(), false);
    assertNull(Bukkit.getRecipe(source.getKey()));
    assertFalse(registry.byManagedKey(source.getKey()).enabled());
    registry.setEnabled(override.id(), true);
    assertNotNull(Bukkit.getRecipe(override.managedKey(plugin)));
    registry.removeOrRevert(override.id());
    assertNotNull(Bukkit.getRecipe(source.getKey()));
  }
}
