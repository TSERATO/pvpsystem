package dev.tserato.PvPSystem.Database;

import org.bukkit.Bukkit;

import java.sql.*;
import java.util.UUID;

public class Database {
    private static final String DATABASE_URL = "jdbc:sqlite:" + Bukkit.getPluginManager().getPlugin("PvPSystem").getDataFolder() + "/pvpsystem.db";

    public static void setupDatabase() {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL)) {
            if (conn != null) {
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

    private static void insertDefaultPlayer(UUID playerUUID) {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT OR IGNORE INTO players (uuid, mmr, wins, losses, total_games, title) VALUES (?, 300, 0, 0, 0, 'None')"
             )) {
            stmt.setString(1, playerUUID.toString());
            int rowsInserted = stmt.executeUpdate();
            Bukkit.getLogger().info("Default player inserted: " + rowsInserted + " row(s) for UUID: " + playerUUID);
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error inserting default player: " + e.getMessage());
        }
    }

    public static void incrementWins(UUID playerUUID) {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL)) {
            Bukkit.getLogger().info("Attempting to increment wins for player: " + playerUUID);

            // Fetch the current wins and total games
            int currentWins = getWins(playerUUID);
            int currentTotalGames = getTotalGames(playerUUID);

            Bukkit.getLogger().info("Current stats - Wins: " + currentWins + ", Total Games: " + currentTotalGames);

            // Increment the values
            int newWins = currentWins + 1;
            int newTotalGames = currentTotalGames + 1;

            Bukkit.getLogger().info("New stats - Wins: " + newWins + ", Total Games: " + newTotalGames);

            // Update the database
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE players SET wins = ?, total_games = ? WHERE uuid = ?"
            )) {
                stmt.setInt(1, newWins);
                stmt.setInt(2, newTotalGames);
                stmt.setString(3, playerUUID.toString());

                int rowsUpdated = stmt.executeUpdate();
                Bukkit.getLogger().info("Rows updated: " + rowsUpdated);

                if (rowsUpdated == 0) {
                    Bukkit.getLogger().warning("No rows were updated. Player record may not exist. Attempting to insert default player.");
                    insertDefaultPlayer(playerUUID);
                    incrementWins(playerUUID); // Retry the increment
                } else {
                    Bukkit.getLogger().info("Wins incremented successfully for player: " + playerUUID);
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error incrementing wins for player " + playerUUID + ": " + e.getMessage());
        }
    }

    public static void incrementLosses(UUID playerUUID) {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL)) {
            Bukkit.getLogger().info("Attempting to increment losses for player: " + playerUUID);

            // Fetch the current losses and total games
            int currentLosses = getLosses(playerUUID);
            int currentTotalGames = getTotalGames(playerUUID);

            Bukkit.getLogger().info("Current stats - Losses: " + currentLosses + ", Total Games: " + currentTotalGames);

            // Increment the values
            int newLosses = currentLosses + 1;
            int newTotalGames = currentTotalGames + 1;

            Bukkit.getLogger().info("New stats - Losses: " + newLosses + ", Total Games: " + newTotalGames);

            // Update the database
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE players SET losses = ?, total_games = ? WHERE uuid = ?"
            )) {
                stmt.setInt(1, newLosses);
                stmt.setInt(2, newTotalGames);
                stmt.setString(3, playerUUID.toString());

                int rowsUpdated = stmt.executeUpdate();
                Bukkit.getLogger().info("Rows updated: " + rowsUpdated);

                if (rowsUpdated == 0) {
                    Bukkit.getLogger().warning("No rows were updated. Player record may not exist. Attempting to insert default player.");
                    insertDefaultPlayer(playerUUID);
                    incrementLosses(playerUUID); // Retry the increment
                } else {
                    Bukkit.getLogger().info("Losses incremented successfully for player: " + playerUUID);
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error incrementing losses for player " + playerUUID + ": " + e.getMessage());
        }
    }

    public static void setTitle(UUID playerUUID, String title) {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement("UPDATE players SET title = ? WHERE uuid = ?")) {
            stmt.setString(1, title);
            stmt.setString(2, playerUUID.toString());
            int updatedRows = stmt.executeUpdate();
            if (updatedRows == 0) {
                // If no rows were updated, the player doesn't exist, so we create a default record and retry
                insertDefaultPlayer(playerUUID);
                setTitle(playerUUID, title); // Retry setting the title
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error setting title: " + e.getMessage());
        }
    }

        // Existing methods remain unchanged
    public static int getMMR(UUID playerUUID) {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement("SELECT mmr FROM players WHERE uuid = ?")) {
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("mmr");
            } else {
                setMMR(playerUUID, 300);
                return 300;
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error fetching MMR: " + e.getMessage());
        }
        return 300;
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

    public static int getWins(UUID playerUUID) {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement("SELECT wins FROM players WHERE uuid = ?")) {
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("wins");
            } else {
                // Insert default only if the record truly does not exist
                insertDefaultPlayer(playerUUID);
                return 0;
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error fetching wins: " + e.getMessage());
        }
        return 0; // Default value on error
    }

    public static int getLosses(UUID playerUUID) {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement("SELECT losses FROM players WHERE uuid = ?")) {
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("losses");
            } else {
                // Insert default only if the record truly does not exist
                insertDefaultPlayer(playerUUID);
                return 0;
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error fetching losses: " + e.getMessage());
        }
        return 0; // Default value on error
    }

    public static int getTotalGames(UUID playerUUID) {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement("SELECT total_games FROM players WHERE uuid = ?")) {
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("total_games");
            } else {
                // Insert default only if the record truly does not exist
                insertDefaultPlayer(playerUUID);
                return 0;
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error fetching total games: " + e.getMessage());
        }
        return 0; // Default value on error
    }
}
