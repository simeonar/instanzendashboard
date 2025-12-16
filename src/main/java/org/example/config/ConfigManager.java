package org.example.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration manager for loading application settings
 */
public class ConfigManager {
    private static final String CONFIG_FILE = "application.properties";
    private final Properties properties;

    public ConfigManager() throws IOException {
        properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                throw new IOException("Unable to find " + CONFIG_FILE);
            }
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
        return paths.split(",");
    }

    // Dashboard settings
    public boolean isDashboardShowMetadata() {
        return Boolean.parseBoolean(getProperty("dashboard.show.metadata", "true"));
    }

    public boolean isDashboardFilterUnreachable() {
        return Boolean.parseBoolean(getProperty("dashboard.filter.unreachable", "false"));
    }
}
