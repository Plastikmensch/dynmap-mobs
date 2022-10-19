package org.dynmap.mobs;

import org.bukkit.*;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.event.*;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.*;
import org.dynmap.markers.Marker;
import org.dynmap.mobs.DynmapMobsConfig.MobLayerConfig;

import java.io.*;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Main class of Dynmap Mobs
 */
public class DynmapMobsPlugin extends JavaPlugin implements IDynmapMobs {
    DynmapMobsLogger logger;
    Plugin dynmap;
    DynmapAPI api;
    MarkerAPI markerapi;
    DynmapMobsConfig config;
    static String obcpackage;
    static String nmspackage;
    Method gethandle;
    // List containing all information about mobs
    MobList mobList = new MobList();
    // Map containing Map of map icons per world
    Map<String, Map<Integer, Marker>> worldIcons= new HashMap<String, Map<Integer, Marker>>();
    
    @Override
    public void onLoad() {
        logger = new DynmapMobsLogger(this);
        config = new DynmapMobsConfig(this);
    }

    @Override
    public MarkerAPI getMarkerAPI() {
        return markerapi;
    }

    @Override
    public DynmapMobsConfig getPluginConfig() {
        return config;
    }

    @Override
    public DynmapMobsLogger getPluginLogger() {
        return logger;
    }
    
    public static String mapClassName(String n) {
        if (n.startsWith("org.bukkit.craftbukkit")) {
            n = getOBCPackage() + n.substring("org.bukkit.craftbukkit".length());
        }
        else if (n.startsWith("net.minecraft.server")) {
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
            if (get(data.mobID) == null) moblist.add(data);
        }

        /**
         * Get MobData for id
         * @param id MobID
         * @return MobData for id or null if not found
         */
        MobData get(String id) {
            for (MobData data : moblist) {
                if (data.mobID.equals(id)) return data;
            }
            return null;
        }

        /**
         * Get a list of mobs belonging to category
         * @param category Category
         * @return List of entities belonging to category
         */
        private List<MobData> getSubList(MobCategory category) {
            List<MobData> subList = new ArrayList<MobData>();
            for (MobData data : moblist) {
                if (data.mobCategory.equals(category)) subList.add(data);
            }
            return subList;
        }

        /**
         * Get List of entities of category vehicle
         * @return List containing all vehicles
         */
        List<MobData> getVehicles() {
            return getSubList(MobCategory.VEHICLE);
        }

        /**
         * Get List of entities of category hostile
         * @return List containing all hostile mobs
         */
        List<MobData> getHostiles() {
            return getSubList(MobCategory.HOSTILE);
        }

        /**
         * Get List of entities of category passive
         * @return List containing all passive mobs
         */
        List<MobData> getPassives() {
            return getSubList(MobCategory.PASSIVE);
        }

        /**
         * Returns the mobID for given entity
         * @param ent Entity
         * @return mobID
         */
        String getMobID(Entity ent) {
            for (MobData entData: moblist) {
                String key = entData.mobID;
                if (entData.mobClass == null) continue;
                if (entData.mobClass.isInstance(ent)) {
                    switch (key) {
                        case "spider": {
                            List<Entity> passengers = ent.getPassengers();
                            if (!passengers.isEmpty() && passengers.get(0) instanceof Skeleton) {
                                key = "spiderjockey";

                                // Create data for spiderjockey
                                if (get(key) == null) {
                                    logger.debug("creating " + key);
                                    put(new MobData(key, null, entData.mobCategory, "Spider Jockey"));
                                }
                            }
                            break;
                        }
                        case "chicken": {
                            List<Entity> passengers = ent.getPassengers();
                            if (!passengers.isEmpty()) {
                                //TODO: Differentiate between jockeys
                                key = "chickenjockey";

                                // Create data for chickenjockey
                                if (get(key) == null) {
                                    logger.debug("creating " + key);

                                    // Get category from passenger
                                    //MobCategory category = get(getMobID(passengers.get(0))).mobCategory;
                                    //debug("Category of passenger: " + category.asString());

                                    // Default to hostile for now, since category is depending on the first chicken jockey found
                                    //TODO: replace after differentiating jockeys
                                    put(new MobData(key, null, MobCategory.HOSTILE, "Chicken Jockey"));
                                }
                            }
                            break;
                        }
                        // Traderllama is subclass of llama
                        case "llama": {
                            MobData subData = get("traderllama");
                            if (subData != null && subData.mobClass.isInstance(ent)) {
                                key = "traderllama";
                            }
                            break;
                        }
                        // PigZombie is subclass of zombie
                        case "zombie": {
                            MobData subData = get("zombifiedpiglin");
                            if (subData != null && subData.mobClass.isInstance(ent)) {
                                key = "zombifiedpiglin";
                            }
                            break;
                        }
                        // MagmaCube is subclass of slime
                        case "slime": {
                            MobData subData = get("magmacube");
                            if (subData != null && subData.mobClass.isInstance(ent)) {
                                key = "magmacube";
                            }
                            break;
                        }
                        case "boat": {
                            MobData subData = get("chestboat");
                            if (subData != null && subData.mobClass.isInstance(ent)) {
                                key = "chestboat";
                            }
                            break;
                        }
                        default: {
                            if (ent instanceof Tameable && ((Tameable)ent).isTamed()) {
                                key = "tamed" + key;

                                // Create data for tamed
                                if (get(key) == null) {
                                    logger.debug("creating " + key);
                                    put(new MobData(key, null, entData.mobCategory, entData.label));
                                }
                            }
                        }
                    }
                    return key;
                }
            }
            logger.severe("No id for " + ent.getClass().getName());
            return "";
        }
    }

    private class MobData {
        // Only used internally
        String cls;
        String category;
        // Used for logic
        String mobID;
        Class<Entity> mobClass;
        String label;
        MobCategory mobCategory;
        boolean enabled;
        MarkerIcon icon;

        MobData(String mobID, String cls, MobCategory category) {
            this(mobID, cls, category, null);
        }

        MobData(String mobID, String cls, MobCategory category, String label) {
            this.mobID = mobID;
            this.cls = cls;
            this.mobCategory = category;
            this.category = category.asString();
            if (label == null) label = getLabelFromClass(cls);
            this.label = label;
            this.mobClass = getMobClass();
            this.icon = loadIcon();
            this.enabled = config.fileConfiguration.getBoolean(this.category + "." + mobID, true);
            if (!config.fileConfiguration.contains(this.category + "." + mobID, true)) logger.severe("Missing ID in config: " + mobID);
        }

        /**
         * Get default label inferred from name of class
         * @param cls Class name
         * @return Label to use for this entity
         */
        private String getLabelFromClass(String cls) {
            StringBuilder label = new StringBuilder();
            // Remove everything before last dot and convert name of class to Title Case
            for (String w : cls.replaceAll("(.+\\.)", "").split("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")) {
                label.append(w).append(" ");
            }
            return label.toString().trim();
        }

        /**
         * Get class of entity
         * @return Entity class or null
         */
        @SuppressWarnings("unchecked")
        private Class<Entity> getMobClass() {
            try {
                return (Class<Entity>) Class.forName(mapClassName(cls));
            }
            catch (ClassNotFoundException cnfx) {
                logger.severe("Not Found: " + cnfx.getMessage());
                return null;
            }
            // mapClassName throws NPE if cls is null
            catch (NullPointerException e) {
                return null;
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
            InputStream in;
            if (config.layer.get(mobCategory).tinyIcons) {
                logger.debug("Getting tinyicon for " + mobID);
                in = getClass().getResourceAsStream("/8x8/" + mobID + ".png");
            }
            else {
                logger.debug("Getting icon for " + mobID);
                in = getClass().getResourceAsStream("/" + mobID + ".png");
            }

            if (in != null) {
                // Create icon
                if (icon == null) {
                    logger.debug("Creating icon for " + mobID);
                    icon = markerapi.createMarkerIcon(category + "." + mobID, label, in);
                }
                // Update icon image
                else {
                    logger.debug("Updating icon for " + mobID);
                    icon.setMarkerIconImage(in);
                }
            }
            else {
                logger.severe("No resource for " + mobID);
            }
            
            // Fallback to default icon if still null
            if (icon == null) {
                logger.severe("No icon found for " + mobID);
                icon = markerapi.getMarkerIcon(MarkerIcon.DEFAULT);
            }
            
            return icon;
        }

        // Temporary function to log values for logger.debugging
        private void print() {
            logger.debug("mobID: " + mobID + " cls: " + cls + ", mobclass: " + mobClass + ", label: " + label + ", category: " + category + ", enabled: " + enabled + ", icon: " + icon);
        }
    }

    private class MobUpdate implements Runnable {
        private final Map<World, BukkitTask> tasks = new HashMap<World, BukkitTask>();

        @Override
        public void run() {
            logger.debug("Getting worlds");
            ArrayList<World> worldsTodo = new ArrayList<World>(getServer().getWorlds());
            for (World world : worldsTodo) {
                //TODO: Check if world enabled (#38)
                // Add new HashMap to worldIcons, if not already present. Adding without this check overwrites the HashMap.
                if (worldIcons.get(world.getName()) == null) {
                    logger.debug("Added " + world.getName() + " to worldIcons");
                    worldIcons.put(world.getName(), new HashMap<Integer, Marker>());
                }

                if (!isStillRunning(tasks.get(world))) {
                    logger.debug("Scheduling update for " + world.getName());
                    tasks.put(world, getServer().getScheduler().runTask(DynmapMobsPlugin.this, new MobWorldUpdate(world)));
                    logger.debug("Created Task: " + tasks.get(world).getTaskId());
                }
                else logger.severe("Update for " + world.getName() + " still running. Increase updatePeriod!");
            }
        }

        private boolean isStillRunning(BukkitTask task) {
            return (task != null && (getServer().getScheduler().isCurrentlyRunning(task.getTaskId()) || getServer().getScheduler().isQueued(task.getTaskId())));
        }

        //TODO: Rethink if functions have to be attached to class
        private class MobWorldUpdate implements Runnable {
            // Marker reference to processed entities
            Map<Integer, Marker> updatedMap = new HashMap<Integer, Marker>();
            List<Entity> entitiesToDo;
            World curWorld;
            MobLayerConfig mlConfig;

            MobWorldUpdate(World world) {
                this.curWorld = world;
                this.entitiesToDo = getWorldEntities();
            }

            public void run() {
                logger.debug("Starting update on " + curWorld.getName());
                if (entitiesToDo.isEmpty()) {
                    logger.debug("All entities processed in " + curWorld.getName());
                    // Cleanup markers
                    logger.debug("Processed entities: " + updatedMap.size());
                    logger.debug("Markers to remove: " + worldIcons.get(curWorld.getName()).size());
                    worldIcons.get(curWorld.getName()).forEach((id, oldMarker) -> {
                        logger.debug("Deleting marker " + oldMarker.getMarkerID() + " for entity " + id + " ("+ oldMarker.getLabel() + ")");
                        oldMarker.deleteMarker();
                    });
                    // Clear worldIcons for world
                    worldIcons.get(curWorld.getName()).clear();

                    // Add updatedMap to worldIcons
                    for (Integer key : updatedMap.keySet()) {
                        worldIcons.get(curWorld.getName()).put(key, updatedMap.get(key));
                    }
                    return;
                }

                // Process entities
                for (int cnt = 0; cnt <= config.updatesPerTick; cnt++) {
                    // All entities processed
                    if (entitiesToDo.isEmpty()) {
                        break;
                    }

                    // Get next entity
                    Entity ent = entitiesToDo.remove(0);
                    MobData mobData = mobList.get(mobList.getMobID(ent));

                    if (mobData == null) {
                        logger.severe("No data for " + ent.getClass().getName());
                        continue;
                    }

                    if (!mobData.enabled) {
                        logger.debug("Skipping: disabled entity " + mobData.mobID);
                        continue;
                    }

                    if (ent.isInsideVehicle()) {
                        logger.debug("Skipping: entity is passenger");
                        continue;
                    }

                    logger.debug("Processing: " + ent.getEntityId() + " " + mobData.mobID);

                    // Get location of Entity
                    Location loc = ent.getLocation();
                    if (mobData.mobCategory.equals(MobCategory.VEHICLE) && !ent.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;

                    // Skip if entity is considered hidden
                    if (isHidden(loc)) continue;

                    //TODO: Could be saved to a MobPosition class. Minor priority though as there isn't much of a benefit.
                    double x = Math.round(loc.getX() / config.positionResolution) * config.positionResolution;
                    double y = Math.round(loc.getY() / config.positionResolution) * config.positionResolution;
                    double z = Math.round(loc.getZ() / config.positionResolution) * config.positionResolution;

                    // Get layer config
                    mlConfig = config.layer.get(mobData.mobCategory);

                    // Get label for entity
                    String label = getLabel(ent, mobData, x, y, z);

                    // Create Marker
                    createUpdateMarker(ent, label, x, y, z, mobData.icon);

                }
                // Reschedule this run
                tasks.put(curWorld, getServer().getScheduler().runTaskLater(DynmapMobsPlugin.this, this, 1));
            }

            /**
             * Get List of entities in curWorld
             * @return List of entities in curWorld
             */
            public List<Entity> getWorldEntities() {
                logger.debug("Getting entities in " + curWorld.getName());
                // Use set to get non-duplicate list of entities
                Set<Entity> set = new HashSet<Entity>(curWorld.getLivingEntities());
                // Remove players from set
                curWorld.getPlayers().forEach(set::remove);
                // Remove Armor Stand from set
                set.removeIf(ent -> ent.getType() == EntityType.ARMOR_STAND);
                // Add vehicles
                set.addAll(curWorld.getEntitiesByClasses(Vehicle.class));

                logger.debug("Got " + set.size() + " entities");

                // Convert set to list and return it
                return new ArrayList<Entity>(set);
            }

            /**
             * Get the label to use for a given entity
             * @param le The entity
             * @param mobData MobData associated with le
             * @param x X coordinate
             * @param y Y coordinate
             * @param z Z coordinate
             * @return Label for entity
             */
            public String getLabel(Entity le, MobData mobData, double x, double y, double z) {
                String lbl = null;

                // Labels disabled, so no need to continue
                if (mlConfig.noLabels) return "";

                switch (mobData.mobID) {
                    case "villager": {
                        Villager v = (Villager)le;
                        Profession p = v.getProfession();
                        switch (p) {
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
                            default:
                                logger.severe("Missing Villager profession: " + p);
                                lbl = "Unknown";
                        }
                        break;
                    }
                    case "skeletonhorse":
                    case "zombiehorse": {
                        List<Entity> passengers = le.getPassengers();
                        if (!passengers.isEmpty()) {
                            Entity e = passengers.get(0);
                            if (e instanceof Player) {
                                lbl = mobData.label + " (" + ((Player)e).getName() + ")";
                            }
                        }
                        break;
                    }
                    default: {
                        if (mobData.mobID.startsWith("tamed")) {
                            AnimalTamer t = ((Tameable)le).getOwner();
                            if (t instanceof OfflinePlayer) {
                                lbl = mobData.label + " (" + ((OfflinePlayer)t).getName() + ")";
                            }
                        }
                    }
                }

                // Get default label
                if (lbl == null) {
                    lbl = mobData.label;
                }

                // Add custom name to label
                if (le.getCustomName() != null) {
                    lbl = le.getCustomName() + " (" + lbl + ")";
                }

                // Add coordinates to label
                if (mlConfig.incCoord) {
                    lbl = lbl + " [" + (int)x + "," + (int)y + "," + (int)z + "]";
                }

                return lbl;
            }

            /**
             * Creates or updates a marker and adds it to updatedMap.
             * @param ent Entity to create marker for
             * @param label Label to use for marker
             * @param x X coordinate of marker
             * @param y Y coordinate of marker
             * @param z Z coordinate of marker
             * @param icon Icon to use for marker
             */
            public void createUpdateMarker(Entity ent, String label, double x, double y, double z, MarkerIcon icon) {
                logger.debug("Marker passed arguments: " + ent.getEntityId() + " " + label + " " + x + " " + y + " " + z + " " + icon.getMarkerIconID());
                String world = ent.getWorld().getName();
                logger.debug("Entity is in " + world);
                // Get existent marker. NOTE: Marker reference can exist, while Marker is deleted
                Marker m = worldIcons.get(world).remove(ent.getEntityId());

                // Create new marker
                if (m == null || m.getUniqueMarkerID() == null) {
                    logger.debug("Creating new marker for " + label);
                    m = mlConfig.set.createMarker(mlConfig.identifier+ent.getEntityId(), label, world, x, y, z, icon, false);

                    // createMarker() returns null if marker with given id already exists.
                    if (m == null) logger.severe("Failed to create marker");
                }
                // Update marker
                else {
                    logger.debug("Updating existing marker for " + label);
                    m.setLocation(world, x, y, z);
                    m.setLabel(label);
                    m.setMarkerIcon(icon);
                }

                // Add marker to new map
                if (m != null) {
                    logger.debug("Adding marker to updated map");
                    updatedMap.put(ent.getEntityId(), m);
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

                logger.debug("Block is in " + blk.getWorld().getEnvironment().name());
                logger.debug("Sky Light: " + blk.getLightFromSky());
                logger.debug("Block Light: " + blk.getLightLevel());
                logger.debug("Block Light (no sun): " + blk.getLightFromBlocks());
                logger.debug("Light: " + light);

                //NOTE: Sky light is always 0 in nether and the end. Unknown behaviour in custom environment
                if ((config.minSkyLight < 15) && blk.getLightFromSky() <= config.minSkyLight && blk.getWorld().getEnvironment() == Environment.NORMAL) {
                    logger.debug("Mob is underground");
                    return true;
                }

                //NOTE: Block light changes based on time of day, while sky light doesn't
                if ((config.minLight < 15) && light <= config.minLight) {
                    logger.debug("Mob is in shadow");
                    return true;
                }
                return false;
            }
        }
    }

    private class OurServerListener implements Listener {
        @EventHandler(priority=EventPriority.MONITOR)
        public void onPluginEnable(PluginEnableEvent event) {
            Plugin p = event.getPlugin();
            String name = p.getDescription().getName();
            if (name.equals("dynmap")) {
                activate();
            }
        }
    }
    
    public void onEnable() {
        logger.info("Initializing");
        PluginManager pm = getServer().getPluginManager();
        // Get dynmap
        dynmap = pm.getPlugin("dynmap");
        if (dynmap == null) {
            logger.severe("Cannot find dynmap!");
            return;
        }
        // Get API
        api = (DynmapAPI)dynmap;

        getServer().getPluginManager().registerEvents(new OurServerListener(), this);

        // If dynmap is enabled, activate
        if (dynmap.isEnabled()) activate();
    }

    private static String getNMSPackage() {
        if (nmspackage == null) {
            Server srv = Bukkit.getServer();
            // Get getHandle() method
            try {
                Method m = srv.getClass().getMethod("getHandle");
                // And use it to get SCM (nms object)
                Object scm = m.invoke(srv);
                nmspackage = scm.getClass().getPackage().getName();
            }
            catch (Exception x) {
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
    public void activate() {
        // Look up the getHandle method for CraftEntity
        try {
            Class<?> cls = Class.forName(mapClassName("org.bukkit.craftbukkit.entity.CraftEntity"));
            gethandle = cls.getMethod("getHandle");
        }
        catch (ClassNotFoundException | SecurityException | NoSuchMethodException e) {
            logger.severe("Unable to locate CraftEntity.getHandle() - cannot process most custom mobs");
        }
        
        // Now, get dynmaps marker API
        markerapi = api.getMarkerAPI();
        if (markerapi == null) {
            logger.severe("Error loading Dynmap marker API!");
            return;
        }

        config.read();

        if (config.isDev) logger.info("You are using an unstable build. Use at your own risk");

        // Get List of Entities
        for (EntityType type : EntityType.values()) {
            MobCategory category;

            /*NOTE: Mountable entities are assignable to Vehicle
             *      Zombified Piglin is assignable to Monster despite counting as passive
             *      Hoglin, Slime, MagmaCube, Ghast, EnderDragon, Shulker and Phantom are not assignable to Monster
             *      Warnings about "might be null" or "may produce NPE" can be safely ignored, as only UNKNOWN has no attached class
             */
            switch (type) {
                // Assignable entities to ignore
                case ARMOR_STAND:
                case PLAYER:
                case UNKNOWN: {
                    continue;
                }
                // Hostile entities which aren't assignable to Monster
                case HOGLIN:
                case SLIME:
                case MAGMA_CUBE:
                case GHAST:
                case ENDER_DRAGON:
                case SHULKER:
                case PHANTOM: {
                    category = MobCategory.HOSTILE;
                    break;
                }
                // Passive entities assignable to Monster
                case ZOMBIFIED_PIGLIN: {
                    category = MobCategory.PASSIVE;
                    break;
                }
                // Determine which category an entity belongs to by checking to which class it's assignable
                default: {
                    if (Monster.class.isAssignableFrom(type.getEntityClass())) category = MobCategory.HOSTILE;
                    else if (LivingEntity.class.isAssignableFrom(type.getEntityClass())) category = MobCategory.PASSIVE;
                    else if (Vehicle.class.isAssignableFrom(type.getEntityClass())) category = MobCategory.VEHICLE;
                    else {
                        logger.debug("Unknown category for " + type);
                        continue;
                    }
                }
            }

            logger.debug("Found entity: " + type);
            logger.debug("Class: " + type.getEntityClass().getName());

            // Infer mobid from type
            String mobID = type.toString().replace("_", "").toLowerCase();

            // Legacy: Zombified_Piglin used to be Pig_Zombie.
            if (mobID.equals("pigzombie")) {
                mobID = "zombifiedpiglin";
                category = MobCategory.PASSIVE;
            }

            // Add to moblist
            mobList.put(new MobData(mobID, type.getEntityClass().getName(), category));

            logger.debug("Created " + mobID);
            mobList.get(mobID).print();
        }

        logger.debug("Found " + mobList.getHostiles().size() + " hostile mobs, " + mobList.getPassives().size() + " passive mobs and " + mobList.getVehicles().size() + " vehicles");

        // Mob Update
        getServer().getScheduler().runTaskTimer(this, new MobUpdate(), config.updatePeriod, config.updatePeriod);

        // Update Check
        if (config.updateCheck) {
            getServer().getScheduler().runTask(this, new DynmapMobsUpdateCheck(this));
            logger.info("Update check enabled");
        }
        else logger.info("Update check disabled");

        logger.info("Activated");
    }

    public void onDisable() {
        reset();
    }

    @Override
    public void reset() {
        // cancel any pending tasks
        getServer().getScheduler().getPendingTasks().forEach((task) -> {
            if (task.getOwner() == DynmapMobsPlugin.this) task.cancel();
        });

        // Delete markersets
        for (MobCategory conf : config.layer.keySet()) {
            config.layer.get(conf).clear();
        }
        // Dereference markers.
        worldIcons.clear();
    }

}
