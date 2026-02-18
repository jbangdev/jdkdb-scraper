package dev.jbang.jdkdb.scraper.impl;

import dev.jbang.jdkdb.scraper.JavaNetBaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.Arrays;
import java.util.List;

/** Scraper for mainline OpenJDK releases */
public class OpenJdk extends JavaNetBaseScraper {
	public OpenJdk(ScraperConfig config) {
		super(config);
	}

	@Override
	protected List<String> getIndexUrls() {
		return Arrays.asList(
				"http://jdk.java.net/archive/",
				"http://jdk.java.net/25/",
				"http://jdk.java.net/26/",
				"http://jdk.java.net/27/");
	}

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return VENDOR;
		}

		@Override
		public String vendor() {
			return VENDOR;
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new OpenJdk(config);
		}
	}
}
