package org.dynmap.mobs;

import org.bukkit.configuration.file.FileConfiguration;
import org.dynmap.markers.MarkerSet;

import java.util.HashMap;

/**
 * Plugin config
 */
public class DynmapMobsConfig {
    final IDynmapMobs plugin;
    final DynmapMobsLogger logger;
    final FileConfiguration fileConfiguration;
    double positionResolution;
    byte minSkyLight;
    byte minLight;
    int updatesPerTick;
    long updatePeriod;
    boolean updateCheck;
    boolean isDev;
    boolean debug;
    boolean reload;
    boolean enabled = true;
    // Map containing config for layers
    HashMap<MobCategory, MobLayerConfig> layer = new HashMap<MobCategory, MobLayerConfig>();

    DynmapMobsConfig(IDynmapMobs plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        this.fileConfiguration = plugin.getConfig();
        this.logger = plugin.getPluginLogger();
    }

    /**
     * Layer config
     */
    public class MobLayerConfig {
        MarkerSet set;
        boolean tinyIcons;
        boolean noLabels;
        boolean incCoord;
        final String identifier;

        MobLayerConfig(MobCategory category) {
            this(category.asString());
        }

        MobLayerConfig(String category) {
            String layer = category + "layer.";
            identifier = category;
            tinyIcons = fileConfiguration.getBoolean(layer + "tinyicons", false);
            noLabels = fileConfiguration.getBoolean(layer + "nolabels", false);
            incCoord = fileConfiguration.getBoolean(layer + "inc-coord", false);
            set = plugin.getMarkerAPI().getMarkerSet(layer + "markerset");

            // Set default label for layer
            String defaultLabel;
            if (category.equals(MobCategory.VEHICLE.asString())) {
                defaultLabel = "Vehicles";
            }
            else {
                defaultLabel = category.substring(0, 1).toUpperCase() + category.substring(1) + " Mobs";
            }

            if (set == null) {
                set = plugin.getMarkerAPI().createMarkerSet(layer + "markerset", fileConfiguration.getString(layer + "name", defaultLabel), null, false);
            }
            else {
                set.setMarkerSetLabel(fileConfiguration.getString(layer + "name", defaultLabel));
            }

            if (set == null) {
                logger.severe("Error creating marker set " + defaultLabel);
                return;
            }

            set.setLayerPriority(fileConfiguration.getInt(layer + "layerprio", 10));
            set.setHideByDefault(fileConfiguration.getBoolean(layer + "hidebydefault", false));
            set.setMinZoom(fileConfiguration.getInt(layer + "minzoom", 0));
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

    /**
     * Read and set config
     */
    public void read() {
        if (reload) {
            plugin.reloadConfig();
            plugin.reset();
        }
        reload = true;

        // Load defaults if needed
        fileConfiguration.options().copyDefaults(true);

        // Save updates if needed
        plugin.saveConfig();

        // Toggle debug
        debug = fileConfiguration.getBoolean("debug.all", false);

        // Check if build is dev build
        isDev = plugin.getDescription().getVersion().contains("-");
        
        // Set layer config
        layer.put(MobCategory.HOSTILE, new MobLayerConfig(MobCategory.HOSTILE));
        layer.put(MobCategory.PASSIVE, new MobLayerConfig(MobCategory.PASSIVE));
        layer.put(MobCategory.VEHICLE, new MobLayerConfig(MobCategory.VEHICLE));

        // Entity hiding
        minLight = (byte)fileConfiguration.getInt("update.hideifshadow", 4);
        minSkyLight = (byte)fileConfiguration.getInt("update.hideifundercover", 15);

        // MobUpdate config
        updatesPerTick = fileConfiguration.getInt("update.updates-per-tick", 20);
        double per = fileConfiguration.getDouble("update.period", 5.0);
        if (per < 1.0) per = 1.0;
        updatePeriod = (long)(per*20.0);

        // Position resolution
        positionResolution = fileConfiguration.getDouble("update.resolution", 1.0);

        // Update Check
        updateCheck = fileConfiguration.getBoolean("general.update-check", true);
    }
}
