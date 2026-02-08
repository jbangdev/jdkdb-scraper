package dev.jbang.jdkdb.reporting;

import java.time.Instant;

/** Log/progress message from a scraper (forwarded to the reporter for logging). */
public record ProgressEvent(String scraperId, EventType eventType, String message, Instant timestamp, Throwable error) {
	public enum EventType {
		PROGRESS
	}

	public static ProgressEvent progress(String scraperId, String message) {
		return new ProgressEvent(scraperId, EventType.PROGRESS, message, Instant.now(), null);
	}

	/** Progress event with an optional throwable (e.g. from a log record). */
	public static ProgressEvent progress(String scraperId, String message, Throwable error) {
		return new ProgressEvent(scraperId, EventType.PROGRESS, message, Instant.now(), error);
	}

	@Override
	public String toString() {
		return "[%s] %s: %s".formatted(timestamp, scraperId, message);
	}
}
