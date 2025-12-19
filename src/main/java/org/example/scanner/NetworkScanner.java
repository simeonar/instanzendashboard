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

    public interface ProgressListener {
        void onStarted(int total);

        void onItemStarted(String ipAddress);

        void onItemCompleted(String ipAddress);

        void onFinished();
    }

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
        return scanRange(startIp, endIp, port, null);
    }

    /**
     * Scans a range of IP addresses and reports progress.
     * Returns results for ALL scanned addresses, including unreachable.
     */
    public List<Instance> scanRange(String startIp, String endIp, int port, ProgressListener progressListener) {
        List<String> ipAddresses = generateIpRange(startIp, endIp);
        java.util.Map<String, Instance> resultsByIp = new java.util.concurrent.ConcurrentHashMap<>();

        logger.info("Scanning {} IP addresses on port {} with health checks", ipAddresses.size(), port);
        if (progressListener != null) {
            progressListener.onStarted(ipAddresses.size());
        }

        CompletionService<Instance> completionService = new ExecutorCompletionService<>(executorService);
        for (String ip : ipAddresses) {
            completionService.submit(() -> {
                if (progressListener != null) {
                    progressListener.onItemStarted(ip);
                }
                try {
                    return checkInstanceMultiLevel(ip, port);
                } catch (Exception e) {
                    // Never drop a scanned address due to unexpected errors.
                    Instance failed = new Instance(ip, port);
                    failed.setStatus(InstanceStatus.API_ERROR);
                    failed.setErrorMessage("Scan error: " + e.getMessage());
                    return failed;
                } finally {
                    if (progressListener != null) {
                        progressListener.onItemCompleted(ip);
                    }
                }
            });
        }

        for (int i = 0; i < ipAddresses.size(); i++) {
            try {
                Future<Instance> future = completionService.take();
                Instance instance = future.get();
                resultsByIp.put(instance.getIpAddress(), instance);
            } catch (InterruptedException e) {
                logger.debug("Scan interrupted (scanner restarting)");
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException e) {
                logger.error("Error checking instance", e);
            }
        }

        if (progressListener != null) {
            progressListener.onFinished();
        }

        // Return results in the same order as the input IP range for better UX.
        List<Instance> ordered = new ArrayList<>(ipAddresses.size());
        for (String ip : ipAddresses) {
            Instance instance = resultsByIp.get(ip);
            if (instance == null) {
                Instance missing = new Instance(ip, port);
                missing.setStatus(InstanceStatus.UNKNOWN);
                missing.setErrorMessage("No scan result");
                ordered.add(missing);
            } else {
                ordered.add(instance);
            }
        }

        logger.info("Scan complete. Scanned {} addresses", ordered.size());
        return ordered;
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
            if (checkPaths != null && checkPaths.length > 0) {
                for (String path : checkPaths) {
                    org.example.model.PathCheckResult pathResult = new org.example.model.PathCheckResult(path);
                    pathResult.setStatus(InstanceStatus.UNREACHABLE);
                    pathResult.setErrorMessage("TCP port unreachable");
                    instance.addPathResult(pathResult);
                }
            }
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
        long start = ipv4ToLong(startIp);
        long end = ipv4ToLong(endIp);
        if (end < start) {
            long tmp = start;
            start = end;
            end = tmp;
        }

        long count = (end - start) + 1;
        if (count > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("IP range too large: " + count);
        }
        for (long v = start; v <= end; v++) {
            ipAddresses.add(longToIpv4(v));
        }
        return ipAddresses;
    }

    private long ipv4ToLong(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid IPv4 address: " + ip);
        }
        long result = 0;
        for (int i = 0; i < 4; i++) {
            int part = Integer.parseInt(parts[i]);
            if (part < 0 || part > 255) {
                throw new IllegalArgumentException("Invalid IPv4 address: " + ip);
            }
            result = (result << 8) | (part & 0xFF);
        }
        return result;
    }

    private String longToIpv4(long value) {
        return ((value >> 24) & 0xFF) + "." +
                ((value >> 16) & 0xFF) + "." +
                ((value >> 8) & 0xFF) + "." +
                (value & 0xFF);
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
