package org.macl.ctc.kits;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.EndGateway;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.GlassPane;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.*;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.macl.ctc.Main;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

public class Tank extends Kit {

    ItemStack shield = (main.game.redHas(p)) ? newItem(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + "Shield") : newItem(Material.BLUE_STAINED_GLASS_PANE, ChatColor.BLUE + "Shield");
    ItemStack spentShield = newItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE,ChatColor.GRAY + "Shield Recharging...");
    ItemStack glass = (main.game.redHas(p)) ? new ItemStack(Material.RED_STAINED_GLASS_PANE) : new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
    ItemStack hellfire = newItem(Material.FLINT_AND_STEEL, ChatColor.RED + "Hellfire Missile", 1);
    ItemStack gun = newItem(Material.NETHERITE_SHOVEL, ChatColor.GOLD + "Gatling Gun");
    ItemStack gunOff = newItem(Material.NETHERITE_SHOVEL, ChatColor.DARK_GRAY + "Gatling - Off");
    ItemStack gunOn = newItem(Material.GOLDEN_SHOVEL,ChatColor.GOLD + "" + ChatColor.BOLD + "FIRING!");
    ItemStack napalm = newItem(Material.EXPERIENCE_BOTTLE,ChatColor.YELLOW + "Napalm Charge",2);

    boolean setup = false;

    boolean gatlingBuffer = false;

    GatlingProcess gatlingProcess;

    ArrayList<Location> locs = new ArrayList<>();

    private Material savedBlockType = null;

    public Tank(Main main, Player p, KitType type) {
        super(main, p, type);
        p.getInventory().addItem(gun);
        p.getInventory().addItem(hellfire);
        p.getInventory().addItem(shield);
        e.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        e.setLeggings(newItem(Material.IRON_LEGGINGS, "piss Pants"));
        e.setBoots(new ItemStack(Material.NETHERITE_BOOTS));
        giveWool(24);
        p.removePotionEffect(PotionEffectType.SPEED);
        setHearts(28);
        p.setHealth(28);
    }

    public void gatlingSetup() {
        Location bLoc = p.getLocation().getBlock().getLocation().add(0.5,0.0,0.5);

        BlockFace face = p.getFacing();

        bLoc.setPitch(p.getLocation().getPitch());
        bLoc.setYaw(p.getLocation().getYaw());

        ArrayList<Location> ironLocs = new ArrayList<>();
        ArrayList<Location> topSlabLocs = new ArrayList<>();
        ArrayList<Location> bottomSlabLocs = new ArrayList<>();

        savedBlockType = bLoc.clone().subtract(0,1,0).getBlock().getType();

        BukkitTask t = new BukkitRunnable() {
            public void run() {
//                main.broadcast(face.toString());

                if (face == BlockFace.EAST || face == BlockFace.WEST) {
                    // z + -
                    ironLocs.add(bLoc.clone().add(0, 1, 1));
                    ironLocs.add(bLoc.clone().add(0, 1, -1));
                    topSlabLocs.add(bLoc.clone().add(0, 2, -1));
                    topSlabLocs.add(bLoc.clone().add(0, 2, 1));
                } else if (face == BlockFace.SOUTH || face == BlockFace.NORTH) {
                    // x + -
                    ironLocs.add(bLoc.clone().add(1, 1, 0));
                    ironLocs.add(bLoc.clone().add(-1, 1, 0));
                    topSlabLocs.add(bLoc.clone().add(1, 2, 0));
                    topSlabLocs.add(bLoc.clone().add(-1, 2, 0));
                }
                if (!main.restricted.contains(bLoc.clone().add(0, -1, 0).getBlock().getType())) {
                    bLoc.clone().add(0, -1, 0).getBlock().setType(Material.LIGHT_GRAY_WOOL);
                    locs.add(bLoc.clone().add(0, -1, 0));
                }

                ironLocs.add(bLoc.clone().add(-1, 0, 0));
                ironLocs.add(bLoc.clone().add(1, 0, 0));
                ironLocs.add(bLoc.clone().add(0, 0, 1));
                ironLocs.add(bLoc.clone().add(0, 0, -1));

                Material m = (main.game.redHas(p)) ? Material.REDSTONE_BLOCK : Material.LAPIS_BLOCK;

                if (!main.restricted.contains(bLoc.clone().add(0, 2, 0).getBlock().getType())) {
                    bLoc.clone().add(0, 2, 0).getBlock().setType(m);
                    locs.add(bLoc.clone().add(0, 2, 0));
                }

                topSlabLocs.add(bLoc.clone().add(1, 0, 1));
                topSlabLocs.add(bLoc.clone().add(-1, 0, 1));
                topSlabLocs.add(bLoc.clone().add(1, 0, -1));
                topSlabLocs.add(bLoc.clone().add(-1, 0, -1));
                bottomSlabLocs.add(bLoc.clone().add(1, -1, 0));
                bottomSlabLocs.add(bLoc.clone().add(1, -1, 1));
                bottomSlabLocs.add(bLoc.clone().add(0, -1, 1));
                bottomSlabLocs.add(bLoc.clone().add(-1, -1, 0));
                bottomSlabLocs.add(bLoc.clone().add(-1, -1, -1));
                bottomSlabLocs.add(bLoc.clone().add(0, -1, -1));
                bottomSlabLocs.add(bLoc.clone().add(1, -1, -1));
                bottomSlabLocs.add(bLoc.clone().add(-1, -1, 1));

                for (Location loc : ironLocs) {
                    if (main.restricted.contains(loc.getBlock().getType())) continue;
                    loc.getBlock().setType(Material.IRON_BLOCK);
                    locs.add(loc);
                }
                for (Location loc : topSlabLocs) {
                    if (main.restricted.contains(loc.getBlock().getType())) continue;
                    loc.getBlock().setType(Material.QUARTZ_SLAB);
                    locs.add(loc);
                }
                for (Location loc : bottomSlabLocs) {
                    if (loc.getBlock().getType() == Material.AIR) {
                        loc.getBlock().setType(Material.QUARTZ_SLAB);
                        Slab s = (Slab) loc.getBlock().getBlockData();
                        s.setType(Slab.Type.TOP);
                        loc.getBlock().setBlockData(s);
                        locs.add(loc);
                    }
                }

                p.teleport(bLoc);
            }
        }.runTaskLater(main, 3L);
        registerTask(t);

        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 999999999, 5));
        e.setItem(0, gunOff);
        e.setItem(1,napalm);
        e.setItem(2,newItem(Material.FLINT,ChatColor.DARK_RED + "EXIT"));

        gatlingProcess = new GatlingProcess();
        BukkitTask task = gatlingProcess.runTaskTimer(main,0L,1L);
        registerTask(task);

        setup = true;
    }

    public void gatling() {
        if (isOnCooldown("gatling") || inHellfire)
            return;
        if (!setup) {
            if (locs != null) {
                for (Location l : locs) {
                    l.getBlock().setType(Material.AIR);
                }
                locs.clear();
            }
            gatlingSetup();
        } else {
            if (gatlingProcess == null) return;

            if (!gatlingProcess.shootingActive && !gatlingBuffer) {
                e.setItem(0,gunOn);
                gatlingBuffer = true;
                gatlingProcess.shootingActive = true;
            }
            if (p.hasPotionEffect(PotionEffectType.SLOWNESS)) {
                p.removePotionEffect(PotionEffectType.SLOWNESS);
            }
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,999999999,11));

        }
    }

    public void switchGatlingOff() {
        if (gatlingProcess == null) return;

        if (gatlingProcess.shootingActive && !gatlingBuffer) {
            e.setItem(0,gunOff);
            gatlingBuffer = true;
            gatlingProcess.shootingActive = false;
            if (p.hasPotionEffect(PotionEffectType.SLOWNESS)) {
                p.removePotionEffect(PotionEffectType.SLOWNESS);
            }
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,999999999,5));
        }
    }

    public class GatlingProcess extends BukkitRunnable {

        boolean shootingActive = false;

        int shots = 0;

        int maxShots = 200;

        int regenTicks = 0;

        ItemStack item;

        public GatlingProcess() {

        }

        public void run() {
            item = e.getItem(0);

            if (gatlingBuffer) gatlingBuffer = false;

            if (shots >= maxShots) {
                exit();
                this.cancel();
                return;
            }

            if (shootingActive) {
                shoot();
            } else {
                regenHeat();
            }

            updateToolDamage();

        }


        public void shoot() {
            Snowball b = p.launchProjectile(Snowball.class);
            if (shots > (maxShots / 2)) {
                b.setFireTicks(999);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.1f);
            }
            b.setVelocity(new Vector());

            Vector lookDir = p.getEyeLocation().getDirection();
            double overheatAngle = (((double) shots / maxShots) * 0.125);

            Vector newDir = Grandpa.randomizeVectorAngle(lookDir,overheatAngle);

            b.setVelocity(newDir.normalize().multiply(1.35 + (float) shots / 175));
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, 1f, 0.1f + ((float) shots / 200));
            shots++;

        }

        public void regenHeat() {
            if (regenTicks <= 1) {
                regenTicks++;
            } else {
                shots = Math.max(shots - 1,0);
            }
        }

        public void updateToolDamage() {
            int maxDura = item.getType().getMaxDurability();
            double heatRatio = ((double) shots / maxShots);
            int damage = (int) (maxDura * (heatRatio));

            setItemDamage(item,damage);
        }

        static public void setItemDamage(ItemStack item,int dmg) {
            ItemMeta itemMeta = item.getItemMeta();
            Damageable damage = (Damageable) itemMeta;

            if (itemMeta != null) {
                if (dmg != item.getType().getMaxDurability()) {
//                    main.broadcast("" + dmg);
                    damage.setDamage(dmg);
                }
            }

            item.setItemMeta(itemMeta);
        }



    }


    public void shield(Block placedBlock, BlockFace playerFacing) {
        if(inHellfire) return;
        if (isOnCooldown("Shield")) return;
        setCooldown("Shield", 32, Sound.BLOCK_CHAIN_PLACE, () -> {
            if (gatlingProcess == null) {
                e.setItem(2, shield);
            }
        });

        e.setItem(2,spentShield);

        ArrayList<Block> blocks = new ArrayList<>();

        placedBlock.setType(glass.getType(),true);
        blocks.add(placedBlock);

        double wallX = 1.5;
        double wallZ = 1.5;
        double wallY = 3;

        if(playerFacing == BlockFace.NORTH || playerFacing == BlockFace.SOUTH) {
            // Place east to west (+x -x)
            wallX = 4 + 0.5;
            for(int x = -3; x < 4; x++) {
                for(int y = 0; y < 3; y++) {
                    Block b = placedBlock.getWorld().getBlockAt(placedBlock.getX() + x, placedBlock.getY() + y, placedBlock.getZ());
                    if(b.getType() == Material.AIR) {
                        b.setType(glass.getType(), true);
                        blocks.add(b);
                    }
                }
            }
        } else if(playerFacing == BlockFace.EAST || playerFacing == BlockFace.WEST) {
            wallZ = 4 + 0.5;
            for(int z = -3; z < 4; z++) {
                for(int y = 0; y < 3; y++) {
                    Block b = placedBlock.getWorld().getBlockAt(placedBlock.getX(), placedBlock.getY() + y, placedBlock.getZ() + z);
                    if(b.getType() == Material.AIR) {
                        b.setType(glass.getType(), true);
                        blocks.add(b);
                    }
                }
            }
        }

        new ShieldProcess(
                main,
                p,
                blocks,
                placedBlock.getLocation().add(0, 1.5, 0),
                glass.getType(),
                wallX, wallY, wallZ
        ).runTaskTimer(main,0,1L); //if too resource intensive increase the period (and divide maxTicks by period)

        p.getWorld().playSound(placedBlock.getLocation(), Sound.ENTITY_IRON_GOLEM_REPAIR, 1f, 0.5f);

    }

    private static class ShieldProcess extends BukkitRunnable {

        int ticks = 0;
        int maxTicks = (20*16);

        Player p;

        Main m;

        Material glassMat;

        ArrayList<Block> glassBlocks;
        ArrayList<Block> ignoreBlocks = new ArrayList<>();

        Location wallLoc;

        double wallX;
        double wallY;
        double wallZ;

        public ShieldProcess(Main m,Player p,ArrayList<Block> blocks,Location wallLoc,Material glass,double x,double y, double z) {
            this.m = m;
            this.p = p;
            this.glassBlocks = blocks;
            this.wallLoc = wallLoc;
            this.glassMat = glass;
            this.wallX = x;
            this.wallY = y;
            this.wallZ = z;
        }

        public void run() {
            if (ticks < maxTicks) {
                ticks++;
                handleWallLogic();
            } else {
                endWall();
            }
        }

        public void handleWallLogic() {

            handleIgnoreBlocks();

            for (Block b : glassBlocks) {
                if (ignoreBlocks.contains(b)) {
                    b.setType(Material.AIR);
                    continue;
                }
                b.setType(glassMat);
                MultipleFacing m = (MultipleFacing) b.getBlockData();
                for (BlockFace bf : (m.getAllowedFaces())) {
                    boolean touch = b.getRelative(bf).getType().isSolid();
                    m.setFace(bf,touch);
                    b.setBlockData(m);
                }
            }
        }

        public void handleIgnoreBlocks() {
            ignoreBlocks.clear();

            for (Entity e : wallLoc.getWorld().getNearbyEntities(wallLoc,wallX,wallY,wallZ)) {
                if (e instanceof Player pe) {
                    if (m.game.sameTeam(pe.getUniqueId(),p.getUniqueId())) {
                        addIgnoreBlock(pe.getLocation().add(0,0.5,0));
                        addIgnoreBlock(pe.getLocation().add(0,1.5,0));
                    }
                }
                if (e instanceof Projectile pj && pj.getShooter() instanceof Player pe) {
                    if (m.game.sameTeam(pe.getUniqueId(),p.getUniqueId())) {
                        addIgnoreBlock(pj.getLocation());
                    }
                }
            }


        }


        public void addIgnoreBlock(Location from) {
            Block closestBlock = null;
            for (Block b : glassBlocks) {
                if (closestBlock == null) {
                    closestBlock = b;
                    continue;
                }

                double toClosestLength = (closestBlock.getLocation().add(0.5,0.5,0.5).toVector().subtract(from.toVector()).lengthSquared());
                double fromLength = (b.getLocation().add(0.5,0.5,0.5).toVector().subtract(from.toVector()).lengthSquared());

                if (toClosestLength > fromLength) {
                    closestBlock = b;
                }

            }
            if (!ignoreBlocks.contains(closestBlock)) {
                ignoreBlocks.add(closestBlock);
            }
        }



        public void endWall() {
            for(Block b : glassBlocks)
                b.setType(Material.AIR);
            p.playSound(p.getLocation(), Sound.BLOCK_METAL_PLACE, 1f, 1f);
            this.cancel();
        }

    }

    boolean inHellfire = false;

    public void hellfire() {
        if (inHellfire || isOnCooldown("hellfire"))
            return;
        Location previousLoc = p.getLocation();
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 5f, 1f);
        inHellfire = true;

        int blockRange = 120;
        int solidBlocksPassed = 0;
        int maxSolidBlocks = 20;
        int yTeleport = 0;

        for (int i = 0; i < blockRange; i++) {
            Location targetLoc = previousLoc.clone().add(0,i,0);
            if (targetLoc.getY() >= p.getWorld().getMaxHeight()) {
                yTeleport = i;
                break;
            }

            if (targetLoc.getBlock().getType().isSolid()) {
                solidBlocksPassed++;
            } else if (solidBlocksPassed < maxSolidBlocks){
                yTeleport = i;
                solidBlocksPassed = 0;
            } else {
                break;
            }
        }

        Location ps = previousLoc.clone();
        ps.setY(ps.getY() + yTeleport);
        ps.setPitch(90);
//        ps.getChunk().load();   // make sure the chunk exists

        BukkitTask hellfireTeleportTask = new BukkitRunnable() {
            @Override
            public void run() {
                boolean ok = p.teleport(ps, PlayerTeleportEvent.TeleportCause.PLUGIN);
                main.getLogger().info("[Hellfire] teleported=" + ok + "  targetY=" + ps.getY());
                p.setInvulnerable(true);

                e.setItem(1,napalm);
//                e.getItem(1).setAmount(1);

                BukkitTask hellfireFallTask = new hellfireProcess(previousLoc).runTaskTimer(main,0L,1L);
                registerTask(hellfireFallTask);
            }
        }.runTaskLater(main, 8L);
        registerTask(hellfireTeleportTask);
    }

    public class hellfireProcess extends BukkitRunnable{
        int ticks = 0;
        Location prevLoc;

        public hellfireProcess(Location prevLoc) {
            this.prevLoc = prevLoc;
        }

        public void run() {
            for (Entity ent : p.getWorld().getNearbyEntities(p.getLocation(), 2, 2, 2)) {
                if (ent instanceof Player pe
                        && !ent.equals(p)
                        && !main.game.sameTeam(p.getUniqueId(),pe.getUniqueId())) {
                    explode();
                    return;
                }
            }
            p.setGliding(true);
            if (ticks > 20 * 14 || (p.getFallDistance() == 0 && ticks > 30)) {
                explode();
            } else {

                p.getWorld().spawnParticle(Particle.FLAME, p.getLocation(), 10,0,0,0,0.3);
                p.getWorld().spawnParticle(Particle.LARGE_SMOKE, p.getLocation(), 3,0.2,0.2,0.2,0.05);
//                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 1,0.2,0.2,0.2,0.05);
                p.setVelocity(p.getLocation().getDirection().multiply(1.75));
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_SHOOT, 5f, 0.5f);
            }
            ticks++;
        }

        public void explode() {
            Location explodeLoc = p.getLocation();
            p.setInvulnerable(false);
            p.teleport(prevLoc);
            p.setFallDistance(0f);
            p.setInvulnerable(false);
            p.setGliding(false);
            p.setFireTicks(0);
            e.setItem(1,hellfire);
            main.fakeExplode(p, explodeLoc, 12, 10, true, true, true, "hellfire");
            inHellfire = false;
            setCooldown("hellfire", 20, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
            this.cancel();
        }
    };

    public void exit() {
        setCooldown("gatling", 24, Sound.BLOCK_DISPENSER_DISPENSE);

        BukkitTask t = new BukkitRunnable() {
            @Override
            public void run() {

                if (gatlingProcess != null) gatlingProcess.cancel();

                gatlingProcess = null;

                p.removePotionEffect(PotionEffectType.SLOWNESS);
                p.playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_DEATH, 1f, 1f);

                for (Location loc : locs) loc.getBlock().setType(Material.AIR);
                locs.clear();
                Block below = p.getLocation().clone().add(0, -1, 0).getBlock();
                if (savedBlockType != null
                        && !main.restricted.contains(below.getType())) {
                    below.setType(savedBlockType);
                }
                savedBlockType = null;

                e.setItem(0,gun);
                e.setItem(1,hellfire);
                if (isOnCooldown("Shield")) {
                    e.setItem(2,spentShield);
                } else e.setItem(2,shield);

                setup  = false;
            }
        }.runTaskLater(main, 2L);
        registerTask(t);
    }

    public void bottleHit(ProjectileHitEvent event) {
        Location hitLoc = event.getEntity().getLocation().getBlock().getLocation().add(0.5,0.5,0.5);
        ArrayList<Block> fireBlocks = new ArrayList<Block>();
        for (Location l : Archer.sphere(hitLoc,5,false)) {
            if (Math.random() > 0.25) continue;
            Block b = l.getBlock();
            if (b.getType().isAir()) continue;

            Block upBlock = b.getRelative(BlockFace.UP);

            if (upBlock.getType().isAir()) {
                upBlock.setType(Material.FIRE);
                fireBlocks.add(upBlock);
            }
        }

        p.getWorld().spawnParticle(Particle.FLAME,hitLoc,30,2,2,2,0.4);
        p.getWorld().spawnParticle(Particle.LARGE_SMOKE,hitLoc,10,2,2,2,0.2);
        p.getWorld().spawnParticle(Particle.LAVA,hitLoc,20,2,2,2,0.3);

        p.getWorld().playSound(hitLoc,Sound.ENTITY_BLAZE_HURT,3.0f,1.2f);
        p.getWorld().playSound(hitLoc,Sound.ENTITY_BLAZE_SHOOT,3.0f,0.8f);

        for (Entity e : p.getWorld().getNearbyEntities(hitLoc,4,4,4)) {
            e.setFireTicks(80);
            if (e instanceof Player pe) {
                main.combatTracker.setHealth(pe,pe.getHealth() - 3,p,"napalm");
            }
        }

        new BukkitRunnable() {
            public void run() {
                for (Block b : fireBlocks) {
                    if (b.getType() == Material.FIRE) {
                        b.setType(Material.AIR);
                    }
                }
                p.getWorld().playSound(hitLoc,Sound.BLOCK_FIRE_EXTINGUISH,3.0f,1.2f);
            }
        }.runTaskLater(main,90L);

    }

}