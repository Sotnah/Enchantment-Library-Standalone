package dev.sotnah.enchantmentlibrary;

import net.minecraft.world.entity.player.Player;

public class PlayerXpHelper {

    public static int getPlayerTotalXp(Player player) {
        return (int) (getExperienceForLevel(player.experienceLevel)
                + (player.experienceProgress * player.getXpNeededForNextLevel()));
    }

    // Max level is capped at 50 in Config to prevent the "Synchronous Loop
    // Overhead" bug
    public static void addPlayerXp(Player player, int amount) {
        long newTotal = (long) getPlayerTotalXp(player) + amount;
        int totalXp = (int) Math.max(0L, Math.min(newTotal, Integer.MAX_VALUE));

        player.experienceProgress = 0.0F;
        player.experienceLevel = 0;
        player.totalExperience = 0;

        // Efficiently add XP mimicking Vanilla's logic but correctly setting total
        while (totalXp > 0) {
            int xpToNextLevel = player.getXpNeededForNextLevel();
            if (totalXp >= xpToNextLevel) {
                totalXp -= xpToNextLevel;
                player.experienceLevel++;
            } else {
                player.experienceProgress = (float) totalXp / (float) xpToNextLevel;
                totalXp = 0;
            }
        }

        player.totalExperience = getPlayerTotalXp(player);
    }

    private static int getExperienceForLevel(int level) {
        if (level >= 32) {
            return (int) (4.5D * Math.pow(level, 2) - 162.5D * level + 2220.0D);
        } else if (level >= 17) {
            return (int) (2.5D * Math.pow(level, 2) - 40.5D * level + 360.0D);
        } else {
            return (int) (Math.pow(level, 2) + 6.0D * level);
        }
    }

    /**
     * Calculates the raw XP cost for a given enchantment level using long arithmetic
     * to prevent overflow up to Long.MAX_VALUE.
     * This prevents the "Zero-Cost Upgrade" exploit where costs capped at Integer.MAX_VALUE
     * could result in a 0 difference for high-level upgrades.
     */
    public static long getCostRaw(int level) {
        int baseCost = Config.baseXpMultiplier.get();
        long levelSquared = (long) level * (long) level;
        return (long) baseCost * levelSquared;
    }

    // Max level is capped at 50 in Config to prevent the "Integer Overflow" bug
    public static int getCost(int level) {
        return (int) Math.min(getCostRaw(level), Integer.MAX_VALUE);
    }

    public static int getLevelFromXp(int xp) {
        if (xp <= 0)
            return 0;
        int level = 0;
        while (getExperienceForLevel(level + 1) <= xp) {
            level++;
        }
        return level;
    }
}
