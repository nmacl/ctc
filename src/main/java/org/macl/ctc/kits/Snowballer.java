package org.macl.ctc.kits;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.macl.ctc.Main;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Objects;

public class Snowballer extends Kit {

    public ItemStack rocketJump = newItem(Material.GOLDEN_HOE, ChatColor.YELLOW + "Rocket Jump");

    public ItemStack snowSlam = newItem(Material.POWDER_SNOW_BUCKET,ChatColor.AQUA + "Snow Slam");

    private int snowballICD = 0;

    private boolean isShooting = false;

    public Snowballer(Main main, Player p, KitType type) {
        super(main, p, type);

        BukkitTask process = new snowballerProcess().runTaskTimer(main,0,1);
        this.registerTask(process);

        e.addItem(newItem(Material.WOODEN_SWORD, ChatColor.BLUE + "Snowball Launcher"));
        e.addItem(rocketJump);
        e.addItem(snowSlam);
        e.setBoots(newItemEnchanted(Material.DIAMOND_BOOTS, "Feather Boots", Enchantment.FEATHER_FALLING, 7));
        giveWool();
        giveWool();
        setHearts(16);


    }

    public void shootSnowball() {
        if (snowballICD > 0) return;
        if (getSwordMeta() != null) {
            if (getSwordDamage() >= 55){
                return;
            }
        }

        snowballICD = 7;

        setSwordDamage(5);

        BukkitTask b = new BukkitRunnable()  {

            final float sPitch = (getSwordDamage() - 20) > 100f ? 100f : (getSwordDamage() - 20) < 0f ? 0f : (getSwordDamage() - 20);

            final float pitch = 0.1f +  (sPitch / 10);;

            int shots = 0;
            final int maxShots = 3;

            @Override
            public void run() {

                if (shots < maxShots) {
                    shots++;

                    Projectile s = p.launchProjectile(Snowball.class);
                    s.setVelocity(s.getVelocity().multiply(0));
                    s.setVelocity(p.getEyeLocation().getDirection().normalize().multiply(1.9));
                    p.getWorld().playSound(p.getLocation(),
                            Sound.ENTITY_EGG_THROW,
                            0.4f, pitch);

                } else{
                    snowballICD = 7;
                    cancel();
                }


            }
        }.runTaskTimer(main,0,1);
        this.registerTask(b);
    }

    public class snowballerProcess extends BukkitRunnable {

        int swordCount = 0;
        final int maxSwordCount = 4;

        @Override
        public void run() {
            failsafe();

            if (snowballICD > 0) {
                snowballICD--;
                swordCount = 0;
            } else {
                if (swordCount < maxSwordCount) {
                    swordCount++;
                } else {
                    swordCount = 0;
                    handleSwordRecharge();
                }
            }

        }

        public void handleSwordRecharge() {
            if (isShooting) {
                isShooting = false;
            } else {
                if (getSwordMeta() != null) {
                    if (getSwordDamage() > 0) setSwordDamage(-1);
                }
            }
        }

        public void failsafe() {
            if (main.kit.kits.get(p.getUniqueId()) == null) {
                cancel();
            }
            if (!(main.kit.kits.get(p.getUniqueId()) instanceof Snowballer)) {
                cancel();
            }
        }

    }

    public class rocketTrail extends BukkitRunnable {
        public float lastFall = 0.1f;
        double r = 1;
        double m = 0;
        double t = 0;
        public void run() {
            Location l = p.getLocation();
            Location l2 = p.getLocation();
            for(int i = 0; i < 3; i++) {
                m = m + Math.PI/32;
                double x = r*Math.cos(m);
                double y = r;
                double z = r*Math.sin(m);
                l.add(x,y,z);
                l2.subtract(x,-y,z);
                Particle.DustOptions blueDust = new Particle.DustOptions(Color.fromRGB(180, 225, 255), 2);;

                p.getWorld().spawnParticle(Particle.DUST,l,2, 0,0,0,blueDust);
                p.getWorld().spawnParticle(Particle.DUST,l2,2, 0,0,0,blueDust);

                l.subtract(x,y,z);
                l2.add(x,-y,z);
            }

            lastFall = p.getFallDistance();
            t++;
            Location loc = p.getLocation();
            loc.add(-0.3, -0.5, 0);

            p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,l,1,0,0,0,0);

            if (isOnGround() && t > 5) {
                this.cancel();
            }

            if(t == 20*3)
                this.cancel();
        }
    }

    public void launch() {
        if(!isOnCooldown("Rocket Jump")) {
            setCooldown("Rocket Jump", 9, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, () -> {
                p.getInventory().setItem(1, rocketJump);
            });

            p.setNoDamageTicks(8);

            p.getInventory().setItem(1, newItem(Material.STICK, ChatColor.GRAY + "Refueling..."));

            p.getWorld().playSound(p.getLocation(),Sound.ENTITY_BLAZE_SHOOT,0.5f,0.8f);

            p.setVelocity(p.getLocation().getDirection().multiply(2.0f));

            Vector l = p.getLocation().getDirection().multiply(-1.1);

            Firework backBlast = p.getLocation().getWorld().spawn(p.getLocation().add(l),Firework.class);

            FireworkMeta meta = backBlast.getFireworkMeta();
            backBlast.setShooter(p);
            meta.setPower(5);
            meta.addEffect(FireworkEffect.builder().withColor(Color.WHITE).with(FireworkEffect.Type.BURST).build());
            backBlast.setFireworkMeta(meta);
            backBlast.detonate();

            Collection<Entity> entities = p.getWorld().getNearbyEntities(p.getLocation().add(l), 1.15,1.15,1.15);
            for (Entity e : entities) {
                if (e != p) {
                    e.setFreezeTicks(100);
                    if (e instanceof Player victim) {
                        double dmg = 0.5;
                        double newHp = victim.getHealth() - dmg;

                        main.getCombatTracker().setHealth(victim, newHp, p, "backblast");
                    }
                }
            }

            BukkitTask t = new rocketTrail().runTaskTimer(main, 0L, 1L);
            this.registerTask(t);

        }
    }

    public void slam() {
        if(!isOnCooldown("Slam")) {
            setCooldown("Slam", 6, Sound.ITEM_BUCKET_FILL_POWDER_SNOW, () -> {
                p.getInventory().setItem(2, snowSlam);
            });
        }

        p.getInventory().setItem(2,newItem(Material.BUCKET,ChatColor.GRAY + "Slammed..."));

        slamProcess s = new slamProcess();

        s.initial_height = (float) p.getLocation().getY();

        BukkitTask sTask = s.runTaskTimer(main,0,1L);

        p.getWorld().playSound(p.getLocation(),Sound.ENTITY_ITEM_PICKUP, 0.8F,0.7f);

        p.setVelocity(new Vector(0,0.5,0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20, 1));

        FallingBlock b = p.getWorld().spawnFallingBlock(p.getLocation().add(0,1,0), Material.POWDER_SNOW.createBlockData());

        s.snowBlock = b;
        b.setGravity(false);

        p.addPassenger(b);


        this.registerTask(sTask);

    }


    public class slamProcess extends BukkitRunnable {
        boolean dirSnapped;
        Vector dir;
        float initial_height;

        int hover_time = 7;
        int current_hover_time = 0;

        FallingBlock snowBlock;

        @Override
        public void run() {
            if (current_hover_time <= hover_time) {
                current_hover_time ++;
                createRingParticles(Particle.CLOUD,10,null,1,p.getLocation().add(0,1,0));
                p.getWorld().playSound(p.getLocation(),Sound.ENTITY_HORSE_BREATHE, 0.3F,0.4f);
            } else {
                if (!dirSnapped) {
                    dirSnapped = true;
                    p.getWorld().playSound(p.getLocation(),Sound.ITEM_TRIDENT_THROW, 4.0F,0.6f);
                    snapshotDirection();
                }

//                main.broadcast("" + p.getFallDistance());
                if (p.getVelocity().length() < 2.5) {
                    descend();
                }


                if (isOnGround()) {
                    snowBlock.setGravity(false);
                    snowBlock.setCancelDrop(true);
                    p.removePassenger(snowBlock);

                    Vector downVector = p.getEyeLocation().getDirection();

                    downVector.setY(-0.75);

                    snowBlock.setVelocity(downVector.multiply(0.75f));
                    p.getWorld().playSound(p.getLocation(),Sound.BLOCK_SNOW_BREAK, 2.0F,0.8f);
                    p.getWorld().playSound(p.getLocation(),Sound.BLOCK_POWDER_SNOW_BREAK, 2.0F,0.8f);
                    spawn_slam();
                }
            }
        }

        public void descend() {
            p.setVelocity(p.getVelocity().multiply(0));
            p.setVelocity(dir.multiply(1.05f));
//            main.broadcast(String.valueOf((p.getVelocity().length())));
        }

        public void spawn_slam() {
            p.setVelocity(p.getVelocity().multiply(0.01));
            double heightFallen = (initial_height - p.getLocation().getY());
            double maxHeightFall = 35.0;
            double maxDamageGain = 9.0;
            double damageGain;

            if (heightFallen > maxHeightFall) {
                heightFallen = maxHeightFall;
            }

            damageGain = (maxDamageGain) * (heightFallen / maxHeightFall);

            if (damageGain < 0) {
                damageGain = 0;
            }

            Vector lookAtXY = p.getLocation().getDirection().setY(0).normalize();

            Location slamPos = p.getLocation().add(lookAtXY.multiply(2.25));

            createRingParticles(Particle.CLOUD,80,null,2,slamPos.clone().add(0,0.5,0));

            main.broadcast("" + (5.0 + damageGain));

            Collection<Entity> entities = p.getWorld().getNearbyEntities(slamPos,2,2,2);
//            main.fakeExplode(p,slamPos,0,2,false,false,true,"snowSlam");
            p.getWorld().createExplosion(slamPos, 1f, false, false);
            for (Entity e : entities) {
                if (e != p) {
                    e.setFreezeTicks(60);
                    if (e instanceof Player victim && !main.game.sameTeam(victim.getUniqueId(),p.getUniqueId())) {

                        double dmg = 5.0 + damageGain;
                        double newHp = victim.getHealth() - dmg;

                        main.getCombatTracker().setHealth(victim, newHp, p, "snowSlam");
                    }
                }
            }


            BukkitTask b = new BukkitRunnable(){
                @Override
                public void run() {
                    if (snowBlock != null) {
                        snowBlock.remove();
                    }
                }
            }.runTaskLater(main,10L);

            this.cancel();
        }

        public void snapshotDirection() {
            Vector downVector = p.getLocation().getDirection();

            if (downVector.getY() > -0.75 ) {
                downVector.setY(-0.75);
            }

            dir = downVector;
        }


    }

    public static <T> void createRingParticles(Particle particle, int count, @Nullable T data, double rad, Location loc,boolean inwards, double vel) {
        for (int i = 0; i < count; i++) {

            double theta = Math.random() * 2 * Math.PI;
            double x = rad * Math.cos(theta);
            double z = rad * Math.sin(theta);

            Location particleLocation = loc.clone().add(x, 0.0, z);

            if (!inwards) {
                x = 0;
                z = 0;
            }

            loc.getWorld().spawnParticle(particle, particleLocation, 0,x,0,z,vel,data);
        }
    }

    public static <T> void createRingParticles(Particle particle, int count, @Nullable T data, double rad, Location loc) {
        createRingParticles(particle,count,data,rad,loc,false,0.0);
    }

    private ItemStack getSword() {
        if (p.getInventory().getItem(0).getType() == Material.WOODEN_SWORD) {
            return p.getInventory().getItem(0);
        } else {
            return null;
        }
    }

    private ItemMeta getSwordMeta() {
        if (p.getInventory().getItem(0).getType() == Material.WOODEN_SWORD) {
            return p.getInventory().getItem(0).getItemMeta();
        } else {
            return null;
        }
    }

    private void setSwordDamage(int damage) {
        if (getSwordMeta() != null) {
            if (getSwordMeta() instanceof Damageable) {
                Damageable dmg = (Damageable) getSwordMeta();

                int totalDamage = getSwordDamage() + damage;
                if (totalDamage > 59) totalDamage = 59;

                dmg.setDamage(totalDamage);
                getSword().setItemMeta(dmg);
            }
        }
    }

    private int getSwordDamage() {
        if (getSwordMeta() != null) {
            if (getSwordMeta() instanceof Damageable) {
                return ((Damageable) getSwordMeta()).getDamage();
            }
        }
        return 0;
    }


    // CLEAN AND TURN INTO A KIT METHOD!

}
