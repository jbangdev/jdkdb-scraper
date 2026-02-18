package dev.jbang.jdkdb.scraper;

import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.util.ArchiveUtils;
import dev.jbang.jdkdb.util.HashUtils;
import dev.jbang.jdkdb.util.HttpUtils;
import dev.jbang.jdkdb.util.MetadataUtils;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
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
	private final int maxDownloadsPerHost;
	private final ConcurrentHashMap<String, AtomicInteger> activeDownloadsPerHost;

	private static final Logger logger = LoggerFactory.getLogger(DefaultDownloadManager.class);
	private static final int DEFAULT_MAX_DOWNLOADS_PER_HOST = 3;

	/**
	 * Create a new DefaultDownloadManager.
	 *
	 * @param threadCount Number of parallel download threads
	 * @param metadataDir The directory to save metadata files
	 * @param checksumDir The directory to save checksum files
	 */
	public DefaultDownloadManager(int threadCount, Path metadataDir, Path checksumDir) {
		this(threadCount, metadataDir, checksumDir, DEFAULT_MAX_DOWNLOADS_PER_HOST);
	}

	/**
	 * Create a new DefaultDownloadManager.
	 *
	 * @param threadCount Number of parallel download threads
	 * @param metadataDir The directory to save metadata files
	 * @param checksumDir The directory to save checksum files
	 * @param maxDownloadsPerHost Maximum number of concurrent downloads per host (default: 3)
	 */
	public DefaultDownloadManager(int threadCount, Path metadataDir, Path checksumDir, int maxDownloadsPerHost) {
		this.downloadQueue = new LinkedBlockingQueue<>();
		this.executorService = Executors.newFixedThreadPool(threadCount);
		this.httpUtils = new HttpUtils();
		this.activeDownloads = new AtomicInteger(0);
		this.completedDownloads = new AtomicInteger(0);
		this.failedDownloads = new AtomicInteger(0);
		this.metadataDir = metadataDir;
		this.checksumDir = checksumDir;
		this.shutdownRequested = false;
		this.maxDownloadsPerHost = maxDownloadsPerHost;
		this.activeDownloadsPerHost = new ConcurrentHashMap<>();
	}

	/**
	 * Start the download worker threads. Should be called once after construction.
	 */
	@Override
	public void start() {
		logger.info(
				"Starting DownloadManager with {} threads, max {} downloads per host",
				((ThreadPoolExecutor) executorService).getCorePoolSize(),
				maxDownloadsPerHost);
		int threadCount = ((ThreadPoolExecutor) executorService).getCorePoolSize();
		for (int i = 0; i < threadCount; i++) {
			executorService.submit(this::downloadWorker);
		}
	}

	/**
	 * Submit a metadata item for download.
	 *
	 * @param metadata The JDK metadata containing the URL to download
	 * @param vendor The vendor of the JDK
	 * @param downloadLogger The logger for progress reporting
	 */
	@Override
	public void submit(JdkMetadata metadata, String vendor, Logger downloadLogger) {
		if (shutdownRequested) {
			throw new IllegalStateException("Cannot submit downloads after shutdown requested");
		}
		if (metadata.url() == null || metadata.filename() == null) {
			return;
		}
		try {
			downloadQueue.put(new DownloadTask(metadata, vendor, downloadLogger));
			downloadLogger.info("Queued download for " + metadata.filename());
			logger.debug("Submitted download for {} - {}", vendor, metadata.filename());
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
	@Override
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
	@Override
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
	@Override
	public int getCompletedCount() {
		return completedDownloads.get();
	}

	/**
	 * Get the number of failed downloads.
	 *
	 * @return Number of failed downloads
	 */
	@Override
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
					// Extract host from URL
					String host = extractHost(task.metadata.url());
					if (host == null) {
						// Invalid URL, log failure and skip
						failedDownloads.incrementAndGet();
						task.downloadLogger()
								.error("Invalid URL for {}: {}", task.metadata.filename(), task.metadata.url());
						logger.debug(
								"Failed download for {} [{}] - invalid URL", task.metadata.filename(), task.vendor);
						continue;
					}

					// Check if we can download from this host
					AtomicInteger hostCount = activeDownloadsPerHost.computeIfAbsent(host, k -> new AtomicInteger(0));
					int currentCount = hostCount.get();

					if (currentCount >= maxDownloadsPerHost) {
						// Host limit reached, put task back at end of queue and try next one
						downloadQueue.offer(task);
						// Small sleep to avoid busy waiting when all hosts are at limit
						Thread.sleep(100);
						continue;
					}

					// Increment host counter and proceed with download
					hostCount.incrementAndGet();
					activeDownloads.incrementAndGet();
					try {
						processDownload(task);
						completedDownloads.incrementAndGet();
						logger.debug("Succeeded download for {} [{}]", task.metadata.filename(), task.vendor);
					} catch (Exception e) {
						failedDownloads.incrementAndGet();
						task.downloadLogger().error("Failed to download {}", task.metadata.filename(), e);
						logger.debug("Failed download for {} [{}]", task.metadata.filename(), task.vendor);
					} finally {
						activeDownloads.decrementAndGet();
						// Decrement host counter
						int newCount = hostCount.decrementAndGet();
						// Clean up if no more active downloads for this host
						if (newCount == 0) {
							activeDownloadsPerHost.remove(host, hostCount);
						}
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
			task.downloadLogger().info("Downloading " + filename);
			httpUtils.downloadFile(url, tempFile);

			long size = Files.size(tempFile);

			// Compute hashes
			task.downloadLogger().info("Computing hashes for " + filename);
			String md5 = HashUtils.computeHash(tempFile, "MD5");
			String sha1 = HashUtils.computeHash(tempFile, "SHA-1");
			String sha256 = HashUtils.computeHash(tempFile, "SHA-256");
			String sha512 = HashUtils.computeHash(tempFile, "SHA-512");

			// Save checksum files
			Path vendorChecksumDir = checksumDir.resolve(task.vendor);
			Files.createDirectories(vendorChecksumDir);
			saveChecksumFile(vendorChecksumDir, filename, "md5", md5);
			saveChecksumFile(vendorChecksumDir, filename, "sha1", sha1);
			saveChecksumFile(vendorChecksumDir, filename, "sha256", sha256);
			saveChecksumFile(vendorChecksumDir, filename, "sha512", sha512);

			// Update metadata with download results
			DownloadResult result = new DownloadResult(md5, sha1, sha256, sha512, size);
			metadata.download(result);

			// Extract and parse release info from archive
			try {
				task.downloadLogger().info("Extracting release info from " + filename);
				Map<String, String> releaseInfo = ArchiveUtils.extractReleaseInfo(tempFile, filename);
				if (releaseInfo != null && !releaseInfo.isEmpty()) {
					metadata.releaseInfo(releaseInfo);
					task.downloadLogger()
							.debug("Extracted release info with " + releaseInfo.size() + " properties from "
									+ filename);
				} else {
					metadata.releaseInfo(Collections.emptyMap());
					task.downloadLogger().debug("No release info found in " + filename);
				}
			} catch (Exception e) {
				// Don't fail the download if release extraction fails
				task.downloadLogger().warn("Failed to extract release info from " + filename, e);
			}

			// Save metadata file
			Path vendorMetadataDir = metadataDir.resolve("vendor").resolve(task.vendor);
			Files.createDirectories(vendorMetadataDir);
			Path metadataFile = vendorMetadataDir.resolve(metadata.metadataFile());
			MetadataUtils.saveMetadataFile(metadataFile, metadata);

			// Apply the original file timestamp to the metadata file
			try {
				var fileTime = Files.getLastModifiedTime(tempFile);
				Files.setLastModifiedTime(metadataFile, fileTime);
			} catch (IOException e) {
				// Ignore if we can't set the timestamp
			}

			// Report success
			task.downloadLogger().info("Processed " + filename);
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

	/**
	 * Extract the host from a URL.
	 *
	 * @param urlString The URL string
	 * @return The host, or null if the URL is invalid
	 */
	private String extractHost(String urlString) {
		if (urlString == null) {
			return null;
		}
		try {
			URL url = new URL(urlString);
			return url.getHost();
		} catch (MalformedURLException e) {
			logger.warn("Invalid URL: {}", urlString);
			return null;
		}
	}

	/** Internal class representing a download task */
	private record DownloadTask(JdkMetadata metadata, String vendor, Logger downloadLogger) {}
}
