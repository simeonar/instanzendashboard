package org.example.model;

/**
 * Represents the status of an instance
 */
public enum InstanceStatus {
    UNKNOWN("Unknown", "Status not yet determined"),
    UNREACHABLE("Unreachable", "Port is not accessible"),
    PORT_OPEN("Port Open", "Port is open but HTTP not responding"),
    HTTP_OK("HTTP OK", "Web interface is accessible"),
    API_HEALTHY("API Healthy", "REST API is responding and healthy"),
    API_DEGRADED("API Degraded", "Web works but API not responding"),
    API_ERROR("API Error", "API responding with error"),
    TIMEOUT("Timeout", "Request timed out");

    private final String displayName;
    private final String description;

    InstanceStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isHealthy() {
        return this == API_HEALTHY || this == HTTP_OK;
    }

    public boolean isReachable() {
        return this != UNREACHABLE && this != UNKNOWN && this != TIMEOUT;
    }
}
