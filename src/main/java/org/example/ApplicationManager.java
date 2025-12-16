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
    private boolean isScanning = false;
    
    public ApplicationManager(ConfigManager configManager, DashboardManager dashboardManager, ConsoleDashboard consoleDashboard) {
        this.configManager = configManager;
        this.dashboardManager = dashboardManager;
        this.consoleDashboard = consoleDashboard;
    }
    
    /**
     * Initialize scanner with current configuration (no auto-scan)
     */
    public synchronized void startScanner() throws IOException {
        logger.info("Initializing scanner with current configuration...");
        
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
        
        logger.info("Scanner initialized successfully (manual scan mode)");
    }
    
    /**
     * Perform a manual scan
     */
    public synchronized void performScan() {
        if (isScanning) {
            logger.warn("Scan already in progress");
            throw new RuntimeException("Scan already in progress");
        }
        
        isScanning = true;
        try {
            logger.info("Starting manual scan...");
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
            logger.info("Manual scan completed: {} instances found", instances.size());
        } catch (Exception e) {
            logger.error("Error during manual scan", e);
            throw new RuntimeException("Scan failed: " + e.getMessage());
        } finally {
            isScanning = false;
        }
    }
    
    /**
     * Check if scan is currently running
     */
    public boolean isScanning() {
        return isScanning;
    }
    
    /**
     * Stop the scanner
     */
    public synchronized void stopScanner() {
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
