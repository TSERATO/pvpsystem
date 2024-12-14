package dev.tserato.PvPSystem.MMR;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.LinkedHashMap;
import java.util.Map;

public class MMR {

    // MMR thresholds for ranking
    private static final Map<String, Integer> rankMMRThresholds = new LinkedHashMap<>(); // Preserve order
    private static final Map<String, NamedTextColor> rankColors = new LinkedHashMap<>(); // Color for each rank group

    static {
        rankMMRThresholds.put("Wood", 0);
        rankMMRThresholds.put("Stone I", 30);
        rankMMRThresholds.put("Stone II", 60);
        rankMMRThresholds.put("Stone III", 90);
        rankMMRThresholds.put("Copper I", 120);
        rankMMRThresholds.put("Copper II", 150);
        rankMMRThresholds.put("Copper III", 180);
        rankMMRThresholds.put("Iron I", 210);
        rankMMRThresholds.put("Iron II", 240);
        rankMMRThresholds.put("Iron III", 270);
        rankMMRThresholds.put("Gold I", 300);
        rankMMRThresholds.put("Gold II", 330);
        rankMMRThresholds.put("Gold III", 360);
        rankMMRThresholds.put("Diamond I", 390);
        rankMMRThresholds.put("Diamond II", 420);
        rankMMRThresholds.put("Diamond III", 450);
        rankMMRThresholds.put("Netherite I", 480);
        rankMMRThresholds.put("Netherite II", 510);
        rankMMRThresholds.put("Netherite III", 540);
        rankMMRThresholds.put("Bedrock", 570);

        // Define colors for rank groups
        rankColors.put("Wood", NamedTextColor.GRAY);
        rankColors.put("Stone", NamedTextColor.DARK_GRAY);
        rankColors.put("Copper", NamedTextColor.GOLD);
        rankColors.put("Iron", NamedTextColor.WHITE);
        rankColors.put("Gold", NamedTextColor.YELLOW);
        rankColors.put("Diamond", NamedTextColor.AQUA);
        rankColors.put("Netherite", NamedTextColor.LIGHT_PURPLE);
        rankColors.put("Bedrock", NamedTextColor.DARK_PURPLE);
    }

    public static String getRankForMMR(int MMR) {
        String currentRank = "Gold I"; // Default rank if no thresholds are met
        for (Map.Entry<String, Integer> entry : rankMMRThresholds.entrySet()) {
            if (MMR >= entry.getValue()) {
                currentRank = entry.getKey(); // Update rank as long as MMR meets the threshold
            } else {
                break; // Stop checking once MMR is below a threshold
            }
        }
        return currentRank;
    }


    public static NamedTextColor getColorForRank(String rank) {
        // Match the rank group (e.g., "Stone I" matches "Stone")
        String rankGroup = rank.split(" ")[0];
        return rankColors.getOrDefault(rankGroup, NamedTextColor.WHITE);
    }

    public static int getMMRForRank(String rank) {
        return rankMMRThresholds.getOrDefault(rank, 0);
    }

    public static int calculateNewMMR(int currentMMR, boolean isWinner, int opponentMMR) {
        int ratingChange = 10;

        if (isWinner) {
            return currentMMR + ratingChange;
        } else {
            return currentMMR - ratingChange;
        }
    }

    public static String rankChangeTitle(String oldRank, String newRank) {
        if (!oldRank.equals(newRank)) {
            return newRank;
        }
        return oldRank;
    }
}
