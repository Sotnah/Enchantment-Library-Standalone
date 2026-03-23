package dev.sotnah.enchantmentlibrary;

public class LibraryConstants {

    // --- Tier Max Levels ---
    public static final int DEFAULT_TIER1_MAX_LEVEL = 5;
    public static final int DEFAULT_TIER2_MAX_LEVEL = 10;
    public static final int DEFAULT_TIER3_MAX_LEVEL = 30;

    // --- Tier Max Points ---
    // Represents mathematically max points per level (1L << (level - 1)) * 10L
    public static final long DEFAULT_TIER1_MAX_POINTS = (1L << (DEFAULT_TIER1_MAX_LEVEL - 1)) * 10L; // 160
    public static final long DEFAULT_TIER2_MAX_POINTS = (1L << (DEFAULT_TIER2_MAX_LEVEL - 1)) * 10L; // 5120
    public static final long DEFAULT_TIER3_MAX_POINTS = (1L << (DEFAULT_TIER3_MAX_LEVEL - 1)) * 10L; // 5368709120
}
