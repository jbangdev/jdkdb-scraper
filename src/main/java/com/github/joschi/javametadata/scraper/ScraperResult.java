package com.github.joschi.javametadata.scraper;

/** Result of a scraper execution */
public record ScraperResult(
        String scraperId, boolean success, int itemsProcessed, Exception error) {

    public static ScraperResult success(String scraperId, int itemsProcessed) {
        return new ScraperResult(scraperId, true, itemsProcessed, null);
    }

    public static ScraperResult failure(String scraperId, Exception error) {
        return new ScraperResult(scraperId, false, 0, error);
    }

    @Override
    public String toString() {
        return success
                ? "%s: SUCCESS (%d items)".formatted(scraperId, itemsProcessed)
                : "%s: FAILED - %s"
                        .formatted(scraperId, error != null ? error.getMessage() : "Unknown error");
    }
}
