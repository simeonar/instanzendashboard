package org.example.dashboard;

import org.example.model.Instance;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Console-based dashboard renderer
 */
public class ConsoleDashboard {
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_BOLD = "\u001B[1m";

    private final String title;

    public ConsoleDashboard(String title) {
        this.title = title;
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
        System.out.println("  Reachable:            " + ANSI_GREEN + dashboardManager.getReachableInstances() + ANSI_RESET);
        System.out.println("  Active Applications:  " + ANSI_YELLOW + dashboardManager.getActiveApplications() + ANSI_RESET);
        System.out.println();
    }

    private void printInstanceTable(List<Instance> instances) {
        System.out.println(ANSI_BOLD + "Instances:" + ANSI_RESET);
        System.out.println("┌─────────────────────┬──────────┬──────────────┬───────────────┐");
        System.out.println("│ IP Address          │ Port     │ Status       │ Response Time │");
        System.out.println("├─────────────────────┼──────────┼──────────────┼───────────────┤");

        if (instances.isEmpty()) {
            System.out.println("│                   No instances found                          │");
        } else {
            for (Instance instance : instances) {
                printInstanceRow(instance);
            }
        }

        System.out.println("└─────────────────────┴──────────┴──────────────┴───────────────┘");
        System.out.println();
    }

    private void printInstanceRow(Instance instance) {
        String ip = String.format("%-19s", instance.getIpAddress());
        String port = String.format("%-8s", instance.getPort());
        
        String status;
        if (instance.isReachable()) {
            status = ANSI_GREEN + "✓ Online    " + ANSI_RESET;
        } else {
            status = ANSI_RED + "✗ Offline   " + ANSI_RESET;
        }

        String responseTime;
        if (instance.getResponseTimeMs() >= 0) {
            responseTime = String.format("%-13s", instance.getResponseTimeMs() + " ms");
        } else {
            responseTime = String.format("%-13s", "N/A");
        }

        System.out.println("│ " + ip + " │ " + port + " │ " + status + " │ " + responseTime + " │");
    }

    private void printFooter(long lastUpdateTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String lastUpdate = lastUpdateTime > 0 ? sdf.format(new Date(lastUpdateTime)) : "Never";
        
        System.out.println(ANSI_BOLD + "Last Update: " + ANSI_RESET + lastUpdate);
        System.out.println();
    }
}
