package dev.jbang.jdkdb.scraper.vendors;

import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.Arrays;
import java.util.List;

/** Scraper for mainline OpenJDK releases */
public class OpenJdk extends OpenJdkBaseScraper {
	public OpenJdk(ScraperConfig config) {
		super(config);
	}

	@Override
	protected List<String> getIndexUrls() {
		return Arrays.asList(
				"http://jdk.java.net/archive/",
				"http://jdk.java.net/21/",
				"http://jdk.java.net/22/",
				"http://jdk.java.net/23/",
				"http://jdk.java.net/24/",
				"http://jdk.java.net/25/",
				"http://jdk.java.net/26/");
	}

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "openjdk";
		}

		@Override
		public String vendor() {
			return "openjdk";
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new OpenJdk(config);
		}
	}
}
