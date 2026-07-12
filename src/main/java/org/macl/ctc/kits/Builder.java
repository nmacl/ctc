package org.macl.ctc.kits;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.block.data.type.Ladder;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.checkerframework.checker.units.qual.C;
import org.macl.ctc.Main;

import java.util.*;

public class Builder extends Kit {

    boolean stairs = false;
    boolean tower = false;
    boolean bridge = false;
    boolean platform = false;
    Inventory inv;

    ItemStack medFire = (
            main.game.redHas(p))
            ? newItem(Material.CAMPFIRE, ChatColor.RED + "Med-Fire")
            : newItem(Material.SOUL_CAMPFIRE, ChatColor.AQUA + "Med-Fire");

    public Builder(Main main, Player p, KitType type) {
        super(main, p, type);
        // TODO Auto-generated constructor stub
        PlayerInventory e = p.getInventory();
        e.addItem(newItem(Material.DIAMOND_SHOVEL, ChatColor.DARK_GREEN + "Hammer"));
        e.setHelmet(newItem(Material.GOLDEN_HELMET, ChatColor.GOLD + "Hard Hat"));
        e.setChestplate(newItem(Material.LEATHER_CHESTPLATE, ChatColor.YELLOW + "Shirt"));
        e.setBoots(newItem(Material.IRON_BOOTS, ChatColor.DARK_GRAY + "Boots"));
        e.addItem(newItem(Material.SHEARS, ChatColor.GRAY + "Wool Shear"));
        e.addItem(medFire);
        giveWool();
        giveWool();
        giveWool();
        giveWool();
        regenItem("Medfire",medFire,60,1,2);
        inv = Bukkit.createInventory(p, 9, main.prefix + "BuildTools");

        inv.setItem(2, newItem(Material.OAK_SLAB, ChatColor.DARK_GREEN + "3x12 wool bridge with packed ice"));
        List<String> stairLore = Arrays.asList(
                ChatColor.YELLOW + "Right Click to descend"
        );
        ItemStack stair = newItem(Material.OAK_STAIRS, ChatColor.LIGHT_PURPLE + "5x5 wool stairs");
        ItemMeta m = stair.getItemMeta();
        m.setLore(stairLore);
        stair.setItemMeta(m);
        inv.setItem(3, stair);
        inv.setItem(5, newItem(Material.LADDER, ChatColor.GOLD + "3x3 wool tower"));
        inv.setItem(6, newItem(Material.SLIME_BLOCK, ChatColor.GREEN + "5x5 bouncy platform"));
    }

    public void openMenu() {
        p.openInventory(inv);
    }

    public void stairs(boolean altCast) {
        if (stairs)
            return;
        BukkitTask t = new BuildStair(main, p.getLocation(), p.getFacing(), p,altCast).runTaskTimer(main, 0, 4L);
        registerTask(t);
    }

    public void tower() {
        if (tower)
            return;
        BukkitTask t = new BuildTower(main, p.getLocation(), p,p.getFacing()).runTaskTimer(main, 0L, 4L);
        registerTask(t);
    }

    public void bridge() {
        if (bridge)
            return;
        BukkitTask t = new BuildBridge(main, p.getLocation(), p.getFacing(), p).runTaskTimer(main, 0L, 4L);
        registerTask(t);
    }

    public void platform() {
        if (platform)
            return;
        BukkitTask t = new BuildPlatform(main, p.getLocation(), p).runTaskTimer(main, 0L, 4L);
        registerTask(t);
    }

    public class BuildStair extends BukkitRunnable {

        private final Main main;

        private int temp = 0;
        private int cooldown = 8*(20/4);
        private Location loc;
        private Player p;
        private BlockFace dir;
        private Material woolType;
        private int descend = 1;

        public BuildStair(Main main, Location loc, BlockFace dire, Player p,boolean altCast) {
            this.main = main;
            this.loc = loc;
            this.p = p;
            this.dir = dire;
            woolType = (main.game.redHas(p)) ? Material.ORANGE_WOOL : Material.LIGHT_BLUE_WOOL;
            loc.setY(loc.getBlockY() - 1);
            if (dir == BlockFace.NORTH) {
                loc.setZ(loc.getBlockZ() - 1);
            } else if (dir == BlockFace.SOUTH) {
                loc.setZ(loc.getBlockZ() + 1);
            } else if (dir == BlockFace.WEST) {
                loc.setX(loc.getBlockX() - 1);
            } else if (dir == BlockFace.EAST) {
                loc.setX(loc.getBlockX() + 1);
            }
            stairs = true;
            if (altCast) descend = -1;
        }

        //north = -z, south = +z, east = +x, west = -x
        public void run() {
            Location tempLoc = loc;
            World w = loc.getWorld();
            if (temp <= 5) {
                int y = temp*descend;
                if (dir == BlockFace.NORTH) {
                    tempLoc.setZ(loc.getBlockZ() - 1);
                    for (int i = -5; i < 5; i++) {
                        if (main.restricted.contains(w.getBlockAt(tempLoc.getBlockX() + i, tempLoc.getBlockY() + y, tempLoc.getBlockZ()).getType()))
                            continue;
                        w.getBlockAt(tempLoc.getBlockX() + i, tempLoc.getBlockY() + y, tempLoc.getBlockZ()).setType(woolType);
                    }
                } else if (dir == BlockFace.SOUTH) {
                    tempLoc.setZ(loc.getBlockZ() + 1);
                    for (int i = -5; i < 5; i++) {
                        if (main.restricted.contains(w.getBlockAt(tempLoc.getBlockX() + i, tempLoc.getBlockY() + y, tempLoc.getBlockZ()).getType()))
                            continue;
                        w.getBlockAt(tempLoc.getBlockX() + i, tempLoc.getBlockY() + y, tempLoc.getBlockZ()).setType(woolType);
                    }
                } else if (dir == BlockFace.WEST) {
                    tempLoc.setX(loc.getBlockX() - 1);
                    for (int i = -5; i < 5; i++) {
                        if (main.restricted.contains(w.getBlockAt(tempLoc.getBlockX(), tempLoc.getBlockY() + y, tempLoc.getBlockZ() + i).getType()))
                            continue;
                        w.getBlockAt(tempLoc.getBlockX(), tempLoc.getBlockY() + y, tempLoc.getBlockZ() + i).setType(woolType);
                    }
                } else if (dir == BlockFace.EAST) {
                    tempLoc.setX(loc.getBlockX() + 1);
                    for (int i = -5; i < 5; i++) {
                        if (main.restricted.contains(w.getBlockAt(tempLoc.getBlockX(), tempLoc.getBlockY() + y, tempLoc.getBlockZ() + i).getType()))
                            continue;
                        w.getBlockAt(tempLoc.getBlockX(), tempLoc.getBlockY() + y, tempLoc.getBlockZ() + i).setType(woolType);
                    }
                }
                w.playSound(loc, Sound.BLOCK_GLASS_PLACE, 10, temp);

                //loc.subtract(0, temp, 0);
            } else {
                if (cooldown == 0) {
                    this.cancel();
                    int first = inv.first(Material.OAK_STAIRS);
                    inv.getItem(first).setAmount(1);
                    stairs = false;
                    return;
                }
            }
            int first = inv.first(Material.OAK_STAIRS);
            inv.getItem(first).setAmount(Math.max(((cooldown + 8) / 5),1));
            cooldown--;
            temp++;
        }
    }

    public class BuildTower extends BukkitRunnable {

        private int temp = 30;
        private int cooldown = 10*(20/4);
        private Location loc;
        private Material woolType;
        private BlockFace dir;

        public BuildTower(Main main, Location loc, Player p,BlockFace dir) {
            this.loc = loc;
            this.dir = dir;
            woolType = (main.game.redHas(p)) ? Material.ORANGE_WOOL : Material.LIGHT_BLUE_WOOL;
            loc.setY(loc.getBlockY() - 1);
            for (Location b : circle(loc, 3, false)) {
                if(!main.restricted.contains(loc.getWorld().getBlockAt(b).getType()))
                    loc.getWorld().getBlockAt(b).setType(woolType);
            }
            tower = true;
        }

        //north = -z, south = +z, east = +x, west = -x
        public void run() {
            if (temp >= 23) {
                Location tempLoc = loc;
                tempLoc.setY(loc.getY() + 1);
                World w = loc.getWorld();
                for (Location b : circle(tempLoc, 3, true)) {
                    if (main.restricted.contains(w.getBlockAt(b).getType()))
                        continue;
                    w.getBlockAt(b).setType(woolType);
                }

                if (dir == BlockFace.SOUTH) {
                    if (!main.restricted.contains(w.getBlockAt(loc.getBlockX(), tempLoc.getBlockY(), loc.getBlockZ() + 2).getType())) {
                        Block ladderLoc = w.getBlockAt(loc.getBlockX(), tempLoc.getBlockY(), loc.getBlockZ() + 2);
                        ladderLoc.setType(Material.LADDER);
                        Directional d = ((Directional) ladderLoc.getBlockData());
                        d.setFacing(BlockFace.NORTH);
                        ladderLoc.setBlockData(d);

                    }
                } else if (dir == BlockFace.NORTH) {
                    if (!main.restricted.contains(w.getBlockAt(loc.getBlockX(), tempLoc.getBlockY(), loc.getBlockZ() - 2).getType())) {
                        Block ladderLoc = w.getBlockAt(loc.getBlockX(), tempLoc.getBlockY(), loc.getBlockZ() - 2);
                        ladderLoc.setType(Material.LADDER);
                        Directional d = ((Directional) ladderLoc.getBlockData());
                        d.setFacing(BlockFace.SOUTH);
                        ladderLoc.setBlockData(d);

                    }
                } else if (dir == BlockFace.WEST) {
                    if (!main.restricted.contains(w.getBlockAt(loc.getBlockX() - 2, tempLoc.getBlockY(), loc.getBlockZ()).getType())) {
                        Block ladderLoc = w.getBlockAt(loc.getBlockX() - 2, tempLoc.getBlockY(), loc.getBlockZ());
                        ladderLoc.setType(Material.LADDER);
                        Directional d = ((Directional) ladderLoc.getBlockData());
                        d.setFacing(BlockFace.EAST);
                        ladderLoc.setBlockData(d);

                    }
                } else if (dir == BlockFace.EAST) {
                    if (!main.restricted.contains(w.getBlockAt(loc.getBlockX() + 2, tempLoc.getBlockY(), loc.getBlockZ()).getType())) {
                        Block ladderLoc = w.getBlockAt(loc.getBlockX() + 2, tempLoc.getBlockY(), loc.getBlockZ());
                        ladderLoc.setType(Material.LADDER);
                        Directional d = ((Directional) ladderLoc.getBlockData());
                        d.setFacing(BlockFace.WEST);
                        ladderLoc.setBlockData(d);

                    }
                }

                Location effectLoc = new Location(loc.getWorld(), loc.getX(), loc.getY() + 1, loc.getZ());
                w.playSound(loc, Sound.BLOCK_LADDER_PLACE, 10, temp);
                w.playEffect(effectLoc, Effect.SMOKE, temp);
            } else {
                if (cooldown == 0) {
                    this.cancel();
                    tower = false;
                    int first = inv.first(Material.LADDER);
                    inv.getItem(first).setAmount(1);
                    return;
                }
            }
            int first = inv.first(Material.LADDER);
            inv.getItem(first).setAmount(Math.max(((cooldown + 8) / 5),1));
            cooldown--;
            temp--;
        }
    }

    public class BuildBridge extends BukkitRunnable {

        private int temp = 30;
        private int cooldown = 10*(20/4);
        private Location loc;
        private BlockFace dir;
        private Material woolType;

        public BuildBridge(Main main, Location loc, BlockFace dire, Player p) {
            this.loc = loc;
            this.dir = dire;
            woolType = (main.game.redHas(p)) ? Material.ORANGE_WOOL : Material.LIGHT_BLUE_WOOL;
            loc.setY(loc.getBlockY() - 1);
            bridge = true;
        }

        //north = -z, south = +z, east = +x, west = -x
        public void run() {
            if (temp >= 19) {
                Location tempLoc = loc;
                World w = loc.getWorld();
                if (dir == BlockFace.NORTH) {
                    tempLoc.setZ(loc.getBlockZ() - 1);
                    if (!main.restricted.contains(w.getBlockAt(tempLoc).getType())) {
                        w.getBlockAt(tempLoc).setType(Material.PACKED_ICE);
                    }
                    if (!main.restricted.contains(w.getBlockAt(tempLoc.getBlockX() + 1, tempLoc.getBlockY(), tempLoc.getBlockZ()).getType())) {
                        w.getBlockAt(tempLoc.getBlockX() + 1, tempLoc.getBlockY(), tempLoc.getBlockZ()).setType(woolType);
                    }
                    if (!main.restricted.contains(w.getBlockAt(tempLoc.getBlockX() - 1, tempLoc.getBlockY(), tempLoc.getBlockZ()).getType())) {
                        w.getBlockAt(tempLoc.getBlockX() - 1, tempLoc.getBlockY(), tempLoc.getBlockZ()).setType(woolType);
                    }
                } else if (dir == BlockFace.SOUTH) {
                    tempLoc.setZ(loc.getBlockZ() + 1);
                    if (!main.restricted.contains(w.getBlockAt(tempLoc).getType())) {
                        w.getBlockAt(tempLoc).setType(Material.PACKED_ICE);
                    }
                    if (!main.restricted.contains(w.getBlockAt(tempLoc.getBlockX() + 1, tempLoc.getBlockY(), tempLoc.getBlockZ()).getType())) {
                        w.getBlockAt(tempLoc.getBlockX() + 1, tempLoc.getBlockY(), tempLoc.getBlockZ()).setType(woolType);
                    }
                    if (!main.restricted.contains(w.getBlockAt(tempLoc.getBlockX() - 1, tempLoc.getBlockY(), tempLoc.getBlockZ()).getType())) {
                        w.getBlockAt(tempLoc.getBlockX() - 1, tempLoc.getBlockY(), tempLoc.getBlockZ()).setType(woolType);
                    }
                } else if (dir == BlockFace.WEST) {
                    tempLoc.setX(loc.getBlockX() - 1);
                    if (!main.restricted.contains(w.getBlockAt(tempLoc).getType())) {
                        w.getBlockAt(tempLoc).setType(Material.PACKED_ICE);
                    }
                    if (!main.restricted.contains(w.getBlockAt(tempLoc.getBlockX(), tempLoc.getBlockY(), tempLoc.getBlockZ() + 1).getType())) {
                        w.getBlockAt(tempLoc.getBlockX(), tempLoc.getBlockY(), tempLoc.getBlockZ() + 1).setType(woolType);
                    }
                    if (!main.restricted.contains(w.getBlockAt(tempLoc.getBlockX(), tempLoc.getBlockY(), tempLoc.getBlockZ() - 1).getType())) {
                        w.getBlockAt(tempLoc.getBlockX(), tempLoc.getBlockY(), tempLoc.getBlockZ() - 1).setType(woolType);
                    }
                } else if (dir == BlockFace.EAST) {
                    tempLoc.setX(loc.getBlockX() + 1);
                    if (!main.restricted.contains(w.getBlockAt(tempLoc).getType())) {
                        w.getBlockAt(tempLoc).setType(Material.PACKED_ICE);
                    }
                    if (!main.restricted.contains(w.getBlockAt(tempLoc.getBlockX(), tempLoc.getBlockY(), tempLoc.getBlockZ() + 1).getType())) {
                        w.getBlockAt(tempLoc.getBlockX(), tempLoc.getBlockY(), tempLoc.getBlockZ() + 1).setType(woolType);
                    }
                    if (!main.restricted.contains(w.getBlockAt(tempLoc.getBlockX(), tempLoc.getBlockY(), tempLoc.getBlockZ() - 1).getType())) {
                        w.getBlockAt(tempLoc.getBlockX(), tempLoc.getBlockY(), tempLoc.getBlockZ() - 1).setType(woolType);
                    }
                }

                Location effectLoc = new Location(loc.getWorld(), loc.getX(), loc.getY() + 1, loc.getZ());
                w.playSound(loc, Sound.BLOCK_WOOL_PLACE, 10, temp);
                w.playEffect(effectLoc, Effect.SMOKE, temp);
            } else {
                if (cooldown == 0) {
                    this.cancel();
                    bridge = false;
                    int first = inv.first(Material.OAK_SLAB);
                    inv.getItem(first).setAmount(1);
                    return;
                }
            }
            int first = inv.first(Material.OAK_SLAB);
            inv.getItem(first).setAmount(Math.max(((cooldown + 8) / 5),1));
            cooldown--;
            temp--;
        }
    }

    public class BuildPlatform extends BukkitRunnable {

        private final Main main;

        private int temp = 0;
        private int cooldown = 12*(20/4);
        private Location loc;
        private Player p;
        private Material woolType;

        public BuildPlatform(Main main, Location loc, Player p) {
            this.main = main;
            this.loc = loc;
            this.p = p;
            woolType = (main.game.redHas(p)) ? Material.ORANGE_WOOL : Material.LIGHT_BLUE_WOOL;
            loc.setY(loc.getBlockY() - 1);
            platform = true;
        }

        //north = -z, south = +z, east = +x, west = -x
        public void run() {
            World w = loc.getWorld();
            if (temp <= 2) {

                for (int x = -temp; x <= temp; x++) {
                    for (int z = -temp; z <= temp; z++) {
                        Block b = loc.clone().add(x, -2, z).getBlock();
                        Material current = b.getType();

                        if (main.restricted.contains(current)) {
                            continue;
                        }
                        if (temp < 2) {
                            b.setType(Material.SLIME_BLOCK);
                        } else if (b.getType() != Material.SLIME_BLOCK) b.setType(woolType);
                    }
                }

                if (temp < 2) {
                    w.playSound(loc, Sound.BLOCK_SLIME_BLOCK_PLACE, 10, temp);

                } else w.playSound(loc, Sound.BLOCK_WOOL_PLACE, 10, temp);

                //loc.subtract(0, temp, 0);
            } else {
                if (cooldown == 0) {
                    this.cancel();
                    int first = inv.first(Material.SLIME_BLOCK);
                    inv.getItem(first).setAmount(1);
                    platform = false;
                    return;
                }
            }
            int first = inv.first(Material.SLIME_BLOCK);
            inv.getItem(first).setAmount(Math.max(((cooldown + 8) / 5),1));
            cooldown--;
            temp++;
        }
    }

    public void createMedFire(BlockPlaceEvent event) {
        new medFireProcess(event,main.game.redHas(p)).runTaskTimer(main,0,1L);
    }


    public class medFireProcess extends BukkitRunnable {

        boolean isRed;
        Block b;
        BukkitTask refreshTask;
        Location l;


        public medFireProcess(BlockPlaceEvent event, Boolean isRed) {
            this.isRed = isRed;
            b = event.getBlock();
            l = event.getBlock().getLocation();
            p.getWorld().playSound(event.getBlock().getLocation(),Sound.ENTITY_BLAZE_SHOOT,2.0f,1.2f);
            p.getWorld().playSound(event.getBlock().getLocation(),Sound.ENTITY_BLAZE_SHOOT,2.0f,0.8f);
            p.getWorld().playSound(event.getBlock().getLocation(),Sound.BLOCK_END_PORTAL_FRAME_FILL,1.0f,0.2f);
        }

        public void run() {

//            Material m = b.getType();

            if ((b.getType() != Material.SOUL_CAMPFIRE && b.getType() != Material.CAMPFIRE)) {
                if (refreshTask != null) {
                    refreshTask.cancel();
                }
                p.getWorld().playSound(l.clone().add(0.5,0.5,0.5),Sound.ENTITY_BLAZE_DEATH,0.5f,0.8f);
                this.cancel();
            }

            if (!isCampfireLit()) return;

            for (Entity e : b.getWorld().getNearbyEntities(b.getLocation().add(0,0.1,0),1.5,1.5,1.5)) {
                if (e.getLocation().subtract(b.getLocation().clone().add(0.5,0.5,0.5)).length() > 1.4) return;

                if (e instanceof Player pl) {
                    if (isRed) {
                        if (main.game.redHas(pl)) {
                            healPlayer(pl);
                        }
                    } else {
                        if (!main.game.redHas(pl)) {
                            healPlayer(pl);
                        }
                    }
                }
            }
            Particle pa = (main.game.redHas(p) ? Particle.FLAME : Particle.SOUL_FIRE_FLAME);
            b.getWorld().spawnParticle(pa,b.getLocation().add(0.5,0,0.5),1,0.3,0.3,0.3,0.1);
        }

        public void healPlayer(Player p) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.INSTANT_HEALTH,1,0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,15*20,0));
//            p.playSound(p,Sound.BLOCK_AMETHYST_BLOCK_RESONATE,1.0f,1.2f);
            p.getWorld().playSound(b.getLocation().clone().add(0.5,0.5,0.5),Sound.BLOCK_END_PORTAL_FRAME_FILL,1.0f,1.2f);
            setCampfireLit(false);

            refreshTask = new BukkitRunnable() {
                public void run() {
                    setCampfireLit(true);
                }
            }.runTaskLater(main,12*20L);
        }

        public void setCampfireLit(boolean lit) {
            if (b.getBlockData() instanceof Campfire c) {
                c.setLit(lit);
                b.setBlockData(c);
                if (lit) {
                    c.setSignalFire(true);
                    p.getWorld().playSound(b.getLocation().clone().add(0.5,0.5,0.5),Sound.ENTITY_BLAZE_SHOOT,0.5f,0.8f);
                }
            }
        }

        public boolean isCampfireLit() {
            if (b.getBlockData() instanceof Campfire c) {
                return c.isLit();
            } else return false;
        }

    };


    public void useShear() {
        if (isOnCooldown("Wool Shear")) return;
        setCooldown("Wool Shear", 20, Sound.BLOCK_BEEHIVE_SHEAR);

        Location shearLoc = p.getLocation().add(0,0,0);

        new BukkitRunnable() {

            int stage = 0;
            int step = 0;

            public void run() {
                step++;
                for (Location l : sphere(shearLoc,step + 1)) { // this can be optimized. but not now. im lazy.
                    if (Tag.WOOL.isTagged(l.getBlock().getBlockData().getMaterial())) {
                        if (l.getBlock().getType() != getWoolType()) {
                            l.getBlock().setType(getWoolType());
                        }
                    }
                }

                p.getWorld().playSound(shearLoc,Sound.BLOCK_NOTE_BLOCK_FLUTE,1.5f,(float)((0.1) + ((stage / 5.0) * step)));

                if (step >= 4) {
                    step = 0;
                    stage++;
                }

                if (stage >= 4) {
                    p.getWorld().playSound(shearLoc,Sound.ENTITY_SHEEP_SHEAR,2.5f,1.0f);
                    for (Location l : sphere(shearLoc,5)) {
                        if (Tag.WOOL.isTagged(l.getBlock().getBlockData().getMaterial())) {
                            l.getBlock().setType(Material.AIR);
                        }
                    }
                    this.cancel();
                }

            }

            public Material getWoolType() {
                Material m = Material.LIGHT_GRAY_WOOL;
                if (stage >= 1) m = Material.GRAY_WOOL;
                if (stage >= 2) m = Material.BLACK_WOOL;
//                if (stage >= 3) m = Material.AIR;
                return m;
            }

        }.runTaskTimer(main,0L,4L);


    }

    private static Set<Location> makeHollow(Set<Location> blocks, boolean sphere) {
        Set<Location> edge = new HashSet<Location>();
        if (!sphere) {
            for (Location l : blocks) {
                World w = l.getWorld();
                int X = l.getBlockX();
                int Y = l.getBlockY();
                int Z = l.getBlockZ();
                Location front = new Location(w, X + 1, Y, Z);
                Location back = new Location(w, X - 1, Y, Z);
                Location left = new Location(w, X, Y, Z + 1);
                Location right = new Location(w, X, Y, Z - 1);
                if (!(blocks.contains(front) && blocks.contains(back) && blocks.contains(left) && blocks.contains(right))) {
                    edge.add(l);
                }
            }
            return edge;
        } else {
            for (Location l : blocks) {
                World w = l.getWorld();
                int X = l.getBlockX();
                int Y = l.getBlockY();
                int Z = l.getBlockZ();
                Location front = new Location(w, X + 1, Y, Z);
                Location back = new Location(w, X - 1, Y, Z);
                Location left = new Location(w, X, Y, Z + 1);
                Location right = new Location(w, X, Y, Z - 1);
                Location top = new Location(w, X, Y + 1, Z);
                Location bottom = new Location(w, X, Y - 1, Z);
                if (!(blocks.contains(front) && blocks.contains(back) && blocks.contains(left) && blocks.contains(right) && blocks.contains(top) && blocks.contains(bottom))) {
                    edge.add(l);
                }
            }
            return edge;
        }
    }

    public static Set<Location> circle(Location location, int radius, boolean hollow) {
        Set<Location> blocks = new HashSet<Location>();
        World world = location.getWorld();
        int X = location.getBlockX();
        int Y = location.getBlockY();
        int Z = location.getBlockZ();
        int radiusSquared = (radius * radius) + 1;

        if (hollow) {
            for (int x = X - radius; x <= X + radius; x++) {
                for (int z = Z - radius; z <= Z + radius; z++) {
                    if ((X - x) * (X - x) + (Z - z) * (Z - z) <= radiusSquared) {
                        Location block = new Location(world, x, Y, z);
                        blocks.add(block);
                    }
                }
            }
            return makeHollow(blocks, false);
        } else {
            for (int x = X - radius; x <= X + radius; x++) {
                for (int z = Z - radius; z <= Z + radius; z++) {
                    if ((X - x) * (X - x) + (Z - z) * (Z - z) <= radiusSquared) {
                        Location block = new Location(world, x, Y, z);
                        blocks.add(block);
                    }
                }
            }
            return blocks;
        }
    }

    public ArrayList<Location> sphere(Location location, int radius) {
        ArrayList<Location> blocks = new ArrayList<Location>();
        World world = location.getWorld();
        int X = location.getBlockX();
        int Y = location.getBlockY();
        int Z = location.getBlockZ();
        int radiusSquared = radius * radius;


        for (int x = X - radius; x <= X + radius; x++) {
            for (int y = Y - radius; y <= Y + radius; y++) {
                for (int z = Z - radius; z <= Z + radius; z++) {
                    if ((X - x) * (X - x) + (Y - y) * (Y - y) + (Z - z) * (Z - z) <= radiusSquared) {
                        Location block = new Location(world, x, y, z);
                        blocks.add(block);
                    }
                }
            }
        }
        return blocks;
    }
}