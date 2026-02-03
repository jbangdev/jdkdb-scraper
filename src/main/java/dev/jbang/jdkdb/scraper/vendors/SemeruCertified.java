package dev.jbang.jdkdb.scraper.vendors;

import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.List;

/** Scraper for IBM Semeru Certified Edition releases */
public class SemeruCertified extends SemeruBaseScraper {
	private static final String VENDOR = "semeru";

	// List of Java versions for certified edition
	private static final List<String> JAVA_VERSIONS =
			List.of("11-certified", "17-certified", "21-certified", "25-certified");

	public SemeruCertified(ScraperConfig config) {
		super(config);
	}

	@Override
	protected List<String> getJavaVersions() {
		return JAVA_VERSIONS;
	}

	@Override
	protected String getFilenamePrefix() {
		return "ibm-semeru-certified-";
	}

	@Override
	protected String getVendor() {
		return VENDOR;
	}

	@Override
	protected List<String> getAdditionalFeatures() {
		return List.of("certified");
	}

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "semeru-certified";
		}

		@Override
		public String vendor() {
			return VENDOR;
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new SemeruCertified(config);
		}
	}
}
