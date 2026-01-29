package com.github.joschi.javametadata.scraper;

import java.util.concurrent.Callable;

/**
 * Interface for vendor scrapers that collect JDK metadata. Scrapers implement {@link Callable} to
 * support parallel execution.
 */
public interface Scraper extends Callable<ScraperResult> {

    /**
     * Get the unique identifier for this scraper.
     *
     * @return the scraper ID
     */
    String getScraperId();

    /**
     * Get the vendor name.
     *
     * @return the vendor name
     */
    String getVendorName();
}
