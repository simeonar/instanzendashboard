# Example REST API Health Endpoint

This is an example of what your application's REST API health endpoint should return.

## Endpoint: `/api/health`

### Response Format

```json
{
  "status": "healthy",
  "branch": "feature/user-authentication",
  "version": "2.1.0-SNAPSHOT",
  "commit": "a7b3c9f",
  "deployedAt": "2025-12-16T10:30:45Z",
  "uptime": 3600,
  "services": {
    "database": "connected",
    "cache": "connected"
  }
}
```

### Minimum Required Fields

For the Instance Dashboard to extract metadata, your API should at least return:

```json
{
  "branch": "main",
  "version": "1.0.0",
  "commit": "abc123",
  "status": "healthy"
}
```

## Configuration Mapping

Map your JSON fields to the dashboard in `application.properties`:

```properties
metadata.branch.field=branch
metadata.version.field=version
metadata.commit.field=commit
metadata.timestamp.field=deployedAt
metadata.status.field=status
```

## Example Implementation (Spring Boot)

```java
@RestController
@RequestMapping("/api")
public class HealthController {
    
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "healthy");
        health.put("branch", System.getenv("GIT_BRANCH"));
        health.put("version", System.getenv("APP_VERSION"));
        health.put("commit", System.getenv("GIT_COMMIT"));
        health.put("deployedAt", Instant.now().toString());
        return health;
    }
}
```

## Testing Your Endpoint

Test your health endpoint with curl:

```bash
curl http://YOUR_INSTANCE_IP:YOUR_PORT/api/health
```

Expected response should be valid JSON with at least the branch/version information.
