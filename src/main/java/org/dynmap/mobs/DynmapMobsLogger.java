package org.dynmap.mobs;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wrapper class for logging
 */
public final class DynmapMobsLogger {
    static Logger logger;
    private final IDynmapMobs plugin;

    DynmapMobsLogger(IDynmapMobs plugin) {
        this.plugin = plugin;
        DynmapMobsLogger.logger = plugin.getLogger();
    }

    /**
     * Log info message to console
     * @param msg Message to log
     */
    public void info(String msg) {
        logger.log(Level.INFO, msg);
    }

    /**
     * Log error message to console
     * @param msg Message to log
     */
    public void severe(String msg) {
        logger.log(Level.SEVERE, msg);
    }

    //TODO: Create categories for debug logs
    /**
     * Log info message if debug enabled
     * @param msg Message to log
     */
    public void debug(String msg) {
        if(plugin.getPluginConfig().debug) info(msg);
    }
}
