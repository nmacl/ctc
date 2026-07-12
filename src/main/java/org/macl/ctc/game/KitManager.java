package org.macl.ctc.game;

import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.macl.ctc.Main;
import org.macl.ctc.events.DefaultListener;
import org.macl.ctc.kits.*;

import java.util.*;

public class KitManager implements Listener {
    Main main;
    public HashMap<UUID, Kit> kits = new HashMap<UUID, Kit>();

    public KitManager(Main main) {
        this.main = main;
        main.listens.add(this);
    }

    /*
        Kit Manager
     */

    int slot = 0;

    public int getSlot() {
        int currentSlot = slot;
        slot++;
        return currentSlot;
    }

    public KitMenu getMenu() {
        KitMenu menu = new KitMenu(main.prefix + "Kit Menu", 18+9+9);

        for (int i = 0; i < 9; i++) {
            menu.setItem(getSlot(),ChatColor.GRAY + "",Material.GRAY_STAINED_GLASS_PANE);
        }

        List<String> gLore = createLore(ChatColor.DARK_PURPLE + "Cane: Knockback 10",
                ChatColor.LIGHT_PURPLE + "Cookies: Right click player to heal",
                ChatColor.RED + "No speed");

        menu.setItem(getSlot(), ChatColor.LIGHT_PURPLE + "GRANDMA", Enchantment.KNOCKBACK, gLore, Material.STICK);

        List<String> snowLore = createLore(ChatColor.BLUE + "Rocket Jump: Right click to fly",
                ChatColor.DARK_BLUE + "Snowball Launcher: Right click for snowballs",
                ChatColor.WHITE + "Speed: II");

        menu.setItem(getSlot(), ChatColor.AQUA + "SNOWBALLER", Enchantment.FEATHER_FALLING, snowLore, Material.SNOWBALL);


        List<String> demoLore = createLore(ChatColor.RED + "Egg Grenade",
                ChatColor.GRAY + "Mine",
                ChatColor.BOLD +"" + ChatColor.DARK_RED + "Sheep Bomb: Heat seeking sheep");

        menu.setItem(getSlot(), ChatColor.DARK_RED + "DEMOLITIONIST", Enchantment.BLAST_PROTECTION, demoLore, Material.TNT);

        List<String> buildLore = createLore(ChatColor.DARK_GREEN + "Hammer: Right click - Build Menu",
                ChatColor.DARK_GRAY + "Shears: Right click - Wool Shear",
                ChatColor.RED + "Medfire: deployable health pack",
                ChatColor.GREEN + "Extra Wool!");

        menu.setItem(getSlot(), ChatColor.DARK_AQUA + "BUILDER", Enchantment.EFFICIENCY, buildLore, Material.GRASS_BLOCK);

        List<String> spyLore = createLore(ChatColor.GREEN + "Poison Dagger",
                ChatColor.WHITE + "Invisibility",
                ChatColor.RED + "Can't mine core");

        menu.setItem(getSlot(), ChatColor.WHITE + "SPY", Enchantment.INFINITY, spyLore, Material.IRON_HOE);

        List<String> runnerLore = createLore(ChatColor.BLUE + "Runner",
                ChatColor.YELLOW + "Block Run",
                ChatColor.WHITE + "Polar Deflection Field",
                ChatColor.GOLD + "Dash");

        menu.setItem(getSlot(), ChatColor.BLUE + "RUNNER", Enchantment.FROST_WALKER, runnerLore, Material.LEATHER_BOOTS);

        List<String> tankLore = createLore(ChatColor.DARK_GREEN + "Gatling Gun",
                ChatColor.RED + "Hellfire Missile",
                ChatColor.DARK_AQUA + "Shield, blocks enemies",
                ChatColor.DARK_RED +  "Reduced Wool!");

        menu.setItem(getSlot(), ChatColor.GRAY + "TANK", Enchantment.PROTECTION, tankLore, Material.IRON_BLOCK);

        List<String> fishLore = createLore(ChatColor.GOLD + "Fishing rod",
                ChatColor.YELLOW + "Pufferfish Bomb",
                ChatColor.LIGHT_PURPLE + "Cod Sniper");

        menu.setItem(getSlot(), ChatColor.DARK_AQUA + "FISHERMAN", Enchantment.LUCK_OF_THE_SEA, fishLore, Material.FISHING_ROD);

        List<String> engineerLore = Arrays.asList(
                ChatColor.YELLOW + "Firework Blaster",
                ChatColor.WHITE + "Turret",
                ChatColor.RED + "Enemy Detector",
                ChatColor.BLUE + "Teleporters"
        );

        menu.setItem(getSlot(), ChatColor.DARK_GRAY + "ENGINEER", Enchantment.EFFICIENCY, engineerLore, Material.DISPENSER);

        List<String> grandpaLore = Arrays.asList(
                ChatColor.GOLD + "Booze",
                ChatColor.GRAY + "Slugged Shotgun",
                ChatColor.YELLOW + "Peppergun",
                ChatColor.DARK_GREEN + "Veteran of many battles"
        );

        menu.setItem(getSlot(), ChatColor.LIGHT_PURPLE + "GRANDPA", Enchantment.SHARPNESS, grandpaLore, Material.HONEY_BOTTLE);

        List<String> archerLore = createLore(
                ChatColor.RED       + "Flame Arrow: Unlimited fire arrows",
                ChatColor.YELLOW    + "Lightning Arrow: Strike lightning on hit",
                ChatColor.GREEN     + "Teleport Arrow: Swap positions with target",
                ChatColor.DARK_AQUA + "Ice Arrow: Cover an area in an ice dome",
                ChatColor.WHITE     + "Cyclone Arrow: Whirls players around",
                ChatColor.GRAY      + "Gravity Arrow: Lift blocks & players"
        );

        menu.setItem(getSlot(), ChatColor.GOLD + "ARCHER", Enchantment.POWER, archerLore, Material.BOW);

        List<String> artificerLore = createLore(
                ChatColor.RED      + "Light enemies ablaze with the Flamethrower",
                ChatColor.BLUE     + "Frost Dagger, freezes foes in place.",
                ChatColor.AQUA     + "Launch yourself skyward with Updraft",
                ChatColor.DARK_GRAY+ "Gather enough Void Fragments to form the Void Bomb"
        );
        menu.setItem(getSlot(), ChatColor.GREEN + "ARTIFICER", Enchantment.MENDING, artificerLore, Material.BLAZE_POWDER);

        List<String> lumberjackLore = createLore(
                ChatColor.YELLOW  + "Destroy all with the Chain Axe",
                ChatColor.GOLD     + "Chuck your allies and Sap with Log Chuck",
                ChatColor.GREEN     + "Mystic Sap rejuvenates allies and debuffs enemies",
                ChatColor.DARK_PURPLE + "Have allies Right-Click you to be ridden!"
        );
        menu.setItem(getSlot(), ChatColor.GOLD + "LUMBERJACK", Enchantment.EFFICIENCY, lumberjackLore, Material.GOLDEN_AXE);

        List<String> operatorLore = Arrays.asList(
                ChatColor.RED + "L0-27 Bovine Strike",
                ChatColor.DARK_AQUA + "Spreaders",
                ChatColor.WHITE + "Puller",
                ChatColor.AQUA + "Dislocator"
        );

        menu.setItem(getSlot(), ChatColor.BLUE + "OPERATOR", Enchantment.KNOCKBACK, operatorLore, Material.ECHO_SHARD);

        for (int i = 18+9; i < 26+9; i++) {
            menu.setItem(i,ChatColor.GRAY + "",Material.GRAY_STAINED_GLASS_PANE);
        }

        menu.setItem(26+9,ChatColor.AQUA + "Info Menu",Material.BOOK);


        slot = 0;

        return menu;
    }


    public void openMenu(Player p) {
        p.openInventory(getMenu().getKitMenu());
    }

    public static List<String> createLore(String... lines) {
        return Arrays.asList(lines);
    }



    @EventHandler
    public void close(InventoryCloseEvent event) {
        Player p = (Player) event.getPlayer();
        if(kits.get(p.getUniqueId()) == null && main.game.started && (main.game.redHas(p) || main.game.blueHas(p)) && event.getView().getTitle().equalsIgnoreCase(main.prefix + "Kit Menu"))
            kits.put(p.getUniqueId(), new Snowballer(main, p, KitType.SNOWBALLER));
    }

    @EventHandler
    public void open(InventoryOpenEvent event) {
        if(event.getView().getTitle().equalsIgnoreCase("Chest")) {
            event.setCancelled(true);
            openMenu((Player) event.getPlayer());
        }
    }

    @EventHandler
    public void dropItem(PlayerDropItemEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        Player p = (Player) event.getWhoClicked();
        InventoryView view = event.getView();
        ItemStack click = event.getCurrentItem();
        //if(main.game.started != true)
        //  return;

        if(main.game.started)
            event.setCancelled(true);
        if(view.getTitle().equals(main.prefix + "Kit Menu")) {
            if(click == null)
                return;
            if(kits.get(p.getUniqueId()) != null) {
                kits.get(p.getUniqueId()).cancelAllCooldowns();
                kits.get(p.getUniqueId()).cancelAllRegen();
                kits.get(p.getUniqueId()).cancelAllTasks();
            }

            switch(click.getType()) {
                case SNOWBALL:
                    kits.put(p.getUniqueId(), new Snowballer(main, p, KitType.SNOWBALLER));
                    break;
                case STICK:
                    kits.put(p.getUniqueId(), new Grandma(main, p, KitType.GRANDMA));
                    break;
                case IRON_HOE:
                    kits.put(p.getUniqueId(), new Spy(main, p, KitType.SPY));
                    break;
                case TNT:
                    kits.put(p.getUniqueId(), new Demolitionist(main, p, KitType.DEMOLITIONIST));
                    break;
                case GRASS_BLOCK:
                    kits.put(p.getUniqueId(), new Builder(main, p, KitType.BUILDER));
                    break;
                case LEATHER_BOOTS:
                    kits.put(p.getUniqueId(), new Runner(main, p, KitType.RUNNER));
                    break;
                case IRON_BLOCK:
                    kits.put(p.getUniqueId(), new Tank(main, p, KitType.TANK));
                    break;
                case FISHING_ROD:
                    kits.put(p.getUniqueId(), new Fisherman(main, p, KitType.FISHERMAN));
                    break;
                case HONEY_BOTTLE:
                    kits.put(p.getUniqueId(), new Grandpa(main, p, KitType.GRANDPA));
                    break;
                case DISPENSER:
                    kits.put(p.getUniqueId(), new Engineer(main, p, KitType.ENGINEER));
                    break;
                case BOW:
                    kits.put(p.getUniqueId(), new Archer(main, p, KitType.ARCHER));
                    break;
                case BLAZE_POWDER:
                    kits.put(p.getUniqueId(), new Artificer(main, p, KitType.ARTIFICER));
                    break;
                case GOLDEN_AXE:
                    kits.put(p.getUniqueId(), new Lumberjack(main,p,KitType.LUMBERJACK));
                    break;
                case ECHO_SHARD:
                    kits.put(p.getUniqueId(),new Operator(main,p,KitType.OPERATOR));
                    break;
                default:
                    break;
            }
            event.setCancelled(true);
            p.closeInventory();
        }

        if(view.getTitle().equalsIgnoreCase(main.prefix + "BuildTools")) {
            if(main.getKits().get(p.getUniqueId()) != null && main.getKits().get(p.getUniqueId()) instanceof Builder) {
                Builder b = (Builder) main.getKits().get(p.getUniqueId());
                switch (click.getType()) {
                    case OAK_SLAB:
                        b.bridge();
                        break;
                    case OAK_STAIRS:
                        b.stairs(event.isRightClick());
                        break;
                    case LADDER:
                        b.tower();
                        break;
                    case SLIME_BLOCK:
                        b.platform();
                        break;
                    default:
                        event.setCancelled(true);
                        p.closeInventory();
                        break;
                }
                event.setCancelled(true);
                p.closeInventory();
            }
        }
    }

    public void remove(Player p) {
        if(kits.get(p.getUniqueId()) != null) {
            kits.remove(p.getUniqueId());
        }
    }

    class KitMenu {
        Inventory e;

        public KitMenu(String name, int size) {
            e = Bukkit.createInventory(null, size, name);
        }

        public void setItem(int slot, String name, Enchantment enchantment, List<String> lore, Material m) {
            ItemStack st = new ItemStack(m);
            ItemMeta stmeta = st.getItemMeta();
            stmeta.setLore(lore);
            stmeta.setDisplayName(name);
            stmeta.setEnchantmentGlintOverride(true);
            stmeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            st.setItemMeta(stmeta);
//            st.addUnsafeEnchantment(enchantment, 1);
            e.setItem(slot, st);
        }

        public void setItem(int slot, String name, Material m) {
            ItemStack st = new ItemStack(m);
            ItemMeta stmeta = st.getItemMeta();
            stmeta.setDisplayName(name);
            st.setItemMeta(stmeta);
            e.setItem(slot, st);
        }


        public Inventory getKitMenu() {
            return e;
        }
    }

    //COMBINE 2 METHOD
}
