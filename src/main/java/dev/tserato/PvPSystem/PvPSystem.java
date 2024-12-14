package dev.tserato.PvPSystem;

import com.mojang.brigadier.Command;
import dev.tserato.PvPSystem.Database.Database;
import dev.tserato.PvPSystem.Expansion.PAPIExpansion;
import dev.tserato.PvPSystem.MMR.MMR;
import dev.tserato.PvPSystem.Queue.PlayerQueue;
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
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

import static dev.tserato.PvPSystem.MMR.MMR.getRankForMMR;

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

        new BukkitRunnable() {
            @Override
            public void run() {
                checkAndDeleteEmptyArenas();
            }
        }.runTaskTimerAsynchronously(this, 0L, 20L * 60);

        Database.setupDatabase();

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) { //
            new PAPIExpansion(this).register(); //
        }

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
                                    addToQueueAndSearch(player);
                                }
                                return Command.SINGLE_SUCCESS;
                            })
                            .build(),
                    "Join the ranked queue",
                    List.of("lr")
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
                                player.getInventory().clear();
                                return Command.SINGLE_SUCCESS;
                            })
                            .build(),
                    "Return to the lobby",
                    List.of("l", "leave")
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
        String oldWinnerRank = getRankForMMR(winnerMMR);
        String newWinnerRank = getRankForMMR(newWinnerMMR);
        String oldLoserRank = getRankForMMR(loserMMR);
        String newLoserRank = getRankForMMR(newLoserMMR);

        // Get colors for the new ranks
        NamedTextColor winnerColor = MMR.getColorForRank(newWinnerRank);
        NamedTextColor loserColor = MMR.getColorForRank(newLoserRank);

        // Update MMR and ranks in the database
        Database.setMMR(winner.getUniqueId(), newWinnerMMR);
        Database.setMMR(loser.getUniqueId(), newLoserMMR);

        // Check if the winner or loser has ranked up or down
        boolean winnerRankedUp = !oldWinnerRank.equals(newWinnerRank) && !oldWinnerRank.split(" ")[0].equals(newWinnerRank.split(" ")[0]);
        boolean loserRankedDown = !oldLoserRank.equals(newLoserRank) && !oldLoserRank.split(" ")[0].equals(newLoserRank.split(" ")[0]);

        // Display rank change titles with conditional messages
        if (winnerRankedUp) {
            winner.showTitle(
                    Title.title(
                            Component.text(newWinnerRank).color(winnerColor),
                            Component.text("You ranked up!").color(NamedTextColor.GREEN)
                    )
            );
        } else {
            winner.showTitle(
                    Title.title(
                            Component.text(MMR.rankChangeTitle(oldWinnerRank, newWinnerRank)).color(winnerColor),
                            Component.text("You won.").color(NamedTextColor.GREEN)
                    )
            );
        }

        if (loserRankedDown) {
            loser.showTitle(
                    Title.title(
                            Component.text(newLoserRank).color(loserColor),
                            Component.text("You ranked down!").color(NamedTextColor.RED)
                    )
            );
        } else {
            loser.showTitle(
                    Title.title(
                            Component.text(MMR.rankChangeTitle(oldLoserRank, newLoserRank)).color(loserColor),
                            Component.text("You lost.").color(NamedTextColor.RED)
                    )
            );
        }
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
        System.out.println("Executed: kit give diamondpot " + playerName);

        player.setHealth(20);

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

        // If player was in an arena, handle match logic
        World arenaWorld = playerArenaMap.get(player.getUniqueId());
        if (arenaWorld != null && arenaWorld.getName().startsWith("arena_")) {
            // Determine the remaining player in the arena
            Player winner = null;
            for (Player p : arenaWorld.getPlayers()) {
                if (!p.equals(player) && p.isOnline()) {
                    winner = p; // The remaining player becomes the winner
                    break;
                }
            }

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
                        if (finalWinner.isOnline()) {
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
        }
        playerArenaMap.remove(player.getUniqueId());
    }

    private void checkAndDeleteArenaWorld(World world) {
        // Check if the world name starts with "arena_"
        if (world.getName().startsWith("arena_")) {
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
        if (event.getLocation().getWorld().getName().startsWith("arena_")) {
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

                winner.setHealth(20);
                player.setHealth(20);

                winner.getInventory().clear();
                player.getInventory().clear();

                spectatingPlayers.put(player.getUniqueId(), true);
                spectatingPlayers.put(winner.getUniqueId(), true);

                clearAllPotionEffects(winner);
                clearAllPotionEffects(player);

                clearEntitiesExceptPlayers(arenaWorld);

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
                            winner.getInventory().clear();
                            player.getInventory().clear();
                        }
                        deleteArenaWorld(arenaWorld);
                    }
                }.runTaskLater(this, 20L * 15);

                // Handle MMR adjustments after match
                handleMMRAfterMatch(winner, player);
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

    @Override
    public void onDisable() {
        getLogger().info("Shutting down PvPSystem");
    }
}
