package dev.sotnah.enchantmentlibrary;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import dev.sotnah.enchantmentlibrary.block.EnchLibraryBlockEntity;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Mod configuration manager.
 * Manages snapshots via mod events to prevent tight-coupling and performance
 * bottlenecks.
 */
@SuppressWarnings({ "deprecation" })
public class Config {

        public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
        public static final ModConfigSpec SPEC;

        // --- Active Configurations ---
        public static final ModConfigSpec.IntValue tier1MaxLevel;
        public static final ModConfigSpec.IntValue tier2MaxLevel;
        public static final ModConfigSpec.IntValue tier3MaxLevel;

        public static final ModConfigSpec.LongValue tier1MaxPoints;
        public static final ModConfigSpec.LongValue tier2MaxPoints;
        public static final ModConfigSpec.LongValue tier3MaxPoints;

        public static final ModConfigSpec.BooleanValue keepInventory;
        public static final ModConfigSpec.BooleanValue requireSilkTouchForKeepInventory;

        public static final ModConfigSpec.ConfigValue<List<? extends String>> enchantmentBlacklist;

        public static final ModConfigSpec.BooleanValue enableDisenchantButton;

        public static final ModConfigSpec.IntValue baseXpMultiplier;
        public static final ModConfigSpec.BooleanValue requireXpForExtraction;

        // Optimization: Epoch increases only when inventory re-validation is strictly
        // required (tier levels/points or blacklist changes).
        private static final AtomicInteger TIER_CONFIG_EPOCH = new AtomicInteger(0);
        private static volatile Set<Identifier> BLACKLIST_CACHE = Set.of();

        /**
         * Immutable snapshot of tier limits to ensure zero overhead on repeated calls.
         */
        public record TierLimitsRecord(int maxLevel, long maxPoints) {
        }

        private static volatile java.util.Map<EnchLibraryBlockEntity.Tier, TierLimitsRecord> TIER_LIMITS_SNAPSHOT = java.util.Map
                        .of(
                                        EnchLibraryBlockEntity.Tier.TIER1, new TierLimitsRecord(
                                                        LibraryConstants.DEFAULT_TIER1_MAX_LEVEL,
                                                        LibraryConstants.DEFAULT_TIER1_MAX_POINTS),
                                        EnchLibraryBlockEntity.Tier.TIER2, new TierLimitsRecord(
                                                        LibraryConstants.DEFAULT_TIER2_MAX_LEVEL,
                                                        LibraryConstants.DEFAULT_TIER2_MAX_POINTS),
                                        EnchLibraryBlockEntity.Tier.TIER3, new TierLimitsRecord(
                                                        LibraryConstants.DEFAULT_TIER3_MAX_LEVEL,
                                                        LibraryConstants.DEFAULT_TIER3_MAX_POINTS));

        // --- Initialization ---
        static {
                requireXpForExtraction = BUILDER
                                .comment("Whether XP is required to extract enchantments.")
                                .define("requireXpForExtraction", true);

                baseXpMultiplier = BUILDER
                                .comment("Base multiplier for XP extraction cost. Cost = baseXpMultiplier * (level * level).")
                                .defineInRange("baseXpMultiplier", 40, 0, 100000);

                enableDisenchantButton = BUILDER
                                .comment("Enables/disables disenchant button functionality.")
                                .define("enableDisenchantButton", true);

                keepInventory = BUILDER
                                .comment("If true, library keeps stored data when broken.")
                                .define("keepInventory", true);

                requireSilkTouchForKeepInventory = BUILDER
                                .comment(
                                                "If keepInventory is false and this is true, data is kept only when broken with a Silk Touch pickaxe.")
                                .define("requireSilkTouchForKeepInventory", false);

                enchantmentBlacklist = BUILDER
                                .comment(
                                                "Blacklist of enchantment ids (e.g. minecraft:sharpness). Blacklisted enchants are not storable/extractable. If an enchantment in the library is blacklisted, it will be deleted from the library.")
                                .defineList("enchantmentBlacklist", List.of(), Config::isValidIdentifierString);

                tier1MaxLevel = BUILDER.comment("Max enchantment level storable/extractable in tier 1 library.")
                                .defineInRange("tier1MaxLevel", LibraryConstants.DEFAULT_TIER1_MAX_LEVEL, 1, 50);
                tier2MaxLevel = BUILDER.comment("Max enchantment level storable/extractable in tier 2 library.")
                                .defineInRange("tier2MaxLevel", LibraryConstants.DEFAULT_TIER2_MAX_LEVEL, 1, 50);
                tier3MaxLevel = BUILDER.comment("Max enchantment level storable/extractable in tier 3 library.")
                                .defineInRange("tier3MaxLevel", LibraryConstants.DEFAULT_TIER3_MAX_LEVEL, 1, 50);

                long maxPointLimit = 10L * (1L << 49);

                tier1MaxPoints = BUILDER.comment("Max enchantment points storable per enchantment in tier 1 library.")
                                .defineInRange("tier1MaxPoints", LibraryConstants.DEFAULT_TIER1_MAX_POINTS, 1L,
                                                maxPointLimit);
                tier2MaxPoints = BUILDER.comment("Max enchantment points storable per enchantment in tier 2 library.")
                                .defineInRange("tier2MaxPoints", LibraryConstants.DEFAULT_TIER2_MAX_POINTS, 1L,
                                                maxPointLimit);
                tier3MaxPoints = BUILDER.comment("Max enchantment points storable per enchantment in tier 3 library.")
                                .defineInRange("tier3MaxPoints", LibraryConstants.DEFAULT_TIER3_MAX_POINTS, 1L,
                                                maxPointLimit);

                SPEC = BUILDER.build();
        }

        private static boolean isValidIdentifierString(Object value) {
                return value instanceof String str && Identifier.tryParse(str) != null;
        }

        // --- Event Listeners ---
        public static void onConfigLoad(final ModConfigEvent.Loading event) {
                if (event.getConfig().getSpec() == SPEC) {
                        rebuildCaches();
                }
        }

        public static void onConfigReload(final ModConfigEvent.Reloading event) {
                if (event.getConfig().getSpec() == SPEC) {
                        rebuildCaches();
                }
        }

        /**
         * Rebuilds configuration snapshots and caches.
         * Increments the epoch only if critical data (tier limits or backlist) has
         * changed.
         */
        public static void rebuildCaches() {
                boolean criticalConfigChanged = false;

                java.util.Map<EnchLibraryBlockEntity.Tier, TierLimitsRecord> newLimits = new java.util.EnumMap<>(
                                EnchLibraryBlockEntity.Tier.class);
                newLimits.put(EnchLibraryBlockEntity.Tier.TIER1,
                                new TierLimitsRecord(tier1MaxLevel.get(), tier1MaxPoints.get()));
                newLimits.put(EnchLibraryBlockEntity.Tier.TIER2,
                                new TierLimitsRecord(tier2MaxLevel.get(), tier2MaxPoints.get()));
                newLimits.put(EnchLibraryBlockEntity.Tier.TIER3,
                                new TierLimitsRecord(tier3MaxLevel.get(), tier3MaxPoints.get()));

                if (!newLimits.equals(TIER_LIMITS_SNAPSHOT)) {
                        TIER_LIMITS_SNAPSHOT = java.util.Collections.unmodifiableMap(newLimits);
                        criticalConfigChanged = true;
                }

                Set<Identifier> newBlacklist = new HashSet<>();
                for (String raw : enchantmentBlacklist.get()) {
                        Identifier id = Identifier.tryParse(raw);
                        if (id != null) {
                                newBlacklist.add(id);
                        }
                }

                if (!newBlacklist.equals(BLACKLIST_CACHE)) {
                        BLACKLIST_CACHE = Set.copyOf(newBlacklist);
                        criticalConfigChanged = true;
                }

                if (criticalConfigChanged) {
                        TIER_CONFIG_EPOCH.incrementAndGet();
                }
        }

        // --- Accessors ---

        public static int getTierConfigEpoch() {
                return TIER_CONFIG_EPOCH.get();
        }

        public static TierLimitsRecord getTierLimits(EnchLibraryBlockEntity.Tier tier) {
                return TIER_LIMITS_SNAPSHOT.get(tier);
        }

        public static boolean isBlacklisted(Holder<Enchantment> enchantment) {
                Identifier id = enchantment.unwrapKey().map(key -> key.identifier()).orElse(null);
                if (id == null) {
                        return false;
                }
                return BLACKLIST_CACHE.contains(id);
        }
}
