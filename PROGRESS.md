# InstanzenDashboard - Development Progress

## 2025-12-20: UI Overhaul + Bug Fixes

### Implemented

#### 1. ✅ Modern Slate/Blue Theme (replaces "hacker" green)
- **Problem**: Green "hacker" theme with monospace font was hard on the eyes.
- **Fix**: Complete UI overhaul with modern slate/blue color scheme:
  - Background: `#1e293b` → `#0f172a` gradient
  - Text: `#e2e8f0` (light slate)
  - Accents: `#38bdf8` (sky blue) instead of green
  - Font: Segoe UI / system-ui (more readable)
  - Borders: Subtle slate gray `rgba(148,163,184,...)`
- **Scope**: Both Dashboard and Settings pages updated.
- **File**: `src/main/java/org/example/dashboard/WebDashboard.java`

#### 2. ✅ Fixed paths not loading from external config
- **Problem**: External config file at `~/.instanzen-dashboard/application.properties` contained `check.paths=[object Object], [object Object]...` due to earlier serialization bug.
- **Fix**: Restored correct paths in external config file:
  - `check.paths=/,/index.html,/index.php,/index.php?action=ping`
- **Root cause**: Previous JS serialization bug (fixed in earlier session) left corrupted data in config.

#### 3. ✅ Scan results sorted by IP order (not by status)
- **Problem**: `sortInstancesForReadability()` sorted by status severity, making it hard to find specific IPs.
- **Fix**: Changed to preserve natural IP order from scanner (deterministic).
- **Code**: `sortInstancesForReadability()` now returns `instances || []` without sorting.
- **File**: `src/main/java/org/example/dashboard/WebDashboard.java`

### Color Scheme Reference
| Element | Old (Green) | New (Slate/Blue) |
|---------|-------------|------------------|
| Body BG | `#0b1220` | `#1e293b` |
| Text | `#c7f9cc` | `#e2e8f0` |
| Accent | `#86efac` / `#22c55e` | `#38bdf8` |
| Cards | `rgba(5,10,18,...)` | `rgba(30,41,59,...)` |
| Borders | `rgba(34,197,94,...)` | `rgba(148,163,184,...)` |

---

## 2025-12-19: Scan UX + Persistence Improvements

### Implemented

#### 1. ✅ Show all scanned addresses (including unreachable)
- **Problem**: Scanner returned only reachable instances, so the dashboard missed most addresses.
- **Fix**: `NetworkScanner.scanRange()` now returns results for **every IP** in the configured range.
- **UX**: Results are returned in deterministic IP order for easier reading.

#### 2. ✅ Persistent settings across sessions/restarts
- **Problem**: `reloadProperties()` loaded from classpath resources, so saved settings did not reliably persist.
- **Fix**: Configuration is now loaded/saved from an external file in the user home directory:
  - `~/.instanzen-dashboard/application.properties`
- If the external file does not exist, it is created on first run.

#### 3. ✅ Scan progress visible in Web Dashboard
- **Added**: Thread-safe scan progress tracker (`ScanProgressTracker`) and immutable snapshot (`ScanProgressSnapshot`).
- **API**: `/api/stats` now includes `scanProgress` (`total/completed/currentAddress/elapsedMs`).
- **Frontend**: Dashboard shows percentage + current address during scan and stops polling when scan completes.

#### 4. ✅ Better status readability
- Dashboard now displays friendly status labels instead of raw enum constants.

#### 5. ✅ Toggle view mode (Cards ↔ Table)
- **Added**: A view toggle button on the dashboard header.
- **Modes**:
  - **Cards**: Current collapsible per-instance card view.
  - **Table**: Flat list for fast scanning.
- **Persistence**: View mode is stored in browser `localStorage`.
- **Readability**: Instances are sorted by status severity (healthy first, unreachable last).

#### 6. ✅ Dark "hacker" UI theme
- **Updated**: Dashboard + Settings pages now use a consistent dark theme (monospace font, green accents, improved contrast).
- **Scope**: Styling only (no functional changes).
- **File**: `src/main/java/org/example/dashboard/WebDashboard.java`

#### 7. ✅ Fix broken "Scan" button (dashboard JS)
- **Problem**: Dashboard JavaScript contained a corrupted function block, causing a syntax error so handlers (including `triggerScan()`) were not available.
- **Fix**: Restored `updateScanUiFromStats()` and re-added missing `fetchStats()` / `fetchInstances()` functions.
- **Impact**: "Scan" button works again and stats/instances refresh correctly.
- **File**: `src/main/java/org/example/dashboard/WebDashboard.java`

#### 8. ✅ Fix Settings endpoints corruption (`[object Object]`)
- **Problem**: Settings page stored `check.paths` using `endpoints.join(', ')` where `endpoints` is an array of objects, producing `[object Object]` in config and breaking add/remove/display.
- **Fix**: Added path normalization/deduplication and serialize using `endpoints.map(e => e.path).join(', ')`.
- **Also**: Auto-open toggle now re-renders immediately.
- **File**: `src/main/java/org/example/dashboard/WebDashboard.java`

#### 9. ✅ Open in selected browser (server-side)
- **Problem**: `window.open()` always uses the client default browser and cannot respect `browser.choice`.
- **Fix**: Added `/api/open` endpoint that opens the URL on the **server machine** using `browser.choice` (default/chrome/firefox/edge), with a safe fallback to `Desktop.browse()`.
- **Safety**: `/api/open` allows only `http`/`https` URLs.
- **UI**: Dashboard "Open" button now calls `/api/open` with fallback to `window.open` if it fails.
- **Files**: `src/main/java/org/example/dashboard/WebDashboard.java`, `src/main/java/org/example/config/ConfigManager.java`

### Files
- Backend: `src/main/java/org/example/config/ConfigManager.java`, `src/main/java/org/example/scanner/NetworkScanner.java`, `src/main/java/org/example/scanner/HealthChecker.java`, `src/main/java/org/example/ApplicationManager.java`
- Web UI: `src/main/java/org/example/dashboard/WebDashboard.java`
- New: `src/main/java/org/example/scanner/ScanProgressTracker.java`, `src/main/java/org/example/scanner/ScanProgressSnapshot.java`

## 2025-12-16: Fixes and Improvements (Commit: b936509)

### Fixed Issues

#### 1. ✅ Instance Status Logic Correction
- **Problem**: Instance showed HTTP_OK overall status even when first path was UNREACHABLE
- **Root Cause**: `checkMultiplePathsWithMetadata()` used "best status" approach - if any path succeeded, entire instance marked as successful
- **Fix**: Changed to degraded status when some paths work but others don't
- **New Logic**:
  - All paths successful → use best status (API_HEALTHY or HTTP_OK)
  - Some paths successful, some failed → API_DEGRADED with detailed message
  - No paths successful → UNREACHABLE
- **File**: HealthChecker.java
- **Benefit**: More accurate instance health representation

#### 2. ✅ Removed "API Healthy" from Dashboard Stats
- **Problem**: "API Healthy" card always showed 0 in the statistics header
- **Reason**: Most endpoints return HTTP_OK without metadata, so API_HEALTHY status rarely achieved
- **Fix**: Removed from both backend stats and frontend UI
- **Changes**:
  - StatsHandler: Removed `stats.put("healthy", ...)` 
  - Dashboard HTML: Removed "API Healthy" stat card
  - JavaScript: Removed `healthyInstances` update
- **Files**: WebDashboard.java
- **Benefit**: Cleaner UI showing only relevant metrics

#### 3. ✅ Fixed "Open Button" Visibility Logic
- **Problem**: Checkbox labeled "Auto-open in browser" but actually should control button visibility
- **User Clarification**: "Checkbox should mean that 'Open' button will be visible in the row"
- **Fix**: 
  - Changed checkbox label from "🌐 Auto-open in browser" to "🔘 Show Open button"
  - Updated help text to explain checkbox controls button visibility
  - Modified dashboard to only show "Open" button for paths marked with checkbox
  - Added `pathsWithOpenButton` array to track which paths have button enabled
- **Technical Implementation**:
  - StatsHandler sends `pathsWithOpenButton` from `check.paths.autoopen` config
  - JavaScript stores this list globally
  - When rendering path table, checks if path is in list before showing button
  - Paths without checkbox show "-" instead of button
- **Files**: WebDashboard.java
- **Benefit**: Users control which endpoints get "Open" buttons, reducing UI clutter

### Technical Details

#### Status Determination Algorithm (HealthChecker.java)
```java
// Count successful, unreachable, and error paths
if (successfulPaths > 0 && (unreachablePaths > 0 || errorPaths > 0)) {
    // Mixed results = degraded
    instance.setStatus(InstanceStatus.API_DEGRADED);
    instance.setErrorMessage(String.format("%d/%d paths OK, %d unreachable, %d errors", 
        successfulPaths, paths.length, unreachablePaths, errorPaths));
} else if (successfulPaths > 0) {
    // All successful = use best status
    instance.setStatus(bestStatus);
} else {
    // None successful = unreachable
    instance.setStatus(InstanceStatus.UNREACHABLE);
}
```

#### Open Button Visibility (WebDashboard.java)
```javascript
// Global variable to store paths with Open button enabled
let pathsWithOpenButton = [];

// Load from stats
if (stats.pathsWithOpenButton) {
    pathsWithOpenButton = stats.pathsWithOpenButton.split(',').map(s => s.trim()).filter(s => s);
}

// Render table with conditional button
const showOpenButton = pathsWithOpenButton.includes(path);
if (showOpenButton) {
    pathsHtml += '<button class="open-btn" onclick="window.open(\'' + url + '\', \'_blank\')">Open</button>';
} else {
    pathsHtml += '-';
}
```

### Configuration Properties
```properties
check.paths.autoopen=/mobil/servlet/start?mde=100
```
Comma-separated list of paths that should show "Open" button in dashboard.

---

## 2025-12-16: Major UX Redesign (Commit: fa7958e)

### Implemented Changes

#### 1. ✅ Disabled Console Output
- **ApplicationManager.java**: Removed `consoleDashboard.render()` from scan loop
- Console output completely disabled during scanning
- Clean operation without terminal flooding

#### 2. ✅ Manual Scan Mode
- **Removed**: Auto-refresh with ScheduledExecutorService
- **Added**: Manual scan trigger via `/api/scan` endpoint
- **Added**: `performScan()` method with concurrency protection using `isScanning` flag
- **Frontend**: Replaced "Refresh" button with "Scan" button
- **Frontend**: Added scan status indicator with real-time feedback
- **Frontend**: Removed auto-refresh timer, removed "Next scan in:" countdown

#### 3. ✅ Collapsible IP Details
- **Added**: HTML5 `<details>/<summary>` elements for each instance
- **Added**: CSS styles for collapsible lists
- **Frontend**: Each IP address now shows compact header with status badge
- **Frontend**: Scan details (paths table) hidden by default, expandable on click
- **UX**: Shows number of paths in summary (e.g., "📋 Scan Details (3 paths)")

#### 4. ✅ Auto-Open Checkboxes
- **Settings Page**: Added checkboxes next to each endpoint path
- **Configuration**: New property `check.paths.autoopen` stores comma-separated list of paths to auto-open
- **JavaScript**: Modified endpoint objects to include `{path: string, autoOpen: boolean}`
- **UI**: Shows "🌐 Auto-open in browser" checkbox for each endpoint
- **Persistence**: Checkbox states saved to application.properties

#### 5. ✅ Browser Selection
- **Settings Page**: Added "Browser Settings" section
- **Configuration**: New property `browser.choice` with options:
  - `default` - System Default Browser
  - `chrome` - Google Chrome
  - `firefox` - Mozilla Firefox
  - `edge` - Microsoft Edge
- **UI**: Dropdown selector in settings page
- **Persistence**: Browser choice saved to application.properties

### Technical Details

#### Backend Changes
- **ApplicationManager.java**:
  - Removed `ScheduledExecutorService scheduler` field
  - Added `boolean isScanning` flag for concurrency control
  - Modified `startScanner()` to only initialize (no scheduling)
  - Added `performScan()` method for on-demand scanning
  - Added `isScanning()` getter for UI status checks
  - Removed console output from scan loop

- **WebDashboard.java**:
  - Added `ScanHandler` class for `/api/scan` endpoint
  - Modified `start()` to register scan endpoint
  - Updated dashboard HTML:
    - Replaced refresh button with scan button
    - Added scan status display
    - Removed next-scan countdown
    - Added collapsible `<details>` elements
  - Updated settings page HTML:
    - Added browser selection dropdown
    - Added checkboxes to endpoint items
    - Updated CSS for checkbox styling
  - Updated JavaScript:
    - Modified `triggerScan()` function to call `/api/scan`
    - Updated `renderEndpoints()` to display checkboxes
    - Modified `loadConfig()` to load auto-open settings
    - Updated `saveSettings()` to save auto-open and browser settings
    - Added `toggleAutoOpen()` function

#### Configuration Properties
- Existing: `network.ip.range.start`, `network.ip.range.end`, `network.port`, `check.paths`
- **New**: `check.paths.autoopen` - comma-separated list of paths to auto-open
- **New**: `browser.choice` - selected browser (default/chrome/firefox/edge)

### Build Status
- ✅ Compilation: SUCCESS
- ✅ Package: SUCCESS
- ✅ Java 8 Compatibility: Maintained
- ✅ Output JAR: `InstanzenDashboard-1.0-SNAPSHOT-jar-with-dependencies.jar`

### User Experience Improvements
1. **Less Noise**: No console flooding, clean operation
2. **User Control**: Manual scan on-demand instead of automatic intervals
3. **Better Organization**: Collapsible lists reduce clutter when many instances found
4. **Automation**: Auto-open feature for frequently accessed paths
5. **Flexibility**: Browser choice for different workflows

### Next Steps
- Consider implementing actual browser opening logic based on `browser.choice` setting
- Consider adding functionality to auto-open paths marked with `autoOpen=true` after successful scan
- Consider adding scan progress indicator during long scans
- Consider adding cancel button for active scans

---

## Previous Development

### Initial Setup
- Java 8 project with Maven
- Multi-level health checking (TCP → HTTP → REST API)
- 8-state instance status system
- Web dashboard with embedded HTTP server (port 8081)

### Features Added
- Metadata extraction from JSON APIs
- Per-path result tracking
- Settings page for configuration
- Hot-reload with "Apply Now" button
- Security: sensitive data excluded from git

### Technical Stack
- Java 8 (bytecode 52.0)
- Maven with Assembly Plugin
- Gson 2.8.9
- SLF4J 1.7.36 + Logback 1.2.11
- Java HttpServer (com.sun.net.httpserver)
