package org.example.dashboard;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
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
    private final Gson gson;
    private HttpServer server;

    public WebDashboard(int port, DashboardManager dashboardManager) {
        this.port = port;
        this.dashboardManager = dashboardManager;
        this.gson = new Gson();
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // API endpoints
        server.createContext("/api/instances", new InstancesHandler());
        server.createContext("/api/stats", new StatsHandler());
        
        // Static content
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
            stats.put("healthy", dashboardManager.getHealthyInstances());
            stats.put("httpOk", dashboardManager.getHttpOkInstances());
            stats.put("degraded", dashboardManager.getDegradedInstances());
            stats.put("errors", dashboardManager.getErrorInstances());
            stats.put("lastUpdate", dashboardManager.getLastUpdateTime());

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

    /**
     * Handler for main dashboard HTML page
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
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <div class=\"header\">\n" +
                "            <h1>🖥️ Instance Dashboard</h1>\n" +
                "            <div class=\"last-update\" id=\"lastUpdate\">Loading...</div>\n" +
                "        </div>\n" +
                "\n" +
                "        <div class=\"stats\" id=\"stats\">\n" +
                "            <div class=\"stat-card\">\n" +
                "                <div class=\"label\">Total Instances</div>\n" +
                "                <div class=\"value\" id=\"totalInstances\">0</div>\n" +
                "            </div>\n" +
                "            <div class=\"stat-card healthy\">\n" +
                "                <div class=\"label\">API Healthy</div>\n" +
                "                <div class=\"value\" id=\"healthyInstances\">0</div>\n" +
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
                "        function fetchStats() {\n" +
                "            fetch('/api/stats')\n" +
                "                .then(response => response.json())\n" +
                "                .then(stats => {\n" +
                "                    document.getElementById('totalInstances').textContent = stats.total;\n" +
                "                    document.getElementById('healthyInstances').textContent = stats.healthy;\n" +
                "                    document.getElementById('httpOkInstances').textContent = stats.httpOk;\n" +
                "                    document.getElementById('degradedInstances').textContent = stats.degraded;\n" +
                "                    document.getElementById('errorInstances').textContent = stats.errors;\n" +
                "                    \n" +
                "                    const date = new Date(stats.lastUpdate);\n" +
                "                    document.getElementById('lastUpdate').textContent = 'Last Update: ' + date.toLocaleString();\n" +
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
                "                            pathsHtml = '<table class=\"paths-table\"><thead><tr><th>Path</th><th>Status</th><th>HTTP Code</th><th>Response Time</th><th>Action</th></tr></thead><tbody>';\n" +
                "                            for (const [path, result] of Object.entries(paths)) {\n" +
                "                                const url = 'http://' + instance.ipAddress + ':' + instance.port + path;\n" +
                "                                pathsHtml += '<tr>';\n" +
                "                                pathsHtml += '<td><a href=\"' + url + '\" target=\"_blank\" class=\"path-link\">' + path + '</a></td>';\n" +
                "                                pathsHtml += '<td><span class=\"status-badge status-' + result.status + '\">' + result.status + '</span></td>';\n" +
                "                                pathsHtml += '<td>' + (result.httpStatusCode || '-') + '</td>';\n" +
                "                                pathsHtml += '<td>' + (result.responseTimeMs >= 0 ? result.responseTimeMs + 'ms' : 'N/A') + '</td>';\n" +
                "                                pathsHtml += '<td><button class=\"open-btn\" onclick=\"window.open(\\'' + url + '\\', \\'_blank\\')\">Open</button></td>';\n" +
                "                                pathsHtml += '</tr>';\n" +
                "                            }\n" +
                "                            pathsHtml += '</tbody></table>';\n" +
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
                "        // Initial load\n" +
                "        refresh();\n" +
                "\n" +
                "        // Auto-refresh every 5 seconds\n" +
                "        setInterval(refresh, 5000);\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
    }
}
