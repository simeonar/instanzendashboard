package org.example;

import org.example.config.ConfigManager;
import org.example.dashboard.ConsoleDashboard;
import org.example.dashboard.DashboardManager;
import org.example.dashboard.WebDashboard;
import org.example.model.Instance;
import org.example.scanner.HealthChecker;
import org.example.scanner.NetworkScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Main application class for Instance Dashboard with multi-level health checking
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            // Load configuration
            ConfigManager config = new ConfigManager();
            logger.info("Configuration loaded successfully");

            // Prepare metadata field mapping
            Map<String, String> metadataMapping = new HashMap<>();
            metadataMapping.put("branch", config.getMetadataBranchField());
            metadataMapping.put("version", config.getMetadataVersionField());
            metadataMapping.put("commit", config.getMetadataCommitField());
            metadataMapping.put("timestamp", config.getMetadataTimestampField());
            metadataMapping.put("status", config.getMetadataStatusField());

            // Initialize health checker
            HealthChecker healthChecker = new HealthChecker(
                    config.getHealthCheckTimeoutMs(),
                    config.getHealthCheckPath(),
                    config.getHealthCheckExpectedStatus(),
                    config.isMetadataEnabled(),
                    metadataMapping
            );

            // Initialize scanner with health checker
            NetworkScanner scanner = new NetworkScanner(
                    config.getConnectionTimeoutMs(),
                    Runtime.getRuntime().availableProcessors() * 2,
                    healthChecker,
                    config.isHealthCheckEnabled(),
                    config.getCheckPaths()
            );

            DashboardManager dashboardManager = new DashboardManager();
            ConsoleDashboard consoleDashboard = new ConsoleDashboard(
                    config.getDashboardTitle(),
                    config.isDashboardShowMetadata()
            );

            // Start web dashboard if enabled
            WebDashboard webDashboard = null;
            if (config.isWebDashboardEnabled()) {
                try {
                    webDashboard = new WebDashboard(
                            config.getWebDashboardPort(), 
                            dashboardManager,
                            config,
                            config.getWebDashboardRefreshSeconds()
                    );
                    webDashboard.start();
                    logger.info("Web dashboard available at http://localhost:{}", config.getWebDashboardPort());
                } catch (IOException e) {
                    logger.error("Failed to start web dashboard", e);
                }
            }

            // Scheduled executor for periodic scanning
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

            // Scan task with filtering
            Runnable scanTask = () -> {
                try {
                    logger.info("Starting network scan with health checks...");
                    List<Instance> instances = scanner.scanRange(
                            config.getIpRangeStart(),
                            config.getIpRangeEnd(),
                            config.getNetworkPort()
                    );
                    
                    // Filter unreachable if configured
                    if (config.isDashboardFilterUnreachable()) {
                        instances = instances.stream()
                                .filter(Instance::isReachable)
                                .collect(Collectors.toList());
                    }
                    
                    dashboardManager.updateInstances(instances);
                    consoleDashboard.render(dashboardManager);
                    
                    logger.info("Scan complete: {} instances, {} healthy", 
                            instances.size(), dashboardManager.getHealthyInstances());
                } catch (Exception e) {
                    logger.error("Error during scan", e);
                }
            };

            // Initial scan
            scanTask.run();

            // Schedule periodic scans
            scheduler.scheduleAtFixedRate(
                    scanTask,
                    config.getScanIntervalSeconds(),
                    config.getScanIntervalSeconds(),
                    TimeUnit.SECONDS
            );

            logger.info("Dashboard started. Press Ctrl+C to exit.");

            // For shutdown hook
            final WebDashboard finalWebDashboard = webDashboard;
            
            // Shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down...");
                scheduler.shutdown();
                scanner.shutdown();
                if (finalWebDashboard != null) {
                    finalWebDashboard.stop();
                }
                try {
                    scheduler.awaitTermination(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    logger.error("Error during shutdown", e);
                }
                logger.info("Shutdown complete");
            }));

            // Keep main thread alive
            Thread.currentThread().join();

        } catch (IOException e) {
            logger.error("Failed to load configuration", e);
            System.err.println("Error: Unable to load configuration file. Please check application.properties");
            System.exit(1);
        } catch (InterruptedException e) {
            logger.error("Application interrupted", e);
            Thread.currentThread().interrupt();
        }
    }
}