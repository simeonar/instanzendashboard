package org.example;

import org.example.config.ConfigManager;
import org.example.dashboard.ConsoleDashboard;
import org.example.dashboard.DashboardManager;
import org.example.scanner.HealthChecker;
import org.example.scanner.NetworkScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages application lifecycle and allows hot-reloading of configuration
 */
public class ApplicationManager {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationManager.class);
    
    private final ConfigManager configManager;
    private final DashboardManager dashboardManager;
    private final ConsoleDashboard consoleDashboard;
    
    private HealthChecker healthChecker;
    private NetworkScanner networkScanner;
    private ScheduledExecutorService scheduler;
    
    public ApplicationManager(ConfigManager configManager, DashboardManager dashboardManager, ConsoleDashboard consoleDashboard) {
        this.configManager = configManager;
        this.dashboardManager = dashboardManager;
        this.consoleDashboard = consoleDashboard;
    }
    
    /**
     * Start or restart the scanner with current configuration
     */
    public synchronized void startScanner() throws IOException {
        logger.info("Starting scanner with current configuration...");
        
        // Stop existing scanner if running
        stopScanner();
        
        // Reload configuration
        configManager.reloadProperties();
        
        // Prepare metadata field mapping
        java.util.Map<String, String> metadataMapping = new java.util.HashMap<>();
        metadataMapping.put("branch", configManager.getMetadataBranchField());
        metadataMapping.put("version", configManager.getMetadataVersionField());
        metadataMapping.put("commit", configManager.getMetadataCommitField());
        metadataMapping.put("timestamp", configManager.getMetadataTimestampField());
        metadataMapping.put("status", configManager.getMetadataStatusField());
        
        // Initialize HealthChecker
        healthChecker = new HealthChecker(
                configManager.getHealthCheckTimeoutMs(),
                configManager.getHealthCheckPath(),
                configManager.getHealthCheckExpectedStatus(),
                configManager.isMetadataEnabled(),
                metadataMapping
        );
        
        // Initialize NetworkScanner
        networkScanner = new NetworkScanner(
                configManager.getConnectionTimeoutMs(),
                Runtime.getRuntime().availableProcessors() * 2,
                healthChecker,
                configManager.isHealthCheckEnabled(),
                configManager.getCheckPaths()
        );
        
        // Create scheduler for periodic scanning
        scheduler = Executors.newScheduledThreadPool(1);
        
        // Schedule periodic scans
        scheduler.scheduleAtFixedRate(() -> {
            try {
                logger.info("Starting scheduled scan...");
                java.util.List<org.example.model.Instance> instances = networkScanner.scanRange(
                        configManager.getIpRangeStart(),
                        configManager.getIpRangeEnd(),
                        configManager.getNetworkPort()
                );
                
                // Filter unreachable if configured
                if (configManager.isDashboardFilterUnreachable()) {
                    instances = instances.stream()
                            .filter(org.example.model.Instance::isReachable)
                            .collect(java.util.stream.Collectors.toList());
                }
                
                dashboardManager.updateInstances(instances);
                consoleDashboard.render(dashboardManager);
            } catch (Exception e) {
                logger.error("Error during scheduled scan", e);
            }
        }, 0, configManager.getScanIntervalSeconds(), TimeUnit.SECONDS);
        
        logger.info("Scanner started successfully");
    }
    
    /**
     * Stop the scanner
     */
    public synchronized void stopScanner() {
        if (scheduler != null && !scheduler.isShutdown()) {
            logger.info("Stopping scanner...");
            scheduler.shutdownNow();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }
        
        if (networkScanner != null) {
            networkScanner.shutdown();
            networkScanner = null;
        }
    }
    
    /**
     * Restart scanner with new configuration (hot reload)
     */
    public synchronized void restartScanner() {
        try {
            logger.info("Restarting scanner with new configuration...");
            startScanner();
            logger.info("Scanner restarted successfully");
        } catch (IOException e) {
            logger.error("Failed to restart scanner", e);
            throw new RuntimeException("Failed to restart scanner: " + e.getMessage());
        }
    }
    
    /**
     * Graceful shutdown
     */
    public void shutdown() {
        logger.info("Shutting down application...");
        stopScanner();
    }
}
