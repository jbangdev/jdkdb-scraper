package dev.jbang.jdkdb.scraper;

import java.nio.file.Path;
import org.slf4j.Logger;

/**
 * Configuration record for scraper instances. Encapsulates the metadata directory, checksum
 * directory, logger, download manager, and whether to ignore existing metadata files.
 */
public record ScraperConfig(
		Path metadataDir,
		Path checksumDir,
		Logger logger,
		boolean fromStart,
		int maxFailureCount,
		int limitProgress,
		DownloadManager downloadManager) {}
