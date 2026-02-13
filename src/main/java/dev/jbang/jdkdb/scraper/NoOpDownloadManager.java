package dev.jbang.jdkdb.scraper;

import dev.jbang.jdkdb.model.JdkMetadata;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No-operation implementation of DownloadManager that skips all downloads. Useful for testing or
 * metadata-only scraping.
 */
public class NoOpDownloadManager implements DownloadManager {
	private final AtomicInteger completedDownloads = new AtomicInteger(0);

	private static final Logger logger = LoggerFactory.getLogger(NoOpDownloadManager.class);

	/**
	 * Start the download manager. This is a no-op.
	 */
	@Override
	public void start() {
		logger.info("NoOpDownloadManager started - all downloads will be skipped");
	}

	/**
	 * Submit a metadata item for download. This implementation logs and ignores the request.
	 *
	 * @param metadata The JDK metadata containing the URL to download
	 * @param scraper The scraper that submitted this download
	 */
	@Override
	public void submit(JdkMetadata metadata, BaseScraper scraper) {
		if (metadata.url() != null && metadata.filename() != null) {
			completedDownloads.incrementAndGet();
			logger.debug("Ignoring download request for: {} (no-download option specified)", metadata.filename());
			scraper.log("Ignoring download request for: " + metadata.filename() + " (no-download option specified)");
		}
	}

	/**
	 * Signal that no more downloads will be submitted. This is a no-op.
	 */
	@Override
	public void shutdown() {
		logger.info("NoOpDownloadManager shutdown");
	}

	/**
	 * Wait for all queued downloads to complete. This returns immediately as there are no downloads.
	 */
	@Override
	public void awaitCompletion() {
		// No-op - nothing to wait for
	}

	/**
	 * Get the number of completed downloads.
	 *
	 * @return Returns the number of submitted downloads
	 */
	@Override
	public int getCompletedCount() {
		return completedDownloads.get();
	}

	/**
	 * Get the number of failed downloads.
	 *
	 * @return Always returns 0
	 */
	@Override
	public int getFailedCount() {
		return 0;
	}

	/**
	 * Get the number of downloads currently in progress.
	 *
	 * @return Always returns 0
	 */
	public int getActiveCount() {
		return 0;
	}

	/**
	 * Get the number of downloads waiting in the queue.
	 *
	 * @return Always returns 0
	 */
	public int getQueuedCount() {
		return 0;
	}
}
