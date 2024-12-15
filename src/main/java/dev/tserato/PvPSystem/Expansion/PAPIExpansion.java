package dev.tserato.PvPSystem.Expansion;

import dev.tserato.PvPSystem.Database.Database;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.tserato.PvPSystem.MMR.MMR.getRankForMMR;

public class PAPIExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;

    public PAPIExpansion(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    @NotNull
    public String getAuthor() {
        return "TSERATO"; //
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return "pvpsystem"; //
    }

    @Override
    @NotNull
    public String getVersion() {
        return "1.0.0"; //
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        // Update top players dynamically whenever a placeholder is requested
        Map<String, String> topPlaceholders = getTopMMRPlaceholders();

        if (params.startsWith("top_")) {
            return topPlaceholders.getOrDefault(params, "N/A");
        }

        // New placeholders for position and percentage
        if (params.equalsIgnoreCase("position")) {
            return getPlayerPosition(player.getUniqueId());
        }

        if (params.equalsIgnoreCase("position_in_percent")) {
            return getPlayerPositionInPercent(player.getUniqueId());
        }

        return null;
    }

    /**
     * Returns the player's position among all players by MMR.
     *
     * @param playerUUID The UUID of the player.
     * @return The player's position as a string.
     */
    private String getPlayerPosition(UUID playerUUID) {
        Map<UUID, Integer> playerMMRMap = new HashMap<>();
        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            UUID uuid = offlinePlayer.getUniqueId();
            int mmr = Database.getMMR(uuid); // Replace with your actual method to get MMR
            playerMMRMap.put(uuid, mmr);
        }

        // Sort players by MMR in descending order
        List<Map.Entry<UUID, Integer>> sortedPlayers = playerMMRMap.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue())) // Descending order
                .toList();

        // Find the position of the player
        for (int i = 0; i < sortedPlayers.size(); i++) {
            if (sortedPlayers.get(i).getKey().equals(playerUUID)) {
                return String.valueOf(i + 1); // Position is index + 1
            }
        }

        return "N/A"; // If player is not found
    }

    /**
     * Returns the percentage of players on the same rank as the given player by MMR.
     *
     * @param playerUUID The UUID of the player.
     * @return The percentage of players on the same rank as a string.
     */
    private String getPlayerPositionInPercent(UUID playerUUID) {
        Map<UUID, Integer> playerMMRMap = new HashMap<>();
        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            UUID uuid = offlinePlayer.getUniqueId();
            int mmr = Database.getMMR(uuid); // Replace with your actual method to get MMR
            playerMMRMap.put(uuid, mmr);
        }

        // Get the MMR of the requesting player
        Integer playerMMR = playerMMRMap.get(playerUUID);
        if (playerMMR == null) {
            return "N/A"; // Player not found in the database
        }

        // Count the total number of players and players with the same MMR
        int totalPlayers = playerMMRMap.size();
        long sameRankCount = playerMMRMap.values().stream()
                .filter(mmr -> mmr.equals(playerMMR))
                .count();

        // Calculate the percentage of players with the same rank
        double percentage = ((double) sameRankCount / totalPlayers) * 100;

        // Format the percentage to two decimal places
        return String.format("%.2f%%", percentage);
    }

    public Map<String, String> getTopMMRPlaceholders() {
        Map<String, String> topPlaceholders = new HashMap<>();

        // Get all players' UUIDs and MMRs
        Map<UUID, Integer> playerMMRMap = new HashMap<>();
        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            UUID playerUUID = offlinePlayer.getUniqueId();
            int mmr = Database.getMMR(playerUUID); // Replace with your actual method to get MMR
            playerMMRMap.put(playerUUID, mmr);
        }

        // Sort players by MMR in descending order
        List<Map.Entry<UUID, Integer>> sortedPlayers = playerMMRMap.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue())) // Descending order
                .toList();

        // Assign top players to placeholders
        for (int i = 0; i < 10 && i < sortedPlayers.size(); i++) {
            UUID playerUUID = sortedPlayers.get(i).getKey();
            int mmr = sortedPlayers.get(i).getValue();
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUUID);

            // Example format: "PlayerName - 1234 MMR"
            String placeholderValue = player.getName() + " - " + getRankForMMR(mmr) + " (" + mmr + ")";

            // Save the placeholder
            topPlaceholders.put("top_" + (i + 1), placeholderValue);
        }

        return topPlaceholders;
    }


    public String onPlaceholderRequest(Player player, @NotNull String params) {
        return params;
    }
}
