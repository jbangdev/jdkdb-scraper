package dev.jbang.jdkdb.scraper.vendors;

import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.ArrayList;
import java.util.List;

/** Scraper for IBM Semeru Open Edition releases */
public class Semeru extends SemeruBaseScraper {
	private static final String VENDOR = "semeru";

	// List of Java versions for open edition
	private static final List<String> JAVA_VERSIONS =
			List.of("8", "11", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25");

	public Semeru(ScraperConfig config) {
		super(config);
	}

	@Override
	protected List<String> getJavaVersions() {
		return JAVA_VERSIONS;
	}

	@Override
	protected String getFilenamePrefix() {
		return "ibm-semeru-open-";
	}

	@Override
	protected String getVendor() {
		return VENDOR;
	}

	@Override
	protected List<String> getAdditionalFeatures() {
		return new ArrayList<>();
	}

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "semeru";
		}

		@Override
		public String vendor() {
			return "semeru";
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new Semeru(config);
		}
	}
}
