package org.macl.ctc.events;

import com.destroystokyo.paper.event.entity.ThrownEggHatchEvent;
import net.minecraft.world.entity.animal.SnowGolem;
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
import java.util.Objects;
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
            boolean wasOnTeam = game.resetPlayer(p, true);
            main.broadcast("quit event " + wasOnTeam);
            // Remove from cache after save
            main.getStats().removeFromCache(p.getUniqueId());

            // Check if we should end the game due to low player count
            // Schedule this check for next tick to ensure player is fully removed
            Bukkit.getScheduler().runTask(main, () -> {
                if (game.started) {
                    int remainingPlayers = Bukkit.getOnlinePlayers().size();

                    if (remainingPlayers < 2) {
                        main.broadcast(ChatColor.YELLOW + "Not enough players to continue! Game ending...");

                        // End the game - no winner, just restart
                        game.started = false;
                        game.starting = false;

                        // Save remaining player stats
                        for (Player online : Bukkit.getOnlinePlayers()) {
                            main.getStats().savePlayer(online.getUniqueId());
                        }

                        // Restart server
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                main.broadcast(ChatColor.GOLD + "=========================", ChatColor.GOLD);
                                main.broadcast("Server restarting due to insufficient players...", ChatColor.YELLOW);
                                main.broadcast(ChatColor.GOLD + "=========================", ChatColor.GOLD);
                            }
                        }.runTaskLater(main, 60L);

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                Bukkit.shutdown();
                            }
                        }.runTaskLater(main, 100L);
                    }
                }
            });
        });
    }


    @EventHandler
    public void launch(ProjectileLaunchEvent event) {
        Projectile proj = event.getEntity();

        if(proj instanceof EnderPearl e && proj.getShooter() instanceof Player p) {
            e.addPassenger(p);
        }

        if(proj instanceof ThrownExpBottle && proj.getShooter() instanceof Player) {
            Vector eyeDir = ((Player) proj.getShooter()).getEyeLocation().getDirection();
            proj.setVelocity(eyeDir.multiply(2.75));
        }

        if (proj instanceof Snowball s && proj.getShooter() instanceof Player p) {
            if(kit.kits.get(p.getUniqueId()) instanceof Operator o) {
                Vector eyeDir = ((Player) proj.getShooter()).getEyeLocation().getDirection();
                proj.setVelocity(eyeDir.multiply(1.25));
                o.throwPuller(s);
            }
        }

        if(proj instanceof Arrow && proj.getShooter() instanceof Player p) {
            if(kit.kits.get(p.getUniqueId()) instanceof Archer a) {
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
    public void dismount(EntityDismountEvent event) {
        if (event.getDismounted() instanceof EnderPearl) {
            if (event.getEntity() instanceof Player p) {
                if (kit.kits.get(p.getUniqueId()) != null) {
                    Kit k = kit.kits.get(p.getUniqueId());

                    if (k instanceof Spy s) {
                        s.onPearlDismount();
                    }
                }
            }
        }
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        Player p = event.getPlayer();
        Kit kit = main.getKits().get(p.getUniqueId());

        if (kit instanceof Fisherman) {
            event.getHook().setVelocity(event.getHook().getVelocity().multiply(1.7));

            if (event.getCaught() != null) {
                Entity c = event.getCaught();

                if (event.getCaught() instanceof LivingEntity e) {
                    e.addPotionEffect(new PotionEffect(PotionEffectType.UNLUCK, 30, 1, true, true));
                    e.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 30, 1, false, false));
                }

                Vector velo = p.getLocation().getDirection().multiply(-3f);
                double y = p.getLocation().distance(c.getLocation());
                y *= 0.14;
                velo.setY(Math.min(y, 1.8));
                velo.setX(velo.getX() * 0.3);
                velo.setZ(velo.getZ() * 0.3);
                c.setVelocity(velo);
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
        if (event.getEntity() instanceof Egg e && event.getHitEntity() instanceof FallingBlock) {
            if (e.getShooter() instanceof Player p) {
                if (kit.kits.get(p.getUniqueId()) != null) {
                    Kit k = kit.kits.get(p.getUniqueId());
                    if (k instanceof Demolitionist d) {
                        if (d.currentThrownMine != null) { // holy mother of nest
                            if (d.currentThrownMine.fallMine == event.getHitEntity()) d.currentThrownMine.nuclearBomb();
                        }
                    }
                }
            }
        }

        if(event.getEntity() instanceof ThrownExpBottle && event.getEntity().getShooter() instanceof Player p) {
            if(kit.kits.get(p.getUniqueId()) instanceof Tank t) {
                t.bottleHit(event);
                event.setCancelled(true);
            }
        }

        if (event.getEntity() instanceof Fireball f  && !(event.getEntity() instanceof SmallFireball)) {
            if (event.getEntity().getShooter() instanceof Player p && main.kit.kits.get(p.getUniqueId()) instanceof Grandpa g) {
                if (g.finalShotCanExplode) {
                    g.finalShotCanExplode = false;
                    main.fakeExplode(
                            (Player) f.getShooter(),
                            f.getLocation(),
                            6,
                            3,
                            true,
                            true,
                            true,
                            "Fireball",
                            0.2f);
                }
            }
        }

    }



    @EventHandler
    public void onEggThrow(PlayerEggThrowEvent e) {
        e.setHatching(false);
    }

    @EventHandler
    public void onExpLand(ExpBottleEvent event) {
        event.setExperience(0);
        event.setShowEffect(false);
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

        if (damager instanceof Firework f) {
            if (f.getShooter() == null && !(Objects.equals(f.getCustomName(), ""))){
                return Bukkit.getPlayer(UUID.fromString(f.getName()));
            } else {
                return (Player) f.getShooter();
            }

        }


        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player shooter) {
            return shooter;
        }

        return null;
    }

    private String resolveAbility(EntityDamageByEntityEvent event, Player attacker) {
        Entity damager = event.getDamager();

        Kit k = kit.kits.get(event.getEntity().getUniqueId());

        // Projectiles
        if (damager instanceof SpectralArrow) return "Spectral Arrow";
        if (damager instanceof Arrow)         return "Bow";
        if (damager instanceof Snowball s) {
            if (Objects.equals(s.getCustomName(), "Turret")) return "Turret";

            if (k instanceof Snowballer) return "Snowball";
            if (k instanceof Tank) return "Snowball";

            return "Snowball";
        }

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
                    shot.setFreezeTicks(100);
                    shot.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 12));
                    shot.sendTitle(
                            ChatColor.AQUA + "***** YOU'VE BEEN FROZEN! *****",
                            "",
                            5, 25, 5
                    );
                }
            }
        }

        if (event.getDamager() instanceof Firework f) {
//            main.broadcast("" + event.getEntity());
            int d = f.getFireworkMeta().getPower();
            event.setDamage(d);
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

        if (event.getDamager() instanceof Snowball s) {
            if (s.getShooter() instanceof Player p) {
                if (kit.kits.get(p.getUniqueId()) != null) {
                    event.setDamage(1.2);
                    Kit k = kit.kits.get(p.getUniqueId());

                    if (k instanceof Snowballer) {
                        event.setDamage(1.0);
                    }

                    if (k instanceof Tank) {
                        event.setDamage(1.3);
                        if (s.getFireTicks() > 0) event.getEntity().setFireTicks(30);
                    }

                }
            }
            if (s.getShooter() instanceof SnowGolem) {
                event.setDamage(0.8);
            }
        }

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

        if (event.getDamager() instanceof Player p && main.kit.kits.get(p.getUniqueId()) instanceof Kit k) {
            if(k instanceof Grandma g && p.getInventory().getItemInMainHand().getType() == Material.STICK) {
                g.onCaneHit();
            }
            else if(k instanceof Grandma g && p.getInventory().getItemInMainHand().getType() == Material.BLAZE_ROD) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        g.onClassicCaneHit();
                    }
                }.runTaskLater(main, 1L);
            }

            if (k instanceof Spy s) {
                if (s.isHoldingShank()) {
                    s.reshank();
                }
            }

        }
        if(event.getEntity() instanceof Player) {
            Player p = (Player) event.getEntity();
            if(event.getDamager() instanceof Player) {
                Player attacker = (Player) event.getDamager();
                if(attacker.getInventory().getItemInMainHand().getType() == Material.DIAMOND_PICKAXE)
                    event.setDamage(0.5);
                if(main.getKits().get(attacker.getUniqueId()) != null && main.getKits().get(attacker.getUniqueId()) instanceof Spy && attacker.getInventory().getItemInMainHand().getType() == Material.IRON_HOE) {
//                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20*2, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 20 * 2, 2));
                }
            }
        }
    }




    @EventHandler
    public void pickup(EntityPickupItemEvent event) {
        if(main.game.started) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void portal(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        // Removed teleport - let players hang out after game ends
        // if(!main.game.started && p.getWorld().getName() == "map")
        //     p.teleport(Bukkit.getWorld("world").getSpawnLocation());
        if(event.getTo().getBlock().getType() == Material.NETHER_PORTAL)
            game.connectToServer(p, "practice");
        PotionEffect slowness = p.getPotionEffect(PotionEffectType.SLOWNESS);
        // so hacky but we ball
        // getPotionEffect returns null if they don't have that effect
        if (slowness != null && slowness.getAmplifier() > 10) {
            float toYaw = event.getTo().getYaw();
            float toPitch = event.getTo().getPitch();

            Location newLoc = event.getFrom().clone();
            newLoc.setYaw(toYaw);
            newLoc.setPitch(toPitch);

            event.setTo(newLoc);

        }

        Kit k = main.kit.kits.get(event.getPlayer().getUniqueId());
        if (k != null) {
            k.realVelocity = (event.getTo().toVector().subtract(event.getFrom().toVector()));
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
    public void onHeal(EntityRegainHealthEvent event) {
        if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.MAGIC) {
            if (event.getEntity() instanceof Player p && p.hasPotionEffect(PotionEffectType.INSTANT_HEALTH)) {
                if (p.getPotionEffect(PotionEffectType.INSTANT_HEALTH).getAmplifier() == 123) {
                    event.setAmount(0.5);
                }
            }
        }
    }

    @EventHandler
    public void food(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onFoodConsumed(PlayerItemConsumeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerShootBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player p) {
            Kit k = kit.kits.get(p.getUniqueId());
            if (k instanceof Archer a) {
//                event.getConsumable().setAmount(event.getConsumable().getAmount() + 1);
                a.setArrows();
                a.switchToBow(a.getPlayer().getInventory().getHeldItemSlot());
//                event.setConsumeItem(false);
            }
            if (k instanceof Engineer e) {
                e.onFireworkConsumed(event);
            }
        }
    }

    @EventHandler
    public void onFireworkExplode(FireworkExplodeEvent event) {
        if (event.getEntity().getShooter() instanceof Player p) {
            Kit k = kit.kits.get(p.getUniqueId());
            if (k instanceof Engineer e) {
                event.getEntity().setShooter(null);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDurabilityLoss(PlayerItemDamageEvent event) {
        // prevent *any* item from losing durability
        event.setCancelled(true);
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p) {
            event.setCancelled(true);
        }
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
        event.setDeathMessage(null);
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

        if (!main.game.started) {
            // Practice/debug mode: no match is running, so there's no team spawn or respawn
            // timer to apply. Scatter them somewhere new in the arena and get them straight
            // back into the action with a fresh kit - free-for-all means dying shouldn't
            // leave you stuck kit-less or respawning in the same spot you just died.
            event.setRespawnLocation(game.randomMapSpawn());
            Bukkit.getScheduler().runTaskLater(main, () -> {
                if (!p.isOnline()) return;
                p.setGameMode(GameMode.SURVIVAL);
                main.kit.openMenu(p);
                game.giveLeaveItem(p);
                main.send(p, "Use /ctc kit to select your kit!", ChatColor.AQUA);
            }, 1L);
            return;
        }

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
     * Calculate respawn time based on horizontal distance from player's core.
     * - Die at your core: 12s (max penalty)
     * - Die at center/beyond: 6s (min penalty)
     * - Linear scaling in between
     * Note: Only considers X and Z coordinates (ignores height/Y)
     */
    private int calculateRespawnTime(Player p, Location teamSpawn) {
        final int MIN_RESPAWN = 7;  // Die far from core (quick respawn)
        final int MAX_RESPAWN = 16; // Die near core (slow respawn)

        Location deathLoc = p.getLocation();

        // If no center defined, use default 8s
        if (world.center == null || world.center.isEmpty()) {
            return 8;
        }

        Location centerLoc = world.center.get(0); // First center point

        // Calculate 2D horizontal distances (ignoring Y coordinate)
        double distanceFromCore = getHorizontalDistance(deathLoc, teamSpawn);
        double coreToCenter = getHorizontalDistance(teamSpawn, centerLoc);

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

    /**
     * Calculate horizontal distance between two locations (ignoring Y coordinate)
     */
    private double getHorizontalDistance(Location loc1, Location loc2) {
        double dx = loc1.getX() - loc2.getX();
        double dz = loc1.getZ() - loc2.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }


    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID)
                event.setDamage(player.getHealth());
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
                if (kit.kits.get(player.getUniqueId()) != null) {
                    Kit k = kit.kits.get(player.getUniqueId());


                    event.setDamage((event.getDamage() - 3.0) / 3.0);

                    if (event.getDamage() < 1) {
                        event.setCancelled(true);
                        return;
                    }

                    if (k instanceof Grandpa g) {
                        if(g.fallImmune) {
                            g.fallImmune = false;
                            event.setDamage(0);
                        }
                    }
                }
            }

            if (kit.kits.get(player.getUniqueId()) != null) {
                Kit k = kit.kits.get(player.getUniqueId());
                if (event.getDamage() > 0) {
                    k.procHungerDamage();

                    if (k instanceof Runner r) {
                        r.damaged = true;
                    }

                    if (k instanceof Spy s) {
                        s.uninvis();
                        s.reshank();
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
