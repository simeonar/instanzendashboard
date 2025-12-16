package org.example.scanner;

import org.example.model.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Network scanner for checking instance availability
 */
public class NetworkScanner {
    private static final Logger logger = LoggerFactory.getLogger(NetworkScanner.class);
    private final int connectionTimeoutMs;
    private final ExecutorService executorService;

    public NetworkScanner(int connectionTimeoutMs, int threadPoolSize) {
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
    }

    /**
     * Scans a range of IP addresses for reachable instances
     */
    public List<Instance> scanRange(String startIp, String endIp, int port) {
        List<Instance> instances = new ArrayList<>();
        List<String> ipAddresses = generateIpRange(startIp, endIp);
        
        logger.info("Scanning {} IP addresses on port {}", ipAddresses.size(), port);
        
        List<Future<Instance>> futures = new ArrayList<>();
        for (String ip : ipAddresses) {
            futures.add(executorService.submit(() -> checkInstance(ip, port)));
        }

        for (Future<Instance> future : futures) {
            try {
                Instance instance = future.get();
                if (instance.isReachable()) {
                    instances.add(instance);
                    logger.debug("Found reachable instance: {}", instance.getAddress());
                }
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error checking instance", e);
            }
        }

        logger.info("Scan complete. Found {} reachable instances", instances.size());
        return instances;
    }

    /**
     * Checks if a specific instance is reachable
     */
    public Instance checkInstance(String ipAddress, int port) {
        Instance instance = new Instance(ipAddress, port);
        long startTime = System.currentTimeMillis();

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ipAddress, port), connectionTimeoutMs);
            instance.setReachable(true);
            instance.setResponseTimeMs(System.currentTimeMillis() - startTime);
            logger.trace("Instance {} is reachable ({}ms)", instance.getAddress(), instance.getResponseTimeMs());
        } catch (IOException e) {
            instance.setReachable(false);
            logger.trace("Instance {} is not reachable", instance.getAddress());
        } finally {
            instance.setLastChecked(System.currentTimeMillis());
        }

        return instance;
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
