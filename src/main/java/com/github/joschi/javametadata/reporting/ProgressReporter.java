package com.github.joschi.javametadata.reporting;

import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central reporting thread that receives and processes progress events from all scrapers. Maintains
 * a list of currently running scrapers and logs their progress.
 */
public class ProgressReporter implements Runnable, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ProgressReporter.class);
    private static final ProgressEvent POISON_PILL = ProgressEvent.progress("SHUTDOWN", "");

    private final BlockingQueue<ProgressEvent> eventQueue;
    private final Set<String> runningScrapers;
    private final AtomicBoolean running;
    private Thread reporterThread;

    public ProgressReporter() {
        this.eventQueue = new LinkedBlockingQueue<>();
        this.runningScrapers = ConcurrentHashMap.newKeySet();
        this.running = new AtomicBoolean(false);
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
        switch (event.eventType()) {
            case STARTED -> {
                runningScrapers.add(event.scraperId());
                logger.info(
                        "STARTED: {} | Currently running: {}",
                        event.scraperId(),
                        runningScrapers.size());
                logRunningScrapers();
            }
            case PROGRESS -> logger.info("PROGRESS: {} - {}", event.scraperId(), event.message());
            case COMPLETED -> {
                runningScrapers.remove(event.scraperId());
                logger.info(
                        "COMPLETED: {} | Remaining: {}", event.scraperId(), runningScrapers.size());
                logRunningScrapers();
            }
            case FAILED -> {
                runningScrapers.remove(event.scraperId());
                if (event.error() != null) {
                    logger.error(
                            "FAILED: {} - {} | Remaining: {}",
                            event.scraperId(),
                            event.message(),
                            runningScrapers.size(),
                            event.error());
                } else {
                    logger.error(
                            "FAILED: {} - {} | Remaining: {}",
                            event.scraperId(),
                            event.message(),
                            runningScrapers.size());
                }
                logRunningScrapers();
            }
        }
    }

    private void logRunningScrapers() {
        if (!runningScrapers.isEmpty()) {
            logger.debug("Currently running scrapers: {}", String.join(", ", runningScrapers));
        }
    }

    /** Get the current count of running scrapers */
    public int getRunningCount() {
        return runningScrapers.size();
    }

    /** Get a snapshot of currently running scraper IDs */
    public Set<String> getRunningScrapers() {
        return Set.copyOf(runningScrapers);
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
