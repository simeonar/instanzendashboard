package org.example.scanner;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.example.model.Instance;
import org.example.model.InstanceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

/**
 * HTTP and REST API health checker
 */
public class HealthChecker {
    private static final Logger logger = LoggerFactory.getLogger(HealthChecker.class);
    
    private final int httpTimeout;
    private final String healthCheckPath;
    private final int expectedStatusCode;
    private final boolean metadataEnabled;
    private final Map<String, String> metadataFieldMapping;
    private final Gson gson;

    public HealthChecker(int httpTimeout, String healthCheckPath, int expectedStatusCode, 
                        boolean metadataEnabled, Map<String, String> metadataFieldMapping) {
        this.httpTimeout = httpTimeout;
        this.healthCheckPath = healthCheckPath;
        this.expectedStatusCode = expectedStatusCode;
        this.metadataEnabled = metadataEnabled;
        this.metadataFieldMapping = metadataFieldMapping;
        this.gson = new Gson();
    }

    /**
     * Performs HTTP check on the instance
     */
    public void checkHttp(Instance instance, String path) {
        String url = "http://" + instance.getIpAddress() + ":" + instance.getPort() + path;
        
        try {
            long startTime = System.currentTimeMillis();
            HttpURLConnection connection = openConnection(url);
            
            int statusCode = connection.getResponseCode();
            long responseTime = System.currentTimeMillis() - startTime;
            
            instance.setHttpStatusCode(statusCode);
            instance.setResponseTimeMs(responseTime);
            
            if (statusCode >= 200 && statusCode < 300) {
                instance.setStatus(InstanceStatus.HTTP_OK);
                logger.debug("HTTP OK: {} returned {}", url, statusCode);
            } else {
                instance.setStatus(InstanceStatus.API_ERROR);
                instance.setErrorMessage("HTTP " + statusCode);
                logger.debug("HTTP Error: {} returned {}", url, statusCode);
            }
            
            connection.disconnect();
            
        } catch (IOException e) {
            instance.setStatus(InstanceStatus.PORT_OPEN);
            instance.setErrorMessage("HTTP connection failed: " + e.getMessage());
            logger.trace("HTTP failed for {}: {}", url, e.getMessage());
        }
    }

    /**
     * Performs REST API health check with metadata extraction
     */
    public void checkApiHealth(Instance instance) {
        String url = "http://" + instance.getIpAddress() + ":" + instance.getPort() + healthCheckPath;
        
        try {
            long startTime = System.currentTimeMillis();
            HttpURLConnection connection = openConnection(url);
            
            int statusCode = connection.getResponseCode();
            long responseTime = System.currentTimeMillis() - startTime;
            
            instance.setHttpStatusCode(statusCode);
            instance.setResponseTimeMs(responseTime);
            
            if (statusCode == expectedStatusCode) {
                // Read response body
                String responseBody = readResponse(connection);
                
                // Extract metadata if enabled
                if (metadataEnabled && responseBody != null && !responseBody.isEmpty()) {
                    extractMetadata(instance, responseBody);
                }
                
                instance.setStatus(InstanceStatus.API_HEALTHY);
                logger.debug("API Healthy: {} returned {} in {}ms", url, statusCode, responseTime);
                
            } else if (statusCode >= 200 && statusCode < 300) {
                instance.setStatus(InstanceStatus.HTTP_OK);
                logger.debug("API responded with non-expected status: {} returned {}", url, statusCode);
            } else {
                instance.setStatus(InstanceStatus.API_ERROR);
                instance.setErrorMessage("API Error: HTTP " + statusCode);
                logger.debug("API Error: {} returned {}", url, statusCode);
            }
            
            connection.disconnect();
            
        } catch (IOException e) {
            // API not available, but HTTP might work
            if (instance.getStatus() == InstanceStatus.HTTP_OK) {
                instance.setStatus(InstanceStatus.API_DEGRADED);
                instance.setErrorMessage("API not available");
            } else {
                instance.setStatus(InstanceStatus.PORT_OPEN);
                instance.setErrorMessage("API connection failed");
            }
            logger.trace("API health check failed for {}: {}", url, e.getMessage());
        }
    }

    /**
     * Opens HTTP connection with timeout
     */
    private HttpURLConnection openConnection(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(httpTimeout);
        connection.setReadTimeout(httpTimeout);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "InstanceDashboard/1.0");
        connection.setRequestProperty("Accept", "application/json, text/html");
        return connection;
    }

    /**
     * Reads response body from connection
     */
    private String readResponse(HttpURLConnection connection) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        } catch (IOException e) {
            logger.trace("Failed to read response body: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts metadata from JSON response
     */
    private void extractMetadata(Instance instance, String jsonResponse) {
        try {
            JsonObject jsonObject = gson.fromJson(jsonResponse, JsonObject.class);
            
            for (Map.Entry<String, String> entry : metadataFieldMapping.entrySet()) {
                String metadataKey = entry.getKey();
                String jsonField = entry.getValue();
                
                JsonElement element = jsonObject.get(jsonField);
                if (element != null && !element.isJsonNull()) {
                    String value = element.getAsString();
                    instance.addMetadata(metadataKey, value);
                    logger.trace("Extracted metadata: {}={} from field {}", metadataKey, value, jsonField);
                }
            }
            
        } catch (Exception e) {
            logger.trace("Failed to extract metadata: {}", e.getMessage());
        }
    }

    /**
     * Tries multiple paths to find a working HTTP endpoint
     * Returns the first working path or null if none work
     */
    public String tryMultiplePaths(Instance instance, String[] paths) {
        for (String path : paths) {
            try {
                String url = "http://" + instance.getIpAddress() + ":" + instance.getPort() + path;
                HttpURLConnection connection = openConnection(url);
                int statusCode = connection.getResponseCode();
                connection.disconnect();
                
                if (statusCode >= 200 && statusCode < 300) {
                    logger.debug("Found working path {} for {}", path, instance.getAddress());
                    return path;
                }
            } catch (IOException e) {
                logger.trace("Path {} failed for {}: {}", path, instance.getAddress(), e.getMessage());
            }
        }
        return null;
    }
    
    /**
     * Checks multiple paths and tries to extract metadata from each
     * Uses the best result found (preferring paths with metadata)
     */
    public void checkMultiplePathsWithMetadata(Instance instance, String[] paths) {
        InstanceStatus bestStatus = InstanceStatus.PORT_OPEN;
        String bestPath = null;
        
        for (String path : paths) {
            String url = "http://" + instance.getIpAddress() + ":" + instance.getPort() + path;
            
            try {
                long startTime = System.currentTimeMillis();
                HttpURLConnection connection = openConnection(url);
                
                int statusCode = connection.getResponseCode();
                long responseTime = System.currentTimeMillis() - startTime;
                
                if (statusCode >= 200 && statusCode < 300) {
                    instance.setHttpStatusCode(statusCode);
                    instance.setResponseTimeMs(responseTime);
                    
                    // Try to read response and extract metadata
                    String responseBody = readResponse(connection);
                    boolean hasMetadata = false;
                    
                    if (metadataEnabled && responseBody != null && !responseBody.isEmpty()) {
                        int metadataCount = instance.getMetadata().size();
                        extractMetadata(instance, responseBody);
                        hasMetadata = instance.getMetadata().size() > metadataCount;
                    }
                    
                    // Determine status priority: API_HEALTHY > HTTP_OK
                    if (hasMetadata && statusCode == expectedStatusCode) {
                        instance.setStatus(InstanceStatus.API_HEALTHY);
                        bestStatus = InstanceStatus.API_HEALTHY;
                        bestPath = path;
                        logger.debug("API Healthy found at {} ({}ms)", url, responseTime);
                        // Continue checking other paths
                    } else if (statusCode >= 200 && statusCode < 300) {
                        if (bestStatus != InstanceStatus.API_HEALTHY) {
                            instance.setStatus(InstanceStatus.HTTP_OK);
                            bestStatus = InstanceStatus.HTTP_OK;
                            bestPath = path;
                        }
                        logger.debug("HTTP OK at {}", url);
                    }
                }
                
                connection.disconnect();
                
            } catch (IOException e) {
                logger.trace("Failed to check {}: {}", url, e.getMessage());
            }
        }
        
        if (bestPath != null) {
            logger.info("Best path for {}: {} (status: {})", 
                instance.getAddress(), bestPath, instance.getStatus());
        } else {
            instance.setStatus(InstanceStatus.PORT_OPEN);
            instance.setErrorMessage("No HTTP paths responding");
        }
    }
}
