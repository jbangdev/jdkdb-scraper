package dev.jbang.jdkdb;

import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.DefaultDownloadManager;
import dev.jbang.jdkdb.scraper.DownloadManager;
import dev.jbang.jdkdb.scraper.NoOpDownloadManager;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperFactory;
import dev.jbang.jdkdb.scraper.ScraperResult;
import dev.jbang.jdkdb.util.GitHubUtils;
import dev.jbang.jdkdb.util.MetadataUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Update command to scrape JDK metadata from various distros */
@Command(
		name = "update",
		description = "Scrape JDK metadata from various distros and update metadata files",
		mixinStandardHelpOptions = true)
public class UpdateCommand implements Callable<Integer> {
	private static final Logger logger = LoggerFactory.getLogger("command");

	@Option(
			names = {"-m", "--metadata-dir"},
			description = "Directory to store metadata files (default: db/metadata)",
			defaultValue = "db/metadata")
	private Path metadataDir;

	@Option(
			names = {"-x", "--index-dir"},
			description = "Directory to write generated index files to (default: db/metadata)",
			defaultValue = "db/metadata")
	private Path indexDir;

	@Option(
			names = {"-c", "--checksum-dir"},
			description = "Directory to store checksum files (default: db/checksums)",
			defaultValue = "db/checksums")
	private Path checksumDir;

	@Option(
			names = {"-p", "--prune-dir"},
			description =
					"Move prunable metadata and checksum files to this directory (retaining distro subfolder structure)")
	private Path pruneDir;

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
			names = {"--limit-total"},
			description = "Maximum total number of downloads to accept before stopping (default: unlimited)",
			defaultValue = "-1")
	private int limitTotal;

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

	@Option(
			names = {"--include"},
			description =
					"Include only these file types (e.g., tar_gz,zip). If specified, only these types will be downloaded.",
			split = ",")
	private List<JdkMetadata.FileType> includeFileTypes;

	@Option(
			names = {"--exclude"},
			description = "Exclude these file types (e.g., msi,exe). These types will not be downloaded.",
			split = ",")
	private List<JdkMetadata.FileType> excludeFileTypes;

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
			logger.error(
					"Invalid --skip-ea duration format: '{}'. Expected format: [number][d|w|m|y] (e.g., '30d', '6m', '1y')",
					skipEa);
			return 1;
		}

		// Process file type filter
		Set<JdkMetadata.FileType> fileTypeFilter = processFileTypeFilter(includeFileTypes, excludeFileTypes);

		GitHubUtils.setupGitHubToken();

		logger.info("Java Metadata Scraper - Update");
		logger.info("==============================");
		logger.info("Metadata directory: {}", metadataDir.toAbsolutePath());
		logger.info("Checksum directory: {}", checksumDir.toAbsolutePath());
		logger.info("Index directory: {}", indexDir.toAbsolutePath());
		logger.info("Max parallel threads: {}", threadCount);
		logger.info("");

		// Create and start download manager
		DownloadManager downloadManager;
		if (noDownload) {
			downloadManager = new NoOpDownloadManager(fileTypeFilter);
			downloadManager.start();
			logger.info("No-download mode enabled - files will not be downloaded");
		} else {
			downloadManager =
					new DefaultDownloadManager(threadCount, metadataDir, checksumDir, 3, limitTotal, fileTypeFilter);
			downloadManager.start();
			logger.info("Started download manager with {} download threads", threadCount);
		}
		if (fileTypeFilter != null) {
			logger.info("File type filter enabled: {}", fileTypeFilter);
		}
		logger.info("");

		// Create scrapers
		var fact = ScraperFactory.create(
				metadataDir, checksumDir, fromStart, maxFailures, limitProgress, skipEaDuration, downloadManager);
		var allDiscoveries = ScraperFactory.getAvailableScraperDiscoveries();
		if (scraperIds == null) {
			scraperIds = new ArrayList<>(allDiscoveries.keySet());
		}
		var scrapers = new HashMap<String, Scraper>();
		var affectedDistros = new HashSet<String>();
		for (var scraperId : scraperIds) {
			var discovery = allDiscoveries.get(scraperId);
			if (discovery == null) {
				logger.warn("Warning: Unknown scraper ID: {}", scraperId);
				continue;
			}

			// Check if scraper should run based on schedule
			if (!shouldRunScraper(discovery, metadataDir)) {
				logger.info("Skipping scraper '{}' - not scheduled to run yet ({})", scraperId, discovery.when());
				continue;
			}

			scrapers.put(scraperId, fact.createScraper(scraperId));
			// Track which distro this scraper affects
			affectedDistros.add(discovery.distro());
		}
		if (scrapers.isEmpty()) {
			logger.info("No scrapers scheduled to run.");
			return 0;
		}

		logger.info("Running scrapers: {}", String.join(", ", scrapers.keySet()));
		logger.info("Total scrapers: {}", scrapers.size());
		logger.info("");

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
					logger.error("Scraper execution failed: {}", e.getCause().getMessage());
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					logger.error("Scraper execution interrupted");
				}
			}

			// All scrapers have completed, signal download manager to shut down
			logger.info("");
			logger.info("All scrapers completed. Waiting for downloads to complete...");
			downloadManager.shutdown();

			try {
				downloadManager.awaitCompletion();
				logger.info("All downloads completed.");
				logger.info("  Total completed: {}", downloadManager.getCompletedCount());
				logger.info("  Total failed: {}", downloadManager.getFailedCount());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				logger.error("Download manager interrupted while waiting for completion");
			}

			pruneOldMetadata(results, allDiscoveries, scrapers);

			if (!noIndex) {
				// Generate all.json files for affected distro directories only
				logger.info("");
				logger.info("Generating all.json files for affected distro directories...");
				try {
					IndexCommand.generateIndices(metadataDir, indexDir, new ArrayList<>(affectedDistros), noDownload);
					logger.info("Successfully generated all.json files");
				} catch (Exception e) {
					logger.error("Failed to generate all.json files: {}", e.getMessage(), e);
				}
			}

			// Allow time for async logging to flush before printing summary
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}

			// Print summary
			logger.info("");
			logger.info("Execution Summary");
			logger.info("=================");

			var successful = 0;
			var failed = 0;
			var totalItems = 0;
			var totalSkipped = 0;
			var totalFailedItems = 0;

			for (var result : results.values()) {
				logger.info("{}", result);
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
			logger.info("");
			logger.info("Per-Scraper Breakdown");
			logger.info("=====================");
			for (var entry : results.entrySet()) {
				var scraperName = entry.getKey();
				var result = entry.getValue();
				logger.info("  {}:", scraperName);
				logger.info("    Status: {}", result.success() ? "SUCCESS" : "FAILED");
				logger.info("    Processed: {}", result.itemsProcessed());
				logger.info("    Skipped: {}", result.itemsSkipped());
				logger.info("    Failures: {}", result.itemsFailed());
				if (!result.success()) {
					logger.info(
							"    Error: {}",
							result.error() != null ? result.error().getMessage() : "Unknown error");
				}
			}
			logger.info("");
			logger.info("Totals");
			logger.info("======");

			logger.info("");
			logger.info("Total scrapers: {}", results.size());
			logger.info("Successful: {}", successful);
			logger.info("Failed: {}", failed);
			logger.info("Total items processed: {}", totalItems);
			logger.info("Total items skipped: {}", totalSkipped);
			logger.info("Total items failed: {}", totalFailedItems);

			var endTime = System.currentTimeMillis();
			var duration = (endTime - startTime) / 1000.0;
			logger.info("");
			logger.info("All scrapers completed in {} seconds", duration);

			return successful > 0 ? 0 : 1;
		}
	}

	/**
	 * Check if a scraper should run based on its scheduling configuration and the last update time
	 * of the distro's all.json file.
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
		Path distroAllJson = metadataDir.resolve(discovery.distro()).resolve("all.json");

		Instant lastUpdate = getLastModifiedTime(distroAllJson);
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
		logger.info("Available Scrapers:");
		logger.info("==================");

		var names = ScraperFactory.getAvailableScraperDiscoveries().keySet().stream()
				.sorted()
				.toList();

		for (var name : names) {
			logger.info("  - {}", name);
		}

		logger.info("");
		logger.info("Total: {} scrapers", names.size());
	}

	private void pruneOldMetadata(
			Map<String, ScraperResult> results,
			Map<String, Scraper.Discovery> allDiscoveries,
			Map<String, Scraper> scrapers) {
		// 1. Collect all local metadata file paths (exclude index files)
		var candidatePaths = new HashSet<Path>();
		try (var pathStream = Files.walk(metadataDir, 2)) {
			pathStream
					.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().endsWith(".json"))
					.filter(p -> !p.getFileName().toString().equals("all.json"))
					.filter(p -> !p.getFileName().toString().equals("latest.json"))
					.map(p -> p.toAbsolutePath().normalize())
					.forEach(candidatePaths::add);
		} catch (IOException e) {
			logger.error("Failed to collect metadata files for pruning: {}", e.getMessage(), e);
			return;
		}

		// 2. Build protected-distro set: scrapers not scheduled to run, or that failed / returned empty results
		var protectedDistros = new HashSet<String>();
		for (var entry : allDiscoveries.entrySet()) {
			if (!scrapers.containsKey(entry.getKey())) {
				protectedDistros.add(entry.getValue().distro());
			}
		}
		for (var entry : results.entrySet()) {
			var result = entry.getValue();
			var discovery = allDiscoveries.get(entry.getKey());
			if (discovery == null) continue;
			if (!result.success() || result.allMetadata().isEmpty()) {
				protectedDistros.add(discovery.distro());
			}
		}

		// 3. Remove files belonging to protected distros from candidates
		candidatePaths.removeIf(p -> {
			var parent = p.getParent();
			return parent != null
					&& protectedDistros.contains(parent.getFileName().toString());
		});

		// 4. Remove known-good files (still publicly listed by a successful scraper)
		for (var entry : results.entrySet()) {
			var result = entry.getValue();
			if (!result.success() || result.allMetadata().isEmpty()) continue;
			var discovery = allDiscoveries.get(entry.getKey());
			if (discovery == null) continue;
			String distro = discovery.distro();
			for (var metadata : result.allMetadata()) {
				Path expectedPath = metadataDir
						.resolve(distro)
						.resolve(metadata.metadataFile())
						.toAbsolutePath()
						.normalize();
				candidatePaths.remove(expectedPath);
			}
		}

		// 5. Log summary
		var byDistro = new TreeMap<String, List<Path>>();
		for (var path : candidatePaths) {
			String distro = path.getParent().getFileName().toString();
			byDistro.computeIfAbsent(distro, k -> new ArrayList<>()).add(path);
		}

		logger.info("");
		logger.info("Prunable Metadata Files");
		logger.info("=======================");
		if (candidatePaths.isEmpty()) {
			logger.info("No prunable metadata files found.");
			return;
		}
		logger.info("Total prunable files: {}", candidatePaths.size());
		for (var entry : byDistro.entrySet()) {
			logger.info("  {}: {} file(s)", entry.getKey(), entry.getValue().size());
		}

		// 6. Move files if --prune-dir was specified
		if (pruneDir != null) {
			logger.info("Moving prunable files to: {}", pruneDir.toAbsolutePath());
			int moved = 0;
			int errors = 0;
			for (var entry : byDistro.entrySet()) {
				String distro = entry.getKey();
				Path targetDistroDir = pruneDir.resolve(distro);
				try {
					Files.createDirectories(targetDistroDir);
				} catch (IOException e) {
					logger.error("Failed to create prune directory {}: {}", targetDistroDir, e.getMessage());
					errors++;
					continue;
				}
				for (var srcJson : entry.getValue()) {
					// Move the .json metadata file
					Path targetJson = targetDistroDir.resolve(srcJson.getFileName());
					try {
						Files.move(srcJson, targetJson, StandardCopyOption.REPLACE_EXISTING);
						moved++;
					} catch (IOException e) {
						logger.error("Failed to move {}: {}", srcJson, e.getMessage());
						errors++;
						continue;
					}
					// Move corresponding checksum files alongside the json
					String baseName = srcJson.getFileName().toString();
					baseName = baseName.substring(0, baseName.length() - 5); // strip ".json"
					for (var ext : List.of(".md5", ".sha1", ".sha256", ".sha512")) {
						Path srcChecksum = checksumDir.resolve(distro).resolve(baseName + ext);
						if (Files.exists(srcChecksum)) {
							Path targetChecksum = targetDistroDir.resolve(baseName + ext);
							try {
								Files.move(srcChecksum, targetChecksum, StandardCopyOption.REPLACE_EXISTING);
							} catch (IOException e) {
								logger.error("Failed to move checksum file {}: {}", srcChecksum, e.getMessage());
								errors++;
							}
						}
					}
				}
			}
			logger.info("Moved {} metadata file(s) to {}", moved, pruneDir.toAbsolutePath());
			if (errors > 0) {
				logger.warn("{} error(s) occurred during pruning", errors);
			}
		} else {
			logger.info("Use --prune-dir to move prunable files to a separate directory.");
		}
	}

	/**
	 * Process the include and exclude file type options to create a filter set.
	 *
	 * @param includeFileTypes List of file types to include (null or empty means include all)
	 * @param excludeFileTypes List of file types to exclude (null or empty means exclude none)
	 * @return A set of file types to accept, or null if no filtering should be applied
	 */
	private Set<JdkMetadata.FileType> processFileTypeFilter(
			List<JdkMetadata.FileType> includeFileTypes, List<JdkMetadata.FileType> excludeFileTypes) {
		if ((includeFileTypes == null || includeFileTypes.isEmpty())
				&& (excludeFileTypes == null || excludeFileTypes.isEmpty())) {
			return null; // No filtering
		}

		Set<JdkMetadata.FileType> result;
		if (includeFileTypes != null && !includeFileTypes.isEmpty()) {
			// Start with only the included types
			result = EnumSet.copyOf(includeFileTypes);
		} else {
			// Start with all types
			result = EnumSet.allOf(JdkMetadata.FileType.class);
		}

		// Remove excluded types
		if (excludeFileTypes != null && !excludeFileTypes.isEmpty()) {
			result.removeAll(excludeFileTypes);
		}

		return result.isEmpty() ? null : result;
	}
}
