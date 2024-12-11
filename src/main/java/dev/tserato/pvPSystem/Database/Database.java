package dev.tserato.pvPSystem.Database;

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
                        + "mmr INTEGER NOT NULL DEFAULT 1200)";
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
                setMMR(playerUUID, 1200); // Default MMR
                return 1200; // Return default MMR
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
}
