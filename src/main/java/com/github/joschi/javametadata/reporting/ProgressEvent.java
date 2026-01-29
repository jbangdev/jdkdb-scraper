package com.github.joschi.javametadata.reporting;

import java.time.Instant;

/** Represents a progress event from a scraper */
public record ProgressEvent(
        String scraperId, EventType eventType, String message, Instant timestamp, Throwable error) {
    public enum EventType {
        STARTED,
        PROGRESS,
        COMPLETED,
        FAILED
    }

    public static ProgressEvent started(String scraperId) {
        return new ProgressEvent(
                scraperId, EventType.STARTED, "Scraper started", Instant.now(), null);
    }

    public static ProgressEvent progress(String scraperId, String message) {
        return new ProgressEvent(scraperId, EventType.PROGRESS, message, Instant.now(), null);
    }

    public static ProgressEvent completed(String scraperId) {
        return new ProgressEvent(
                scraperId,
                EventType.COMPLETED,
                "Scraper completed successfully",
                Instant.now(),
                null);
    }

    public static ProgressEvent failed(String scraperId, String message, Throwable error) {
        return new ProgressEvent(scraperId, EventType.FAILED, message, Instant.now(), error);
    }

    @Override
    public String toString() {
        return "[%s] %s: %s - %s".formatted(timestamp, scraperId, eventType, message);
    }
}
