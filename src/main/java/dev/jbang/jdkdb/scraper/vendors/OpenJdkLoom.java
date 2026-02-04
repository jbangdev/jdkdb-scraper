package dev.jbang.jdkdb.scraper.vendors;

import dev.jbang.jdkdb.scraper.JavaNetBaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.*;

/** Scraper for OpenJDK Project Loom early access builds */
public class OpenJdkLoom extends JavaNetBaseScraper {

	public OpenJdkLoom(ScraperConfig config) {
		super(config);
	}

	@Override
	protected List<String> getIndexUrls() {
		return Collections.singletonList("https://jdk.java.net/loom/");
	}

	@Override
	protected String getFeature() {
		return "loom";
	}

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "openjdk-loom";
		}

		@Override
		public String vendor() {
			return VENDOR;
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new OpenJdkLoom(config);
		}
	}
}
