# Instance Dashboard

A Java 8 application for monitoring network instances with multi-level health checking and metadata extraction.

## Features

- 🔍 **Multi-level Health Checking**: TCP → HTTP → REST API validation
- 📊 **Rich Status Display**: 8 different instance states with color coding
- 🏷️ **Metadata Extraction**: Automatically extracts branch, version, commit info from API
- ⚡ **Multi-threaded Scanning**: Parallel checks for optimal performance
- ⏱️ **Network-Friendly**: Configurable scan intervals to minimize load
- 🎨 **Enhanced Console UI**: Detailed or simple view modes
- ⚙️ **Highly Configurable**: All settings via properties file
- 🔄 **Real-time Updates**: Periodic scanning with live dashboard

## Prerequisites

- Java 8 or higher
- Maven 3.x

## Configuration

1. Copy the template configuration file:
   ```bash
   cp src/main/resources/application.properties.template src/main/resources/application.properties
   ```

2. Edit `src/main/resources/application.properties` with your settings:
   ```properties
   # Network scan settings
   network.ip.range.start=192.168.0.1
   network.ip.range.end=192.168.0.254
   network.port=8080

   # Scan interval in seconds
   scan.interval.seconds=60
   connection.timeout.ms=2000

   # HTTP/REST API health check
   health.check.enabled=true
   health.check.path=/api/health
   health.check.timeout.ms=3000
   health.check.expected.status=200

   # Metadata extraction from API response
   metadata.enabled=true
   metadata.branch.field=branch
   metadata.version.field=version
   metadata.commit.field=commit

   # Alternative paths to check
   check.paths=/,/index.html,/health

   # Dashboard settings
   dashboard.refresh.seconds=30
   dashboard.title=Instance Dashboard
   dashboard.show.metadata=true
   dashboard.filter.unreachable=false
   ```

## Instance Status Levels

The dashboard uses a sophisticated multi-level checking system:

| Status | Icon | Description |
|--------|------|-------------|
| **API Healthy** | ✓ | REST API responding correctly with metadata |
| **HTTP OK** | ◉ | Web interface accessible but API unavailable |
| **API Degraded** | ⚠ | Web works but API not responding |
| **API Error** | ✗ | API responding with error status |
| **Port Open** | ○ | TCP port open but HTTP not responding |
| **Unreachable** | ✗ | Port is not accessible |
| **Timeout** | ⏱ | Request timed out |

## Metadata Extraction

When `metadata.enabled=true`, the dashboard extracts information from REST API JSON responses:

```json
{
  "branch": "feature/new-api",
  "version": "1.2.3",
  "commit": "a1b2c3d",
  "deployedAt": "2025-12-16T10:30:00Z",
  "status": "healthy"
}
```

This metadata is displayed in the dashboard for easy identification of running branches.

## Build

```bash
mvn clean package
```

This will create two JAR files in the `target` directory:
- `InstanzenDashboard-1.0-SNAPSHOT.jar` - Basic JAR
- `InstanzenDashboard-1.0-SNAPSHOT-jar-with-dependencies.jar` - Fat JAR with all dependencies

## Run

### Using Maven:
```bash
mvn exec:java -Dexec.mainClass="org.example.Main"
```

### Using the Fat JAR:
```bash
java -jar target/InstanzenDashboard-1.0-SNAPSHOT-jar-with-dependencies.jar
```

## Project Structure

```
src/
├── main/
│   ├── java/
│   │   └── org/example/
│   │       ├── Main.java                         # Application entry point
│   │       ├── config/
│   │       │   └── ConfigManager.java            # Configuration loader
│   │       ├── model/
│   │       │   ├── Instance.java                 # Instance data model
│   │       │   └── InstanceStatus.java           # Status enumeration
│   │       ├── scanner/
│   │       │   ├── NetworkScanner.java           # Multi-level scanning
│   │       │   └── HealthChecker.java            # HTTP/REST health checks
│   │       └── dashboard/
│   │           ├── DashboardManager.java         # Dashboard state manager
│   │           └── ConsoleDashboard.java         # Enhanced console renderer
│   └── resources/
│       ├── application.properties                # Configuration (not in git)
│       └── application.properties.template       # Configuration template
```

## Dashboard Display

### Statistics Panel
- **Total Instances**: All discovered instances
- **Healthy (API)**: Instances with working REST API
- **HTTP OK**: Web interface working
- **Degraded**: Web OK but API unavailable
- **Errors**: API errors or timeouts

### Instance Table (Detailed View)
Shows comprehensive information for each instance:
- **IP Address** and **Port**
- **Status** with color-coded icons
- **HTTP Status Code** (200, 404, 500, etc.)
- **Response Time** in milliseconds
- **Metadata**: Branch name, version, commit hash
- **Last Update**: Timestamp of last scan

## Use Case: GitLab Pipeline Instances

This dashboard is designed for environments where:
- Multiple branch instances are deployed on different IPs
- Each instance exposes a web interface and REST API
- Finding the correct branch instance requires checking many pages
- REST API provides metadata about the deployed branch/version

The dashboard automatically scans the IP range, checks HTTP and API endpoints, 
and displays branch information for easy identification.

## Technical Details

- **Java Version**: 8
- **Build Tool**: Maven
- **Logging**: SLF4J + Logback
- **JSON Processing**: Gson
- **Network Protocols**: 
  - TCP Socket for port checking
  - HTTP URLConnection for web/API checks
- **Concurrency**: ThreadPoolExecutor for parallel scanning
- **Health Check Levels**:
  1. TCP port reachability
  2. HTTP endpoint validation
  3. REST API health with metadata extraction

## Author

Simeon Arshyn

## License

Copyright © 2025
