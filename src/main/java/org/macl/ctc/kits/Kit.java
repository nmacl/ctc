package org.macl.ctc.kits;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.macl.ctc.Main;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class Kit implements Listener {
    public Main main;
    public int cooldownDisplayType;
    public boolean displayCooldowns = true;
    public Vector realVelocity;
    KitType type;
    Player p;
    PlayerInventory e;

    public Material wool = Material.WHITE_WOOL;
    private HashMap<String, Cooldown> cooldowns = new HashMap<>();
    private HashMap<String, RegenItem> regenItems = new HashMap<>();

    private final List<BukkitTask> activeTasks = new ArrayList<>();

    public RegenItem regenItem(String name, ItemStack item, int seconds, int maxAmt, int slot) {
        RegenItem regen = new RegenItem(this, p, name, item, seconds, maxAmt, slot);
        regenItems.put(name, regen);
        return regen;
    }

    /** Register a BukkitTask so we can cancel it later. */
    protected void registerTask(BukkitTask task) {
        activeTasks.add(task);
    }

    /** Cancel every task this Kit has ever scheduled. */
    public void cancelAllTasks() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }

    /** Schedule and auto‐register a repeating runnable. */
    protected BukkitTask runTaskTimer(BukkitRunnable r, long delay, long period) {
        BukkitTask t = r.runTaskTimer(main, delay, period);
        registerTask(t);
        return t;
    }




    public void cancelAllCooldowns() {
        for (Cooldown cooldown : cooldowns.values()) {
            cooldown.cancel();
        }
        cooldowns.clear(); // Optionally clear all entries if they are no longer needed
    }

    public void cancelAllRegen() {
        for (RegenItem regens : regenItems.values()) {
            regens.cancel();
        }
        regenItems.clear();
    }

    public void setHearts(double d) {
        AttributeInstance playerAttribute = p.getAttribute(Attribute.MAX_HEALTH);
        playerAttribute.setBaseValue(d);
        p.setHealth(d);
    }

    public Kit(Main main, Player p, KitType type) {
        this.main = main;
        this.type = type;
        this.p = p;
        this.e = p.getInventory();
        main.getServer().getPluginManager().registerEvents(this, main);
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        for(PotionEffect e : p.getActivePotionEffects())
            p.removePotionEffect(e.getType());
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 99999999, 0));
        if(main.game.redHas(p) && main.game.center == 1)
            p.getInventory().setItem(8, main.coreCrush());
        if(main.game.blueHas(p) && main.game.center == 2)
            p.getInventory().setItem(8, main.coreCrush());
        if(main.game.redHas(p)) {
            wool = Material.RED_WOOL;
        }
        if(main.game.blueHas(p)) {
            wool = Material.BLUE_WOOL;
        }
        AttributeInstance playerAttribute = p.getAttribute(Attribute.MAX_HEALTH);
        playerAttribute.setBaseValue(20);
        AttributeInstance armorAttribute = p.getAttribute(Attribute.ARMOR);
        armorAttribute.setBaseValue(-30);

        p.setUnsaturatedRegenRate(99999999);
        p.setSaturatedRegenRate(15); // out of combat healing
        hungerProcess = new HungerRegenProcess();
        runTaskTimer(hungerProcess,0,1L);
    }

    public final HungerRegenProcess hungerProcess;

    public class HungerRegenProcess extends BukkitRunnable {

        boolean hidden = true; //Displays the Combat Timer as hunger if false. Could probably become a setting in the far far future
        int hiddenHunger = 20;

        int hungerTarget = 20;
        int onDamageTarget = 7;
        int hungerRegainBuffer = 0; //After taking damage, the buffer will count down before being able to tick the combat interval.
        int hungerRegainInterval = 20; //Combat period, it takes (Interval*(20 - onDamageTarget) in ticks) to leave combat after taking damage
        int currentRegainInterval = 20; // Additionally, every interval regains 0.5 HP to the player


        public void run() {
            if (getHunger() != hungerTarget) {
                if (getHunger() < hungerTarget) {
                    setHunger(getHunger() + 1);

                } else if (getHunger() > hungerTarget) {
                    setHunger(getHunger() - 1);
                    currentRegainInterval++;
                }
            }

            if (hungerTarget < 20) {

                if (hungerRegainBuffer > 0) {
                    hungerRegainBuffer--;
                } else if (currentRegainInterval > 0) {
                    currentRegainInterval--;
                } else {
                    currentRegainInterval = hungerRegainInterval;
                    hungerTarget++;
                    if (p.getHealth() != p.getAttribute(Attribute.MAX_HEALTH).getValue()) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 123));
                    }
                }

            } else {
                p.setSaturation(99999f);
            }


        }

        public void setHunger(int hungy) {
            if (!hidden) {
                p.setFoodLevel(hungy);
            } else {
                hiddenHunger = hungy;
            }
        }

        public int getHunger() {
            if (!hidden) {
                return p.getFoodLevel();
            } else {
                return hiddenHunger;
            }
        }

        public void onDamage() {
            hungerTarget = onDamageTarget;
            hungerRegainBuffer = 30;
            if (p.getSaturation() > 20) {
                p.setSaturation(0);
            }
        }

    }

    public void procHungerDamage() {
        if (hungerProcess != null) {
            hungerProcess.onDamage();
        }
    }

    public void setHungerOnHit(int hungy) {
        if (hungerProcess != null) {
            hungerProcess.onDamageTarget = hungy;
        }
    }

    public Vector getRealVelocity() {
        Vector c = realVelocity.clone();
        return c;
    }

    public boolean isOnGround() {
        boolean onGround = false;

        ArrayList<Boolean> locBelowsSolid = new ArrayList<>();

        locBelowsSolid.add(p.getLocation().add(0,-0.2,0).getBlock().isSolid());
        for (int x = -1; x <= 1; x += 2) {
            for (int z = -1; z <= 1; z += 2) {
                Block block = p.getLocation().add(0.3 * x,-0.2,0.3 * z).getBlock();
                locBelowsSolid.add(block.isCollidable());
            }
        }

        boolean yLevel = Math.abs((Math.floor(getRealVelocity().getY() * 100) / 100)) <= 0.1;
//        boolean fallDistZero = Math.abs((Math.floor(p.getFallDistance() * 100) / 100)) <= 0.1;

        onGround = yLevel && locBelowsSolid.contains(true);

        return onGround;
    }

    public static String getProgressIndicator(double progress,int stages,ChatColor full,ChatColor empty) {
        StringBuilder sb = new StringBuilder();
        int currentStage = (int) Math.ceil(progress * stages);

        for (int i = 0; i < stages; i++) {
            if (i < currentStage) {
                sb.append(full).append("|");
            } else {
                sb.append(empty).append("|");
            }
        }
        return sb.toString();
    }

    public static String createProgressTime(double time,double originalTime,boolean precise,boolean bold) {
        String s = "";
        if (!precise) {
            time = Math.ceil(time);
            originalTime = Math.ceil(originalTime);
        }
        double progress = (time / originalTime);
        ChatColor color;

        if (progress > 0.66) {
            color = ChatColor.RED;
        } else if (progress > 0.33) {
            color = ChatColor.YELLOW;
        } else {
            color = ChatColor.GREEN;
        }

        if (precise) {
            s = String.format("%.1f", time);
        } else {
            s = String.format("%.0f", time);
        }

        StringBuilder sb = new StringBuilder();

        if (bold) {
            s = ("" + color +""+ ChatColor.BOLD +""+ ChatColor.ITALIC + s);
        } else {
            s = ("" + color +""+ ChatColor.ITALIC + s);
        }

        return s;
    }

    /**
     * Sets a cooldown for a specific ability.
     * @param ability The name of the ability.
     * @param seconds The duration of the cooldown in seconds.
     */
// in Kit.java, *below* your existing setCooldown(...)
    public void setCooldown(String ability, int seconds, Sound endSound, Runnable onComplete) {
        // cancel any existing
        if (cooldowns.containsKey(ability)) {
            cooldowns.get(ability).cancel();
        }
        // wrap their onComplete to also remove it from the map
        Cooldown cd = new Cooldown(this, p, ability, seconds, endSound, () -> {
            onComplete.run();
            cooldowns.remove(ability);
        });
        cooldowns.put(ability, cd);
    }

    // now tweak the old setCooldown to delegate:
    public void setCooldown(String ability, int seconds, Sound endSound) {
        setCooldown(ability, seconds, endSound, () -> {
            //p.sendMessage(ChatColor.GREEN + ability + " is ready!");
        });
    }

    public void cancelCooldown(String ability) {
        Cooldown cd = cooldowns.remove(ability);
        if (cd != null) {
            cd.cancel();
        }
    }

    /**
     * Checks if a specific ability is on cooldown.
     * @param ability The name of the ability to check.
     * @return true if the ability is currently on cooldown, false otherwise.
     */
    public boolean isOnCooldown(String ability) {
        Cooldown cooldown = cooldowns.get(ability);
        return cooldown != null && cooldown.isActive();
    }
    class Cooldown {
        private Player player;
        private BukkitTask task;
        private boolean active;
        private int originalTime;
        private String abilityName;
        private int timeLeft;  // Declare timeLeft as an instance variable
        private int bufferOut = 80;

        public Cooldown(Kit kit, Player player, String abilityName, int seconds, Sound endSound, Runnable onComplete) {
            this.player = player;
            this.abilityName = abilityName;
            this.originalTime = seconds * 20;
            this.active = true;
            this.timeLeft = seconds * 20;

            task = new BukkitRunnable() {
                public void run() {
                    if (!active) {
                        this.cancel();
                        return;
                    }
                    if (timeLeft <= 0) {
                        if (active) {
                            onComplete.run();
                            player.playSound(player.getLocation(), endSound, 1, 1);
                            active = false;
                        } else if (bufferOut > 0) {
                            bufferOut--;
                        } else {
                            this.cancel();
                        }
                    } else {
                        timeLeft--;  // Update timeLeft here
                    }
                    kit.updateCooldowns();  // Notify the Kit to update the action bar
                }
            }.runTaskTimer(main, 0L, 1L);  // Make sure kit can provide access to main
            registerTask(task);
        }

        public String getProgressIndicator() {
            double progress = (double) (originalTime - timeLeft) / originalTime;
            switch (cooldownDisplayType) {
                case 0 -> {
                    return createProgressDots(progress);
                }
                case 1 -> {
                    return createProgressTime(((double) timeLeft / 20),((double) originalTime / 20),false,false);
                }
                case 2 -> {
                    return createProgressTime(((double) timeLeft / 20),((double) originalTime / 20),true,false);
                }
            }
            return createProgressDots(progress);
        }

        private String createProgressDots(double progress) {
            int stages = 9; // Total number of stages for detailed progression
            int currentStage = (int) (progress * stages);
            int[] thresholds = {1, 3, 5, 6, 7, 9}; // Thresholds for changing dot colors
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                if (currentStage >= thresholds[i + 3]) {
                    sb.append(ChatColor.GREEN).append("● ");
                } else if (currentStage >= thresholds[i]) {
                    sb.append(ChatColor.YELLOW).append("● ");
                } else {
                    sb.append(ChatColor.RED).append("● ");
                }
            }
            return sb.toString().trim();
        }

        public boolean isActive() {
            return active;
        }

        public void cancel() {
            if (task != null) {
                task.cancel();
                active = false;
            }
        }
    }

    public void updateCooldowns() {
        if (!displayCooldowns) return;

        StringBuilder message = new StringBuilder();
        cooldowns.forEach((ability, cooldown) -> {
            if (cooldown.isActive()) {
                message.append(ChatColor.WHITE).append(ChatColor.BOLD).append(ability.toUpperCase()).append(" ");
                message.append(cooldown.getProgressIndicator()).append(" ");
            }
        });
        regenItems.forEach((name, regen) -> {
            if (regen.isActive()) {
                message.append(ChatColor.WHITE).append(ChatColor.BOLD).append(name.toUpperCase()).append(" ");
                message.append(regen.getProgressIndicator()).append(" ");
            }
        });

        if (!message.toString().isEmpty()) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message.toString()));
        }
    }

    public void onKill() {

    }

    public class RegenItem {
        private Player player;
        private BukkitTask task;
        private ItemStack item;
        private int maxAmt;
        private int slot;
        private String itemName;
        public int originalTime;
        public int timeLeft;
        public boolean active;
        public boolean paused;

        public RegenItem(Kit kit, Player player, String itemName, ItemStack item, int seconds, int maxAmt, int slot) {
            this.player = player;
            this.itemName = itemName;
            this.item = item;
            this.maxAmt = maxAmt;
            this.slot = slot;
            this.originalTime = seconds * 20;
            this.timeLeft = seconds * 20;
            this.active = true;
            this.paused = false;

            task = new BukkitRunnable() {
                public void run() {
                    if (!active || !player.isOnline() || player.isDead()) {
                        this.cancel();
                        return;
                    }

                    if (paused) return;

                    int itemCount = getCurrentItemCount();

                    // Check if the item count is below maximum allowed
                    if (itemCount < maxAmt) {
                        if (timeLeft <= 0) {
                            item.setAmount(itemCount + 1);
                            player.getInventory().setItem(slot, item);
                            timeLeft += originalTime;
                        } else {
                            timeLeft--;
                        }
                        kit.updateCooldowns(); // Update all cooldown displays to show progress
                    } else {
                        // If max amount reached, do not reset the timer but stop decrementing
                        timeLeft = originalTime;
                    }
                }
            }.runTaskTimer(kit.main, 0L, 1L);
            registerTask(task);
        }

        public int getCurrentItemCount() {
            int itemCount = 0;
            for (ItemStack stack : player.getInventory().getContents()) {
                if (stack != null && stack.isSimilar(item)) {
                    itemCount += stack.getAmount();
                }
            }
            return itemCount;
        }

        public String getProgressIndicator() {

            switch (cooldownDisplayType) {
                case 0 -> {
                    return getProgressIndicator(ChatColor.GREEN,ChatColor.RED);
                }
                case 1 -> {
                    return createProgressTime(((double) timeLeft / 20),((double) originalTime / 20),false,true);
                }
                case 2 -> {
                    return createProgressTime(((double) timeLeft / 20),((double) originalTime / 20),true,true);
                }
            }

            return getProgressIndicator(ChatColor.GREEN,ChatColor.RED);
        }

        public String getProgressIndicator(ChatColor full, ChatColor empty) {
            if (getCurrentItemCount() >= maxAmt) {
                return ""; // No indicator if at max amount
            }
            double progress = (double) (originalTime - timeLeft) / originalTime;
            StringBuilder sb = new StringBuilder();
            int stages = 10;
            int currentStage = (int) (progress * stages);
            for (int i = 0; i < stages; i++) {
                if (i < currentStage) {
                    sb.append(ChatColor.GREEN).append("|");
                } else {
                    sb.append(ChatColor.RED).append("|");
                }
            }
            return sb.toString();
        }

        public boolean isActive() {
            return active && getCurrentItemCount() < maxAmt;
        }

        public void cancel() {
            if (task != null) {
                task.cancel();
                active = false;
            }
        }
    }

    //Setup with parameters instead of arguments String[] params

    public ItemStack newItem(Material m, String name) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack newItem(Material m, String name, int stack) {
        ItemStack item = new ItemStack(m,stack);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack newItemEnchanted(Material m, String name, Enchantment enchant, int level) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        item.addUnsafeEnchantment(enchant, level);
        return item;
    }

    public ItemStack newItemEnchants(Material m, String name, ArrayList<Enchantment> enchants, int level) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        for(Enchantment enchant : enchants)
            item.addUnsafeEnchantment(enchant, level);
        return item;
    }

    public void onWoolOut() {
        if (isOnCooldown("wool")) return;
        setCooldown("wool",5,Sound.BLOCK_WOOL_PLACE, () ->{
            giveWool(8);
        });
    }

    public void giveWool(int amount) {
        Material wool = Material.WHITE_WOOL;
        if(main.game.redHas(p))
            wool = Material.RED_WOOL;
        if(main.game.blueHas(p))
            wool = Material.BLUE_WOOL;
        p.getInventory().addItem(new ItemStack(wool, amount));
    }

    public void giveWool() {
        giveWool(64);
    }

    public void playExp(float level) {
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, level);
    }

    public boolean has(Player p1) {
        if(p1.getUniqueId() == p.getUniqueId())
            return true;
        return false;
    }

    public Player getPlayer() {
        return p;
    }
}
