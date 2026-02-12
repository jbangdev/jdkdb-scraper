package dev.jbang.jdkdb.scraper;

import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.util.HashUtils;
import dev.jbang.jdkdb.util.HttpUtils;
import dev.jbang.jdkdb.util.MetadataUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation that manages parallel downloads of JDK files across multiple threads.
 * Receives JdkMetadata from scrapers, queues them, and downloads files in parallel worker threads.
 */
public class DefaultDownloadManager implements DownloadManager {
	private final BlockingQueue<DownloadTask> downloadQueue;
	private final ExecutorService executorService;
	private final HttpUtils httpUtils;
	private final AtomicInteger activeDownloads;
	private final AtomicInteger completedDownloads;
	private final AtomicInteger failedDownloads;
	private final Path metadataDir;
	private final Path checksumDir;
	private volatile boolean shutdownRequested;

	private static final Logger logger = LoggerFactory.getLogger(DefaultDownloadManager.class);

	/**
	 * Create a new DefaultDownloadManager.
	 *
	 * @param threadCount Number of parallel download threads
	 * @param metadataDir The directory to save metadata files
	 * @param checksumDir The directory to save checksum files
	 */
	public DefaultDownloadManager(int threadCount, Path metadataDir, Path checksumDir) {
		this.downloadQueue = new LinkedBlockingQueue<>();
		this.executorService = Executors.newFixedThreadPool(threadCount);
		this.httpUtils = new HttpUtils();
		this.activeDownloads = new AtomicInteger(0);
		this.completedDownloads = new AtomicInteger(0);
		this.failedDownloads = new AtomicInteger(0);
		this.metadataDir = metadataDir;
		this.checksumDir = checksumDir;
		this.shutdownRequested = false;
	}

	/**
	 * Start the download worker threads. Should be called once after construction.
	 */
	public void start() {
		logger.info(
				"Starting DownloadManager with {} threads", ((ThreadPoolExecutor) executorService).getCorePoolSize());
		int threadCount = ((ThreadPoolExecutor) executorService).getCorePoolSize();
		for (int i = 0; i < threadCount; i++) {
			executorService.submit(this::downloadWorker);
		}
	}

	/**
	 * Submit a metadata item for download.
	 *
	 * @param metadata The JDK metadata containing the URL to download
	 * @param scraper The scraper that submitted this download (for progress reporting)
	 */
	@Override
	public void submit(JdkMetadata metadata, BaseScraper scraper) {
		if (shutdownRequested) {
			throw new IllegalStateException("Cannot submit downloads after shutdown requested");
		}
		if (metadata.url() == null || metadata.filename() == null) {
			return;
		}
		try {
			downloadQueue.put(new DownloadTask(metadata, scraper));
			scraper.log("Queued download for " + metadata.filename());
			logger.debug(
					"Scraper {} submitted download for {}", scraper.getClass().getSimpleName(), metadata.filename());
			logger.info(
					"Downloads: {} queued, {} active, {} completed, {} failed",
					downloadQueue.size(),
					activeDownloads.get(),
					completedDownloads.get(),
					failedDownloads.get());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while submitting download", e);
		}
	}

	/**
	 * Signal that no more downloads will be submitted. Call this after all scrapers have finished.
	 */
	public void shutdown() {
		logger.info("Shutting down DownloadManager");
		shutdownRequested = true;
	}

	/**
	 * Wait for all queued downloads to complete. This method blocks until all downloads are
	 * finished.
	 *
	 * @throws InterruptedException if interrupted while waiting
	 */
	public void awaitCompletion() throws InterruptedException {
		// Wait for queue to be empty and all downloads to complete
		while (!downloadQueue.isEmpty() || activeDownloads.get() > 0) {
			Thread.sleep(100);
		}

		// Shutdown executor and wait for all threads to finish
		executorService.shutdown();
		executorService.awaitTermination(1, TimeUnit.HOURS);
	}

	/**
	 * Get the number of completed downloads.
	 *
	 * @return Number of successfully completed downloads
	 */
	public int getCompletedCount() {
		return completedDownloads.get();
	}

	/**
	 * Get the number of failed downloads.
	 *
	 * @return Number of failed downloads
	 */
	public int getFailedCount() {
		return failedDownloads.get();
	}

	/**
	 * Get the number of downloads currently in progress.
	 *
	 * @return Number of active downloads
	 */
	public int getActiveCount() {
		return activeDownloads.get();
	}

	/**
	 * Get the number of downloads waiting in the queue.
	 *
	 * @return Number of queued downloads
	 */
	public int getQueuedCount() {
		return downloadQueue.size();
	}

	/** Worker thread that processes downloads from the queue */
	private void downloadWorker() {
		while (!shutdownRequested || !downloadQueue.isEmpty()) {
			try {
				DownloadTask task = downloadQueue.poll(500, TimeUnit.MILLISECONDS);
				if (task != null) {
					activeDownloads.incrementAndGet();
					try {
						processDownload(task);
						completedDownloads.incrementAndGet();
						logger.debug(
								"Succeeded download for {} [{}]",
								task.metadata.filename(),
								task.scraper.getClass().getSimpleName());
					} catch (Exception e) {
						failedDownloads.incrementAndGet();
						task.scraper.fail("Failed to download " + task.metadata.filename(), e);
						logger.debug(
								"Failed download for {} [{}]",
								task.metadata.filename(),
								task.scraper.getClass().getSimpleName());
					} finally {
						activeDownloads.decrementAndGet();
					}
					logger.info(
							"Downloads: {} queued, {} active, {} completed, {} failed",
							downloadQueue.size(),
							activeDownloads.get(),
							completedDownloads.get(),
							failedDownloads.get());
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	/** Process a single download task */
	private void processDownload(DownloadTask task) throws IOException, InterruptedException {
		JdkMetadata metadata = task.metadata;
		String filename = metadata.filename();
		String url = metadata.url();

		if (filename == null || url == null) {
			return;
		}

		Path tempFile = Files.createTempFile("jdk-metadata-", "-" + filename);

		try {
			task.scraper.log("Downloading " + filename);
			httpUtils.downloadFile(url, tempFile);

			long size = Files.size(tempFile);

			// Compute hashes
			task.scraper.log("Computing hashes for " + filename);
			String md5 = HashUtils.computeHash(tempFile, "MD5");
			String sha1 = HashUtils.computeHash(tempFile, "SHA-1");
			String sha256 = HashUtils.computeHash(tempFile, "SHA-256");
			String sha512 = HashUtils.computeHash(tempFile, "SHA-512");

			// Save checksum files
			saveChecksumFile(checksumDir, filename, "md5", md5);
			saveChecksumFile(checksumDir, filename, "sha1", sha1);
			saveChecksumFile(checksumDir, filename, "sha256", sha256);
			saveChecksumFile(checksumDir, filename, "sha512", sha512);

			// Update metadata with download results
			DownloadResult result = new DownloadResult(md5, sha1, sha256, sha512, size);
			metadata.download(result);

			// Save metadata file
			Path metadataFile = metadataDir.resolve(metadata.metadataFilename());
			MetadataUtils.saveMetadataFile(metadataFile, metadata);

			// Report success
			task.scraper.log("Processed " + filename);
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	/** Save checksum to file */
	private void saveChecksumFile(Path checksumDir, String filename, String algorithm, String checksum)
			throws IOException {
		Path checksumFile = checksumDir.resolve(filename + "." + algorithm);
		Files.writeString(checksumFile, checksum + "  " + filename + "\n");
	}

	/** Internal class representing a download task */
	private record DownloadTask(JdkMetadata metadata, BaseScraper scraper) {}
}
