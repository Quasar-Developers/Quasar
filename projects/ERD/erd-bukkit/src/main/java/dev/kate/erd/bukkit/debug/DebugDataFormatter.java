package dev.kate.erd.bukkit.debug;

import dev.kate.erd.core.debug.DebugSnapshot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;

/**
 * Formats debug snapshots into Adventure Components for display in-game.
 *
 * <p>Converts the core debug data structures into beautifully formatted
 * colored text suitable for Minecraft chat and action bars.
 */
public class DebugDataFormatter {

    private static final TextColor COLOR_PINK = TextColor.color(255, 100, 255);
    private static final TextColor COLOR_CYAN = TextColor.color(85, 255, 255);
    private static final TextColor COLOR_YELLOW = TextColor.color(255, 255, 50);
    private static final TextColor COLOR_BLUE = TextColor.color(100, 200, 255);

    /**
     * Formats a debug snapshot into a list of components for display.
     *
     * @param snapshot the debug snapshot
     * @return list of formatted components
     */
    public static List<Component> format(DebugSnapshot snapshot) {
        List<Component> components = new ArrayList<>();
        List<DebugSnapshot.Section> sections = snapshot.sections();

        // Top spacing for "boxed" look
        components.add(Component.empty());

        for (DebugSnapshot.Section section : sections) {
            // Filter out unwanted sections
            if (section.name().equalsIgnoreCase("Structure")) {
                continue;
            }

            // Section Header (Skip generic "Machine Info")
            if (!section.name().isEmpty() && !section.name().equalsIgnoreCase("Machine Info")) {
                components.add(Component.text(section.name() + ":", NamedTextColor.GRAY));
            }

            // Entries
            for (DebugSnapshot.Entry entry : section.entries()) {
                components.addAll(formatEntry(entry));
            }
        }

        // Bottom spacing
        components.add(Component.empty());

        return components;
    }

    /**
     * Formats a single debug entry.
     */
    private static List<Component> formatEntry(DebugSnapshot.Entry entry) {
        return switch (entry) {
            case DebugSnapshot.Property prop ->
                List.of(Component.text()
                    .append(Component.text(prop.name() + ": ", NamedTextColor.GRAY))
                    .append(Component.text(prop.value(), COLOR_CYAN))
                    .build());

            case DebugSnapshot.KeyValue kv ->
                List.of(Component.text()
                    .append(Component.text(kv.key() + ": ", NamedTextColor.GRAY))
                    .append(Component.text(kv.value(), COLOR_PINK))
                    .build());

            case DebugSnapshot.Metric metric ->
                List.of(Component.text()
                    .append(Component.text(metric.name() + ": ", NamedTextColor.GRAY))
                    .append(Component.text(String.format("%.1f / %.1f", metric.value(), metric.max()),
                        getSeverityColor(metric.severity())))
                    .build());

            case DebugSnapshot.ProgressBar bar ->
                formatProgressBar(bar);

            case DebugSnapshot.Status status ->
                List.of(Component.text()
                    .append(Component.text("● ", getSeverityColor(status.severity())))
                    .append(Component.text(status.name(), getSeverityColor(status.severity())))
                    .build());

            case DebugSnapshot.Warning warning ->
                List.of(Component.text(warning.message(), NamedTextColor.YELLOW,
                    TextDecoration.BOLD));
        };
    }

    /**
     * Formats a progress bar with visual bar representation.
     */
    private static List<Component> formatProgressBar(DebugSnapshot.ProgressBar bar) {
        double percentage = bar.value() / bar.max();
        NamedTextColor barColor = getProgressBarColor(percentage);

        // Line 1: Label: current / max (pct%)
        String pctStr = String.format("(%.1f%%)", percentage * 100.0);

        Component line1 = Component.text()
            .append(Component.text(bar.name() + ": ", NamedTextColor.GRAY))
            .append(Component.text(String.format("%.1f / %.1f ", bar.value(), bar.max()), NamedTextColor.GREEN))
            .append(Component.text(pctStr, NamedTextColor.WHITE))
            .build();

        // Line 2: [||||||||::::]
        // Use 20 bars
        int totalBars = 24; // Width of bar
        int filled = (int) (percentage * totalBars);
        filled = Math.max(0, Math.min(totalBars, filled));

        String filledStr = "|".repeat(filled);
        String emptyStr = ":".repeat(totalBars - filled);

        Component line2 = Component.text()
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text(filledStr, barColor))
            .append(Component.text(emptyStr, NamedTextColor.DARK_GRAY))
            .append(Component.text("]", NamedTextColor.DARK_GRAY))
            .build();

        return List.of(line1, line2);
    }


    /**
     * Gets color for progress bar based on fill percentage.
     */
    private static NamedTextColor getProgressBarColor(double percentage) {
        if (percentage < 0.25) return NamedTextColor.RED;
        if (percentage < 0.50) return NamedTextColor.YELLOW;
        if (percentage < 0.75) return NamedTextColor.GREEN;
        return NamedTextColor.AQUA;
    }

    /**
     * Gets color for severity level.
     */
    private static NamedTextColor getSeverityColor(DebugSnapshot.Severity severity) {
        return switch (severity) {
            case INFO -> NamedTextColor.AQUA;
            case SUCCESS -> NamedTextColor.GREEN;
            case WARNING -> NamedTextColor.YELLOW;
            case ERROR -> NamedTextColor.RED;
            case NEUTRAL -> NamedTextColor.GRAY;
        };
    }

    /**
     * Creates a compact single-line summary of a machine for action bars.
     *
     * @param displayName the machine display name
     * @param hasCritical whether the machine has critical issues
     * @return a compact component
     */
    public static Component formatCompact(String displayName, boolean hasCritical) {
        NamedTextColor color = hasCritical ? NamedTextColor.RED : NamedTextColor.GREEN;
        String icon = hasCritical ? "⚠ " : "✓ ";

        return Component.text()
            .append(Component.text(icon, color, TextDecoration.BOLD))
            .append(Component.text(displayName, color))
            .build();
    }
}
