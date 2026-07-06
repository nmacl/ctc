package org.macl.ctc.kits;

import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;
import org.bukkit.*;
import org.bukkit.Color;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.macl.ctc.Main;

import java.util.*;

public class Runner extends Kit {
    Material wool = (main.game.redHas(p)) ? Material.RED_WOOL : Material.BLUE_WOOL;
    ItemStack sword = newItem(Material.STONE_SWORD, ChatColor.YELLOW + "Block Run");
    ItemStack field = newItem(Material.CLOCK, ChatColor.WHITE + "Polar Deflection Field");
    ItemStack dash = newItem(Material.FEATHER,ChatColor.GOLD + "Dash",2);

    Dash dashProcess;

    public boolean damaged = false;

    public Runner(Main main, Player p, KitType type) {
        super(main, p, type);
        e.setItem(0, sword);
        p.getInventory().setItem(1,field);
        p.getInventory().setItem(2,dash);
//        p.getInventory().setItem(3, newItem(Material.IRON_INGOT, ChatColor.GRAY + "Platform"));
        e.setLeggings(new ItemStack(Material.IRON_LEGGINGS, 1));
        e.setBoots(new ItemStack(Material.LEATHER_BOOTS, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999999, 0));
        regenItem("Dash", dash, 8, 2, 2);
        giveWool();
        giveWool();
        setHearts(16);
    }

    ArrayList<Block> blocks = new ArrayList<Block>();
    public void blockRun() {
        if (!isOnCooldown("Block Run")) {
            setCooldown("Block Run", 18, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);

            // grab the BukkitTask and register it
            BukkitTask runTask = new BukkitRunnable() {
                int timer = 0;
                int exp = 50;
                final int rad = 1;

                @Override
                public void run() {
                    timer++;
                    // if the player no longer has a kit, stop
                    if (main.getKits().get(p.getUniqueId()) == null) {
                        startCleanup();
                        this.cancel();
                        return;
                    }
                    if (timer < 5 * 20) {
                        exp++;
                        p.playSound(p,Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.2f,exp * 0.01f);
                        Vector v = getRealVelocity();
                        Vector hv = new Vector(v.getX(),0.01,v.getZ()).normalize();
                        for (int i = -rad; i <= rad; i++) {
                            for (int j = -1; j <= 1; j++) {
                                for (int z = -rad; z <= rad; z++) {
                                    Block b = p.getLocation().add(i + hv.getX(), -1, z + hv.getZ()).getBlock();
                                    if (b.getType() == Material.AIR) {
                                        b.setType(wool);
                                        blocks.add(b);
                                    }
                                }
                            }
                        }
                    } else {
                        // clean up
                        startCleanup();
                        this.cancel();
                    }
                }

                public void startCleanup() {
                    ArrayList<Block> cleanBlocks = (ArrayList<Block>) blocks.clone();
                    ArrayList<Block> queuedBlocks = new ArrayList<Block>();

                    World w = p.getWorld();

                    blocks.clear();

                    new BukkitRunnable() {
                        @Override
                        public void run() {

                            if (cleanBlocks.isEmpty()) {
                                this.cancel();
                                return;
                            }

                            for (int i = 0; i <= 5; i++) {
                                int index = (cleanBlocks.size() - 1) - i;
                                if (index < 0) break;
                                Block qB = cleanBlocks.get(index);
                                if (qB != null) {
                                    queuedBlocks.add(qB);
                                }
                            }

                            Location l = queuedBlocks.get((0)).getLocation();
                            w.playSound(l,Sound.ENTITY_ITEM_PICKUP,2.5f,0.5f);

                            for (Block b : queuedBlocks) {
                                if (b.getType() == wool) b.setType(Material.AIR);
                                cleanBlocks.remove(b);
                            }

                            queuedBlocks.clear();

                        }
                    }.runTaskTimer(main,0L,1L);
                }

            }.runTaskTimer(main, 0L, 1L);

//            registerTask(runTask);
        }
    }

    int ticks = 0;
    ArrayList<Entity> es = new ArrayList<>();

    public void polarField() {
        if(isOnCooldown("Deflection Field"))
            return;
        setCooldown("Deflection Field", 19, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, () ->
                p.getInventory().setItem(1,field));

        p.getInventory().setItem(1,newItem(Material.FIRE_CHARGE,ChatColor.RED + "Device Overheated!"));

        damaged = true;

        p.getWorld().playSound(p.getLocation(),Sound.BLOCK_TRIAL_SPAWNER_SPAWN_MOB,1.5f,1.2f);
        p.getWorld().playSound(p.getLocation(),Sound.BLOCK_BEACON_POWER_SELECT,1.5f,1.4f);

        // Register the task properly
        BukkitTask fieldTask = new BukkitRunnable() {
//            @Override

            double rad = 0.0;

            public void run() {
                ArrayList<Projectile> proj = new ArrayList<Projectile>();

                if(ticks > 8*20 || p.isDead()) {
                    ticks = 0;
                    this.cancel();
                    es.clear();
                    return;
                }

                if (ticks < 150) {
                    if (rad < 2.5) {
                        rad += 0.25;
                    } else rad = 2.5;

                } else {
                    if (rad > 0) {
                        rad -= 0.25;
                    } else rad = 0;
                }

                for(Entity e : p.getNearbyEntities(rad, rad, rad)) {
                    if(es.contains(e)) continue;

                    if(e instanceof Projectile) {
                        es.add(e);
                        Projectile projectile = (Projectile) e;
                        if(!(projectile instanceof FishHook || projectile instanceof FishingHook)) {
                            projectile.setBounce(true);
                            projectile.setShooter(null);
                        }
                        if(!proj.contains(projectile)) {
                            Vector v = e.getVelocity();
                            v.multiply(-0.67f);
                            v.setY(Math.abs(v.getY()));
                            projectile.setVelocity(v);
                        }
                    }
                }

                if (damaged) {
                    damaged = false;
                    // Handle all other entities (players, mobs, etc.)

                    p.getWorld().playSound(p.getLocation(),Sound.ENTITY_WIND_CHARGE_WIND_BURST,0.8f,0.85f);
                    p.getWorld().playSound(p.getLocation(),Sound.BLOCK_GLASS_BREAK,1.2f,1.5f);

                    p.getWorld().spawnParticle(Particle.GUST,p.getLocation().add(0,1,0),3,null);
                    p.getWorld().spawnParticle(Particle.FLASH,p.getLocation().add(0,1,0),1,Color.WHITE);

                    for(Entity e : p.getNearbyEntities(2.5, 2.5, 2.5)) {
                        if(es.contains(e)) continue;
                        // Calculate radial direction from player to target
                        Vector direction = e.getLocation().toVector().subtract(p.getLocation().toVector()).normalize();

                        // Apply radial knockback with lift
                        Vector knockback = direction.multiply(0.8f);
                        knockback.setY(0.3f); // Add upward lift

                        e.setVelocity(knockback.multiply(1.4));
                        if (e instanceof LivingEntity le) {
                            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,30,2));
                        }

                        // Spawn particle trail on the knocked back entity
                        new BukkitRunnable() {
                            int trailTicks = 0;
                            @Override
                            public void run() {
                                if(trailTicks > 20 || e.isDead()) { // Trail for 1 second
                                    this.cancel();
                                    return;
                                }
                                e.getWorld().spawnParticle(Particle.CRIT, e.getLocation().add(0, 1, 0), 3, 0.2, 0.2, 0.2, 0.1);
                                trailTicks++;
                            }
                        }.runTaskTimer(main, 0L, 1L);
                    }
                }


                double rotX = (double) ticks / 25;
                double rotY = (double) ticks / 25;
                double rotZ = (double) ticks / 25;

                Location loc = p.getLocation();
                createRingParticles(30,rad,loc,rotX,rotY,rotZ* 0);
                createRingParticles(30,rad,loc,(rotY * 1.5) + 15,(rotZ * 1.5 * 0) -15 ,(rotX * 1.5) + 7);
                createRingParticles(30,rad,loc,(rotZ / 1.5) - 25 ,(rotX / 1.5) - 20,(rotY / 1.5 * 0) - 10);
                ticks++;



            }

            public void createRingParticles(int particles, double rad, Location loc,double rotX, double rotY, double rotZ) {
                for (int i = 0; i < particles; i++) {
                    //createRingParticles has been used so many times it should probably be turned into a main function
                    //instead of being created individually on a kit. I'm not going to do that right now. Lol!
                    double theta = Math.random() * 2 * Math.PI;
                    double x = rad * Math.cos(theta);
                    double z = rad * Math.sin(theta);
//                    double y = rad * Math.tan(theta);

                    Vector particleRotation = new Vector(x,0,z);
                    particleRotation.rotateAroundX(rotX);
                    particleRotation.rotateAroundZ(rotZ);
                    particleRotation.rotateAroundY(rotY);

                    Location particleLocation = loc.clone().add(particleRotation).add(0,0.5,0);

                    Vector speed = getRealVelocity();
//                    Vector posOffset = p.getVelocity().normalize().multiply(1.75);
//                    posOffset.setY(0);
//                    particleLocation.add(posOffset);


                    Objects.requireNonNull(loc.getWorld()).spawnParticle(
                            Particle.ELECTRIC_SPARK, particleLocation, 0,speed.getX(),speed.getY(),speed.getZ(),1.5);
                }
            }

        }.runTaskTimer(main, 0L, 1L);

        // Register the task
        registerTask(fieldTask);
    }

    /* keep this alongside your other per‑ability fields if you later decide
       you want to remember which blocks were placed so they can be removed */
    private final List<Block> platformBlocks = new ArrayList<>();

    /**
     * Spawns a 5×5 concrete platform two blocks beneath the player, then
     * starts a 12‑second cooldown (“Platform”).
     */
    public void platform() {
        // 1. cooldown gate
        if (isOnCooldown("Platform")) return;
        setCooldown("Platform", 12, Sound.BLOCK_AMETHYST_BLOCK_PLACE);

        // 2. make sure the player won’t take fall damage immediately afterwards
        p.setFallDistance(0);

        // 3. build the platform
        Location base = p.getLocation();                     // current position

        for (int x = -2; x <= 2; x++) {                      // -2 … +2  (5 blocks)
            for (int z = -2; z <= 2; z++) {
                Block b = base.clone().add(x, -2, z).getBlock();
                Material current = b.getType();

                // --- new safety check -------------------------------------------------
                if (main.restricted.contains(current)) {     // don’t touch restricted materials
                    continue;
                }
                // ---------------------------------------------------------------------

                if (current == Material.AIR) {               // place only into empty space
                    if (main.game.redHas(p)) {
                        b.setType(Material.RED_CONCRETE, false);
                    } else if (main.game.blueHas(p)) {
                        b.setType(Material.BLUE_CONCRETE, false);
                    } else {
                        b.setType(Material.BLACK_CONCRETE, false);
                    }
                }
            }
        }
    }

    public void dash() {
        int dash = p.getInventory().first(Material.FEATHER);
        p.getInventory().getItem(dash).setAmount(p.getInventory().getItem(dash).getAmount() - 1);

        p.setFallDistance(0);

        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 15, 0));

        double boost = 1.1;

        if (p.isOnGround()) {
            boost = 1.75;
        }

        if (dashProcess != null) {
            dashProcess.cancel();
        }

        Vector dir = p.getEyeLocation().getDirection().setY(0.01).normalize().multiply(boost);
        dir.setY(0.2);
        p.setVelocity(dir);

        p.getWorld().playSound(p.getLocation(),Sound.ENTITY_HORSE_BREATHE,1.8f,0.5f);
        p.getWorld().playSound(p.getLocation(),Sound.ENTITY_HORSE_BREATHE,1.8f,1.25f);
        p.getWorld().playSound(p.getLocation(),Sound.ENTITY_BREEZE_SHOOT,1.8f,0.65f);
        p.getWorld().playSound(p.getLocation(),Sound.ENTITY_WIND_CHARGE_WIND_BURST,1.8f,0.65f);

        p.getWorld().spawnParticle(Particle.GUST,p.getLocation().add(0,1,0),3,null);

        p.setFreezeTicks(40);

        Dash d = new Dash();
        dashProcess = d;
        runTaskTimer(d,0L,1L);

    }

    public class Dash extends BukkitRunnable {
        int ticks = 0;

        final Set<UUID> dashThrough = new HashSet<>();

        public void run() {
            ticks++;

            if (ticks < 15) {
                p.getWorld().spawnParticle(Particle.CLOUD,p.getLocation().add(0,1.0,0),3,0.2,0.6,0.22,0.0);
            } else{
                this.cancel();
            }

            for (Entity e : p.getNearbyEntities(1.5, 0.5, 1.5)) {
                if (e instanceof Player target
                        && !target.getUniqueId().equals(p.getUniqueId())
                        && dashThrough.add(target.getUniqueId())) {

                    p.getWorld().playSound(target.getLocation(),Sound.ENTITY_BREEZE_SHOOT,1.0f,0.85f);
                    p.getWorld().playSound(target.getLocation(),Sound.ENTITY_WIND_CHARGE_WIND_BURST,1.0f,0.85f);

                    p.getWorld().spawnParticle(Particle.GUST,target.getLocation().add(0,0,0),1,null);

                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,15,0));

                    target.setVelocity(new Vector(0,0.9,0));

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            ticks++;

                            if (ticks < 15) {
                                target.getWorld().spawnParticle(Particle.CLOUD,target.getLocation(),2,0.4,0.4,0.4,0.0);
                            } else {
                                this.cancel();
                            }
                        }
                    }.runTaskTimer(main,0L,1L);

                }
            }
        }
    }

}
