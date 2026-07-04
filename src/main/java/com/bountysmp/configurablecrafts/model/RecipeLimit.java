package com.bountysmp.configurablecrafts.model;

public final class RecipeLimit {
    private int crafts;
    private long windowSeconds;

    public RecipeLimit() {
    }

    public RecipeLimit(int crafts, long windowSeconds) {
        set(crafts, windowSeconds);
    }

    public int crafts() {
        return crafts;
    }

    public long windowSeconds() {
        return windowSeconds;
    }

    public boolean enabled() {
        return crafts > 0 && windowSeconds > 0;
    }

    public void set(int crafts, long windowSeconds) {
        this.crafts = Math.max(0, crafts);
        this.windowSeconds = Math.max(0L, windowSeconds);
    }

    public void clear() {
        crafts = 0;
        windowSeconds = 0;
    }

    public RecipeLimit copy() {
        return new RecipeLimit(crafts, windowSeconds);
    }
}
