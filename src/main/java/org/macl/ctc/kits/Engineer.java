package org.macl.ctc.kits;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.block.EndGateway;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.macl.ctc.Main;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class Engineer extends Kit {
    private Location loc1 = null;
    private Location loc2 = null;
    private teleporterProcess teleporterA = null;
    private teleporterProcess teleporterB = null;
    private DetectorProcess detectorProcess = null;
    private boolean canPlaceTeleport = true;
    private boolean canTeleport = true;

    SnowmanTurret snowMan;

    ItemStack turret = newItem(Material.DISPENSER, ChatColor.BOLD  + "" + ChatColor.WHITE + "Snowman Turret");
    ItemStack firework =  newItem(Material.FIREWORK_ROCKET,ChatColor.WHITE + "Firework", 5);
    ItemStack teleporter = newItem(Material.BEACON,ChatColor.AQUA + "Teleporter",2);
    ItemStack enemyDetector = (
            main.game.redHas(p)) ?
            newItem(Material.RED_SHULKER_BOX, ChatColor.RED + "Enemy Detector") :
            newItem(Material.BLUE_SHULKER_BOX, ChatColor.BLUE + "Enemy Detector");


    public Engineer(Main main, Player p, KitType type) {
        super(main, p, type);
        setupInventory(p);
    }

    private void setupInventory(Player p) {
        PlayerInventory e = p.getInventory();
        ItemStack chestplate = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta meta = (LeatherArmorMeta) chestplate.getItemMeta();
        if (meta != null) {
            meta.setColor(main.game.redHas(p) ? Color.RED : Color.BLUE);
            chestplate.setItemMeta(meta);
        }
        e.setHelmet(newItem(Material.CHAINMAIL_HELMET,ChatColor.WHITE + "Engineer's Cap"));
        e.setBoots(newItem(Material.LEATHER_BOOTS,ChatColor.WHITE + "Engineer's Boots"));
        p.getInventory().setChestplate(chestplate);
        FireworkMeta fwm = (FireworkMeta)firework.getItemMeta();
        FireworkEffect effect;
        if(main.game.redHas(p)) {
            effect = FireworkEffect.builder().withColor(Color.RED).with(FireworkEffect.Type.BALL_LARGE).withFlicker().trail(false).build();
        } else {
            effect = FireworkEffect.builder().withColor(Color.BLUE).with(FireworkEffect.Type.BALL_LARGE).withFlicker().trail(false).build();
        }
        fwm.addEffects(effect);
        fwm.setPower(1);
        firework.setItemMeta(fwm);
        e.setItemInOffHand(firework);
        ArrayList<Enchantment> es = new ArrayList<Enchantment>();
        es.add(Enchantment.QUICK_CHARGE);
        es.add(Enchantment.MULTISHOT);
        ItemStack crossbow = newItemEnchants(Material.CROSSBOW, ChatColor.YELLOW + "Firework Blaster", es, 1);
        e.addItem(crossbow);
        e.addItem(turret);
        e.addItem(teleporter);
        e.addItem(enemyDetector);
//        regenItem("turret",turret,25,1,1);
        giveWool();
        giveWool();
    }

    public void onFireworkConsumed(EntityShootBowEvent event) {

        Firework f = (Firework) event.getProjectile();
        f.setTicksFlown(0);
        f.teleport(f.getLocation().add(f.getVelocity().normalize().multiply(2.8)));
        FireworkMeta fw = f.getFireworkMeta();
        fw.setPower(3);
        f.setFireworkMeta(fw);
        new BukkitRunnable(){
            public void run() {
                if (f.isValid()) f.detonate();
                this.cancel();
            }
        }.runTaskLater(main,15);

        f.setCustomName(String.valueOf(p.getUniqueId()));
//        main.broadcast("" + ((Firework) event.getProjectile()).getShooter());
        if (e.getItemInOffHand().getAmount() == 0) {
            reloadFireworks();
        }
    }

    public void reloadFireworks() {
        if (isOnCooldown("Reloading") || e.getItemInOffHand().getAmount() >= 5) return;
        e.getItemInOffHand().setAmount(0);
        p.getWorld().playSound(p.getLocation(),Sound.ITEM_ARMOR_EQUIP_LEATHER,1.0f,0.75f);
        setCooldown("Reloading",5,Sound.ITEM_ARMOR_EQUIP_CHAIN, () -> {
            e.setItemInOffHand(firework);
        });
    }

    public void placeDetector(Location l) {
        if (isOnCooldown("Detector")) return;

        if (detectorProcess != null) detectorProcess.cancel();

        setCooldown("Detector",18,Sound.BLOCK_SHULKER_BOX_OPEN, () -> {
            e.setItem(3,enemyDetector);
        });

        detectorProcess = new DetectorProcess(l,p.getWorld());
        detectorProcess.runTaskTimer(main,0L,2L);
    }

    public class DetectorProcess extends BukkitRunnable {

        World w;
        Location decLoc;
        boolean onRed;

        HashMap<Player,Integer> enemiesInside = new HashMap<>();
        ArrayList<Player> enemiesInside2 = new ArrayList<>();

        public DetectorProcess(Location l, World w) {
            this.onRed = main.game.redHas(p);
            this.decLoc = l;
            this.w = w;
        }

        public void run() {
            if (main.getKits().get(p.getUniqueId()) == null) {
                this.cancel();
                return;
            }

            checkBlock();
            checkPlayers();

            for (Player e : enemiesInside.keySet()) {
                e.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,3,0));
            }

        }

        public void checkPlayers() {
            enemiesInside2.clear();
            for (org.bukkit.entity.Entity e: w.getNearbyEntities(decLoc,18,18,18)) {
                if (e instanceof Player pe && !isOnSameTeam(pe)) {
                    addEnemyToList(pe);
                    enemiesInside2.add(pe);
                }
            }

            for (Player e : enemiesInside.keySet()) {
                if (enemiesInside.get(e) > 0) {
                    enemiesInside.put(e,enemiesInside.get(e) - 1);
                } else {
                    createLine(e);
                    enemiesInside.put(e,50);
                }
            }

            enemiesInside.keySet().removeIf(e -> !enemiesInside2.contains(e));

        }

        public void addEnemyToList(Player enemy) {
            if (enemiesInside.containsKey(enemy)) return;
            enemiesInside.put(enemy,65);
            createLine(enemy);
        }

        public void checkBlock() {
            if (decLoc.getBlock().getType() != Material.BLUE_SHULKER_BOX && decLoc.getBlock().getType() != Material.RED_SHULKER_BOX) {
                this.cancel();
            }
        }

        public void createLine(Player enemy) {
            double distance = Math.min(decLoc.distance(e.getLocation()), 14);
            int newColor = (int)(((180) * (1 - (1 - (distance / 14)))));
            newColor = Math.clamp(newColor,0,255);
            Particle.DustOptions d = new Particle.DustOptions(Color.fromRGB(newColor,200,newColor),2.0f);
            enemy.playSound(decLoc,Sound.ENTITY_ALLAY_ITEM_GIVEN,1.2f,2.0f);
            Archer.createParticleLine(enemy,decLoc,enemy.getLocation().add(0,1,0),d,3.0,0.1);
        }

        public void cancel() {
            if (decLoc.getBlock().getType() != Material.AIR) {
                decLoc.getBlock().setType(Material.AIR);
            }
            super.cancel();
        }

        public boolean isOnSameTeam(Player pl) {
            boolean isOnSameTeam = false;

            if (onRed && main.game.redHas(pl)) isOnSameTeam = true;

            if (!onRed && main.game.blueHas(pl)) isOnSameTeam = true;

            return isOnSameTeam;
        }

    }

    public void turret(Location l) {
        if (isOnCooldown("turret")) return;
        e.setItem(1,newItem(Material.SMOOTH_STONE,ChatColor.GREEN + "Return Turret!"));
        CraftWorld w = (CraftWorld) p.getLocation().getWorld();
        SnowmanTurret s = new SnowmanTurret(EntityType.SNOW_GOLEM, w.getHandle(), l.add(0.5,0,0.5));
        Snowman s1 = (Snowman) s.getBukkitEntity();
        s1.setRotation(p.getLocation().getYaw(),0.0f);

        if (snowMan != null) {
            snowMan.remove(Entity.RemovalReason.DISCARDED);
        }

        s.engineer = this;
        snowMan = s;

        s1.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 99999999, 255));

        s1.setCustomName("Turret");
    }

    public void onTurretDeath() {
        setCooldown("turret",18,Sound.BLOCK_DISPENSER_DISPENSE,() -> {
            e.setItem(1,turret);
        } );
        e.setItem(1,newItem(Material.COBBLESTONE,ChatColor.RED + "Turret Down..."));
    }

    public void destroyTurret() {
        if (snowMan == null) return;
        int sec = (int)(10.0 - snowMan.getHealth());
        snowMan.remove(Entity.RemovalReason.DISCARDED);
        setCooldown("turret",3 + sec,Sound.BLOCK_DISPENSER_DISPENSE,() -> {
            e.setItem(1,turret);
        } );
        e.setItem(1,newItem(Material.STONE,ChatColor.AQUA + "Repairing Turret"));

    }

    public void placeTeleport(Location l, BlockPlaceEvent event) {
        teleporterProcess p = new teleporterProcess();
        BukkitTask b = p.runTaskTimer(main,0L,1L);
        boolean canPlace = p.initiateLocation(l);
        if (!canPlace) event.setCancelled(true);
        else {
            if (teleporterA == null) {
                teleporterA = p;
            } else {
                teleporterB = p;
                setCooldown("Teleporter",36,Sound.BLOCK_BEACON_AMBIENT,() -> {
                    e.setItem(2,newItem(Material.IRON_INGOT,ChatColor.GREEN + "Return Teleporters"));
                });
            }
//            registerTask(b);
        }

    }


    public void replaceTeleporters() {
        if (teleporterA != null) {
            teleporterA.cancel();
            teleporterA = null;
        }
        if (teleporterB != null) {
            teleporterB.cancel();
            teleporterB = null;
        }
        e.setItem(2,teleporter);
    }

    public class teleporterProcess extends BukkitRunnable {

        Location tpLoc;

        boolean active = false;

        ArrayList<Material> blockMats = new ArrayList<>();

        ArrayList<Block> glassPanes = new ArrayList<>();

        ArrayList<Block> teleporterBlocks =  new ArrayList<>();

        teleporterProcess otherTeleporter;

        HashMap<Player, Integer> playerBuffer = new HashMap<>();

        int tpBuffer = 10;

        int particleRate = 2;

        boolean onRed = main.game.redHas(p);

        Particle.DustOptions teamDust;

        public boolean initiateLocation(Location l) {
//            main.broadcast("initiated teleporter");
            tpLoc = l.clone().add(0,2,0);
            blockMats.add(onRed ? Material.REDSTONE_BLOCK : Material.LAPIS_BLOCK);
            blockMats.add(onRed ? Material.RED_CONCRETE : Material.BLUE_CONCRETE);
            blockMats.add(onRed ? Material.RED_GLAZED_TERRACOTTA : Material.BLUE_GLAZED_TERRACOTTA);
            blockMats.add(onRed ? Material.ORANGE_STAINED_GLASS_PANE : Material.LIGHT_BLUE_STAINED_GLASS_PANE);

            if (teleporterA != null) {
                otherTeleporter = teleporterA;
                teleporterA.otherTeleporter = this;
                this.activate();
                otherTeleporter.activate();
            }

            if (onRed) {
                teamDust = new Particle.DustOptions(Color.fromRGB(255, 0, 0), 2f);
            } else {
                teamDust = new Particle.DustOptions(Color.fromRGB(0, 0, 255), 2f);
            }


//            main.broadcast("" + !(l.clone().add(0,1,0).getBlock().getType().isSolid()));
            if (!(l.clone().add(0,1,0).getBlock().getType().isSolid())) {
                l.clone().add(0,1,0).getBlock().setType(Material.BEACON);
                teleporterBlocks.add(l.clone().add(0,1,0).getBlock());
                createTeleporterBlocks(l);
                return true;
            } else {
                this.cancel();
                return false;
            }


        }

        public void run() {
            if (main.getKits().get(p.getUniqueId()) == null) {
                this.cancel();
                return;
            }

            if (active) {
                handlePlayerTeleport();
            }

            Block oB = tpLoc.clone().add(0,-2,0).getBlock();
            if (oB.getType() == Material.END_GATEWAY) {
                EndGateway gate = (EndGateway) oB.getState();
                gate.setAge(150);
                gate.update();
            }

            if (active) {
                if (particleRate <= 0) {
                    particleRate = 2;
                    spawnBeamParticles();
                } else {
                    particleRate--;
                }
            }

            if (tpBuffer > 0) tpBuffer --;
            else checkBlocks();

        }

        public void spawnBeamParticles() {
            p.getWorld().spawnParticle(Particle.DUST,tpLoc.clone().add(0,60,0),60,0.2,60,0.2,0,teamDust,true);
        }

        public void createTeleporterBlocks(Location l) {
            ArrayList<Location> cornerBlocks = new ArrayList<>();
            ArrayList<Location> edgeBlocks = new ArrayList<>();
            if (!main.restricted.contains(l.getBlock().getType())) {
                if (active) l.getBlock().setType(Material.END_GATEWAY);
                else l.getBlock().setType(blockMats.get(0));
                teleporterBlocks.add(l.getBlock());
            }

            edgeBlocks.add(l.clone().add(1, 0, 0));
            cornerBlocks.add(l.clone().add(1, 0, 1));
            edgeBlocks.add(l.clone().add(0, 0, 1));
            edgeBlocks.add(l.clone().add(-1, 0, 0));
            cornerBlocks.add(l.clone().add(-1, 0, -1));
            edgeBlocks.add(l.clone().add(0, 0, -1));
            cornerBlocks.add(l.clone().add(1, 0, -1));
            cornerBlocks.add(l.clone().add(-1, 0, 1));

            for (Location le : cornerBlocks) {
                if (!main.restricted.contains(le.getBlock().getType())) {
                    if (active) le.getBlock().setType(blockMats.get(1));
                    else le.getBlock().setType(Material.IRON_BLOCK);
                    teleporterBlocks.add(le.getBlock());
                }
            }
            for (Location le : edgeBlocks) {
                if (!main.restricted.contains(le.getBlock().getType())) {
                    if (active) le.getBlock().setType(blockMats.get(2));
                    else le.getBlock().setType(Material.LIGHT_GRAY_CONCRETE);
                    teleporterBlocks.add(le.getBlock());
                    if (active) le.clone().add(0,1,0).getBlock().setType(blockMats.get(3),true);
                    else le.clone().add(0,1,0).getBlock().setType(Material.LIGHT_GRAY_STAINED_GLASS_PANE,true);
                    teleporterBlocks.add(le.clone().add(0,1,0).getBlock());

                }
            }
        }

        public void checkBlocks() {
            int glassHP = 0;
            int beaconHP = 0;

            for (Block b : teleporterBlocks) {
                if (b.getType() == Material.LIGHT_GRAY_STAINED_GLASS_PANE || b.getType() == blockMats.get(3)) {
                    glassHP++;
                }
                if (b.getType() == Material.BEACON) {
                    beaconHP++;
                    b.getState().update(true,true);
                }
            }

            if (glassHP <= 0 || beaconHP <= 0){
                this.cancel();
                if (otherTeleporter != null) otherTeleporter.cancel();

                Player closestP = null;
                double dist = 0;

                for (org.bukkit.entity.Entity e : p.getWorld().getNearbyEntities(tpLoc,4,4,4)) {
                    if  (!(e instanceof Player pe)) continue;
                    if (main.game.sameTeam(pe.getUniqueId(),p.getUniqueId())) continue;
                    double newDist = pe.getLocation().distanceSquared(tpLoc);
                    if (newDist < dist){
                        dist = newDist;
                        closestP = pe;
                    }
                }

                if (closestP != null) {
                    p.sendMessage(ChatColor.GRAY + "Your teleporter was destroyed by " + closestP.getDisplayName() + ChatColor.GRAY + "!");
                } else p.sendMessage(ChatColor.GRAY + "Your teleporter was destroyed!");

                teleporterA = null;
                teleporterB = null;
                p.playSound(p,Sound.BLOCK_BEACON_DEACTIVATE,1.0f,0.7f);
                e.remove(Material.IRON_INGOT);
                e.remove(Material.BEACON);
                setCooldown("Teleporter",50,Sound.BLOCK_BEACON_ACTIVATE,() -> {
                    e.setItem(2,teleporter);
                });
            }


        }

        public void handlePlayerTeleport() {
            for (org.bukkit.entity.Entity e : p.getWorld().getNearbyEntities(tpLoc,0.5,0.5,0.5)) {
                if (
                        e instanceof Player pe &&
                                main.game.sameTeam(p.getUniqueId(),pe.getUniqueId()) &&
                                !playerBuffer.containsKey(pe)) {

                    Location destination = otherTeleporter.tpLoc;
                    destination.setPitch(pe.getLocation().getPitch());
                    destination.setYaw(pe.getLocation().getYaw());
                    pe.teleport(destination);
                    pe.getWorld().playSound(destination,Sound.ITEM_GOAT_HORN_SOUND_1,0.7f,2.0f);
                    pe.getWorld().playSound(destination,Sound.BLOCK_DISPENSER_FAIL,0.7f,2.0f);
                    playerBuffer.put(pe,6);
                    otherTeleporter.playerBuffer.put(pe,6);


                }

                if (e instanceof Player pe) {
                    if (playerBuffer.containsKey(pe)) {
                        playerBuffer.put(pe,playerBuffer.get(p) + 1);
                    }
                }
            }



            for (Player p : playerBuffer.keySet()) {
                playerBuffer.put(p,playerBuffer.get(p) - 1);
                if (playerBuffer.get(p) <= 0) {
                    playerBuffer.remove(p);
                }
            }

        }

        public void activate() {
            active = true;
            createTeleporterBlocks(tpLoc.clone().add(0,-2,0));
        }

        public void deactivate() {
            for (Block b : teleporterBlocks) {
                b.setType(Material.AIR);
            }
        }

        public void cancel() {
            deactivate();
            super.cancel();
        }
    }


    //in the future we should polish this and add it as an alternate ability
    public void overload() {
        new BukkitRunnable() {

            int timer = 0;
            @Override
            public void run() {
                Location l = p.getLocation().add(0,2,0);
                World w = l.getWorld();
                Vector dir = p.getLocation().getDirection().multiply(1.8);
                timer++;
                if(timer > 120) {
                    this.cancel();
                }
                FallingBlock fire = p.getWorld().spawnFallingBlock(l, Material.FIRE.createBlockData());
                dir.multiply(Math.random()*0.4);
                FallingBlock web = p.getWorld().spawnFallingBlock(l, Material.COBWEB.createBlockData());
                fire.setVelocity(dir);
                web.setVelocity(dir);
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, p.getLocation().add(dir),3, 0.1,0.1,0.1);
                w.spawnParticle(Particle.FLAME, p.getLocation().add(dir),3, 0.1,0.1,0.1);
                p.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 3f, 3f);
            }
        }.runTaskTimer(main, 0L, 1L);
    }

    public class SnowmanTurret extends SnowGolem {

        public Engineer engineer;
        double maxHealth = 10;
        int fireRate = 0;
        int maxFireRate = 0;
        ChatColor teamColor = (main.game.redHas(p)) ? ChatColor.RED : ChatColor.BLUE;
        Snowman snowMan;

        public SnowmanTurret(EntityType<? extends SnowGolem> entitytypes, Level world, Location loc) {
            super((EntityType)entitytypes, world);

            this.setPos(loc.getX(), loc.getY(), loc.getZ());
            world.addFreshEntity(this);
            Snowman s = (Snowman) this.getBukkitEntity();
            snowMan = s;
            s.getAttribute(Attribute.MAX_HEALTH).setBaseValue(maxHealth);
            s.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(10.0f);
            s.getAttribute(Attribute.FOLLOW_RANGE).setBaseValue(0f);
            s.setHealth(maxHealth);
            s.setCustomNameVisible(true);
            s.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 99999999, 2));
            s.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 99999999, 0));
//            Bukkit.broadcastMessage("hi");
        }

        public void registerGoals() {
//            Snowman s = (Snowman) this.getBukkitEntity();
//            int fireRate = (int) (4 - ((s.getHealth() / maxHealth) * 3));;

            this.goalSelector.addGoal(1, (Goal)new RangedAttackGoal(this, 1.25D, 1, 30.0F));
            this.goalSelector.addGoal(2, (Goal)new WaterAvoidingRandomStrollGoal(this, 1.0D, 1.0000001E-5F));
            this.goalSelector.addGoal(3, (Goal)new LookAtPlayerGoal((Mob)this, Pig.class, 6.0F));
            this.goalSelector.addGoal(4, (Goal)new RandomLookAroundGoal((Mob)this));
//            this.targetSelector.addGoal(1, (Goal)new NearestAttackableTargetGoal((Mob)this, net.minecraft.world.entity.player.Player.class, 30, true, false, entityliving -> entityliving instanceof net.minecraft.world.entity.player.Player));
            Mob.createMobAttributes().add(Attributes.MAX_HEALTH, maxHealth).add(Attributes.MOVEMENT_SPEED, 0D).add(Attributes.KNOCKBACK_RESISTANCE,10.0D);
        }


        public void performRangedAttack(LivingEntity entityliving, float f) {
//            Snowball entitysnowball = new Snowball(EntityType.SNOWBALL,this.level());
//            double d0 = entityliving.getEyeY() - 1.100000023841858D;
//            double d1 = entityliving.getX() - getX();
//            double d2 = d0 - entitysnowball.getY();
//            double d3 = entityliving.getZ() - getZ();
//            double d4 = Math.sqrt(d1 * d1 + d3 * d3) * 0.20000000298023224D;

            if (fireRate > 0) {
                fireRate--;
                return;
            } else {
                fireRate = (int) Math.floor(Math.clamp(((maxHealth - snowMan.getHealth()) / 10) * 3,0,3));
            }

            Projectile s = snowMan.launchProjectile(org.bukkit.entity.Snowball.class);

            s.setVelocity(s.getVelocity().multiply(0));
            s.setCustomName(p.getUniqueId().toString());
            Vec3 tPos = entityliving.getEyePosition();
            Vector targetPos = new Vector(tPos.x,tPos.y,tPos.z);
            Vector eyeDir = targetPos.subtract(snowMan.getEyeLocation().toVector()).normalize();
            eyeDir =  Grandpa.randomizeVectorAngle(eyeDir,0.25);
            s.setVelocity(eyeDir.multiply(1.9f));
            playSound(SoundEvents.SNOW_GOLEM_SHOOT, 1.0F, 0.4F / (getRandom().nextFloat() * 0.4F + 0.8F));
        }

        public void aiStep() {
            super.aiStep();
            Snowman s = (Snowman) this.getBukkitEntity();
            HashMap<Double, Player> dubs = new HashMap<Double, Player>();

            s.setCustomName(ChatColor.BOLD + Kit.getProgressIndicator((s.getHealth() / maxHealth),20,teamColor,ChatColor.DARK_GRAY));

            s.setDerp(s.getHealth() < 4);

            for(org.bukkit.entity.Entity e : s.getNearbyEntities(20, 20, 20)) {
                if(e instanceof Player) {
                    Player p1 = (Player) e;
                    if(p1.getGameMode() == GameMode.SPECTATOR)
                        continue;

                    if (p1.hasPotionEffect(PotionEffectType.INVISIBILITY) && !p1.hasPotionEffect(PotionEffectType.GLOWING)) continue;

                    if (main.game.sameTeam(p1.getUniqueId(),p.getUniqueId())) continue;

                    dubs.put(s.getLocation().distance(p1.getLocation()), p1);
                }
            }

            if (dubs.isEmpty()) {
                s.setTarget(null);
                return;
            }

            double min = Collections.min(dubs.keySet());
            Player target = dubs.get(min);
            s.setTarget(target);
        }

        public void die(DamageSource d) {
            super.die(d);
            if (main.kit.kits.containsValue(engineer)) {
                engineer.onTurretDeath();
            }
        }

    }
}

