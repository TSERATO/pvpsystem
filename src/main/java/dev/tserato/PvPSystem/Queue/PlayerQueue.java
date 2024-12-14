package dev.tserato.PvPSystem.Queue;

import org.bukkit.entity.Player;

import java.util.*;

public class PlayerQueue {

    private static final int MMR_TOLERANCE = 70; // Tolerance for matchmaking
    private final Map<UUID, Integer> queue = new HashMap<>(); // Player UUID to MMR mapping

    /**
     * Add a player to the queue.
     *
     * @param player Player to add.
     * @param mmr    Player's MMR.
     */
    public synchronized void addPlayer(Player player, int mmr) {
        queue.put(player.getUniqueId(), mmr);
    }

    /**
     * Remove a player from the queue.
     *
     * @param player Player to remove.
     */
    public synchronized void removePlayer(Player player) {
        queue.remove(player.getUniqueId());
    }

    /**
     * Try to find a match for players in the queue.
     *
     * @return A list of matched players or an empty list if no match found.
     */
    public synchronized List<UUID> findMatch() {
        List<UUID> matchedPlayers = new ArrayList<>();
        for (UUID player1 : queue.keySet()) {
            int mmr1 = queue.get(player1);

            for (UUID player2 : queue.keySet()) {
                if (player1.equals(player2)) continue;

                int mmr2 = queue.get(player2);
                if (Math.abs(mmr1 - mmr2) <= MMR_TOLERANCE) {
                    matchedPlayers.add(player1);
                    matchedPlayers.add(player2);
                    queue.remove(player1);
                    queue.remove(player2);
                    return matchedPlayers;
                }
            }
        }
        return matchedPlayers; // Empty list if no match found
    }

    /**
     * Check if the queue is empty.
     *
     * @return True if empty, otherwise false.
     */
    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }
}
