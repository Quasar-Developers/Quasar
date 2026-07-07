package dev.kate.erd.core.debug;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot of machine debug information for display.
 */
public record DebugSnapshot(List<Section> sections) {

    public static Builder builder() {
        return new Builder();
    }

    public record Section(String name, List<Entry> entries) {}

    public sealed interface Entry permits Property, KeyValue, Metric, ProgressBar, Status, Warning {}

    public record Property(String name, String value) implements Entry {}
    public record KeyValue(String key, String value) implements Entry {}
    public record Metric(String name, double value, double max, Severity severity) implements Entry {}
    public record ProgressBar(String name, double value, double max) implements Entry {}
    public record Status(String name, Severity severity) implements Entry {}
    public record Warning(String message) implements Entry {}

    public enum Severity {
        SUCCESS, NEUTRAL, INFO, WARNING, ERROR
    }

    public static class Builder {
        private final List<Section> sections = new ArrayList<>();
        private String currentSectionName;
        private List<Entry> currentEntries = new ArrayList<>();

        public Builder section(String name) {
            flushSection();
            currentSectionName = name;
            currentEntries = new ArrayList<>();
            return this;
        }

        public Builder property(String name, String value) {
            currentEntries.add(new Property(name, value));
            return this;
        }

        public Builder keyValue(String key, String value) {
            currentEntries.add(new KeyValue(key, value));
            return this;
        }

        public Builder metric(String name, double value, double max, Severity severity) {
            currentEntries.add(new Metric(name, value, max, severity));
            return this;
        }

        public Builder progressBar(String name, double value, double max) {
            currentEntries.add(new ProgressBar(name, value, max));
            return this;
        }

        public Builder status(String name, Severity severity) {
            currentEntries.add(new Status(name, severity));
            return this;
        }

        public Builder warning(String message) {
            currentEntries.add(new Warning(message));
            return this;
        }

        private void flushSection() {
            if (currentSectionName != null && !currentEntries.isEmpty()) {
                sections.add(new Section(currentSectionName, List.copyOf(currentEntries)));
            }
        }

        public DebugSnapshot build() {
            flushSection();
            return new DebugSnapshot(List.copyOf(sections));
        }
    }
}
