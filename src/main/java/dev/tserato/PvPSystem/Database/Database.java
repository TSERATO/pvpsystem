package dev.tserato.PvPSystem.Database;

import org.bukkit.Bukkit;

import java.sql.*;
import java.util.UUID;

public class Database {
    private static final String DATABASE_URL = "jdbc:sqlite:" + Bukkit.getPluginManager().getPlugin("PvPSystem").getDataFolder() + "/pvpsystem.db";

    public static void setupDatabase() {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL)) {
            if (conn != null) {
                // Create or update the players table to include win/loss stats
                String createPlayersTable = "CREATE TABLE IF NOT EXISTS players ("
                        + "uuid TEXT PRIMARY KEY, "
                        + "mmr INTEGER NOT NULL DEFAULT 300, "
                        + "wins INTEGER NOT NULL DEFAULT 0, "
                        + "losses INTEGER NOT NULL DEFAULT 0, "
                        + "total_games INTEGER NOT NULL DEFAULT 0, "
                        + "title TEXT NOT NULL DEFAULT 'None')";
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(createPlayersTable);
                    Bukkit.getLogger().info("PvPSystem database setup successfully.");
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error setting up the database: " + e.getMessage());
        }
    }

    public static int getMMR(UUID playerUUID) {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement("SELECT mmr FROM players WHERE uuid = ?")) {
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("mmr");
            } else {
                // Player doesn't have an MMR record, so we assign the default value
                setMMR(playerUUID, 300); // Default MMR
                return 300; // Return default MMR
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error fetching MMR: " + e.getMessage());
        }
        return 300; // Default MMR
    }

    public static void setMMR(UUID playerUUID, int mmr) {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement("REPLACE INTO players (uuid, mmr) VALUES (?, ?)")) {
            stmt.setString(1, playerUUID.toString());
            stmt.setInt(2, mmr);
            stmt.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error updating MMR: " + e.getMessage());
        }
    }

    public static void updatePlayerStats(UUID playerUUID, int wins, int losses, int totalGames) {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement("REPLACE INTO players (uuid, wins, losses, total_games) VALUES (?, ?, ?, ?)")) {
            stmt.setString(1, playerUUID.toString());
            stmt.setInt(2, wins);
            stmt.setInt(3, losses);
            stmt.setInt(4, totalGames);
            stmt.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error updating player stats: " + e.getMessage());
        }
    }

    public static String getPlayerStats(UUID playerUUID) {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement("SELECT wins, losses, total_games FROM players WHERE uuid = ?")) {
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int wins = rs.getInt("wins");
                int losses = rs.getInt("losses");
                int totalGames = rs.getInt("total_games");
                return "Wins: " + wins + "\nLosses: " + losses + "\nTotal Games: " + totalGames;
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error fetching player stats: " + e.getMessage());
        }
        return "No stats available.";
    }

    // Method to get the number of wins for a player
    public static int getWins(UUID playerUUID) {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement("SELECT wins FROM players WHERE uuid = ?")) {
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("wins");
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error fetching wins: " + e.getMessage());
        }
        return 0; // Return 0 if no data found
    }

    // Method to get the number of losses for a player
    public static int getLosses(UUID playerUUID) {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement("SELECT losses FROM players WHERE uuid = ?")) {
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("losses");
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error fetching losses: " + e.getMessage());
        }
        return 0; // Return 0 if no data found
    }
}