package org.example.dashboard;

import org.example.model.Instance;
import org.example.model.InstanceStatus;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Enhanced console dashboard with metadata display
 */
public class ConsoleDashboard {
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_MAGENTA = "\u001B[35m";
    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_DIM = "\u001B[2m";

    private final String title;
    private final boolean showMetadata;

    public ConsoleDashboard(String title, boolean showMetadata) {
        this.title = title;
        this.showMetadata = showMetadata;
    }

    public void render(DashboardManager dashboardManager) {
        clearScreen();
        
        printHeader();
        printStatistics(dashboardManager);
        printInstanceTable(dashboardManager.getInstances());
        printFooter(dashboardManager.getLastUpdateTime());
    }

    private void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    private void printHeader() {
        System.out.println(ANSI_BOLD + ANSI_BLUE + "═══════════════════════════════════════════════════════════════════" + ANSI_RESET);
        System.out.println(ANSI_BOLD + ANSI_BLUE + "  " + title + ANSI_RESET);
        System.out.println(ANSI_BOLD + ANSI_BLUE + "═══════════════════════════════════════════════════════════════════" + ANSI_RESET);
        System.out.println();
    }

    private void printStatistics(DashboardManager dashboardManager) {
        System.out.println(ANSI_BOLD + "Statistics:" + ANSI_RESET);
        System.out.println("  Total Instances:      " + dashboardManager.getTotalInstances());
        System.out.println("  Healthy (API):        " + ANSI_GREEN + dashboardManager.getHealthyInstances() + ANSI_RESET);
        System.out.println("  HTTP OK:              " + ANSI_CYAN + dashboardManager.getHttpOkInstances() + ANSI_RESET);
        System.out.println("  Degraded:             " + ANSI_YELLOW + dashboardManager.getDegradedInstances() + ANSI_RESET);
        System.out.println("  Errors:               " + ANSI_RED + dashboardManager.getErrorInstances() + ANSI_RESET);
        System.out.println();
    }

    private void printInstanceTable(List<Instance> instances) {
        System.out.println(ANSI_BOLD + "Instances:" + ANSI_RESET);
        
        if (showMetadata) {
            printDetailedTable(instances);
        } else {
            printSimpleTable(instances);
        }
    }

    private void printSimpleTable(List<Instance> instances) {
        System.out.println("┌─────────────────────┬──────────┬──────────────────┬──────────┬─────────┐");
        System.out.println("│ IP Address          │ Port     │ Status           │ HTTP     │ Time    │");
        System.out.println("├─────────────────────┼──────────┼──────────────────┼──────────┼─────────┤");

        if (instances.isEmpty()) {
            System.out.println("│                        No instances found                           │");
        } else {
            for (Instance instance : instances) {
                printSimpleRow(instance);
            }
        }

        System.out.println("└─────────────────────┴──────────┴──────────────────┴──────────┴─────────┘");
        System.out.println();
    }

    private void printDetailedTable(List<Instance> instances) {
        System.out.println("╔═════════════════════╤══════════╤══════════════════╤══════════╤═════════╤══════════════════════════════╗");
        System.out.println("║ IP Address          │ Port     │ Status           │ HTTP     │ Time    │ Metadata                     ║");
        System.out.println("╟─────────────────────┼──────────┼──────────────────┼──────────┼─────────┼──────────────────────────────╢");

        if (instances.isEmpty()) {
            System.out.println("║                                    No instances found                                              ║");
        } else {
            for (Instance instance : instances) {
                printDetailedRow(instance);
            }
        }

        System.out.println("╚═════════════════════╧══════════╧══════════════════╧══════════╧═════════╧══════════════════════════════╝");
        System.out.println();
    }

    private void printSimpleRow(Instance instance) {
        String ip = String.format("%-19s", truncate(instance.getIpAddress(), 19));
        String port = String.format("%-8s", instance.getPort());
        String status = formatStatus(instance.getStatus(), 16);
        String httpCode = String.format("%-8s", instance.getHttpStatusCode() > 0 ? instance.getHttpStatusCode() : "-");
        String time = String.format("%-7s", instance.getResponseTimeMs() >= 0 ? instance.getResponseTimeMs() + "ms" : "N/A");

        System.out.println("│ " + ip + " │ " + port + " │ " + status + " │ " + httpCode + " │ " + time + " │");
    }

    private void printDetailedRow(Instance instance) {
        String ip = String.format("%-19s", truncate(instance.getIpAddress(), 19));
        String port = String.format("%-8s", instance.getPort());
        String status = formatStatus(instance.getStatus(), 16);
        String httpCode = String.format("%-8s", instance.getHttpStatusCode() > 0 ? instance.getHttpStatusCode() : "-");
        String time = String.format("%-7s", instance.getResponseTimeMs() >= 0 ? instance.getResponseTimeMs() + "ms" : "N/A");
        String metadata = formatMetadata(instance);

        System.out.println("║ " + ip + " │ " + port + " │ " + status + " │ " + httpCode + " │ " + time + " │ " + metadata + " ║");
    }

    private String formatStatus(InstanceStatus status, int width) {
        String color;
        String icon;
        
        switch (status) {
            case API_HEALTHY:
                color = ANSI_GREEN;
                icon = "✓";
                break;
            case HTTP_OK:
                color = ANSI_CYAN;
                icon = "◉";
                break;
            case API_DEGRADED:
                color = ANSI_YELLOW;
                icon = "⚠";
                break;
            case API_ERROR:
                color = ANSI_RED;
                icon = "✗";
                break;
            case PORT_OPEN:
                color = ANSI_MAGENTA;
                icon = "○";
                break;
            case UNREACHABLE:
                color = ANSI_RED;
                icon = "✗";
                break;
            case TIMEOUT:
                color = ANSI_YELLOW;
                icon = "⏱";
                break;
            default:
                color = ANSI_DIM;
                icon = "?";
        }
        
        String text = color + icon + " " + status.getDisplayName() + ANSI_RESET;
        int displayWidth = status.getDisplayName().length() + 2; // icon + space + text
        int padding = width - displayWidth;
        
        return text + repeatString(" ", Math.max(0, padding));
    }

    private String formatMetadata(Instance instance) {
        if (!instance.hasMetadata()) {
            return String.format("%-28s", ANSI_DIM + "No metadata" + ANSI_RESET);
        }
        
        StringBuilder meta = new StringBuilder();
        String branch = instance.getBranch();
        String version = instance.getVersion();
        String commit = instance.getCommit();
        
        if (branch != null) {
            meta.append(ANSI_CYAN).append("Branch: ").append(ANSI_RESET).append(truncate(branch, 10));
        }
        if (version != null) {
            if (meta.length() > 0) meta.append(" ");
            meta.append(ANSI_YELLOW).append("v").append(truncate(version, 6)).append(ANSI_RESET);
        }
        if (commit != null) {
            if (meta.length() > 0) meta.append(" ");
            meta.append(ANSI_DIM).append(truncate(commit, 7)).append(ANSI_RESET);
        }
        
        String result = meta.toString();
        // Approximate display width (without ANSI codes)
        int approxWidth = branch != null ? branch.length() + 8 : 0;
        approxWidth += version != null ? version.length() + 2 : 0;
        approxWidth += commit != null ? commit.length() + 1 : 0;
        
        int padding = 28 - approxWidth;
        if (padding > 0) {
            result += repeatString(" ", padding);
        }
        
        return result;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 1) + "…";
    }

    /**
     * Repeats a string n times (Java 8 compatible alternative to String.repeat)
     */
    private String repeatString(String str, int count) {
        if (count <= 0) return "";
        StringBuilder sb = new StringBuilder(str.length() * count);
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    private void printFooter(long lastUpdateTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String lastUpdate = lastUpdateTime > 0 ? sdf.format(new Date(lastUpdateTime)) : "Never";
        
        System.out.println(ANSI_BOLD + "Last Update: " + ANSI_RESET + lastUpdate);
        System.out.println();
    }
}
