package dev.tserato.PvPSystem;

import com.mojang.brigadier.Command;
import dev.tserato.PvPSystem.Database.Database;
import dev.tserato.PvPSystem.Database.StatisticsDatabase;
import dev.tserato.PvPSystem.Expansion.PAPIExpansion;
import dev.tserato.PvPSystem.GUI.GUIManager;
import dev.tserato.PvPSystem.MMR.MMR;
import dev.tserato.PvPSystem.Match.Match;
import dev.tserato.PvPSystem.Match.MatchManager;
import dev.tserato.PvPSystem.Queue.PlayerQueue;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.tserato.PvPSystem.MMR.MMR.getRankForMMR;

public class PvPSystem extends JavaPlugin implements Listener {

    private final Map<UUID, Boolean> frozenPlayers = new HashMap<>();
    private final Map<UUID, World> playerArenaMap = new HashMap<>();  // Tracks player's arenas
    private final Map<UUID, Long> winnerTimeMap = new HashMap<>(); // Tracks winner's time remaining
    private final PlayerQueue queue = new PlayerQueue();
    private final Map<UUID, Boolean> spectatingPlayers = new HashMap<>();
    private final Map<UUID, BossBar> activeBossBars = new HashMap<>();
    private final Map<UUID, UUID> pendingDuels = new HashMap<>();
    private PAPIExpansion papiExpansion;
    private MatchManager matchManager;
    private GUIManager guiManager;

    @Override
    public void onEnable() {
        matchManager = new MatchManager();
        guiManager = new GUIManager(matchManager);


        Bukkit.getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(guiManager, this);

        getLogger().info("Starting up PvPSystem");

        File pluginFolder = getDataFolder();
        if (!pluginFolder.exists()) {
            if (pluginFolder.mkdirs()) {
                getLogger().info("PvPSystem folder created successfully.");
            } else {
                getLogger().severe("Failed to create PvPSystem folder!");
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                checkAndDeleteEmptyArenas();
            }
        }.runTaskTimerAsynchronously(this, 0L, 20L * 60);

        Database.setupDatabase();
        StatisticsDatabase.setupStatisticsDatabase();

        this.papiExpansion = new PAPIExpansion(this);

        papiExpansion.register();

        @NotNull LifecycleEventManager<Plugin> manager = this.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            commands.register(
                    Commands.literal("ranked")
                            .executes(ctx -> {
                                CommandSender sender = ctx.getSource().getSender();
                                if (!(sender instanceof Player player)) {
                                    sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
                                    return Command.SINGLE_SUCCESS;
                                }
                                if (!spectatingPlayers.getOrDefault(player.getUniqueId(), false)) {
                                    if (!matchManager.isPlayerInMatch(player)) {
                                        addToQueueAndSearch(player);
                                    } else {
                                        player.sendMessage(Component.text("You can't queue while being in a match.").color(NamedTextColor.RED));
                                    }
                                }
                                return Command.SINGLE_SUCCESS;
                            })
                            .build(),
                    "Join the ranked queue",
                    List.of("rk")
            );
            commands.register(
                    Commands.literal("leaveranked")
                            .executes(ctx -> {
                                CommandSender sender = ctx.getSource().getSender();
                                if (!(sender instanceof Player player)) {
                                    sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
                                    return Command.SINGLE_SUCCESS;
                                }
                                delFromQueue(player);
                                return Command.SINGLE_SUCCESS;
                            })
                            .build(),
                    "Leave the ranked queue",
                    List.of("lr")
            );
            commands.register(
                    Commands.literal("leaderboard")
                            .executes(ctx -> {
                                CommandSender sender = ctx.getSource().getSender();
                                if (!(sender instanceof Player player)) {
                                    sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
                                    return Command.SINGLE_SUCCESS;
                                }
                                openTopMMRGUI(player);
                                return Command.SINGLE_SUCCESS;
                            })
                            .build(),
                    "Return to the lobby",
                    List.of("lb")
            );
            commands.register(
                    Commands.literal("lobby")
                            .executes(ctx -> {
                                CommandSender sender = ctx.getSource().getSender();
                                if (!(sender instanceof Player player)) {
                                    sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
                                    return Command.SINGLE_SUCCESS;
                                }
                                player.teleport(Bukkit.getWorld("world").getSpawnLocation());
                                player.getInventory().clear();
                                player.setGameMode(GameMode.SURVIVAL);
                                return Command.SINGLE_SUCCESS;
                            })
                            .build(),
                    "Return to the lobby",
                    List.of("l", "leave")
            );
            commands.register(
                    Commands.literal("matches")
                            .executes(ctx -> {
                                CommandSender sender = ctx.getSource().getSender();
                                if (!(sender instanceof Player player)) {
                                    sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
                                    return Command.SINGLE_SUCCESS;
                                }
                                guiManager.openMatchGUI(player);
                                return Command.SINGLE_SUCCESS;
                            })
                            .build(),
                    "Opens the Matches GUI",
                    List.of("")
            );
            commands.register(
                    Commands.literal("wins")
                            .executes(ctx -> {
                                CommandSender sender = ctx.getSource().getSender();
                                if (!(sender instanceof Player player)) {
                                    sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
                                    return Command.SINGLE_SUCCESS;
                                }
                                if (StatisticsDatabase.getWins(player.getUniqueId()) == 1) {
                                    player.sendMessage(Component.text("You have " + StatisticsDatabase.getWins(player.getUniqueId()) + " Win").color(NamedTextColor.GREEN));
                                } else {
                                    player.sendMessage(Component.text("You have " + StatisticsDatabase.getWins(player.getUniqueId()) + " Wins").color(NamedTextColor.GREEN));
                                }
                                return Command.SINGLE_SUCCESS;
                            })
                            .build(),
                    "Returns wins of a player",
                    List.of("")
            );
            commands.register(
                    Commands.literal("losses")
                            .executes(ctx -> {
                                CommandSender sender = ctx.getSource().getSender();
                                if (!(sender instanceof Player player)) {
                                    sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
                                    return Command.SINGLE_SUCCESS;
                                }
                                if (StatisticsDatabase.getLosses(player.getUniqueId()) == 1) {
                                    player.sendMessage(Component.text("You have " + StatisticsDatabase.getLosses(player.getUniqueId()) + " Loss").color(NamedTextColor.GREEN));
                                } else {
                                    player.sendMessage(Component.text("You have " + StatisticsDatabase.getLosses(player.getUniqueId()) + " Losses").color(NamedTextColor.GREEN));
                                }
                                return Command.SINGLE_SUCCESS;
                            })
                            .build(),
                    "Returns losses of a player",
                    List.of("")
            );
            commands.register(
                    Commands.literal("mmr")
                            .then(
                                    Commands.argument("player", ArgumentTypes.player())
                                            .executes(ctx -> {
                                                CommandSender sender = ctx.getSource().getSender();
                                                Player player = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst();
                                                assert player != null;
                                                UUID playerUUID = player.getUniqueId();
                                                int mmr = Database.getMMR(playerUUID);
                                                String rank = getRankForMMR(mmr);
                                                sender.sendMessage(Component.text(player.getName() + " with UUID " + playerUUID + " has " + mmr + " MMR. This equals the rank of: " + rank).color(NamedTextColor.GREEN));
                                                return Command.SINGLE_SUCCESS;
                                            })
                            ).build(),
                    "Get the MMR of players",
                    List.of("getmmr")
            );
            commands.register(
                    Commands.literal("duel")
                            .then(
                                    Commands.argument("player", ArgumentTypes.player())
                                            .executes(ctx -> {
                                                CommandSender sender = ctx.getSource().getSender();
                                                Player challenger = (Player) sender;
                                                Player challenged = ctx.getArgument("player", PlayerSelectorArgumentResolver.class)
                                                        .resolve(ctx.getSource())
                                                        .getFirst();

                                                handleDuelRequest(challenger, challenged);
                                                return Command.SINGLE_SUCCESS;
                                            })
                            )
                            .build(),
                    "Challenge a player to a duel",
                    List.of("duelchallenge")
            );

            commands.register(
                    Commands.literal("duelaccept")
                            .executes(ctx -> {
                                CommandSender sender = ctx.getSource().getSender();
                                Player player = (Player) sender;
                                handleDuelAccept(player);
                                return Command.SINGLE_SUCCESS;
                            })
                            .build(),
                    "Accept a duel challenge",
                    List.of("acceptduel")
            );

            commands.register(
                    Commands.literal("dueldecline")
                            .executes(ctx -> {
                                CommandSender sender = ctx.getSource().getSender();
                                Player player = (Player) sender;
                                handleDuelDecline(player);
                                return Command.SINGLE_SUCCESS;
                            })
                            .build(),
                    "Decline a duel challenge",
                    List.of("declineduel")
            );
        });
    }

    public void addToQueueAndSearch(Player player) {
        UUID playerUUID = player.getUniqueId();
        int playerMMR = Database.getMMR(playerUUID);

        if (playerMMR == -1) {
            playerMMR = 300;
            Database.setMMR(playerUUID, playerMMR);  // Save the default MMR
        }

        assignPlayerTitles();

        // Add player to the queue
        queue.addPlayer(player, playerMMR);
        player.sendMessage(Component.text("Looking for a ranked match...").color(NamedTextColor.YELLOW));

        // Remove any existing boss bar for this player
        if (activeBossBars.containsKey(playerUUID)) {
            BossBar existingBossBar = activeBossBars.get(playerUUID);
            player.hideBossBar(existingBossBar);
            activeBossBars.remove(playerUUID);
        }

        // Create and display a new boss bar
        BossBar bossBar = BossBar.bossBar(
                Component.text("Queueing for a match...").color(NamedTextColor.YELLOW),
                1.0f,  // Full progress bar
                BossBar.Color.GREEN,
                BossBar.Overlay.PROGRESS
        );

        player.showBossBar(bossBar);
        activeBossBars.put(playerUUID, bossBar);

        // Attempt to find a match
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            List<UUID> matchedPlayers = queue.findMatch();
            if (matchedPlayers.size() == 2) {
                Bukkit.getScheduler().runTask(this, () -> {
                    // Match found, remove the player from queue and stop the boss bar
                    queue.removePlayer(player);
                    player.hideBossBar(bossBar);
                    activeBossBars.remove(playerUUID);

                    Player player1 = Bukkit.getPlayer(matchedPlayers.get(0));
                    Player player2 = Bukkit.getPlayer(matchedPlayers.get(1));

                    if (player1 != null && player2 != null) {
                        String arenaName = "arena_" + UUID.randomUUID().toString().substring(0, 8);
                        World arenaWorld = createArenaWorld(arenaName);

                        if (arenaWorld != null) {
                            player1.sendMessage(Component.text("You have been matched! Teleporting to arena...").color(NamedTextColor.GREEN));
                            player2.sendMessage(Component.text("You have been matched! Teleporting to arena...").color(NamedTextColor.GREEN));

                            Location loc1 = new Location(arenaWorld, 0, -60, 10, 180, 0);
                            Location loc2 = new Location(arenaWorld, 0, -60, -10);
                            teleportWithCountdown(player1, loc1);
                            teleportWithCountdown(player2, loc2);

                            playerArenaMap.put(player1.getUniqueId(), arenaWorld);
                            playerArenaMap.put(player2.getUniqueId(), arenaWorld);

                            // Add the match to the MatchManager
                            Match match = new Match(player1, player2, arenaWorld);
                            matchManager.addMatch(match); // Add match to MatchManager

                            if (activeBossBars.containsKey(playerUUID)) {
                                BossBar existingBossBar = activeBossBars.get(playerUUID);
                                player.hideBossBar(existingBossBar);
                                activeBossBars.remove(playerUUID);
                            }
                        } else {
                            player1.sendMessage(Component.text("Failed to create arena. Please contact the server admins.").color(NamedTextColor.RED));
                            player2.sendMessage(Component.text("Failed to create arena. Please contact the server admins.").color(NamedTextColor.RED));
                        }
                    }
                });
            }
        });
    }

    private void handleDuelRequest(Player challenger, Player challenged) {
        UUID challengerUUID = challenger.getUniqueId();
        UUID challengedUUID = challenged.getUniqueId();

        if (challenger.equals(challenged)) {
            challenger.sendMessage(Component.text("You cannot challenge yourself to a duel.").color(NamedTextColor.RED));
            return;
        }

        if (pendingDuels.containsKey(challengerUUID)) {
            challenger.sendMessage(Component.text("You already have a pending duel challenge.").color(NamedTextColor.RED));
            return;
        }

        if (pendingDuels.containsValue(challengerUUID)) {
            challenger.sendMessage(Component.text("You cannot challenge someone while they are deciding on a duel.").color(NamedTextColor.RED));
            return;
        }

        pendingDuels.put(challengerUUID, challengedUUID);

        challenger.sendMessage(Component.text("You have challenged " + challenged.getName() + " to a duel!").color(NamedTextColor.YELLOW));
        challenged.sendMessage(Component.text(challenger.getName() + " has challenged you to a duel!").color(NamedTextColor.YELLOW));
        challenged.sendMessage(Component.text("Type /duelaccept to accept or /dueldecline to decline.").color(NamedTextColor.GREEN));
    }

    private void handleDuelAccept(Player challenged) {
        UUID challengedUUID = challenged.getUniqueId();
        UUID challengerUUID = pendingDuels.entrySet().stream()
                .filter(entry -> entry.getValue().equals(challengedUUID))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        if (challengerUUID == null) {
            challenged.sendMessage(Component.text("You have no pending duel requests.").color(NamedTextColor.RED));
            return;
        }

        Player challenger = Bukkit.getPlayer(challengerUUID);
        if (challenger == null || !challenger.isOnline()) {
            challenged.sendMessage(Component.text("The challenger is no longer online.").color(NamedTextColor.RED));
            pendingDuels.remove(challengerUUID);
            return;
        }

        pendingDuels.remove(challengerUUID);

        String arenaName = "duel_arena_" + UUID.randomUUID().toString().substring(0, 8);
        World arenaWorld = createArenaWorld(arenaName);

        if (arenaWorld != null) {
            challenger.sendMessage(Component.text("Your duel is starting! Teleporting to the arena...").color(NamedTextColor.GREEN));
            challenged.sendMessage(Component.text("Your duel is starting! Teleporting to the arena...").color(NamedTextColor.GREEN));

            Location loc1 = new Location(arenaWorld, 0, -60, 10, 180, 0);
            Location loc2 = new Location(arenaWorld, 0, -60, -10);
            teleportWithCountdown(challenger, loc1);
            teleportWithCountdown(challenged, loc2);

            playerArenaMap.put(challengerUUID, arenaWorld);
            playerArenaMap.put(challengedUUID, arenaWorld);

            // Add the duel match to the match manager (non-ranked)
            Match match = new Match(challenger, challenged, arenaWorld);
            matchManager.addMatch(match);
        } else {
            challenger.sendMessage(Component.text("Failed to create duel arena. Please contact the server admins.").color(NamedTextColor.RED));
            challenged.sendMessage(Component.text("Failed to create duel arena. Please contact the server admins.").color(NamedTextColor.RED));
        }
    }

    private void handleDuelDecline(Player challenged) {
        UUID challengedUUID = challenged.getUniqueId();
        UUID challengerUUID = pendingDuels.entrySet().stream()
                .filter(entry -> entry.getValue().equals(challengedUUID))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        if (challengerUUID == null) {
            challenged.sendMessage(Component.text("You have no pending duel requests to decline.").color(NamedTextColor.RED));
            return;
        }

        Player challenger = Bukkit.getPlayer(challengerUUID);
        if (challenger != null && challenger.isOnline()) {
            challenger.sendMessage(Component.text(challenged.getName() + " has declined your duel request.").color(NamedTextColor.RED));
        }

        challenged.sendMessage(Component.text("You have declined the duel request.").color(NamedTextColor.YELLOW));
        pendingDuels.remove(challengerUUID);
    }

    public void openTopMMRGUI(Player player) {
        // Fetch the top placeholders from the PAPIExpansion class
        Map<String, String> topMMRPlaceholders = papiExpansion.getTopMMRPlaceholders();

        // Create a new inventory with a size of 18 (2 rows) and a title
        Inventory topMMRInventory = Bukkit.createInventory(null, 18, "Top MMR Players");

        // Populate the inventory with player heads
        int slot = 0;
        for (Map.Entry<String, String> entry : topMMRPlaceholders.entrySet()) {
            String placeholderKey = entry.getKey(); // Example: "top_1"
            String placeholderValue = entry.getValue(); // Example: "PlayerName - Rank (MMR)"

            // Extract player name and details from placeholder value
            String[] parts = placeholderValue.split(" - ");
            if (parts.length >= 2) {
                String playerName = parts[0];
                String rankAndMMR = parts[1];

                // Get the player's UUID from the database
                UUID playerUUID = Bukkit.getOfflinePlayer(playerName).getUniqueId();

                // Create the player head item
                ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) playerHead.getItemMeta();

                if (meta != null) {
                    // Set the player head's owner
                    meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));

                    // Set display name and tooltip (lore)
                    meta.setDisplayName(ChatColor.GOLD + playerName);
                    meta.setLore(Arrays.asList(ChatColor.GRAY + rankAndMMR));

                    // Store the player's UUID in the PersistentDataContainer
                    NamespacedKey key = new NamespacedKey("pvpsystem", "player_uuid");
                    meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, playerUUID.toString());

                    playerHead.setItemMeta(meta);
                }

                // Add the player head to the inventory
                topMMRInventory.setItem(slot, playerHead);
                slot++;
            }
        }

        // Open the inventory for the player
        player.openInventory(topMMRInventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals("Top MMR Players")) {
            event.setCancelled(true); // Prevent taking items

            // Check if the clicked item is a player head
            if (event.getCurrentItem() != null && event.getCurrentItem().getItemMeta() != null &&
                    event.getCurrentItem().getItemMeta() instanceof SkullMeta) {

                SkullMeta meta = (SkullMeta) event.getCurrentItem().getItemMeta();

                OfflinePlayer clickedPlayer = meta.getOwningPlayer();
                Player player = (Player) event.getWhoClicked();
                openPlayerStatsGUI(player, clickedPlayer);
            }
        } else if (event.getView().getTitle().startsWith("Player Stats - ")) {
            event.setCancelled(true);
        }
    }


    public void openPlayerStatsGUI(Player viewer, OfflinePlayer clickedPlayer) {
        // Get the player's UUID
        UUID playerUUID = clickedPlayer.getUniqueId();

        // Fetch the player's statistics (wins, losses, and total games)
        String winStats = String.valueOf(StatisticsDatabase.getWins(playerUUID));
        String lossStats = String.valueOf(StatisticsDatabase.getLosses(playerUUID));
        String totalGamesStats = String.valueOf(StatisticsDatabase.getTotalGames(playerUUID));

        // Create a new inventory for displaying the player's stats
        Inventory statsInventory = Bukkit.createInventory(null, 9, "Player Stats - " + clickedPlayer.getName());

        // Create an item to display the stats in the GUI
        ItemStack winsItem = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta winsMeta = winsItem.getItemMeta();

        ItemStack totalGamesItem = new ItemStack(Material.PAPER);
        ItemMeta totalGamesMeta = totalGamesItem.getItemMeta();

        ItemStack lossItem = new ItemStack(Material.WOODEN_SWORD);
        ItemMeta lossMeta = lossItem.getItemMeta();

        if (winsMeta != null) {
            // Set the display name and lore (stats)
            winsMeta.setDisplayName(ChatColor.GREEN + "Wins for " + clickedPlayer.getName());
            winsMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + winStats
            ));

            winsItem.setItemMeta(winsMeta);
        }

        if (lossMeta != null) {
            // Set the display name and lore (stats)
            lossMeta.setDisplayName(ChatColor.RED + "Losses for " + clickedPlayer.getName());
            lossMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + lossStats
            ));

            lossItem.setItemMeta(lossMeta);
        }

        if (totalGamesMeta != null) {
            // Set the display name and lore (stats)
            totalGamesMeta.setDisplayName(ChatColor.AQUA + "Total Games for " + clickedPlayer.getName());
            totalGamesMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + totalGamesStats
            ));

            totalGamesItem.setItemMeta(totalGamesMeta);
        }

        // Set the stats item in the center slot
        statsInventory.setItem(2, winsItem);
        statsInventory.setItem(4, totalGamesItem);
        statsInventory.setItem(6, lossItem);

        // Open the stats inventory for the viewer (the player who clicked the head)
        viewer.openInventory(statsInventory);
    }

    public void delFromQueue(Player player) {
        queue.removePlayer(player);
        if (activeBossBars.containsKey(player.getUniqueId())) {
            BossBar existingBossBar = activeBossBars.get(player.getUniqueId());
            player.hideBossBar(existingBossBar);
            activeBossBars.remove(player.getUniqueId());
        }
        player.sendMessage(Component.text("You have left the ranked queue.").color(NamedTextColor.YELLOW));
    }

    private void handleMMRAfterMatch(Player winner, Player loser) {

        assignPlayerTitles();

        int winnerMMR = Database.getMMR(winner.getUniqueId());
        int loserMMR = Database.getMMR(loser.getUniqueId());

        // Calculate new MMRs
        int newWinnerMMR = MMR.calculateNewMMR(winnerMMR, true, loserMMR);
        int newLoserMMR = MMR.calculateNewMMR(loserMMR, false, winnerMMR);

        // Get old and new ranks
        String oldWinnerRank = getRankForMMR(winnerMMR);
        String newWinnerRank = getRankForMMR(newWinnerMMR);
        String oldLoserRank = getRankForMMR(loserMMR);
        String newLoserRank = getRankForMMR(newLoserMMR);

        // Get colors for the new ranks
        NamedTextColor winnerOldColor = MMR.getColorForRank(oldWinnerRank);
        NamedTextColor winnerNewColor = MMR.getColorForRank(newWinnerRank);
        NamedTextColor loserOldColor = MMR.getColorForRank(oldLoserRank);
        NamedTextColor loserNewColor = MMR.getColorForRank(newLoserRank);

        // Update MMR and ranks in the database
        Database.setMMR(winner.getUniqueId(), newWinnerMMR);
        Database.setMMR(loser.getUniqueId(), newLoserMMR);

        StatisticsDatabase.incrementWins(winner.getUniqueId());
        StatisticsDatabase.incrementLosses(loser.getUniqueId());

        // Check if the winner or loser has ranked up or down
        boolean winnerRankedUp = !oldWinnerRank.equals(newWinnerRank);
        boolean loserRankedDown = !oldLoserRank.equals(newLoserRank);

        // Display rank change titles with animations
        if (winnerRankedUp) {
            showRankChangeAnimation(winner, oldWinnerRank, newWinnerRank, winnerOldColor, winnerNewColor, true);
        } else {
            winner.showTitle(
                    Title.title(
                            Component.text(oldWinnerRank).color(winnerOldColor),
                            Component.text("You won.").color(NamedTextColor.GREEN)
                    )
            );
        }

        if (loserRankedDown) {
            showRankChangeAnimation(loser, oldLoserRank, newLoserRank, loserOldColor, loserNewColor, false);
        } else {
            loser.showTitle(
                    Title.title(
                            Component.text(oldLoserRank).color(loserOldColor),
                            Component.text("You lost.").color(NamedTextColor.RED)
                    )
            );
        }
    }

    /**
     * Shows a rank change animation with color transition and final title change.
     *
     * @param player       The player to show the animation to.
     * @param oldRank      The old rank of the player.
     * @param newRank      The new rank of the player.
     * @param oldColor     The initial color of the rank.
     * @param newColor     The final color of the rank.
     * @param rankedUp     Whether the player ranked up or down.
     */
    private void showRankChangeAnimation(Player player, String oldRank, String newRank, NamedTextColor oldColor, NamedTextColor newColor, boolean rankedUp) {
        final int animationSteps = 3;
        final long animationDelay = 20L; // 1 second in ticks

        // Calculate intermediate colors for the gradient
        List<NamedTextColor> colorGradient = createColorGradient(oldColor, newColor);

        // Use AtomicInteger to store task ID, which can be modified later
        AtomicInteger taskId = new AtomicInteger();

        // Schedule the animation
        taskId.set(Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            int step = 0;

            @Override
            public void run() {
                if (step < colorGradient.size()) {
                    // Show intermediate title with transitioning colors
                    player.showTitle(
                            Title.title(
                                    Component.text(oldRank).color(colorGradient.get(step)),
                                    Component.text(rankedUp ? "You won." : "You lost.").color(rankedUp ? NamedTextColor.GREEN : NamedTextColor.RED)
                            )
                    );
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    step++;
                } else {
                    // End of animation, show the new rank title
                    player.showTitle(
                            Title.title(
                                    Component.text(newRank).color(newColor),
                                    Component.text(rankedUp ? "Rank up!" : "Rank down!").color(rankedUp ? NamedTextColor.GOLD : NamedTextColor.RED)
                            )
                    );
                    player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 1.0f);
                    Bukkit.getScheduler().cancelTask(taskId.get());
                }
            }
        }, 0L, animationDelay).getTaskId());
    }

    /**
     * Creates a gradient of colors between two NamedTextColors.
     *
     * @param startColor The starting color of the gradient.
     * @param endColor   The ending color of the gradient.
     * @return A list of NamedTextColors representing the gradient.
     */
    private List<NamedTextColor> createColorGradient(NamedTextColor startColor, NamedTextColor endColor) {
        List<NamedTextColor> gradient = new ArrayList<>();
        int startRed = startColor.red();
        int startGreen = startColor.red();
        int startBlue = startColor.red();

        int endRed = endColor.red();
        int endGreen = endColor.green();
        int endBlue = endColor.blue();

        for (int i = 0; i <= 3; i++) {
            double ratio = (double) i / 3;
            int red = (int) (startRed + (endRed - startRed) * ratio);
            int green = (int) (startGreen + (endGreen - startGreen) * ratio);
            int blue = (int) (startBlue + (endBlue - startBlue) * ratio);
            gradient.add(NamedTextColor.namedColor(red << 16 | green << 8 | blue));
        }
        return gradient;
    }

    public World createArenaWorld(String arenaName) {
        File serverRoot = Bukkit.getWorldContainer();
        File sourceWorld = new File(serverRoot, "template_arena");
        File arenaWorld = new File(serverRoot, arenaName);

        try {
            // Copy the world directory
            copyFolder(sourceWorld, arenaWorld);

            // Delete uid.dat to reset the world's unique ID
            File uidFile = new File(arenaWorld, "uid.dat");
            if (uidFile.exists() && !uidFile.delete()) {
                throw new IOException("Failed to delete uid.dat in arena world: " + arenaWorld.getName());
            }

            // Load the new world
            WorldCreator creator = new WorldCreator(arenaName);
            return creator.createWorld();

        } catch (IOException e) {
            getLogger().severe("Error while creating arena world: " + e.getMessage());
        }

        return null;
    }

    private void copyFolder(File source, File destination) throws IOException {
        if (!source.exists() || !source.isDirectory()) {
            throw new IOException("Source folder does not exist or is not a directory: " + source.getPath());
        }

        if (!destination.exists() && !destination.mkdirs()) {
            throw new IOException("Failed to create destination folder: " + destination.getPath());
        }

        File[] files = source.listFiles();
        if (files == null) {
            throw new IOException("Failed to list files in source folder: " + source.getPath());
        }

        for (File file : files) {
            File destFile = new File(destination, file.getName());
            if (file.isDirectory()) {
                copyFolder(file, destFile);
            } else {
                Files.copy(file.toPath(), destFile.toPath());
            }
        }
    }

    private void teleportWithCountdown(Player player, Location location) {
        // Teleport the player and mark them as frozen
        player.teleport(location);
        frozenPlayers.put(player.getUniqueId(), true);

        String playerName = player.getName();

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "kit give diamondpot " + playerName);

        player.setHealth(20);

        updateTabAppearance(player, true);

        if (activeBossBars.containsKey(player.getUniqueId())) {
            BossBar existingBossBar = activeBossBars.get(player.getUniqueId());
            player.hideBossBar(existingBossBar);
            activeBossBars.remove(player.getUniqueId());
        }

        new BukkitRunnable() {
            int countdown = 10;

            @Override
            public void run() {
                if (countdown > 0) {
                    if (countdown == 3) {
                        player.showTitle(Title.title(Component.text(countdown).color(NamedTextColor.GREEN), Component.text("")));
                    } else if (countdown == 2) {
                        player.showTitle(Title.title(Component.text(countdown).color(NamedTextColor.YELLOW), Component.text("")));
                    } else if (countdown == 1) {
                        player.showTitle(Title.title(Component.text(countdown).color(NamedTextColor.RED), Component.text("")));
                    } else {
                        player.showTitle(Title.title(Component.text(countdown).color(NamedTextColor.AQUA), Component.text("")));
                    }
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    if (activeBossBars.containsKey(player.getUniqueId())) {
                        BossBar existingBossBar = activeBossBars.get(player.getUniqueId());
                        player.hideBossBar(existingBossBar);
                        activeBossBars.remove(player.getUniqueId());
                    }
                    countdown--;
                } else {
                    player.showTitle(Title.title(Component.text("Fight!").color(NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD), Component.text("")));
                    player.playSound(player.getLocation(), Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.0f, 1.0f);
                    frozenPlayers.put(player.getUniqueId(), false); // Unfreeze the player
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 20L); // Run every second (20 ticks)
    }

    private void updateTabAppearance(Player player, boolean inMatch) {
        if (inMatch) {
            // Using 'obfuscated' effect or muted color to simulate the "grayed-out" or faded look
            player.playerListName(Component.text("⚔ ").color(NamedTextColor.GRAY) // Keep the sword emoji normal
                    .append(Component.text(player.getName()).color(NamedTextColor.GRAY))); // Italicize and gray the player name
        } else {
            // Normal appearance
            player.playerListName(Component.text(player.getName()).color(NamedTextColor.WHITE));
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        // Use getOrDefault to avoid NullPointerException
        if (frozenPlayers.getOrDefault(player.getUniqueId(), false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (frozenPlayers.getOrDefault(player.getUniqueId(), false) || spectatingPlayers.getOrDefault(player.getUniqueId(), false)) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType() == Material.BOW) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (frozenPlayers.getOrDefault(player.getUniqueId(), false)) {
            event.setCancelled(true);
        }
    }


    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Arrow || event.getEntity() instanceof ThrownPotion) {
            if (event.getEntity().getShooter() instanceof Player shooter) {
                if (frozenPlayers.getOrDefault(shooter.getUniqueId(), false) || spectatingPlayers.getOrDefault(shooter.getUniqueId(), false)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    private void checkAndDeleteEmptyArenas() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getName().startsWith("arena_")) {
                if (world.getPlayers().isEmpty()) {
                    getLogger().info("Deleting empty arena world: " + world.getName());
                    deleteArenaWorld(world);
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity target = event.getEntity();

        if (damager instanceof Player && target instanceof Player) {
            double originalDamage = event.getDamage();
            double boostedDamage = originalDamage * 1.33;
            event.setDamage(boostedDamage);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        frozenPlayers.remove(player.getUniqueId()); // Clean up when the player leaves

        player.getInventory().clear();

        updateTabAppearance(player, false);

        // If player was in an arena, handle match logic
        World arenaWorld = playerArenaMap.get(player.getUniqueId());
        if (arenaWorld != null && arenaWorld.getName().startsWith("arena_") && !spectatingPlayers.getOrDefault(player.getUniqueId(), false)) {
            // Determine the remaining player in the arena
            Player winner = null;
            for (Player p : arenaWorld.getPlayers()) {
                if (!p.equals(player) && p.isOnline()) {
                    winner = p; // The remaining player becomes the winner
                    break;
                }
            }

            assignPlayerTitles();

            if (winner != null) {
                // Declare final reference for inner class
                final Player finalWinner = winner;

                finalWinner.setHealth(20);
                clearAllPotionEffects(finalWinner);

                spectatingPlayers.put(finalWinner.getUniqueId(), true);

                Location locWinner = new Location(arenaWorld, 0, -60, -10);
                finalWinner.teleport(locWinner);

                finalWinner.getInventory().clear();

                // Schedule teleportation back to the lobby
                winnerTimeMap.put(finalWinner.getUniqueId(), System.currentTimeMillis());
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (finalWinner.isOnline() && finalWinner.getWorld().getName().startsWith("arena_") || finalWinner.isOnline() && finalWinner.getWorld().getName().startsWith("duel_arena_")) {
                            finalWinner.teleport(Objects.requireNonNull(Bukkit.getWorld("world")).getSpawnLocation());
                            finalWinner.sendMessage(Component.text("Teleporting back to lobby.").color(NamedTextColor.YELLOW));
                            spectatingPlayers.put(finalWinner.getUniqueId(), false);
                            finalWinner.getInventory().clear();
                        }
                        deleteArenaWorld(arenaWorld);
                    }
                }.runTaskLater(this, 20L * 15);

                // Handle MMR adjustments after match
                handleMMRAfterMatch(finalWinner, player);
            } else {
                // No remaining player, just delete the arena
                deleteArenaWorld(arenaWorld);
            }

            // Remove the match from MatchManager
            matchManager.getActiveMatches().stream()
                    .filter(match -> match.getArenaWorld().equals(arenaWorld))
                    .findFirst().ifPresent(matchToRemove -> matchManager.removeMatch(matchToRemove));

        } else if (arenaWorld != null && arenaWorld.getName().startsWith("duel_arena_") && !spectatingPlayers.getOrDefault(player.getUniqueId(), false)) {
            // Determine the remaining player in the arena
            Player winner = null;
            for (Player p : arenaWorld.getPlayers()) {
                if (!p.equals(player) && p.isOnline()) {
                    winner = p; // The remaining player becomes the winner
                    break;
                }
            }

            assignPlayerTitles();

            if (winner != null) {
                // Declare final reference for inner class
                final Player finalWinner = winner;

                finalWinner.setHealth(20);
                clearAllPotionEffects(finalWinner);

                spectatingPlayers.put(finalWinner.getUniqueId(), true);

                Location locWinner = new Location(arenaWorld, 0, -60, -10);
                finalWinner.teleport(locWinner);

                finalWinner.getInventory().clear();

                // Schedule teleportation back to the lobby
                winnerTimeMap.put(finalWinner.getUniqueId(), System.currentTimeMillis());
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (finalWinner.isOnline() && finalWinner.getWorld().getName().startsWith("arena_") || finalWinner.isOnline() && finalWinner.getWorld().getName().startsWith("duel_arena_")) {
                            finalWinner.teleport(Objects.requireNonNull(Bukkit.getWorld("world")).getSpawnLocation());
                            finalWinner.sendMessage(Component.text("Teleporting back to lobby.").color(NamedTextColor.YELLOW));
                            spectatingPlayers.put(finalWinner.getUniqueId(), false);
                            finalWinner.getInventory().clear();
                        }
                        deleteArenaWorld(arenaWorld);
                    }
                }.runTaskLater(this, 20L * 15);
            } else {
                // No remaining player, just delete the arena
                deleteArenaWorld(arenaWorld);
            }

            // Remove the match from MatchManager
            matchManager.getActiveMatches().stream()
                    .filter(match -> match.getArenaWorld().equals(arenaWorld))
                    .findFirst().ifPresent(matchToRemove -> matchManager.removeMatch(matchToRemove));
        }

        playerArenaMap.remove(player.getUniqueId());
    }

    private void checkAndDeleteArenaWorld(World world) {
        // Check if the world name starts with "arena_"
        if (world.getName().startsWith("arena_") || world.getName().startsWith("duel_arena_")) {
            // Check if the world is empty
            long playerCount = world.getPlayers().size();
            if (playerCount == 0) {
                // Trigger the delete method
                deleteArenaWorld(world);
            }
        }
    }

    private void deleteArenaWorld(World arenaWorld) {
        String arenaName = arenaWorld.getName();
        Bukkit.getScheduler().runTask(this, () -> {
                File worldFolder = new File(Bukkit.getWorldContainer(), arenaName);
                try {
                    deleteFolder(worldFolder);
                    getLogger().info("Successfully deleted arena world: " + arenaName);
                } catch (IOException e) {
                    getLogger().severe("Failed to delete arena world: " + e.getMessage());
                }
        });
    }

    private void deleteFolder(File folder) throws IOException {
        if (folder.exists()) {
            for (File file : Objects.requireNonNull(folder.listFiles())) {
                if (file.isDirectory()) {
                    deleteFolder(file);
                } else {
                    if (!file.delete()) {
                        throw new IOException("Failed to delete file: " + file.getPath());
                    }
                }
            }
            if (!folder.delete()) {
                throw new IOException("Failed to delete folder: " + folder.getPath());
            }
        }
    }

    @EventHandler
    public void onMobSpawn(EntitySpawnEvent event) {
        if (event.getLocation().getWorld().getName().startsWith("arena_") || event.getLocation().getWorld().getName().startsWith("duel_arena_")) {
            if(event.getEntity().getType().isAlive()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            UUID playerUUID = player.getUniqueId();

            // Check if the player is in the spectatingPlayers map and if they're spectating
            if (spectatingPlayers.getOrDefault(playerUUID, false)) {
                event.setCancelled(true); // Cancel the damage event if they are spectating
            }
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        checkAndDeleteArenaWorld(event.getFrom());
    }

    @EventHandler
    public void onPlayerDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (player.getWorld().getName().startsWith("arena_")) {
                Player winner = player.getKiller();
                World arenaWorld = playerArenaMap.get(player.getUniqueId());

                // Cancel the death event
                event.setCancelled(true);

                assert winner != null;

                assignPlayerTitles();

                // Reset players and handle cleanup
                winner.setHealth(20);
                player.setHealth(20);

                winner.getInventory().clear();
                player.getInventory().clear();

                spectatingPlayers.put(winner.getUniqueId(), true);
                spectatingPlayers.put(player.getUniqueId(), true);

                updateTabAppearance(winner, false);
                updateTabAppearance(player, false);

                clearAllPotionEffects(winner);
                clearAllPotionEffects(player);

                clearEntitiesExceptPlayers(arenaWorld);

                Location loc1 = new Location(arenaWorld, 0, -60, 10, 180, 0);
                Location loc2 = new Location(arenaWorld, 0, -60, -10);
                player.teleport(loc1);
                winner.teleport(loc2);

                winnerTimeMap.put(winner.getUniqueId(), System.currentTimeMillis());

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (winner.isOnline() && winner.getWorld().getName().startsWith("arena_") || winner.isOnline() && winner.getWorld().getName().startsWith("duel_arena_") || player.isOnline() && player.getWorld().getName().startsWith("arena_") || player.isOnline() && player.getWorld().getName().startsWith("duel_arena_")) {
                            winner.teleport(Objects.requireNonNull(Bukkit.getWorld("world")).getSpawnLocation());
                            player.teleport(Objects.requireNonNull(Bukkit.getWorld("world")).getSpawnLocation());
                            winner.sendMessage(Component.text("Teleporting back to lobby.").color(NamedTextColor.YELLOW));
                            player.sendMessage(Component.text("Teleporting back to lobby.").color(NamedTextColor.YELLOW));
                            spectatingPlayers.put(player.getUniqueId(), false);
                            spectatingPlayers.put(winner.getUniqueId(), false);
                            winner.getInventory().clear();
                            player.getInventory().clear();
                        }
                        deleteArenaWorld(arenaWorld);
                    }
                }.runTaskLater(this, 20L * 15);

                // Handle MMR adjustments
                handleMMRAfterMatch(winner, player);

                // Remove the match from the MatchManager
                matchManager.getActiveMatches().stream()
                        .filter(match -> match.getArenaWorld().equals(arenaWorld))
                        .findFirst().ifPresent(matchToRemove -> matchManager.removeMatch(matchToRemove));
            } else if (player.getWorld().getName().startsWith("duel_arena_")) {
                Player winner = player.getKiller();
                World arenaWorld = playerArenaMap.get(player.getUniqueId());

                // Cancel the death event
                event.setCancelled(true);

                assert winner != null;

                assignPlayerTitles();

                // Reset players and handle cleanup
                winner.setHealth(20);
                player.setHealth(20);

                winner.getInventory().clear();
                player.getInventory().clear();

                spectatingPlayers.put(winner.getUniqueId(), true);
                spectatingPlayers.put(player.getUniqueId(), true);

                updateTabAppearance(winner, false);
                updateTabAppearance(player, false);

                clearAllPotionEffects(winner);
                clearAllPotionEffects(player);

                clearEntitiesExceptPlayers(arenaWorld);

                Location loc1 = new Location(arenaWorld, 0, -60, 10, 180, 0);
                Location loc2 = new Location(arenaWorld, 0, -60, -10);
                player.teleport(loc1);
                winner.teleport(loc2);

                winner.showTitle(Title.title(Component.text("You Won!").color(NamedTextColor.GREEN), Component.text("")));
                winner.showTitle(Title.title(Component.text("You Lost!").color(NamedTextColor.RED), Component.text("")));

                winnerTimeMap.put(winner.getUniqueId(), System.currentTimeMillis());

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (winner.isOnline() && winner.getWorld().getName().startsWith("arena_") || winner.isOnline() && winner.getWorld().getName().startsWith("duel_arena_") || player.isOnline() && player.getWorld().getName().startsWith("arena_") || player.isOnline() && player.getWorld().getName().startsWith("duel_arena_")) {
                            winner.teleport(Objects.requireNonNull(Bukkit.getWorld("world")).getSpawnLocation());
                            player.teleport(Objects.requireNonNull(Bukkit.getWorld("world")).getSpawnLocation());
                            winner.sendMessage(Component.text("Teleporting back to lobby.").color(NamedTextColor.YELLOW));
                            player.sendMessage(Component.text("Teleporting back to lobby.").color(NamedTextColor.YELLOW));
                            spectatingPlayers.put(player.getUniqueId(), false);
                            spectatingPlayers.put(winner.getUniqueId(), false);
                            winner.getInventory().clear();
                            player.getInventory().clear();
                        }
                        deleteArenaWorld(arenaWorld);
                    }
                }.runTaskLater(this, 20L * 15);

                // Remove the match from the MatchManager
                matchManager.getActiveMatches().stream()
                        .filter(match -> match.getArenaWorld().equals(arenaWorld))
                        .findFirst().ifPresent(matchToRemove -> matchManager.removeMatch(matchToRemove));
            }
        }
    }

    public void clearAllPotionEffects(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    public void clearEntitiesExceptPlayers(World world) {
        for (Entity entity : world.getEntities()) {
            // Check if the entity is not a player
            if (entity.getType() != EntityType.PLAYER) {
                entity.remove(); // Remove the entity
            }
        }
    }

    public void assignPlayerTitles() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> { // Run asynchronously for database operations
            try {
                // Fetch all players from the database
                List<UUID> allPlayerUUIDs = Database.getAllPlayers();
                List<PlayerMMR> playerMMRs = new ArrayList<>();

                for (UUID uuid : allPlayerUUIDs) {
                    int mmr = Database.getMMR(uuid);
                    playerMMRs.add(new PlayerMMR(uuid, mmr));
                }

                // Sort players by MMR in descending order
                playerMMRs.sort((a, b) -> Integer.compare(b.getMMR(), a.getMMR()));

                // Determine rankings
                int totalPlayers = playerMMRs.size();
                int top1Index = 0; // Highest ranking player (Champion)
                int top5PercentIndex = Math.max((int) (totalPlayers * 0.05) - 1, 0);
                int top10PercentIndex = Math.max((int) (totalPlayers * 0.10) - 1, 0);

                // Assign titles
                for (int i = 0; i < playerMMRs.size(); i++) {
                    PlayerMMR player = playerMMRs.get(i);
                    String playerName = Bukkit.getOfflinePlayer(player.getUuid()).getName();

                    if (i == top1Index) {
                        // Champion title for the top player
                        assignLuckPermsTitle(playerName, "champion");
                    } else if (i <= top5PercentIndex) {
                        // Duelist title for top 5% players
                        assignLuckPermsTitle(playerName, "duelist");
                    } else if (i <= top10PercentIndex) {
                        // Gladiator title for top 10% players
                        assignLuckPermsTitle(playerName, "gladiator");
                    } else {
                        // Reset the title for others (optional)
                        assignLuckPermsTitle(playerName, "default");
                    }
                }

            } catch (Exception e) {
                Bukkit.getLogger().severe("Error assigning titles: " + e.getMessage());
            }
        });
    }

    // Helper method to assign LuckPerms title
    private void assignLuckPermsTitle(String playerName, String title) {
        if (playerName == null) return;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "/lp user " + playerName + " parent set " + title);
    }

    // Data structure for storing player MMR
    private static class PlayerMMR {
        private final UUID uuid;
        private final int mmr;

        public PlayerMMR(UUID uuid, int mmr) {
            this.uuid = uuid;
            this.mmr = mmr;
        }

        public UUID getUuid() {
            return uuid;
        }

        public int getMMR() {
            return mmr;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.joinMessage(Component.text("[").color(NamedTextColor.GRAY).append(Component.text("+").color(NamedTextColor.GREEN).append(Component.text("] ").color(NamedTextColor.GRAY).append(Component.text(player.getName()).color(NamedTextColor.GRAY)))));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        event.quitMessage(Component.text("[").color(NamedTextColor.GRAY).append(Component.text("-").color(NamedTextColor.RED).append(Component.text("] ").color(NamedTextColor.GRAY).append(Component.text(player.getName()).color(NamedTextColor.GRAY)))));
    }

    @Override
    public void onDisable() {
        getLogger().info("Shutting down PvPSystem");
    }
}
