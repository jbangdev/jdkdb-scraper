package dev.jbang.jdkdb;

import dev.jbang.jdkdb.reporting.ProgressEvent;
import dev.jbang.jdkdb.reporting.ProgressReporter;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperFactory;
import dev.jbang.jdkdb.scraper.ScraperResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Main application class with CLI support */
@Command(
		name = "java-metadata-scraper",
		version = "1.0.0",
		description = "Scrapes JDK metadata from various vendors",
		mixinStandardHelpOptions = true)
public class Main implements Callable<Integer> {

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

	@Override
	public Integer call() throws Exception {
		// Handle list command
		if (listScrapers) {
			listAvailableScrapers();
			return 0;
		}

		// Determine thread count
		var threadCount = maxThreads > 0 ? maxThreads : Runtime.getRuntime().availableProcessors();

		System.out.println("Java Metadata Scraper");
		System.out.println("====================");
		System.out.println("Metadata directory: " + metadataDir.toAbsolutePath());
		System.out.println("Checksum directory: " + checksumDir.toAbsolutePath());
		System.out.println("Max parallel threads: " + threadCount);
		System.out.println();

		// Create progress reporter
		try (var reporter = new ProgressReporter()) {
			reporter.start();

			// Create scrapers
			var fact = ScraperFactory.create(metadataDir, checksumDir, reporter, fromStart, maxFailures, limitProgress);
			if (scraperIds == null) {
				scraperIds = new ArrayList<>(
						ScraperFactory.getAvailableScraperDiscoveries().keySet());
			}
			var scrapers = new HashMap<String, Scraper>();
			for (var scraperId : scraperIds) {
				scrapers.put(scraperId, fact.createScraper(scraperId));
			}

			if (scraperIds != null && !scraperIds.isEmpty()) {
				System.out.println("Running specific scrapers: " + String.join(", ", scraperIds));
			} else {
				System.out.println("Running all available scrapers");
			}

			System.out.println("Total scrapers: " + scrapers.size());
			System.out.println();

			long startTime = System.currentTimeMillis();

			// Execute scrapers in parallel
			try (var executor = Executors.newFixedThreadPool(threadCount)) {
				// Submit all scrapers and wrap them to report start/complete/failed events
				var futures = new ArrayList<Future<ScraperResult>>();
				for (var scraperEntry : scrapers.entrySet()) {
					Future<ScraperResult> future = executor.submit(() -> {
						String scraperName = scraperEntry.getKey();
						reporter.report(ProgressEvent.started(scraperName));
						try {
							ScraperResult result = scraperEntry.getValue().call();
							if (result.success()) {
								reporter.report(ProgressEvent.completed(scraperName));
							} else {
								reporter.report(ProgressEvent.failed(
										scraperName,
										result.error() != null ? result.error().getMessage() : "Unknown error",
										result.error()));
							}
							return result;
						} catch (Exception e) {
							reporter.report(ProgressEvent.failed(scraperName, e.getMessage(), e));
							return ScraperResult.failure(e);
						}
					});
					futures.add(future);
				}

				// Wait for all scrapers to complete and collect results
				var results = new ArrayList<ScraperResult>();
				for (var future : futures) {
					try {
						results.add(future.get());
					} catch (ExecutionException e) {
						System.err.println(
								"Scraper execution failed: " + e.getCause().getMessage());
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						System.err.println("Scraper execution interrupted");
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

				for (var result : results) {
					System.out.println(result);
					if (result.success()) {
						successful++;
						totalItems += result.itemsProcessed();
					} else {
						failed++;
					}
				}

				System.out.println();
				System.out.println("Total scrapers: " + results.size());
				System.out.println("Successful: " + successful);
				System.out.println("Failed: " + failed);
				System.out.println("Total items processed: " + totalItems);

				var endTime = System.currentTimeMillis();
				var duration = (endTime - startTime) / 1000.0;
				System.out.println();
				System.out.println("All scrapers completed in " + duration + " seconds");

				return failed > 0 ? 1 : 0;
			}
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
			e.printStackTrace();
			return 1;
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

	public static void main(String[] args) {
		int exitCode = new CommandLine(new Main()).execute(args);
		System.exit(exitCode);
	}
}
