package dev.jbang.jdkdb.scraper;

import dev.jbang.jdkdb.model.JdkMetadata;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;
import org.slf4j.Logger;

/**
 * Configuration record for scraper instances.
 */
public record ScraperConfig(
		Path metadataDir,
		Path checksumDir,
		Logger logger,
		boolean fromStart,
		int maxFailureCount,
		int limitProgress,
		Duration skipEaDuration,
		Consumer<JdkMetadata> submitDownload) {}
