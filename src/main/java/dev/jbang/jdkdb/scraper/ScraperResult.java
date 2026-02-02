package dev.jbang.jdkdb.scraper;

/** Result of a scraper execution */
public record ScraperResult(boolean success, int itemsProcessed, Exception error) {

	public static ScraperResult success(int itemsProcessed) {
		return new ScraperResult(true, itemsProcessed, null);
	}

	public static ScraperResult failure(Exception error) {
		return new ScraperResult(false, 0, error);
	}

	@Override
	public String toString() {
		return success
				? "SUCCESS (%d items)".formatted(itemsProcessed)
				: "FAILED - %s".formatted(error != null ? error.getMessage() : "Unknown error");
	}
}
