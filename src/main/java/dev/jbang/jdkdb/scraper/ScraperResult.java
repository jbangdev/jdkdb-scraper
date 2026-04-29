package dev.jbang.jdkdb.scraper;

import dev.jbang.jdkdb.model.JdkMetadata;
import java.util.List;

/** Result of a scraper execution */
public record ScraperResult(
		boolean success,
		int itemsProcessed,
		int itemsSkipped,
		int itemsFailed,
		Exception error,
		List<JdkMetadata> allMetadata) {

	public static ScraperResult success(
			int itemsProcessed, int itemsSkipped, int itemsFailed, List<JdkMetadata> allMetadata) {
		return new ScraperResult(true, itemsProcessed, itemsSkipped, itemsFailed, null, List.copyOf(allMetadata));
	}

	public static ScraperResult failure(Exception error) {
		return new ScraperResult(false, 0, 0, 0, error, List.of());
	}

	@Override
	public String toString() {
		return success
				? "SUCCESS (%d items processed, %d items skipped, %d items failed)"
						.formatted(itemsProcessed, itemsSkipped, itemsFailed)
				: "FAILED - %s".formatted(error != null ? error.getMessage() : "Unknown error");
	}
}
