package dev.jbang.jdkdb.scraper;

import java.util.concurrent.Callable;

/**
 * Interface for vendor scrapers that collect JDK metadata. Scrapers implement {@link Callable} to
 * support parallel execution.
 */
public interface Scraper extends Callable<ScraperResult> {

	/** Factory interface for scraper discovery */
	interface Discovery {
		String name();

		String vendor();

		Scraper create(ScraperConfig config);
	}
}
