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
        return getProperty("network.ip.range.start", "192.168.1.1");
    }

    public String getIpRangeEnd() {
        return getProperty("network.ip.range.end", "192.168.1.254");
    }

    public int getNetworkPort() {
        return getIntProperty("network.port", 8080);
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
}
