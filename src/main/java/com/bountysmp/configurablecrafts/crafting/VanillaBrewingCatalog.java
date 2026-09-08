package com.bountysmp.configurablecrafts.crafting;

import com.bountysmp.configurablecrafts.model.IngredientSpec;
import com.bountysmp.configurablecrafts.model.ManagedRecipe;
import com.bountysmp.configurablecrafts.model.RecipeKind;
import com.bountysmp.configurablecrafts.util.ItemText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import com.bountysmp.configurablecrafts.model.MatcherType;

/** Versioned description of the vanilla brewing graph targeted by the plugin. */
public final class VanillaBrewingCatalog {
    public static final int VERSION = 1;
    public static final String SOURCE_PREFIX = "minecraft:brewing/";

    private static final List<Entry> ENTRIES = createEntries();
    private static final Map<String, Entry> BY_SOURCE = indexEntries();

    private VanillaBrewingCatalog() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Entry bySourceKey(String sourceKey) {
        return sourceKey == null ? null : BY_SOURCE.get(sourceKey);
    }

    public static boolean isOverride(ManagedRecipe recipe) {
        return recipe != null && bySourceKey(recipe.sourceKey()) != null;
    }

    public static boolean matchesPotionInput(IngredientSpec spec, ItemStack input) {
        if (spec == null || input == null || !isPotionContainer(input.getType())) {
            return false;
        }
        ItemStack sample = spec.sample();
        if (sample == null || !isPotionContainer(sample.getType())) {
            return false;
        }
        if (spec.hasMatcher(MatcherType.EXACT)) {
            PotionType expected = ((PotionMeta) sample.getItemMeta()).getBasePotionType();
            PotionType actual = ((PotionMeta) input.getItemMeta()).getBasePotionType();
            return expected == actual;
        }
        return true;
    }

    private static boolean isPotionContainer(Material material) {
        return material == Material.POTION || material == Material.SPLASH_POTION || material == Material.LINGERING_POTION;
    }

    private static List<Entry> createEntries() {
        List<Entry> entries = new ArrayList<>();
        add(entries, "awkward", PotionType.WATER, Material.NETHER_WART, PotionType.AWKWARD);
        add(entries, "weakness", PotionType.WATER, Material.FERMENTED_SPIDER_EYE, PotionType.WEAKNESS);
        add(entries, "thick", PotionType.WATER, Material.GLOWSTONE_DUST, PotionType.THICK);
        add(entries, "mundane", PotionType.WATER, Material.REDSTONE, PotionType.MUNDANE);

        add(entries, "night_vision", PotionType.AWKWARD, Material.GOLDEN_CARROT, PotionType.NIGHT_VISION);
        add(entries, "invisibility", PotionType.NIGHT_VISION, Material.FERMENTED_SPIDER_EYE, PotionType.INVISIBILITY);
        add(entries, "long_invisibility_from_long_night_vision", PotionType.LONG_NIGHT_VISION, Material.FERMENTED_SPIDER_EYE, PotionType.LONG_INVISIBILITY);
        add(entries, "leaping", PotionType.AWKWARD, Material.RABBIT_FOOT, PotionType.LEAPING);
        add(entries, "fire_resistance", PotionType.AWKWARD, Material.MAGMA_CREAM, PotionType.FIRE_RESISTANCE);
        add(entries, "swiftness", PotionType.AWKWARD, Material.SUGAR, PotionType.SWIFTNESS);
        add(entries, "slowness_from_swiftness", PotionType.SWIFTNESS, Material.FERMENTED_SPIDER_EYE, PotionType.SLOWNESS);
        add(entries, "slowness_from_leaping", PotionType.LEAPING, Material.FERMENTED_SPIDER_EYE, PotionType.SLOWNESS);
        add(entries, "long_slowness_from_long_leaping", PotionType.LONG_LEAPING, Material.FERMENTED_SPIDER_EYE, PotionType.LONG_SLOWNESS);
        add(entries, "long_slowness_from_long_swiftness", PotionType.LONG_SWIFTNESS, Material.FERMENTED_SPIDER_EYE, PotionType.LONG_SLOWNESS);
        add(entries, "turtle_master", PotionType.AWKWARD, Material.TURTLE_HELMET, PotionType.TURTLE_MASTER);
        add(entries, "water_breathing", PotionType.AWKWARD, Material.PUFFERFISH, PotionType.WATER_BREATHING);
        add(entries, "healing", PotionType.AWKWARD, Material.GLISTERING_MELON_SLICE, PotionType.HEALING);
        add(entries, "harming_from_healing", PotionType.HEALING, Material.FERMENTED_SPIDER_EYE, PotionType.HARMING);
        add(entries, "strong_harming_from_strong_healing", PotionType.STRONG_HEALING, Material.FERMENTED_SPIDER_EYE, PotionType.STRONG_HARMING);
        add(entries, "harming_from_poison", PotionType.POISON, Material.FERMENTED_SPIDER_EYE, PotionType.HARMING);
        add(entries, "harming_from_long_poison", PotionType.LONG_POISON, Material.FERMENTED_SPIDER_EYE, PotionType.HARMING);
        add(entries, "strong_harming_from_strong_poison", PotionType.STRONG_POISON, Material.FERMENTED_SPIDER_EYE, PotionType.STRONG_HARMING);
        add(entries, "poison", PotionType.AWKWARD, Material.SPIDER_EYE, PotionType.POISON);
        add(entries, "regeneration", PotionType.AWKWARD, Material.GHAST_TEAR, PotionType.REGENERATION);
        add(entries, "strength", PotionType.AWKWARD, Material.BLAZE_POWDER, PotionType.STRENGTH);
        add(entries, "slow_falling", PotionType.AWKWARD, Material.PHANTOM_MEMBRANE, PotionType.SLOW_FALLING);
        add(entries, "infested", PotionType.AWKWARD, Material.STONE, PotionType.INFESTED);
        add(entries, "oozing", PotionType.AWKWARD, Material.SLIME_BLOCK, PotionType.OOZING);
        add(entries, "weaving", PotionType.AWKWARD, Material.COBWEB, PotionType.WEAVING);
        add(entries, "wind_charged", PotionType.AWKWARD, Material.BREEZE_ROD, PotionType.WIND_CHARGED);

        extend(entries, PotionType.NIGHT_VISION, PotionType.LONG_NIGHT_VISION);
        extend(entries, PotionType.INVISIBILITY, PotionType.LONG_INVISIBILITY);
        extend(entries, PotionType.LEAPING, PotionType.LONG_LEAPING);
        extend(entries, PotionType.FIRE_RESISTANCE, PotionType.LONG_FIRE_RESISTANCE);
        extend(entries, PotionType.SWIFTNESS, PotionType.LONG_SWIFTNESS);
        extend(entries, PotionType.SLOWNESS, PotionType.LONG_SLOWNESS);
        extend(entries, PotionType.TURTLE_MASTER, PotionType.LONG_TURTLE_MASTER);
        extend(entries, PotionType.WATER_BREATHING, PotionType.LONG_WATER_BREATHING);
        extend(entries, PotionType.POISON, PotionType.LONG_POISON);
        extend(entries, PotionType.REGENERATION, PotionType.LONG_REGENERATION);
        extend(entries, PotionType.STRENGTH, PotionType.LONG_STRENGTH);
        extend(entries, PotionType.WEAKNESS, PotionType.LONG_WEAKNESS);
        extend(entries, PotionType.SLOW_FALLING, PotionType.LONG_SLOW_FALLING);

        strengthen(entries, PotionType.LEAPING, PotionType.STRONG_LEAPING);
        strengthen(entries, PotionType.SWIFTNESS, PotionType.STRONG_SWIFTNESS);
        strengthen(entries, PotionType.SLOWNESS, PotionType.STRONG_SLOWNESS);
        strengthen(entries, PotionType.HEALING, PotionType.STRONG_HEALING);
        strengthen(entries, PotionType.HARMING, PotionType.STRONG_HARMING);
        strengthen(entries, PotionType.POISON, PotionType.STRONG_POISON);
        strengthen(entries, PotionType.REGENERATION, PotionType.STRONG_REGENERATION);
        strengthen(entries, PotionType.STRENGTH, PotionType.STRONG_STRENGTH);
        strengthen(entries, PotionType.TURTLE_MASTER, PotionType.STRONG_TURTLE_MASTER);

        entries.add(Entry.container("splash", Material.POTION, Material.GUNPOWDER, Material.SPLASH_POTION));
        entries.add(Entry.container("lingering", Material.SPLASH_POTION, Material.DRAGON_BREATH, Material.LINGERING_POTION));
        return List.copyOf(entries);
    }

    private static void extend(List<Entry> entries, PotionType input, PotionType result) {
        add(entries, "long_" + key(input), input, Material.REDSTONE, result);
    }

    private static void strengthen(List<Entry> entries, PotionType input, PotionType result) {
        add(entries, "strong_" + key(input), input, Material.GLOWSTONE_DUST, result);
    }

    private static void add(List<Entry> entries, String id, PotionType input, Material ingredient, PotionType result) {
        entries.add(Entry.potion(id, input, ingredient, result));
    }

    private static String key(PotionType type) {
        return type.getKey().getKey();
    }

    private static Map<String, Entry> indexEntries() {
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            if (entries.put(entry.sourceKey(), entry) != null) {
                throw new IllegalStateException("Duplicate vanilla brewing id: " + entry.sourceKey());
            }
        }
        return Map.copyOf(entries);
    }

    public record Entry(String id, PotionType inputPotion, Material inputMaterial, Material ingredient,
                        PotionType resultPotion, Material resultMaterial, boolean containerConversion) {
        static Entry potion(String id, PotionType input, Material ingredient, PotionType result) {
            return new Entry(id, input, Material.POTION, ingredient, result, Material.POTION, false);
        }

        static Entry container(String id, Material input, Material ingredient, Material result) {
            return new Entry(id, null, input, ingredient, null, result, true);
        }

        public String sourceKey() {
            return SOURCE_PREFIX + id;
        }

        public String managedId() {
            return "override_minecraft_brewing_" + id;
        }

        public ItemStack input() {
            return inputPotion == null ? new ItemStack(inputMaterial) : VanillaBrewingCatalog.potion(inputMaterial, inputPotion);
        }

        public ItemStack result() {
            return resultPotion == null ? new ItemStack(resultMaterial) : VanillaBrewingCatalog.potion(resultMaterial, resultPotion);
        }

        public ManagedRecipe toManagedRecipe() {
            ManagedRecipe recipe = new ManagedRecipe(managedId(), RecipeKind.BREWING);
            recipe.setSourceKey(sourceKey());
            recipe.setIngredient(0, containerConversion
                ? IngredientSpec.fromSample(input())
                : IngredientSpec.fromExactSample(input()));
            recipe.setIngredient(1, IngredientSpec.fromSample(new ItemStack(ingredient)));
            recipe.setResult(result());
            return recipe;
        }

        public String displayName() {
            return resultPotion == null ? ItemText.humanize(id) : ItemText.humanize(key(resultPotion));
        }
    }

    private static ItemStack potion(Material material, PotionType type) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(type));
        return stack;
    }
}
