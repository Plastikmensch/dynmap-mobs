package org.dynmap.mobs;
import org.bukkit.*;
import org.bukkit.World.Environment;
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
    double res; // Position resolution
    int minSkyLight;
    int minBlockLight;
    int updatesPerTick;
    long updatePeriod;
    boolean stop;
    boolean isDev;
    boolean reload = false;
    boolean debug;
    static String obcpackage;
    static String nmspackage;
    Method gethandle;
    // List containing all information about mobs
    MobList mobList = new MobList();
    // Map containing config for layers
    HashMap<String,MobLayerConfig> layerConfig = new HashMap<String,MobLayerConfig>();
    // Map containing Map of map icons per world
    Map<String,Map<Integer,Marker>> worldIcons= new HashMap<String,Map<Integer,Marker>>();
    
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

    private class MobList {
        // List of all entities
        List<MobData> moblist = new ArrayList<MobData>();

        /**
         * Add data to moblist
         * @param data MobData to add
         */
        void put(MobData data) {
            if(!moblist.contains(data)) moblist.add(data);
        }

        /**
         * Get MobData for id
         * @param id MobID
         * @return MobData for id or null if not found
         */
        MobData get(String id) {
            for (MobData data : moblist) {
                if(data.mobID.equals(id)) return data;
            }
            return null;
        }

        List<MobData> getVehicles() {
            return getSubList("vehicle");
        }

        List<MobData> getHostiles() {
            return getSubList("hostile");
        }

        List<MobData> getPassives() {
            return getSubList("passive");
        }

        private List<MobData> getSubList(String category) {
            //HashMap<String,MobData> subList = new HashMap<String,MobData>();
            List<MobData> subList = new ArrayList<MobData>();
            for (MobData data : moblist) {
                if (data.category.equals(category)) subList.add(data);
            }
            return subList;
        }

        /**
         * Returns the mobID for given entity
         * @param ent Entity
         * @return mobID
         */
        String getMobID(Entity ent) {
            for (MobData entData: moblist) {
                String key = entData.mobID;
                if(entData.mobClass == null) continue;
                if (entData.mobClass.isInstance(ent)) {
                    switch(key) {
                        case "spider": {
                            List<Entity> passengers = ent.getPassengers();
                            if(passengers != null && !passengers.isEmpty() && passengers.get(0) instanceof Skeleton) {
                                key = "spiderjockey";

                                // Create data for spiderjockey
                                if(get(key) == null) {
                                    debug("creating " + key);
                                    put(new MobData(key, null, entData.category, "Spider Jockey"));
                                }
                            }
                            break;
                        }
                        case "chicken": {
                            List<Entity> passengers = ent.getPassengers();
                            if(passengers != null && !passengers.isEmpty()) {
                                //TODO: Differentiate between jockeys
                                key = "chickenjockey";

                                // Create data for chickenjockey
                                if(get(key) == null) {
                                    debug("creating " + key);

                                    // Get category from passenger
                                    //String category = get(getMobID(passengers.get(0))).category;
                                    //debug("Category of passenger: " + category);

                                    // Default to hostile for now, since category is depending on the first chicken jockey found
                                    //TODO: remove after differentiating jockeys 
                                    String category = "hostile";
                                    put(new MobData(key, null, category, "Chicken Jockey"));
                                }
                            }
                            break;
                        }
                        // Traderllama is subclass of llama
                        case "llama": {
                            if (get("traderllama").mobClass.isInstance(ent)) {
                                key = "traderllama";
                            }
                            break;
                        }
                        // PigZombie is subclass of zombie
                        case "zombie": {
                            if (get("zombifiedpiglin").mobClass.isInstance(ent)) {
                                key = "zombifiedpiglin";
                            }
                        }
                        // MagmaCube is subclass of slime
                        case "slime": {
                            if(get("magmacube").mobClass.isInstance(ent)) {
                                key = "magmacube";
                            }
                        }
                        default: {
                            if(ent instanceof Tameable && ((Tameable)ent).isTamed()) {
                                key = "tamed" + key;

                                // Create data for tamed
                                if(get(key) == null) {
                                    debug("creating " + key);
                                    put(new MobData(key, null, entData.category, entData.label));
                                }
                            }
                        }
                    }
                    return key;
                }
            }
            severe("No id for " + ent.getClass().getName());
            return null;
        }

        MarkerIcon getMobIcon(String mobID) {
            MobData data = get(mobID);
            return data.icon;
        }
    }

    private class MobData {
        // Only used internally
        String cls;
        // Used for logic
        String mobID;
        Class<Entity> mobClass;
        String label;
        String category;
        boolean enabled;
        MarkerIcon icon;

        MobData(String mobID, String cls, String category) {
            this(mobID, cls, category, null);
        }
        MobData(String mobID, String cls, String category, String label) {
            this.mobID = mobID;
            this.cls = cls;
            this.category = category;
            if(label == null) label = getLabelFromClass(cls);
            this.label = label;
            setMobClass();
            this.icon = loadIcon();
            this.enabled = cfg.getBoolean(category + "." + mobID, true);
            if (!cfg.contains(category + "." + mobID, true)) severe("Missing ID in config: " + mobID);
        }

        /**
         * Get default label infered from name of class
         * @param cls Class name
         * @return Label to use for this entity
         */
        private String getLabelFromClass(String cls) {
            String label = "";
            // Remove everything before last dot and convert name of class to Title Case
            for (String w : cls.replaceAll("(.+\\.)", "").split("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")) {
                label += w + " ";
            }
            return label.trim();
        }

        @SuppressWarnings("unchecked")
        private void setMobClass() {
            //TODO: Nullcheck in try..catch
            if (cls == null) {
                mobClass = null;
                return;
            }
            try {
                mobClass = (Class<Entity>) Class.forName(mapClassName(cls));
            } catch (ClassNotFoundException cnfx) {
                severe("Not found: " + cnfx.getMessage());
                mobClass = null;
            }
        }

        /**
         * Load icon image from file and create icon
         * @return Icon
         */
        private MarkerIcon loadIcon() {
            // Get icon from dynmap
            MarkerIcon icon = markerapi.getMarkerIcon(category + "." + mobID);

            // Load image from file
            InputStream in = null;
            if(layerConfig.get(category).tinyIcons) {
                debug("Getting tinyicon for " + mobID);
                in = getClass().getResourceAsStream("/8x8/" + mobID + ".png");
            }
            else {
                debug("Getting icon for " + mobID);
                in = getClass().getResourceAsStream("/" + mobID + ".png");
            }

            if(in != null) {
                // Create icon
                if(icon == null) {
                    debug("Creating icon for " + mobID);
                    icon = markerapi.createMarkerIcon(category + "." + mobID, label, in);
                }
                // Update icon image
                else {
                    debug("Updating icon for " + mobID);
                    icon.setMarkerIconImage(in);
                }
            }
            else {
                severe("No resource for " + mobID);
            }
            
            // Fallback to default icon if still null
            if(icon == null) {
                severe("No icon found for " + mobID);
                icon = markerapi.getMarkerIcon(MarkerIcon.DEFAULT);
            }
            
            return icon;
        }

        // Temporary function to log values for debugging
        private void print() {
            debug("mobID: " + mobID + " cls: " + cls + ", mobclass: " + mobClass + ", label: " + label + ", category: " + category + ", enabled: " + enabled + ", icon: " + icon);
        }
    }

    public static void info(String msg) {
        log.log(Level.INFO, msg);
    }
    public static void severe(String msg) {
        log.log(Level.SEVERE, msg);
    }

    /* TODO: Create logger/debug class
     * This is temporary!
     * Simple "debug" function to not print unnecessary logs, while preserving them for development
     * Will be replaced by a logger/debug class later
     */
    public void debug(String msg) {
        if(debug) info(msg);
    }

    private class MobLayerConfig {
        MarkerSet set;
        boolean tinyIcons;
        boolean noLabels;
        boolean incCoord;
        String identifier;

        MobLayerConfig(String category) {
            String layer = category + "layer.";
            identifier = category;
            tinyIcons = cfg.getBoolean(layer + "tinyicons", false);
            noLabels = cfg.getBoolean(layer + "nolabels", false);
            incCoord = cfg.getBoolean(layer + "inc_coord", false);
            set = markerapi.getMarkerSet(layer + "markerset");
            //FIXME: Default label for vehiclelayer becomes "Vehicle Mobs"
            if(set == null)
                set = markerapi.createMarkerSet(layer + "markerset", cfg.getString(layer + "name", category.substring(0, 1).toUpperCase() + category.substring(1) + " Mobs"), null, false);
            else
                set.setMarkerSetLabel(cfg.getString(layer + "name", category.substring(0, 1).toUpperCase() + category.substring(1) + " Mobs"));
            if(set == null) {
                severe("Error creating marker set");
                return;
            }
            set.setLayerPriority(cfg.getInt(layer + "layerprio", 10));
            set.setHideByDefault(cfg.getBoolean(layer + "hidebydefault", false));
            set.setMinZoom(cfg.getInt(layer + "minzoom", 0));
        }
        /**
         * Deletes MarkerSet
         */
        public void clear() {
            if (set != null) {
                set.deleteMarkerSet();
                set = null;
            }
        }

    }

    /*TODO: Might be better to do what was previously done 
     * Use runTaskLater to init this class
     * Get worlds
     * Process entities
     * Self-call/reschedule task 1 tick until all worlds processed
     * init class again after all worlds done, reschedule with updatePeriod
     * Has the disadvantage of not running exactly at updateperiod, but avoids racing conditions  
    */
    private class MobUpdate implements Runnable {
        // Marker reference to processed entities
        Map<String,Map<Integer,Marker>> newMap = new HashMap<String,Map<Integer,Marker>>();
        ArrayList<World> worldsToDo = null;
        List<Entity> entitiesToDo = null;
        World curWorld = null;
        MobLayerConfig mlConfig;

        /*
         * Expected code flow:
         * Get worlds and delete all current markers
         * iterate worlds
         * get entities in world
         * process entities
         */
        public void run() {
            debug("Run started");
            entitiesToDo = null;
            // Get worlds
            if(worldsToDo == null) {
                debug("Getting worlds");
                worldsToDo = new ArrayList<World>(getServer().getWorlds());
            }
            // Get entities
            while (entitiesToDo == null) {
                debug("entitiesToDo is null");
                if (worldsToDo.isEmpty()) {
                    worldsToDo = null;
                    debug("All worlds processed");
                    return;
                }
                else {
                    curWorld = worldsToDo.remove(0);
                    entitiesToDo = getWorldEntities();

                    // Add new HashMap to worldIcons, if not already present. Adding without this check overwrites the HashMap.
                    if (worldIcons.get(curWorld.getName()) == null) {
                        debug("Added " + curWorld.getName() + " to worldIcons");
                        worldIcons.put(curWorld.getName(), new HashMap<Integer, Marker>());
                    }

                    if (newMap.get(curWorld.getName()) == null) {
                        debug("Added " + curWorld.getName() + " to newMap");
                        newMap.put(curWorld.getName(), new HashMap<Integer, Marker>());
                    }
                    
                    debug("newmap: " + newMap.get(curWorld.getName()).size());
                    debug("current map: " + worldIcons.get(curWorld.getName()).size());

                    debug("Removing " + worldIcons.get(curWorld.getName()).size() + " markers");
                    // Delete any markers left in worldIcons, as entities were not processed
                    for (Marker oldm : worldIcons.get(curWorld.getName()).values()) {
                        debug("Deleting " + oldm.getMarkerID() + " " + oldm.getLabel());
                        oldm.deleteMarker();
                    }

                    // Clear markers as they no longer exist
                    worldIcons.get(curWorld.getName()).clear();

                    debug("Replacing map icons");
                    // Add newMap to worldIcons
                    for (Integer key : newMap.get(curWorld.getName()).keySet()) {
                        worldIcons.get(curWorld.getName()).put(key, newMap.get(curWorld.getName()).get(key));
                    }

                    debug("Current map now: " + worldIcons.get(curWorld.getName()).size());

                    // Skip world if no entitites
                    if (entitiesToDo.isEmpty()) {
                        debug("No entities found");
                        entitiesToDo = null;
                        continue;
                    }

                    // Process Entities in world
                    getServer().getScheduler().runTaskTimer(DynmapMobsPlugin.this, task -> {
                        debug("Running loop");
                            // Process up to limit per tick
                            for(int cnt = 0; cnt < updatesPerTick; cnt++) {
                                if (entitiesToDo.isEmpty()) {
                                    debug("All entities processed");
                                    //entitiesToDo = null;
                                    task.cancel();
                                    // Run MobUpdate again until all worlds processed.
                                    getServer().getScheduler().runTaskLater(DynmapMobsPlugin.this, MobUpdate.this, 1);
                                    break;
                                }
                                // Get next entity
                                Entity le = entitiesToDo.remove(0);//entitiesToDo.get(mobIndex);
                                String mobID = mobList.getMobID(le);
                                MobData mobData = mobList.get(mobID);

                                // Skip if no data
                                if (mobData == null) {
                                    severe("No data for " + mobID);
                                    continue;
                                }

                                // get config for layer
                                mlConfig = layerConfig.get(mobData.category);

                                // Continue if Entity is disabled
                                if (!mobData.enabled) {
                                    debug("Skipping. " + mobID + " disabled");
                                    continue;
                                }

                                // Continue if Entity is passenger
                                if (le.isInsideVehicle()) {
                                    debug("Skipping. " + mobID + " is passenger");
                                    continue;
                                }
                                debug("Processing " + mobID);
                                
                                // Get location of Entity
                                Location loc = le.getLocation();
                                if(mobData.category == "vehicle" && le.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4) == false) continue;
        
                                // Skip if entity is considered hidden
                                if(isHidden(loc)) continue;
        
                                //TODO: Could be saved to a MobPosition class. Minor priority though as there isn't much of a benefit.
                                double x = Math.round(loc.getX() / res) * res;
                                double y = Math.round(loc.getY() / res) * res;
                                double z = Math.round(loc.getZ() / res) * res;
        
                                // Get label for entity
                                String label = getLabel(le, mobID, x, y, z);
                                
                                
                                // Create Marker
                                createUpdateMarker(le, label, x, y, z, mobData.icon);
                            }
                    }, 0, 1);
                    
                }
            }
            debug("End of run");
            
        }
        
        /**
         * Get List of entities in curWorld
         * @return List of entities in curWorld
         */
        public List<Entity> getWorldEntities() {
            debug("Getting entities in " + curWorld.getName());
            // Use set to get non-duplicate list of entities
            Set<Entity> set = new HashSet<Entity>();
            // List of entities which ends up returned
            List<Entity> list = new ArrayList<Entity>();

            // Add LivingEntites
            set.addAll(curWorld.getLivingEntities());
            // Remove players from list
            set.removeAll(curWorld.getPlayers());
            // Add vehicles
            set.addAll(curWorld.getEntitiesByClasses(Vehicle.class));
            
            // Add all entites to list
            list.addAll(set);
            
            debug("Got " + list.size() + " entities");
            return list;
        }

        /**
         * Get the label to use for a given entity
         * @param le The entity
         * @param mobID Internal ID of le
         * @param x X coordinate
         * @param y Y coordinate
         * @param z Z coordinate
         * @return Label for entity
         */
        public String getLabel(Entity le, String mobID, double x, double y, double z) {
            String lbl = null;

            // Labels disabled, so no need to continue
            if (mlConfig.noLabels) return "";

            switch(mobID) {
                case "villager": {
                    Villager v = (Villager)le;
                    Profession p = v.getProfession();
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
                    break;
                }
                case "skeletonhorse":
                case "zombiehorse": {
                    List<Entity> passengers = le.getPassengers();
                    if(passengers != null && !passengers.isEmpty()) {
                        Entity e = passengers.get(0);
                        if (e instanceof Player) {
                            lbl = mobList.get(mobID).label + " (" + ((Player)e).getName() + ")";
                        }
                    }
                    break;
                }
                default: {
                    //TODO: Instead of checking for Tameable and isTamed, check if mobID starts with "tamed", as getMobID takes care of this already
                    if (le instanceof Tameable) {
                        Tameable tameable = (Tameable)le;
                        if (tameable.isTamed()) {
                            AnimalTamer t = tameable.getOwner();
                            if((t != null) && (t instanceof OfflinePlayer)) {
                                lbl = mobList.get(mobID).label + " (" + ((OfflinePlayer)t).getName() + ")";
                            }
                        }
                    }
                }
            }

            // Get default label
            if(lbl == null) {
                lbl = mobList.get(mobID).label;
            }

            // Add custom name to label
            if(le.getCustomName() != null) {
                lbl = le.getCustomName() + " (" + lbl + ")";
            }

            // Add coordinates to label
            if(mlConfig.incCoord) {
                lbl = lbl + " [" + (int)x + "," + (int)y + "," + (int)z + "]";
            }

            return lbl;
        }
        /**
         * Creates or updates a marker and adds it to newmap.
         * @param ent Entity to create marker for
         * @param label Label to use for marker
         * @param x X coordinate of marker
         * @param y Y coordinate of marker
         * @param z Z coordinate of marker
         * @param icon Icon to use for marker
         */
        public void createUpdateMarker(Entity ent, String label, double x, double y, double z, MarkerIcon icon) {
            debug("Marker passed arguments: " + ent.getEntityId() + " " + label + " " + x + " " + y + " " + z + " " + icon);
            // Get existent marker. NOTE: Marker reference can exist, while Marker is deleted
            Marker m = worldIcons.get(ent.getWorld().getName()).remove(ent.getEntityId());

            // Create new marker
            if(m == null || m.getUniqueMarkerID() == null) {
                debug("Creating new marker for " + label);
                m = mlConfig.set.createMarker(mlConfig.identifier+ent.getEntityId(), label, ent.getWorld().getName(), x, y, z, icon, false);

                // createMarker() returns null if marker with given id already exists. 
                if (m == null) severe("Failed to create marker");
            }
            // Update marker
            else {
                debug("Updating existing marker for " + label);
                m.setLocation(ent.getWorld().getName(), x, y, z);
                m.setLabel(label);
                m.setMarkerIcon(icon);
            }
            // Add marker to new map
            if (m != null) {
                debug("Adding marker to new map");
                newMap.get(ent.getWorld().getName()).put(ent.getEntityId(), m);
            }
        }
    }

    /**
     * Checks for updates on Github.
     */
    private class UpdateCheck implements Runnable {
        private final String updateURL = "https://api.github.com/repos/Plastikmensch/dynmap-mobs/releases/latest";
        private final String downloadURL = "https://github.com/Plastikmensch/dynmap-mobs/releases/latest";
        // Delay between update checks in server ticks. 25h by default.
        private final long delay = 1800000L;
        // ETag used for conditional requests
        private String etag = null;
        // Cached release tag
        private String cachedRelease = null;
        
        /**
         * Compares release tag with plugin version
         */
        public void run() {
            getVersion(version -> {
                String curVersion = DynmapMobsPlugin.this.getDescription().getVersion();
                cachedRelease = version;
                int compare = compareVersions(curVersion, version);
                
                if(compare != -1) {
                    if(compare == 0) {
                        if(isDev) {
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
                            if(Integer.parseInt(current.group(i)) != Integer.parseInt(latest.group(i))) return (Integer.parseInt(current.group(i)) < Integer.parseInt(latest.group(i))) ? i : -1;
                        }
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
     * Check whether entity is considered hidden
     * @param loc Location of entity
     * @return true if hidden, otherwise false
     */
    private boolean isHidden(Location loc) {
        // Get block in location
        Block blk = loc.getBlock();
        // Get light level of block by getting max value of sky light and block light
        int light = Math.max(blk.getLightFromSky(), blk.getLightLevel());

        debug("Block is in " + blk.getWorld().getEnvironment().name());
        debug("Sky Light: " + blk.getLightFromSky());
        debug("Block Light: " + blk.getLightLevel());
        debug("Block Light (no sun): " + blk.getLightFromBlocks());
        debug("Light: " + light);

        //NOTE: Sky light is always 0 in nether and the end. Unknown behaviour in custom environment
        if((minSkyLight < 15) && blk.getLightFromSky() <= minSkyLight && blk.getWorld().getEnvironment() == Environment.NORMAL) {
            debug("Mob is underground");
            return true;
        }

        //NOTE: Block light changes based on time of day, while sky light doesn't
        if((minBlockLight < 15) && light <= minBlockLight) {
            debug("Mob is in shadow");
            return true;
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
        info("Initializing");
        PluginManager pm = getServer().getPluginManager();
        // Get dynmap
        dynmap = pm.getPlugin("dynmap");
        if(dynmap == null) {
            severe("Cannot find dynmap!");
            return;
        }
        // Get API
        api = (DynmapAPI)dynmap;

        getServer().getPluginManager().registerEvents(new OurServerListener(), this);        

        // If dynmap is enabled, activate
        if(dynmap.isEnabled())
            activate();

    }

    private static String getNMSPackage() {
        if (nmspackage == null) {
            Server srv = Bukkit.getServer();
            // Get getHandle() method
            try {
                Method m = srv.getClass().getMethod("getHandle");
                Object scm = m.invoke(srv); // And use it to get SCM (nms object)
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

    /**
     * Activate DynmapMobs
     */
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
            severe("Unable to locate CraftEntity.getHandle() - cannot process most custom mobs");
        }
        
        // Now, get dynmaps marker API
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
        // Load defaults, if needed
        cfg.options().copyDefaults(true);
        // Save updates, if needed
        this.saveConfig();

        // Toggle debugging
        debug = cfg.getBoolean("debug.all", false);

        // Check if build is dev build
        isDev = this.getDescription().getVersion().contains("-");

        if(isDev) info("You are using an unstable build. Use at your own risk");

        // Set Layer Config
        layerConfig.put("hostile", new MobLayerConfig("hostile"));
        layerConfig.put("passive", new MobLayerConfig("passive"));
        layerConfig.put("vehicle", new MobLayerConfig("vehicle"));

        // Get List of Entities
        for(EntityType type : EntityType.values()) {
            try {
                // Ignore ARMOR_STAND and PLAYER
                if (type.equals(EntityType.ARMOR_STAND) || type.equals(EntityType.PLAYER)) continue;

                String category = null;

                //NOTE: Mountable mobs count as vehicles.
                //      Zombified Piglin is part of Monster.
                //      Hoglin, Slime, MagmaCube, Ghast, EnderDragon, Shulker and Phantom are not part of Monster
                
                //TODO: beautify this
                if (Monster.class.isAssignableFrom(type.getEntityClass()) && !(type.equals(EntityType.ZOMBIFIED_PIGLIN))) category = "hostile";
                else if (type.equals(EntityType.HOGLIN) || type.equals(EntityType.SLIME) || type.equals(EntityType.MAGMA_CUBE) || type.equals(EntityType.GHAST) || type.equals(EntityType.ENDER_DRAGON) || type.equals(EntityType.SHULKER) || type.equals(EntityType.PHANTOM)) category = "hostile";
                else if (LivingEntity.class.isAssignableFrom(type.getEntityClass())) category = "passive";
                else if (Vehicle.class.isAssignableFrom(type.getEntityClass())) category = "vehicle";
                else {
                    debug("Unknown category for " + type);
                    continue;
                }

                debug("Found Entity: " + type);
                debug("class: " + type.getEntityClass().getName());
                // Infere mobid from type
                String mobID = type.toString().replace("_", "").toLowerCase();
                // Add to moblist
                mobList.put(new MobData(mobID, type.getEntityClass().getName(), category));

                debug("Created " + mobID);
                mobList.get(mobID).print();
                
            }
            // Silently ignore null exception thrown
            catch (Exception e) {}
        }

        minBlockLight = cfg.getInt("update.hideifshadow", 4);
        minSkyLight = cfg.getInt("update.hideifundercover", 15);
        updatesPerTick = cfg.getInt("update.updates-per-tick", 20);

        double per = cfg.getDouble("update.period", 5.0);
        if (per < 2.0) per = 2.0;
        updatePeriod = (long)(per*20.0);

        // Position resolution
        res = cfg.getDouble("update.resolution", 1.0);

        // Mob Update
        getServer().getScheduler().runTaskTimer(this, new MobUpdate(), updatePeriod, updatePeriod);

        // Update Check
        if(cfg.getBoolean("general.update-check", true)) {
            getServer().getScheduler().scheduleSyncDelayedTask(this, new UpdateCheck());
            info("Update check enabled");
        }
        else info("Update check disabled");

        info("Activated");
    }

    public void onDisable() {
        reset();
        stop = true;
    }

    public void reset() {
        // Delete markersets
        for (String conf : layerConfig.keySet()) {
            layerConfig.get(conf).clear();
        }
        // Dereference markers.
        worldIcons.clear();
    }

}
