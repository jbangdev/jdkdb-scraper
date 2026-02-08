package dev.jbang.jdkdb.scraper;

import dev.jbang.jdkdb.reporting.ProgressReporter;
import dev.jbang.jdkdb.reporting.ScraperState;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Configuration record for scraper instances. Encapsulates the metadata directory, checksum
 * directory, logger, progress reporter, optional scraper state (for heartbeat), and run options.
 */
public record ScraperConfig(
		Path metadataDir,
		Path checksumDir,
		Logger logger,
		ProgressReporter reporter,
		ScraperState state,
		boolean fromStart,
		int maxFailureCount,
		int limitProgress) {}
