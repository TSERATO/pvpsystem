package dev.tserato.pvPSystem.MMR;

import java.util.HashMap;
import java.util.Map;

public class MMR {

    // MMR thresholds for ranking
    private static final Map<String, Integer> rankMMRThresholds = new HashMap<>();
    static {
        rankMMRThresholds.put("Wood", 0);
        rankMMRThresholds.put("Stone I", 100);
        rankMMRThresholds.put("Stone II", 200);
        rankMMRThresholds.put("Stone III", 300);
        rankMMRThresholds.put("Copper I", 400);
        rankMMRThresholds.put("Copper II", 500);
        rankMMRThresholds.put("Copper III", 600);
        rankMMRThresholds.put("Iron I", 700);
        rankMMRThresholds.put("Iron II", 800);
        rankMMRThresholds.put("Iron III", 900);
        rankMMRThresholds.put("Gold I", 1000);
        rankMMRThresholds.put("Gold II", 1100);
        rankMMRThresholds.put("Gold III", 1200);
        rankMMRThresholds.put("Diamond I", 1300);
        rankMMRThresholds.put("Diamond II", 1400);
        rankMMRThresholds.put("Diamond III", 1500);
        rankMMRThresholds.put("Netherite I", 1600);
        rankMMRThresholds.put("Netherite II", 1700);
        rankMMRThresholds.put("Netherite III", 1800);
        rankMMRThresholds.put("Bedrock", 2000);
    }

    public static String getRankForMMR(int mmr) {
        for (Map.Entry<String, Integer> entry : rankMMRThresholds.entrySet()) {
            if (mmr < entry.getValue()) {
                return entry.getKey();
            }
        }
        return "Wood"; // Default highest rank
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
            return "You have ranked up to " + newRank + "!";
        }
        return "You have stayed at " + oldRank + ".";
    }
}
