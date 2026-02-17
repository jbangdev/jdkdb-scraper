package dev.jbang.jdkdb;

import dev.jbang.jdkdb.scraper.DefaultDownloadManager;
import dev.jbang.jdkdb.scraper.DownloadManager;
import dev.jbang.jdkdb.scraper.NoOpDownloadManager;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperFactory;
import dev.jbang.jdkdb.scraper.ScraperResult;
import dev.jbang.jdkdb.util.MetadataUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Update command to scrape JDK metadata from various vendors */
@Command(
		name = "update",
		description = "Scrape JDK metadata from various vendors and update metadata files",
		mixinStandardHelpOptions = true)
public class UpdateCommand implements Callable<Integer> {

	@Option(
			names = {"-m", "--metadata-dir"},
			description = "Directory to store metadata files (default: docs/metadata)",
			defaultValue = "docs/metadata")
	private Path metadataDir;

	@Option(
			names = {"-c", "--checksum-dir"},
			description = "Directory to store checksum files (default: docs/checksums)",
			defaultValue = "docs/checksums")
	private Path checksumDir;

	@Option(
			names = {"-s", "--scrapers"},
			description = "Comma-separated list of scraper IDs to run (if not specified, all scrapers run)",
			split = ",")
	private List<String> scraperIds;

	@Option(
			names = {"-l", "--list"},
			description = "List all available scraper IDs and exit")
	private boolean listScrapers;

	@Option(
			names = {"-t", "--threads"},
			description = "Maximum number of parallel scraper threads (default: number of processors)",
			defaultValue = "-1")
	private int maxThreads;

	@Option(
			names = {"--from-start"},
			description = "Ignore existing metadata files and scrape all items from the start")
	private boolean fromStart;

	@Option(
			names = {"--max-failures"},
			description = "Maximum number of allowed failures per scraper before aborting that scraper (default: 10)",
			defaultValue = "10")
	private int maxFailures;

	@Option(
			names = {"--limit-progress"},
			description =
					"Maximum number of metadata items to process per scraper before aborting (default: unlimited)",
			defaultValue = "-1")
	private int limitProgress;

	@Option(
			names = {"--no-download"},
			description = "Skip downloading files and only generate metadata (for testing/dry-run)")
	private boolean noDownload;

	@Option(
			names = {"--no-index"},
			description = "Skip generating index files (for testing/dry-run)")
	private boolean noIndex;

	@Option(
			names = {"--skip-ea"},
			description =
					"Skip early access (EA) releases older than the specified duration (e.g., '6m' for 6 months, '1y' for 1 year) (default: 6m)",
			defaultValue = "6m")
	private String skipEa;

	@Override
	public Integer call() throws Exception {
		// Handle list command
		if (listScrapers) {
			listAvailableScrapers();
			return 0;
		}

		// Determine thread count
		var threadCount = maxThreads > 0 ? maxThreads : Runtime.getRuntime().availableProcessors();

		// Parse skip-ea duration
		Duration skipEaDuration = MetadataUtils.parseDuration(skipEa);
		if (skipEaDuration == null) {
			System.err.println("Invalid --skip-ea duration format: '" + skipEa
					+ "'. Expected format: [number][d|w|m|y] (e.g., '30d', '6m', '1y')");
			return 1;
		}

		System.out.println("Java Metadata Scraper - Update");
		System.out.println("==============================");
		System.out.println("Metadata directory: " + metadataDir.toAbsolutePath());
		System.out.println("Checksum directory: " + checksumDir.toAbsolutePath());
		System.out.println("Max parallel threads: " + threadCount);
		System.out.println();

		// Create and start download manager
		DownloadManager downloadManager;
		if (noDownload) {
			downloadManager = new NoOpDownloadManager();
			downloadManager.start();
			System.out.println("No-download mode enabled - files will not be downloaded");
		} else {
			downloadManager = new DefaultDownloadManager(threadCount, metadataDir, checksumDir);
			downloadManager.start();
			System.out.println("Started download manager with " + threadCount + " download threads");
		}
		System.out.println();

		// Create scrapers
		var fact = ScraperFactory.create(
				metadataDir, checksumDir, fromStart, maxFailures, limitProgress, skipEaDuration, downloadManager);
		var allDiscoveries = ScraperFactory.getAvailableScraperDiscoveries();
		if (scraperIds == null) {
			scraperIds = new ArrayList<>(allDiscoveries.keySet());
		}
		var scrapers = new HashMap<String, Scraper>();
		var affectedVendors = new HashSet<String>();
		for (var scraperId : scraperIds) {
			var discovery = allDiscoveries.get(scraperId);
			if (discovery == null) {
				System.err.println("Warning: Unknown scraper ID: " + scraperId);
				continue;
			}

			// Check if scraper should run based on schedule
			if (!shouldRunScraper(discovery, metadataDir)) {
				System.out.println(
						"Skipping scraper '" + scraperId + "' - not scheduled to run yet (" + discovery.when() + ")");
				continue;
			}

			scrapers.put(scraperId, fact.createScraper(scraperId));
			// Track which vendor this scraper affects
			affectedVendors.add(discovery.vendor());
		}
		if (scrapers.isEmpty()) {
			System.out.println("No scrapers scheduled to run.");
			return 0;
		}

		System.out.println("Running scrapers: " + String.join(", ", scrapers.keySet()));
		System.out.println("Total scrapers: " + scrapers.size());
		System.out.println();

		long startTime = System.currentTimeMillis();

		// Execute scrapers in parallel
		try (var executor = Executors.newFixedThreadPool(threadCount)) {
			// Submit all scrapers and wrap them to report start/complete/failed events
			var futures = new ArrayList<Future<ScraperResult>>();
			for (var scraperEntry : scrapers.entrySet()) {
				Future<ScraperResult> future = executor.submit(() -> {
					try {
						return scraperEntry.getValue().call();
					} catch (Exception e) {
						return ScraperResult.failure(e);
					}
				});
				futures.add(future);
			}

			// Wait for all scrapers to complete and collect results
			var results = new HashMap<String, ScraperResult>();
			var scraperNames = new ArrayList<>(scrapers.keySet());
			for (int i = 0; i < futures.size(); i++) {
				try {
					results.put(scraperNames.get(i), futures.get(i).get());
				} catch (ExecutionException e) {
					System.err.println(
							"Scraper execution failed: " + e.getCause().getMessage());
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					System.err.println("Scraper execution interrupted");
				}
			}

			// All scrapers have completed, signal download manager to shut down
			System.out.println();
			System.out.println("All scrapers completed. Waiting for downloads to complete...");
			downloadManager.shutdown();

			try {
				downloadManager.awaitCompletion();
				System.out.println("All downloads completed.");
				System.out.println("  Total completed: " + downloadManager.getCompletedCount());
				System.out.println("  Total failed: " + downloadManager.getFailedCount());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				System.err.println("Download manager interrupted while waiting for completion");
			}

			if (!noIndex) {
				// Generate all.json files for affected vendor directories only
				System.out.println();
				System.out.println("Generating all.json files for affected vendor directories...");
				try {
					IndexCommand.generateIndices(metadataDir, new ArrayList<>(affectedVendors), false);
					System.out.println("Successfully generated all.json files");
				} catch (Exception e) {
					System.err.println("Failed to generate all.json files: " + e.getMessage());
					e.printStackTrace();
				}

				// Generate comprehensive indices (all.json, ga.json, ea.json, and nested structures)
				System.out.println();
				try {
					MetadataUtils.generateComprehensiveIndices(metadataDir, false);
				} catch (Exception e) {
					System.err.println("Failed to generate comprehensive indices: " + e.getMessage());
					e.printStackTrace();
				}
			}

			// Allow time for async logging to flush before printing summary
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}

			// Print summary
			System.out.println();
			System.out.println("Execution Summary");
			System.out.println("=================");

			var successful = 0;
			var failed = 0;
			var totalItems = 0;
			var totalSkipped = 0;
			var totalFailedItems = 0;

			for (var result : results.values()) {
				System.out.println(result);
				if (result.success()) {
					successful++;
					totalItems += result.itemsProcessed();
					totalSkipped += result.itemsSkipped();
					totalFailedItems += result.itemsFailed();
				} else {
					failed++;
				}
			}

			// Per-scraper breakdown
			System.out.println();
			System.out.println("Per-Scraper Breakdown");
			System.out.println("=====================");
			for (var entry : results.entrySet()) {
				var scraperName = entry.getKey();
				var result = entry.getValue();
				System.out.printf("  %s:%n", scraperName);
				System.out.printf("    Status: %s%n", result.success() ? "SUCCESS" : "FAILED");
				System.out.printf("    Processed: %d%n", result.itemsProcessed());
				System.out.printf("    Skipped: %d%n", result.itemsSkipped());
				System.out.printf("    Failures: %d%n", result.itemsFailed());
				if (!result.success()) {
					System.out.printf(
							"    Error: %s%n",
							result.error() != null ? result.error().getMessage() : "Unknown error");
				}
			}
			System.out.println();
			System.out.println("Totals");
			System.out.println("======");

			System.out.println();
			System.out.println("Total scrapers: " + results.size());
			System.out.println("Successful: " + successful);
			System.out.println("Failed: " + failed);
			System.out.println("Total items processed: " + totalItems);
			System.out.println("Total items skipped: " + totalSkipped);
			System.out.println("Total items failed: " + totalFailedItems);

			var endTime = System.currentTimeMillis();
			var duration = (endTime - startTime) / 1000.0;
			System.out.println();
			System.out.println("All scrapers completed in " + duration + " seconds");

			return failed > 0 ? 1 : 0;
		}
	}

	/**
	 * Check if a scraper should run based on its scheduling configuration and the last update time
	 * of the vendor's all.json file.
	 *
	 * @param discovery The scraper discovery with scheduling information
	 * @param metadataDir The metadata directory
	 * @return true if the scraper should run, false otherwise
	 */
	private boolean shouldRunScraper(Scraper.Discovery discovery, Path metadataDir) {
		Scraper.When when = discovery.when();

		// NEVER scrapers should never run
		if (when == Scraper.When.NEVER) {
			return false;
		}

		// ALWAYS scrapers should always run
		if (when == Scraper.When.ALWAYS) {
			return true;
		}

		// For other schedules, check the last update time
		Path vendorAllJson =
				metadataDir.resolve("vendor").resolve(discovery.vendor()).resolve("all.json");

		Instant lastUpdate = getLastModifiedTime(vendorAllJson);
		if (lastUpdate == null) {
			// No all.json exists, so we should run the scraper
			return true;
		}

		// IF_MISSING scrapers should only run if their all.json does not exist
		if (when == Scraper.When.IF_MISSING) {
			return false;
		}

		Instant now = Instant.now();
		Duration timeSinceUpdate = Duration.between(lastUpdate, now);

		return switch (when) {
			case ONCE_A_DAY -> timeSinceUpdate.compareTo(Duration.ofDays(1)) >= 0;
			case ONCE_A_WEEK -> timeSinceUpdate.compareTo(Duration.ofDays(7)) >= 0;
			case ONCE_A_MONTH -> timeSinceUpdate.compareTo(Duration.ofDays(30)) >= 0;
			default -> true; // ALWAYS or unknown
		};
	}

	/**
	 * Get the last modified time of a file, or null if the file doesn't exist.
	 *
	 * @param path The file path
	 * @return The last modified time as an Instant, or null if the file doesn't exist
	 */
	private Instant getLastModifiedTime(Path path) {
		try {
			if (!Files.exists(path)) {
				return null;
			}
			FileTime fileTime = Files.getLastModifiedTime(path);
			return fileTime.toInstant();
		} catch (IOException e) {
			// If we can't read the file time, treat it as if the file doesn't exist
			return null;
		}
	}

	private void listAvailableScrapers() {
		System.out.println("Available Scrapers:");
		System.out.println("==================");

		var names = ScraperFactory.getAvailableScraperDiscoveries().keySet().stream()
				.sorted()
				.toList();

		for (var name : names) {
			System.out.println("  - " + name);
		}

		System.out.println();
		System.out.println("Total: " + names.size() + " scrapers");
	}
}
