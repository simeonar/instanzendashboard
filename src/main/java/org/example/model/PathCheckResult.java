package org.example.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Result of checking a specific path on an instance
 */
public class PathCheckResult {
    private final String path;
    private InstanceStatus status;
    private int httpStatusCode;
    private long responseTimeMs;
    private String errorMessage;
    private Map<String, String> metadata;

    public PathCheckResult(String path) {
        this.path = path;
        this.status = InstanceStatus.UNKNOWN;
        this.metadata = new HashMap<>();
    }

    public String getPath() {
        return path;
    }

    public InstanceStatus getStatus() {
        return status;
    }

    public void setStatus(InstanceStatus status) {
        this.status = status;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    public void setHttpStatusCode(int httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }

    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void addMetadata(String key, String value) {
        this.metadata.put(key, value);
    }

    public boolean isHealthy() {
        return status == InstanceStatus.API_HEALTHY || status == InstanceStatus.HTTP_OK;
    }

    public boolean hasMetadata() {
        return !metadata.isEmpty();
    }

    @Override
    public String toString() {
        return String.format("PathCheckResult{path='%s', status=%s, httpCode=%d, responseTime=%dms, metadata=%d}",
                path, status, httpStatusCode, responseTimeMs, metadata.size());
    }
}
