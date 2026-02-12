package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.AdoptiumMarketplaceScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.List;

/** Scraper for Red Hat JDK releases */
public class Redhat extends AdoptiumMarketplaceScraper {
	private static final String VENDOR = "redhat";

	public Redhat(ScraperConfig config) {
		super(config);
	}

	@Override
	public String getVendorName() {
		return VENDOR;
	}

	@Override
	protected String getApiBase() {
		return "https://marketplace-api.adoptium.net/v1";
	}

	@Override
	protected String getAvailableReleasesPath() {
		return "/info/available_releases/redhat";
	}

	@Override
	protected String getAssetsPathTemplate() {
		return "/assets/feature_releases/redhat/%d?";
	}

	@Override
	protected String extractJavaVersion(JsonNode asset) {
		return asset.path("openjdk_version_data").path("openjdk_version").asText();
	}

	@Override
	protected String extractVersion(JsonNode asset) {
		String releaseName = asset.path("release_name").asText();
		// Version is release_name with first 4 characters removed (e.g., "jdk-11.0.10" -> "11.0.10")
		return releaseName.length() > 4 ? releaseName.substring(4) : releaseName;
	}

	@Override
	protected JdkMetadata processAsset(JsonNode binary, String version, String javaVersion) {
		return createStandardMetadata(binary, version, javaVersion, List.of());
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
			return new Redhat(config);
		}
	}
}
