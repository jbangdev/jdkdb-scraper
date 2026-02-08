package dev.jbang.jdkdb.reporting;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central reporting: holds a list of scraper states (for heartbeat) and processes progress events
 * (log messages only). Scrapers register their state and update it directly; heartbeat reads from
 * the list.
 */
public class ProgressReporter implements Runnable, AutoCloseable {
	private static final Logger logger = LoggerFactory.getLogger(ProgressReporter.class);
	private static final ProgressEvent POISON_PILL = ProgressEvent.progress("SHUTDOWN", "");

	private final BlockingQueue<ProgressEvent> eventQueue;
	private final List<ScraperState> registeredStates;
	private final AtomicBoolean running;
	private Thread reporterThread;

	public ProgressReporter() {
		this.eventQueue = new LinkedBlockingQueue<>();
		this.registeredStates = new CopyOnWriteArrayList<>();
		this.running = new AtomicBoolean(false);
	}

	/** Register a scraper state so it is included in heartbeat. Call when creating the scraper. */
	public void register(ScraperState state) {
		registeredStates.add(state);
	}

	/** Start the reporter thread */
	public void start() {
		if (running.compareAndSet(false, true)) {
			reporterThread = new Thread(this, "ProgressReporter");
			reporterThread.setDaemon(false);
			reporterThread.start();
			logger.info("Progress reporter started");
		}
	}

	/** Submit a progress event to be processed */
	public void report(ProgressEvent event) {
		try {
			eventQueue.put(event);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.error("Interrupted while submitting event", e);
		}
	}

	@Override
	public void run() {
		logger.info("Progress reporter thread started");

		while (running.get() || !eventQueue.isEmpty()) {
			try {
				ProgressEvent event = eventQueue.take();

				// Check for shutdown signal
				if (event == POISON_PILL) {
					break;
				}

				processEvent(event);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				logger.warn("Reporter thread interrupted");
				break;
			} catch (Exception e) {
				logger.error("Error processing event", e);
			}
		}

		logger.info("Progress reporter thread stopped");
	}

	private void processEvent(ProgressEvent event) {
		if (event.eventType() == ProgressEvent.EventType.PROGRESS
				&& event.message() != null
				&& !event.message().isEmpty()) {
			if (event.error() != null) {
				logger.error("PROGRESS: {} - {}", event.scraperId(), event.message(), event.error());
			} else {
				logger.info("PROGRESS: {} - {}", event.scraperId(), event.message());
			}
		}
	}

	/** Get the current count of running scrapers (status == RUNNING). */
	public int getRunningCount() {
		return (int) registeredStates.stream()
				.filter(s -> s.getStatus() == ScraperState.Status.RUNNING)
				.count();
	}

	/** Snapshot of (processed, failed, skipped) per scraper id. */
	public Map<String, int[]> getScraperStats() {
		Map<String, int[]> copy = new ConcurrentHashMap<>();
		for (ScraperState s : registeredStates) {
			if (s.getStatus() == ScraperState.Status.RUNNING) {
				copy.put(s.getScraperId(), new int[] {s.getProcessed(), s.getFailed(), s.getSkipped()});
			}
		}
		return Collections.unmodifiableMap(copy);
	}

	/** IDs of scrapers that completed successfully (or reached progress limit). */
	public Set<String> getCompletedScraperIds() {
		Set<String> out = new HashSet<>();
		for (ScraperState s : registeredStates) {
			if (s.getStatus() == ScraperState.Status.COMPLETED) out.add(s.getScraperId());
		}
		return out;
	}

	/** IDs of scrapers that have completed with failure. */
	public Set<String> getFailedScraperIds() {
		Set<String> out = new HashSet<>();
		for (ScraperState s : registeredStates) {
			if (s.getStatus() == ScraperState.Status.FAILED) out.add(s.getScraperId());
		}
		return out;
	}

	/** Build a one-line heartbeat message from registered scraper states. */
	public String formatHeartbeat() {
		int active = getRunningCount();
		var stats = getScraperStats();
		var completed = getCompletedScraperIds();
		var failed = getFailedScraperIds();
		StringBuilder sb = new StringBuilder();
		sb.append("Heartbeat: ").append(active).append(" active");
		if (!stats.isEmpty()) {
			stats.forEach((id, arr) -> {
				boolean anyStats = (arr.length >= 3) && (arr[0] > 0 || arr[1] > 0 || arr[2] > 0);
				sb.append(" | ").append(id).append(": ");
				if (anyStats) {
					sb.append(arr[0]);
					if (arr[1] > 0) {
						sb.append(" f").append(arr[1]);
					}
					if (arr[2] > 0) {
						sb.append(" s").append(arr[2]);
					}
				} else {
					sb.append("-");
				}
			});
		}

		if (!completed.isEmpty()) {
			sb.append(" | ").append(completed.size()).append(" completed: ").append(String.join(", ", completed));
		}
		if (!failed.isEmpty()) {
			sb.append(" | ").append(failed.size()).append(" failed: ").append(String.join(", ", failed));
		}
		return sb.toString();
	}

	/**
	 * Wait for the event queue to drain (empty) or the given timeout to elapse. Use before printing
	 * summary so async log events are processed first.
	 *
	 * @param timeoutMs maximum time to wait in milliseconds
	 */
	public void awaitDrain(long timeoutMs) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (!eventQueue.isEmpty() && System.currentTimeMillis() < deadline) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	/** Shutdown the reporter and wait for all events to be processed */
	@Override
	public void close() {
		if (running.compareAndSet(true, false)) {
			try {
				// Send poison pill to stop the thread
				eventQueue.put(POISON_PILL);

				// Wait for the thread to finish
				if (reporterThread != null) {
					reporterThread.join(5000); // Wait up to 5 seconds
				}

				logger.info("Progress reporter shutdown complete");
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				logger.error("Interrupted while shutting down reporter", e);
			}
		}
	}
}
