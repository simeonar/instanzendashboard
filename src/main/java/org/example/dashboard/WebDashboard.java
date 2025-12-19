package org.example.dashboard;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.example.config.ConfigManager;
import org.example.model.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
        server.createContext("/api/open", new OpenInBrowserHandler());
        
        // Pages
        server.createContext("/settings", new SettingsPageHandler());
        server.createContext("/", new DashboardHandler());
        
        server.setExecutor(null);
        server.start();
        
        logger.info("Web dashboard started at http://localhost:{}", port);
    }

    private static String readBody(InputStream input) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            bos.write(buffer, 0, read);
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static class OpenRequest {
        String url;
    }

    private class OpenInBrowserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            try {
                String body = readBody(exchange.getRequestBody());
                OpenRequest req = gson.fromJson(body, OpenRequest.class);
                if (req == null || req.url == null || req.url.trim().isEmpty()) {
                    throw new IllegalArgumentException("Missing url");
                }

                String url = req.url.trim();
                String browserChoice = configManager.getProperty("browser.choice", "default");
                openUrlInBrowser(browserChoice, url);

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Opened");

                String json = gson.toJson(result);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                byte[] response = json.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            } catch (Exception e) {
                logger.error("Failed to open URL", e);
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "Failed to open URL: " + e.getMessage());

                String json = gson.toJson(result);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                byte[] response = json.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            }
        }
    }

    private void openUrlInBrowser(String browserChoice, String url) throws Exception {
        java.net.URI uri = new java.net.URI(url);

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("URL scheme is missing");
        }
        String schemeLower = scheme.toLowerCase(java.util.Locale.ROOT);
        if (!"http".equals(schemeLower) && !"https".equals(schemeLower)) {
            throw new IllegalArgumentException("Only http/https URLs are allowed");
        }

        String normalized = (browserChoice == null ? "default" : browserChoice.trim().toLowerCase(java.util.Locale.ROOT));
        if (normalized.isEmpty() || "default".equals(normalized)) {
            java.awt.Desktop.getDesktop().browse(uri);
            return;
        }

        boolean isWindows = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");
        String cmd = null;
        if ("chrome".equals(normalized)) cmd = "chrome";
        if ("firefox".equals(normalized)) cmd = "firefox";
        if ("edge".equals(normalized)) cmd = "msedge";

        if (isWindows && cmd != null) {
            try {
                new ProcessBuilder(cmd, url).start();
                return;
            } catch (IOException ignored) {
                try {
                    new ProcessBuilder("cmd", "/c", "start", "", cmd, url).start();
                    return;
                } catch (IOException ignored2) {
                    // fall through
                }
            }
        }

        java.awt.Desktop.getDesktop().browse(uri);
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

            // Scan progress
            if (applicationManager != null) {
                stats.put("scanning", applicationManager.isScanning());
                org.example.scanner.ScanProgressSnapshot progress = applicationManager.getScanProgressSnapshot();
                if (progress != null) {
                    Map<String, Object> progressMap = new HashMap<>();
                    progressMap.put("inProgress", progress.isInProgress());
                    progressMap.put("startedAtEpochMs", progress.getStartedAtEpochMs());
                    progressMap.put("total", progress.getTotal());
                    progressMap.put("completed", progress.getCompleted());
                    progressMap.put("currentAddress", progress.getCurrentAddress());
                    progressMap.put("elapsedMs", progress.getElapsedMs(System.currentTimeMillis()));
                    stats.put("scanProgress", progressMap);
                }
            } else {
                stats.put("scanning", false);
            }
            
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
                "            font-family: Consolas, 'Courier New', monospace;\n" +
                "            background: radial-gradient(circle at top, #0b1220 0%, #05070d 60%, #02040a 100%);\n" +
                "            color: #c7f9cc;\n" +
                "            padding: 18px;\n" +
                "            min-height: 100vh;\n" +
                "        }\n" +
                "        .container {\n" +
                "            max-width: 1400px;\n" +
                "            margin: 0 auto;\n" +
                "        }\n" +
                "        .header {\n" +
                "            background: rgba(5, 10, 18, 0.85);\n" +
                "            padding: 22px;\n" +
                "            border-radius: 12px;\n" +
                "            border: 1px solid rgba(34, 197, 94, 0.25);\n" +
                "            box-shadow: 0 0 0 1px rgba(34, 197, 94, 0.08), 0 10px 30px rgba(0,0,0,0.55);\n" +
                "            margin-bottom: 16px;\n" +
                "        }\n" +
                "        h1 {\n" +
                "            color: #86efac;\n" +
                "            margin-bottom: 8px;\n" +
                "            letter-spacing: 0.5px;\n" +
                "        }\n" +
                "        .stats {\n" +
                "            display: grid;\n" +
                "            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));\n" +
                "            gap: 15px;\n" +
                "            margin-bottom: 20px;\n" +
                "        }\n" +
                "        .stat-card {\n" +
                "            background: rgba(5, 10, 18, 0.85);\n" +
                "            padding: 20px;\n" +
                "            border-radius: 12px;\n" +
                "            border: 1px solid rgba(34, 197, 94, 0.18);\n" +
                "            box-shadow: 0 0 0 1px rgba(34, 197, 94, 0.06), 0 10px 30px rgba(0,0,0,0.45);\n" +
                "            text-align: center;\n" +
                "        }\n" +
                "        .stat-card .label {\n" +
                "            font-size: 14px;\n" +
                "            color: rgba(199, 249, 204, 0.70);\n" +
                "            margin-bottom: 5px;\n" +
                "        }\n" +
                "        .stat-card .value {\n" +
                "            font-size: 32px;\n" +
                "            font-weight: bold;\n" +
                "        }\n" +
                "        .stat-card.healthy .value { color: #86efac; }\n" +
                "        .stat-card.http-ok .value { color: #a7f3d0; }\n" +
                "        .stat-card.degraded .value { color: #fde68a; }\n" +
                "        .stat-card.errors .value { color: #fca5a5; }\n" +
                "        .instances-container {\n" +
                "            background: rgba(5, 10, 18, 0.75);\n" +
                "            padding: 30px;\n" +
                "            border-radius: 12px;\n" +
                "            border: 1px solid rgba(34, 197, 94, 0.18);\n" +
                "            box-shadow: 0 0 0 1px rgba(34, 197, 94, 0.05), 0 10px 30px rgba(0,0,0,0.45);\n" +
                "        }\n" +
                "        .instance-card {\n" +
                "            border: 1px solid rgba(34, 197, 94, 0.22);\n" +
                "            border-radius: 8px;\n" +
                "            padding: 20px;\n" +
                "            margin-bottom: 15px;\n" +
                "            transition: all 0.3s ease;\n" +
                "        }\n" +
                "        .instance-card:hover {\n" +
                "            box-shadow: 0 0 0 1px rgba(34, 197, 94, 0.14), 0 12px 40px rgba(0,0,0,0.55);\n" +
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
                "            color: #c7f9cc;\n" +
                "        }\n" +
                "        .status-badge {\n" +
                "            padding: 6px 12px;\n" +
                "            border-radius: 20px;\n" +
                "            font-size: 12px;\n" +
                "            font-weight: 600;\n" +
                "            text-transform: uppercase;\n" +
                "        }\n" +
                "        .status-API_HEALTHY { background: rgba(34, 197, 94, 0.14); color: #86efac; border: 1px solid rgba(34, 197, 94, 0.35); }\n" +
                "        .status-HTTP_OK { background: rgba(16, 185, 129, 0.12); color: #a7f3d0; border: 1px solid rgba(16, 185, 129, 0.30); }\n" +
                "        .status-API_DEGRADED { background: rgba(245, 158, 11, 0.14); color: #fde68a; border: 1px solid rgba(245, 158, 11, 0.30); }\n" +
                "        .status-API_ERROR { background: rgba(239, 68, 68, 0.14); color: #fca5a5; border: 1px solid rgba(239, 68, 68, 0.30); }\n" +
                "        .status-PORT_OPEN { background: rgba(59, 130, 246, 0.12); color: #bfdbfe; border: 1px solid rgba(59, 130, 246, 0.28); }\n" +
                "        .status-UNREACHABLE { background: rgba(239, 68, 68, 0.10); color: rgba(252, 165, 165, 0.95); border: 1px solid rgba(239, 68, 68, 0.22); }\n" +
                "        .metadata {\n" +
                "            display: flex;\n" +
                "            gap: 20px;\n" +
                "            margin-bottom: 15px;\n" +
                "            color: rgba(199, 249, 204, 0.70);\n" +
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
                "            background: rgba(34, 197, 94, 0.06);\n" +
                "            padding: 10px;\n" +
                "            text-align: left;\n" +
                "            font-size: 12px;\n" +
                "            color: rgba(199, 249, 204, 0.75);\n" +
                "            font-weight: 600;\n" +
                "            text-transform: uppercase;\n" +
                "        }\n" +
                "        .paths-table td {\n" +
                "            padding: 12px 10px;\n" +
                "            border-top: 1px solid rgba(34, 197, 94, 0.14);\n" +
                "            font-size: 14px;\n" +
                "        }\n" +
                "        .path-link {\n" +
                "            color: #86efac;\n" +
                "            text-decoration: none;\n" +
                "            font-weight: 500;\n" +
                "            transition: color 0.2s;\n" +
                "        }\n" +
                "        .path-link:hover {\n" +
                "            color: #c7f9cc;\n" +
                "            text-decoration: underline;\n" +
                "        }\n" +
                "        .open-btn {\n" +
                "            background: rgba(34, 197, 94, 0.12);\n" +
                "            color: #c7f9cc;\n" +
                "            border: none;\n" +
                "            padding: 6px 12px;\n" +
                "            border-radius: 6px;\n" +
                "            cursor: pointer;\n" +
                "            font-size: 12px;\n" +
                "            transition: background 0.2s;\n" +
                "        }\n" +
                "        .open-btn:hover {\n" +
                "            background: rgba(34, 197, 94, 0.22);\n" +
                "        }\n" +
                "        .refresh-btn {\n" +
                "            background: rgba(34, 197, 94, 0.10);\n" +
                "            color: #c7f9cc;\n" +
                "            border: 1px solid rgba(34, 197, 94, 0.35);\n" +
                "            padding: 10px 18px;\n" +
                "            border-radius: 8px;\n" +
                "            cursor: pointer;\n" +
                "            font-size: 16px;\n" +
                "            font-weight: 600;\n" +
                "            transition: all 0.3s;\n" +
                "        }\n" +
                "        .refresh-btn:hover {\n" +
                "            background: rgba(34, 197, 94, 0.20);\n" +
                "            color: #d1fae5;\n" +
                "            transform: translateY(-1px);\n" +
                "        }\n" +
                "        .refresh-btn:active {\n" +
                "            transform: translateY(0) scale(0.98);\n" +
                "        }\n" +
                "        .last-update {\n" +
                "            text-align: center;\n" +
                "            color: rgba(199, 249, 204, 0.70);\n" +
                "            font-size: 14px;\n" +
                "            margin-top: 10px;\n" +
                "        }\n" +
                "        .loading {\n" +
                "            text-align: center;\n" +
                "            padding: 40px;\n" +
                "            color: rgba(199, 249, 204, 0.70);\n" +
                "        }\n" +
                "        details {\n" +
                "            margin-top: 10px;\n" +
                "        }\n" +
                "        details summary {\n" +
                "            cursor: pointer;\n" +
                "            padding: 10px;\n" +
                "            background: rgba(34, 197, 94, 0.06);\n" +
                "            border-radius: 6px;\n" +
                "            font-weight: 600;\n" +
                "            color: rgba(199, 249, 204, 0.90);\n" +
                "            transition: background 0.2s;\n" +
                "            user-select: none;\n" +
                "        }\n" +
                "        details summary:hover {\n" +
                "            background: rgba(34, 197, 94, 0.12);\n" +
                "        }\n" +
                "        details[open] summary {\n" +
                "            margin-bottom: 10px;\n" +
                "            background: rgba(34, 197, 94, 0.14);\n" +
                "        }\n" +
                "        .view-toggle {\n" +
                "            display: inline-flex;\n" +
                "            align-items: center;\n" +
                "            gap: 8px;\n" +
                "            margin-right: 10px;\n" +
                "            padding: 8px 14px;\n" +
                "            border-radius: 10px;\n" +
                "            border: 1px solid rgba(34, 197, 94, 0.35);\n" +
                "            background: rgba(34, 197, 94, 0.08);\n" +
                "            color: rgba(199, 249, 204, 0.95);\n" +
                "            font-weight: 600;\n" +
                "            cursor: pointer;\n" +
                "            user-select: none;\n" +
                "        }\n" +
                "        .instances-table {\n" +
                "            width: 100%;\n" +
                "            border-collapse: collapse;\n" +
                "            margin-top: 10px;\n" +
                "        }\n" +
                "        .instances-table th {\n" +
                "            background: #f9fafb;\n" +
                "            padding: 10px;\n" +
                "            text-align: left;\n" +
                "            font-size: 12px;\n" +
                "            color: #6b7280;\n" +
                "            font-weight: 700;\n" +
                "            text-transform: uppercase;\n" +
                "        }\n" +
                "        .instances-table td {\n" +
                "            padding: 12px 10px;\n" +
                "            border-top: 1px solid #e5e7eb;\n" +
                "            font-size: 14px;\n" +
                "            vertical-align: top;\n" +
                "        }\n" +
                "        .paths-summary {\n" +
                "            display: flex;\n" +
                "            flex-wrap: wrap;\n" +
                "            gap: 6px;\n" +
                "        }\n" +
                "        .paths-summary .status-badge {\n" +
                "            font-size: 11px;\n" +
                "            padding: 4px 8px;\n" +
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
                "                    <button class=\"view-toggle\" id=\"viewToggle\" onclick=\"toggleViewMode()\">View: Cards</button>\n" +
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
                "        let scanPollInterval = null;\n" +
                "        let viewMode = (localStorage.getItem('viewMode') || 'cards');\n" +
                "        let lastInstances = [];\n" +
                "\n" +
                "        const STATUS_LABELS = {\n" +
                "            'UNKNOWN': 'Unknown',\n" +
                "            'UNREACHABLE': 'Unreachable',\n" +
                "            'PORT_OPEN': 'Port Open',\n" +
                "            'HTTP_OK': 'HTTP OK',\n" +
                "            'API_HEALTHY': 'API Healthy',\n" +
                "            'API_DEGRADED': 'Degraded',\n" +
                "            'API_ERROR': 'Error',\n" +
                "            'TIMEOUT': 'Timeout'\n" +
                "        };\n" +
                "\n" +
                "        function formatStatusLabel(status) {\n" +
                "            return STATUS_LABELS[status] || status;\n" +
                "        }\n" +
                "\n" +
                "        function applyViewModeUi() {\n" +
                "            const toggle = document.getElementById('viewToggle');\n" +
                "            if (!toggle) return;\n" +
                "            toggle.textContent = (viewMode === 'table') ? 'View: Table' : 'View: Cards';\n" +
                "        }\n" +
                "\n" +
                "        function toggleViewMode() {\n" +
                "            viewMode = (viewMode === 'table') ? 'cards' : 'table';\n" +
                "            localStorage.setItem('viewMode', viewMode);\n" +
                "            applyViewModeUi();\n" +
                "            renderInstances(lastInstances);\n" +
                "        }\n" +
                "\n" +
                "        function statusRank(status) {\n" +
                "            switch (status) {\n" +
                "                case 'API_HEALTHY': return 0;\n" +
                "                case 'HTTP_OK': return 1;\n" +
                "                case 'API_DEGRADED': return 2;\n" +
                "                case 'PORT_OPEN': return 3;\n" +
                "                case 'API_ERROR': return 4;\n" +
                "                case 'TIMEOUT': return 5;\n" +
                "                case 'UNREACHABLE': return 6;\n" +
                "                case 'UNKNOWN': return 7;\n" +
                "                default: return 99;\n" +
                "            }\n" +
                "        }\n" +
                "\n" +
                "        function sortInstancesForReadability(instances) {\n" +
                "            return (instances || []).slice().sort((a, b) => {\n" +
                "                const ra = statusRank(a.status);\n" +
                "                const rb = statusRank(b.status);\n" +
                "                if (ra !== rb) return ra - rb;\n" +
                "                const aa = (a.ipAddress || '') + ':' + (a.port || '');\n" +
                "                const bb = (b.ipAddress || '') + ':' + (b.port || '');\n" +
                "                return aa.localeCompare(bb);\n" +
                "            });\n" +
                "        }\n" +
                "\n" +
                "        function renderInstances(instances) {\n" +
                "            const container = document.getElementById('instances');\n" +
                "            if (!container) return;\n" +
                "            lastInstances = sortInstancesForReadability(instances || []);\n" +
                "\n" +
                "            if (lastInstances.length === 0) {\n" +
                "                container.innerHTML = '<div class=\"loading\">No instances found</div>';\n" +
                "                return;\n" +
                "            }\n" +
                "\n" +
                "            if (viewMode === 'table') {\n" +
                "                container.innerHTML = renderInstancesTable(lastInstances);\n" +
                "            } else {\n" +
                "                container.innerHTML = renderInstancesCards(lastInstances);\n" +
                "            }\n" +
                "        }\n" +
                "\n" +
                "        function renderInstancesTable(instances) {\n" +
                "            let html = '<table class=\"instances-table\">';\n" +
                "            html += '<thead><tr><th>Address</th><th>Status</th><th>Paths</th><th>Metadata</th></tr></thead><tbody>';\n" +
                "            for (const instance of instances) {\n" +
                "                const metadata = instance.metadata || {};\n" +
                "                const paths = instance.pathResults || {};\n" +
                "\n" +
                "                const addr = `${instance.ipAddress}:${instance.port}`;\n" +
                "                const statusHtml = `<span class=\"status-badge status-${instance.status}\">${formatStatusLabel(instance.status)}</span>`;\n" +
                "\n" +
                "                let pathsHtml = '<div class=\"paths-summary\">';\n" +
                "                const entries = Object.entries(paths);\n" +
                "                if (entries.length === 0) {\n" +
                "                    pathsHtml += '<span style=\"color:#9ca3af\">No paths</span>';\n" +
                "                } else {\n" +
                "                    for (const [path, result] of entries) {\n" +
                "                        const label = path;\n" +
                "                        const badge = `<span class=\"status-badge status-${result.status}\" title=\"${label}\">${label} · ${formatStatusLabel(result.status)}</span>`;\n" +
                "                        pathsHtml += badge;\n" +
                "                    }\n" +
                "                }\n" +
                "                pathsHtml += '</div>';\n" +
                "\n" +
                "                let metaHtml = '';\n" +
                "                if (metadata.branch) metaHtml += `🌿 <strong>${metadata.branch}</strong> `;\n" +
                "                if (metadata.version) metaHtml += `📦 <strong>${metadata.version}</strong> `;\n" +
                "                if (metadata.commit) metaHtml += `💾 <strong>${metadata.commit}</strong>`;\n" +
                "                if (!metaHtml) metaHtml = '<span style=\"color:#9ca3af\">-</span>';\n" +
                "\n" +
                "                html += '<tr>';\n" +
                "                html += `<td><strong>${addr}</strong></td>`;\n" +
                "                html += `<td>${statusHtml}</td>`;\n" +
                "                html += `<td>${pathsHtml}</td>`;\n" +
                "                html += `<td>${metaHtml}</td>`;\n" +
                "                html += '</tr>';\n" +
                "            }\n" +
                "            html += '</tbody></table>';\n" +
                "            return html;\n" +
                "        }\n" +
                "\n" +
                "        function renderInstancesCards(instances) {\n" +
                "            return instances.map(instance => {\n" +
                "                const metadata = instance.metadata || {};\n" +
                "                const paths = instance.pathResults || {};\n" +
                "\n" +
                "                let metadataHtml = '';\n" +
                "                if (metadata.branch || metadata.version || metadata.commit) {\n" +
                "                    metadataHtml = '<div class=\"metadata\">';\n" +
                "                    if (metadata.branch) metadataHtml += '<span>🌿 Branch: <strong>' + metadata.branch + '</strong></span>';\n" +
                "                    if (metadata.version) metadataHtml += '<span>📦 Version: <strong>' + metadata.version + '</strong></span>';\n" +
                "                    if (metadata.commit) metadataHtml += '<span>💾 Commit: <strong>' + metadata.commit + '</strong></span>';\n" +
                "                    metadataHtml += '</div>';\n" +
                "                }\n" +
                "\n" +
                "                let pathsHtml = '';\n" +
                "                if (Object.keys(paths).length > 0) {\n" +
                "                    pathsHtml = '<details><summary>📋 Scan Details (' + Object.keys(paths).length + ' paths)</summary>';\n" +
                "                    pathsHtml += '<table class=\"paths-table\"><thead><tr><th>Path</th><th>Status</th><th>HTTP Code</th><th>Response Time</th><th>Action</th></tr></thead><tbody>';\n" +
                "                    for (const [path, result] of Object.entries(paths)) {\n" +
                "                        const url = 'http://' + instance.ipAddress + ':' + instance.port + path;\n" +
                "                        const showOpenButton = pathsWithOpenButton.includes(path);\n" +
                "                        pathsHtml += '<tr>';\n" +
                "                        pathsHtml += '<td><a href=\"' + url + '\" target=\"_blank\" class=\"path-link\">' + path + '</a></td>';\n" +
                "                        pathsHtml += '<td><span class=\"status-badge status-' + result.status + '\">' + formatStatusLabel(result.status) + '</span></td>';\n" +
                "                        pathsHtml += '<td>' + (result.httpStatusCode || '-') + '</td>';\n" +
                "                        pathsHtml += '<td>' + (result.responseTimeMs >= 0 ? result.responseTimeMs + 'ms' : 'N/A') + '</td>';\n" +
                "                        pathsHtml += '<td>';\n" +
                "                        if (showOpenButton) {\n" +
                "                            pathsHtml += '<button class=\"open-btn\" onclick=\"openInBrowser(\\'' + url + '\\')\">Open</button>';\n" +
                "                        } else {\n" +
                "                            pathsHtml += '-';\n" +
                "                        }\n" +
                "                        pathsHtml += '</td>';\n" +
                "                        pathsHtml += '</tr>';\n" +
                "                    }\n" +
                "                    pathsHtml += '</tbody></table></details>';\n" +
                "                }\n" +
                "\n" +
                "                return `\n" +
                "                    <div class=\"instance-card\">\n" +
                "                        <div class=\"instance-header\">\n" +
                "                            <div class=\"instance-title\">${instance.ipAddress}:${instance.port}</div>\n" +
                "                            <span class=\"status-badge status-${instance.status}\">${formatStatusLabel(instance.status)}</span>\n" +
                "                        </div>\n" +
                "                        ${metadataHtml}\n" +
                "                        ${pathsHtml}\n" +
                "                    </div>\n" +
                "                `;\n" +
                "            }).join('');\n" +
                "        }\n" +
                "\n" +
                "        function updateScanUiFromStats(stats) {\n" +
                "            const btn = document.getElementById('scanBtn');\n" +
                "            const statusEl = document.getElementById('scanStatus');\n" +
                "            if (!btn || !statusEl) return;\n" +
                "            const scanning = !!(stats && stats.scanning);\n" +
                "            const progress = stats && stats.scanProgress ? stats.scanProgress : null;\n" +
                "\n" +
                "            if (scanning) {\n" +
                "                const total = progress && progress.total ? progress.total : 0;\n" +
                "                const completed = progress && progress.completed ? progress.completed : 0;\n" +
                "                const current = progress && progress.currentAddress ? progress.currentAddress : '';\n" +
                "                const percent = total > 0 ? Math.floor((completed * 100) / total) : 0;\n" +
                "                let text = total > 0 ? (`Scanning: ${completed}/${total} (${percent}%)`) : 'Scanning...';\n" +
                "                if (current) text += ' | ' + current;\n" +
                "                statusEl.textContent = text;\n" +
                "                statusEl.style.color = '#f59e0b';\n" +
                "                btn.disabled = true;\n" +
                "                btn.style.opacity = '0.5';\n" +
                "                return;\n" +
                "            }\n" +
                "\n" +
                "            if (progress && progress.total && progress.completed >= progress.total) {\n" +
                "                statusEl.textContent = 'Scan completed';\n" +
                "                statusEl.style.color = '#86efac';\n" +
                "            }\n" +
                "\n" +
                "            btn.disabled = false;\n" +
                "            btn.style.opacity = '1';\n" +
                "\n" +
                "            if (scanPollInterval) {\n" +
                "                clearInterval(scanPollInterval);\n" +
                "                scanPollInterval = null;\n" +
                "            }\n" +
                "        }\n" +
                "\n" +
                "        function fetchStats() {\n" +
                "            fetch('/api/stats')\n" +
                "                .then(r => r.json())\n" +
                "                .then(stats => {\n" +
                "                    document.getElementById('totalInstances').textContent = stats.total || 0;\n" +
                "                    document.getElementById('httpOkInstances').textContent = stats.httpOk || 0;\n" +
                "                    document.getElementById('degradedInstances').textContent = stats.degraded || 0;\n" +
                "                    document.getElementById('errorInstances').textContent = stats.errors || 0;\n" +
                "\n" +
                "                    if (stats.pathsWithOpenButton) {\n" +
                "                        pathsWithOpenButton = stats.pathsWithOpenButton.split(',').map(s => s.trim()).filter(s => s);\n" +
                "                    } else {\n" +
                "                        pathsWithOpenButton = [];\n" +
                "                    }\n" +
                "\n" +
                "                    const lastUpdateEl = document.getElementById('lastUpdate');\n" +
                "                    if (lastUpdateEl) {\n" +
                "                        if (stats.lastUpdate && stats.lastUpdate > 0) {\n" +
                "                            lastUpdateEl.textContent = 'Last update: ' + new Date(stats.lastUpdate).toLocaleString();\n" +
                "                        } else {\n" +
                "                            lastUpdateEl.textContent = 'No scans yet';\n" +
                "                        }\n" +
                "                    }\n" +
                "\n" +
                "                    updateScanUiFromStats(stats);\n" +
                "                })\n" +
                "                .catch(err => {\n" +
                "                    console.error('Error fetching stats:', err);\n" +
                "                });\n" +
                "        }\n" +
                "\n" +
                "        function fetchInstances() {\n" +
                "            fetch('/api/instances')\n" +
                "                .then(r => r.json())\n" +
                "                .then(instances => {\n" +
                "                    renderInstances(instances);\n" +
                "                })\n" +
                "                .catch(err => {\n" +
                "                    console.error('Error fetching instances:', err);\n" +
                "                    const container = document.getElementById('instances');\n" +
                "                    if (container) container.innerHTML = '<div class=\"loading\">Error loading instances</div>';\n" +
                "                });\n" +
                "        }\n" +
                "\n" +
                "        function refresh() {\n" +
                "            fetchStats();\n" +
                "            fetchInstances();\n" +
                "        }\n" +
                "\n" +
                "        function openInBrowser(url) {\n" +
                "            // Note: This opens a browser on the SERVER machine.\n" +
                "            fetch('/api/open', {\n" +
                "                method: 'POST',\n" +
                "                headers: { 'Content-Type': 'application/json' },\n" +
                "                body: JSON.stringify({ url: url })\n" +
                "            })\n" +
                "            .then(r => r.json())\n" +
                "            .then(res => {\n" +
                "                if (!res || !res.success) {\n" +
                "                    window.open(url, '_blank');\n" +
                "                }\n" +
                "            })\n" +
                "            .catch(() => window.open(url, '_blank'));\n" +
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
                "                        statusEl.textContent = 'Scanning...';\n" +
                "                        statusEl.style.color = '#f59e0b';\n" +
                "                        // Poll while scan is in progress\n" +
                "                        if (scanPollInterval) clearInterval(scanPollInterval);\n" +
                "                        scanPollInterval = setInterval(() => {\n" +
                "                            refresh();\n" +
                "                        }, 1000);\n" +
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
                "        applyViewModeUi();\n" +
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
                "            font-family: Consolas, 'Courier New', monospace;\n" +
                "            background: radial-gradient(circle at top, #0b1220 0%, #05070d 60%, #02040a 100%);\n" +
                "            color: #c7f9cc;\n" +
                "            min-height: 100vh;\n" +
                "            padding: 18px;\n" +
                "        }\n" +
                "        .container {\n" +
                "            max-width: 900px;\n" +
                "            margin: 0 auto;\n" +
                "        }\n" +
                "        .header {\n" +
                "            background: rgba(5, 10, 18, 0.85);\n" +
                "            padding: 18px;\n" +
                "            border-radius: 12px;\n" +
                "            margin-bottom: 16px;\n" +
                "            border: 1px solid rgba(34, 197, 94, 0.25);\n" +
                "            box-shadow: 0 0 0 1px rgba(34, 197, 94, 0.08), 0 10px 30px rgba(0,0,0,0.55);\n" +
                "            display: flex;\n" +
                "            justify-content: space-between;\n" +
                "            align-items: center;\n" +
                "        }\n" +
                "        h1 { color: #86efac; font-size: 22px; letter-spacing: 0.5px; }\n" +
                "        .back-btn {\n" +
                "            background: rgba(34, 197, 94, 0.10);\n" +
                "            border: 1px solid rgba(34, 197, 94, 0.35);\n" +
                "            padding: 8px 14px;\n" +
                "            border-radius: 10px;\n" +
                "            cursor: pointer;\n" +
                "            text-decoration: none;\n" +
                "            color: #c7f9cc;\n" +
                "            font-weight: 700;\n" +
                "            transition: all 0.2s ease;\n" +
                "        }\n" +
                "        .back-btn:hover {\n" +
                "            background: rgba(34, 197, 94, 0.20);\n" +
                "            transform: translateY(-1px);\n" +
                "        }\n" +
                "        .settings-card {\n" +
                "            background: rgba(5, 10, 18, 0.85);\n" +
                "            padding: 26px;\n" +
                "            border-radius: 12px;\n" +
                "            border: 1px solid rgba(34, 197, 94, 0.18);\n" +
                "            box-shadow: 0 0 0 1px rgba(34, 197, 94, 0.06), 0 10px 30px rgba(0,0,0,0.45);\n" +
                "            margin-bottom: 16px;\n" +
                "        }\n" +
                "        .settings-card h2 {\n" +
                "            color: rgba(199, 249, 204, 0.95);\n" +
                "            margin-bottom: 18px;\n" +
                "            font-size: 18px;\n" +
                "            border-bottom: 1px solid rgba(34, 197, 94, 0.25);\n" +
                "            padding-bottom: 10px;\n" +
                "            letter-spacing: 0.4px;\n" +
                "        }\n" +
                "        .form-group {\n" +
                "            margin-bottom: 20px;\n" +
                "        }\n" +
                "        .form-group label {\n" +
                "            display: block;\n" +
                "            margin-bottom: 8px;\n" +
                "            color: rgba(199, 249, 204, 0.85);\n" +
                "            font-weight: 600;\n" +
                "        }\n" +
                "        .form-group input, .form-group select {\n" +
                "            width: 100%;\n" +
                "            padding: 10px;\n" +
                "            border: 1px solid rgba(34, 197, 94, 0.22);\n" +
                "            border-radius: 10px;\n" +
                "            font-size: 14px;\n" +
                "            background: rgba(2, 4, 10, 0.75);\n" +
                "            color: #c7f9cc;\n" +
                "            transition: border-color 0.3s;\n" +
                "        }\n" +
                "        .form-group input:focus, .form-group select:focus {\n" +
                "            outline: none;\n" +
                "            border-color: rgba(34, 197, 94, 0.55);\n" +
                "        }\n" +
                "        .endpoints-list {\n" +
                "            border: 1px solid rgba(34, 197, 94, 0.22);\n" +
                "            border-radius: 12px;\n" +
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
                "            background: rgba(34, 197, 94, 0.06);\n" +
                "            border: 1px solid rgba(34, 197, 94, 0.12);\n" +
                "            border-radius: 10px;\n" +
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
                "            color: rgba(199, 249, 204, 0.75);\n" +
                "        }\n" +
                "        .auto-open-checkbox input[type=\"checkbox\"] {\n" +
                "            width: 18px;\n" +
                "            height: 18px;\n" +
                "            cursor: pointer;\n" +
                "        }\n" +
                "        .delete-btn {\n" +
                "            background: rgba(239, 68, 68, 0.14);\n" +
                "            color: rgba(252, 165, 165, 0.95);\n" +
                "            border: none;\n" +
                "            padding: 5px 12px;\n" +
                "            border-radius: 10px;\n" +
                "            cursor: pointer;\n" +
                "            font-size: 12px;\n" +
                "            transition: background 0.3s;\n" +
                "        }\n" +
                "        .delete-btn:hover { background: rgba(239, 68, 68, 0.22); }\n" +
                "        .add-endpoint {\n" +
                "            display: flex;\n" +
                "            gap: 10px;\n" +
                "            margin-top: 10px;\n" +
                "        }\n" +
                "        .add-endpoint input {\n" +
                "            flex: 1;\n" +
                "            padding: 8px;\n" +
                "            border: 1px solid rgba(34, 197, 94, 0.22);\n" +
                "            border-radius: 10px;\n" +
                "            background: rgba(2, 4, 10, 0.75);\n" +
                "            color: #c7f9cc;\n" +
                "        }\n" +
                "        .add-btn, .save-btn {\n" +
                "            background: rgba(34, 197, 94, 0.12);\n" +
                "            color: #c7f9cc;\n" +
                "            border: none;\n" +
                "            padding: 10px 24px;\n" +
                "            border-radius: 10px;\n" +
                "            border: 1px solid rgba(34, 197, 94, 0.35);\n" +
                "            cursor: pointer;\n" +
                "            font-size: 14px;\n" +
                "            font-weight: 600;\n" +
                "            transition: all 0.3s;\n" +
                "        }\n" +
                "        .add-btn:hover, .save-btn:hover {\n" +
                "            background: rgba(34, 197, 94, 0.22);\n" +
                "            transform: translateY(-1px);\n" +
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
                "            background: rgba(34, 197, 94, 0.10);\n" +
                "            border: 1px solid rgba(34, 197, 94, 0.35);\n" +
                "            color: #c7f9cc;\n" +
                "        }\n" +
                "        .message.error {\n" +
                "            background: rgba(239, 68, 68, 0.12);\n" +
                "            border: 1px solid rgba(239, 68, 68, 0.30);\n" +
                "            color: rgba(252, 165, 165, 0.95);\n" +
                "        }\n" +
                "        .help-text {\n" +
                "            font-size: 12px;\n" +
                "            color: rgba(199, 249, 204, 0.65);\n" +
                "            margin-top: 6px;\n" +
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
                "                <select id='browserChoice'>\n" +
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
                "            <button class='save-btn' onclick='applyNow()' style='flex: 1; background: rgba(34, 197, 94, 0.22);'>⚡ Apply Now</button>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <script>\n" +
                "        let endpoints = [];\n" +
                "\n" +
                "        function normalizePath(p) {\n" +
                "            if (p === null || p === undefined) return null;\n" +
                "            let s = String(p).trim();\n" +
                "            if (!s) return null;\n" +
                "            if (s === '[object Object]') return null;\n" +
                "            if (!s.startsWith('/')) s = '/' + s;\n" +
                "            return s;\n" +
                "        }\n" +
                "\n" +
                "        function normalizeEndpoints(list) {\n" +
                "            const seen = {};\n" +
                "            const out = [];\n" +
                "            (list || []).forEach(item => {\n" +
                "                const obj = (typeof item === 'string') ? { path: item, autoOpen: false } : item;\n" +
                "                const path = normalizePath(obj && obj.path);\n" +
                "                if (!path) return;\n" +
                "                const autoOpen = !!(obj && obj.autoOpen);\n" +
                "                if (seen[path]) {\n" +
                "                    const existing = out.find(e => e.path === path);\n" +
                "                    if (existing) existing.autoOpen = existing.autoOpen || autoOpen;\n" +
                "                    return;\n" +
                "                }\n" +
                "                seen[path] = true;\n" +
                "                out.push({ path: path, autoOpen: autoOpen });\n" +
                "            });\n" +
                "            return out;\n" +
                "        }\n" +
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
                "                const autoOpens = autoOpenPaths ? autoOpenPaths.split(',').map(s => s.trim()).filter(s => s) : [];\n" +
                "                \n" +
                "                endpoints = normalizeEndpoints(paths.map(path => ({\n" +
                "                    path: path,\n" +
                "                    autoOpen: autoOpens.includes(path)\n" +
                "                })));\n" +
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
                "                list.innerHTML = '<div style=\"text-align:center;color:rgba(199, 249, 204, 0.60);padding:20px;\">No endpoints configured</div>';\n" +
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
                "            const value = normalizePath(input.value);\n" +
                "            if (!value) return;\n" +
                "            endpoints = normalizeEndpoints(endpoints.concat([{ path: value, autoOpen: false }]));\n" +
                "            input.value = '';\n" +
                "            renderEndpoints();\n" +
                "        }\n" +
                "\n" +
                "        function removeEndpoint(index) {\n" +
                "            endpoints.splice(index, 1);\n" +
                "            renderEndpoints();\n" +
                "        }\n" +
                "\n" +
                "        function toggleAutoOpen(index) {\n" +
                "            endpoints[index].autoOpen = !endpoints[index].autoOpen;\n" +
                "            renderEndpoints();\n" +
                "        }\n" +
                "\n" +
                "        async function saveSettings() {\n" +
                "            endpoints = normalizeEndpoints(endpoints);\n" +
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
                "            endpoints = normalizeEndpoints(endpoints);\n" +
                "            const paths = endpoints.map(ep => ep.path).join(', ');\n" +
                "            const autoOpenPaths = endpoints.filter(ep => ep.autoOpen).map(ep => ep.path).join(', ');\n" +
                "\n" +
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
