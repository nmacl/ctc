package org.macl.ctc.kits;

import net.minecraft.world.entity.Entity;
import org.bukkit.*;
import org.bukkit.Color;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.macl.ctc.Main;

public class Demolitionist extends Kit {

    public boolean canEgg = true;

    public boolean eggJumping = false;

    public mineTask currentThrownMine;

    int eggTime = 0;
    int sheepTime = 0;

    public ItemStack sheepItem = newItem(Material.CARROT_ON_A_STICK, ChatColor.DARK_RED +"" + ChatColor.BOLD+ "Sheep Launcher");
    ItemStack grenade = newItem(Material.EGG, ChatColor.RED + "Egg Grenade");
    ItemStack mine = newItem(Material.STONE_PRESSURE_PLATE, ChatColor.GRAY + "Mine");

    public Demolitionist(Main main, Player p, KitType type) {
        super(main, p, type);
        CraftPlayer craft = (CraftPlayer) p;
        PlayerInventory e = p.getInventory();
        e.addItem(newItem(Material.STONE_SHOVEL, ChatColor.MAGIC + "OEIHRIOQW"));
        e.setHelmet(newItem(Material.CHAINMAIL_HELMET, ""));
        e.setChestplate(newItem(Material.CHAINMAIL_CHESTPLATE, ""));
        e.setBoots(newItem(Material.CHAINMAIL_BOOTS, ""));
        e.addItem(newItem(Material.EGG, ChatColor.RED + "Egg Grenade", 3));
        e.addItem(newItem(Material.STONE_PRESSURE_PLATE, ChatColor.GRAY + "Mine", 3));
        e.addItem(sheepItem);
        giveWool();
        regenItem("grenade", grenade, 10, 3, 1);
        regenItem("mine", mine, 16, 3, 2);
        setHearts(24);
    }

    public void launchSheep() {
        if(isOnCooldown("sheep"))
            return;
        Sheep g = (Sheep) p.getLocation().getWorld().spawnEntity(p.getLocation(), EntityType.SHEEP);
        g.setVelocity(p.getLocation().getDirection().multiply(1.35f));
        g.setBaby();
        g.setInvulnerable(true);
        BukkitTask shep = new sheepLaunch(g).runTaskTimer(main, 0L, 1L);
        registerTask(shep);
        setCooldown("sheep", 25, Sound.ENTITY_TNT_PRIMED);
    }

    public void throwMine() {
//        if (isOnCooldown("Throw")) return;
//        setCooldown("Throw", 3, Sound.BLOCK_STONE_HIT, () -> {});

        int mines = p.getInventory().first(Material.STONE_PRESSURE_PLATE);
        p.getInventory().getItem(mines).setAmount(p.getInventory().getItem(mines).getAmount() - 1);

        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 1.2f);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.0f, 0.9f);

        Vector dir = p.getLocation().getDirection();
        BlockData block = Material.STONE_PRESSURE_PLATE.createBlockData();
        FallingBlock mine = p.getWorld().spawnFallingBlock(
                p.getEyeLocation().subtract(0.0, 0.5, 0.0),
                block
        );

        Vector velocity = p.getVelocity();
        if (velocity.getY() < 0) velocity.setY(0);
        mine.setVelocity(dir.multiply(0.8).add(velocity.multiply(1.3)));

        mineTask m = new mineTask(mine);
        currentThrownMine = m;
        BukkitTask mineTask = m.runTaskTimer(main,0,1L);


    }

    public class mineTask extends BukkitRunnable {
        public FallingBlock fallMine;

        public mineTask(FallingBlock mine) {
            this.fallMine = mine;
        }

        public void run () {
            if (!fallMine.isValid()){
                this.cancel();
                return;
            }

            for (org.bukkit.entity.Entity e : p.getWorld().getNearbyEntities(fallMine.getLocation(),0.8,0.7,0.8)) {
                if (e instanceof Egg && ((Egg) e).getShooter() == p) {
                    if (isOnCooldown("Nuke")) return;
                    e.remove();
                    nuclearBomb();
                }
            }

        }

        public void nuclearBomb() {
            if (isOnCooldown("Nuke")) return; // need double for direct hit check
            setCooldown("Nuke", 30, Sound.BLOCK_ENCHANTMENT_TABLE_USE, () -> {});

            p.getWorld().playSound(fallMine.getLocation(),Sound.ITEM_TRIDENT_HIT,10f,1.1f);
            p.getWorld().playSound(fallMine.getLocation(),Sound.ITEM_TRIDENT_HIT,10f,0.3f);
            p.getWorld().playSound(fallMine.getLocation(),Sound.ENTITY_BREEZE_INHALE,10f,1.1f);
            p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,fallMine.getLocation(),40,0.2,0.2,0.2,0.2);
            p.getWorld().spawnParticle(Particle.FLASH,fallMine.getLocation(),3,0.2,0.2,0.2,0.2,Color.WHITE);
            fallMine.setGravity(false);
            fallMine.setVelocity(fallMine.getVelocity().multiply(0.0));

            new BukkitRunnable() {
                public void run() {
                    p.getWorld().playSound(fallMine.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 10f, 0.4f);
                    p.getWorld().spawnParticle(Particle.LARGE_SMOKE, fallMine.getLocation(), 80, 3, 3, 3, 0.2);
                    p.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, fallMine.getLocation(), 40, 3, 3, 3, 0.05);
                    p.getWorld().spawnParticle(Particle.CLOUD, fallMine.getLocation(), 50, 5, 5, 5, 0.15);
                    main.fakeExplode(p, fallMine.getLocation(), 28, 10, true, true, true, "Nuke",0.66f);
                    fallMine.remove();
                }
            }.runTaskLater(main,15L);

        }
    }




    public class sheepLaunch extends BukkitRunnable {
        int timer = 0;

        Sheep sheep;

        net.minecraft.world.entity.animal.sheep.Sheep shep;

        public sheepLaunch(Sheep sheep) {
            this.sheep = sheep;
            Entity entitySheep = ((CraftEntity)sheep).getHandle();
            shep = (net.minecraft.world.entity.animal.sheep.Sheep) entitySheep;
            sheep.setCustomNameVisible(true);
        }

        public void run() {
            timer++;
            for(org.bukkit.entity.Entity e : sheep.getNearbyEntities(50, 50, 50)) {
                if(e instanceof Player) {
                    Player p1 = (Player) e;

                    if(main.game.redHas(p1) && main.game.blueHas(p))
                        shep.getNavigation().moveTo(p1.getLocation().getX(), p1.getLocation().getY(), p1.getLocation().getZ(), 1.75f);
                    if(main.game.blueHas(p1) && main.game.redHas(p))
                        shep.getNavigation().moveTo(p1.getLocation().getX(), p1.getLocation().getY(), p1.getLocation().getZ(), 1.75f);
                }
            }

            double seconds = 5.0 - ((double) timer / 20);

            ChatColor c;

            if ((timer % 10) > 5) {
                c = ChatColor.DARK_RED;
            } else {
                c = ChatColor.GOLD;
            }

            String secondsString = String.format("%.1f",seconds);

            sheep.setCustomName((c + secondsString) + (ChatColor.GRAY + "s"));

            if(timer == 20)
                sheep.setColor(DyeColor.YELLOW);
            if(timer == 40)
                sheep.setColor(DyeColor.ORANGE);
            if(timer == 60) {
                sheep.setColor(DyeColor.RED);
                sheep.setAdult();
            }
            sheep.getLocation().getWorld().playSound(sheep.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1, (0.02f*timer));
//            sheep.getLocation().getWorld().spawnParticle(Particle.LARGE_SMOKE,sheep.getLocation().add(0,0.8,0),2,0,0,0,0.0);
            if(timer == 80)
                sheep.setColor(DyeColor.BLACK);
            if(timer == 100) {
                main.fakeExplode(p, sheep.getLocation(), 20, 8, true, true,true, "sheep");
                sheep.setHealth(0);
                this.cancel();
            }
        }
    }
}