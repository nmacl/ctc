package org.macl.ctc.kits;

import net.minecraft.world.item.alchemy.Potion;
import org.bukkit.*;
import org.bukkit.block.Block;
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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.macl.ctc.Main;
import org.macl.ctc.kits.Kit;
import org.macl.ctc.kits.KitType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class Archer extends Kit {
    // PDC key for tagging arrows with their type
    private final NamespacedKey ARROW_TYPE_KEY;
    public void shoot(ProjectileLaunchEvent event) {
        // Determine arrow type from current hotbar slot
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

        // TAG THE ARROW with its type using PDC
        if(event.getEntity() instanceof Arrow) {
            Arrow arrow = (Arrow) event.getEntity();
            arrow.getPersistentDataContainer().set(ARROW_TYPE_KEY, PersistentDataType.STRING, inHand.name());
        }

        // Update curArrow for UI purposes
        curArrow = inHand;

        // Special handling for cyclone
        if(inHand == ArrowType.CYCLONE) {
            BukkitTask cyc = new cycloneTimer(event.getEntity()).runTaskTimer(main, 0L, 1L);
            registerTask(cyc);
        }

        // Check cooldown and remove arrow from hand
        if(inHand != ArrowType.FLAME) {
            if(!canShoot.get(inHand)) {
                event.setCancelled(true);
                return;
            }

            // Start cooldown immediately on shoot
            cooldown(8, inHand);
            p.getInventory().setItemInMainHand(null);
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

            // Pull ALL entities toward cyclone (not just players)
            for(Entity e : a.getNearbyEntities(3,3,3)) {
                // Don't pull the shooter or the arrow itself
                if(e.equals(p) || e.equals(a)) continue;

                int number = random.nextInt(2) - 1;
                e.setVelocity(new Vector(number*Math.random()*0.3, Math.abs(Math.random()+0.2)-0.2, number*Math.random()*0.3));
            }

            a.getWorld().spawnParticle(Particle.CLOUD, a.getLocation(), 5);
            a.getWorld().playSound(a.getLocation(), Sound.ENTITY_HORSE_BREATHE, 1, 1);
            seconds--;
        }
    }

    public void gravity(Location loc) {
        World world = loc.getWorld();

        // Apply effects to nearby players
        for(Entity e : world.getNearbyEntities(loc, 4, 4, 4)) {
            if(e instanceof Player) {
                LivingEntity f = (LivingEntity) e;
                f.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 100, 2));
                f.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 120, 0));
            }
        }

        // Process blocks within a 4 block radius
        int radius = 3; // Radius for block picking
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = world.getBlockAt(loc.getBlockX() + x, loc.getBlockY() + y, loc.getBlockZ() + z);
                    Material blockType = block.getType();
                    if (!blockType.isAir() && blockType.isSolid() && !main.restricted.contains(block.getType())) {
                        Location blockLocation = block.getLocation();

                        // Convert the block to a falling block
                        FallingBlock fallingBlock = world.spawnFallingBlock(blockLocation, block.getBlockData());
                        fallingBlock.setDropItem(false); // Prevents the block from dropping as an item
                        fallingBlock.setHurtEntities(true); // Optional: Falling blocks will hurt entities they fall on

                        // Simulate levitation effect on the block
                        fallingBlock.setVelocity(new Vector(0, 1.5, 0)); // Upward velocity to simulate levitation

                        // Set the original block location to air
                        block.setType(Material.AIR);

                    }
                }
            }
        }
    }



    public class iceTimer extends BukkitRunnable {

        int timer = 0;
        Location loc;
        ArrayList<Material> sphere = new ArrayList<Material>();
        ArrayList<Location> sphereLocs = new ArrayList<Location>();

        public iceTimer(Location loc) {
            this.loc = loc;
            sphereLocs = sphere(loc, 4, true);
            // Store original block types
            for(Location l : sphereLocs) {
                sphere.add(l.getBlock().getType());
            }
        }

        @Override
        public void run() {
            // Validity check - ensure world is still loaded
            if(loc.getWorld() == null || !loc.isWorldLoaded()) {
                this.cancel();
                return;
            }

            if(timer < 5*20) {
                // Place ice blocks
                for(Location l : sphereLocs) {
                    if(!main.restricted.contains(l.getBlock().getType())) {
                        l.getBlock().setType(Material.ICE);
                    }
                }
            } else {
                // Restore original blocks
                for(int i = 0; i < sphere.size() && i < sphereLocs.size(); i++) {
                    if(!main.restricted.contains(sphereLocs.get(i).getBlock().getType())) {
                        sphereLocs.get(i).getBlock().setType(sphere.get(i));
                    }
                }
                this.cancel();
                return;
            }
            timer++;
        }

    }

    public ArrayList<Location> sphere(Location location, int radius, boolean hollow) {
        ArrayList<Location> blocks = new ArrayList<Location>();
        World world = location.getWorld();
        int X = location.getBlockX();
        int Y = location.getBlockY();
        int Z = location.getBlockZ();
        int radiusSquared = radius * radius;

        if (hollow) {
            for (int x = X - radius; x <= X + radius; x++) {
                for (int y = Y - radius; y <= Y + radius; y++) {
                    for (int z = Z - radius; z <= Z + radius; z++) {
                        if ((X - x) * (X - x) + (Y - y) * (Y - y) + (Z - z) * (Z - z) <= radiusSquared) {
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
                        if ((X - x) * (X - x) + (Y - y) * (Y - y) + (Z - z) * (Z - z) <= radiusSquared) {
                            Location block = new Location(world, x, y, z);
                            blocks.add(block);
                        }
                    }
                }
            }
            return blocks;
        }
    }

    private ArrayList<Location> makeHollow(ArrayList<Location> blocks, boolean sphere){
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

    String lightning = ChatColor.YELLOW + "Lightning";
    String teleport = ChatColor.GREEN + "Teleport";
    String ice = ChatColor.DARK_AQUA + "Ice";
    String cyclone = ChatColor.WHITE + "Cyclone";
    String gravity = ChatColor.GRAY + "Gravity";
    String flame = ChatColor.RED + "Flame";

    HashMap<ArrowType, Boolean> canShoot = new HashMap<ArrowType, Boolean>();

    public Archer(Main main, Player p, KitType archer) {
        super(main, p, archer);
        ARROW_TYPE_KEY = new NamespacedKey(main, "arrow_type");
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
    }

    private void initializeCanShoot() {
        for (ArrowType arrowType : ArrowType.values()) {
            canShoot.put(arrowType, true);
        }
    }

    public void cooldown(int seconds, ArrowType arrowType) {
        canShoot.put(arrowType, false);
        ItemStack arrow = p.getInventory().getItem(arrowTypeToSlot(arrowType));

        BukkitTask cool = new BukkitRunnable() {
            @Override
            public void run() {
                canShoot.put(arrowType, true);
                int slot = arrowTypeToSlot(arrowType);
                ItemStack item = p.getInventory().getItem(slot);
                if (item != null) {
                    ItemMeta meta = item.getItemMeta();
                    meta.addEnchant(Enchantment.INFINITY, 1, true);
                    item.setItemMeta(meta);
                    p.getInventory().setItem(slot, item);
                    playExp(1);
                }
            }
        }.runTaskLater(main, seconds * 20);
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
            item = newItem(Material.ARROW, arrowName);  // Assuming newItem creates a new ItemStack

            if (canShoot.get(arrowType)) {
                meta = item.getItemMeta();
                meta.addEnchant(Enchantment.INFINITY, 1, true);  // Unsafe enchantment is allowed
                item.setItemMeta(meta);
            }

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


    public void bHit(ProjectileHitEvent event) {
        // GET ARROW TYPE FROM PDC - not from curArrow!
        if(!(event.getEntity() instanceof Arrow)) return;

        Arrow arrow = (Arrow) event.getEntity();
        String typeStr = arrow.getPersistentDataContainer().get(ARROW_TYPE_KEY, PersistentDataType.STRING);

        // If no tag, it's not our arrow
        if(typeStr == null) return;

        ArrowType hitArrowType;
        try {
            hitArrowType = ArrowType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return; // Invalid arrow type
        }

        // Flame arrows don't have special hit effects (just normal fire)
        if(hitArrowType == ArrowType.FLAME)
            return;

        // Cooldown already started on shoot, just trigger effects on hit
        if(event.getHitBlock() == null) {
            // Hit entity
            switch(hitArrowType) {
                case LIGHTNING -> {
                    event.getHitEntity().getLocation().getWorld().strikeLightning(event.getHitEntity().getLocation());
                    break;
                }
                case ICE -> {
                    BukkitTask t = new iceTimer(event.getHitEntity().getLocation()).runTaskTimer(main, 0L, 1L);
                    registerTask(t);
                    break;
                }
                case GRAVITY -> {
                    gravity(event.getHitEntity().getLocation());
                    break;
                }
                case CYCLONE -> {
                    main.broadcast("cyclone");
                    break;
                }
                case TELEPORT -> {
                    // DIRECT HIT - Super swap: target + all nearby entities swap with shooter
                    Entity target = event.getHitEntity();
                    Location targetLoc = target.getLocation();
                    Location shooterLoc = p.getLocation();

                    // Get all entities within 3 blocks of the target
                    Collection<Entity> nearbyEntities = targetLoc.getWorld().getNearbyEntities(targetLoc, 5, 5, 5);

                    // Play enderpearl sound at both locations
                    targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    shooterLoc.getWorld().playSound(shooterLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

                    // Teleport all nearby entities to shooter's location
                    for(Entity e : nearbyEntities) {
                        if(e.equals(p)) continue; // Don't teleport shooter yet
                        if(e instanceof Player)
                            ((Player) e).addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20*3, 1));
                        e.teleport(shooterLoc);
                    }

                    // Teleport shooter to target location
                    p.teleport(targetLoc);
                    main.broadcast("teleport - direct hit super swap!");
                    break;
                }
            }
        } else {
            // Hit block
            switch(hitArrowType) {
                case FLAME -> {
                    event.getHitBlock().setType(Material.FIRE);
                    break;
                }
                case LIGHTNING -> {
                    event.getHitBlock().getLocation().getWorld().strikeLightning(event.getHitBlock().getLocation());
                    break;
                }
                case ICE -> {
                    BukkitTask t = new iceTimer(event.getHitBlock().getLocation()).runTaskTimer(main, 0L, 1L);
                    registerTask(t);
                    break;
                }
                case GRAVITY -> {
                    gravity(event.getHitBlock().getLocation());
                    break;
                }
                case CYCLONE -> {
                    main.broadcast("cyclone");
                    break;
                }
                case TELEPORT -> {
                    // Arrow landed near (not direct hit) - Check for nearby players
                    Location arrowLoc = event.getHitBlock().getLocation();
                    Entity nearestPlayer = null;
                    double nearestDist = 0.85; // Max distance to check (shorter range)

                    // Find closest player within 2 blocks
                    for(Entity e : arrowLoc.getWorld().getNearbyEntities(arrowLoc, 2, 2, 2)) {
                        if(e instanceof Player && !e.equals(p)) {
                            double dist = e.getLocation().distance(arrowLoc);
                            if(dist < nearestDist) {
                                nearestDist = dist;
                                nearestPlayer = e;
                            }
                        }
                    }

                    // If found a nearby player, do simple swap
                    if(nearestPlayer != null) {
                        Location targetLoc = nearestPlayer.getLocation();
                        Location shooterLoc = p.getLocation();

                        // Play enderpearl sound at both locations
                        targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                        shooterLoc.getWorld().playSound(shooterLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

                        nearestPlayer.teleport(shooterLoc);
                        p.teleport(targetLoc);
                        main.broadcast("teleport - proximity swap!");
                    }
                    break;
                }
            }
        }
    }

    public void handleItemSwitch(PlayerItemHeldEvent event) {
        setArrows();
        switch(event.getNewSlot()) {
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
