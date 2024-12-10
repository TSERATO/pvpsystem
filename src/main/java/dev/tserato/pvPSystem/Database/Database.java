package dev.tserato.pvPSystem.Database;

import org.bukkit.Bukkit;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Database {
    private static final String DATABASE_URL = "jdbc:sqlite:" + Bukkit.getPluginManager().getPlugin("PvPSystem").getDataFolder() + "/pvpsystem.db";

    public static void setupDatabase() {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL)) {
            if (conn != null) {
                String createPlayersTable = "CREATE TABLE IF NOT EXISTS players ("
                        + "uuid TEXT PRIMARY KEY, "
                        + "mmr INTEGER NOT NULL)";
                String createQueueTable = "CREATE TABLE IF NOT EXISTS queue ("
                        + "uuid TEXT PRIMARY KEY)";
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(createPlayersTable);
                    stmt.execute(createQueueTable);
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
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error fetching MMR: " + e.getMessage());
        }
        return 1200; // Default MMR
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

    public static void addToQueue(UUID playerUUID) {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement("INSERT OR REPLACE INTO queue (uuid) VALUES (?)")) {
            stmt.setString(1, playerUUID.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error adding player to queue: " + e.getMessage());
        }
    }

    public static List<UUID> getQueue(int mmr) {
        List<UUID> queue = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement("SELECT uuid FROM queue")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                if (Math.abs(getMMR(uuid) - mmr) <= 100) { // Check for similar MMR
                    queue.add(uuid);
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error getting players from queue: " + e.getMessage());
        }
        return queue;
    }

    public static void removeFromQueue(UUID playerUUID) {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM queue WHERE uuid = ?")) {
            stmt.setString(1, playerUUID.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error removing player from queue: " + e.getMessage());
        }
    }
}