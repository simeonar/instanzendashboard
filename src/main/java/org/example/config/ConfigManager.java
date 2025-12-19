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
    private final java.io.File externalConfigFile;

    public ConfigManager() throws IOException {
        properties = new Properties();

        this.externalConfigFile = resolveExternalConfigFile();

        // Load configuration (external first, then bundled defaults)
        if (externalConfigFile.exists()) {
            loadFromFile(externalConfigFile);
            logger.info("Configuration loaded from {}", externalConfigFile.getAbsolutePath());
        } else {
            loadFromClasspath();
            // Bootstrap external config on first run so that UI edits persist across restarts
            ensureParentDirectoryExists(externalConfigFile);
            saveProperties();
            logger.info("External configuration created at {}", externalConfigFile.getAbsolutePath());
        }
    }

    private java.io.File resolveExternalConfigFile() {
        // Prefer user home for persistence and permissions.
        // Windows: C:\Users\<user>\.instanzen-dashboard\application.properties
        // Linux/macOS: /home/<user>/.instanzen-dashboard/application.properties
        String userHome = System.getProperty("user.home");
        java.io.File dir = new java.io.File(userHome, ".instanzen-dashboard");
        return new java.io.File(dir, CONFIG_FILE);
    }

    private void ensureParentDirectoryExists(java.io.File file) throws IOException {
        java.io.File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs() && !parent.exists()) {
                throw new IOException("Failed to create config directory: " + parent.getAbsolutePath());
            }
        }
    }

    private void loadFromClasspath() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                throw new IOException("Unable to find " + CONFIG_FILE);
            }
            properties.clear();
            properties.load(input);
        }
    }

    private void loadFromFile(java.io.File file) throws IOException {
        try (InputStream input = new java.io.FileInputStream(file)) {
            properties.clear();
            properties.load(input);
        }
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
        String[] raw = paths.split(",");
        java.util.List<String> cleaned = new java.util.ArrayList<>();
        for (String p : raw) {
            if (p == null) continue;
            String trimmed = p.trim();
            if (trimmed.isEmpty()) continue;
            if ("[object Object]".equals(trimmed)) continue;
            if (!trimmed.startsWith("/")) trimmed = "/" + trimmed;
            cleaned.add(trimmed);
        }
        return cleaned.toArray(new String[0]);
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
        ensureParentDirectoryExists(externalConfigFile);
        try (FileOutputStream fos = new FileOutputStream(externalConfigFile)) {
            properties.store(fos, "Updated by InstanzenDashboard - " + new java.util.Date());
        }
        logger.info("Configuration saved to {}", externalConfigFile.getAbsolutePath());
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
        if (externalConfigFile.exists()) {
            loadFromFile(externalConfigFile);
            logger.info("Configuration reloaded from {}", externalConfigFile.getAbsolutePath());
            return;
        }

        // Fallback for unusual environments
        loadFromClasspath();
        logger.info("Configuration reloaded from classpath {}", CONFIG_FILE);
    }
}
