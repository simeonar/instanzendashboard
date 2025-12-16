package org.example.model;

/**
 * Represents a network instance with its status
 */
public class Instance {
    private final String ipAddress;
    private final int port;
    private boolean reachable;
    private boolean applicationActive;
    private long lastChecked;
    private long responseTimeMs;

    public Instance(String ipAddress, int port) {
        this.ipAddress = ipAddress;
        this.port = port;
        this.reachable = false;
        this.applicationActive = false;
        this.lastChecked = 0;
        this.responseTimeMs = -1;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    public boolean isReachable() {
        return reachable;
    }

    public void setReachable(boolean reachable) {
        this.reachable = reachable;
    }

    public boolean isApplicationActive() {
        return applicationActive;
    }

    public void setApplicationActive(boolean applicationActive) {
        this.applicationActive = applicationActive;
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

    public String getAddress() {
        return ipAddress + ":" + port;
    }

    @Override
    public String toString() {
        return "Instance{" +
                "address=" + getAddress() +
                ", reachable=" + reachable +
                ", applicationActive=" + applicationActive +
                ", responseTime=" + responseTimeMs + "ms" +
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
