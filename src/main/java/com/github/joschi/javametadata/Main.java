package com.github.joschi.javametadata;

import com.github.joschi.javametadata.reporting.ProgressEvent;
import com.github.joschi.javametadata.reporting.ProgressReporter;
import com.github.joschi.javametadata.scraper.ScraperFactory;
import com.github.joschi.javametadata.scraper.ScraperResult;
import java.nio.file.Path;
import java.util.ArrayList;
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
            description =
                    "Comma-separated list of scraper IDs to run (if not specified, all scrapers run)",
            split = ",")
    private List<String> scraperIds;

    @Option(
            names = {"-l", "--list"},
            description = "List all available scraper IDs and exit")
    private boolean listScrapers;

    @Option(
            names = {"-t", "--threads"},
            description =
                    "Maximum number of parallel scraper threads (default: number of processors)",
            defaultValue = "-1")
    private int maxThreads;

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
            var scrapers =
                    (scraperIds != null && !scraperIds.isEmpty())
                            ? ScraperFactory.createScrapers(
                                    scraperIds, metadataDir, checksumDir, reporter)
                            : ScraperFactory.createAllScrapers(metadataDir, checksumDir, reporter);

            if (scraperIds != null && !scraperIds.isEmpty()) {
                System.out.println("Running specific scrapers: " + String.join(", ", scraperIds));
            } else {
                System.out.println("Running all available scrapers");
            }

            System.out.println("Total scrapers: " + scrapers.size());
            System.out.println();

            // Execute scrapers in parallel
            try (var executor = Executors.newFixedThreadPool(threadCount)) {
                // Submit all scrapers and wrap them to report start/complete/failed events
                var futures = new ArrayList<Future<ScraperResult>>();
                for (var scraper : scrapers) {
                    Future<ScraperResult> future =
                            executor.submit(
                                    () -> {
                                        String scraperId = scraper.getScraperId();
                                        reporter.report(ProgressEvent.started(scraperId));
                                        try {
                                            ScraperResult result = scraper.call();
                                            if (result.success()) {
                                                reporter.report(
                                                        ProgressEvent.completed(scraperId));
                                            } else {
                                                reporter.report(
                                                        ProgressEvent.failed(
                                                                scraperId,
                                                                result.error() != null
                                                                        ? result.error()
                                                                                .getMessage()
                                                                        : "Unknown error",
                                                                result.error()));
                                            }
                                            return result;
                                        } catch (Exception e) {
                                            reporter.report(
                                                    ProgressEvent.failed(
                                                            scraperId, e.getMessage(), e));
                                            return ScraperResult.failure(scraperId, e);
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
                    }
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

                return failed > 0 ? 1 : 0;
            }
        }
    }

    private void listAvailableScrapers() {
        System.out.println("Available Scrapers:");
        System.out.println("==================");

        try (var dummyReporter = new ProgressReporter()) {
            var ids =
                    ScraperFactory.getAvailableScraperIds(metadataDir, checksumDir, dummyReporter);

            for (var id : ids) {
                System.out.println("  - " + id);
            }

            System.out.println();
            System.out.println("Total: " + ids.size() + " scrapers");
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
