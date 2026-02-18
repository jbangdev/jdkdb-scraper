package dev.jbang.jdkdb.scraper.impl;

import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.List;

/** Scraper for IBM Semeru Certified Edition releases */
public class SemeruCertified extends SemeruBaseScraper {
	private static final String VENDOR = "semeru";

	public SemeruCertified(ScraperConfig config) {
		super(config);
	}

	@Override
	protected String getRepoSearchString() {
		return "semeru+certified";
	}

	@Override
	protected String getRepoPattern() {
		return "^semeru\\d+-certified-binaries$";
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
