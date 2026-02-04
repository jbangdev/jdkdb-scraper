package dev.jbang.jdkdb.scraper;

import java.util.concurrent.Callable;

/**
 * Interface for vendor scrapers that collect JDK metadata. Scrapers implement {@link Callable} to
 * support parallel execution.
 */
public interface Scraper extends Callable<ScraperResult> {

	public enum When {
		ALWAYS,
		ONCE_A_DAY,
		ONCE_A_WEEK,
		ONCE_A_MONTH,
		NEVER
	}

	/** Factory interface for scraper discovery */
	interface Discovery {
		String name();

		String vendor();

		// IMPORTANT: for now all scrapers of the same vendor
		// must share the same schedule to avoid problems!!
		default When when() {
			return When.ALWAYS;
		}

		Scraper create(ScraperConfig config);
	}
}
