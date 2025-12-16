package org.example;

import org.example.config.ConfigManager;
import org.example.dashboard.ConsoleDashboard;
import org.example.dashboard.DashboardManager;
import org.example.dashboard.WebDashboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Main application class for Instance Dashboard with hot-reload support
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        ApplicationManager appManager = null;
        WebDashboard webDashboard = null;
        
        try {
            // Load configuration
            ConfigManager config = new ConfigManager();
            logger.info("Configuration loaded successfully");

            // Initialize dashboard components
            DashboardManager dashboardManager = new DashboardManager();
            ConsoleDashboard consoleDashboard = new ConsoleDashboard(
                    config.getDashboardTitle(),
                    config.isDashboardShowMetadata()
            );

            // Initialize application manager for hot-reload support
            appManager = new ApplicationManager(config, dashboardManager, consoleDashboard);
            appManager.startScanner();

            // Start web dashboard if enabled
            if (config.isWebDashboardEnabled()) {
                try {
                    webDashboard = new WebDashboard(
                            config.getWebDashboardPort(), 
                            dashboardManager,
                            config,
                            config.getWebDashboardRefreshSeconds()
                    );
                    webDashboard.setApplicationManager(appManager);
                    webDashboard.start();
                    logger.info("Web dashboard available at http://localhost:{}", config.getWebDashboardPort());
                } catch (IOException e) {
                    logger.error("Failed to start web dashboard", e);
                }
            }

            logger.info("Dashboard started. Press Ctrl+C to exit.");

            // For shutdown hook
            final ApplicationManager finalAppManager = appManager;
            final WebDashboard finalWebDashboard = webDashboard;
            
            // Shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down...");
                if (finalAppManager != null) {
                    finalAppManager.shutdown();
                }
                if (finalWebDashboard != null) {
                    finalWebDashboard.stop();
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