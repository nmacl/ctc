package org.macl.ctc.kits;

import com.zaxxer.hikari.util.FastList;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.macl.ctc.Main;
import org.macl.ctc.kits.Kit;
import org.macl.ctc.kits.KitType;

import javax.annotation.Nullable;
import java.util.*;

public class Archer extends Kit {
    public void shoot(ProjectileLaunchEvent event) {
        ArrowType inHand = ArrowType.FLAME;

        switch(p.getInventory().getHeldItemSlot()) {
            case 0:
                inHand = ArrowType.FLAME;
                break;
            case 1:
                inHand = ArrowType.LIGHTNING;
                break;
            case 2:
                inHand = ArrowType.TELEPORT;
                break;
            case 3:
                inHand = ArrowType.ICE;
                break;
            case 4:
                inHand = ArrowType.CYCLONE;
                break;
            case 5:
                inHand = ArrowType.GRAVITY;
                break;
        }

        if (event.getEntity() instanceof Arrow a) {
            for (PotionEffect p : a.getCustomEffects()) {
                a.removeCustomEffect(p.getType());
            }
            a.setCustomName(arrowTypeToName(curArrow));
            a.setColor(getArrowPotionType(curArrow).getPotionEffects().get(0).getType().getColor());
        }

        if(inHand == ArrowType.CYCLONE) {
            BukkitTask cyc = new cycloneTimer(event.getEntity()).runTaskTimer(main, 0L, 1L);
            registerTask(cyc);
        }
        if(inHand != ArrowType.FLAME) {
            p.getInventory().setItemInMainHand(null);
            if(!canShoot.get(inHand)) {
                event.setCancelled(true);
                return;
            }
        }
        if (curArrow != ArrowType.FLAME) {
            cooldown(getArrowCooldown(curArrow), curArrow);
        }


    }

    public class cycloneTimer extends BukkitRunnable {

        private int seconds = 20*6;

        Entity a;

        public cycloneTimer(Entity e) {
            this.a = e;
        }

        //north = -z, south = +z, east = +x, west = -x
        @Override
        public void run() {
            if(seconds == 0) {
                a.remove();
                this.cancel();
            }
            Random random = new Random();
            for(Entity e : a.getNearbyEntities(3,3,3)) {
                int number = random.nextInt(2) - 1;
                boolean skip = false;
                if (e instanceof Player pl) if (pl.getUniqueId() == p.getUniqueId()) skip = true;
                if(!skip)
                    e.setVelocity(new Vector(number*Math.random()*0.3, Math.abs(Math.random()+0.2)-0.2, number*Math.random()*0.3));

            }
            a.getWorld().spawnParticle(Particle.CLOUD, a.getLocation(), 5,0,0,0,0.3,null,true);
            a.getWorld().playSound(a.getLocation(), Sound.ENTITY_HORSE_BREATHE, 1, 1);
            seconds--;
        }
    }

    public void teleport(Location hitLoc,Entity hitEntity) {
        Particle.DustOptions d = new Particle.DustOptions(Color.fromRGB(136, 255, 136), 1.5F);
        if (hitEntity != null) {
//                    main.broadcast("teleport");
            if (hitEntity instanceof Player pe) { // hit a player
                Location targetLoc = hitEntity.getLocation();
                Location shooterLoc = p.getLocation();

                Collection<Entity> nearbyEntities = targetLoc.getWorld().getNearbyEntities(targetLoc, 3, 3, 3);

                targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                targetLoc.getWorld().playSound(shooterLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                Location pLoc = p.getLocation();
                for(Entity e : nearbyEntities) {
                    if(e.equals(p)) continue;
                    if(e instanceof Player)
                        ((Player) e).addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20*3, 1));
                    e.teleport(shooterLoc);
                    createParticleLine(p,pLoc.clone().add(0,1,0),hitLoc.clone().add(0,1,0),d,4.0,0.2);
                }

                p.teleport(targetLoc);
//                        main.broadcast("teleport - direct hit super swap!");

            } else { // did not hit a player (block, snowman, etc)
                Location pLoc = p.getLocation();
                hitEntity.teleport(pLoc);
                p.teleport(hitLoc);
                createParticleLine(p,pLoc.clone().add(0,1,0),hitLoc.clone().add(0,1,0),d,4.0,0.2);
                p.getWorld().playSound(hitLoc,Sound.ENTITY_PLAYER_TELEPORT,2.0f,0.9f);
                p.getWorld().playSound(pLoc,Sound.ENTITY_PLAYER_TELEPORT,2.0f,0.9f);
            }
        } else { //hit a block
            Entity nearestPlayer = null;
            double nearestDist = 0.85; // Max distance to check (shorter range)

            for(Entity e : hitLoc.getWorld().getNearbyEntities(hitLoc, 2, 2, 2)) {
                if(e instanceof Player && !e.equals(p)) {
                    double dist = e.getLocation().distance(hitLoc);
                    if(dist < nearestDist) {
                        nearestDist = dist;
                        nearestPlayer = e;
                    }
                }
            }

            if(nearestPlayer != null) {
                Location targetLoc = nearestPlayer.getLocation();
                Location shooterLoc = p.getLocation();
                targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                shooterLoc.getWorld().playSound(shooterLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                createParticleLine(p,shooterLoc.clone().add(0,1,0),hitLoc.clone().add(0,1,0),d,4.0,0.2);
                nearestPlayer.teleport(shooterLoc);
                p.teleport(targetLoc);
            }
        }
    }

    public void gravity(Location loc) {
        World world = loc.getWorld();

        // Apply effects to nearby players
        for(Entity e : world.getNearbyEntities(loc, 4, 4, 4)) {
            if(e instanceof LivingEntity f) {
                f.setVelocity(new Vector(0,0.25,0));
                f.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 100, 2));
                f.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 120, 0));
            }
        }

//        List<FallingBlock> fallingBlockList = new ArrayList<>(List.of());
        ArrayList<FallingBlock> fallingBlocks = new ArrayList<>();

        // Process blocks within a 4 block radius
        int radius = 3; // Radius for block picking
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = world.getBlockAt(loc.getBlockX() + x, loc.getBlockY() + y, loc.getBlockZ() + z);
                    Material blockType = block.getType();
                    if (!blockType.isAir() && blockType.isSolid() && !main.restricted.contains(block.getType())) {

                        Location blockLocation = block.getLocation().add(0.5,0.5,0.5);

                        // Convert the block to a falling block
//                        FallingBlock fallingBlock = world.spawnFallingBlock(blockLocation, block.getBlockData());
                        FallingBlock fallingBlock = world.spawn(blockLocation,FallingBlock.class);
                        fallingBlock.setBlockData(block.getBlockData());
                        fallingBlock.setDropItem(false); // Prevents the block from dropping as an item
                        fallingBlock.setHurtEntities(true); // Optional: Falling blocks will hurt entities they fall on
                        fallingBlock.setGravity(false);
                        // Simulate levitation effect on the block
                        fallingBlock.setVelocity(new Vector(0, 0.2, 0)); // Upward velocity to simulate levitation

                        if (currentIce != null) {
                            if (currentIce.sphere.contains(block)) {
                                currentIce.sphere.remove(block);
                                currentIce.fallingSphere.add(fallingBlock);
                            }
                        }


                        fallingBlocks.add(fallingBlock);

                        // Set the original block location to air
                        block.setType(Material.AIR);

                    }
                }
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (FallingBlock b : fallingBlocks) {
                    b.setGravity(true);
                    cancel();
                }
            }
        }.runTaskLater(main,100L);

        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(33,33,33),1.5F);

        double visualRad = radius + 0.5;

        loc = loc.getBlock().getLocation().add(0.5,0.5,0.5);

        p.getWorld().playSound(loc,Sound.BLOCK_ANVIL_LAND,0.3f,0.1f);
        p.getWorld().playSound(loc,Sound.BLOCK_SHULKER_BOX_OPEN,2.0f,0.5f);

        Location corner1 = loc.clone().add(visualRad,visualRad,visualRad);
        Location corner2 = loc.clone().add(-visualRad,visualRad,visualRad);
        Location corner3 = loc.clone().add(-visualRad,visualRad,-visualRad);
        Location corner4 = loc.clone().add(visualRad,visualRad,-visualRad);

        Location corner5 = loc.clone().add(visualRad,-visualRad,visualRad);
        Location corner6 = loc.clone().add(-visualRad,-visualRad,visualRad);
        Location corner7 = loc.clone().add(-visualRad,-visualRad,-visualRad);
        Location corner8 = loc.clone().add(visualRad,-visualRad,-visualRad);

        ArrayList<Location> edges1 = new ArrayList<>(List.of(corner1, corner2, corner3, corner4));
        ArrayList<Location> edges2 = new ArrayList<>(List.of(corner5, corner6, corner7, corner8));
        ArrayList<Location> edges3 = new ArrayList<>(List.of(corner1, corner5, corner2, corner6,corner3,corner7,corner4,corner8));
        for (int i = 0; i < 4; i++) {
            createParticleLine(p,edges1.get(i),edges1.get((i + 1) % 4),dust,6.0,0.05);
        }
        for (int i = 0; i < edges2.size(); i++) {
            createParticleLine(p,edges2.get(i),edges2.get((i + 1) % (4)),dust,6.0,0.05);
        }
        for (int i = 0; i < 8; i += 2) {
            createParticleLine(p,edges3.get(i),edges3.get(((i + 1))),dust,6.0,0.05);
        }

    }

    public iceTimer currentIce;

    public class iceTimer extends BukkitRunnable {

        int timer = 0;
        Location loc;
        public ArrayList<Block> sphere = new ArrayList<Block>();
        public ArrayList<FallingBlock> fallingSphere = new ArrayList<FallingBlock>();
        public iceTimer(Location loc) {
            this.loc = loc;
            this.loc.getWorld().playSound(loc,Sound.BLOCK_GLASS_BREAK,2.0f,1.5f);
            for(Location l : sphere(loc, 4, true)) {
                if (canPlaceBlock(l.getBlock())) {
                    sphere.add(l.getBlock());
                }
            }
        }

        @Override
        public void run() {
            if(timer < 5*20) {
                for (Block b : sphere) {
                    b.setType(Material.ICE);
                }
                for(Entity e : loc.getWorld().getNearbyEntities(loc,4,4,4)) {
                    if (e.getLocation().toVector().subtract(loc.toVector()).length() < 3.7) {
                        e.setFreezeTicks(150);
                        if (e instanceof Player p) {
                            p.playSound(loc,Sound.ENTITY_HORSE_BREATHE,1f,0.5f);
                        }
                    }
                }
                loc.getWorld().spawnParticle(Particle.SNOWFLAKE,loc,5,2,2,2,0.2);

            }  else {
//                for (int i = 0; i < sphere.size(); i++)
//                    if (canPlaceBlock(sphere(loc, 4, true).get(i).getBlock())) {
//                        sphere(loc, 4, true).get(i).getBlock().setType(sphere.get(i));
//                    }
                for (Block b : sphere) {
                    if (b.getType() == Material.ICE) b.setType(Material.AIR);
                }
                for (FallingBlock b : fallingSphere) {
                    b.remove();
                }
                loc.getWorld().playSound(loc,Sound.BLOCK_GLASS_BREAK,2.0f,0.5f);
                loc.getWorld().spawnParticle(Particle.SNOWFLAKE,loc,50,2,2,2,0.2);
                this.cancel();
            }
            timer++;
        }

        public boolean canPlaceBlock(Block b) {

            return (b.getType() == Material.AIR || !b.getType().isSolid()) && !main.restricted.contains(b.getType());
        }

    }

    public static <T> void createParticleLine(
            Player pl,
            Location loc1, Location loc2,
            Particle particle,
            @Nullable T data,
            double density, double spread) {
        Vector vecLine = (loc1.clone().toVector().subtract(loc2.clone().toVector()));
        int count;

        count = (int)(vecLine.length() * density);

        for (int i = 0; i < count;i++) {
            pl.getWorld().spawnParticle(
                    particle,
                    loc2.clone().add(vecLine.clone().multiply(Math.random())),
                    1,
                    spread, spread, spread,
                    0.02,
                    data,
                    true

            );
        }
    }

    public static void createParticleLine(
            Player pl,
            Location loc1, Location loc2,
            Particle.DustOptions dust,
            double density, double spread) {

        createParticleLine(pl,loc1,loc2,Particle.DUST,dust,density,spread);

    }


    static public ArrayList<Location> sphere(Location location, int radius, boolean hollow) {
        ArrayList<Location> blocks = new ArrayList<Location>();
        World world = location.getWorld();
        int X = location.getBlockX();
        int Y = location.getBlockY();
        int Z = location.getBlockZ();
        int radiusSquared = radius * radius + 3;

        if (hollow) {
            for (int x = X - radius; x <= X + radius; x++) {
                for (int y = Y - radius; y <= Y + radius; y++) {
                    for (int z = Z - radius; z <= Z + radius; z++) {
                        if ((X - x) * (X - x) + (Y - y) * (Y - y) + (Z - z) * (Z - z) < radiusSquared) {
                            Location block = new Location(world, x, y, z);
                            blocks.add(block);
                        }
                    }
                }
            }
            return makeHollow(blocks, true);
        } else {
            for (int x = X - radius; x <= X + radius; x++) {
                for (int y = Y - radius; y <= Y + radius; y++) {
                    for (int z = Z - radius; z <= Z + radius; z++) {
                        if ((X - x) * (X - x) + (Y - y) * (Y - y) + (Z - z) * (Z - z) < radiusSquared) {
                            Location block = new Location(world, x, y, z);
                            blocks.add(block);
                        }
                    }
                }
            }
            return blocks;
        }
    }

    static private ArrayList<Location> makeHollow(ArrayList<Location> blocks, boolean sphere){
        ArrayList<Location> edge = new ArrayList<Location>();
        if(!sphere){
            for(Location l : blocks){
                World w = l.getWorld();
                int X = l.getBlockX();
                int Y = l.getBlockY();
                int Z = l.getBlockZ();
                Location front = new Location(w, X + 1, Y, Z);
                Location back = new Location(w, X - 1, Y, Z);
                Location left = new Location(w, X, Y, Z + 1);
                Location right = new Location(w, X, Y, Z - 1);
                if(!(blocks.contains(front) && blocks.contains(back) && blocks.contains(left) && blocks.contains(right))){
                    edge.add(l);
                }
            }
            return edge;
        } else {
            for(Location l : blocks){
                World w = l.getWorld();
                int X = l.getBlockX();
                int Y = l.getBlockY();
                int Z = l.getBlockZ();
                Location front = new Location(w, X + 1, Y, Z);
                Location back = new Location(w, X - 1, Y, Z);
                Location left = new Location(w, X, Y, Z + 1);
                Location right = new Location(w, X, Y, Z - 1);
                Location top = new Location(w, X, Y + 1, Z);
                Location bottom = new Location(w, X, Y - 1, Z);
                if(!(blocks.contains(front) && blocks.contains(back) && blocks.contains(left) && blocks.contains(right) && blocks.contains(top) && blocks.contains(bottom))){
                    edge.add(l);
                }
            }
            return edge;
        }
    }

    private ArrayList<Material> restricted = main.restricted;

    public enum ArrowType {
        FLAME, LIGHTNING, TELEPORT, ICE, CYCLONE, GRAVITY
    }

    private ArrowType curArrow = ArrowType.FLAME;

    final String lightning = ChatColor.YELLOW + "Lightning";
    final String teleport = ChatColor.GREEN + "Teleport";
    final String ice = ChatColor.DARK_AQUA + "Ice";
    final String cyclone = ChatColor.WHITE + "Cyclone";
    final String gravity = ChatColor.GRAY + "Gravity";
    final String flame = ChatColor.RED + "Flame";

    HashMap<ArrowType, Boolean> canShoot = new HashMap<ArrowType, Boolean>();

    public Archer(Main main, Player p, KitType archer) {
        super(main, p, archer);
        p.getInventory().setHeldItemSlot(0);
        setupInitialInventory(p);
    }

    private void setupInitialInventory(Player p) {
        initializeCanShoot();
        p.getInventory().clear(); // Clear existing inventory items

        ItemStack bow = newItemEnchanted(Material.BOW, flame, Enchantment.INFINITY, 1);
        ItemMeta bowMeta = bow.getItemMeta();
        setArrows();
        bowMeta.addEnchant(Enchantment.FLAME, 1, false);
        bow.setItemMeta(bowMeta);
        p.getInventory().setItem(0, bow);
        p.getInventory().setChestplate(new ItemStack(Material.GOLDEN_CHESTPLATE));
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        LeatherArmorMeta meta = (LeatherArmorMeta) helmet.getItemMeta();

        if (meta != null) {
            // Set the color of the helmet to red
            if(this.wool == Material.RED_WOOL)
                meta.setColor(Color.RED);
            if(this.wool == Material.BLUE_WOOL)
                meta.setColor(Color.BLUE);
            helmet.setItemMeta(meta);
        }
        p.getInventory().setHelmet(helmet);
        giveWool();
        setHearts(16);
    }

    private void initializeCanShoot() {
        for (ArrowType arrowType : ArrowType.values()) {
            canShoot.put(arrowType, true);
        }
    }

    public void cooldown(int seconds, ArrowType arrowType) {
        canShoot.put(arrowType, false);
        ItemStack arrow = p.getInventory().getItem(arrowTypeToSlot(arrowType));

        setArrows();

        setCooldown(getArrowCooldownName(arrowType), seconds, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);

        BukkitTask cool = new BukkitRunnable() {
            @Override
            public void run() {
                canShoot.put(arrowType, true);
                int slot = arrowTypeToSlot(arrowType);
                ItemStack item = p.getInventory().getItem(slot);
                if (item != null) {
                    ItemMeta meta = item.getItemMeta();
                    meta.addEnchant(Enchantment.INFINITY, 1, true);  // true to allow unsafe enchantments
                    item.setItemMeta(meta);
                    p.getInventory().setItem(slot, item);
                    setArrows();
                    switchToBow(e.getHeldItemSlot());
                }
            }
        }.runTaskLater(main, seconds * 20);  // Convert seconds to game ticks
        registerTask(cool);
    }

    private int arrowTypeToSlot(ArrowType arrowType) {
        return switch (arrowType) {
            case LIGHTNING -> 1;
            case TELEPORT -> 2;
            case ICE -> 3;
            case CYCLONE -> 4;
            case GRAVITY -> 5;
            default -> 0;  // Default case for FLAME
        };
    }

    private String arrowTypeToName(ArrowType arrowType) {
        // Convert ArrowType to its display name
        return switch (arrowType) {
            case FLAME -> flame;
            case LIGHTNING -> lightning;
            case TELEPORT -> teleport;
            case ICE -> ice;
            case CYCLONE -> cyclone;
            case GRAVITY -> gravity;
        };
    }

    public void setArrows() {
        ItemStack item;
        ItemMeta meta;

        // Loop over all ArrowTypes and set the items in the inventory with or without enchantment based on canShoot
        for (ArrowType arrowType : ArrowType.values()) {
            int slot = arrowTypeToSlot(arrowType);
            String arrowName = arrowTypeToName(arrowType);
            item = newItem(Material.TIPPED_ARROW, arrowName);  // Assuming newItem creates a new ItemStack

            if (canShoot.get(arrowType)) {
                meta = item.getItemMeta();
                meta.addEnchant(Enchantment.INFINITY, 1, true);  // Unsafe enchantment is allowed
                ((PotionMeta) meta).setBasePotionType(getArrowPotionType(arrowType));
                item.setItemMeta(meta);
            } else item = newItem(Material.ARROW,ChatColor.DARK_GRAY + "Spent " + arrowName +" Arrow");

            p.getInventory().setItem(slot, item);

        }
    }
    public void setFlame() {
        if (canShoot.get(ArrowType.FLAME)) {
            ItemStack bow = newItemEnchanted(Material.BOW, ChatColor.RED + "Flame Bow", Enchantment.INFINITY, 1);
            ItemMeta bowMeta = bow.getItemMeta();
            bowMeta.addEnchant(Enchantment.FLAME, 1, false);
            bow.setItemMeta(bowMeta);
            p.getInventory().setItem(0, bow);
            curArrow = ArrowType.FLAME;
        }
    }

    public void setLightning() {
        if (canShoot.get(ArrowType.LIGHTNING)) {
            ItemStack bow = newItemEnchanted(Material.BOW, lightning, Enchantment.INFINITY, 1);
            p.getInventory().setItem(1, bow);
            curArrow = ArrowType.LIGHTNING;
        }
    }

    public void setTeleport() {
        if (canShoot.get(ArrowType.TELEPORT)) {
            ItemStack bow = newItemEnchanted(Material.BOW, teleport, Enchantment.INFINITY, 1);
            p.getInventory().setItem(2, bow);
            curArrow = ArrowType.TELEPORT;
        }
    }

    public void setIce() {
        if (canShoot.get(ArrowType.ICE)) {
            ItemStack bow = newItemEnchanted(Material.BOW, ice, Enchantment.INFINITY, 1);
            p.getInventory().setItem(3, bow);
            curArrow = ArrowType.ICE;
        }
    }

    public void setCyclone() {
        if (canShoot.get(ArrowType.CYCLONE)) {
            ItemStack bow = newItemEnchanted(Material.BOW, cyclone, Enchantment.INFINITY, 1);
            p.getInventory().setItem(4, bow);
            curArrow = ArrowType.CYCLONE;
        }
    }

    public void setGravity() {
        if (canShoot.get(ArrowType.GRAVITY)) {
            ItemStack bow = newItemEnchanted(Material.BOW, gravity, Enchantment.INFINITY, 1);
            p.getInventory().setItem(5, bow);
            curArrow = ArrowType.GRAVITY;
        }
    }

    public PotionType getArrowPotionType(ArrowType arrowType) {
        return switch (arrowType) {
            case FLAME -> PotionType.HEALING;
            case LIGHTNING -> PotionType.STRENGTH;
            case TELEPORT -> PotionType.NIGHT_VISION;
            case ICE -> PotionType.SWIFTNESS;
            case CYCLONE -> PotionType.INVISIBILITY;
            case GRAVITY -> PotionType.WEAKNESS;
        };

    }

    public int getArrowCooldown(ArrowType arrowType) {
        return switch (arrowType) {
            case FLAME -> 0;
            case LIGHTNING -> 6;
            case TELEPORT -> 9;
            case ICE -> 12;
            case CYCLONE -> 17;
            case GRAVITY -> 13;
        };
    }

    public String getArrowCooldownName(ArrowType arrowType) {
        return switch (arrowType) {
            case FLAME -> ChatColor.RED + "🔥";
            case LIGHTNING -> ChatColor.YELLOW + "⚡";
            case TELEPORT -> ChatColor.GREEN + "⇄";
            case ICE -> ChatColor.AQUA + "❄";
            case CYCLONE -> ChatColor.WHITE + "☁";
            case GRAVITY -> ChatColor.DARK_GRAY + "ʊ";
        };
    }


    public void bHit(ProjectileHitEvent event) {
        Location hitLocation = null;
        String arrowName;

        arrowName = event.getEntity().getName();
        arrowName = ChatColor.stripColor(arrowName);

        if(arrowName.toLowerCase().equals("flame"))
            return;

        if (event.getHitBlock() != null) hitLocation = event.getHitBlock().getLocation();
        if (event.getHitEntity() != null) hitLocation = event.getHitEntity().getLocation();

        if (hitLocation == null) return;

        switch(arrowName.toLowerCase()) {
            case "lightning" -> {
                hitLocation.getWorld().strikeLightning(hitLocation);
                hitLocation.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,hitLocation.add(0,1,0),60,0.5,1,0.5,0.3);
                hitLocation.getWorld().spawnParticle(Particle.FIREWORK,hitLocation.add(0,1,0),30,0.5,1,0.5,0.3);
                hitLocation.getWorld().spawnParticle(Particle.CLOUD,hitLocation.add(0,1,0),10,0.5,1,0.5,0);
                hitLocation.getWorld().spawnParticle(Particle.FLASH,hitLocation.add(0,1,0),1);
            }
            case "ice" -> {
                iceTimer i = new iceTimer(hitLocation);
                currentIce = i;
                BukkitTask t = i.runTaskTimer(main, 0L, 1L);
                registerTask(t);
            }
            case "gravity" -> {
                gravity(hitLocation);
            }
            case "teleport" -> {
                teleport(hitLocation,event.getHitEntity());
            }
        }
    }

    public void handleItemSwitch(PlayerItemHeldEvent event) {
        setArrows();
        switchToBow(event.getNewSlot());
    }
    public void switchToBow(int slot) {
        switch(slot) {
            case 0:
                setFlame();
                break;
            case 1:
                setLightning();
                break;
            case 2:
                setTeleport();
                break;
            case 3:
                setIce();
                break;
            case 4:
                setCyclone();
                break;
            case 5:
                setGravity();
                break;
        }
    }

}
