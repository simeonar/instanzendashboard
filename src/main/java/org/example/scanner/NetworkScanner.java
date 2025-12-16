package org.example.scanner;

import org.example.model.Instance;
import org.example.model.InstanceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Network scanner with multi-level health checking
 */
public class NetworkScanner {
    private static final Logger logger = LoggerFactory.getLogger(NetworkScanner.class);
    private final int connectionTimeoutMs;
    private final ExecutorService executorService;
    private final HealthChecker healthChecker;
    private final boolean healthCheckEnabled;
    private final String[] checkPaths;

    public NetworkScanner(int connectionTimeoutMs, int threadPoolSize, 
                         HealthChecker healthChecker, boolean healthCheckEnabled, String[] checkPaths) {
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        this.healthChecker = healthChecker;
        this.healthCheckEnabled = healthCheckEnabled;
        this.checkPaths = checkPaths;
    }

    /**
     * Scans a range of IP addresses with multi-level health checking
     */
    public List<Instance> scanRange(String startIp, String endIp, int port) {
        List<Instance> instances = new ArrayList<>();
        List<String> ipAddresses = generateIpRange(startIp, endIp);
        
        logger.info("Scanning {} IP addresses on port {} with health checks", ipAddresses.size(), port);
        
        List<Future<Instance>> futures = new ArrayList<>();
        for (String ip : ipAddresses) {
            futures.add(executorService.submit(() -> checkInstanceMultiLevel(ip, port)));
        }

        for (Future<Instance> future : futures) {
            try {
                Instance instance = future.get();
                if (instance.isReachable()) {
                    instances.add(instance);
                    logger.debug("Found instance: {} - Status: {}", 
                        instance.getAddress(), instance.getStatus().getDisplayName());
                }
            } catch (InterruptedException e) {
                logger.debug("Scan interrupted (scanner restarting)");
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException e) {
                logger.error("Error checking instance", e);
            }
        }

        logger.info("Scan complete. Found {} reachable instances", instances.size());
        return instances;
    }

    /**
     * Performs multi-level health check:
     * Level 1: TCP Socket (port reachability)
     * Level 2: HTTP (web interface)
     * Level 3: REST API (health endpoint with metadata)
     */
    public Instance checkInstanceMultiLevel(String ipAddress, int port) {
        Instance instance = new Instance(ipAddress, port);
        instance.setLastChecked(System.currentTimeMillis());
        
        // Level 1: TCP Socket Check
        if (!checkTcpPort(instance)) {
            instance.setStatus(InstanceStatus.UNREACHABLE);
            return instance;
        }
        
        // Level 2 & 3: HTTP Check with metadata extraction (if enabled)
        if (healthCheckEnabled && checkPaths != null && checkPaths.length > 0) {
            // Check all configured paths and extract metadata from any that work
            healthChecker.checkMultiplePathsWithMetadata(instance, checkPaths);
        } else {
            // No HTTP check, just mark as port open
            instance.setStatus(InstanceStatus.PORT_OPEN);
        }
        
        return instance;
    }

    /**
     * Checks TCP port reachability
     */
    private boolean checkTcpPort(Instance instance) {
        long startTime = System.currentTimeMillis();
        
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(instance.getIpAddress(), instance.getPort()), 
                          connectionTimeoutMs);
            instance.setResponseTimeMs(System.currentTimeMillis() - startTime);
            logger.trace("TCP port {} is open ({}ms)", instance.getAddress(), instance.getResponseTimeMs());
            return true;
        } catch (IOException e) {
            logger.trace("TCP port {} is not reachable", instance.getAddress());
            return false;
        }
    }

    /**
     * Generates a list of IP addresses in the given range
     */
    private List<String> generateIpRange(String startIp, String endIp) {
        List<String> ipAddresses = new ArrayList<>();
        
        String[] startParts = startIp.split("\\.");
        String[] endParts = endIp.split("\\.");
        
        int start = Integer.parseInt(startParts[3]);
        int end = Integer.parseInt(endParts[3]);
        String baseIp = startParts[0] + "." + startParts[1] + "." + startParts[2] + ".";
        
        for (int i = start; i <= end; i++) {
            ipAddresses.add(baseIp + i);
        }
        
        return ipAddresses;
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}
