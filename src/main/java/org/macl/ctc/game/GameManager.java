package org.macl.ctc.game;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.KeyedBossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;
import org.macl.ctc.Main;
import org.macl.ctc.kits.Kit;
import org.macl.ctc.kits.Spy;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;

public class GameManager {

    private ArrayList<UUID> stack = new ArrayList<>();

    public boolean started = false;
    public boolean starting = false;
    private BukkitTask lobby;
    private BukkitTask gameTimer; // Game time limit timer

    private Main main;
    private WorldManager world;
    private KitManager kit;

    public int center = 0;
    public int timeRemaining = 3600; // 1 hour in seconds (60 * 60)

    public int redCoreHealth = 3; // Number of times the red core needs to be mined
    public int blueCoreHealth = 3; // Number of times the blue core needs to be mined

    private Scoreboard gameScoreboard; // New scoreboard for each game
    private Team red;
    private Team blue;

    // Practice servers never call start(), so gameScoreboard stays null forever there.
    // This is a separate, longer-lived scoreboard so practice players still get balanced
    // red/blue teams (colors, no friendly fire, stats-friendly teammate checks) without
    // touching any of the "is a real match running" checks that key off gameScoreboard.
    private Scoreboard practiceScoreboard;

    private final String serverName;

    // Practice maps often don't have a border baked in (or inherit a huge default one),
    // which used to scatter practice spawns across a mostly-empty world. Keep the
    // practice arena small and contained instead.
    private static final double PRACTICE_BORDER_SIZE = 100.0;

    public GameManager(Main main) {
        this.main = main;
        this.world = main.worldManager;
        this.kit = main.kit;
        String envName = System.getenv("SERVER_NAME");
        this.serverName = (envName == null || envName.isEmpty()) ? "unknown" : envName.toLowerCase();
        clean();
        startStatusReporter();
    }

    /**
     * Periodically pushes live match state (phase, clock, core health, center control)
     * to the shared database so the lobby can show real status instead of guessing off player count.
     */
    private void startStatusReporter() {
        Bukkit.getScheduler().runTaskTimer(main, () -> reportStatus(null), 20L, 60L);
    }

    private void reportStatus(String phaseOverride) {
        String phase = phaseOverride != null ? phaseOverride
                : started ? "IN_PROGRESS"
                : starting ? "STARTING"
                : "WAITING";

        int redCount = getReds().size();
        int blueCount = getBlues().size();

        Bukkit.getScheduler().runTaskAsynchronously(main, () ->
                main.getDatabase().updateServerStatus(
                        serverName, phase, timeRemaining, redCoreHealth, blueCoreHealth,
                        center, redCount, blueCount
                )
        );
    }

    public void addTeam(Player p) {
        boolean practice = gameScoreboard == null;
        if (practice && practiceScoreboard == null) {
            // First player to ever need a team on this server - stand up the practice
            // scoreboard lazily (real matches build gameScoreboard the same way in start()).
            practiceScoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            register(practiceScoreboard);
        }

        String name = p.getName();
        int redSize = getRed().getSize();
        int blueSize = getBlue().getSize();

        Bukkit.getLogger().info("Adding player " + name + " to a team. Red team size: " + redSize + ", Blue team size: " + blueSize);

        if (redSize > blueSize) {
            getBlue().addEntry(name);
            Bukkit.getLogger().info("Player " + name + " added to Blue team.");
        } else {
            getRed().addEntry(name);
            Bukkit.getLogger().info("Player " + name + " added to Red team.");
        }

        if (practice) {
            giveLeaveItem(p);
            giveKitClock(p);
            main.send(p, "Use /ctc kit to select your kit!", ChatColor.AQUA);
        } else {
            main.getStats().recordGamePlayed(p);
        }
        setup(p);
    }
    // In GameManager.java

    /**
     * Puts a player on the Red team (removing them from Blue if present).
     */
    public void joinRed(Player p) {
        // remove from Blue if they were there
        Team blue = gameScoreboard.getTeam(BLUE_TEAM_NAME);
        if (blue != null) blue.removeEntry(p.getName());

        // add to Red
        Team red = gameScoreboard.getTeam(RED_TEAM_NAME);
        if (red != null) {
            red.addEntry(p.getName());
            // make sure they see the right scoreboard
            p.setScoreboard(gameScoreboard);
        }
    }

    /**
     * Puts a player on the Blue team (removing them from Red if present).
     */
    public void joinBlue(Player p) {
        // remove from Red if they were there
        Team red = gameScoreboard.getTeam(RED_TEAM_NAME);
        if (red != null) red.removeEntry(p.getName());

        // add to Blue
        Team blue = gameScoreboard.getTeam(BLUE_TEAM_NAME);
        if (blue != null) {
            blue.addEntry(p.getName());
            // make sure they see the right scoreboard
            p.setScoreboard(gameScoreboard);
        }
    }


    public void start() {
        started = true;
        starting = false;
        Collections.shuffle(stack);

        // Initialize a new scoreboard for the game
        gameScoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        register(); // Register teams on the game scoreboard

        // Assign the game scoreboard to all players
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(gameScoreboard);
        }

        // Add each shuffled player to a team
        for (Iterator<UUID> iterator = stack.iterator(); iterator.hasNext();) {
            UUID uuid = iterator.next();
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) {
                iterator.remove();
                continue;
            }
            addTeam(p);
        }

        main.broadcast("The game has begun! Destroy the other team's core to win!");
        stack.clear();

        // Initialize the scoreboard
        updateScoreboard(0, 0);
        center = 0;

        // Start the game timer (1 hour)
        timeRemaining = 3600; // Reset to 1 hour
        startGameTimer();
    }

    /**
     * Starts the 1-hour game timer that counts down every second
     */
    private void startGameTimer() {
        gameTimer = new BukkitRunnable() {
            @Override
            public void run() {
                if (!started) {
                    this.cancel();
                    return;
                }

                // Only decrement timer if there are 2+ players online
                int playerCount = Bukkit.getOnlinePlayers().size();
                if (playerCount >= 2) {
                    timeRemaining--;
                }

                // Update scoreboard every second
                int redCount = 0, blueCount = 0;
                for (Location loc : world.getCenter()) {
                    Block b = loc.getWorld().getBlockAt(loc);
                    if      (b.getType() == Material.RED_WOOL)  redCount++;
                    else if (b.getType() == Material.BLUE_WOOL) blueCount++;
                }
                updateScoreboard(redCount, blueCount);

                // Warnings at specific time marks (only if timer is counting)
                if (playerCount >= 2) {
                    if (timeRemaining == 600) { // 10 minutes left
                        main.broadcast(ChatColor.YELLOW + "⏰ 10 minutes remaining!");
                    } else if (timeRemaining == 300) { // 5 minutes left
                        main.broadcast(ChatColor.GOLD + "⏰ 5 minutes remaining!");
                    } else if (timeRemaining == 60) { // 1 minute left
                        main.broadcast(ChatColor.RED + "⏰ 1 MINUTE REMAINING!");
                    } else if (timeRemaining == 30 || timeRemaining == 10) {
                        main.broadcast(ChatColor.RED + "⏰ " + timeRemaining + " seconds remaining!");
                    } else if (timeRemaining <= 5 && timeRemaining > 0) {
                        main.broadcast(ChatColor.DARK_RED + "⏰ " + timeRemaining + "...");
                    }

                    // Time's up - end game
                    if (timeRemaining <= 0) {
                        this.cancel();
                        timeUp();
                    }
                }
            }
        }.runTaskTimer(main, 20L, 20L); // Run every second
    }

    /**
     * Called when the 1-hour time limit expires
     */
    private void timeUp() {
        main.broadcast(ChatColor.GOLD + "=========================", ChatColor.GOLD);
        main.broadcast(ChatColor.DARK_RED + "⏰ TIME'S UP!");
        main.broadcast(ChatColor.GOLD + "=========================", ChatColor.GOLD);

        // Determine winner based on core health
        Player winner = null;
        if (redCoreHealth > blueCoreHealth) {
            main.broadcast(ChatColor.RED + "Red team wins with more core health!", ChatColor.RED);
            // Pick a random red player to credit the win
            if (!getReds().isEmpty()) {
                winner = getReds().get(0);
            }
        } else if (blueCoreHealth > redCoreHealth) {
            main.broadcast(ChatColor.BLUE + "Blue team wins with more core health!", ChatColor.BLUE);
            // Pick a random blue player to credit the win
            if (!getBlues().isEmpty()) {
                winner = getBlues().get(0);
            }
        } else {
            main.broadcast(ChatColor.GRAY + "It's a tie! Both teams have equal core health!", ChatColor.GRAY);
            main.broadcast(ChatColor.GRAY + "No winner this round.", ChatColor.GRAY);
        }

        // End the game
        if (winner != null) {
            stop(winner);
        } else {
            // Tie - just restart without winner
            started = false;
            starting = false;
            reportStatus("RESTARTING");
            broadcastLiveGameAwards();

            // Save stats
            for (Player p : Bukkit.getOnlinePlayers()) {
                main.getStats().savePlayer(p.getUniqueId());
            }

            // Restart server
            new BukkitRunnable() {
                @Override public void run() {
                    main.broadcast(ChatColor.GOLD + "=========================", ChatColor.GOLD);
                    main.broadcast("Server restarting for next game...", ChatColor.YELLOW);
                    main.broadcast(ChatColor.GOLD + "=========================", ChatColor.GOLD);
                }
            }.runTaskLater(main, 60L);

            new BukkitRunnable() {
                @Override
                public void run() {
                    // Update database to show server is restarting
                    String serverName = System.getenv("SERVER_NAME");
                    if (serverName == null || serverName.isEmpty()) {
                        serverName = "unknown";
                    }
                    main.getDatabase().updateServerMap(serverName, "Restarting...");

                    Bukkit.shutdown();
                }
            }.runTaskLater(main, 100L);
        }
    }

    private void setup(Player p) {
        // setScoreboard(null) throws, so only assign if a match or practice scoreboard exists.
        Scoreboard board = activeScoreboard();
        if (board != null) {
            p.setScoreboard(board);
        }
        teleportSpawn(p);
        AttributeInstance attribute = p.getAttribute(Attribute.MAX_HEALTH);
        attribute.setBaseValue(20.0);
        p.setHealth(20);
        for (PotionEffect g : p.getActivePotionEffects())
            p.removePotionEffect(g.getType());
        new BukkitRunnable() {
            @Override
            public void run() {
                kit.openMenu(p);
            }
        }.runTaskLater(main, 3L);
    }

    public void clearPlayerDisplays() {
        // Clear the action bar for all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (Iterator<KeyedBossBar> it = Bukkit.getBossBars(); it.hasNext(); ) {
                BossBar bossBar = it.next();
                bossBar.removePlayer(player);
            }
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));

            // Clear the sidebar display
            Scoreboard scoreboard = player.getScoreboard();
            if (scoreboard != null) {
                Objective sidebarObjective = scoreboard.getObjective(DisplaySlot.SIDEBAR);
                if (sidebarObjective != null) {
                    sidebarObjective.unregister(); // Unregister the sidebar objective
                }
            }

            // Do not override the game scoreboard
            // Optionally reset player's scoreboard to main scoreboard after the game ends
            if (!started) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
    }
    private void broadcastLiveGameAwards() {
        // 1) Grab all online players
        List<Player> alive = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (alive.isEmpty()) return;

        StatsManager sm = main.getStats();

        // 2) Utility to find everyone tied for the top of a stat
        BiFunction<ToIntFunction<Player>, String, Void> award = (statFn, label) -> {
            int best = alive.stream()
                    .mapToInt(statFn)
                    .max()
                    .orElse(0);

            List<String> names = alive.stream()
                    .filter(p -> statFn.applyAsInt(p) == best)
                    .map(Player::getName)
                    .toList();

            String joined = String.join(", ", names);
            main.broadcast(
                    label
                            + ChatColor.WHITE + joined
                            + ChatColor.GRAY + " (" + best + ")",
                    ChatColor.GOLD
            );
            return null;
        };

        // 3) Broadcast with your colors & labels
        main.broadcast(ChatColor.GOLD + "=== Live Game Awards ===");

        award.apply(
                p -> sm.getSessionStats(p.getUniqueId()).kills(),
                ChatColor.RED +   "⫸ Offensive MVP(s): "
        );

        award.apply(
                p -> sm.getSessionStats(p.getUniqueId()).captures(),
                ChatColor.AQUA +  "⫸ Defense MVP(s): "
        );

        award.apply(
                p -> sm.getSessionStats(p.getUniqueId()).coreCracks(),
                ChatColor.DARK_PURPLE + "⫸ Core MVP(s): "
        );

        award.apply(
                p -> sm.getSessionStats(p.getUniqueId()).deaths(),
                ChatColor.DARK_RED + "⫸ LVP(s): "
        );

        main.broadcast(ChatColor.GOLD + "=========================");
    }



    public void stop(Player stopper) {
        started  = false;
        starting = false;
        reportStatus("RESTARTING");

        // Cancel game timer
        if (gameTimer != null) {
            gameTimer.cancel();
            gameTimer = null;
        }

        boolean redWin = redHas(stopper);
        Collection<Player> winners = redWin ? getReds() : getBlues();
        for (Player p : winners) {
            main.getStats().recordWin(p);
        }

        // Save all player stats to DB after game ends (before server restart)
        main.getLogger().info("Game ended - saving all player stats to database...");
        for (Player p : Bukkit.getOnlinePlayers()) {
            main.getStats().savePlayer(p.getUniqueId());
        }

        broadcastLiveGameAwards();

        // Notify players about server restart
        new BukkitRunnable() {
            @Override public void run() {
                main.broadcast(ChatColor.GOLD + "=========================", ChatColor.GOLD);
                main.broadcast("Server restarting for next game...", ChatColor.YELLOW);
                main.broadcast(ChatColor.GOLD + "=========================", ChatColor.GOLD);
            }
        }.runTaskLater(main, 60L);

        // Shutdown cleanly; container restart policy will bring it back fresh
        new BukkitRunnable() {
            @Override
            public void run() {
                main.getLogger().info("Game ended - exiting JVM for container restart.");

                // Update database to show server is restarting
                String serverName = System.getenv("SERVER_NAME");
                if (serverName == null || serverName.isEmpty()) {
                    serverName = "unknown";
                }
                main.getDatabase().updateServerMap(serverName, "Restarting...");

                // System.exit ensures Docker sees the process end and restarts the container
                Bukkit.shutdown();
            }
        }.runTaskLater(main, 100L); // 5 seconds after game end
    }


    public void stack(Player p) {
        p.teleport(p.getWorld().getSpawnLocation());
        if (started) {
            // IMPORTANT: Set the player's scoreboard to the game scoreboard BEFORE adding to team
            if (gameScoreboard != null) {
                p.setScoreboard(gameScoreboard);
            }
            addTeam(p);
            return;
        }
        if (stack.contains(p.getUniqueId())) {
            main.send(p, "You have been removed from the stack");
            stack.remove(p.getUniqueId());
            if (stack.size() < 2 && lobby != null) {
                starting = false;
                lobby.cancel();
                main.broadcast("The game cannot begin until another player joins the stack!");
            }
        } else {
            stack.add(p.getUniqueId());
            if (stack.size() >= 2 && !starting && !started) {
                starting = true;
                lobby = new LobbyTimer(10).runTaskTimer(main, 0L, 20L);
            }
            main.send(p, "You have been added to the stack");
        }
    }

    public Player getRandomTeammate(Player p) {
        List<Player> teammates = new ArrayList<>();

        // Check if player is on red or blue team
        boolean isPlayerRed = main.game.redHas(p);
        boolean isPlayerBlue = main.game.blueHas(p);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online != p && online.isOnline() &&
                    !online.getGameMode().equals(GameMode.SPECTATOR)) {

                // Add teammate if they're on the same team
                if ((isPlayerRed && main.game.redHas(online)) ||
                        (isPlayerBlue && main.game.blueHas(online))) {
                    teammates.add(online);
                }
            }
        }

        if (!teammates.isEmpty()) {
            return teammates.get(new Random().nextInt(teammates.size()));
        }

        return null;
    }
    public boolean sameTeam(UUID uuid1, UUID uuid2) {
        Player p1 = Bukkit.getPlayer(uuid1);
        Player p2 = Bukkit.getPlayer(uuid2);

        // if either player isn’t online or doesn’t exist, they can’t be on the same team
        if (p1 == null || p2 == null) {
            return false;
        }

        if (getRed() == null && getBlue() == null) {
            // No match or practice teams active at all - fall back to manually-set /team
            // entries on the main scoreboard so friendly-fire logic can still be tested
            // standalone (e.g. on a bare dev server).
            Scoreboard score = Bukkit.getScoreboardManager().getMainScoreboard();
            String p1Team = "1";
            String p2Team = "2";

            for (Team team : score.getTeams()) {
                if (team.hasEntry(p1.getName())) {
                    p1Team = team.getName();
                }
                if (team.hasEntry(p2.getName())) {
                    p2Team = team.getName();
                }
            }

            return p1Team.equals(p2Team);
        }

        // both on red?
        boolean bothRed = getRed() != null
                && getRed().hasEntry(p1.getName())
                && getRed().hasEntry(p2.getName());

        // both on blue?
        boolean bothBlue = getBlue() != null
                && getBlue().hasEntry(p1.getName())
                && getBlue().hasEntry(p2.getName());

        return bothRed || bothBlue;
    }


    public class LobbyTimer extends BukkitRunnable {
        int count = 60;

        public LobbyTimer(int count) {
            this.count = count;
        }

        public void run() {
            if (count % 5 == 0 || (count <= 5 && count != 0)) {
                main.broadcast("The game will begin in " + count + " seconds!");
            }
            if (count == 0) {
                this.cancel();
                start();
                stack.clear();
            }
            count--;
        }
    }

    public String centerString(int red, int blue) {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < red; i++)
            builder.append(ChatColor.RED).append(ChatColor.BOLD).append("█").append(ChatColor.WHITE);
        for (int i = 0; i < blue; i++)
            builder.append(ChatColor.BLUE).append(ChatColor.BOLD).append("█").append(ChatColor.WHITE);
        for (int i = red + blue; i < 9; i++)
            builder.append(ChatColor.DARK_GRAY).append(ChatColor.BOLD).append("█").append(ChatColor.WHITE);

        return builder.toString();
    }

    public void resetCenter(@Nullable Player capturer) {
        if (!started) return;

        Bukkit.getScheduler().runTaskLater(main, () -> {
            int redCount = 0, blueCount = 0;
            for (Location loc : world.getCenter()) {
                Block b = loc.getWorld().getBlockAt(loc);
                if      (b.getType() == Material.RED_WOOL)  redCount++;
                else if (b.getType() == Material.BLUE_WOOL) blueCount++;
            }

            updateScoreboard(redCount, blueCount);

            // did we lose control?
            if (center != 0 && redCount < 5 && blueCount < 5) {
                main.broadcast("The center has been reset!");
                center = 0;
            }

            // red capture event
            if (redCount >= 5 && center != 1) {
                main.broadcast("Red has captured the center!", ChatColor.RED);
                center = 1;

                // only record if someone explicitly triggered it
                if (capturer != null) {
                    main.getStats().recordCapture(capturer.getUniqueId());
                    StatsManager.PlayerStats ps = main.getStats().get(capturer.getUniqueId());
                    capturer.sendMessage(ChatColor.RED +
                            "You captured the center! Total captures: " + ps.captures());
                }
            }

            // blue capture event
            if (blueCount >= 5 && center != 2) {
                main.broadcast("Blue has captured the center!", ChatColor.BLUE);
                center = 2;

                if (capturer != null) {
                    main.getStats().recordCapture(capturer.getUniqueId());
                    StatsManager.PlayerStats ps = main.getStats().get(capturer.getUniqueId());
                    capturer.sendMessage(ChatColor.BLUE +
                            "You captured the center! Total captures: " + ps.captures());
                }
            }

            // give or remove pickaxes as before
            switch (center) {
                case 0 -> {
                    getReds().forEach(p -> p.getInventory().remove(Material.DIAMOND_PICKAXE));
                    getBlues().forEach(p -> p.getInventory().remove(Material.DIAMOND_PICKAXE));
                }
                case 1 -> {
                    getBlues().forEach(p -> p.getInventory().remove(Material.DIAMOND_PICKAXE));
                    getReds().forEach(p -> {
                        if (!(main.getKits().get(p.getUniqueId()) instanceof Spy))
                            p.getInventory().setItem(8, main.coreCrush());
                    });
                }
                case 2 -> {
                    getReds().forEach(p -> p.getInventory().remove(Material.DIAMOND_PICKAXE));
                    getBlues().forEach(p -> {
                        if (!(main.getKits().get(p.getUniqueId()) instanceof Spy))
                            p.getInventory().setItem(8, main.coreCrush());
                    });
                }
            }
        }, 2L);
    }


    // ******************************//
    //         TEAM MANAGER          //
    // ******************************//

    public boolean resetPlayer(Player p, boolean quit) {
        // Super reset the player as if they never joined the server
        // 1. Clear Inventory and Armor
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);

        // 2. Reset Health and Food
        AttributeInstance attribute = p.getAttribute(Attribute.MAX_HEALTH);
        attribute.setBaseValue(20.0);
        p.setHealth(attribute.getDefaultValue());
        p.setFoodLevel(20);
        p.setSaturation(0);

        // 3. Reset Experience and Level
        p.setTotalExperience(0);
        p.setExp(0);
        p.setLevel(0);

        // 4. Remove Potion Effects
        for (PotionEffect effect : p.getActivePotionEffects())
            p.removePotionEffect(effect.getType());

        // 5. Reset Fire Ticks and Other States
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.setRemainingAir(p.getMaximumAir());

        // 6. Reset Ender Chest
        p.getEnderChest().clear();

        // 7. Remove from Teams
        boolean wasOnTeam = false;
        if (redHas(p)) {
            getRed().removeEntry(p.getName());
            wasOnTeam = true;
        }
        if (blueHas(p)) {
            getBlue().removeEntry(p.getName());
            wasOnTeam = true;
        }

        // 8. Reset Player Location to Default World Spawn
        World defaultWorld = Bukkit.getWorld("world");
        if (defaultWorld == null) {
            defaultWorld = Bukkit.createWorld(new WorldCreator("world"));
        }
        p.teleport(defaultWorld.getSpawnLocation());

        // 9. Reset Kit Data
        Kit playerKit = kit.kits.remove(p.getUniqueId());
        if (playerKit != null) {
            playerKit.cancelAllCooldowns();
            playerKit.cancelAllRegen();
            playerKit.cancelAllTasks();
        }

        // 10. Clear Player Display (Action Bar, Boss Bars, etc.)
        clearPlayerDisplaysForPlayer(p);

        // 11. Reset Player Scoreboard to Main Scoreboard
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());

        // 12. Send Leave Message if Quitting
        if (quit) {
            main.broadcast(p.getName() + " has left the game!");
        }

        return wasOnTeam;
    }

    private void clearPlayerDisplaysForPlayer(Player player) {
        // Remove Boss Bars
        for (Iterator<KeyedBossBar> it = Bukkit.getBossBars(); it.hasNext(); ) {
            BossBar bossBar = it.next();
            bossBar.removePlayer(player);
        }
        // Clear Action Bar
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));

        // Clear Sidebar Display
        Scoreboard scoreboard = player.getScoreboard();
        if (scoreboard != null) {
            Objective sidebarObjective = scoreboard.getObjective(DisplaySlot.SIDEBAR);
            if (sidebarObjective != null) {
                sidebarObjective.unregister(); // Unregister the sidebar objective
            }
        }
    }

    public ArrayList<Player> getReds() {
        ArrayList<Player> reds = new ArrayList<>();
        if (getRed() != null) {
            for (String s : getRed().getEntries())
                if (Bukkit.getPlayer(s) != null)
                    reds.add(Bukkit.getPlayer(s));
        }
        return reds;
    }

    public ArrayList<Player> getBlues() {
        ArrayList<Player> blues = new ArrayList<>();
        if (getBlue() != null) {
            for (String s : getBlue().getEntries())
                if (Bukkit.getPlayer(s) != null)
                    blues.add(Bukkit.getPlayer(s));
        }
        return blues;
    }

    public boolean redHas(Player p) {
        if (getRed() == null && getBlue() == null) {
            // No match or practice teams active - fall back to manually-set /team entries
            // on the main scoreboard (bare dev/testing server).
            Scoreboard score = Bukkit.getScoreboardManager().getMainScoreboard();

            for (Team team : score.getTeams()) {
                if (team.hasEntry(p.getName())) {
                    if (team.getName().equalsIgnoreCase("red")) return true;
                }
            }

            return false;
        }

        return getRed() != null && getRed().hasEntry(p.getName());
    }

    public boolean blueHas(Player p) {
        if (getRed() == null && getBlue() == null) {
            Scoreboard score = Bukkit.getScoreboardManager().getMainScoreboard();

            for (Team team : score.getTeams()) {
                if (team.hasEntry(p.getName())) {
                    if (team.getName().equalsIgnoreCase("blue")) return true;
                }
            }

            return false;
        }

        return getBlue() != null && getBlue().hasEntry(p.getName());
    }

    // Whichever scoreboard is actually holding teams right now - the real match's if one is
    // running, otherwise practice's. Never both: a server only ever has one or the other.
    private Scoreboard activeScoreboard() {
        return gameScoreboard != null ? gameScoreboard : practiceScoreboard;
    }

    public Team getRed() {
        Scoreboard board = activeScoreboard();
        return board != null ? board.getTeam(RED_TEAM_NAME) : null;
    }

    public Team getBlue() {
        Scoreboard board = activeScoreboard();
        return board != null ? board.getTeam(BLUE_TEAM_NAME) : null;
    }

    public void clean() {
        // Unregister teams and objectives from the gameScoreboard
        if (gameScoreboard != null) {
            for (Team t : gameScoreboard.getTeams())
                t.unregister();

            for (Objective obj : gameScoreboard.getObjectives())
                obj.unregister();
        }

        red = null;
        blue = null;

        // Reset the game scoreboard
        gameScoreboard = null;

        for (Player p : Bukkit.getOnlinePlayers()) {
            resetPlayer(p, false);
            // Reset the player's scoreboard to main scoreboard
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        clearPlayerDisplays();
    }

    public static final String RED_TEAM_NAME = "red";
    public static final String BLUE_TEAM_NAME = "blue";

    public void register() {
        register(gameScoreboard);
    }

    public void register(Scoreboard board) {
        red = board.getTeam(RED_TEAM_NAME);
        blue = board.getTeam(BLUE_TEAM_NAME);

        // Register teams if they do not already exist
        if (red == null) {
            red = board.registerNewTeam(RED_TEAM_NAME);
        }
        if (blue == null) {
            blue = board.registerNewTeam(BLUE_TEAM_NAME);
        }

        // Set properties for the red team
        if (red != null) {
            red.setColor(ChatColor.RED);
            red.setPrefix(ChatColor.RED.toString());
            red.setDisplayName(ChatColor.RED + "Red");
            red.setCanSeeFriendlyInvisibles(true);
            red.setAllowFriendlyFire(false);
        } else {
            Bukkit.getLogger().warning("Failed to register or retrieve the red team.");
        }

        // Set properties for the blue team
        if (blue != null) {
            blue.setColor(ChatColor.BLUE);
            blue.setPrefix(ChatColor.BLUE.toString());
            blue.setDisplayName(ChatColor.BLUE + "Blue");
            blue.setCanSeeFriendlyInvisibles(true);
            blue.setAllowFriendlyFire(false);
        } else {
            Bukkit.getLogger().warning("Failed to register or retrieve the blue team.");
        }
    }

    public void teleportSpawn(Player p) {
        if (!started) {
            // Practice mode: players are on a balanced red/blue team (see addTeam) for
            // colors/friendly-fire/stats, but there's no real match with fixed bases here -
            // everyone scatters within the (small) practice arena instead.
            p.teleport(randomMapSpawn());
            return;
        }
        if (redHas(p)) {
            p.teleport(world.getRed());
        } else if (blueHas(p)) {
            p.teleport(world.getBlue());
        } else {
            p.teleport(randomMapSpawn());
        }
    }

    /**
     * Picks a random, safe (on-top-of-terrain) location within the "map" world's current
     * world border. Used for free-for-all spawns/respawns where there's no fixed team spawn.
     * On the practice server, the border is first clamped down to PRACTICE_BORDER_SIZE so
     * spawns stay clustered even on maps that bake in a huge (or no) border.
     */
    public Location randomMapSpawn() {
        World mapWorld = Bukkit.getWorld("map");
        if (mapWorld == null) {
            mapWorld = Bukkit.createWorld(new WorldCreator("map"));
        }

        WorldBorder border = mapWorld.getWorldBorder();
        if (serverName.equalsIgnoreCase("practice") && border.getSize() > PRACTICE_BORDER_SIZE) {
            border.setCenter(border.getCenter());
            border.setSize(PRACTICE_BORDER_SIZE);
        }

        Location center = border.getCenter();
        double halfSize = border.getSize() / 2.0;
        double range = Math.max(halfSize - Math.min(halfSize * 0.15, 10), 1);

        Random rand = new Random();
        double x = center.getX() + (rand.nextDouble() * 2 - 1) * range;
        double z = center.getZ() + (rand.nextDouble() * 2 - 1) * range;
        int y = mapWorld.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1;

        return new Location(mapWorld, x, y, z);
    }

    public static final String LEAVE_ITEM_KEY = "leave_item";

    /**
     * Gives the practice-mode "LEAVE" clock in the last hotbar slot. Tagged via persistent
     * data (not just Material.CLOCK) because Runner's kit already binds a right-click ability
     * to a plain clock - tagging lets Interact's handler tell the two apart regardless of
     * which kit the player has equipped.
     */
    public void giveLeaveItem(Player p) {
        ItemStack clock = new ItemStack(Material.CLOCK);
        ItemMeta meta = clock.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "LEAVE");
        meta.setLore(List.of(ChatColor.GRAY + "Right-click to return to the lobby"));
        meta.getPersistentDataContainer().set(
                new NamespacedKey(main, LEAVE_ITEM_KEY), PersistentDataType.BYTE, (byte) 1);
        clock.setItemMeta(meta);
        p.getInventory().setItem(8, clock);
    }

    public boolean isLeaveItem(ItemStack item) {
        if (item == null || item.getType() != Material.CLOCK || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(new NamespacedKey(main, LEAVE_ITEM_KEY), PersistentDataType.BYTE);
    }

    public static final String KIT_ITEM_KEY = "kit_item";

    /**
     * Gives the practice-mode "SELECT KIT" clock (one slot left of LEAVE). Right-clicking it
     * just calls kit.openMenu(p), the same thing /ctc kit does - saves practice players from
     * having to type the command every time they want to switch kits.
     */
    public void giveKitClock(Player p) {
        ItemStack clock = new ItemStack(Material.CLOCK);
        ItemMeta meta = clock.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "SELECT KIT");
        meta.setLore(List.of(ChatColor.GRAY + "Right-click to open the kit menu"));
        meta.getPersistentDataContainer().set(
                new NamespacedKey(main, KIT_ITEM_KEY), PersistentDataType.BYTE, (byte) 1);
        clock.setItemMeta(meta);
        p.getInventory().setItem(7, clock);
    }

    public boolean isKitItem(ItemStack item) {
        if (item == null || item.getType() != Material.CLOCK || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(new NamespacedKey(main, KIT_ITEM_KEY), PersistentDataType.BYTE);
    }

    public void sendToLobby(Player p) {
        connectToServer(p, "lobby");
    }

    public void connectToServer(Player p, String serverName) {
        com.google.common.io.ByteArrayDataOutput out = com.google.common.io.ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(serverName);
        p.sendPluginMessage(main, "BungeeCord", out.toByteArray());
    }

    /**
     * Updates the scoreboard with the current center status and core health.
     *
     * @param redCount  Number of center blocks captured by red team
     * @param blueCount Number of center blocks captured by blue team
     */
    public void updateScoreboard(int redCount, int blueCount) {
        if (gameScoreboard == null) {
            return;
        }

        Objective objective = gameScoreboard.getObjective("GameInfo");
        if (objective == null) {
            objective = gameScoreboard.registerNewObjective("GameInfo", "dummy", ChatColor.GOLD + "" + ChatColor.BOLD + "Game Status");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        // Clear existing scores
        for (String entry : gameScoreboard.getEntries()) {
            gameScoreboard.resetScores(entry);
        }

        // Add core health display with labels "Core Health", "Blue:", and "Red:"
        String coreHealthHeader = ChatColor.GOLD + "Core Health";
        String blueCore = ChatColor.BLUE + "Blue: " + heartString(blueCoreHealth);
        String redCore = ChatColor.RED + "Red: " + heartString(redCoreHealth);

        // Add center status display
        String centerStatus = ChatColor.GREEN + "Center Control:";
        String centerControl = centerString(redCount, blueCount);

        // Add time remaining display
        String timeDisplay = ChatColor.YELLOW + "Time: " + formatTime(timeRemaining);

        // Set scores: center control, core health, time, then IP
        objective.getScore(" ").setScore(9); // Blank line for spacing
        objective.getScore(centerStatus).setScore(8);
        objective.getScore(centerControl).setScore(7);
        objective.getScore("  ").setScore(6); // Blank line
        objective.getScore(coreHealthHeader).setScore(5);
        objective.getScore(blueCore).setScore(4);
        objective.getScore(redCore).setScore(3);
        objective.getScore("   ").setScore(2); // Blank line before IP
        objective.getScore(timeDisplay).setScore(1);
        objective.getScore(ChatColor.GOLD + "" + ChatColor.BOLD + "playctc.co").setScore(0); // Server IP
    }

    /**
     * Formats time in seconds to MM:SS format
     */
    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    // Override method that only updates the core health
    public void updateScoreboard() {
        if (gameScoreboard == null) {
            return;
        }

        Objective objective = gameScoreboard.getObjective("GameInfo");
        if (objective == null) {
            objective = gameScoreboard.registerNewObjective("GameInfo", "dummy", ChatColor.GOLD + "" + ChatColor.BOLD + "Game Status");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        // Clear only the core health related scores
        gameScoreboard.resetScores(ChatColor.BLUE + "Blue: " + heartString(blueCoreHealth));
        gameScoreboard.resetScores(ChatColor.RED + "Red: " + heartString(redCoreHealth));

        // Add core health display with labels "Core Health", "Blue:", and "Red:"
        String coreHealthHeader = ChatColor.GOLD + "Core Health";
        String blueCore = ChatColor.BLUE + "Blue: " + heartString(blueCoreHealth);
        String redCore = ChatColor.RED + "Red: " + heartString(redCoreHealth);

        // Update the scores for core health without modifying the center control
        objective.getScore("  ").setScore(5); // Ensure this blank line remains
        objective.getScore(coreHealthHeader).setScore(4);
        objective.getScore(blueCore).setScore(3);
        objective.getScore(redCore).setScore(2);
        objective.getScore("   ").setScore(1); // Blank line before IP
        objective.getScore(ChatColor.GOLD + "" + ChatColor.BOLD + "playctc.co").setScore(0); // Server IP
    }


    /**
     * Generates a string of hearts representing the core health.
     *
     * @param health The current health of the core
     * @return A string representing the core health with hearts
     */
    private String heartString(int health) {
        StringBuilder builder = new StringBuilder();
        int maxHealth = 3; // Maximum core health
        for (int i = 0; i < health; i++) {
            builder.append("❤ ");
        }
        for (int i = health; i < maxHealth; i++) {
            builder.append(ChatColor.DARK_GRAY).append("❤ ").append(ChatColor.RESET);
        }
        return builder.toString().trim();
    }
}
