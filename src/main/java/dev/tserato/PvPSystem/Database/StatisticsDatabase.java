package dev.tserato.PvPSystem.Database;

import org.bukkit.Bukkit;

import java.sql.*;
import java.util.UUID;

public class StatisticsDatabase {
    private static final String STATISTICS_DATABASE_URL = "jdbc:sqlite:" + Bukkit.getPluginManager().getPlugin("PvPSystem").getDataFolder() + "/statistics.db";

    public static void setupStatisticsDatabase() {
        try (Connection conn = DriverManager.getConnection(STATISTICS_DATABASE_URL)) {
            if (conn != null) {
                String createStatsTable = "CREATE TABLE IF NOT EXISTS player_stats ("
                        + "uuid TEXT PRIMARY KEY, "
                        + "wins INTEGER NOT NULL DEFAULT 0, "
                        + "losses INTEGER NOT NULL DEFAULT 0, "
                        + "total_games INTEGER NOT NULL DEFAULT 0)";
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(createStatsTable);
                    Bukkit.getLogger().info("Statistics database setup successfully.");
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error setting up the statistics database: " + e.getMessage());
        }
    }

    private static void insertDefaultPlayerStats(UUID playerUUID) {
        try (Connection conn = DriverManager.getConnection(STATISTICS_DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT OR IGNORE INTO player_stats (uuid, wins, losses, total_games) VALUES (?, 0, 0, 0)"
             )) {
            stmt.setString(1, playerUUID.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error inserting default stats for player: " + e.getMessage());
        }
    }

    public static void incrementWins(UUID playerUUID) {
        try (Connection conn = DriverManager.getConnection(STATISTICS_DATABASE_URL)) {
            int currentWins = getWins(playerUUID);
            int newWins = currentWins + 1;
            int newTotalGames = getTotalGames(playerUUID) + 1;

            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE player_stats SET wins = ?, total_games = ? WHERE uuid = ?"
            )) {
                stmt.setInt(1, newWins);
                stmt.setInt(2, newTotalGames);
                stmt.setString(3, playerUUID.toString());

                int rowsUpdated = stmt.executeUpdate();
                if (rowsUpdated == 0) {
                    insertDefaultPlayerStats(playerUUID);
                    incrementWins(playerUUID);
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error incrementing wins for player: " + e.getMessage());
        }
    }

    public static void incrementLosses(UUID playerUUID) {
        try (Connection conn = DriverManager.getConnection(STATISTICS_DATABASE_URL)) {
            int currentLosses = getLosses(playerUUID);
            int newLosses = currentLosses + 1;
            int newTotalGames = getTotalGames(playerUUID) + 1;

            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE player_stats SET losses = ?, total_games = ? WHERE uuid = ?"
            )) {
                stmt.setInt(1, newLosses);
                stmt.setInt(2, newTotalGames);
                stmt.setString(3, playerUUID.toString());

                int rowsUpdated = stmt.executeUpdate();
                if (rowsUpdated == 0) {
                    insertDefaultPlayerStats(playerUUID);
                    incrementLosses(playerUUID);
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error incrementing losses for player: " + e.getMessage());
        }
    }

    public static int getWins(UUID playerUUID) {
        try (Connection conn = DriverManager.getConnection(STATISTICS_DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement("SELECT wins FROM player_stats WHERE uuid = ?")) {
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("wins");
            } else {
                insertDefaultPlayerStats(playerUUID);
                return 0;
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error fetching wins for player: " + e.getMessage());
        }
        return 0;
    }

    public static int getLosses(UUID playerUUID) {
        try (Connection conn = DriverManager.getConnection(STATISTICS_DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement("SELECT losses FROM player_stats WHERE uuid = ?")) {
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("losses");
            } else {
                insertDefaultPlayerStats(playerUUID);
                return 0;
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error fetching losses for player: " + e.getMessage());
        }
        return 0;
    }

    public static int getTotalGames(UUID playerUUID) {
        try (Connection conn = DriverManager.getConnection(STATISTICS_DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement("SELECT total_games FROM player_stats WHERE uuid = ?")) {
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("total_games");
            } else {
                insertDefaultPlayerStats(playerUUID);
                return 0;
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error fetching total games for player: " + e.getMessage());
        }
        return 0;
    }
}
