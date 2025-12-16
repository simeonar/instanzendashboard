package org.example.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a network instance with its status and metadata
 */
public class Instance {
    private final String ipAddress;
    private final int port;
    private InstanceStatus status;
    private long lastChecked;
    private long responseTimeMs;
    private int httpStatusCode;
    private String errorMessage;
    
    // Metadata extracted from API
    private final Map<String, String> metadata;
    
    public Instance(String ipAddress, int port) {
        this.ipAddress = ipAddress;
        this.port = port;
        this.status = InstanceStatus.UNKNOWN;
        this.lastChecked = 0;
        this.responseTimeMs = -1;
        this.httpStatusCode = 0;
        this.metadata = new HashMap<>();
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    public InstanceStatus getStatus() {
        return status;
    }

    public void setStatus(InstanceStatus status) {
        this.status = status;
    }

    public long getLastChecked() {
        return lastChecked;
    }

    public void setLastChecked(long lastChecked) {
        this.lastChecked = lastChecked;
    }

    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    public void setHttpStatusCode(int httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Map<String, String> getMetadata() {
        return new HashMap<>(metadata);
    }

    public void addMetadata(String key, String value) {
        this.metadata.put(key, value);
    }

    public String getMetadataValue(String key) {
        return metadata.get(key);
    }

    public boolean hasMetadata() {
        return !metadata.isEmpty();
    }

    public String getBranch() {
        return metadata.get("branch");
    }

    public String getVersion() {
        return metadata.get("version");
    }

    public String getCommit() {
        return metadata.get("commit");
    }

    public boolean isReachable() {
        return status != InstanceStatus.UNREACHABLE && status != InstanceStatus.UNKNOWN;
    }

    public boolean isHealthy() {
        return status == InstanceStatus.API_HEALTHY;
    }

    public String getAddress() {
        return ipAddress + ":" + port;
    }

    @Override
    public String toString() {
        return "Instance{" +
                "address=" + getAddress() +
                ", status=" + status +
                ", responseTime=" + responseTimeMs + "ms" +
                ", branch=" + getBranch() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Instance instance = (Instance) o;
        return port == instance.port && ipAddress.equals(instance.ipAddress);
    }

    @Override
    public int hashCode() {
        int result = ipAddress.hashCode();
        result = 31 * result + port;
        return result;
    }
}
