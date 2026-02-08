package dev.jbang.jdkdb.reporting;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mutable state for a single scraper. Updated by the scraper; read by the progress reporter for
 * heartbeat. Thread-safe.
 */
public class ScraperState {
	public enum Status {
		RUNNING,
		COMPLETED,
		FAILED
	}

	private final String scraperId;
	private final AtomicInteger processed = new AtomicInteger(0);
	private final AtomicInteger failed = new AtomicInteger(0);
	private final AtomicInteger skipped = new AtomicInteger(0);
	private final AtomicReference<Status> status = new AtomicReference<>(Status.RUNNING);

	public ScraperState(String scraperId) {
		this.scraperId = scraperId;
	}

	public String getScraperId() {
		return scraperId;
	}

	public int getProcessed() {
		return processed.get();
	}

	public int getFailed() {
		return failed.get();
	}

	public int getSkipped() {
		return skipped.get();
	}

	public Status getStatus() {
		return status.get();
	}

	public void incrementProcessed() {
		processed.incrementAndGet();
	}

	public void incrementFailed() {
		failed.incrementAndGet();
	}

	public void incrementSkipped() {
		skipped.incrementAndGet();
	}

	public void complete() {
		status.set(Status.COMPLETED);
	}

	public void fail() {
		status.set(Status.FAILED);
	}
}
