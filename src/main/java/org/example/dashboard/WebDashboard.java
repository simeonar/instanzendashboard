package org.example.dashboard;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.example.config.ConfigManager;
import org.example.model.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Web-based dashboard with HTTP server
 */
public class WebDashboard {
    private static final Logger logger = LoggerFactory.getLogger(WebDashboard.class);
    
    private final int port;
    private final DashboardManager dashboardManager;
    private final ConfigManager configManager;
    private org.example.ApplicationManager applicationManager;
    private final Gson gson;
    private HttpServer server;

    private final int refreshIntervalSeconds;

    public WebDashboard(int port, DashboardManager dashboardManager, ConfigManager configManager, int refreshIntervalSeconds) {
        this.port = port;
        this.dashboardManager = dashboardManager;
        this.configManager = configManager;
        this.refreshIntervalSeconds = refreshIntervalSeconds;
        this.gson = new Gson();
    }

    public void setApplicationManager(org.example.ApplicationManager applicationManager) {
        this.applicationManager = applicationManager;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // API endpoints
        server.createContext("/api/instances", new InstancesHandler());
        server.createContext("/api/stats", new StatsHandler());
        server.createContext("/api/config", new ConfigHandler());
        server.createContext("/api/config/apply", new ConfigApplyHandler());
        server.createContext("/api/scan", new ScanHandler());
        
        // Pages
        server.createContext("/settings", new SettingsPageHandler());
        server.createContext("/", new DashboardHandler());
        
        server.setExecutor(null);
        server.start();
        
        logger.info("Web dashboard started at http://localhost:{}", port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            logger.info("Web dashboard stopped");
        }
    }

    /**
     * Handler for instances API endpoint
     */
    private class InstancesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String json = gson.toJson(dashboardManager.getInstances());
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            
            OutputStream os = exchange.getResponseBody();
            os.write(response);
            os.close();
        }
    }

    /**
     * Handler for statistics API endpoint
     */
    private class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            Map<String, Object> stats = new HashMap<>();
            stats.put("total", dashboardManager.getTotalInstances());
            stats.put("httpOk", dashboardManager.getHttpOkInstances());
            stats.put("degraded", dashboardManager.getDegradedInstances());
            stats.put("errors", dashboardManager.getErrorInstances());
            stats.put("lastUpdate", dashboardManager.getLastUpdateTime());
            stats.put("scanInterval", configManager.getScanIntervalSeconds());
            stats.put("currentTime", System.currentTimeMillis());
            
            // Add paths with Open button visibility
            String autoOpenPaths = configManager.getProperty("check.paths.autoopen", "");
            stats.put("pathsWithOpenButton", autoOpenPaths);

            String json = gson.toJson(stats);
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            
            OutputStream os = exchange.getResponseBody();
            os.write(response);
            os.close();
        }
    }

    /**     * Handler for configuration API endpoint
     */
    private class ConfigHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            
            if ("GET".equals(method)) {
                // Get current configuration
                Map<String, String> config = configManager.getAllProperties();
                String json = gson.toJson(config);
                
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                
                byte[] response = json.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                
                OutputStream os = exchange.getResponseBody();
                os.write(response);
                os.close();
                
            } else if ("POST".equals(method)) {
                // Update configuration
                // Java 8 compatible way to read all bytes
                java.io.InputStream is = exchange.getRequestBody();
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                int nRead;
                byte[] data = new byte[1024];
                while ((nRead = is.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                buffer.flush();
                String body = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
                
                @SuppressWarnings("unchecked")
                Map<String, String> updates = gson.fromJson(body, Map.class);
                
                try {
                    for (Map.Entry<String, String> entry : updates.entrySet()) {
                        configManager.setProperty(entry.getKey(), entry.getValue());
                    }
                    configManager.saveProperties();
                    
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("message", "Configuration saved successfully. Restart required for changes to take effect.");
                    
                    String json = gson.toJson(result);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                    
                    byte[] response = json.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, response.length);
                    
                    OutputStream os = exchange.getResponseBody();
                    os.write(response);
                    os.close();
                    
                } catch (Exception e) {
                    logger.error("Failed to save configuration", e);
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", false);
                    result.put("message", "Failed to save configuration: " + e.getMessage());
                    
                    String json = gson.toJson(result);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    
                    byte[] response = json.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(500, response.length);
                    
                    OutputStream os = exchange.getResponseBody();
                    os.write(response);
                    os.close();
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    /**
     * Handler for applying configuration without restart
     */
    private class ConfigApplyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            try {
                if (applicationManager == null) {
                    throw new RuntimeException("ApplicationManager not initialized");
                }

                // Restart scanner with new configuration
                applicationManager.restartScanner();

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Configuration applied successfully! Scanner restarted with new settings.");

                String json = gson.toJson(result);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

                byte[] response = json.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);

                OutputStream os = exchange.getResponseBody();
                os.write(response);
                os.close();

            } catch (Exception e) {
                logger.error("Failed to apply configuration", e);
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "Failed to apply configuration: " + e.getMessage());

                String json = gson.toJson(result);
                exchange.getResponseHeaders().set("Content-Type", "application/json");

                byte[] response = json.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, response.length);

                OutputStream os = exchange.getResponseBody();
                os.write(response);
                os.close();
            }
        }
    }

    /**
     * Handler for manual scan trigger
     */
    private class ScanHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            try {
                if (applicationManager == null) {
                    throw new RuntimeException("ApplicationManager not initialized");
                }

                // Check if scan is already running
                if (applicationManager.isScanning()) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", false);
                    result.put("message", "Scan is already in progress");

                    String json = gson.toJson(result);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

                    byte[] response = json.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(409, response.length);

                    OutputStream os = exchange.getResponseBody();
                    os.write(response);
                    os.close();
                    return;
                }

                // Trigger scan in background thread
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            applicationManager.performScan();
                        } catch (Exception e) {
                            logger.error("Error during manual scan", e);
                        }
                    }
                }).start();

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Scan started successfully");

                String json = gson.toJson(result);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

                byte[] response = json.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);

                OutputStream os = exchange.getResponseBody();
                os.write(response);
                os.close();

            } catch (Exception e) {
                logger.error("Failed to start scan", e);
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "Failed to start scan: " + e.getMessage());

                String json = gson.toJson(result);
                exchange.getResponseHeaders().set("Content-Type", "application/json");

                byte[] response = json.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, response.length);

                OutputStream os = exchange.getResponseBody();
                os.write(response);
                os.close();
            }
        }
    }

    /**
     * Handler for settings page
     */
    private class SettingsPageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = generateSettingsPage();
            
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            byte[] response = html.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            
            OutputStream os = exchange.getResponseBody();
            os.write(response);
            os.close();
        }
    }

    /**     * Handler for main dashboard HTML page
     */
    private class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = generateDashboardHtml();
            
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            
            byte[] response = html.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            
            OutputStream os = exchange.getResponseBody();
            os.write(response);
            os.close();
        }
    }

    private String generateDashboardHtml() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>Instance Dashboard</title>\n" +
                "    <style>\n" +
                "        * {\n" +
                "            margin: 0;\n" +
                "            padding: 0;\n" +
                "            box-sizing: border-box;\n" +
                "        }\n" +
                "        body {\n" +
                "            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;\n" +
                "            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n" +
                "            color: #333;\n" +
                "            padding: 20px;\n" +
                "            min-height: 100vh;\n" +
                "        }\n" +
                "        .container {\n" +
                "            max-width: 1400px;\n" +
                "            margin: 0 auto;\n" +
                "        }\n" +
                "        .header {\n" +
                "            background: white;\n" +
                "            padding: 30px;\n" +
                "            border-radius: 12px;\n" +
                "            box-shadow: 0 4px 6px rgba(0,0,0,0.1);\n" +
                "            margin-bottom: 20px;\n" +
                "        }\n" +
                "        h1 {\n" +
                "            color: #667eea;\n" +
                "            margin-bottom: 10px;\n" +
                "        }\n" +
                "        .stats {\n" +
                "            display: grid;\n" +
                "            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));\n" +
                "            gap: 15px;\n" +
                "            margin-bottom: 20px;\n" +
                "        }\n" +
                "        .stat-card {\n" +
                "            background: white;\n" +
                "            padding: 20px;\n" +
                "            border-radius: 12px;\n" +
                "            box-shadow: 0 4px 6px rgba(0,0,0,0.1);\n" +
                "            text-align: center;\n" +
                "        }\n" +
                "        .stat-card .label {\n" +
                "            font-size: 14px;\n" +
                "            color: #666;\n" +
                "            margin-bottom: 5px;\n" +
                "        }\n" +
                "        .stat-card .value {\n" +
                "            font-size: 32px;\n" +
                "            font-weight: bold;\n" +
                "        }\n" +
                "        .stat-card.healthy .value { color: #10b981; }\n" +
                "        .stat-card.http-ok .value { color: #06b6d4; }\n" +
                "        .stat-card.degraded .value { color: #f59e0b; }\n" +
                "        .stat-card.errors .value { color: #ef4444; }\n" +
                "        .instances-container {\n" +
                "            background: white;\n" +
                "            padding: 30px;\n" +
                "            border-radius: 12px;\n" +
                "            box-shadow: 0 4px 6px rgba(0,0,0,0.1);\n" +
                "        }\n" +
                "        .instance-card {\n" +
                "            border: 1px solid #e5e7eb;\n" +
                "            border-radius: 8px;\n" +
                "            padding: 20px;\n" +
                "            margin-bottom: 15px;\n" +
                "            transition: all 0.3s ease;\n" +
                "        }\n" +
                "        .instance-card:hover {\n" +
                "            box-shadow: 0 4px 12px rgba(0,0,0,0.1);\n" +
                "            transform: translateY(-2px);\n" +
                "        }\n" +
                "        .instance-header {\n" +
                "            display: flex;\n" +
                "            justify-content: space-between;\n" +
                "            align-items: center;\n" +
                "            margin-bottom: 15px;\n" +
                "        }\n" +
                "        .instance-title {\n" +
                "            font-size: 18px;\n" +
                "            font-weight: bold;\n" +
                "            color: #1f2937;\n" +
                "        }\n" +
                "        .status-badge {\n" +
                "            padding: 6px 12px;\n" +
                "            border-radius: 20px;\n" +
                "            font-size: 12px;\n" +
                "            font-weight: 600;\n" +
                "            text-transform: uppercase;\n" +
                "        }\n" +
                "        .status-API_HEALTHY { background: #d1fae5; color: #065f46; }\n" +
                "        .status-HTTP_OK { background: #cffafe; color: #164e63; }\n" +
                "        .status-API_DEGRADED { background: #fef3c7; color: #92400e; }\n" +
                "        .status-API_ERROR { background: #fee2e2; color: #991b1b; }\n" +
                "        .status-PORT_OPEN { background: #e9d5ff; color: #6b21a8; }\n" +
                "        .status-UNREACHABLE { background: #fee2e2; color: #991b1b; }\n" +
                "        .metadata {\n" +
                "            display: flex;\n" +
                "            gap: 20px;\n" +
                "            margin-bottom: 15px;\n" +
                "            color: #6b7280;\n" +
                "            font-size: 14px;\n" +
                "        }\n" +
                "        .metadata span {\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            gap: 5px;\n" +
                "        }\n" +
                "        .paths-table {\n" +
                "            width: 100%;\n" +
                "            border-collapse: collapse;\n" +
                "            margin-top: 10px;\n" +
                "        }\n" +
                "        .paths-table th {\n" +
                "            background: #f9fafb;\n" +
                "            padding: 10px;\n" +
                "            text-align: left;\n" +
                "            font-size: 12px;\n" +
                "            color: #6b7280;\n" +
                "            font-weight: 600;\n" +
                "            text-transform: uppercase;\n" +
                "        }\n" +
                "        .paths-table td {\n" +
                "            padding: 12px 10px;\n" +
                "            border-top: 1px solid #e5e7eb;\n" +
                "            font-size: 14px;\n" +
                "        }\n" +
                "        .path-link {\n" +
                "            color: #667eea;\n" +
                "            text-decoration: none;\n" +
                "            font-weight: 500;\n" +
                "            transition: color 0.2s;\n" +
                "        }\n" +
                "        .path-link:hover {\n" +
                "            color: #764ba2;\n" +
                "            text-decoration: underline;\n" +
                "        }\n" +
                "        .open-btn {\n" +
                "            background: #667eea;\n" +
                "            color: white;\n" +
                "            border: none;\n" +
                "            padding: 6px 12px;\n" +
                "            border-radius: 6px;\n" +
                "            cursor: pointer;\n" +
                "            font-size: 12px;\n" +
                "            transition: background 0.2s;\n" +
                "        }\n" +
                "        .open-btn:hover {\n" +
                "            background: #764ba2;\n" +
                "        }\n" +
                "        .refresh-btn {\n" +
                "            background: white;\n" +
                "            color: #667eea;\n" +
                "            border: 2px solid #667eea;\n" +
                "            padding: 10px 20px;\n" +
                "            border-radius: 8px;\n" +
                "            cursor: pointer;\n" +
                "            font-size: 16px;\n" +
                "            font-weight: 600;\n" +
                "            transition: all 0.3s;\n" +
                "        }\n" +
                "        .refresh-btn:hover {\n" +
                "            background: #667eea;\n" +
                "            color: white;\n" +
                "            transform: rotate(180deg);\n" +
                "        }\n" +
                "        .refresh-btn:active {\n" +
                "            transform: rotate(180deg) scale(0.95);\n" +
                "        }\n" +
                "        .last-update {\n" +
                "            text-align: center;\n" +
                "            color: #6b7280;\n" +
                "            font-size: 14px;\n" +
                "            margin-top: 10px;\n" +
                "        }\n" +
                "        .loading {\n" +
                "            text-align: center;\n" +
                "            padding: 40px;\n" +
                "            color: #6b7280;\n" +
                "        }\n" +
                "        details {\n" +
                "            margin-top: 10px;\n" +
                "        }\n" +
                "        details summary {\n" +
                "            cursor: pointer;\n" +
                "            padding: 10px;\n" +
                "            background: #f9fafb;\n" +
                "            border-radius: 6px;\n" +
                "            font-weight: 600;\n" +
                "            color: #374151;\n" +
                "            transition: background 0.2s;\n" +
                "            user-select: none;\n" +
                "        }\n" +
                "        details summary:hover {\n" +
                "            background: #f3f4f6;\n" +
                "        }\n" +
                "        details[open] summary {\n" +
                "            margin-bottom: 10px;\n" +
                "            background: #e5e7eb;\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <div class=\"header\">\n" +
                "            <div style=\"display: flex; justify-content: space-between; align-items: center;\">\n" +
                "                <h1>🖥️ Instance Dashboard</h1>\n" +
                "                <div>\n" +
                "                    <a href='/settings' class=\"settings-btn\">⚙️ Settings</a>\n" +
                "                    <button class=\"refresh-btn\" onclick=\"triggerScan()\" id=\"scanBtn\">🔍 Scan</button>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "            <div class=\"last-update\" id=\"lastUpdate\">No scans yet</div>\n" +
                "            <div class=\"last-update\" id=\"scanStatus\" style=\"margin-top: 5px; color: #9ca3af;\"></div>\n" +
                "        </div>\n" +
                "\n" +
                "        <div class=\"stats\" id=\"stats\">\n" +
                "            <div class=\"stat-card\">\n" +
                "                <div class=\"label\">Total Instances</div>\n" +
                "                <div class=\"value\" id=\"totalInstances\">0</div>\n" +
                "            </div>\n" +
                "            <div class=\"stat-card http-ok\">\n" +
                "                <div class=\"label\">HTTP OK</div>\n" +
                "                <div class=\"value\" id=\"httpOkInstances\">0</div>\n" +
                "            </div>\n" +
                "            <div class=\"stat-card degraded\">\n" +
                "                <div class=\"label\">Degraded</div>\n" +
                "                <div class=\"value\" id=\"degradedInstances\">0</div>\n" +
                "            </div>\n" +
                "            <div class=\"stat-card errors\">\n" +
                "                <div class=\"label\">Errors</div>\n" +
                "                <div class=\"value\" id=\"errorInstances\">0</div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "\n" +
                "        <div class=\"instances-container\">\n" +
                "            <h2 style=\"margin-bottom: 20px;\">Instances</h2>\n" +
                "            <div id=\"instances\" class=\"loading\">Loading instances...</div>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <script>\n" +
                "        let pathsWithOpenButton = [];\n" +
                "        \n" +
                "        function fetchStats() {\n" +
                "            fetch('/api/stats')\n" +
                "                .then(response => response.json())\n" +
                "                .then(stats => {\n" +
                "                    document.getElementById('totalInstances').textContent = stats.total;\n" +
                "                    document.getElementById('httpOkInstances').textContent = stats.httpOk;\n" +
                "                    document.getElementById('degradedInstances').textContent = stats.degraded;\n" +
                "                    document.getElementById('errorInstances').textContent = stats.errors;\n" +
                "                    \n" +
                "                    // Store paths with Open button enabled\n" +
                "                    if (stats.pathsWithOpenButton) {\n" +
                "                        pathsWithOpenButton = stats.pathsWithOpenButton.split(',').map(s => s.trim()).filter(s => s);\n" +
                "                    }\n" +
                "                    \n" +
                "                    const date = new Date(stats.lastUpdate);\n" +
                "                    document.getElementById('lastUpdate').textContent = 'Last Update: ' + date.toLocaleString();\n" +
                "                    \n" +
                "                    // Calculate next scan time\n" +
                "                    if (stats.lastUpdate && stats.scanInterval) {\n" +
                "                        const lastUpdate = stats.lastUpdate;\n" +
                "                        const scanInterval = stats.scanInterval * 1000;\n" +
                "                        const nextScanTime = lastUpdate + scanInterval;\n" +
                "                        const now = stats.currentTime || Date.now();\n" +
                "                        const secondsUntilNext = Math.max(0, Math.floor((nextScanTime - now) / 1000));\n" +
                "                        document.getElementById('nextScan').textContent = `Next scan in: ${secondsUntilNext}s (every ${stats.scanInterval}s)`;\n" +
                "                    }\n" +
                "                })\n" +
                "                .catch(err => console.error('Error fetching stats:', err));\n" +
                "        }\n" +
                "\n" +
                "        function fetchInstances() {\n" +
                "            fetch('/api/instances')\n" +
                "                .then(response => response.json())\n" +
                "                .then(instances => {\n" +
                "                    const container = document.getElementById('instances');\n" +
                "                    \n" +
                "                    if (instances.length === 0) {\n" +
                "                        container.innerHTML = '<div class=\"loading\">No instances found</div>';\n" +
                "                        return;\n" +
                "                    }\n" +
                "                    \n" +
                "                    container.innerHTML = instances.map(instance => {\n" +
                "                        const metadata = instance.metadata || {};\n" +
                "                        const paths = instance.pathResults || {};\n" +
                "                        \n" +
                "                        let metadataHtml = '';\n" +
                "                        if (metadata.branch || metadata.version || metadata.commit) {\n" +
                "                            metadataHtml = '<div class=\"metadata\">';\n" +
                "                            if (metadata.branch) metadataHtml += '<span>🌿 Branch: <strong>' + metadata.branch + '</strong></span>';\n" +
                "                            if (metadata.version) metadataHtml += '<span>📦 Version: <strong>' + metadata.version + '</strong></span>';\n" +
                "                            if (metadata.commit) metadataHtml += '<span>💾 Commit: <strong>' + metadata.commit + '</strong></span>';\n" +
                "                            metadataHtml += '</div>';\n" +
                "                        }\n" +
                "                        \n" +
                "                        let pathsHtml = '';\n" +
                "                        if (Object.keys(paths).length > 0) {\n" +
                "                            pathsHtml = '<details><summary>📋 Scan Details (' + Object.keys(paths).length + ' paths)</summary>';\n" +
                "                            pathsHtml += '<table class=\"paths-table\"><thead><tr><th>Path</th><th>Status</th><th>HTTP Code</th><th>Response Time</th><th>Action</th></tr></thead><tbody>';\n" +
                "                            for (const [path, result] of Object.entries(paths)) {\n" +
                "                                const url = 'http://' + instance.ipAddress + ':' + instance.port + path;\n" +
                "                                const showOpenButton = pathsWithOpenButton.includes(path);\n" +
                "                                pathsHtml += '<tr>';\n" +
                "                                pathsHtml += '<td><a href=\"' + url + '\" target=\"_blank\" class=\"path-link\">' + path + '</a></td>';\n" +
                "                                pathsHtml += '<td><span class=\"status-badge status-' + result.status + '\">' + result.status + '</span></td>';\n" +
                "                                pathsHtml += '<td>' + (result.httpStatusCode || '-') + '</td>';\n" +
                "                                pathsHtml += '<td>' + (result.responseTimeMs >= 0 ? result.responseTimeMs + 'ms' : 'N/A') + '</td>';\n" +
                "                                pathsHtml += '<td>';\n" +
                "                                if (showOpenButton) {\n" +
                "                                    pathsHtml += '<button class=\"open-btn\" onclick=\"window.open(\\'' + url + '\\', \\'_blank\\')\">Open</button>';\n" +
                "                                } else {\n" +
                "                                    pathsHtml += '-';\n" +
                "                                }\n" +
                "                                pathsHtml += '</td>';\n" +
                "                                pathsHtml += '</tr>';\n" +
                "                            }\n" +
                "                            pathsHtml += '</tbody></table></details>';\n" +
                "                        }\n" +
                "                        \n" +
                "                        return `\n" +
                "                            <div class=\"instance-card\">\n" +
                "                                <div class=\"instance-header\">\n" +
                "                                    <div class=\"instance-title\">${instance.ipAddress}:${instance.port}</div>\n" +
                "                                    <span class=\"status-badge status-${instance.status}\">${instance.status}</span>\n" +
                "                                </div>\n" +
                "                                ${metadataHtml}\n" +
                "                                ${pathsHtml}\n" +
                "                            </div>\n" +
                "                        `;\n" +
                "                    }).join('');\n" +
                "                })\n" +
                "                .catch(err => {\n" +
                "                    console.error('Error fetching instances:', err);\n" +
                "                    document.getElementById('instances').innerHTML = '<div class=\"loading\">Error loading instances</div>';\n" +
                "                });\n" +
                "        }\n" +
                "\n" +
                "        function refresh() {\n" +
                "            fetchStats();\n" +
                "            fetchInstances();\n" +
                "        }\n" +
                "\n" +
                "        function triggerScan() {\n" +
                "            const btn = document.getElementById('scanBtn');\n" +
                "            const statusEl = document.getElementById('scanStatus');\n" +
                "            \n" +
                "            btn.disabled = true;\n" +
                "            btn.style.opacity = '0.5';\n" +
                "            statusEl.textContent = 'Scanning...';\n" +
                "            statusEl.style.color = '#f59e0b';\n" +
                "            \n" +
                "            fetch('/api/scan', { method: 'POST' })\n" +
                "                .then(response => response.json())\n" +
                "                .then(result => {\n" +
                "                    if (result.success) {\n" +
                "                        statusEl.textContent = 'Scan started successfully';\n" +
                "                        statusEl.style.color = '#10b981';\n" +
                "                        \n" +
                "                        // Poll for updates\n" +
                "                        const pollInterval = setInterval(() => {\n" +
                "                            refresh();\n" +
                "                        }, 2000);\n" +
                "                        \n" +
                "                        // Stop polling after 30 seconds\n" +
                "                        setTimeout(() => {\n" +
                "                            clearInterval(pollInterval);\n" +
                "                            btn.disabled = false;\n" +
                "                            btn.style.opacity = '1';\n" +
                "                            statusEl.textContent = '';\n" +
                "                        }, 30000);\n" +
                "                    } else {\n" +
                "                        statusEl.textContent = 'Error: ' + result.message;\n" +
                "                        statusEl.style.color = '#ef4444';\n" +
                "                        btn.disabled = false;\n" +
                "                        btn.style.opacity = '1';\n" +
                "                    }\n" +
                "                })\n" +
                "                .catch(err => {\n" +
                "                    console.error('Error triggering scan:', err);\n" +
                "                    statusEl.textContent = 'Error: ' + err.message;\n" +
                "                    statusEl.style.color = '#ef4444';\n" +
                "                    btn.disabled = false;\n" +
                "                    btn.style.opacity = '1';\n" +
                "                });\n" +
                "        }\n" +
                "\n" +
                "        // Initial load\n" +
                "        refresh();\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
    }

    private String generateSettingsPage() {
        return "<!DOCTYPE html>\n" +
                "<html lang='en'>\n" +
                "<head>\n" +
                "    <meta charset='UTF-8'>\n" +
                "    <meta name='viewport' content='width=device-width, initial-scale=1.0'>\n" +
                "    <title>Settings - Instanzen Dashboard</title>\n" +
                "    <style>\n" +
                "        * { margin: 0; padding: 0; box-sizing: border-box; }\n" +
                "        body {\n" +
                "            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;\n" +
                "            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n" +
                "            min-height: 100vh;\n" +
                "            padding: 20px;\n" +
                "        }\n" +
                "        .container {\n" +
                "            max-width: 900px;\n" +
                "            margin: 0 auto;\n" +
                "        }\n" +
                "        .header {\n" +
                "            background: rgba(255,255,255,0.95);\n" +
                "            padding: 20px;\n" +
                "            border-radius: 10px;\n" +
                "            margin-bottom: 20px;\n" +
                "            box-shadow: 0 4px 6px rgba(0,0,0,0.1);\n" +
                "            display: flex;\n" +
                "            justify-content: space-between;\n" +
                "            align-items: center;\n" +
                "        }\n" +
                "        h1 { color: #333; font-size: 24px; }\n" +
                "        .back-btn {\n" +
                "            background: white;\n" +
                "            border: 2px solid #9333ea;\n" +
                "            padding: 8px 16px;\n" +
                "            border-radius: 8px;\n" +
                "            cursor: pointer;\n" +
                "            text-decoration: none;\n" +
                "            color: #9333ea;\n" +
                "            font-weight: 600;\n" +
                "            transition: all 0.3s ease;\n" +
                "        }\n" +
                "        .back-btn:hover {\n" +
                "            background: #9333ea;\n" +
                "            color: white;\n" +
                "        }\n" +
                "        .settings-card {\n" +
                "            background: rgba(255,255,255,0.95);\n" +
                "            padding: 30px;\n" +
                "            border-radius: 10px;\n" +
                "            box-shadow: 0 4px 6px rgba(0,0,0,0.1);\n" +
                "            margin-bottom: 20px;\n" +
                "        }\n" +
                "        .settings-card h2 {\n" +
                "            color: #333;\n" +
                "            margin-bottom: 20px;\n" +
                "            font-size: 20px;\n" +
                "            border-bottom: 2px solid #9333ea;\n" +
                "            padding-bottom: 10px;\n" +
                "        }\n" +
                "        .form-group {\n" +
                "            margin-bottom: 20px;\n" +
                "        }\n" +
                "        .form-group label {\n" +
                "            display: block;\n" +
                "            margin-bottom: 8px;\n" +
                "            color: #555;\n" +
                "            font-weight: 600;\n" +
                "        }\n" +
                "        .form-group input {\n" +
                "            width: 100%;\n" +
                "            padding: 10px;\n" +
                "            border: 2px solid #ddd;\n" +
                "            border-radius: 6px;\n" +
                "            font-size: 14px;\n" +
                "            transition: border-color 0.3s;\n" +
                "        }\n" +
                "        .form-group input:focus {\n" +
                "            outline: none;\n" +
                "            border-color: #9333ea;\n" +
                "        }\n" +
                "        .endpoints-list {\n" +
                "            border: 2px solid #ddd;\n" +
                "            border-radius: 6px;\n" +
                "            padding: 10px;\n" +
                "            max-height: 300px;\n" +
                "            overflow-y: auto;\n" +
                "        }\n" +
                "        .endpoint-item {\n" +
                "            display: flex;\n" +
                "            justify-content: space-between;\n" +
                "            align-items: center;\n" +
                "            padding: 8px 10px;\n" +
                "            margin-bottom: 5px;\n" +
                "            background: #f8f9fa;\n" +
                "            border-radius: 4px;\n" +
                "        }\n" +
                "        .endpoint-item:last-child { margin-bottom: 0; }\n" +
                "        .endpoint-controls {\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            gap: 15px;\n" +
                "        }\n" +
                "        .auto-open-checkbox {\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            gap: 5px;\n" +
                "            font-size: 13px;\n" +
                "            color: #555;\n" +
                "        }\n" +
                "        .auto-open-checkbox input[type=\"checkbox\"] {\n" +
                "            width: 18px;\n" +
                "            height: 18px;\n" +
                "            cursor: pointer;\n" +
                "        }\n" +
                "        .delete-btn {\n" +
                "            background: #ef4444;\n" +
                "            color: white;\n" +
                "            border: none;\n" +
                "            padding: 5px 12px;\n" +
                "            border-radius: 4px;\n" +
                "            cursor: pointer;\n" +
                "            font-size: 12px;\n" +
                "            transition: background 0.3s;\n" +
                "        }\n" +
                "        .delete-btn:hover { background: #dc2626; }\n" +
                "        .add-endpoint {\n" +
                "            display: flex;\n" +
                "            gap: 10px;\n" +
                "            margin-top: 10px;\n" +
                "        }\n" +
                "        .add-endpoint input {\n" +
                "            flex: 1;\n" +
                "            padding: 8px;\n" +
                "            border: 2px solid #ddd;\n" +
                "            border-radius: 4px;\n" +
                "        }\n" +
                "        .add-btn, .save-btn {\n" +
                "            background: #9333ea;\n" +
                "            color: white;\n" +
                "            border: none;\n" +
                "            padding: 10px 24px;\n" +
                "            border-radius: 6px;\n" +
                "            cursor: pointer;\n" +
                "            font-size: 14px;\n" +
                "            font-weight: 600;\n" +
                "            transition: all 0.3s;\n" +
                "        }\n" +
                "        .add-btn:hover, .save-btn:hover {\n" +
                "            background: #7e22ce;\n" +
                "            transform: scale(1.05);\n" +
                "        }\n" +
                "        .save-btn {\n" +
                "            width: 100%;\n" +
                "            margin-top: 20px;\n" +
                "            font-size: 16px;\n" +
                "        }\n" +
                "        .message {\n" +
                "            padding: 15px;\n" +
                "            border-radius: 6px;\n" +
                "            margin-bottom: 20px;\n" +
                "            display: none;\n" +
                "        }\n" +
                "        .message.success {\n" +
                "            background: #d1fae5;\n" +
                "            border: 2px solid #10b981;\n" +
                "            color: #065f46;\n" +
                "        }\n" +
                "        .message.error {\n" +
                "            background: #fee2e2;\n" +
                "            border: 2px solid #ef4444;\n" +
                "            color: #991b1b;\n" +
                "        }\n" +
                "        .help-text {\n" +
                "            font-size: 12px;\n" +
                "            color: #777;\n" +
                "            margin-top: 5px;\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class='container'>\n" +
                "        <div class='header'>\n" +
                "            <h1>⚙️ Settings</h1>\n" +
                "            <a href='/' class='back-btn'>← Back to Dashboard</a>\n" +
                "        </div>\n" +
                "\n" +
                "        <div id='message' class='message'></div>\n" +
                "\n" +
                "        <div class='settings-card'>\n" +
                "            <h2>Network Configuration</h2>\n" +
                "            <div class='form-group'>\n" +
                "                <label for='ipStart'>IP Range Start</label>\n" +
                "                <input type='text' id='ipStart' placeholder='192.168.1.1'>\n" +
                "                <div class='help-text'>Start IP address for scanning</div>\n" +
                "            </div>\n" +
                "            <div class='form-group'>\n" +
                "                <label for='ipEnd'>IP Range End</label>\n" +
                "                <input type='text' id='ipEnd' placeholder='192.168.1.254'>\n" +
                "                <div class='help-text'>End IP address for scanning</div>\n" +
                "            </div>\n" +
                "            <div class='form-group'>\n" +
                "                <label for='port'>Port</label>\n" +
                "                <input type='text' id='port' placeholder='8080'>\n" +
                "                <div class='help-text'>Port number to check</div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "\n" +
                "        <div class='settings-card'>\n" +
                "            <h2>Health Check Endpoints</h2>\n" +
                "            <div class='form-group'>\n" +
                "                <label>Configured Endpoints</label>\n" +
                "                <div class='endpoints-list' id='endpointsList'>\n" +
                "                    <!-- Endpoints will be loaded here -->\n" +
                "                </div>\n" +
                "                <div class='add-endpoint'>\n" +
                "                    <input type='text' id='newEndpoint' placeholder='/api/health' onkeypress='if(event.key===\"Enter\") addEndpoint()'>\n" +
                "                    <button class='add-btn' onclick='addEndpoint()'>+ Add</button>\n" +
                "                </div>\n" +
                "                <div class='help-text'>Add endpoints to check. Use checkbox to show 'Open' button for each path in dashboard.</div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "\n" +
                "        <div class='settings-card'>\n" +
                "            <h2>Browser Settings</h2>\n" +
                "            <div class='form-group'>\n" +
                "                <label for='browserChoice'>Default Browser for Auto-Open</label>\n" +
                "                <select id='browserChoice' style='width: 100%; padding: 10px; border: 2px solid #ddd; border-radius: 6px; font-size: 14px;'>\n" +
                "                    <option value='default'>System Default Browser</option>\n" +
                "                    <option value='chrome'>Google Chrome</option>\n" +
                "                    <option value='firefox'>Mozilla Firefox</option>\n" +
                "                    <option value='edge'>Microsoft Edge</option>\n" +
                "                </select>\n" +
                "                <div class='help-text'>Select which browser to use for opening paths marked with auto-open checkbox</div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "\n" +
                "        <div style='display: flex; gap: 10px;'>\n" +
                "            <button class='save-btn' onclick='saveSettings()' style='flex: 1;'>💾 Save Settings</button>\n" +
                "            <button class='save-btn' onclick='applyNow()' style='flex: 1; background: #10b981;'>⚡ Apply Now</button>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <script>\n" +
                "        let endpoints = [];\n" +
                "\n" +
                "        async function loadConfig() {\n" +
                "            try {\n" +
                "                const response = await fetch('/api/config');\n" +
                "                const config = await response.json();\n" +
                "                \n" +
                "                document.getElementById('ipStart').value = config['network.ip.range.start'] || '';\n" +
                "                document.getElementById('ipEnd').value = config['network.ip.range.end'] || '';\n" +
                "                document.getElementById('port').value = config['network.port'] || '';\n" +
                "                document.getElementById('browserChoice').value = config['browser.choice'] || 'default';\n" +
                "                \n" +
                "                const checkPaths = config['check.paths'] || '';\n" +
                "                const autoOpenPaths = config['check.paths.autoopen'] || '';\n" +
                "                \n" +
                "                const paths = checkPaths ? checkPaths.split(',').map(s => s.trim()).filter(s => s) : [];\n" +
                "                const autoOpens = autoOpenPaths ? autoOpenPaths.split(',').map(s => s.trim()) : [];\n" +
                "                \n" +
                "                endpoints = paths.map(path => ({\n" +
                "                    path: path,\n" +
                "                    autoOpen: autoOpens.includes(path)\n" +
                "                }));\n" +
                "                \n" +
                "                renderEndpoints();\n" +
                "            } catch (error) {\n" +
                "                showMessage('Failed to load configuration: ' + error.message, 'error');\n" +
                "            }\n" +
                "        }\n" +
                "\n" +
                "        function renderEndpoints() {\n" +
                "            const list = document.getElementById('endpointsList');\n" +
                "            if (endpoints.length === 0) {\n" +
                "                list.innerHTML = '<div style=\"text-align:center;color:#999;padding:20px;\">No endpoints configured</div>';\n" +
                "                return;\n" +
                "            }\n" +
                "            list.innerHTML = endpoints.map((ep, idx) => {\n" +
                "                const epData = typeof ep === 'string' ? {path: ep, autoOpen: false} : ep;\n" +
                "                return `<div class='endpoint-item'>\n" +
                "                    <span>${epData.path}</span>\n" +
                "                    <div class='endpoint-controls'>\n" +
                "                        <label class='auto-open-checkbox'>\n" +
                "                            <input type='checkbox' ${epData.autoOpen ? 'checked' : ''} onchange='toggleAutoOpen(${idx})'>\n" +
                "                            🔘 Show Open button\n" +
                "                        </label>\n" +
                "                        <button class='delete-btn' onclick='removeEndpoint(${idx})'>✕ Delete</button>\n" +
                "                    </div>\n" +
                "                </div>`;\n" +
                "            }).join('');\n" +
                "        }\n" +
                "\n" +
                "        function addEndpoint() {\n" +
                "            const input = document.getElementById('newEndpoint');\n" +
                "            const value = input.value.trim();\n" +
                "            if (value) {\n" +
                "                const exists = endpoints.some(ep => ep.path === value);\n" +
                "                if (!exists) {\n" +
                "                    endpoints.push({path: value, autoOpen: false});\n" +
                "                    input.value = '';\n" +
                "                    renderEndpoints();\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "\n" +
                "        function removeEndpoint(index) {\n" +
                "            endpoints.splice(index, 1);\n" +
                "            renderEndpoints();\n" +
                "        }\n" +
                "\n" +
                "        function toggleAutoOpen(index) {\n" +
                "            endpoints[index].autoOpen = !endpoints[index].autoOpen;\n" +
                "        }\n" +
                "\n" +
                "        async function saveSettings() {\n" +
                "            const paths = endpoints.map(ep => ep.path).join(', ');\n" +
                "            const autoOpenPaths = endpoints.filter(ep => ep.autoOpen).map(ep => ep.path).join(', ');\n" +
                "            \n" +
                "            const config = {\n" +
                "                'network.ip.range.start': document.getElementById('ipStart').value.trim(),\n" +
                "                'network.ip.range.end': document.getElementById('ipEnd').value.trim(),\n" +
                "                'network.port': document.getElementById('port').value.trim(),\n" +
                "                'check.paths': paths,\n" +
                "                'check.paths.autoopen': autoOpenPaths,\n" +
                "                'browser.choice': document.getElementById('browserChoice').value\n" +
                "            };\n" +
                "\n" +
                "            try {\n" +
                "                const response = await fetch('/api/config', {\n" +
                "                    method: 'POST',\n" +
                "                    headers: { 'Content-Type': 'application/json' },\n" +
                "                    body: JSON.stringify(config)\n" +
                "                });\n" +
                "                \n" +
                "                const result = await response.json();\n" +
                "                if (result.success) {\n" +
                "                    showMessage(result.message, 'success');\n" +
                "                } else {\n" +
                "                    showMessage(result.message, 'error');\n" +
                "                }\n" +
                "            } catch (error) {\n" +
                "                showMessage('Failed to save settings: ' + error.message, 'error');\n" +
                "            }\n" +
                "        }\n" +
                "\n" +
                "        async function applyNow() {\n" +
                "            const config = {\n" +
                "                'network.ip.range.start': document.getElementById('ipStart').value.trim(),\n" +
                "                'network.ip.range.end': document.getElementById('ipEnd').value.trim(),\n" +
                "                'network.port': document.getElementById('port').value.trim(),\n" +
                "                'check.paths': endpoints.join(', ')\n" +
                "            };\n" +
                "\n" +
                "            try {\n" +
                "                // First save the configuration\n" +
                "                const saveResponse = await fetch('/api/config', {\n" +
                "                    method: 'POST',\n" +
                "                    headers: { 'Content-Type': 'application/json' },\n" +
                "                    body: JSON.stringify(config)\n" +
                "                });\n" +
                "                \n" +
                "                const saveResult = await saveResponse.json();\n" +
                "                if (!saveResult.success) {\n" +
                "                    showMessage(saveResult.message, 'error');\n" +
                "                    return;\n" +
                "                }\n" +
                "                \n" +
                "                // Then apply it immediately\n" +
                "                showMessage('Applying configuration...', 'success');\n" +
                "                \n" +
                "                const applyResponse = await fetch('/api/config/apply', {\n" +
                "                    method: 'POST'\n" +
                "                });\n" +
                "                \n" +
                "                const applyResult = await applyResponse.json();\n" +
                "                if (applyResult.success) {\n" +
                "                    showMessage(applyResult.message, 'success');\n" +
                "                } else {\n" +
                "                    showMessage(applyResult.message, 'error');\n" +
                "                }\n" +
                "            } catch (error) {\n" +
                "                showMessage('Failed to apply settings: ' + error.message, 'error');\n" +
                "            }\n" +
                "        }\n" +
                "\n" +
                "        function showMessage(text, type) {\n" +
                "            const msg = document.getElementById('message');\n" +
                "            msg.textContent = text;\n" +
                "            msg.className = 'message ' + type;\n" +
                "            msg.style.display = 'block';\n" +
                "            setTimeout(() => { msg.style.display = 'none'; }, 5000);\n" +
                "        }\n" +
                "\n" +
                "        // Load configuration on page load\n" +
                "        loadConfig();\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
    }
}
