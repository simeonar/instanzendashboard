# Instance Dashboard

A Java 8 application for monitoring network instances in an intranet environment.

## Features

- 🔍 Network scanning of IP address ranges
- 📊 Real-time console dashboard display
- ⚡ Multi-threaded scanning for performance
- ⏱️ Configurable scan intervals to minimize network load
- 🎨 Color-coded status display
- ⚙️ Configurable via properties file

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
   network.ip.range.start=192.168.1.1
   network.ip.range.end=192.168.1.254
   network.port=8080

   # Scan interval in seconds (avoid flooding)
   scan.interval.seconds=60

   # Connection timeout in milliseconds
   connection.timeout.ms=2000

   # Dashboard settings
   dashboard.refresh.seconds=30
   dashboard.title=Instance Dashboard
   ```

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
│   │       ├── Main.java                    # Application entry point
│   │       ├── config/
│   │       │   └── ConfigManager.java       # Configuration loader
│   │       ├── model/
│   │       │   └── Instance.java            # Instance data model
│   │       ├── scanner/
│   │       │   └── NetworkScanner.java      # Network scanning logic
│   │       └── dashboard/
│   │           ├── DashboardManager.java    # Dashboard state manager
│   │           └── ConsoleDashboard.java    # Console renderer
│   └── resources/
│       ├── application.properties           # Configuration (not in git)
│       └── application.properties.template  # Configuration template
```

## Dashboard Display

The dashboard shows:
- Total instances found
- Number of reachable instances
- Active applications count
- Detailed table with:
  - IP Address
  - Port
  - Status (Online/Offline)
  - Response time in milliseconds
- Last update timestamp

## Technical Details

- **Java Version**: 8
- **Build Tool**: Maven
- **Logging**: SLF4J + Logback
- **Network Protocol**: TCP Socket connections
- **Concurrency**: ThreadPoolExecutor for parallel scanning

## Author

Simeon Arshyn

## License

Copyright © 2025
