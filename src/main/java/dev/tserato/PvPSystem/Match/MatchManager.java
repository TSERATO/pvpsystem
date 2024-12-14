package dev.tserato.PvPSystem.Match;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class MatchManager {

    private final List<Match> activeMatches = new ArrayList<>();

    public void addMatch(Match match) {
        activeMatches.add(match);
    }

    public void removeMatch(Match match) {
        activeMatches.remove(match);
    }

    public List<Match> getActiveMatches() {
        return new ArrayList<>(activeMatches);
    }

    // Method to check if a player is currently in a match
    public boolean isPlayerInMatch(Player player) {
        for (Match match : activeMatches) {
            // Check if the player is part of this match
            if (match.getPlayer1().equals(player) || match.getPlayer2().equals(player)) {
                return true;
            }
        }
        return false;
    }
}
