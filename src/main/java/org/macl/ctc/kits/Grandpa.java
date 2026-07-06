package org.macl.ctc.kits;

import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.macl.ctc.Main;

import java.awt.*;
import java.util.ArrayList;
import java.util.Random;

public class Grandpa extends Kit {

    private int maxAmmo = 2;
    private int reloadTime = 3;
    private int ammo = maxAmmo;

    private boolean finalShotBoosted = false;
    public boolean finalShotCanExplode = false;

    private final int maxPepperAmmo = 3;
    private int pepperReloadTime = 9;
    private int pepperAmmo = maxPepperAmmo;

    ItemStack shotgun = newItem(Material.PRISMARINE_SHARD, ChatColor.GRAY + "Slugged Shotgun", 2);
    ItemStack peppergun = newItem(Material.PRISMARINE_CRYSTALS, ChatColor.YELLOW + "Peppergun", 3);

    public boolean fallImmune = false;

    public Grandpa(Main main, Player p, KitType type) {
        super(main, p, type);
        p.removePotionEffect(PotionEffectType.SPEED);
        e = p.getInventory();

        // Set up player's inventory
        e.setHelmet(newItem(Material.IRON_HELMET, ChatColor.DARK_GREEN + "Veteran's Helmet"));
        e.addItem(shotgun);
        e.addItem(peppergun);
        e.setItem(2, newItem(Material.HONEY_BOTTLE, ChatColor.GOLD + "Booze"));
        e.setItem(3, newItem(Material.LADDER, "Old Ladder", 24));
        giveWool();
        giveWool();
        setHearts(20); // Set health since you didn't specify | ( ͡° ͜ʖ ͡°)
    }

    public void shootGun() {
        if (ammo > 0) {
            ammo--;

            setItemAmount(0,ammo);

            p.getWorld().playSound(p.getLocation(),
                    Sound.ENTITY_FIREWORK_ROCKET_BLAST,
                    1f, 0.1f);

            Vector dir = p.getLocation().getDirection().multiply(0.4);

            Projectile bullet;

            if ((finalShotBoosted && ammo == 1)) {
                e.getItem(0).addUnsafeEnchantment(Enchantment.FIRE_ASPECT,0);
            }

            if (!(finalShotBoosted && ammo == 0)) {
                bullet = p.getWorld()
                        .spawn(p.getEyeLocation().subtract(0, 0.5, 0),
                                SmallFireball.class);
            } else {
                finalShotBoosted = false;
                finalShotCanExplode = true;
                 bullet = p.getWorld()
                         .spawn(p.getEyeLocation().subtract(0, 0.5, 0),
                                 Fireball.class);
                p.getWorld().playSound(p.getLocation(),
                        Sound.ENTITY_BLAZE_SHOOT,
                        1f, 0.2f);
            }

            bullet.setShooter(p);
            dir.multiply(1.6);
            bullet.setVelocity(dir.clone().multiply(5.5));

//            bullet.setIsIncendiary(p.hasPotionEffect(PotionEffectType.DARKNESS));

            new BukkitRunnable() {
                int ticks = 0;
                final int duration = 12;

                public void run() {
                    ticks++;
                    if (ticks > 2) {
                        p.getWorld().spawnParticle(
                                Particle.LARGE_SMOKE,
                                bullet.getLocation(),
                                6,
                                0, 0, 0,
                                0.0,
                                null,
                                true);
                    }

                    if (ticks >= duration) {
                        bullet.remove();
                        cancel();
                    }
                }

            }.runTaskTimer(main, 0, 1L);

            // knockback
            double airLaunchModifier = -0.175;
            if (!p.isOnGround()) {
                airLaunchModifier = -1.25;
            }

            Vector r = dir.multiply((airLaunchModifier));
            r.setY(r.getY() * 1.2);
            p.setVelocity(p.getVelocity().add(r));

            fallImmune = true;

            if (ammo == 0) {
                e.setItem(0, newItem(Material.LIGHT_GRAY_DYE,
                        ChatColor.RED + "Reload"));
                reloadGun();
            }
        } else if (!isOnCooldown("Shotgun")) {
            reloadGun();
        }
    }

    private void reloadGun() {
        // just fire off one Kit cooldown
        e.setItem(0, newItem(Material.GRAY_DYE,
                ChatColor.GREEN + "Reloading..."));
        p.playSound(p.getLocation(),
                Sound.ITEM_ARMOR_EQUIP_LEATHER,
                1f, 1f);

        int rng = (int) Math.round(Math.random());
        int extraReloadTime = 0;

        if (rng == 0) {
            extraReloadTime = 2;
        }

//        main.broadcast(Integer.toString(extraReloadTime));

        setCooldown("Shotgun", reloadTime + extraReloadTime, Sound.ITEM_ARMOR_EQUIP_IRON, () -> {
            // onComplete!
            ammo = maxAmmo;
            e.setItem(0, shotgun);
            p.playSound(p.getLocation(),
                    Sound.ITEM_ARMOR_EQUIP_IRON,
                    1f, 0.5f);
        });
    }

    public void shootPepper() {
        if (pepperAmmo > 0) {
            pepperAmmo--;

            setItemAmount(1,pepperAmmo);

            p.getWorld().playSound(p.getLocation(),
                    Sound.ENTITY_FIREWORK_ROCKET_BLAST,
                    1f, 1.5f);

            ArrayList<ShulkerBullet> shots = new ArrayList<ShulkerBullet>();

            Vector dir = p.getLocation().getDirection();
            Vector randDir = dir;
            for (int i = 0; i < 12; ++i) {
                ShulkerBullet bullet = p.getWorld()
                        .spawn(p.getEyeLocation().subtract(0, 0.5, 0),
                                ShulkerBullet.class);
                bullet.setGravity(false);
                shots.add(bullet);
                bullet.setShooter(p);
                bullet.setVelocity(randDir.multiply(1.5));
                randDir = randomizeVectorAngle(dir,0.2);


            }

            new BukkitRunnable() {
                int ticks = 0;
                final int duration = 8;

                public void run() {
                    ticks++;
                    if (ticks >= duration) {
                        for (ShulkerBullet b : shots) {
                            b.remove();
                        }
                        cancel();
                    }
                }

            }.runTaskTimer(main, 0, 1);

            // knockback
            Vector r = dir.multiply((-0.1));
            p.setVelocity(r);
            fallImmune = true;

            if (pepperAmmo == 0) {
                e.setItem(1, newItem(Material.LIGHT_GRAY_DYE,
                        ChatColor.RED + "Reloading..."));
                reloadPepper();
            }
        } else if (!isOnCooldown("Peppergun")) {
            reloadPepper();
        }
    }

    private void reloadPepper() {
        // just fire off one Kit cooldown
        e.setItem(1, newItem(Material.LIGHT_GRAY_DYE,
                ChatColor.GREEN + "Repeppering..."));
        p.playSound(p.getLocation(),
                Sound.ITEM_ARMOR_EQUIP_LEATHER,
                1f, 1.5f);

        setCooldown("Peppergun", pepperReloadTime, Sound.ITEM_ARMOR_EQUIP_CHAIN, () -> {
            pepperAmmo = maxPepperAmmo;
            e.setItem(1, peppergun);
            p.playSound(p.getLocation(),
                    Sound.ITEM_ARMOR_EQUIP_CHAIN,
                    1f, 1.5f);
        });
    }

    public void drinkBooze() {
        if (isOnCooldown("Booze")) return;

        e.setItem(2, newItemEnchanted(
                Material.GLASS_BOTTLE,
                ChatColor.DARK_RED + "Empty Flask",
                Enchantment.SHARPNESS, 3
        ));

        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20 * 8, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 15, 2));
        p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 1, 0));

        cancelCooldown("Shotgun");
        ammo = maxAmmo + 1;
        e.setItem(0, shotgun);
        setItemAmount(0,3);
        finalShotBoosted = true;


        cancelCooldown("Peppergun");
            pepperAmmo = maxPepperAmmo;
            e.setItem(1, peppergun);

        p.playSound(p.getLocation(),
                Sound.ENTITY_GENERIC_DRINK,
                1f, 0.2f);

        if (p.getHealth() <= 5) {
            p.setHealth(Math.max(0.5, p.getHealth() - 4));
        } else {
            p.damage(5);
        }

        setCooldown("Booze", 18, Sound.ENTITY_PLAYER_BURP, () -> {
            e.setItem(2, newItem(
                    Material.HONEY_BOTTLE,
                    ChatColor.GOLD + "Booze"
            ));
            finalShotBoosted = false;
        });
    }

    public void setItemAmount(int index, int amount) {
        if (p.getInventory().getItem(index) != null) {
            p.getInventory().getItem(index).setAmount(amount);
        }
    }

    public static Vector randomizeVectorAngle(Vector vec, double maxAngleDeg) {
        if (vec.length() == 0) return vec.clone();

        Random random = new Random();

        Vector direction = vec.clone().normalize();

        direction.rotateAroundY(-maxAngleDeg + ((maxAngleDeg * 2) * random.nextDouble()));
        direction.rotateAroundZ(-maxAngleDeg + ((maxAngleDeg * 2) * random.nextDouble()));
        direction.rotateAroundX(-maxAngleDeg + ((maxAngleDeg * 2) * random.nextDouble()));

        return direction;
    }
}