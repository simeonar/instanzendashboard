package org.example;

import org.example.config.ConfigManager;
import org.example.dashboard.ConsoleDashboard;
import org.example.dashboard.DashboardManager;
import org.example.model.Instance;
import org.example.scanner.NetworkScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main application class for Instance Dashboard
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            // Load configuration
            ConfigManager config = new ConfigManager();
            logger.info("Configuration loaded successfully");

            // Initialize components
            NetworkScanner scanner = new NetworkScanner(
                    config.getConnectionTimeoutMs(),
                    Runtime.getRuntime().availableProcessors() * 2
            );

            DashboardManager dashboardManager = new DashboardManager();
            ConsoleDashboard consoleDashboard = new ConsoleDashboard(config.getDashboardTitle());

            // Scheduled executor for periodic scanning
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

            // Scan task
            Runnable scanTask = () -> {
                try {
                    logger.info("Starting network scan...");
                    List<Instance> instances = scanner.scanRange(
                            config.getIpRangeStart(),
                            config.getIpRangeEnd(),
                            config.getNetworkPort()
                    );
                    dashboardManager.updateInstances(instances);
                    consoleDashboard.render(dashboardManager);
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

            // Shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down...");
                scheduler.shutdown();
                scanner.shutdown();
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