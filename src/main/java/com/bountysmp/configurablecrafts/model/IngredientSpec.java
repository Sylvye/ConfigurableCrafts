package com.bountysmp.configurablecrafts.model;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.inventory.ItemStack;

public final class IngredientSpec {
    private ItemStack sample;
    private final EnumSet<MatcherType> matchers = EnumSet.noneOf(MatcherType.class);
    private String logic = "all";
    private String loreContains = "";
    private String tagKey = "";
    private final Map<String, Integer> enchantments = new LinkedHashMap<>();

    public static IngredientSpec fromSample(ItemStack sample) {
        IngredientSpec spec = new IngredientSpec();
        spec.setSample(sample);
        spec.matchers.add(MatcherType.MATERIAL);
        return spec;
    }

    public static IngredientSpec fromTag(String tagKey) {
        IngredientSpec spec = new IngredientSpec();
        spec.setTagKey(tagKey);
        spec.setMatcher(MatcherType.TAG, !spec.tagKey().isBlank());
        return spec;
    }

    public boolean isEmpty() {
        return !hasUsableTag() && (sample == null || sample.getType().isAir());
    }

    public boolean isTagOnly() {
        return hasUsableTag() && (sample == null || sample.getType().isAir());
    }

    public boolean hasUsableTag() {
        return hasMatcher(MatcherType.TAG) && !tagKey.isBlank();
    }

    public ItemStack sample() {
        return sample == null ? null : sample.clone();
    }

    public void setSample(ItemStack sample) {
        this.sample = sample == null ? null : sample.clone();
    }

    public EnumSet<MatcherType> matchers() {
        return matchers;
    }

    public boolean hasMatcher(MatcherType matcherType) {
        return matchers.contains(matcherType);
    }

    public void setMatcher(MatcherType matcherType, boolean enabled) {
        if (enabled) {
            if (matcherType == MatcherType.EXACT) {
                matchers.clear();
            } else {
                matchers.remove(MatcherType.EXACT);
            }
            matchers.add(matcherType);
        } else {
            matchers.remove(matcherType);
        }
    }

    public void normalizeMatchers() {
        if (matchers.contains(MatcherType.EXACT) && matchers.size() > 1) {
            matchers.clear();
            matchers.add(MatcherType.EXACT);
        }
        if (isTagOnly() && matchers.size() > 1) {
            matchers.clear();
            matchers.add(MatcherType.TAG);
        }
    }

    public String logic() {
        return logic;
    }

    public void setLogic(String logic) {
        this.logic = logic == null || logic.isBlank() ? "all" : logic.toLowerCase(Locale.ROOT);
    }

    public String loreContains() {
        return loreContains;
    }

    public void setLoreContains(String loreContains) {
        this.loreContains = loreContains == null ? "" : loreContains;
    }

    public String tagKey() {
        return tagKey;
    }

    public void setTagKey(String tagKey) {
        this.tagKey = tagKey == null ? "" : tagKey.toLowerCase(Locale.ROOT);
    }

    public Map<String, Integer> enchantments() {
        return enchantments;
    }

    public IngredientSpec copy() {
        IngredientSpec copy = new IngredientSpec();
        copy.setSample(sample);
        copy.matchers.addAll(matchers);
        copy.logic = logic;
        copy.loreContains = loreContains;
        copy.tagKey = tagKey;
        copy.enchantments.putAll(enchantments);
        return copy;
    }
}
