package org.macl.ctc.kits;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.alchemy.Potion;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.macl.ctc.Main;

import java.util.*;

public class Fisherman extends Kit {

    Entity salmon;

    ItemStack codSniper = newItem(Material.COD, ChatColor.LIGHT_PURPLE + "Cod Sniper");
    ItemStack pufferfishBomb = newItem(Material.PUFFERFISH, ChatColor.YELLOW + "Pufferfish Bomb");
    ItemStack salmonrang = newItem(Material.SALMON, ChatColor.DARK_PURPLE + "Salmonrang");
    ItemStack swap = newItem(Material.BONE_MEAL,ChatColor.WHITE + "" + ChatColor.BOLD + "Swap!");

    public Fisherman(Main main, Player p, KitType type) {
        super(main, p, type);
        e.addItem(newItem(Material.FISHING_ROD, ChatColor.GOLD + "Fishing Rod"));
        e.setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
        e.setHelmet(new ItemStack(Material.TURTLE_HELMET));
        e.setBoots(new ItemStack(Material.LEATHER_BOOTS));
        e.addItem(codSniper);
        e.addItem(pufferfishBomb);
        e.addItem(salmonrang);
        giveWool();
        giveWool();
        setHearts(18);
    }

    public void codSniper() {
        if (isOnCooldown("cod")) return;
        setCooldown("cod", 4, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);

        final Set<UUID> hitThisShot = new HashSet<>();
        Entity cod = p.getWorld().spawnEntity(p.getEyeLocation(), EntityType.COD);

        p.getWorld().playSound(p.getLocation(),Sound.ENTITY_BREEZE_IDLE_AIR,1.0f,1.5f);
        p.getWorld().playSound(p.getLocation(),Sound.ENTITY_COD_DEATH,1.0f,0.8f);

        // Pre-compute colour once so it doesn’t flip every tick
        Particle.DustOptions dust =
                main.game.redHas(p)
                        ? new Particle.DustOptions(Color.fromRGB(255, 0, 0), 2.5F)
                        : new Particle.DustOptions(Color.fromRGB(0, 0, 255), 2.5F);

        Vector velocity = p.getLocation().getDirection().multiply(2.5);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                // Lifetime safeguard
                if (!cod.isValid() || ++ticks > 100) {
                    cod.remove();
                    cancel();
                    return;
                }

                // Push the fish forward
                cod.setVelocity(velocity);

                // ✨ trail particles
                if (ticks > 2) {
                    cod.getWorld().spawnParticle(
                            Particle.DUST,
                            cod.getLocation(),
                            Math.min((ticks * 2) - 2 ,10),
                            0.35, 0.2, 0.35,
                            0.2,
                            dust,
                            true
                    );
                }

                // Collision check
                for (Entity e : cod.getNearbyEntities(0.6, 0.6, 0.6)) {
                    if (e instanceof Player target
                            && !target.getUniqueId().equals(p.getUniqueId())
                            && hitThisShot.add(target.getUniqueId())) {

                        double airborneDamage = 0;
                        if (main.kit.kits.get(target.getUniqueId()) instanceof Kit k) {
                            if (!k.isOnGround()) airborneDamage = 4;
                        }
                        // first drop them near-zero but never kill
                        main.combatTracker.setHealth(target, target.getHealth() - (6 + airborneDamage), p, "cod sniper");

                        PotionEffect unluck = target.getPotionEffect(PotionEffectType.UNLUCK);

                        if (unluck != null && unluck.getAmplifier() == 1) {
                            LightningStrike l = p.getWorld().strikeLightning(target.getLocation());
                            l.setFlashes(2);
                            l.setCausingPlayer(p);

                            if (target.hasPotionEffect(PotionEffectType.GLOWING)) {
                                target.removePotionEffect(PotionEffectType.GLOWING);
                            }

                            target.removePotionEffect(unluck.getType());
                            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,10,12));
                            target.getWorld().spawnParticle(Particle.FIREWORK,target.getLocation().add(0,1,0),40,0,0,0,0.1);
                            target.getWorld().spawnParticle(Particle.LARGE_SMOKE,target.getLocation().add(0,1,0),40,0,0,0,0.1);


                            p.getWorld().playSound(target.getLocation(),
                                    Sound.ITEM_TRIDENT_THUNDER, 15f, 1f);

                            p.removePotionEffect(PotionEffectType.UNLUCK);
                        };

                        p.getWorld().playSound(target.getLocation(),
                                Sound.BLOCK_BELL_USE, 1f, 1f);
                        p.getWorld().playSound(p.getLocation(),
                                Sound.BLOCK_BELL_USE, 1f, 1f);

                        cod.remove();
                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(main, 0L, 1L);
    }



    public void pufferfishBomb() {
        if (isOnCooldown("puffer")) return;
        setCooldown("puffer", 18, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);

        // 1️⃣  throw the “grenade” item
        Location throwLoc = p.getEyeLocation().subtract(0, 0.5, 0).add(p.getLocation().getDirection().multiply(1.5));
        Item thrown = p.getWorld().dropItem(throwLoc,
                new ItemStack(Material.PUFFERFISH));
        Vector vel = p.getLocation().getDirection().multiply(1.5);
        vel.setY(vel.getY() + 0.3);
        thrown.setVelocity(vel);
        thrown.setPickupDelay(Integer.MAX_VALUE);           // players can’t grab it
        p.playSound(p.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1f, 0.8f);

        // 2️⃣  wait until the item slows or hits the ground, then detonate
        BukkitTask t = new BukkitRunnable() {
            int timer = 0;
            @Override
            public void run() {
                timer++;
                if (!thrown.isValid()) { cancel(); return; }

                boolean lowVel  = thrown.getVelocity().length() < 0.2;
                boolean tooLong = timer >= 10;
                if (timer >= 1 && (lowVel || tooLong || thrown.isOnGround())) {

                    // --- DETONATION ---
                    Location bombLoc = thrown.getLocation();

                    // 3️⃣  spawn a tight “cloud” of fake pufferfish
                    double clusterRadius = 0.6;           // keeps fish close together
                    for (int i = 0; i < 16; i++) {
                        PufferFish fish = (PufferFish) p.getWorld()
                                .spawnEntity(bombLoc, EntityType.PUFFERFISH);

                        // keep them alive & stationary for ~2 s
                        fish.setRotation((float)Math.random() * 180, (float)(Math.random() * 90));
                        fish.setPuffState(2);      // 0=deflated, 1=half, 2=full
                        fish.setInvulnerable(true);
                        fish.setRemainingAir(999999);
                        fish.setAware(false);             // disable AI flopping
                        fish.setVelocity(new Vector(
                                (Math.random() - 0.5) * 0.2,
                                0.05 + Math.random() * 0.05,
                                (Math.random() - 0.5) * 0.2));

                        // schedule cleanup so they don’t hang around forever
                        new BukkitRunnable() {
                            @Override
                            public void run() { fish.remove(); }
                        }.runTaskLater(main, 180L);         // 2 s later
                    }

                    // 4️⃣  SFX + actual damage
                    p.getWorld().playSound(bombLoc,
                            Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                    p.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER,
                            bombLoc, 3);

                    // ~5-block lethal circle (6 dmg @ center → 0 @ 5 blocks)
                    main.fakeExplode(
                            p, bombLoc,
                            6,        // maxDamage
                            5,        // maxDistance (radius)
                            false,    // no fire
                            false,    // no block break
                            true,    // yes ally damage
                            "pufferfish"
                    );

                    thrown.remove();
                    cancel();
                }
            }
        }.runTaskTimer(main, 0L, 2L);     // check every 2 ticks

        registerTask(t);
    }


    public void salmonrang() {
        if (isOnCooldown("salmon")) return;
        setCooldown("salmon", 15, Sound.ENTITY_EXPERIENCE_ORB_PICKUP,() ->
                p.getInventory().setItem(3,salmonrang));

        BukkitTask t = new BukkitRunnable() {
            public void run() {
                p.getInventory().setItem(3,swap);
            }
        }.runTaskLater(main,5L);
        registerTask(t);

        final Set<UUID> hitThisShot = new HashSet<>();
        Entity salmon = p.getWorld().spawnEntity(p.getEyeLocation(), EntityType.SALMON);

        this.salmon = salmon;

        p.getWorld().playSound(p.getLocation(),Sound.ENTITY_BREEZE_IDLE_AIR,1.0f,0.8f);
        p.getWorld().playSound(p.getLocation(),Sound.ENTITY_SALMON_DEATH,1.0f,0.5f);

        Vector velocity = p.getLocation().getDirection().multiply(1.5);

        salmon.setInvulnerable(true);

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 120;
            @Override
            public void run() {
                // Lifetime safeguard
                if (!salmon.isValid() || ++ticks > maxTicks) {
                    p.getInventory().setItem(3,newItem(Material.COOKED_SALMON,ChatColor.GRAY + "Salmoned Out.."));
                    salmon.remove();
                    cancel();
                    return;
                }

                //Rotate le fish
                salmon.setRotation((float)(ticks * 36 * 1.5), 0.0f);
                // :sparkle: Water Ring
                createRingParticles(30,1.2,salmon.getLocation());

                if (ticks % 5 == 0) {
                    hitThisShot.clear();
                    p.getWorld().playSound(salmon.getLocation(), Sound.ENTITY_SALMON_FLOP, 0.5f, 0.2f);
                }

                // Push le fish forward
                if (ticks < 9) {
                    salmon.setVelocity(velocity);
                } else if (ticks < (maxTicks - 25)) {
                    if (salmon.isInvulnerable()) {
                        salmon.setInvulnerable(false);
                    }
                    salmon.setVelocity(new Vector(0,0,0));
                } else {
                    salmon.setVelocity(
                            (salmon.getLocation().toVector()).subtract(p.getLocation().toVector()).normalize().multiply(-1.5)
                    );

                    if (p == null) {
                        salmon.remove();
                        this.cancel();
                    }
                    for (Entity e : salmon.getNearbyEntities(1.5, 0.5, 1.5)) {
                        if (e == p) {
                            p.getInventory().setItem(3,newItem(Material.COOKED_SALMON,ChatColor.GRAY + "Salmoned Out.."));
                            salmon.remove();
                            cancel();
                        }
                    }

                }

                // Collision check
                for (Entity e : salmon.getNearbyEntities(1.5, 0.5, 1.5)) {
                    if (e instanceof Player target
                            && !target.getUniqueId().equals(p.getUniqueId())
                            && hitThisShot.add(target.getUniqueId())) {

                        main.combatTracker.setHealth(target, target.getHealth() - 1.5, p, "salmonrang");

                        Vector toPlayer = (salmon.getLocation().toVector()).subtract(p.getLocation().toVector()).normalize();

                        target.setVelocity(toPlayer.multiply(0.5));

                    }
                }
            }

            public void createRingParticles(int particles, double rad, Location loc) {

                for (int i = 0; i < particles; i++) {

                    double theta = Math.random() * 2 * Math.PI;
                    double x = rad * Math.cos(theta);
                    double z = rad * Math.sin(theta);

                    Location particleLocation = loc.clone().add(x, 0, z);
                    Objects.requireNonNull(loc.getWorld()).spawnParticle(
                            Particle.BUBBLE, particleLocation, 1,0,0,0,0.01);
                    Objects.requireNonNull(loc.getWorld()).spawnParticle(
                            Particle.BUBBLE_POP, particleLocation, 1,0,0,0,0.01);
                }
            }

        }.runTaskTimer(main, 0L, 1L);
    }

    public void swap() {
        if (salmon == null) return;

        p.getInventory().setItem(3,newItem(Material.GUNPOWDER,ChatColor.GRAY + "Swapped..."));

        Location loc = p.getLocation().add(0,0.75,0.0);

        p.teleport(salmon.getLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);

        salmon.teleport(loc);
    }


}