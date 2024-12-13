package dev.tserato.PvPSystem.MMR;

import java.util.HashMap;
import java.util.Map;

public class MMR {

    // MMR thresholds for ranking
    private static final Map<String, Integer> rankMMRThresholds = new HashMap<>();
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
    }

    public static String getRankForMMR(int MMR) {
        // Iterate through the map and find the rank that corresponds to the MMR
        for (Map.Entry<String, Integer> entry : rankMMRThresholds.entrySet()) {
            if (MMR < entry.getValue()) {
                return entry.getKey();
            }
        }
        // If the MMR is higher than the highest threshold, return "Bedrock"
        return "Bedrock";
    }

    public static int getMMRForRank(String rank) {
        return rankMMRThresholds.getOrDefault(rank, 0);
    }

    public static int calculateNewMMR(int currentMMR, boolean isWinner, int opponentMMR) {
        int ratingChange = 10; // Base rating change (can be adjusted)

        // If the player wins, they gain rating
        if (isWinner) {
            return currentMMR + ratingChange;
        } else {
            // If the player loses, they lose rating
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
