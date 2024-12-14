package dev.tserato.PvPSystem.Match;

import org.bukkit.entity.Player;
import org.bukkit.World;

public class Match {

    private final Player player1;
    private final Player player2;
    private final World arenaWorld;

    /**
     * Constructor for Match.
     *
     * @param player1    the first player in the match
     * @param player2    the second player in the match
     * @param arenaWorld the world where the match will take place
     */
    public Match(Player player1, Player player2, World arenaWorld) {
        this.player1 = player1;
        this.player2 = player2;
        this.arenaWorld = arenaWorld;
    }

    /**
     * Gets the first player.
     *
     * @return the first player
     */
    public Player getPlayer1() {
        return player1;
    }

    /**
     * Gets the second player.
     *
     * @return the second player
     */
    public Player getPlayer2() {
        return player2;
    }

    /**
     * Gets the arena world.
     *
     * @return the world where the match will take place
     */
    public World getArenaWorld() {
        return arenaWorld;
    }

    @Override
    public String toString() {
        return "Match{" +
                "player1=" + player1.getName() +
                ", player2=" + player2.getName() +
                ", arenaWorld=" + arenaWorld.getName() +
                '}';
    }
}
