package org.dynmap.mobs;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.event.*;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.*;
import org.dynmap.markers.Marker;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.*;

import javax.net.ssl.HttpsURLConnection;

public class DynmapMobsPlugin extends JavaPlugin {
    private static Logger log;
    Plugin dynmap;
    DynmapAPI api;
    MarkerAPI markerapi;
    FileConfiguration cfg;
    MobLayerConfig mconf = new MobLayerConfig("mocreat_mob");
    MobLayerConfig hconf = new MobLayerConfig("hostile_mob");
    MobLayerConfig pconf = new MobLayerConfig("passive_mob");
    MobLayerConfig vconf = new MobLayerConfig("vehicle", true);
    double res; /* Position resolution */
    int hideifundercover;
    int hideifshadow;
    boolean stop;
    boolean isdev;
    boolean reload = false;
    static String obcpackage;
    static String nmspackage;
    Method gethandle;
    
    @Override
    public void onLoad() {
        log = this.getLogger();
    }
    
    public static String mapClassName(String n) {
        if(n.startsWith("org.bukkit.craftbukkit")) {
            n = getOBCPackage() + n.substring("org.bukkit.craftbukkit".length());
        }
        else if(n.startsWith("net.minecraft.server")) {
            n = getNMSPackage() + n.substring("net.minecraft.server".length());
        }
        return n;
    }

    /* Mapping of mobs to icons */
    private static class MobMapping {
        String mobid;
        boolean enabled;
        Class<Entity> mobclass;
        Class<?> entclass;
        String cls_id;
        String entclsid;
        String label;
        MarkerIcon icon;
        
        MobMapping(String id, String clsid, String lbl) {
            this(id, clsid, lbl, null);
        }

        MobMapping(String id, String clsid, String lbl, String entclsid) {
            mobid = id;
            label = lbl;
            cls_id = clsid;
        }
        @SuppressWarnings("unchecked")
        public void init() {
            try {
                mobclass = (Class<Entity>) Class.forName(mapClassName(cls_id));
            } catch (ClassNotFoundException cnfx) {
                mobclass = null;
            }
            try {
                this.entclsid = entclsid;
                if(entclsid != null) {
                    entclass = (Class<?>) Class.forName(mapClassName(entclsid));
                }
            } catch (ClassNotFoundException cnfx) {
                entclass = null;
            }
        }
    };

    private MobMapping config_mocreat_mobs[] = {
        // Mo'Creatures
        new MobMapping("horse", "org.bukkit.entity.Animals", "Horse", "net.minecraft.server.MoCEntityHorse"),
        new MobMapping("fireogre", "org.bukkit.entity.Monster", "Fire Ogre", "net.minecraft.server.MoCEntityFireOgre"),
        new MobMapping("caveogre", "org.bukkit.entity.Monster", "Cave Ogre", "net.minecraft.server.MoCEntityCaveOgre"),
        new MobMapping("ogre", "org.bukkit.entity.Monster", "Ogre", "net.minecraft.server.MoCEntityOgre"),
        new MobMapping("boar", "org.bukkit.entity.Pig", "Boar", "net.minecraft.server.MoCEntityBoar"),
        new MobMapping("polarbear", "org.bukkit.entity.Animals", "Polar Bear", "net.minecraft.server.MoCEntityPolarBear"),
        new MobMapping("bear", "org.bukkit.entity.Animals", "Bear", "net.minecraft.server.MoCEntityBear"),
        new MobMapping("duck", "org.bukkit.entity.Chicken", "Duck", "net.minecraft.server.MoCEntityDuck"),
        new MobMapping("bigcat", "org.bukkit.entity.Animals", "Big Cat", "net.minecraft.server.MoCEntityBigCat"),
        new MobMapping("deer", "org.bukkit.entity.Animals", "Deer", "net.minecraft.server.MoCEntityDeer"),
        new MobMapping("wildwolf", "org.bukkit.entity.Monster", "Wild Wolf", "net.minecraft.server.MoCEntityWWolf"),
        new MobMapping("flamewraith", "org.bukkit.entity.Monster", "Flame Wraith", "net.minecraft.server.MoCEntityFlameWraith"),
        new MobMapping("wraith", "org.bukkit.entity.Monster", "Wraith", "net.minecraft.server.MoCEntityWraith"),
        new MobMapping("bunny", "org.bukkit.entity.Animals", "Bunny", "net.minecraft.server.MoCEntityBunny"),
        new MobMapping("bird", "org.bukkit.entity.Animals", "Bird", "net.minecraft.server.MoCEntityBird"),
        new MobMapping("fox", "org.bukkit.entity.Animals", "Fox", "net.minecraft.server.MoCEntityFox"),
        new MobMapping("werewolf", "org.bukkit.entity.Monster", "Werewolf", "net.minecraft.server.MoCEntityWerewolf"),
        new MobMapping("shark", "org.bukkit.entity.WaterMob", "Shark", "net.minecraft.server.MoCEntityShark"),
        new MobMapping("dolphin", "org.bukkit.entity.WaterMob", "Shark", "net.minecraft.server.MoCEntityDolphin"),
        new MobMapping("fishy", "org.bukkit.entity.WaterMob", "Fishy", "net.minecraft.server.MoCEntityFishy"),
        new MobMapping("kitty", "org.bukkit.entity.Animals", "Kitty", "net.minecraft.server.MoCEntityKitty"),
        new MobMapping("hellrat", "org.bukkit.entity.Monster", "Hell Rat", "net.minecraft.server.MoCEntityHellRat"),
        new MobMapping("rat", "org.bukkit.entity.Monster", "Rat", "net.minecraft.server.MoCEntityRat"),
        new MobMapping("mouse", "org.bukkit.entity.Animals", "Mouse", "net.minecraft.server.MoCEntityMouse"),
        new MobMapping("scorpion", "org.bukkit.entity.Monster", "Scorpion", "net.minecraft.server.MoCEntityScorpion"),
        new MobMapping("turtle", "org.bukkit.entity.Animals", "Turtle", "net.minecraft.server.MoCEntityTurtle"),
        new MobMapping("crocodile", "org.bukkit.entity.Animals", "Crocodile", "net.minecraft.server.MoCEntityCrocodile"),
        new MobMapping("ray", "org.bukkit.entity.WaterMob", "Ray", "net.minecraft.server.MoCEntityRay"),
        new MobMapping("jellyfish", "org.bukkit.entity.WaterMob", "Jelly Fish", "net.minecraft.server.MoCEntityJellyFish"),
        new MobMapping("goat", "org.bukkit.entity.Animals", "Goat", "net.minecraft.server.MoCEntityGoat"),
        new MobMapping("snake", "org.bukkit.entity.Animals", "Snake", "net.minecraft.server.MoCEntitySnake"),
        new MobMapping("ostrich", "org.bukkit.entity.Animals", "Ostrich", "net.minecraft.server.MoCEntityOstrich")
    };

    private MobMapping config_hostile_mobs[] = {
        // Standard hostile
        new MobMapping("elderguardian", "org.bukkit.entity.ElderGuardian", "Elder Guardian"),
        new MobMapping("witherskeleton", "org.bukkit.entity.WitherSkeleton", "Wither Skeleton"),
        new MobMapping("stray", "org.bukkit.entity.Stray", "Stray"),
        new MobMapping("husk", "org.bukkit.entity.Husk", "Husk"),
        new MobMapping("zombievillager", "org.bukkit.entity.ZombieVillager", "Zombie Villager"),
        new MobMapping("evoker", "org.bukkit.entity.Evoker", "Evoker"),
        new MobMapping("vex", "org.bukkit.entity.Vex", "Vex"),
        new MobMapping("vindicator", "org.bukkit.entity.Vindicator", "Vindicator"),
        new MobMapping("creeper", "org.bukkit.entity.Creeper", "Creeper"),
        new MobMapping("skeleton", "org.bukkit.entity.Skeleton", "Skeleton"),
        new MobMapping("giant", "org.bukkit.entity.Giant", "Giant"),
        new MobMapping("ghast", "org.bukkit.entity.Ghast", "Ghast"),
        new MobMapping("drowned", "org.bukkit.entity.Drowned", "Drowned"),
        new MobMapping("phantom", "org.bukkit.entity.Phantom", "Phantom"),
        new MobMapping("zombiepigman", "org.bukkit.entity.PigZombie", "Zombified Piglin"),
        new MobMapping("zombie", "org.bukkit.entity.Zombie", "Zombie"), /* Must be last zombie type */
        new MobMapping("enderman", "org.bukkit.entity.Enderman", "Enderman"),
        new MobMapping("cavespider", "org.bukkit.entity.CaveSpider", "Cave Spider"),
        new MobMapping("spider", "org.bukkit.entity.Spider", "Spider"), /* Must be last spider type */
        new MobMapping("spiderjockey", "org.bukkit.entity.Spider", "Spider Jockey"), /* Must be just after spider */
        new MobMapping("silverfish", "org.bukkit.entity.Silverfish", "Silverfish"),
        new MobMapping("blaze", "org.bukkit.entity.Blaze", "Blaze"),
        new MobMapping("magmacube", "org.bukkit.entity.MagmaCube", "Magma Cube"),
        new MobMapping("slime", "org.bukkit.entity.Slime", "Slime"), /* Must be last slime type */
        new MobMapping("enderdragon", "org.bukkit.entity.EnderDragon", "Ender Dragon"),
        new MobMapping("wither", "org.bukkit.entity.Wither", "Wither"),
        new MobMapping("witch", "org.bukkit.entity.Witch", "Witch"),
        new MobMapping("endermite", "org.bukkit.entity.Endermite", "Endermite"),
        new MobMapping("guardian", "org.bukkit.entity.Guardian", "Guardian"),
        new MobMapping("shulker", "org.bukkit.entity.Shulker", "Shulker"),
        new MobMapping("ravager", "org.bukkit.entity.Ravager", "Ravager"),
        new MobMapping("illusioner", "org.bukkit.entity.Illusioner", "Illusioner"),
        new MobMapping("pillager", "org.bukkit.entity.Pillager", "Pillager"),
        new MobMapping("piglin", "org.bukkit.entity.Piglin", "Piglin"),
        new MobMapping("hoglin", "org.bukkit.entity.Hoglin", "Hoglin"),
        new MobMapping("zoglin", "org.bukkit.entity.Zoglin", "Zoglin"),
        new MobMapping("warden", "org.bukkit.entity.Warden", "Warden")
    };

    private MobMapping config_passive_mobs[] = {
        // Standard passive
        new MobMapping("skeletonhorse", "org.bukkit.entity.SkeletonHorse", "Skeleton Horse"),
        new MobMapping("zombiehorse", "org.bukkit.entity.ZombieHorse", "Zombie Horse"),
        new MobMapping("donkey", "org.bukkit.entity.Donkey", "Donkey"),
        new MobMapping("tameddonkey", "org.bukkit.entity.Donkey", "Donkey"),
        new MobMapping("mule", "org.bukkit.entity.Mule", "Mule"),
        new MobMapping("tamedmule", "org.bukkit.entity.Mule", "Mule"),
        new MobMapping("bat", "org.bukkit.entity.Bat", "Bat"),
        new MobMapping("pig", "org.bukkit.entity.Pig", "Pig"),
        new MobMapping("sheep", "org.bukkit.entity.Sheep", "Sheep"),
        new MobMapping("cow", "org.bukkit.entity.Cow", "Cow"),
        new MobMapping("chicken", "org.bukkit.entity.Chicken", "Chicken"),
        new MobMapping("chickenjockey", "org.bukkit.entity.Chicken", "Chicken Jockey"), /* Must be just after chicken */
        new MobMapping("squid", "org.bukkit.entity.Squid", "Squid"),
        new MobMapping("wolf", "org.bukkit.entity.Wolf", "Wolf"),
        new MobMapping("tamedwolf", "org.bukkit.entity.Wolf", "Wolf"), /* Must be just after wolf */
        new MobMapping("mooshroom", "org.bukkit.entity.MushroomCow", "Mooshroom"),
        new MobMapping("snowgolem", "org.bukkit.entity.Snowman", "Snow Golem"),
        new MobMapping("ocelot", "org.bukkit.entity.Ocelot", "Ocelot"),
        new MobMapping("cat", "org.bukkit.entity.Cat", "Cat"),
        new MobMapping("tamedcat", "org.bukkit.entity.Cat", "Cat"),
        new MobMapping("golem", "org.bukkit.entity.IronGolem", "Iron Golem"),
        new MobMapping("vanillahorse", "org.bukkit.entity.Horse", "Horse"),
        new MobMapping("tamedvanillahorse", "org.bukkit.entity.Horse", "Horse"),
        new MobMapping("rabbit", "org.bukkit.entity.Rabbit", "Rabbit"),
        new MobMapping("vanillapolarbear", "org.bukkit.entity.PolarBear", "Polar Bear"),
        new MobMapping("traderllama", "org.bukkit.entity.TraderLlama", "Trader Llama"),
        new MobMapping("tamedtraderllama", "org.bukkit.entity.TraderLlama", "Trader Llama"),
        new MobMapping("llama", "org.bukkit.entity.Llama", "Llama"),
        new MobMapping("tamedllama", "org.bukkit.entity.Llama", "Llama"),
        new MobMapping("wandering_trader", "org.bukkit.entity.WanderingTrader", "Wandering Trader"),
        new MobMapping("villager", "org.bukkit.entity.Villager", "Villager"),
        new MobMapping("vanilladolphin", "org.bukkit.entity.Dolphin", "Dolphin"),
        new MobMapping("cod", "org.bukkit.entity.Cod", "Cod"),
        new MobMapping("salmon", "org.bukkit.entity.Salmon", "Salmon"),
        new MobMapping("pufferfish", "org.bukkit.entity.PufferFish", "Pufferfish"),
        new MobMapping("tropicalfish", "org.bukkit.entity.TropicalFish", "Tropical Fish"),
        new MobMapping("vanillaturtle", "org.bukkit.entity.Turtle", "Turtle"),
        new MobMapping("parrot", "org.bukkit.entity.Parrot", "Parrot"),
        new MobMapping("tamedparrot", "org.bukkit.entity.Parrot", "Parrot"),
        new MobMapping("panda", "org.bukkit.entity.Panda", "Panda"),
        new MobMapping("vanillafox", "org.bukkit.entity.Fox", "Fox" ),
        new MobMapping("bee", "org.bukkit.entity.Bee", "Bee" ),
        new MobMapping("strider", "org.bukkit.entity.Strider", "Strider"),
        new MobMapping("glowsquid", "org.bukkit.entity.GlowSquid", "Glow Squid"),
        new MobMapping("vanillagoat", "org.bukkit.entity.Goat", "Goat"),
        new MobMapping("axolotl", "org.bukkit.entity.Axolotl", "Axolotl"),
        new MobMapping("allay", "org.bukkit.entity.Allay", "Allay"),
        new MobMapping("frog", "org.bukkit.entity.Frog", "Frog"),
        new MobMapping("tadpole", "org.bukkit.entity.Tadpole", "Tadpole")
    };

    private MobMapping config_vehicles[] = {
            // Command Minecart
            new MobMapping("command-minecart", "org.bukkit.entity.minecart.CommandMinecart", "Command Minecart"),
            // Explosive Minecart
            new MobMapping("explosive-minecart", "org.bukkit.entity.minecart.ExplosiveMinecart", "Explosive Minecart"),
            // Hopper Minecart
            new MobMapping("hopper-minecart", "org.bukkit.entity.minecart.HopperMinecart", "Hopper Minecart"),
            // Powered Minecart
            new MobMapping("powered-minecart", "org.bukkit.entity.minecart.PoweredMinecart", "Powered Minecart"),
            // Rideable Minecart
            new MobMapping("minecart", "org.bukkit.entity.minecart.RideableMinecart", "Minecart"),
            // Spawner Minecart
            new MobMapping("spawner-minecart", "org.bukkit.entity.minecart.SpawnerMinecart", "Spawner Minecart"),
            // Storage Minecart
            new MobMapping("storage-minecart", "org.bukkit.entity.minecart.StorageMinecart", "Storage Minecart"),
            // Boat
            new MobMapping("boat", "org.bukkit.entity.Boat", "Boat"),
            // Storage Boat
            new MobMapping("storage-boat", "org.bukkit.entity.ChestBoat", "Boat with Chest")
    };
        
    public static void info(String msg) {
        log.log(Level.INFO, msg);
    }
    public static void severe(String msg) {
        log.log(Level.SEVERE, msg);
    }

    private class MobLayerConfig {
        MobMapping[] mobmap;
        MarkerSet set;
        Map<Integer,Marker> mobicons = new HashMap<Integer, Marker>();
        HashMap<String, Integer> cache = new HashMap<String, Integer>();
        boolean tinyicons;
        boolean nolabels;
        boolean inc_coord;
        boolean isVehicle;
        long period;
        int updates_per_tick;
        String identifier;

        MobLayerConfig(String identifier) {
            this(identifier, false);
        }
        MobLayerConfig(String identifier, boolean isVehicle) {
            this.identifier = identifier;
            this.isVehicle = isVehicle;
        }

    }
    private class MobUpdate implements Runnable {
        Map<Integer,Marker> newmap = new HashMap<Integer,Marker>();
        ArrayList<World> worldsToDo = null;
        List<LivingEntity> mobsToDo = null;
        int mobIndex = 0;
        World curWorld = null;
        MobLayerConfig ml_config;

        MobUpdate(MobLayerConfig conf) {
            ml_config = conf;
        }
        
        public void run() {
            if(stop || ml_config.mobmap == null || ml_config.mobmap.length == 0 || ml_config.set == null) {
                info("Something is null or 0");
                return;
            }

            if(worldsToDo == null) {
                worldsToDo = new ArrayList<World>(getServer().getWorlds());
            }
            while(mobsToDo == null) {
                if(worldsToDo.isEmpty()) {
                    info("No worlds");
                    //Review old map
                    for(Marker oldm : ml_config.mobicons.values()) {
                        oldm.deleteMarker();
                    }
                    // Replace with new map
                    ml_config.mobicons = newmap;
                    // Schedule next run
                    getServer().getScheduler().scheduleSyncDelayedTask(DynmapMobsPlugin.this, new MobUpdate(ml_config), ml_config.period);
                    return;
                }
                else {
                    curWorld = worldsToDo.remove(0);
                    mobsToDo = curWorld.getLivingEntities();
                    mobIndex = 0;
                    if((mobsToDo != null) && mobsToDo.isEmpty()) {
                        mobsToDo = null;
                    }
                }
            }

            // Process up to limit per tick
            for(int cnt = 0; cnt < ml_config.updates_per_tick; cnt++) {
                if(mobIndex >= mobsToDo.size()) {
                    mobsToDo = null;
                    break;
                }
                // Get next entity
                LivingEntity le = mobsToDo.get(mobIndex);
                mobIndex++;
                int i;

                // Do some weird shit
                String clsid = null;
                if(gethandle != null) {
                    try {
                        clsid = gethandle.invoke(le).getClass().getName();
                    }
                    catch (Exception x) {}
                }

                if(clsid == null) {
                    clsid = le.getClass().getName();
                }

                // Get index from cache
                Integer idx = ml_config.cache.get(clsid);
                // clsid not in cache
                if(idx == null) {
                    for(i = 0; i < ml_config.mobmap.length; i++) {
                        if((ml_config.mobmap[i].mobclass != null) && ml_config.mobmap[i].mobclass.isInstance(le)) {
                            if(ml_config.mobmap[i].entclsid == null) break;
                            else if(gethandle != null) {
                                Object obcentity = null;
                                try {
                                    obcentity = gethandle.invoke(le);
                                }
                                catch (Exception x) {}

                                if((ml_config.mobmap[i].entclass != null) && (obcentity != null) && (ml_config.mobmap[i].entclass.isInstance(obcentity))) break;
                            }
                        }
                    }
                    ml_config.cache.put(clsid, i);
                }
                // Found in cache
                else {
                    i = idx;
                }

                // Safeguard against IndexOutOfBounds
                if(i >= ml_config.mobmap.length) continue;

                Location loc = le.getLocation();
                if(ml_config.isVehicle && curWorld.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4) == false) continue;
                if(isHidden(loc)) continue;
                double x = Math.round(loc.getX() / res) * res;
                double y = Math.round(loc.getY() / res) * res;
                double z = Math.round(loc.getZ() / res) * res;

                // Set label
                Object[] obj = getLabel(le, i, x, y, z);
                i = (int)obj[1];
                // Another safeguard against IndexOutOfBounds
                if(i >= ml_config.mobmap.length) continue;
                String label = (String) obj[0];

                // Create Marker
                createUpdateMarker(le.getEntityId(), label, x, y, z, ml_config.mobmap[i].icon);
            }
            getServer().getScheduler().scheduleSyncDelayedTask(DynmapMobsPlugin.this, this, 1);
        }

        public Object[] getLabel(LivingEntity le, int i, double x, double y, double z) {
            String lbl = null;
            // Check for spider jockey
            if(ml_config.mobmap[i].mobid.equals("spider")) {
                if(le.getPassengers() != null && !le.getPassengers().isEmpty()) {
                    i = find("spiderjockey", hconf.mobmap);
                }
            }
            // check for chicken jockey
            else if(ml_config.mobmap[i].mobid.equals("chicken")) {
                if(le.getPassengers() != null && !le.getPassengers().isEmpty()) {
                    i = find("chickenjockey", pconf.mobmap);
                }
            }
            else if(ml_config.mobmap[i].mobid.equals("villager")) {
                Villager v = (Villager)le;
                Profession p = v.getProfession();
                if(p != null) {
                    switch(p) {
                        case NONE:
                            lbl = "Villager";
                            break;
                        case ARMORER:
                            lbl = "Armorer";
                            break;
                        case BUTCHER:
                            lbl = "Butcher";
                            break;
                        case CARTOGRAPHER:
                            lbl = "Cartographer";
                            break;
                        case CLERIC:
                            lbl = "Cleric";
                            break;
                        case FARMER:
                            lbl = "Farmer";
                            break;
                        case FISHERMAN:
                            lbl = "Fisherman";
                            break;
                        case FLETCHER:
                            lbl = "Fletcher";
                            break;
                        case LEATHERWORKER:
                            lbl = "Leatherworker";
                            break;
                        case LIBRARIAN:
                            lbl = "Librarian";
                            break;
                        case MASON:
                            lbl = "Mason";
                            break;
                        case NITWIT:
                            lbl = "Nitwit";
                            break;
                        case SHEPHERD:
                            lbl = "Shepherd";
                            break;
                        case TOOLSMITH:
                            lbl = "Toolsmith";
                            break;
                        case WEAPONSMITH:
                            lbl = "Weaponsmith";
                            break;
                    }
                }
            }
            else if(ml_config.mobmap[i].mobid.equals("zombiehorse")
                 || ml_config.mobmap[i].mobid.equals("skeletonhorse")) {    /* Check for rider */
                List<Entity> passengers = le.getPassengers();
                if(passengers != null && !passengers.isEmpty()) { /* Has passenger? */
                    Entity e = passengers.get(0);
                    if (e instanceof Player) {
                        lbl = ml_config.mobmap[i].label + " (" + ((Player)e).getName() + ")";
                    }
                }
            }
           /*
            * Check if entity is tameable
            * Append owner to label
            * Requires all tamed mobids to start with "tamed"
            * NOTE: Something caused index out of bounds here once, probably weird java/file cache issue
            */
           else if(le instanceof Tameable) {
               info("Is tameable: " + le.getClass().getName());
               Tameable tameable = (Tameable)le;
               if(tameable.isTamed()) {
                   i = find("tamed" + ml_config.mobmap[i].mobid, pconf.mobmap);
                   AnimalTamer t = tameable.getOwner();
                   if((t != null) && (t instanceof OfflinePlayer)) {
                       lbl = ml_config.mobmap[i].label + " (" + ((OfflinePlayer)t).getName() + ")";
                   }
               }
            }

            if (i >= ml_config.mobmap.length) return new Object[] {"", i};

            if(!ml_config.nolabels) {
                if(lbl == null) {
                    lbl = ml_config.mobmap[i].label;
                }

                if(le.getCustomName() != null) {
                    lbl = le.getCustomName() + " (" + lbl + ")";
                }

                if(ml_config.inc_coord) {
                    lbl = lbl + " [" + (int)x + "," + (int)y + "," + (int)z + "]";
                }
            }
            else lbl = "";

            return new Object[] {lbl, i};
        }
        public void createUpdateMarker(int entityID, String label, double x, double y, double z, MarkerIcon icon) {
            // Get existent marker
            Marker m = ml_config.mobicons.remove(entityID);

            // Create new marker
            if(m == null) {
                m = ml_config.set.createMarker(ml_config.identifier+entityID, label, curWorld.getName(), x, y, z, icon, false);
            }
            // Update marker
            else {
                m.setLocation(curWorld.getName(), x, y, z);
                m.setLabel(label);
                m.setMarkerIcon(icon);
            }
            // Add marker to new map
            if (m != null) {
                newmap.put(entityID, m);
            }
        }
    }

    /**
     * Checks for updates on Github.
     */
    private class UpdateCheck implements Runnable {
        private final String updateURL = "https://api.github.com/repos/Plastikmensch/dynmap-mobs/releases/latest";
        private final String downloadURL = "https://github.com/Plastikmensch/dynmap-mobs/releases/latest";
        /* Delay between update checks in server ticks. 25h by default. */
        private final long delay = 1800000L;
        /* ETag used for conditional requests */
        private String etag = null;
        /* Cached release tag */
        private String cachedRelease = null;
        
        /**
         * Compares release tag with plugin version
         */
        public void run() {
            getVersion(version -> {
                String curVersion = DynmapMobsPlugin.this.getDescription().getVersion();
                cachedRelease = version;
                int compare = compareVersions(curVersion, version);
                
                //FIXME: higher dev build notifies about earlier release
                if(compare != -1) {
                    if(compare == 0) {
                        if(isdev) {
                            info("There is a stable release of " + curVersion + " available");
                            info("Get it at " + downloadURL);
                        }
                    }
                    else {
                        info("Version " + version + " is available. You are running " + curVersion);
                        info("Get it at " + downloadURL);
                    }
                }
            });
            getServer().getScheduler().scheduleSyncDelayedTask(DynmapMobsPlugin.this, this, delay);
        }

        /**
         * Parses the body of the GET request and looks for the release tag
         * @param consumer Resolves to release tag
         */
        public void getVersion(final Consumer<String> consumer) {
            getServer().getScheduler().runTaskAsynchronously(DynmapMobsPlugin.this, () -> {
                // Try getting response body
                try (InputStream inputStream = tryConnect().getInputStream(); Scanner scanner = new Scanner(inputStream)) {
                    // split input stream at ","s
                    scanner.useDelimiter(",");
                    // iterate over elements
                    while (scanner.hasNext()) {
                        String key = scanner.next();
                        if(key.contains("tag_name")) {
                            // get release tag
                            String tag = key.split(":")[1].replaceAll("\"", "");
                            // check that release tag has the correct format
                            if (tag.matches("v\\d+\\.\\d+(\\.\\d+)?")) {
                                // return release tag without leading v
                                consumer.accept(tag.substring(1));
                                return;
                            }
                            else throw new Exception("Malformed release tag");
                        }
                    }
                    // if no release tag found, use cached release tag
                    consumer.accept(cachedRelease);
                } catch (Exception e) {
                    severe("Unable to check for updates: " + e.getMessage());
                }
            });
        }
        /**
         * Connect to the github api
         * @return The connection to the github api
         * @throws IOException
         */
        public HttpsURLConnection tryConnect() throws IOException {
            HttpsURLConnection connection = (HttpsURLConnection) new URL(updateURL).openConnection();

            if (etag != null) {
                connection.setRequestProperty("If-None-Match", etag);
            }
            connection.connect();
            if (connection.getResponseCode() == HttpsURLConnection.HTTP_OK) etag = connection.getHeaderField("etag");
            return connection;
        }

        /**
         * Compares two version strings
         * @param version Current version
         * @param newVersion Latest version
         * @return index of mismatch or 0 if same, -1 if ahead
         */
        public int compareVersions(String version, String newVersion) {
            if(!version.equals(newVersion)) {
                try {
                    Pattern semver = Pattern.compile("^v?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$");
                    Matcher current = semver.matcher(version);
                    Matcher latest = semver.matcher(newVersion);

                    if(current.find() && latest.find()) {
                        for (int i=1; i<=3;i++) {
                            if(Integer.parseInt(current.group(i)) < Integer.parseInt(latest.group(i))) return i;
                        }
                        return -1;
                    }
                    else throw new IllegalArgumentException("Invalid semver");
                }
                catch (Exception e) {
                    severe("Can't compare versions: " + e.getMessage());
                    return -1;
                }
            }
            return 0;
        }
    }

    /**
     * Look for index of mobid in provided MobMapping.
     * @param mobid ID to match
     * @param mobs List of mobs
     * @return Index of mobid, otherwise length 
     */
    private int find(String mobid, MobMapping[] mobs) {
        int idx = 0;
        while (idx < mobs.length) {
            if(mobs[idx].mobid.equals(mobid)) return idx;
            idx++;
        }
        severe("find: Couldn't find " + mobid);
        return mobs.length;
    }

    /**
     * Check whether entity is considered hidden
     * @param loc Location of entity
     * @return true if hidden, otherwise false
     */
    private boolean isHidden(Location loc) {
        Block blk = loc.getBlock();
        if(hideifshadow < 15) {
            if(blk.getLightLevel() <= hideifshadow) {
                return true;
            }
        }
        if(hideifundercover < 15) {
            if(blk.getLightFromSky() <= hideifundercover) {
                return true;
            }
        }
        return false;
    }

    private class OurServerListener implements Listener {
        @EventHandler(priority=EventPriority.MONITOR)
        public void onPluginEnable(PluginEnableEvent event) {
            Plugin p = event.getPlugin();
            String name = p.getDescription().getName();
            if(name.equals("dynmap")) {
                activate();
            }
        }
    }
    
    public void onEnable() {
        info("initializing");
        PluginManager pm = getServer().getPluginManager();
        /* Get dynmap */
        dynmap = pm.getPlugin("dynmap");
        if(dynmap == null) {
            severe("Cannot find dynmap!");
            return;
        }
        api = (DynmapAPI)dynmap; /* Get API */

        getServer().getPluginManager().registerEvents(new OurServerListener(), this);        

        /* If enabled, activate */
        if(dynmap.isEnabled())
            activate();

    }

    private static String getNMSPackage() {
        if (nmspackage == null) {
            Server srv = Bukkit.getServer();
            /* Get getHandle() method */
            try {
                Method m = srv.getClass().getMethod("getHandle");
                Object scm = m.invoke(srv); /* And use it to get SCM (nms object) */
                nmspackage = scm.getClass().getPackage().getName();
            } catch (Exception x) {
                nmspackage = "net.minecraft.server";
            }
        }
        return nmspackage;
    }
    private static String getOBCPackage() {
        if (obcpackage == null) {
            obcpackage = Bukkit.getServer().getClass().getPackage().getName();
        }
        return obcpackage;
    }

    private void activate() {
        // Look up the getHandle method for CraftEntity
        try {
            Class<?> cls = Class.forName(mapClassName("org.bukkit.craftbukkit.entity.CraftEntity"));
            gethandle = cls.getMethod("getHandle");
        } catch (ClassNotFoundException cnfx) {
        } catch (NoSuchMethodException e) {
        } catch (SecurityException e) {
        }
        if(gethandle == null) {
            severe("Unable to locate CraftEntity.getHandle() - cannot process most Mo'Creatures mobs");
        }
        
        // Now, get markers API
        markerapi = api.getMarkerAPI();
        if(markerapi == null) {
            severe("Error loading Dynmap marker API!");
            return;
        }
        // Load configuration
        if(reload) {
            reloadConfig();

            reset();
        }
        else {
            reload = true;
        }
        this.saveDefaultConfig();
        cfg = getConfig();
        cfg.options().copyDefaults(true);   /* Load defaults, if needed */
        this.saveConfig();  /* Save updates, if needed */

        // Check if build is dev build
        isdev = this.getDescription().getVersion().contains("-");

        if(isdev) info("You are using an unstable build. Use at your own risk");

        hideifshadow = cfg.getInt("update.hideifshadow", 15);
        hideifundercover = cfg.getInt("update.hideifundercover", 15);
        // Position resolution
        res = cfg.getDouble("update.resolution", 1.0);
        // Update period
        double per = cfg.getDouble("update.period", 5.0);
        if(per < 2.0) per = 2.0;
        long updperiod = (long)(per*20.0);
        mconf.period = updperiod;
        hconf.period = updperiod;
        pconf.period = updperiod;
        // Vehicle update period
        double vper = cfg.getDouble("update.vehicleperiod", 10.0);
        if(vper < 2.0) vper = 2.0;
        vconf.period = (long)(vper*20.0) / 3;

        int updates_per_tick = cfg.getInt("update.mobs-per-tick", 20);
        mconf.updates_per_tick = updates_per_tick;
        hconf.updates_per_tick = updates_per_tick;
        pconf.updates_per_tick = updates_per_tick;
        vconf.updates_per_tick = cfg.getInt("update.vehicles-per-tick", 20);
        stop = false;

        loadConfig("mocreatlayer", "mocreat_mobs", mconf, config_mocreat_mobs);
        loadConfig("hostilelayer", "hostile_mobs", hconf, config_hostile_mobs);
        loadConfig("passivelayer", "passive_mobs", pconf, config_passive_mobs);
        loadConfig("vehiclelayer", "vehicles", vconf, config_vehicles);

        // Update Check
        if(cfg.getBoolean("general.update-check", true)) {
            getServer().getScheduler().scheduleSyncDelayedTask(this, new UpdateCheck());
            info("Update check enabled");
        }
        else info("Update check disabled");

        info("Activated");
    }

    public void loadConfig(String layer, String ident, MobLayerConfig conf, MobMapping conf_mobs[]) {
        conf.tinyicons = cfg.getBoolean(layer + ".tinyicons", false);
        conf.nolabels = cfg.getBoolean(layer + ".nolabels", false);
        conf.inc_coord = cfg.getBoolean(layer + ".inc-coord", false);

        // Check which entities are enabled
        Set<Class<Entity>> clsset = new HashSet<Class<Entity>>();
        int cnt = 0;
        for(int i = 0; i < conf_mobs.length; i++) {
            conf_mobs[i].init();
            conf_mobs[i].enabled = cfg.getBoolean(ident + "." + conf_mobs[i].mobid, false);
            conf_mobs[i].icon = markerapi.getMarkerIcon(ident + "." + conf_mobs[i].mobid);
            InputStream in = null;
            if(conf.tinyicons)
                in = getClass().getResourceAsStream("/8x8/" + conf_mobs[i].mobid + ".png");
            if(in == null)
                in = getClass().getResourceAsStream("/" + conf_mobs[i].mobid + ".png");
            if(in != null) {
                if(conf_mobs[i].icon == null)
                    conf_mobs[i].icon = markerapi.createMarkerIcon(ident + "." + conf_mobs[i].mobid, conf_mobs[i].label, in);
                else    // Update image
                    conf_mobs[i].icon.setMarkerIconImage(in);
            }
            if(conf_mobs[i].icon == null) {
                conf_mobs[i].icon = markerapi.getMarkerIcon(MarkerIcon.DEFAULT);
            }
            if(conf_mobs[i].enabled) {
                cnt++;
            }
        }
        // Make list of just enabled mobs */
        conf.mobmap = new MobMapping[cnt];
        for(int i = 0, j = 0; i < conf_mobs.length; i++) {
            if(conf_mobs[i].enabled) {
                conf.mobmap[j] = conf_mobs[i];
                j++;
                clsset.add(conf_mobs[i].mobclass);
            }
        }

        // Now, add marker set for mobs (make it transient)
        if(conf.mobmap.length > 0) {
            conf.set = markerapi.getMarkerSet(ident + ".markerset");
            if(conf.set == null)
                conf.set = markerapi.createMarkerSet(ident + ".markerset", cfg.getString(layer + ".name", "NoName"), null, false);
            else
                conf.set.setMarkerSetLabel(cfg.getString(layer + ".name", "NoName"));
            if(conf.set == null) {
                severe("Error creating marker set");
                return;
            }
            conf.set.setLayerPriority(cfg.getInt(layer + ".layerprio", 10));
            conf.set.setHideByDefault(cfg.getBoolean(layer + ".hidebydefault", false));
            int minzoom = cfg.getInt(layer + ".minzoom", 0);
            if(minzoom > 0) // Don't call if non-default - lets us work with pre-0.28 dynmap
                conf.set.setMinZoom(minzoom);
            getServer().getScheduler().scheduleSyncDelayedTask(this, new MobUpdate(conf), conf.period);
            info(layer + " enabled");
        }
        else {
            info(layer + " disabled");
        }
    }

    public void onDisable() {
        reset();
        stop = true;
    }

    public void reset() {
        //TODO: A clear function could be attached to class directly
        if (mconf.set != null) {
            mconf.set.deleteMarkerSet();
            mconf.set = null;
        }
        if (hconf.set != null) {
            hconf.set.deleteMarkerSet();
            hconf.set = null;
        }
        if (pconf.set != null) {
            pconf.set.deleteMarkerSet();
            pconf.set = null;
        }

        if (vconf.set != null) {
            vconf.set.deleteMarkerSet();
            vconf.set = null;
        }

        mconf.mobicons.clear();
        hconf.mobicons.clear();
        pconf.mobicons.clear();
        vconf.mobicons.clear();

        mconf.cache.clear();
        hconf.cache.clear();
        pconf.cache.clear();
        vconf.cache.clear();
    }

}
