package org.macl.ctc.kits;

import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.macl.ctc.Main;

import java.util.Objects;

public class Spy extends Kit {

    Location detonate;

    boolean invis = true;

    boolean switching = false;

    boolean shankActive = false;

    boolean resetShank = false;

    int shankProgress = 0;

    int shankMaxProgress = 30;

    int shankLevel = 0;

    int shankMaxLevel = 3;

    pearlDismountProcess pearlProcess;

    ItemStack poisonDagger = newItem(Material.IRON_HOE, ChatColor.GREEN + "Poison Dagger");

    public Location getDetonate() {
        return detonate;
    }

    public void setDetonate(Location detonate) {
        this.detonate = detonate;
    }

    public void addDetonate() {
        if(!(main.getKits().get(p.getUniqueId()) != null && main.getKits().get(p.getUniqueId()) instanceof Spy))
            return;
        p.getInventory().setItem(3, newItem(Material.BLAZE_ROD, ChatColor.DARK_GREEN + "Detonator"));
        int rod = p.getInventory().first(Material.BLAZE_ROD);
        p.getInventory().getItem(rod).addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 1);
//        setHearts(12);
    }

    public Spy(Main main, Player p, KitType type) {
        super(main, p, type);
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 99999999, 99999));
        p.getInventory().addItem(newItem(Material.DIRT,"Bert"));
        p.getInventory().addItem(poisonDagger);
        p.getInventory().addItem(newItem(Material.ENDER_PEARL, ChatColor.DARK_PURPLE + "Cloak", 2));
        //p.getInventory().addItem(newItem(Material.BLAZE_ROD, ChatColor.DARK_GREEN + "Detonator"));
        p.getInventory().addItem(newItem(Material.RED_CANDLE, ChatColor.DARK_RED + "Remote Explosive", 1));
        giveWool();
        giveWool();
        p.getInventory().remove(Material.DIRT);
        regenItem("cloak", newItem(Material.ENDER_PEARL, ChatColor.DARK_PURPLE + "Cloak (Teleport)"), 30, 2, 2);
        BukkitTask inv = new spyInvis().runTaskTimer(main, 0L, 1L);
        this.registerTask(inv);
        BukkitTask shank = new shankUpgrade().runTaskTimer(main,0L,1L);
        this.registerTask(shank);
        p.getInventory().remove(Material.DIAMOND_PICKAXE);
        setHungerOnHit(15);
        setHearts(12);
    }

    public void detonate() {
        if(detonate != null) {
            p.getInventory().remove(Material.BLAZE_ROD);
            BukkitTask detonateTimer = new detonateTimer().runTaskTimer(main, 0, 1L);
            registerTask(detonateTimer);
        }
    }

    public class pearlDismountProcess extends BukkitRunnable {

        boolean landed = false;

        int landedLifetime = 200;
        int currentLifetime = 0;
        double maxRad = 1.0;
        double minRad = 0.25;
        double currentRad = 0.25;
        boolean in = false;
        Location landLoc;

        public void run() {
            if (isOnGround() || landed) {
                if (!landed) {
                    landed = true;
                    landLoc = p.getLocation();
//                    landLoc.add(0,-1,0);
                    p.getWorld().playSound(landLoc,Sound.ENTITY_PLAYER_TELEPORT,0.5f,0.5f);
                    if (p.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
                        p.removePotionEffect(PotionEffectType.SLOW_FALLING);
                    }
                } else {
                    if (currentLifetime < landedLifetime) {
                        currentLifetime++;
                        createLandingParticles();
                    } else {
                        this.cancel();
                    }
                }
            }
        }

        public boolean isOnGround() {
//            return p.getLocation().subtract(0, -1, 0).getBlock().getType() != Material.AIR;
            return p.isOnGround();
        }

        public void createLandingParticles() {
            createRingParticles(10,0.25,landLoc);

            if (in) {
                if (currentRad > minRad) {
                    currentRad -= 0.025;

                } else {
                    in = false;
                }
            } else {
                if (currentRad < maxRad) {
                    currentRad += 0.025;

                } else {
                    in = true;
                }
            }

            createRingParticles(10,currentRad,landLoc);

        }

        public void createRingParticles(int particles, double rad, Location loc) {

            for (int i = 0; i < particles; i++) {

                double theta = Math.random() * 2 * Math.PI;
                double x = rad * Math.cos(theta);
                double z = rad * Math.sin(theta);

                Location particleLocation = loc.clone().add(x, 0.1, z);

                Particle.DustOptions teamDust = new Particle.DustOptions(Color.fromRGB(255, 0, 255), 1);;
                Objects.requireNonNull(loc.getWorld()).spawnParticle(
                        Particle.DUST, particleLocation, 1,0,0,0,0.0,teamDust);
            }
        }


    }

    public void onPearlDismount() {
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,999999,0,true,false,true));

        if (pearlProcess != null) {
            pearlProcess.cancel();
        }

        pearlProcess = new pearlDismountProcess();

        runTaskTimer(pearlProcess,0,1L);

    }

    public void swapToDagger() {
        if (!switching) {
            switching = true;
            shankActive = false;
            p.getWorld().playSound(p.getLocation(),Sound.ITEM_ARMOR_EQUIP_LEATHER,1.0f,0.9f);
            p.getInventory().setItem(1, poisonDagger);
        }
    }

    public void swapToShank() {
        if (!switching) {
            switching = true;
            shankActive = true;
            p.getWorld().playSound(p.getLocation(),Sound.ITEM_ARMOR_EQUIP_GOLD,1.5f,0.9f);
            p.getInventory().setItem(1, getShank());
        }
    }

    public boolean isHoldingShank() {
        if (p.getInventory().getHeldItemSlot() == 1) {
            return e.getItem(1).getType() != Material.IRON_HOE;

        } else {
            return false;
        }
    }

    public ItemStack getShank() {

        Material m = null;

        ChatColor tier = null;

        String name = "Hidden Shank";

        switch (shankLevel) {
            case 0:
                m = Material.WOODEN_SHOVEL;
                tier = ChatColor.DARK_GRAY;
                break;
            case 1:
                m = Material.STONE_SWORD;
                tier = ChatColor.GREEN;
                break;
            case 2:
                m = Material.IRON_SWORD;
                tier = ChatColor.YELLOW;
                break;
            case 3:
                m = Material.DIAMOND_SWORD;
                tier = ChatColor.DARK_RED;
                name = ChatColor.BOLD + "Hidden Shank";
                break;
        }

        ItemStack shank = newItem(m,tier + name);

        double progressRatio = (double) shankProgress / shankMaxProgress;

        if (shank.getItemMeta() instanceof Damageable d) {
            int damage = (int) (shank.getType().getMaxDurability() * (1 - progressRatio));
            d.setDamage(damage);
            shank.setItemMeta(d);
        }

        return shank;
    }

    public void uninvis() {
        this.invis = false;
    }

    public void reshank() {
        this.resetShank = true;
    }

    public class shankUpgrade extends BukkitRunnable {

        public void run() {

            if (resetShank) {
                shankLevel = 0;
                shankProgress = 0;
                resetShank = false;
            }

            if (shankActive) {
                updateShankItem();
            }

            if (switching) {
                switching = false;
            }

            if (isHoldingShank()) {
                if (shankLevel <= shankMaxLevel) {
                    if (shankProgress < shankMaxProgress) {
                        shankProgress++;
                    } else if (shankLevel != shankMaxLevel) {
                        shankProgress = 0;
                        shankLevel++;
                        p.playSound(p.getLocation(),Sound.ENTITY_EXPERIENCE_ORB_PICKUP,0.5f,(float) (0.33 + (0.33 * shankLevel)));
                    }

                } else {
                    if (shankProgress < shankMaxProgress) {
                        shankProgress++;
                    }
                }
            } else {
                if (shankLevel >= 0) {
                    if (shankProgress > 0) {
                        shankProgress--;
//                        shankProgress--;
                    } else if (shankLevel != 0) {
                        shankProgress = shankMaxProgress;
                        shankLevel--;
//                        shankLevel--;
//                        if (shankLevel < 0) shankLevel = 0;
                        p.playSound(p.getLocation(),Sound.ENTITY_EXPERIENCE_ORB_PICKUP,0.5f,(float) (0.20 + (0.20 * shankLevel)));
                    }

                }
            }

        }

        public void updateShankItem() {
            p.getInventory().setItem(1, getShank());
        }


    }

    public class spyInvis extends BukkitRunnable {
        int ticks = 0;

        public void run() {
            if(!p.isOnline() || p.isDead() || (main.getKits().get(p.getUniqueId()) == null || !(main.getKits().get(p.getUniqueId()) instanceof Spy))) {
                this.cancel();
                return;
            }

            if (!isHoldingShank()) {
                if (invis && ticks >= 60) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 999999, 99999));
                } else if (!invis) {
                    p.removePotionEffect(PotionEffectType.INVISIBILITY);
                    ticks = 0;
                    invis = true;
                }
            } else {
                if (p.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                    p.removePotionEffect(PotionEffectType.INVISIBILITY);
                }
            }

            ticks++;
        }
    }

    public class detonateTimer extends BukkitRunnable {
        int timer = 0;

        public void run() {
            if(detonate == null) {
                this.cancel();
                return;
            }
            if(timer == 35) {
                main.fakeExplode(p, getDetonate(), 20, 6, true, false,true, "spy",0.5f);
                p.getWorld().createExplosion(getDetonate(), 2f, false, true);
                this.cancel();
                detonate = null;

                BukkitTask add = new BukkitRunnable() {

                    int count = 15;
                    public void run() {
                        count--;
                        p.setLevel(count);
                        if(count == 0) {
                            this.cancel();
                            p.getInventory().setItem(3,newItem(Material.RED_CANDLE, ChatColor.DARK_RED + "Remote Explosive", 1));
                        }
                    }
                }.runTaskTimer(main, 0L, 20L);
                registerTask(add);
                return;
            }
            detonate.getWorld().playSound(detonate, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, (float) (0.1*timer));
            timer++;
        }
    }
}