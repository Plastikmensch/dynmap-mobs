package org.dynmap.mobs;

import org.bukkit.plugin.Plugin;
import org.dynmap.markers.MarkerAPI;

/**
 * Plugin interface to access variables from main class
 */
public interface IDynmapMobs extends Plugin {
    /**
     * Get Dynmaps marker API
     * @return MarkerAPI
     */
    MarkerAPI getMarkerAPI();

    /**
     * Get configuration
     * @return Plugin configuration
     */
    DynmapMobsConfig getPluginConfig();

    /**
     * Get logger
     * @return Logger
     */
    DynmapMobsLogger getPluginLogger();

    /**
     * Delete marker sets and marker references
     */
    void reset();

    /**
     * Activate this plugin
     */
    void activate();
}
