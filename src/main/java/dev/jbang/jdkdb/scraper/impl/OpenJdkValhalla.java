package dev.jbang.jdkdb.scraper.impl;

import dev.jbang.jdkdb.scraper.JavaNetBaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.*;

/** Scraper for OpenJDK Project Valhalla early access builds */
public class OpenJdkValhalla extends JavaNetBaseScraper {

	public OpenJdkValhalla(ScraperConfig config) {
		super(config);
	}

	@Override
	protected List<String> getIndexUrls() {
		return Collections.singletonList("http://jdk.java.net/valhalla/");
	}

	@Override
	protected String getFeature() {
		return "valhalla";
	}

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "openjdk-valhalla";
		}

		@Override
		public String vendor() {
			return VENDOR;
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new OpenJdkValhalla(config);
		}
	}
}
