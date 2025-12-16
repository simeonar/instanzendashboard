package org.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Configuration manager for loading application settings
 */
public class ConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
    private static final String CONFIG_FILE = "application.properties";
    private final Properties properties;
    private final String configFilePath;

    public ConfigManager() throws IOException {
        properties = new Properties();
        
        // Try to load from resources (bundled in JAR)
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                throw new IOException("Unable to find " + CONFIG_FILE);
            }
            properties.load(input);
        }
        
        // Determine config file path for saving
        String jarPath = ConfigManager.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        java.io.File jarFile = new java.io.File(jarPath);
        configFilePath = jarFile.getParent() + java.io.File.separator + CONFIG_FILE;
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public int getIntProperty(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public String getIpRangeStart() {
        return getProperty("network.ip.range.start", "10.0.0.1");
    }

    public String getIpRangeEnd() {
        return getProperty("network.ip.range.end", "10.0.0.254");
    }

    public int getNetworkPort() {
        return getIntProperty("network.port", 80);
    }

    public int getScanIntervalSeconds() {
        return getIntProperty("scan.interval.seconds", 60);
    }

    public int getConnectionTimeoutMs() {
        return getIntProperty("connection.timeout.ms", 2000);
    }

    public String getHealthCheckEndpoint() {
        return getProperty("health.check.endpoint", "/health");
    }

    public int getDashboardRefreshSeconds() {
        return getIntProperty("dashboard.refresh.seconds", 30);
    }

    public String getDashboardTitle() {
        return getProperty("dashboard.title", "Instance Dashboard");
    }

    // Health check settings
    public boolean isHealthCheckEnabled() {
        return Boolean.parseBoolean(getProperty("health.check.enabled", "true"));
    }

    public String getHealthCheckPath() {
        return getProperty("health.check.path", "/api/health");
    }

    public int getHealthCheckTimeoutMs() {
        return getIntProperty("health.check.timeout.ms", 3000);
    }

    public int getHealthCheckExpectedStatus() {
        return getIntProperty("health.check.expected.status", 200);
    }

    // Metadata settings
    public boolean isMetadataEnabled() {
        return Boolean.parseBoolean(getProperty("metadata.enabled", "true"));
    }

    public String getMetadataBranchField() {
        return getProperty("metadata.branch.field", "branch");
    }

    public String getMetadataVersionField() {
        return getProperty("metadata.version.field", "version");
    }

    public String getMetadataCommitField() {
        return getProperty("metadata.commit.field", "commit");
    }

    public String getMetadataTimestampField() {
        return getProperty("metadata.timestamp.field", "deployedAt");
    }

    public String getMetadataStatusField() {
        return getProperty("metadata.status.field", "status");
    }

    // Check paths
    public String[] getCheckPaths() {
        String paths = getProperty("check.paths", "/,/index.html,/health");
        return paths.split(",");
    }

    // Dashboard settings
    public boolean isDashboardShowMetadata() {
        return Boolean.parseBoolean(getProperty("dashboard.show.metadata", "true"));
    }

    public boolean isDashboardFilterUnreachable() {
        return Boolean.parseBoolean(getProperty("dashboard.filter.unreachable", "false"));
    }

    // Web dashboard settings
    public boolean isWebDashboardEnabled() {
        return Boolean.parseBoolean(getProperty("web.dashboard.enabled", "true"));
    }

    public int getWebDashboardPort() {
        return getIntProperty("web.dashboard.port", 8081);
    }

    public int getWebDashboardRefreshSeconds() {
        return getIntProperty("web.dashboard.refresh.seconds", 300);
    }

    /**
     * Save configuration property
     */
    public synchronized void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }

    /**
     * Save all properties to file
     */
    public synchronized void saveProperties() throws IOException {
        try (FileOutputStream fos = new FileOutputStream(configFilePath)) {
            properties.store(fos, "Updated by InstanzenDashboard - " + new java.util.Date());
        }
        logger.info("Configuration saved to {}", configFilePath);
    }

    /**
     * Get all properties as map
     */
    public Map<String, String> getAllProperties() {
        Map<String, String> map = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            map.put(key, properties.getProperty(key));
        }
        return map;
    }

    /**
     * Reload properties from file
     */
    public synchronized void reloadProperties() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                throw new IOException("Unable to find " + CONFIG_FILE);
            }
            properties.clear();
            properties.load(input);
            logger.info("Configuration reloaded from {}", CONFIG_FILE);
        }
    }
}
