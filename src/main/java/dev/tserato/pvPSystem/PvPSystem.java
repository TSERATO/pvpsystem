package dev.tserato.pvPSystem;

import com.mojang.brigadier.Command;
import dev.tserato.pvPSystem.Database.Database;
import dev.tserato.pvPSystem.MMR.MMR;
import dev.tserato.pvPSystem.Queue.PlayerQueue;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import javax.swing.text.StyleContext;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class PvPSystem extends JavaPlugin implements Listener {

    private final Map<UUID, Boolean> frozenPlayers = new HashMap<>();
    private final Map<UUID, World> playerArenaMap = new HashMap<>();  // Tracks player's arenas
    private final Map<UUID, Long> winnerTimeMap = new HashMap<>(); // Tracks winner's time remaining
    private final PlayerQueue queue = new PlayerQueue();
    private final Map<UUID, Boolean> spectatingPlayers = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        getLogger().info("Starting up PvPSystem");

        File pluginFolder = getDataFolder();
        if (!pluginFolder.exists()) {
            if (pluginFolder.mkdirs()) {
                getLogger().info("PvPSystem folder created successfully.");
            } else {
                getLogger().severe("Failed to create PvPSystem folder!");
            }
        }

        Database.setupDatabase();

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
                                addToQueueAndSearch(player);
                                return Command.SINGLE_SUCCESS;
                            })
                            .build(),
                    "Join the ranked queue",
                    List.of("r")
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
                    "Join the ranked queue",
                    List.of("lr")
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
                                return Command.SINGLE_SUCCESS;
                            })
                            .build(),
                    "Return to the lobby",
                    List.of("l")
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
                                                String rank = MMR.getRankForMMR(mmr);
                                                sender.sendMessage(Component.text(player.getName() + " with UUID " + playerUUID + " has " + mmr + " MMR. This equals the rank of: " + rank).color(NamedTextColor.GREEN));
                                                return Command.SINGLE_SUCCESS;
                                            })
                            ).build(),
                    "Get the MMR of players",
                    List.of("getmmr")
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

        // Add player to the queue
        queue.addPlayer(player, playerMMR);
        player.sendMessage(Component.text("Looking for a ranked match...").color(NamedTextColor.YELLOW));

        // Attempt to find a match
        List<UUID> matchedPlayers = queue.findMatch();
        if (matchedPlayers.size() == 2) {
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
                } else {
                    player1.sendMessage(Component.text("Failed to create arena. Please contact the server admins.").color(NamedTextColor.RED));
                    player2.sendMessage(Component.text("Failed to create arena. Please contact the server admins.").color(NamedTextColor.RED));
                }
            }
        }
    }


    public void delFromQueue(Player player) {
        queue.removePlayer(player);
        player.sendMessage(Component.text("You have left the ranked queue.").color(NamedTextColor.YELLOW));
    }
    private void handleMMRAfterMatch(Player winner, Player loser) {
        int winnerMMR = Database.getMMR(winner.getUniqueId());
        int loserMMR = Database.getMMR(loser.getUniqueId());

        // Calculate new MMRs
        int newWinnerMMR = MMR.calculateNewMMR(winnerMMR, true, loserMMR);
        int newLoserMMR = MMR.calculateNewMMR(loserMMR, false, winnerMMR);

        // Get old and new ranks
        String oldWinnerRank = MMR.getRankForMMR(winnerMMR);
        String newWinnerRank = MMR.getRankForMMR(newWinnerMMR);
        String oldLoserRank = MMR.getRankForMMR(loserMMR);
        String newLoserRank = MMR.getRankForMMR(newLoserMMR);

        // Update MMR and ranks in the database
        Database.setMMR(winner.getUniqueId(), newWinnerMMR);
        Database.setMMR(loser.getUniqueId(), newLoserMMR);

        // Display rank change titles
        winner.showTitle(Title.title(Component.text(MMR.rankChangeTitle(oldWinnerRank, newWinnerRank)).color(NamedTextColor.GREEN), Component.text("You won.").color(NamedTextColor.GREEN)));
        loser.showTitle(Title.title(Component.text(MMR.rankChangeTitle(oldWinnerRank, newWinnerRank)).color(NamedTextColor.RED), Component.text("You lost.").color(NamedTextColor.RED)));
    }

    public World createArenaWorld(String arenaName) {
        File serverRoot = Bukkit.getWorldContainer();
        File sourceWorld = new File(serverRoot, "arena_template");
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
        if (!destination.exists() && !destination.mkdirs()) {
            throw new IOException("Failed to create destination folder: " + destination.getPath());
        }

        for (File file : Objects.requireNonNull(source.listFiles())) {
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

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (frozenPlayers.getOrDefault(player.getUniqueId(), false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        frozenPlayers.remove(player.getUniqueId()); // Clean up when the player leaves

        // If player was in an arena, check if it's empty
        World arenaWorld = playerArenaMap.get(player.getUniqueId());
        if (arenaWorld != null) {
            // Check if all players are gone from the arena
            boolean isArenaEmpty = true;
            for (Player p : arenaWorld.getPlayers()) {
                if (p.isOnline()) {
                    isArenaEmpty = false;
                    break;
                }
            }

            // If the arena is empty, delete it
            if (isArenaEmpty) {
                deleteArenaWorld(arenaWorld);
            }
        }

        playerArenaMap.remove(player.getUniqueId());
    }

    private void deleteArenaWorld(World arenaWorld) {
        String arenaName = arenaWorld.getName();
        Bukkit.getScheduler().runTask(this, () -> {
            Bukkit.unloadWorld(arenaWorld, false);
            File worldFolder = new File(Bukkit.getWorldContainer(), arenaName);
            try {
                deleteFolder(worldFolder);
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
        if (event.getLocation().getWorld().getName().startsWith("arena_")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity().getLocation().getWorld().getName().startsWith("arena_")) {
            if (event.getEntity() instanceof Player player) {
                if (spectatingPlayers.get(player.getUniqueId())) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (player.getWorld().getName().startsWith("arena_")) {
                Player winner = player.getKiller();
                World arenaWorld = playerArenaMap.get(player.getUniqueId());

                // Cancel the death event
                event.setCancelled(true);

                spectatingPlayers.put(player.getUniqueId(), true);
                spectatingPlayers.put(winner.getUniqueId(), true);


                Location loc1 = new Location(arenaWorld, 0, -60, 10, 180, 0);
                Location loc2 = new Location(arenaWorld, 0, -60, -10);
                player.teleport(loc1);
                winner.teleport(loc2);


                // Handle the winner's actions
                winnerTimeMap.put(winner.getUniqueId(), System.currentTimeMillis());

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (winner.isOnline()) {
                            winner.teleport(Objects.requireNonNull(Bukkit.getWorld("world")).getSpawnLocation());
                            player.teleport(Objects.requireNonNull(Bukkit.getWorld("world")).getSpawnLocation());
                            winner.sendMessage(Component.text("Teleporting back to lobby.").color(NamedTextColor.YELLOW));
                            player.sendMessage(Component.text("Teleporting back to lobby.").color(NamedTextColor.YELLOW));
                            spectatingPlayers.put(player.getUniqueId(), false);
                            spectatingPlayers.put(player.getUniqueId(), false);
                        }
                        if (arenaWorld != null) {
                            deleteArenaWorld(arenaWorld);
                        }
                    }
                }.runTaskLater(this, 20L * 15);

                // Handle MMR adjustments after match
                handleMMRAfterMatch(winner, player);
            }
        }
    }


    @Override
    public void onDisable() {
        getLogger().info("Shutting down PvPSystem");
    }
}
