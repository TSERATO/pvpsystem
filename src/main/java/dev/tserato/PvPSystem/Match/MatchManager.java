package dev.tserato.PvPSystem.Match;

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
}
