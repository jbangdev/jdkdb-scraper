package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.AdoptiumMarketplaceScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.List;

/** Scraper for Huawei BiSheng JDK releases */
public class Bisheng extends AdoptiumMarketplaceScraper {
	private static final String VENDOR = "bisheng";

	public Bisheng(ScraperConfig config) {
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
		return "/info/available_releases/huawei";
	}

	@Override
	protected String getAssetsPathTemplate() {
		return "/assets/feature_releases/huawei/%d?";
	}

	@Override
	protected String extractJavaVersion(JsonNode asset) {
		return asset.path("openjdk_version_data").path("openjdk_version").asText();
	}

	@Override
	protected String extractVersion(JsonNode asset) {
		String releaseName = asset.path("release_name").asText();
		// Version is release_name with first 3 characters removed (e.g., "jdk-11.0.10" -> "11.0.10")
		return releaseName.length() > 3 ? releaseName.substring(3) : releaseName;
	}

	@Override
	protected JdkMetadata processBinary(
			JsonNode binary, String version, String javaVersion, List<JdkMetadata> allMetadata) throws Exception {
		return createStandardMetadata(binary, version, javaVersion, allMetadata, List.of());
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
		public When when() {
			// This project now uses some weird Chinese Git hosting service that we don't support
			return When.NEVER;
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new Bisheng(config);
		}
	}
}
