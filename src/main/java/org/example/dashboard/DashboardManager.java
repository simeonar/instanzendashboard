package org.example.dashboard;

import org.example.model.Instance;
import org.example.model.InstanceStatus;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages dashboard state and instance data
 */
public class DashboardManager {
    private final List<Instance> instances;
    private long lastUpdateTime;

    public DashboardManager() {
        this.instances = new CopyOnWriteArrayList<>();
        this.lastUpdateTime = 0;
    }

    public void updateInstances(List<Instance> newInstances) {
        instances.clear();
        instances.addAll(newInstances);
        lastUpdateTime = System.currentTimeMillis();
    }

    public List<Instance> getInstances() {
        return new CopyOnWriteArrayList<>(instances);
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public int getTotalInstances() {
        return instances.size();
    }

    public int getReachableInstances() {
        return (int) instances.stream().filter(Instance::isReachable).count();
    }

    public int getHealthyInstances() {
        return (int) instances.stream()
                .filter(i -> i.getStatus() == InstanceStatus.API_HEALTHY)
                .count();
    }

    public int getHttpOkInstances() {
        return (int) instances.stream()
                .filter(i -> i.getStatus() == InstanceStatus.HTTP_OK)
                .count();
    }

    public int getDegradedInstances() {
        return (int) instances.stream()
                .filter(i -> i.getStatus() == InstanceStatus.API_DEGRADED)
                .count();
    }

    public int getErrorInstances() {
        return (int) instances.stream()
                .filter(i -> i.getStatus() == InstanceStatus.API_ERROR || 
                            i.getStatus() == InstanceStatus.TIMEOUT)
                .count();
    }

    public int getUnreachableInstances() {
        return (int) instances.stream()
                .filter(i -> i.getStatus() == InstanceStatus.UNREACHABLE || 
                            i.getStatus() == InstanceStatus.UNKNOWN)
                .count();
    }

    public int getActiveApplications() {
        return (int) instances.stream().filter(Instance::isHealthy).count();
    }
}
