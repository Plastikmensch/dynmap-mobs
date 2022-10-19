package org.dynmap.mobs;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Check for updates on GitHub
 */
public class DynmapMobsUpdateCheck implements Runnable {
    private final IDynmapMobs plugin;
    private final DynmapMobsLogger logger;
    private final String updateURL = "https://api.github.com/repos/Plastikmensch/dynmap-mobs/releases/latest";
    private final String downloadURL = "https://github.com/Plastikmensch/dynmap-mobs/releases/latest";
    // Delay between update checks in server ticks. 25h by default.
    private final long delay = 1800000L;
    // ETag used for conditional requests
    private String etag;
    // Cached release tag
    private String cachedRelease;

    DynmapMobsUpdateCheck(IDynmapMobs plugin) {
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();
    }

    /**
     * Compares release tag with plugin version
     */
    public void run() {
        logger.debug("Checking for update...");
        getVersion(version -> {
            String curVersion = plugin.getDescription().getVersion();
            cachedRelease = version;
            int compare = compareVersions(curVersion, version);

            if (compare != -1) {
                if (compare == 0) {
                    if (plugin.getPluginConfig().isDev) {
                        logger.info("There is a stable release of " + curVersion + " available");
                        logger.info("Get it at " + downloadURL);
                    }
                }
                else {
                    logger.info("Version " + version + " is available. You are running " + curVersion);
                    logger.info("Get it at " + downloadURL);
                }
            }
        });
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, this, delay);
    }

    /**
     * Parses the body of the GET request and looks for the release tag
     * @param consumer Resolves to release tag
     */
    public void getVersion(final Consumer<String> consumer) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            // Try getting response body
            try (InputStream inputStream = tryConnect().getInputStream(); Scanner scanner = new Scanner(inputStream)) {
                // split input stream at ","s
                scanner.useDelimiter(",");
                // iterate over elements
                while (scanner.hasNext()) {
                    String key = scanner.next();
                    if (key.contains("tag_name")) {
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
            }
            catch (Exception e) {
                logger.severe("Unable to check for updates: " + e.getMessage());
            }
        });
    }

    /**
     * Connect to the GitHub api
     * @return The connection to the GitHub api
     * @throws IOException if an I/O exception occurs
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
        if (!version.equals(newVersion)) {
            try {
                Pattern semver = Pattern.compile("^v?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$");
                Matcher current = semver.matcher(version);
                Matcher latest = semver.matcher(newVersion);

                if (current.find() && latest.find()) {
                    for (int i=1; i<=3; i++) {
                        if (Integer.parseInt(current.group(i)) != Integer.parseInt(latest.group(i))) return (Integer.parseInt(current.group(i)) < Integer.parseInt(latest.group(i))) ? i : -1;
                    }
                }
                else throw new IllegalArgumentException("Invalid semver");
            }
            catch (Exception e) {
                logger.severe("Can't compare versions: " + e.getMessage());
                return -1;
            }
        }
        return 0;
    }
}
