package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.AdoptiumMarketplaceScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.ArrayList;
import java.util.List;

/** Scraper for AdoptOpenJDK releases (legacy, now part of Adoptium) */
public class AdoptOpenJdk extends AdoptiumMarketplaceScraper {
	private static final String VENDOR = "adoptopenjdk";

	public AdoptOpenJdk(ScraperConfig config) {
		super(config);
	}

	@Override
	public String getVendorName() {
		return VENDOR;
	}

	@Override
	protected String getApiBase() {
		return "https://api.adoptopenjdk.net/v3";
	}

	@Override
	protected String getAvailableReleasesPath() {
		return "/info/available_releases";
	}

	@Override
	protected String getAssetsPathTemplate() {
		return "/assets/feature_releases/%d/ga&project=jdk&vendor=adoptopenjdk";
	}

	@Override
	protected String extractJavaVersion(JsonNode asset) {
		return asset.path("version_data").path("openjdk_version").asText();
	}

	@Override
	protected String extractVersion(JsonNode asset) {
		return asset.path("version_data").path("semver").asText();
	}

	@Override
	protected JdkMetadata processBinary(
			JsonNode binary, String version, String javaVersion, List<JdkMetadata> allMetadata) throws Exception {

		String heapSize = binary.path("heap_size").asText();
		String os = binary.path("os").asText();

		// Build additional features
		List<String> additionalFeatures = new ArrayList<>();
		if (heapSize.equals("large")) {
			additionalFeatures.add("large_heap");
		}
		if (os.equals("alpine-linux")) {
			additionalFeatures.add("musl");
		}

		return createStandardMetadata(binary, version, javaVersion, allMetadata, additionalFeatures);
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
			return new AdoptOpenJdk(config);
		}
	}
}
