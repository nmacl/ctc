package org.macl.ctc.kits;

import io.papermc.paper.registry.data.InstrumentRegistryEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.NBTComponentBuilder;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.minecraft.sounds.Music;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.level.Level;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Snow;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.entity.Snowball;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.MusicInstrumentMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.macl.ctc.Main;

import java.util.*;

public class Operator extends Kit {

    boolean inBovineStrike = false;
    boolean canExitStrike = false;
    int strikeStacks = 3;
    BovineStrikeProcess bovineProcess;

    ArrayList<Puller> pullerArray = new ArrayList<>();

    ItemStack bovineStrike = newItemEnchanted(Material.NETHERITE_HOE, ChatColor.RED + "L0-27 Bovine Strike",Enchantment.SHARPNESS,1);
    ItemStack spreaders = newItem(Material.ECHO_SHARD,ChatColor.DARK_AQUA + "Spreaders",2);
    ItemStack puller = newItem(Material.SNOWBALL,ChatColor.WHITE + "Puller",3);
    ItemStack dislocator = newItem(Material.HOPPER,ChatColor.AQUA + "Dislocator");

    int spreaderStacks = 2;
    int pullerStacks = 3;

    ItemStack powerI = newItem(Material.GREEN_TERRACOTTA,ChatColor.GREEN + "Power I");;
    ItemStack powerII = newItem(Material.ORANGE_TERRACOTTA,ChatColor.GOLD + "Power II");;
    ItemStack powerIII = newItem(Material.RED_TERRACOTTA,ChatColor.RED + "Power III");;
    ItemStack powerFull = newItem(Material.GRAY_TERRACOTTA,ChatColor.DARK_RED + "" + ChatColor.BOLD + "POWER 999");
    ItemStack lackStacks = newItem(Material.BLACK_TERRACOTTA,ChatColor.DARK_GRAY + "NOT ENOUGH STACKS!");;

    RegenItem spreaderRegen;
    RegenItem pullerRegen;

    public Operator(Main main, Player p, KitType type) {
        super(main, p, type);

        ArrayList<Enchantment> enchants = new ArrayList<Enchantment>();
        enchants.add(Enchantment.SHARPNESS);
        ItemMeta i = bovineStrike.getItemMeta();
        i.setMaxStackSize(16);
        bovineStrike.setItemMeta(i);
        PlayerInventory e = p.getInventory();
        setStrikeStacks(3);
        e.addItem(bovineStrike);
        e.addItem(spreaders);
        e.addItem(puller);
        e.addItem(dislocator);
        ItemStack helmet = newItem(Material.LEATHER_HELMET, "Operator's Cap");
        LeatherArmorMeta hm = (LeatherArmorMeta) helmet.getItemMeta();
        hm.setColor(Color.fromRGB(80,80,80));
        helmet.setItemMeta(hm);
        e.setHelmet(helmet);
        ItemStack chestplate = newItem(Material.LEATHER_CHESTPLATE, "Suit");
        LeatherArmorMeta cm = (LeatherArmorMeta) chestplate.getItemMeta();
        cm.setColor(Color.fromRGB(60,60,60));
        chestplate.setItemMeta(cm);
        e.setChestplate(chestplate);
        e.setLeggings(newItem(Material.NETHERITE_LEGGINGS, ChatColor.WHITE + "Netherite Leggings"));
        e.setBoots(newItem(Material.CHAINMAIL_BOOTS, ChatColor.YELLOW + "Chainmail Boots"));

        spreaderRegen = regenItem("Spreaders",spreaders,16,2,1);
        pullerRegen = regenItem("Puller",puller,8,3,2);

        giveWool();
        giveWool();
        setHearts(20);
    }

    public void useBovineStrike() {
        if (!inBovineStrike) {
            if (strikeStacks <= 0) {
                p.sendMessage(ChatColor.DARK_GRAY + "NOT ENOUGH STACKS!");
                return;
            }
            cancelCooldown("strike");
            inBovineStrike = true;
            displayCooldowns = false;
            canExitStrike = false;

            bovineProcess = new BovineStrikeProcess();
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 2f, 0.6f);
            runTaskTimer(bovineProcess, 2L, 1L);

            BukkitTask t = new BukkitRunnable() {
                @Override
                public void run() {
                    setBovineStrikeHotbar();
                    canExitStrike = true;
                }
            }.runTaskLater(main,2L);
            registerTask(t);

        } else if (canExitStrike){
            int power = bovineProcess.strikePower;
            if (strikeStacks >= power) {
                setStrikeStacks(strikeStacks - power);
                if (bovineProcess.superStrike) {
                    setStrikeCooldown(20);
                } else setStrikeCooldown(11);
                summonBovineStrike(power);
                e.setHeldItemSlot(0);
                exitBovineStrike();
            } else {
                p.sendMessage(ChatColor.DARK_GRAY + "NOT ENOUGH STACKS!");
            }
        }
    }

    public void setBovineStrikeHotbar() {
        spreaderStacks = spreaderRegen.getCurrentItemCount();
        pullerStacks = pullerRegen.getCurrentItemCount();

        e.setItem(0,powerI);
        e.setItem(1,powerII);
        e.setItem(2,powerIII);
        e.setItem(3,powerFull);
        if (strikeStacks < 3) {
            e.setItem(2,lackStacks);
            e.setItem(3,lackStacks);
        }
        if (strikeStacks < 2) {
            e.setItem(1, lackStacks);
        }
        if (strikeStacks < 1) {
            e.setItem(0,lackStacks);
        }
    }

    public void cleanBovineStrikeHotbar() {
        e.setItem(0,bovineStrike);
        spreaders.setAmount(spreaderStacks);
        e.setItem(1,spreaders);
        puller.setAmount(pullerStacks);
        e.setItem(2,puller);
        e.setItem(3,dislocator);
    }

    public class BovineStrikeProcess extends BukkitRunnable {
        int timeSpent = 0;
        int timeLeft = 150;
        Location lookAtLoc;
        double rayLength = 0;
        int strikePower = 1;
        boolean superStrike = false;

        ArrayList<RegenItem> pcds = new ArrayList<>();

        Particle.DustOptions inRangeDust = new Particle.DustOptions((main.game.redHas(p) ? Color.RED : Color.BLUE),4f);
        Particle.DustOptions outRangeDust = new Particle.DustOptions(Color.GRAY,8f);


        public BovineStrikeProcess() {
            pcds.add(spreaderRegen);
            pcds.add(pullerRegen);

            for (RegenItem r : pcds) r.paused = true;
        }

        public void run() {
            timeSpent++;
            if (timeLeft-- <= 0) {
                setStrikeCooldown(11);
                this.cancel();
                return;
            }

            p.setLevel((int)Math.ceil((double) timeLeft / 20));

            handleLookAtLoc();

            setCrosshair();

            setActionBar();
        }

        public void handleLookAtLoc() {
            RayTraceResult lookHit = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 500.0,FluidCollisionMode.NEVER);
            if (lookHit == null) {
                rayLength = 500;
                lookAtLoc = p.getEyeLocation().getDirection().normalize().multiply(500).toLocation(p.getWorld());
                return;
            }
            lookAtLoc = lookHit.getHitPosition().toLocation(p.getWorld());
            rayLength = lookAtLoc.distance(p.getEyeLocation());
        }

        public void setCrosshair() {
            if (rayLength > 100) {
                p.spawnParticle(Particle.DUST, lookAtLoc, 4,0.3,0.3,0.3,0.0,outRangeDust,true);
            } else {
                p.spawnParticle(Particle.ELECTRIC_SPARK, lookAtLoc, 8,0.15,0.15,0.15,0.0,null,true);
            }
        }

        public void setActionBar() {
            String message = "";

            ChatColor chat = (rayLength > 100) ? ChatColor.DARK_GRAY : ChatColor.RED;
            ChatColor meters = (rayLength > 100) ? ChatColor.GRAY : ChatColor.GOLD;

            String lengthString = "" + String.format("%.1f", rayLength) + "m ";
            message += chat + "" + ChatColor.BOLD + "RANGE - "+ meters + "" + ChatColor.BOLD + lengthString;
            if (!(rayLength > 100)) {
                message += chat + "" + ChatColor.BOLD + "MARK IN RANGE";
            } else {
                message += chat + "" + ChatColor.BOLD + "OUT OF RANGE!!";

            }

            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message.toString()));
        }

        public void setStrikePower(int power) {
            strikePower = Math.clamp(power,1,4);
        }

        public void resolvePausedCooldowns() {
            for (RegenItem r : pcds) {
                r.timeLeft -= timeSpent;
                r.paused = false;
            }
        }

        public void cancel() {
            super.cancel();
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(""));
            cleanBovineStrikeHotbar();
            resolvePausedCooldowns();
            inBovineStrike = false;
            bovineProcess = null;
            displayCooldowns = true;
            p.setLevel(0);
        }

    }

    public void exitBovineStrike() {
        if (bovineProcess == null) return;
        bovineProcess.cancel();
    }

    public boolean holdingPowerTerracotta() {
        return e.getItemInMainHand().getType().name().endsWith("_TERRACOTTA");
    }

    public void setStrikeStacks(int stacks) {
        strikeStacks = stacks;

        Damageable d = (Damageable) bovineStrike.getItemMeta();
        if (stacks > 0) {
            bovineStrike.setAmount(stacks);
            d.setDamage(0);
        } else {
            bovineStrike.setAmount(1);
            d.setDamage(2030);
        }
        bovineStrike.setItemMeta(d);
        if (e.contains(Material.NETHERITE_HOE)) e.setItem(0,bovineStrike);
    }

    public void setStrikeCooldown(int cooldown) {
        if (!isOnCooldown("strike") && strikeStacks < 3) {
            setCooldown("strike",cooldown,Sound.ITEM_BONE_MEAL_USE, () -> {
                setStrikeStacks(strikeStacks + 1);
                BukkitTask b = new BukkitRunnable() {
                    @Override
                    public void run() {
                        setStrikeCooldown(11);
                    }
                }.runTaskLater(main,1L);
                registerTask(b);
            });
        }
    }

    public void summonBovineStrike(int power) {
        if (holdingPowerTerracotta() && e.getHeldItemSlot() == 3) power = 4;
        new BovineStrikeMissile(bovineProcess.lookAtLoc,power).runTaskTimer(main,0L,1L);
    }

    public class BovineStrikeMissile extends BukkitRunnable {
        Particle.DustOptions teamDust;
        Location strikeLoc;
        int power;
        boolean onRed;

        int maxTime;
        int timeLeft;

        double damage;
        double range;
        double falloff;
        boolean destroysBlocks;

        Cow cow;

        public BovineStrikeMissile(Location sLoc, int power) {
            this.power = power;
            this.strikeLoc = sLoc;
            main.broadcast("" + power);
            resolvePropertiesFromPower();
            onRed = main.game.redHas(p);
            createCow();
            if (onRed) {
                teamDust = new Particle.DustOptions(Color.fromRGB(255, 0, 0), 0.8f);
            } else {
                teamDust = new Particle.DustOptions(Color.fromRGB(0, 0, 255), 0.8f);
            }
        }

        public void run() {
            if (timeLeft > 0) {
                timeLeft--;
                createRing();
                handleCow();
            } else {
                onTimeRunOut();
                this.cancel();
            }
        }

        public void createCow(){
            cow = p.getWorld().spawn(strikeLoc.clone().add(0,50,0),Cow.class);
            cow.setInvulnerable(true);
            if (power < 2) {
                cow.setBaby();
            }
        }

        public void handleCow(){
            double cowY = (50 * ((double) timeLeft / maxTime));
            Location tpLoc = strikeLoc.clone();
            tpLoc.setY(strikeLoc.getY() + cowY);
            tpLoc.setRotation((timeLeft * 36 * 2.0f), 0.0f);
            if (timeLeft % 4 == 0) {
                cow.teleport(tpLoc);
                cow.getWorld().playSound(cow.getLocation(), Sound.ENTITY_COW_AMBIENT, 2.0f, 0.9f);
                cow.getWorld().playSound(cow.getLocation(), Sound.ENTITY_COW_HURT, 2.0f, 0.9f);
                cow.getWorld().playSound(cow.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_SHOOT, 5f, 0.5f);
            }
            cow.getWorld().spawnParticle(Particle.FLAME, cow.getLocation(), 10,0,0,0,0.3,null,true);
            cow.getWorld().spawnParticle(Particle.LARGE_SMOKE, cow.getLocation(), 3,0.2,0.2,0.2,0.05,null,true);
        }

        public void createRing(){
            Snowballer.createRingParticles(Particle.DUST,128,teamDust,range,strikeLoc);
            double innerRad = (range * ((double) timeLeft / maxTime));
            Snowballer.createRingParticles(Particle.ELECTRIC_SPARK,64,null,innerRad,strikeLoc);
        }

        public void onTimeRunOut() {
            cow.setHealth(0);
            main.fakeExplode(p,strikeLoc, (int) damage, (int) range,false,destroysBlocks,true,"bovine",falloff);
        }

        public void resolvePropertiesFromPower() {
            switch (power) {
                case 1:
                   maxTime = 45;
                   timeLeft = 45;
                   damage = 6;
                   destroysBlocks = false;
                   range = 2;
                   falloff = 0.8f;
                   break;
                case 2:
                    maxTime = 55;
                    timeLeft = 55;
                    damage = 10;
                    destroysBlocks = true;
                    range = 5;
                    falloff = 0.7f;
                    break;
                case 3:
                    maxTime = 65;
                    timeLeft = 65;
                    damage = 15;
                    destroysBlocks = true;
                    range = 6;
                    falloff = 0.6f;
                    break;
                case 4:
                    maxTime = 120;
                    timeLeft = 120;
                    damage = 40;
                    destroysBlocks = true;
                    range = 10;
                    falloff = 0.5f;
                    break;

            }
        }


    }

    public void hotbarSwitch(PlayerItemHeldEvent event) {
        if (!inBovineStrike && bovineProcess == null) return;

        if (holdingPowerTerracotta() && event.getNewSlot() == 3) {
            bovineProcess.superStrike = true;
            bovineProcess.setStrikePower(3);
            return;
        }

        bovineProcess.superStrike = false;
        if (holdingPowerTerracotta()) bovineProcess.setStrikePower(event.getNewSlot() + 1);
    }

    public void throwPuller(Snowball s) {
        p.getWorld().playSound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_BREAK,1.0f,2.0f);
        Puller pu = new Puller(s);
        pu.runTaskTimer(main,0L,1L);
        pullerArray.add(pu);

    }

    public class Puller extends BukkitRunnable{

        Snowball s;
        Location pullZoneLoc;
        boolean canCreatePullZone = true;
        boolean active = false;
        double rad = 3.5;
        double timeLeft = 20;

        public Puller(Snowball s) {
            this.s = s;
        }

        public void run() {
            if (!s.isValid()) {
                if (canCreatePullZone) createPullZone();
            } else {
                pullZoneLoc = s.getLocation();
                if (timeLeft < 12)
                    s.getWorld().spawnParticle(Particle.END_ROD,pullZoneLoc,3,0.05,0.05,0.05,0);
            }
            if (active) {
                if (timeLeft-- < 0) {
                    pullEntities();
                    pullerArray.remove(this);
                    this.cancel();
                    return;
                }
                checkNearbySnowballs();
                handlePullZone();
            }
        }

        public void createPullZone() {
            canCreatePullZone = false;
            active = true;
        }

        public void handlePullZone() {
            if (timeLeft % 2 == 0) {
                Artificer.createSphereParticle(rad,pullZoneLoc,128,Particle.SMALL_GUST,null);
            }
            Artificer.createSphereParticle(0.3,pullZoneLoc,32,Particle.SMALL_GUST,null);
            pullZoneLoc.getWorld().playSound(pullZoneLoc,Sound.ENTITY_HORSE_BREATHE,0.9f,0.8f);
        }

        public void checkNearbySnowballs() {
            for (Entity e : pullZoneLoc.getWorld().getNearbyEntities(pullZoneLoc,rad,rad,rad)) {
                if (e instanceof Snowball sn) {
                    for (Puller pu : pullerArray) {
                        if (pu.s == sn) {
                            pullZoneLoc.getWorld().playSound(pullZoneLoc,Sound.BLOCK_AMETHYST_BLOCK_BREAK,2.0f,0.4f);
                            pullZoneLoc.getWorld().spawnParticle(Particle.FLASH,pullZoneLoc,1,Color.WHITE);
                            Archer.createParticleLine(p,sn.getLocation(),pullZoneLoc,Particle.END_ROD,null,7.0,0.1);
                            pu.canCreatePullZone = false;
                            sn.remove();
                            pu.cancel();
                            this.timeLeft += 15;
                            this.rad += 0.75;
                        }
                    }
                }
            }
        }

        public void pullEntities() {
            for (Entity e : pullZoneLoc.getWorld().getNearbyEntities(pullZoneLoc,rad,rad,rad)) {
                Location toCenter = (pullZoneLoc.clone().subtract(e.getLocation()));
                double length = toCenter.length();
                double ratio = (length / rad);
                e.setVelocity(toCenter.toVector().normalize().multiply((rad / 3) * ratio));
            }
            Artificer.createSphereParticle(rad,pullZoneLoc,128,Particle.WAX_OFF,null,true,-0.2);
            Artificer.createSphereParticle(rad,pullZoneLoc,128,Particle.FIREWORK,null,true,-0.25);
            pullZoneLoc.getWorld().playSound(pullZoneLoc,Sound.ENTITY_BREEZE_INHALE,0.9f,2);
        }
    }

    public void sendDislocatorBeam() {
        if (isOnCooldown("dislocator")) return;
        setCooldown("dislocator",14,Sound.BLOCK_SCULK_CHARGE);

        p.getWorld().playSound(p.getLocation(),Sound.ENTITY_BREEZE_INHALE,5.5f,1.2f);
        p.getWorld().spawnParticle(Particle.SONIC_BOOM,p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(1.2)),1);

        new BukkitRunnable() {
            @Override
            public void run() {
                p.setVelocity(p.getEyeLocation().getDirection().normalize().multiply(-0.2));

                RayBeam beam = new RayBeam(
                        p.getEyeLocation(),
                        p.getEyeLocation().getDirection(),
                        5,
                        0.6,
                        true,
                        true

                );
                beam.runTaskTimer(main,0L,2L);

                beam.lifetime = 30;
                p.getWorld().playSound(p.getLocation(),Sound.ENTITY_WARDEN_SONIC_BOOM,5.5f,0.7f);

                new BukkitRunnable() {
                    ArrayList<LivingEntity> playersHit = new ArrayList<>();
                    public void run() {
                        p.getWorld().spawnParticle(Particle.SONIC_BOOM,beam.getRayLocation(),1);
                        p.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS,beam.getRayLocation(),12,0.3,0.3,0.3,0.01);
                        p.getWorld().spawnParticle(Particle.SCRAPE,beam.getRayLocation(),3,0.3,0.3,0.3,0.01);
                        p.getWorld().spawnParticle(Particle.SONIC_BOOM,beam.getRayLocation().clone().subtract(beam.rayDir.clone().multiply(0.25)),1);

                        p.getWorld().playSound(beam.getRayLocation(),Sound.BLOCK_LAVA_POP,1.6f,(float)((Math.random() * 0.3f)));
                        p.getWorld().playSound(beam.getRayLocation(),Sound.ENTITY_BREEZE_DEFLECT,1.6f,(float)((Math.random() * 0.3f)));

                        RayTraceResult r = beam.castRay();

                        if (beam.isCancelled()) {
                            this.cancel();
                            return;
                        }

                        if (!r.getHitPosition().isZero()) {
                            beam.cancel();
                            shockwave(r.getHitPosition().toLocation(p.getWorld()));
                        }

                        if (r.getHitEntity() instanceof LivingEntity le) {
                            if (!playersHit.contains(le)) {
                                playersHit.add(le);
                                Vector toPlayer = p.getLocation().subtract(le.getLocation()).toVector().normalize();
                                double leVelY = le.getVelocity().getY();
                                le.setVelocity(new Vector(toPlayer.getX(),leVelY + 0.6,toPlayer.getZ()));
                                if (le instanceof Player pe) {
                                    main.combatTracker.setHealth(pe, pe.getHealth() - 3.0, p, "dislocator");
                                } else {
                                    le.damage(6.0);
                                }
                            }
                        }

                    }

                    public void shockwave(Location hitLoc) {
                        hitLoc = hitLoc.add(0,0.4,0);
                        Snowballer.createRingParticles(Particle.SCULK_CHARGE,256,0f,4,hitLoc,false,0.1);
                        Snowballer.createRingParticles(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS,128,null,4,hitLoc,false,0.1);
                        hitLoc.getWorld().playSound(hitLoc,Sound.ITEM_MACE_SMASH_GROUND_HEAVY,2.0f,0.9f);
                        for (Entity e : p.getWorld().getNearbyEntities(hitLoc,4,1,4)) {
                            if (e instanceof LivingEntity le) {
//                                if (!le.hasLineOfSight(hitLoc)) continue;
                                Vector toPlayer = hitLoc.subtract(le.getLocation()).toVector().normalize();
                                double leVelY = le.getVelocity().getY();
                                le.setVelocity(new Vector(toPlayer.getX(),leVelY + 1.4,toPlayer.getZ()));
                                if (le instanceof Player pe) {
                                    main.combatTracker.setHealth(pe, pe.getHealth() - 3.0, p, "dislocator");
                                } else {
                                    le.damage(6.0);
                                }
                            }
                        }
                    }

                }.runTaskTimer(main,0L,2L);
            }
        }.runTaskLater(main,8L);
    }

    public void sendSpreaderCapsule() {
        e.getItem(e.first(Material.ECHO_SHARD)).setAmount(e.getItem(e.first(Material.ECHO_SHARD)).getAmount() - 1);

        RayBeam beam = new RayBeam(
                p.getEyeLocation(),
                p.getEyeLocation().getDirection(),
                2,
                0.5,
                true,
                true

        );
        beam.runTaskTimer(main,0L,1L);

        beam.lifetime = 50;

        p.getWorld().playSound(p.getLocation(),Sound.BLOCK_SCULK_SENSOR_CLICKING,2.0f,2.0f);
        p.getWorld().playSound(p.getLocation(),Sound.ENTITY_BREEZE_LAND,2.0f,1.0f);
        p.getWorld().playSound(p.getLocation(),Sound.ENTITY_BREEZE_JUMP,2.0f,1.0f);

        Particle.DustOptions sculkDust = new Particle.DustOptions(Color.fromRGB(3,74,100),1.6f);

        new BukkitRunnable() {
            public void run() {
                Location secondPos = beam.getRayLocation().clone().add(beam.rayDir.clone().multiply(beam.raySpeed *beam.stepRange));
                Archer.createParticleLine(p,beam.getRayLocation(),secondPos,sculkDust,10.0,0.0);
                p.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS,beam.getRayLocation(),1,0.1,0.1,0.1,0);

                RayTraceResult r = beam.castRay();

                if (beam.isCancelled()) {
                    this.cancel();
                    return;
                }

                if (beam.blockHit != null) {
                    new BlockSpreader(beam.blockHit).runTaskTimer(main,0L,1L);
                    beam.cancel();
                    this.cancel();
                }

                if (r.getHitEntity() instanceof LivingEntity le) {
                    if (le instanceof Player pe && main.game.sameTeam(pe.getUniqueId(),p.getUniqueId())) return;
                    new EntitySpreader(le,main.game.redHas(p)).runTaskTimer(main,0L,1L);
                    beam.cancel();
                    this.cancel();

                }

            }

        }.runTaskTimer(main,0L,1L);
    }

    public class EntitySpreader extends BukkitRunnable{
        LivingEntity target;
        boolean onRed = false;
        boolean traveling = false;
        int hangTime = 25;
        int searchTime = 20;
        RayBeam rayBeam;

        Particle.DustOptions sculkDust = new Particle.DustOptions(Color.fromRGB(3,74,100),1.6f);

        ArrayList<LivingEntity> infectedEntities = new ArrayList<>();

        public EntitySpreader(LivingEntity e,boolean onRed) {
            this.target = e;
            this.onRed = onRed;
            this.rayBeam = new RayBeam(e.getLocation().add(0,1,0),new Vector(1,1,1),1.5,1,true,false);
            this.rayBeam.runTaskTimer(main,0L,2L);
            rayBeam.lifetime = 99999999;
            enterTarget(target);
            rayBeam.rayLocation = target.getLocation().add(0,1,0);
            this.infectedEntities.add(e);

        }

        public void run() {
            hangOnTarget();

            RayTraceResult r = rayBeam.castRay();

            if (traveling) {
                this.rayBeam.rayDir = dirToTarget();
                spawnTravelParticles();
                if (r.getHitEntity() != null && r.getHitEntity() == target) {
                    this.traveling = false;
                    enterTarget(target);
                }
            }
        }

        public Vector dirToTarget() {
            if (target == null) return new Vector();
            return (target.getLocation().add(0,1,0).subtract(rayBeam.getRayLocation().clone()).toVector().normalize());
        }

        public void hangOnTarget() {
//            main.broadcast("" + hangTime);
//            main.broadcast("" + rayBeam.rayDir);

            if (traveling) return;

            if (hangTime > 0) {
                hangTime--;
                spawnHangParticles(false);
                traveling = false;
            } else if (searchTime > 0) {
                searchTime--;
                spawnHangParticles(false);
                findNewTarget();
            } else {
                die();
            }
        }

        public void findNewTarget() {
            if (traveling) return;
            for(Entity e : target.getNearbyEntities(7, 7, 7)) {
                if(e instanceof LivingEntity le) {
                    if (infectedEntities.contains(le)) continue;

                    if (le instanceof Player pe) {
                        Player p1 = (Player) e;
                        if(p1.getGameMode() == GameMode.SPECTATOR)
                            continue;

                        if (p1.hasPotionEffect(PotionEffectType.INVISIBILITY) && !p1.hasPotionEffect(PotionEffectType.GLOWING)) continue;

                        if (main.game.sameTeam(p1.getUniqueId(),p.getUniqueId())) continue;

                        if (main.game.redHas(pe) == onRed) continue;

                        rayBeam.rayLocation = target.getLocation().add(0,1,0);
                        damageTarget();
                        seekNewTarget(le);
                        break;

                    } else {
                        rayBeam.rayLocation = target.getLocation().add(0,1,0);
                        damageTarget();
                        seekNewTarget(le);
                        break;
                    }
                }
            }
        }

        public void seekNewTarget(LivingEntity target){
            this.traveling = true;
            infectedEntities.add(target);
            this.target = target;
        }

        public void enterTarget(LivingEntity target){
            damageTarget();
            rayBeam.rayLocation = target.getLocation().add(0,1,0);
            this.hangTime = 25;
            this.searchTime = 20;
            this.traveling = false;
        }

        public void damageTarget() {
            target.getWorld().playSound(target.getLocation(),Sound.BLOCK_SCULK_SENSOR_CLICKING,2.0f,1.5f);
            target.getWorld().playSound(target.getLocation(),Sound.ENTITY_BREEZE_LAND,2.0f,0.7f);
            target.getWorld().playSound(target.getLocation(),Sound.ENTITY_BREEZE_JUMP,2.0f,0.7f);
            if (target instanceof Player pe) {
                main.combatTracker.setHealth(pe,pe.getHealth() - 1.5,p,"spreader");
            } else {
                target.damage(2.5);
            }
        }

        public void die() {
            target.getWorld().playSound(target.getLocation(),Sound.ENTITY_ALLAY_DEATH,0.7f,0.3f);
            rayBeam.cancel();
            this.cancel();
        }

        public void playSound() {
            p.getWorld().playSound(p.getLocation(),Sound.BLOCK_SCULK_SENSOR_CLICKING,2.0f,2.0f);
            p.getWorld().playSound(p.getLocation(),Sound.ENTITY_BREEZE_LAND,2.0f,1.0f);
            p.getWorld().playSound(p.getLocation(),Sound.ENTITY_BREEZE_JUMP,2.0f,1.0f);
        }

        public void spawnTravelParticles() {
//            Location secondPos = rayBeam.getRayLocation().clone().add(rayBeam.rayDir.clone().multiply(rayBeam.raySpeed * rayBeam.stepRange));
//            Archer.createParticleLine(p,rayBeam.getRayLocation(),secondPos,sculkDust,10.0,0.0)
            p.getWorld().spawnParticle(Particle.DUST,rayBeam.getRayLocation(),3,0.1,0.1,0.1,0,sculkDust);;
            p.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS,rayBeam.getRayLocation(),1,0.1,0.1,0.1,0);
        }

        public void spawnHangParticles(boolean search) {
            double boundX = 0;
            double boundY = 0;
            double boundZ = 0;

            int count = 4;

            boundX = target.getBoundingBox().getWidthX();
            boundY = target.getBoundingBox().getHeight();
            boundZ = target.getBoundingBox().getWidthZ();

            if (search) {
                count = 2;
            }


            p.getWorld().spawnParticle(Particle.DUST,target.getLocation().add(0,1.1,0),count,boundX / 2,boundY / 2,boundZ / 2,0,sculkDust);
        }

    }

    public class BlockSpreader extends BukkitRunnable{

        Block initialBlock;
        int spreadsLeft = 90;
        int randomThreshold = 60;
        int timeLeft = 30;
        ArrayList<Block> markedBlocks = new ArrayList<>();
        ArrayList<Block> infectedBlocks = new ArrayList<>();

        public BlockSpreader(Block b) {
            this.initialBlock = b;
            if (main.restricted.contains(b.getType())) {
                this.cancel();
                return;
            }
            infectedBlocks.add(initialBlock);
        }

        public void run() {
            if (spreadsLeft > 0) {
                for(int i = 0; i < 2; i++){
                    spreadsLeft--;
                    stepInfection();
                }
            } else {
                if (timeLeft-- <= 0) {
                    for(int i = 0; i < 3; i++){
                        if (markedBlocks.isEmpty()) break;
                        Block chosenOne = markedBlocks.getFirst();
                        chosenOne.setType(Material.AIR);
                        markedBlocks.remove(chosenOne);
                        chosenOne.getWorld().playSound(chosenOne.getLocation(),Sound.BLOCK_SCULK_PLACE,1.5f,1.0f);
                    }
                    if (markedBlocks.isEmpty())
                        this.cancel();
                }
            }
        }

        public ArrayList<Block> getNearbyValidBlocks(Block b) {
           ArrayList<Block> nearbyBlocks = new ArrayList<Block>();
           for (int x = -1; x <= 1; x++) {
               for (int y = -1; y <= 1; y++) {
                   for (int z = -1; z <= 1; z++) {
                       if ((Math.abs(z) + Math.abs(y) + Math.abs(x)) == 3) continue; // literal corner cutting lol
                       Block bu = b.getRelative(x,y,z);
                       if (
                           !main.restricted.contains(bu.getType()) &&
                           bu.getType().isSolid() &&
                           bu.getType() != Material.SCULK &&
                           !markedBlocks.contains(bu)
                       ) {
                           nearbyBlocks.add(bu);
                       }
                   }
               }
           }
           return nearbyBlocks;
        }

        public void stepInfection() {
            Block chosenBlock = null;
            if (spreadsLeft > randomThreshold) {
                chosenBlock = infectedBlocks.getFirst();
            } else {
                Random r = new Random();
                int randomIndex = r.nextInt(infectedBlocks.size());
                chosenBlock = infectedBlocks.get(randomIndex - 1);
            }
            if (chosenBlock == null) return;
            infectedBlocks.remove(chosenBlock);
            markedBlocks.add(chosenBlock);
            chosenBlock.setType(Material.SCULK);
            ArrayList<Block> validBlocks = getNearbyValidBlocks(chosenBlock);
            if (validBlocks.size() == 1) {
                spreadsLeft -= 2;
            } else if (validBlocks.size() < 3) {
                spreadsLeft += 1;
            }
            infectedBlocks.addAll(validBlocks);
            infectedBlocks.removeIf(b -> b.getType() == Material.SCULK);
            chosenBlock.getWorld().playSound(chosenBlock.getLocation(),Sound.BLOCK_SCULK_PLACE,1.5f,1.0f);
        }

    }

    public class RayBeam extends BukkitRunnable{
        Location rayStart;
        Vector rayDir;
        Location rayLocation;
        double raySpeed = 2;
        double stepRange = 0.1;
        public double lifetime = 120;
        boolean hitsPlayers = false;
        boolean hitsBlocks = false;
        boolean hitTarget = false;
        public Block blockHit = null;

        public RayBeam(Location rS, Vector rD, double rSp, double stRn, boolean hitP, boolean hitB) {
            this.rayStart = rS;
            this.rayDir = rD;
            this.raySpeed = rSp;
            this.stepRange = stRn;
            this.hitsPlayers = hitP;
            this.hitsBlocks = hitB;

            rayLocation = rayStart;
            if (!rayDir.isNormalized()) rayDir = rayDir.normalize();
        }

        public Location getRayLocation() {
            return rayLocation;
        }

        public void run() {
            if (lifetime-- <= 0) {
                this.cancel();
            }

            if (Objects.equals(rayDir, new Vector())) return;

            rayLocation.add(rayDir.clone().multiply(stepRange).multiply(raySpeed));

        }

        public void rayHit() {
            this.cancel();
        }

        public RayTraceResult castRay() {
            RayTraceResult r = rayLocation.getWorld().rayTraceBlocks(rayLocation,rayDir,stepRange * raySpeed,FluidCollisionMode.NEVER,true);
            RayTraceResult r2 = rayLocation.getWorld().rayTraceEntities(rayLocation,rayDir,stepRange * raySpeed,0.8);

            Vector hitPos = new Vector();
            Block hitBlock = null;
            BlockFace hitFace = null;
            Entity hitEntity = null;

            if (r != null && hitsBlocks) {
                hitPos = r.getHitPosition();
                blockHit = r.getHitBlock();
                hitFace = r.getHitBlockFace();
            }
            if (r2 != null && hitsPlayers) {
                hitEntity = r2.getHitEntity();
            }
//            main.broadcast("" + hitPos + " "+ hitEntity);

            return new RayTraceResult(hitPos,hitEntity);
        }
    }

}

