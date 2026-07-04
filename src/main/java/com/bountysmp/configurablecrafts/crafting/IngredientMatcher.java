package com.bountysmp.configurablecrafts.crafting;

import com.bountysmp.configurablecrafts.model.IngredientSpec;
import com.bountysmp.configurablecrafts.model.MatcherType;
import com.bountysmp.configurablecrafts.util.ItemText;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

public final class IngredientMatcher {
    private IngredientMatcher() {
    }

    public static boolean matches(IngredientSpec spec, ItemStack input) {
        if (spec == null || spec.isEmpty()) {
            return input == null || input.getType().isAir();
        }
        if (input == null || input.getType().isAir()) {
            return false;
        }
        ItemStack sample = spec.sample();
        for (MatcherType matcher : spec.matchers()) {
            if (!matchesOne(matcher, spec, sample, input)) {
                return false;
            }
        }
        return true;
    }

    public static RecipeChoice toRecipeChoice(IngredientSpec spec) {
        if (spec.hasMatcher(MatcherType.TAG) && !spec.tagKey().isBlank()) {
            List<Material> values = usableTagValues(spec.tagKey());
            if (!values.isEmpty()) {
                return new RecipeChoice.MaterialChoice(values);
            }
        }
        ItemStack sample = spec.sample();
        if (sample == null || sample.getType().isAir()) {
            throw new IllegalArgumentException("Ingredient sample cannot be empty.");
        }
        if (spec.hasMatcher(MatcherType.EXACT)) {
            ItemStack exact = sample.clone();
            exact.setAmount(1);
            return new RecipeChoice.ExactChoice(exact);
        }
        return new RecipeChoice.MaterialChoice(sample.getType());
    }

    public static Tag<Material> resolveTag(String tagKey) {
        NamespacedKey key = NamespacedKey.fromString(tagKey == null ? "" : tagKey.toLowerCase(Locale.ROOT));
        if (key == null) {
            return null;
        }
        return Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
    }

    public static List<Material> usableTagValues(String tagKey) {
        Tag<Material> tag = resolveTag(tagKey);
        if (tag == null) {
            return List.of();
        }
        return tag.getValues().stream()
            .filter(material -> material != null && !material.isAir() && material.isItem())
            .sorted()
            .toList();
    }

    public static void captureEnchantments(IngredientSpec spec) {
        spec.enchantments().clear();
        ItemStack sample = spec.sample();
        if (sample == null) {
            return;
        }
        for (Map.Entry<Enchantment, Integer> entry : sample.getEnchantments().entrySet()) {
            spec.enchantments().put(entry.getKey().getKey().toString(), entry.getValue());
        }
    }

    private static boolean matchesOne(MatcherType matcher, IngredientSpec spec, ItemStack sample, ItemStack input) {
        return switch (matcher) {
            case MATERIAL -> input.getType() == sample.getType();
            case EXACT -> isSimilarIgnoringAmount(sample, input);
            case ITEM_NAME -> sample != null && Objects.equals(ItemText.customName(sample), ItemText.customName(input));
            case LORE_CONTAINS -> loreContains(input, spec.loreContains());
            case TAG -> inTag(input.getType(), spec.tagKey());
            case ENCHANTMENTS -> hasEnchantments(input, spec.enchantments());
        };
    }

    private static boolean isSimilarIgnoringAmount(ItemStack expected, ItemStack actual) {
        ItemStack left = expected.clone();
        ItemStack right = actual.clone();
        left.setAmount(1);
        right.setAmount(1);
        return left.isSimilar(right);
    }

    private static boolean loreContains(ItemStack input, String needle) {
        if (needle == null || needle.isBlank()) {
            return true;
        }
        String lowerNeedle = needle.toLowerCase(Locale.ROOT);
        for (String line : ItemText.lore(input)) {
            if (line.toLowerCase(Locale.ROOT).contains(lowerNeedle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean inTag(Material material, String tagKey) {
        Tag<Material> tag = resolveTag(tagKey);
        return tag != null && tag.isTagged(material);
    }

    @SuppressWarnings("deprecation")
    private static boolean hasEnchantments(ItemStack input, Map<String, Integer> enchantments) {
        if (enchantments.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
            NamespacedKey key = NamespacedKey.fromString(entry.getKey());
            if (key == null) {
                return false;
            }
            Enchantment enchantment = Registry.ENCHANTMENT.get(key);
            if (enchantment == null || input.getEnchantmentLevel(enchantment) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    public static String signatureToken(IngredientSpec spec) {
        if (spec == null || spec.isEmpty()) {
            return "_";
        }
        if (spec.hasMatcher(MatcherType.EXACT)) {
            return "exact:" + exactFingerprint(spec.sample());
        }
        if (spec.hasMatcher(MatcherType.TAG) && !spec.tagKey().isBlank()) {
            return "#" + spec.tagKey();
        }
        ItemStack sample = spec.sample();
        return "material:" + sample.getType().name();
    }

    static String tokenForChoice(RecipeChoice choice) {
        if (choice == null) {
            return "_";
        }
        if (choice instanceof RecipeChoice.MaterialChoice materialChoice) {
            Collection<Material> materials = materialChoice.getChoices();
            if (materials.size() == 1) {
                return "material:" + materials.iterator().next().name();
            }
            return "materials:" + materials.stream().map(Enum::name).sorted().toList();
        }
        if (choice instanceof RecipeChoice.ExactChoice exactChoice) {
            return "exact:" + exactChoice.getChoices().stream()
                .map(IngredientMatcher::exactFingerprint)
                .sorted()
                .toList();
        }
        return choice.toString();
    }

    private static String exactFingerprint(ItemStack itemStack) {
        ItemStack normalized = itemStack.clone();
        normalized.setAmount(1);
        return Base64.getEncoder().encodeToString(normalized.serializeAsBytes());
    }
}
