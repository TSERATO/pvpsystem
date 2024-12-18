package dev.tserato.PvPSystem.Database;

import org.bukkit.Bukkit;

import java.sql.*;
import java.util.UUID;
import java.util.*;

public class Database {
    private static final String DATABASE_URL = "jdbc:sqlite:" + Bukkit.getPluginManager().getPlugin("PvPSystem").getDataFolder() + "/pvpsystem.db";

    public static void setupDatabase() {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL)) {
            if (conn != null) {
                String createPlayersTable = "CREATE TABLE IF NOT EXISTS players ("
                        + "uuid TEXT PRIMARY KEY, "
                        + "mmr INTEGER NOT NULL DEFAULT 300, "
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
                     "INSERT OR IGNORE INTO players (uuid, mmr, title) VALUES (?, 300, 'None')"
             )) {
            stmt.setString(1, playerUUID.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error inserting default player: " + e.getMessage());
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

    public static void setTitle(UUID playerUUID, String title) {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement("UPDATE players SET title = ? WHERE uuid = ?")) {
            stmt.setString(1, title);
            stmt.setString(2, playerUUID.toString());
            int updatedRows = stmt.executeUpdate();
            if (updatedRows == 0) {
                insertDefaultPlayer(playerUUID);
                setTitle(playerUUID, title);
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error setting title: " + e.getMessage());
        }
    }

    public static String getTitle(UUID playerUUID) {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement("SELECT title FROM players WHERE uuid = ?")) {
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("title");
            } else {
                insertDefaultPlayer(playerUUID);
                return "None"; // Default title
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error fetching title for player " + playerUUID + ": " + e.getMessage());
        }
        return "None"; // Default title on error
    }

    public static List<UUID> getAllPlayers() {
        List<UUID> playerUUIDs = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement("SELECT uuid FROM players");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                playerUUIDs.add(UUID.fromString(rs.getString("uuid")));
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error fetching player UUIDs: " + e.getMessage());
        }
        return playerUUIDs;
    }
}
