package dev.tserato.PvPSystem.GUI;

import dev.tserato.PvPSystem.Match.Match;
import dev.tserato.PvPSystem.Match.MatchManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GUIManager implements Listener {

    private final MatchManager matchManager;
    private final Set<Player> spectators = new HashSet<>(); // Track spectators

    public GUIManager(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    /**
     * Opens the GUI with all active matches for the player.
     *
     * @param player the player who opens the GUI
     */
    public void openMatchGUI(Player player) {
        List<Match> matches = matchManager.getActiveMatches();

        // Check if there are no active matches
        if (matches.isEmpty()) {
            player.sendMessage(Component.text("There are no active matches at the moment.").color(NamedTextColor.RED));
            return; // Stop further execution
        }

        int size = ((matches.size() + 8) / 9) * 9; // Calculate the inventory size
        Inventory gui = Bukkit.createInventory(null, size, ChatColor.GREEN + "Active Matches");

        for (int i = 0; i < matches.size(); i++) {
            Match match = matches.get(i);

            // Create an item for each match
            ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + "Match " + (i + 1));
                meta.setLore(List.of(
                        ChatColor.AQUA + "Player 1: " + match.getPlayer1().getName(),
                        ChatColor.AQUA + "Player 2: " + match.getPlayer2().getName(),
                        ChatColor.GRAY + "World: " + match.getArenaWorld().getName()
                ));
                item.setItemMeta(meta);
            }
            gui.setItem(i, item);
        }

        player.openInventory(gui);
    }

    /**
     * Handles clicks on the GUI to teleport players into the match as spectators.
     *
     * @param event the inventory click event
     */
    @EventHandler
    public void onMatchGUIClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(ChatColor.GREEN + "Active Matches")) {
            event.setCancelled(true); // Prevent taking items

            if (event.getCurrentItem() == null || event.getCurrentItem().getItemMeta() == null) {
                return;
            }

            String title = event.getCurrentItem().getItemMeta().getDisplayName();
            Player player = (Player) event.getWhoClicked();

            // Check if the player is already in a match
            if (matchManager.getActiveMatches().stream().anyMatch(
                    match -> match.getPlayer1().equals(player) || match.getPlayer2().equals(player))) {
                player.sendMessage(Component.text("You cannot spectate a match while you are in a match yourself.").color(NamedTextColor.RED));
                return;
            }

            // Extract match index from the item's display name
            if (title.startsWith(ChatColor.YELLOW + "Match")) {
                int matchIndex = Integer.parseInt(title.split(" ")[1]) - 1;
                List<Match> matches = matchManager.getActiveMatches();

                if (matchIndex >= 0 && matchIndex < matches.size()) {
                    Match match = matches.get(matchIndex);

                    // Add the player to the spectator list and teleport them
                    spectators.add(player);
                    player.setGameMode(GameMode.SPECTATOR);
                    player.teleport(match.getArenaWorld().getSpawnLocation());
                    player.sendMessage(Component.text("You are now spectating a match between:").color(NamedTextColor.GOLD));
                    player.sendMessage(Component.text("Player 1: " + match.getPlayer1().getName()).color(NamedTextColor.GOLD));
                    player.sendMessage(Component.text("Player 2: " + match.getPlayer2().getName()).color(NamedTextColor.AQUA));

                    // Schedule teleportation back when the match ends
                    Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugin("PvPSystem"), () -> {
                        while (matchManager.getActiveMatches().contains(match)) {
                            try {
                                Thread.sleep(1000); // Check every second
                            } catch (InterruptedException ignored) {
                            }
                        }
                        Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("PvPSystem"), () -> {
                            if (spectators.contains(player)) {
                                spectators.remove(player);
                                player.setGameMode(GameMode.SURVIVAL);
                                player.teleport(Bukkit.getWorld("world").getSpawnLocation());
                                player.sendMessage(Component.text("The match has ended. You have been teleported back.").color(NamedTextColor.GREEN));
                            }
                        });
                    });
                }
            }
        }
    }
}
