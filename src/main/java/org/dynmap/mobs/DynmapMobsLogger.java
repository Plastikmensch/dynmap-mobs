package org.dynmap.mobs;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wrapper class for logging
 */
public final class DynmapMobsLogger {
    static Logger logger;
    private final IDynmapMobs plugin;

    private final File logFile;
    private final File errorFile;

    DynmapMobsLogger(IDynmapMobs plugin) {
        this.plugin = plugin;
        DynmapMobsLogger.logger = plugin.getLogger();
        // Create log directory
        File logDir = new File(plugin.getDataFolder(), "logs");
        if (!logDir.exists()) logDir.mkdir();
        // Define files
        logFile = new File(logDir.getPath(), "debug.log");
        errorFile = new File(logDir.getPath(), "error.log");
        // Delete old log files
        if (logFile.exists()) logFile.delete();
        if (errorFile.exists()) errorFile.delete();
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
        logToFile(errorFile, msg);
    }

    //TODO: Create categories for debug logs
    /**
     * Log info message if debug enabled
     * @param msg Message to log
     */
    public void debug(String msg) {
        if (plugin.getPluginConfig().debug) {
            info(msg);
            logToFile(logFile, msg);
        }
    }

    /**
     * Log message to file
     * @param file File to log to
     * @param msg Message to log
     */
    public void logToFile(File file, String msg) {
        try {
            if (!file.exists()) file.createNewFile();
            PrintWriter pw = new PrintWriter(new FileWriter(file, true));
            LocalTime.now();
            String timestamp = "[" + DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalTime.now()) + "]: ";
            pw.println(timestamp + msg);
            pw.flush();
            pw.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}
