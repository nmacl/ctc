package org.macl.ctc.events;

import com.destroystokyo.paper.event.entity.ThrownEggHatchEvent;
import net.minecraft.world.item.alchemy.Potion;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.macl.ctc.Main;
import org.macl.ctc.kits.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Players extends DefaultListener {
    public Players(Main main) {
        super(main);
    }

    @EventHandler
    public void playerJoin(AsyncPlayerPreLoginEvent event) {
        if(world.isUnloading)
            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
    }

    @EventHandler
    public void playerJoinReal(PlayerJoinEvent event) {
        Player p = event.getPlayer();

        // Load stats from DB into cache (async, non-blocking)
        main.getStats().loadPlayer(p.getUniqueId()).thenRun(() -> {
            // Schedule back to main thread
            Bukkit.getScheduler().runTask(main, () -> {
                game.resetPlayer(p, false);
                game.addTeam(p);
                p.sendMessage(ChatColor.AQUA + "Join our Discord: https://discord.gg/Qeme8MUXBY");
            });
        });
    }

    @EventHandler
    public void playerQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();

        // Save stats to DB before they leave
        main.getStats().savePlayer(p.getUniqueId()).thenRun(() -> {
            main.broadcast("quit event " + game.resetPlayer(p, true));
            // Remove from cache after save
            main.getStats().removeFromCache(p.getUniqueId());
        });
    }


    @EventHandler
    public void launch(ProjectileLaunchEvent event) {
        Projectile proj = event.getEntity();
        if(proj instanceof EnderPearl && proj.getShooter() instanceof Player) {
            EnderPearl e = (EnderPearl) proj;
            e.setPassenger((Player)proj.getShooter());
        }
        if(proj instanceof Arrow && proj.getShooter() instanceof Player) {
            Player p = (Player) proj.getShooter();
            if(kit.kits.get(p.getUniqueId()) instanceof Archer) {
                Archer a = (Archer) kit.kits.get(p.getUniqueId());
                a.shoot(event);
            }
        }
    }

    @EventHandler
    public void land(PlayerTeleportEvent event) {
        if(event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL)
            event.setCancelled(true);
    }

    @EventHandler
    public void fish(PlayerFishEvent event) {
        event.getHook().setVelocity(event.getHook().getVelocity().multiply(1.7));
        if(event.getCaught() instanceof Player) {
            Player c = (Player) event.getCaught();
            Player p = event.getPlayer();
            Vector velo = p.getLocation().getDirection().multiply(-3f);
            double y = p.getLocation().distance(c.getLocation());
            y*=0.14;
            velo.setY(Math.min(y, 1.8));
            velo.setX(velo.getX()*0.3);
            velo.setZ(velo.getZ()*0.3);
            c.setVelocity(velo);
        }
    }

    @EventHandler
    public void entityDamage(EntityDamageEvent event) {
        if(event.getEntity() instanceof Player) {
            Player p = (Player) event.getEntity();
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                event.setDamage(p.getHealth());
            }
            if(main.getKits().get(p.getUniqueId()) != null && main.getKits().get(p.getUniqueId()) instanceof Spy) {
                ((Spy) main.getKits().get(p.getUniqueId())).uninvis();
            }
        }
    }

    @EventHandler
    public void projectileHit(ProjectileHitEvent event) {
        if(event.getEntity() instanceof Arrow && event.getEntity().getShooter() instanceof Player) {
            Player p = (Player) event.getEntity().getShooter();
            if(kit.kits.get(p.getUniqueId()) instanceof Archer) {
                Archer a = (Archer) kit.kits.get(p.getUniqueId());
                a.bHit(event);
            }
        }
    }



    public void onEggThrow(PlayerEggThrowEvent e) {
        e.setHatching(false);
    }


    @EventHandler
    public void itemBreak(PlayerItemBreakEvent event) {
        event.getBrokenItem().setDurability((short)3);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityHitTag(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;

        double finalDamage = event.getFinalDamage();
        if (finalDamage <= 0) return;

        String ability = resolveAbility(event, attacker);
        main.combatTracker.tagDamage(victim, finalDamage, attacker, ability);
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player p) return p;

        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player shooter) {
            return shooter;
        }

        return null;
    }

    private String resolveAbility(EntityDamageByEntityEvent event, Player attacker) {
        Entity damager = event.getDamager();

        // Projectiles
        if (damager instanceof SpectralArrow) return "Spectral Arrow";
        if (damager instanceof Arrow)         return "Bow";
        if (damager instanceof Snowball)      return "Snowball";

        // Melee – based on held item
        if (damager instanceof Player) {
            Material mat = attacker.getInventory().getItemInMainHand().getType();
            if (mat.name().endsWith("_SWORD")) return "Sword";
            if (mat.name().endsWith("_SHOVEL")) return "Shovel";
            if (mat.name().endsWith("_HOE")) return "Hoe";
            if (mat == Material.FISHING_ROD)   return "Fishing Rod";
            if (mat == Material.STICK)         return "Cane";
            if (mat == Material.BLAZE_ROD)     return "Classic Cane";
        }

        return "";
    }



    @EventHandler
    public void onEntityHit(EntityDamageByEntityEvent event) {
        if(event.getDamager() instanceof SpectralArrow) {
            SpectralArrow arrow = (SpectralArrow) event.getDamager();
            event.setDamage(2.25);
            if(arrow.getShooter() instanceof Player) {
                Player shooter = (Player) arrow.getShooter();
                main.broadcast(shooter.getName());
                if(kit.kits.get(shooter.getUniqueId()) instanceof Artificer && event.getEntity() instanceof Player) {
                    Player shot = (Player) event.getEntity();
                    Kit shooterkit = kit.kits.get(shooter.getUniqueId());
                    if (shooterkit instanceof Artificer) ((Artificer) shooterkit).addVoidFragments(4);
                    main.broadcast(shot.getName());
                    shot.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 12));
                    shot.sendTitle(
                            ChatColor.AQUA + "***** YOU'VE BEEN FROZEN! *****",
                            "",
                            5, 25, 5
                    );
                }
            }
        }

        if (event.getDamager() instanceof SmallFireball fireball) {
            event.setCancelled(true);
            Entity e = event.getEntity();
            if (e instanceof Player victim) {
                Player shooter = null;
                ProjectileSource src = fireball.getShooter();
                if (src instanceof Player p) shooter = p;

                double newHealth = victim.getHealth() - 6.0;
                main.combatTracker.setHealth(victim, newHealth, shooter, "Fireball");
            }
        }


        if (event.getDamager() instanceof ShulkerBullet bullet) {
            event.setCancelled(true);
            Entity e = event.getEntity();
            if (e instanceof Player victim) {
                Player shooter = null;
                ProjectileSource src = bullet.getShooter();
                if (src instanceof Player p) shooter = p;

                double newHealth = victim.getHealth() - 0.4;
                main.combatTracker.setHealth(victim, newHealth, shooter, "Shulker Bullet");

                victim.setNoDamageTicks(0);
                victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 6, 1));
            }
        }

        if (event.getDamager() instanceof Snowball)
            event.setDamage(1.2);

        if (event.getDamager() instanceof Egg) {
            event.setCancelled(true);  // suppress vanilla egg “knockback damage”
            Egg egg = (Egg) event.getDamager();
            ProjectileSource shooterSrc = egg.getShooter();

            if (event.getEntity() instanceof Player && shooterSrc instanceof Player shooter) {
                Player victim = (Player) event.getEntity();

                boolean sameTeam = main.game.sameTeam(
                        victim.getUniqueId(),
                        shooter.getUniqueId()
                );

                // Always do the explosion visual
                victim.getWorld().createExplosion(victim.getLocation(), 2f, false, true);

                if (!sameTeam) {
                    // enemy → subtract HP via CombatTracker so kill is attributed
                    double newHealth = victim.getHealth() - 8.5; // same as before
                    main.combatTracker.setHealth(victim, newHealth, shooter, "direct hit grenade");
                }
            } else {
                // non-player victim or shooter → explosion only
                event.getEntity()
                        .getWorld()
                        .createExplosion(
                                event.getEntity().getLocation(),
                                2f,
                                false,  // no fire
                                true    // break blocks
                        );
            }
            return;
        }

        if (event.getDamager() instanceof Player p) {
            // on Cane (stick) hit
//            main.broadcast("registered damaged");
            if(main.getKits().get(p.getUniqueId()) != null && main.getKits().get(p.getUniqueId()) instanceof Grandma g && p.getInventory().getItemInMainHand().getType() == Material.STICK) {
                g.onCaneHit();
//                main.broadcast("grandma hit with cane");
            }
            //on Classic Cane (blaze rod) hit
            else if(main.getKits().get(p.getUniqueId()) != null && main.getKits().get(p.getUniqueId()) instanceof Grandma g && p.getInventory().getItemInMainHand().getType() == Material.BLAZE_ROD) {
                g.onClassicCaneHit();

//                 new BukkitRunnable() {
//
//                     public void run() {
//                         Vector velocity = event.getEntity().getVelocity();
//                         Vector upVec = new Vector(0.0f,0.5f,0.0f);
//                         event.getEntity().setVelocity(velocity.add(upVec));}
//
//                 }.runTaskLater(main,1L);



//                main.broadcast("grandma hit with classic cane");
            }
        }
        if(event.getEntity() instanceof Player) {
            Player p = (Player) event.getEntity();
            if(event.getDamager() instanceof Player) {
                Player attacker = (Player) event.getDamager();
                if(attacker.getInventory().getItemInMainHand().getType() == Material.DIAMOND_PICKAXE)
                    event.setDamage(0.5);
                if(main.getKits().get(attacker.getUniqueId()) != null && main.getKits().get(attacker.getUniqueId()) instanceof Spy && attacker.getInventory().getItemInMainHand().getType() == Material.IRON_HOE) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20*2, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 20 * 3, 2));
                }
            }
        }
    }




    @EventHandler
    public void pickup(EntityPickupItemEvent event) {
        if(main.game.started)
            event.setCancelled(true);
    }

    @EventHandler
    public void portal(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        // Removed teleport - let players hang out after game ends
        // if(!main.game.started && p.getWorld().getName() == "map")
        //     p.teleport(Bukkit.getWorld("world").getSpawnLocation());
        if(event.getTo().getBlock().getType() == Material.NETHER_PORTAL)
            game.stack(p);
        PotionEffect slowness = p.getPotionEffect(PotionEffectType.SLOWNESS);
        // so hacky but we ball
        // getPotionEffect returns null if they don't have that effect
        if (slowness != null && slowness.getAmplifier() > 10) {
            Location to = event.getTo();
            Location from = event.getFrom();
            to.setX(from.getX());
            to.setY(from.getY());
            to.setZ(from.getZ());
            event.setTo(to);
            event.getPlayer().setFreezeTicks(10);
        }
        if(!main.game.started)
            return;
        double strength = 1.5;
        if(event.getTo().getWorld() != world.getRed().getWorld())
            return;
        if(game.redHas(p) && (event.getTo().distance(world.getBlue()) < 10)) {
            p.sendMessage("away");
            Vector dir = world.getBlue().toVector().subtract(p.getLocation().toVector()).normalize();
            dir.multiply(-1);
            p.setVelocity(dir.multiply(strength));
        }
        if(game.blueHas(p) && (event.getTo().distance((world.getRed())) < 10)) {
            p.sendMessage("away");
            Vector dir = world.getRed().toVector().subtract(p.getLocation().toVector()).normalize();
            dir.multiply(-1);
            p.setVelocity(dir.multiply(strength));
        }
    }

    @EventHandler
    public void food(FoodLevelChangeEvent event) {
        event.setFoodLevel(20);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDurabilityLoss(PlayerItemDamageEvent event) {
        // prevent *any* item from losing durability
        event.setCancelled(true);
    }

    @EventHandler
    public void death(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        main.combatTracker.onDeath(event);

        Kit kitObj = kit.kits.remove(victim.getUniqueId());
        if (kitObj != null) {
            kitObj.cancelAllCooldowns();
            kitObj.cancelAllRegen();
            kitObj.cancelAllTasks();
        }
    }

    @EventHandler
    public void onArrowShoot(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof SpectralArrow) {
            SpectralArrow arrow = (SpectralArrow) event.getEntity();
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
    }

    @EventHandler
    public void hatching(ThrownEggHatchEvent event) {
        event.setHatching(false);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();

        if (!main.game.started) return;

        // 1) Determine where they should reappear after respawn:
        Location teamSpawn;
        if (main.game.redHas(p))      teamSpawn = world.getRed();
        else if (main.game.blueHas(p)) teamSpawn = world.getBlue();
        else                            teamSpawn = Bukkit.getWorld("world").getSpawnLocation();

        // 2) Calculate distance-based respawn timer
        int respawnTime = calculateRespawnTime(p, teamSpawn);

        // 3) Set that as the respawn point (this happens *before* the player actually appears)
        event.setRespawnLocation(teamSpawn);

        // 4) One tick later, put them into Spectator and start the countdown
        Bukkit.getScheduler().runTaskLater(main, () -> {
            p.setGameMode(GameMode.SPECTATOR);

            // 5) Countdown runnable: after calculated time, back to Survival
            new BukkitRunnable() {
                int timer = respawnTime;
                @Override
                public void run() {
                    if (!p.isOnline()) { cancel(); return; }

                    if (timer <= 1) {
                        // back to life
                        p.setGameMode(GameMode.SURVIVAL);
                        // teleport again just to be safe
                        p.teleport(teamSpawn);
                        main.kit.openMenu(p);
                        cancel();
                    } else {
                        // show a little "respawning in Xs" subtitle
                        p.sendTitle("", ChatColor.YELLOW + "Respawning in " + (timer-1) + "s", 0, 20, 0);
                        timer--;
                    }
                }
            }.runTaskTimer(main, 20L, 20L);
        }, 1L);
    }

    /**
     * Calculate respawn time based on distance from player's core.
     * - Die at your core: 12s (max penalty)
     * - Die at center/beyond: 6s (min penalty)
     * - Linear scaling in between
     */
    private int calculateRespawnTime(Player p, Location teamSpawn) {
        final int MIN_RESPAWN = 6;  // Die far from core (quick respawn)
        final int MAX_RESPAWN = 12; // Die near core (slow respawn)

        Location deathLoc = p.getLocation();

        // If no center defined, use default 8s
        if (world.center == null || world.center.isEmpty()) {
            return 8;
        }

        Location centerLoc = world.center.get(0); // First center point

        // Calculate distances
        double distanceFromCore = deathLoc.distance(teamSpawn);
        double coreToCenter = teamSpawn.distance(centerLoc);

        // Cap distance at center (don't reward dying beyond center)
        if (distanceFromCore > coreToCenter) {
            distanceFromCore = coreToCenter;
        }

        // Calculate respawn time (inverse relationship: far = short, close = long)
        // Formula: respawnTime = MAX - (distance / maxDistance) * (MAX - MIN)
        double ratio = distanceFromCore / coreToCenter; // 0.0 (at core) to 1.0 (at center)
        int respawnTime = (int) Math.round(MAX_RESPAWN - (ratio * (MAX_RESPAWN - MIN_RESPAWN)));

        // Clamp between min and max (safety check)
        return Math.max(MIN_RESPAWN, Math.min(MAX_RESPAWN, respawnTime));
    }


    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if(event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)
                event.setDamage(0);
            if(event.getCause() == EntityDamageEvent.DamageCause.LIGHTNING)
                event.setDamage(3);
            // Check if the player is wearing any armor
            if (isWearingArmor(player)
                    && event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)) {

                event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, 0);
            }
            if(event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                if(kit.kits.get(event.getEntity().getUniqueId()) instanceof Grandpa) {
                    Grandpa g = (Grandpa) kit.kits.get(event.getEntity().getUniqueId());
                    if(g.fallImmune) {
                        g.fallImmune = false;
                        event.setDamage(0);
                    }
                }
            }
        }
    }

    private boolean isWearingArmor(Player player) {
        return player.getInventory().getArmorContents() != null &&
                (player.getInventory().getHelmet() != null ||
                        player.getInventory().getChestplate() != null ||
                        player.getInventory().getLeggings() != null ||
                        player.getInventory().getBoots() != null);
    }


}
