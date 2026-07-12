package org.macl.ctc;

import com.maximde.hologramlib.HologramLib;
import com.maximde.hologramlib.hologram.HologramManager;
import com.maximde.hologramlib.hologram.TextHologram;
import com.maximde.hologramlib.hologram.custom.LeaderboardHologram;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.macl.ctc.combat.CombatTracker;
import org.macl.ctc.events.Blocks;
import org.macl.ctc.events.Interact;
import org.macl.ctc.events.Players;
import org.macl.ctc.game.*;
import org.macl.ctc.kits.Kit;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import net.md_5.bungee.api.chat.TextComponent;

public final class Main extends JavaPlugin implements CommandExecutor, Listener {


    public String map = "sandstone";

    public GameManager game;
    public WorldManager worldManager;
    public CombatTracker combatTracker;
    public KitManager kit;
    public StatsManager stats;
    private DatabaseManager db;
    private HologramManager hologramManager;
    private File statsFile;
    private File debugFile;// Store stats file path for Docker volume support
    public String prefix = ChatColor.GOLD + "[CTC] " + ChatColor.GRAY;

    public void send(Player p, String text, ChatColor color) {
        p.sendMessage(prefix + color + text);
    }

    public void send(Player p, String text) {
        p.sendMessage(prefix + text);
    }

    public void broadcast(String text) {
        Bukkit.broadcastMessage(prefix + text);
    }

    public void broadcast(String text, ChatColor color) {
        Bukkit.broadcastMessage(prefix + color + text);
    }

    public ArrayList<Listener> listens = new ArrayList<Listener>();
    public ArrayList<Material> restricted = new ArrayList<Material>();

    Players playerListener;


    @Override
    public void onEnable() {

        // Plugin startup logic
        this.getCommand("ctc").setExecutor(this);
        getLogger().info("Started!");

        HologramLib.getManager().ifPresentOrElse(
                manager -> hologramManager = manager,
                () -> getLogger().severe("Failed to initialize HologramLib manager.")
        );

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // 2) Initialize database connection pool
        db = new DatabaseManager(this);
        db.initialize();

        // 3) Initialize StatsManager with database
        this.stats = new StatsManager(db, this);


        kit = new KitManager(this);
        worldManager = new WorldManager(this);
        game = new GameManager(this);

        restricted.add(Material.OBSIDIAN);
        restricted.add(Material.NETHERITE_BLOCK);
        restricted.add(Material.LAPIS_ORE);
        restricted.add(Material.REDSTONE_ORE);
        restricted.add(Material.BEDROCK);
        restricted.add(Material.BARRIER);

        new Interact(this);
        new Blocks(this);
        playerListener = new Players(this);

        // Load map from config (set by Docker or manually in config.yml)
        String currentMap = getConfig().getString("current-map", "sandstone");
        map = currentMap;
        getLogger().info("Loading map: " + currentMap);
        worldManager.loadWorld("map", currentMap);

        // Update database with current server and map
        String serverName = System.getenv("SERVER_NAME");
        if (serverName == null || serverName.isEmpty()) {
            getLogger().warning("SERVER_NAME environment variable not set! Using 'unknown'");
            serverName = "unknown";
        }
        db.updateServerMap(serverName, currentMap);

        combatTracker = new CombatTracker(this);  // initialize after other managers if needed

        for (Listener i : listens)
            getServer().getPluginManager().registerEvents(i, this);

        registerEvents();
        getCommand("stats").setExecutor(new org.macl.ctc.commands.StatsCommand(this));

        String debugPath = System.getenv("CTC_DEBUG_FILE");

        if (debugPath != null && !debugPath.isEmpty()) {
            debugFile = new File(debugPath);
            getLogger().info("Using debug file from environment: " + debugPath);

            // Ensure parent directories exist
            File parentDir = debugFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (parentDir.mkdirs()) {
                    getLogger().info("Created debug directory: " + parentDir.getAbsolutePath());
                }
            }
        } else {
            // Fallback to plugin data folder
            debugFile = new File(getDataFolder(), "debug.yml");
            getLogger().info("Using default debug file: " + debugFile.getAbsolutePath());
        }

        // 3) Create debug.yml if it doesn't exist
        if (!debugFile.exists()) {
            try (PrintWriter out = new PrintWriter(debugFile)) {
                out.println("debug: 0");                // minimal valid YAML
                getLogger().info("Created new debug file");
            } catch (IOException e) {
                getLogger().severe("Could not create debug.yml: " + e.getMessage());
            }
        }

        // 4) Now safely load it, get copied, @macl
        FileConfiguration debugCfg = YamlConfiguration.loadConfiguration(debugFile);

        boolean startsGame = true;

        if (debugCfg.getInt("debug") > 0) {
            startsGame = false;
        }

        // auto start game
        if (startsGame) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    game.start();
                }
            }.runTaskLater(this, 100L);
        }

    }

    private void registerEvents() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        if (stats != null && db != null) {
            getLogger().info("Flushing all player stats to database...");

            // Save all cached stats
            List<java.util.concurrent.CompletableFuture<Void>> futures = new ArrayList<>();
            for (UUID uuid : stats.getAllCachedPlayers()) {
                futures.add(stats.savePlayer(uuid));
            }

            // Wait for all writes (max 10 seconds)
            try {
                java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]))
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
                getLogger().info("Saved " + futures.size() + " player stats");
            } catch (java.util.concurrent.TimeoutException e) {
                getLogger().severe("Timeout saving stats! Some data may be lost.");
            } catch (Exception e) {
                getLogger().severe("Error saving stats: " + e.getMessage());
            }

            // Shutdown database pool
            db.shutdown();
        }
        getLogger().info("Ended");
    }

    public CombatTracker getCombatTracker() {
        return combatTracker;
    }

    public void spawnHeadDebugBoard(Player p) {
        var hm = this.hologramManager;

        hm.getHologram("debug_heads").ifPresent(hm::remove);

        Location loc = p.getLocation().add(0, 2, 0);

        LeaderboardHologram.LeaderboardOptions options =
                LeaderboardHologram.LeaderboardOptions.builder()
                        .title("DEBUG HEADS")
                        .maxDisplayEntries(3)
                        .suffix("kills")
                        // IMPORTANT: TOP_PLAYER_HEAD to get a single head entity
                        .leaderboardType(LeaderboardHologram.LeaderboardType.TOP_PLAYER_HEAD)
                        .headMode(LeaderboardHologram.HeadMode.ITEM_DISPLAY)
                        .rotationMode(LeaderboardHologram.RotationMode.DYNAMIC)
                        .showEmptyPlaces(true)
                        .sortOrder(LeaderboardHologram.SortOrder.DESCENDING)
                        .titleFormat("<yellow>DEBUG HEADS</yellow>")
                        .footerFormat("<gray>────────────</gray>")
                        .lineHeight(0.25)
                        .background(true)
                        .backgroundWidth(40f)
                        .backgroundColor(0x20000000)
                        .decimalNumbers(false)
                        .build();

        LeaderboardHologram lb = new LeaderboardHologram(options, "debug_heads");
        hm.spawn(lb, loc);

        Map<UUID, LeaderboardHologram.PlayerScore> data = new HashMap<>();
        data.put(p.getUniqueId(), new LeaderboardHologram.PlayerScore(p.getName(), 42.0));
        lb.setAllScores(data);
        lb.update();

        // ---- HologramLib internal introspection ----
        this.getLogger().info("[CTC DEBUG] Spawned debug_heads at " + loc);

        List<TextHologram> texts = lb.getAllTextHolograms();
        this.getLogger().info("[CTC DEBUG] text holograms: " + texts.size());

        var head = lb.getFirstPlaceHead();
        this.getLogger().info("[CTC DEBUG] first place head hologram: " + head);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (sender instanceof Player) {
            Player p = (Player) sender;
            if (!p.isOp())
                return false;

            if (args[0].equalsIgnoreCase("reset")) {
                game.stop(p);
            } else if (args[0].equalsIgnoreCase("teleport")) {
                World w = Bukkit.getWorld(args[1]);
                p.teleport(w.getSpawnLocation());
            } else if (args[0].equalsIgnoreCase("start")) {
                game.start();
            }
            // change later so I can do maps (world manager)
            if (args[0].equalsIgnoreCase("red")) {
                String Map = args[1];
                worldManager.setRed(p, Map);
            } else if (args[0].equalsIgnoreCase("blue")) {
                String Map = args[1];
                worldManager.setBlue(p, Map);
            } else if (args[0].equalsIgnoreCase("center")) {
                String Map = args[1];
                worldManager.setCenter(p, Map);
            } else if (args[0].equalsIgnoreCase("direction")) {
                broadcast(p.getLocation().getDirection().toString());
            } else if (args[0].equalsIgnoreCase("kit")) {
                kit.openMenu(p);
            } else if (args[0].equalsIgnoreCase("map")) {
                map = args[1];
                worldManager.loadWorld("map", map);
                broadcast(args[1]);
            } else if (args[0].equalsIgnoreCase("tp")) {
                if (Bukkit.getWorld(args[1]) != null) {
                    p.teleport(Bukkit.getWorld(args[1]).getSpawnLocation());
                    broadcast("teleport");
                }
            } else if (args[0].equalsIgnoreCase("join")) {
                if (args.length < 2) {
                    send(p, "Usage: /ctc join <red|blue>", ChatColor.RED);
                } else if (args[1].equalsIgnoreCase("red")) {
                    game.joinRed(p);
                    send(p, "You have joined the Red team!", ChatColor.RED);
                } else if (args[1].equalsIgnoreCase("blue")) {
                    game.joinBlue(p);
                    send(p, "You have joined the Blue team!", ChatColor.BLUE);
                } else {
                    send(p, "Unknown team: " + args[1] + ". Use red or blue.", ChatColor.RED);
                }
                return true;
            } else if(args[0].equalsIgnoreCase("holo")) {
                spawnHeadDebugBoard(p);
            } else if (args[0].equalsIgnoreCase("cooldown")) {

                Kit k = (kit.kits.get(p.getUniqueId()));

                if (k == null) {
                    send(p, "No kit detected! This command requires a kit to use (for now)", ChatColor.DARK_AQUA);
                } else if (args.length < 2) {
                    send(p, "Usage: /ctc cooldown <default|seconds|precise>", ChatColor.RED);
                } else if (args[1].equalsIgnoreCase("default")) {
                    k.cooldownDisplayType = 0;
                    send(p, "Cooldowns will now be displayed in dots and lines!", ChatColor.RED);
                } else if (args[1].equalsIgnoreCase("seconds")) {
                    k.cooldownDisplayType = 1;
                    send(p, "Cooldowns will now be displayed in seconds!", ChatColor.BLUE);
                } else if (args[1].equalsIgnoreCase("precise")) {
                    k.cooldownDisplayType = 2;
                    send(p, "Cooldowns will now be precise seconds!", ChatColor.AQUA);
                } else {
                    send(p, "Unknown cooldown type: " + args[1] + ". Use <default|seconds|precise>", ChatColor.RED);
                }
                return true;
            }
        }


        // If the player (or console) uses our command correct, we can return true
        return true;
    }


    public void fakeExplode(
            Player p,
            Location l,
            int maxDamage,
            int maxDistance,
            boolean fire,
            boolean breaksBlocks,
            boolean damagesAllies,
            String ability,
            double falloff
    ) {

        falloff = Math.clamp(falloff,0.0,1.0);

        Location center = l.clone().add(0, 1, 0);
        World world = center.getWorld();

        float power = (maxDistance / 3f);
        power = Math.max(power,2f);

        world.createExplosion(center, power + ((float) maxDamage / 20), fire, breaksBlocks);
//        world.spawnParticle(Particle.FLASH,center,1,null);
        Particle.FLASH.builder()
                        .location(center)
                        .count(1)
                        .color(255,255,255)
                        .spawn();

        world.spawnParticle(Particle.GUST_EMITTER_LARGE,center,1,null);

        final int numberOfRays = 6;
        double[] offsets = {0, 1, 2, 3, 4, 5};

        for (Entity e : world.getNearbyEntities(center, maxDistance, maxDistance, maxDistance)) {
            if (!(e instanceof Player target)) continue;

            boolean isSelf = target.getUniqueId().equals(p.getUniqueId());

            // Skip same-team only if not self (self always allowed)
            if (!isSelf && !damagesAllies && game.sameTeam(p.getUniqueId(), target.getUniqueId())) {
                continue;
            }

            double distance = center.distance(target.getLocation());
            if (distance > maxDistance) continue;

            int raysHit = 0;
            for (double offset : offsets) {
                Location start = center.clone().add(0, offset, 0);
                Vector dir = target.getLocation().toVector().subtract(start.toVector()).normalize();
                RayTraceResult result = world.rayTraceBlocks(
                        start, dir, distance, FluidCollisionMode.NEVER, true
                );
                if (result == null) raysHit++;
            }

            if (raysHit == 0) continue;

            double calcFalloff = ((1 - ((distance / maxDistance) * falloff) ));
            calcFalloff = Math.clamp(calcFalloff,0.0,1.0);
            double damageFactor = (double) raysHit / numberOfRays;
            double damage = maxDamage * calcFalloff * damageFactor;
            if (damage <= 0) continue;

            if (isSelf) {
                // self damage: do NOT track
                target.setHealth(Math.max(0.0, target.getHealth() - damage));

                Kit k = kit.kits.get(target.getUniqueId());
                if (k != null) {
                    k.procHungerDamage();
                }

            } else {
                // tracked damage
                combatTracker.setHealth(target, target.getHealth() - damage, p, ability);
            }
        }
    }

    public void fakeExplode(
            Player p,
            Location l,
            int maxDamage,
            int maxDistance,
            boolean fire,
            boolean breaksBlocks,
            boolean damagesAllies,
            String ability
    ) {
        fakeExplode(p,l,maxDamage,maxDistance,fire,breaksBlocks,damagesAllies,ability,1.0f);
    }

    public ItemStack coreCrush() {
        ItemStack crusher = new ItemStack(Material.DIAMOND_PICKAXE, 1);
        ItemMeta meta = crusher.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "CORE CRUSHER");
        meta.addEnchant(Enchantment.EFFICIENCY, 5, false);
        crusher.setItemMeta(meta);
        return crusher;
    }

    public HashMap<UUID, Kit> getKits() {
        return kit.kits;
    }

    public StatsManager getStats() {
        return stats;
    }

    public DatabaseManager getDatabase() {
        return db;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }
}
