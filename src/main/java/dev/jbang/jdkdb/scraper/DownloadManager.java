package dev.jbang.jdkdb.scraper;

import dev.jbang.jdkdb.model.JdkMetadata;
import org.slf4j.Logger;

/**
 * Interface for managing parallel downloads of JDK files. Implementations receive JdkMetadata from
 * scrapers and handle the downloading of files.
 */
public interface DownloadManager {
	/**
	 * Submit a metadata item for download.
	 *
	 * @param metadata The JDK metadata containing the URL to download
	 * @param vendor The vendor name
	 * @param logger The logger to use for logging download progress and errors
	 */
	void submit(JdkMetadata metadata, String vendor, Logger logger);

	/**
	 * Start the download manager. Should be called once after construction.
	 */
	void start();

	/**
	 * Signal that no more downloads will be submitted. Call this after all scrapers have finished.
	 */
	void shutdown();

	/**
	 * Wait for all queued downloads to complete. This method blocks until all downloads are
	 * finished.
	 *
	 * @throws InterruptedException if interrupted while waiting
	 */
	void awaitCompletion() throws InterruptedException;

	/**
	 * Get the number of completed downloads.
	 *
	 * @return Number of successfully completed downloads
	 */
	int getCompletedCount();

	/**
	 * Get the number of failed downloads.
	 *
	 * @return Number of failed downloads
	 */
	int getFailedCount();
}
