package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.BaseScraper;
import dev.jbang.jdkdb.scraper.DownloadResult;
import dev.jbang.jdkdb.scraper.InterruptedProgressException;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import dev.jbang.jdkdb.scraper.TooManyFailuresException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Scraper for Adoptium Eclipse Temurin releases */
public class Temurin extends BaseScraper {
	private static final String VENDOR = "temurin";
	private static final String API_BASE = "https://api.adoptium.net/v3";

	public Temurin(ScraperConfig config) {
		super(config);
	}

	@Override
	protected List<JdkMetadata> scrape() throws Exception {
		List<JdkMetadata> allMetadata = new ArrayList<>();

		// Get list of available releases
		JsonNode availableReleases;
		try {
			log("Fetching available releases");
			String releasesJson = httpUtils.downloadString(API_BASE + "/info/available_releases");
			JsonNode releasesData = readJson(releasesJson);
			availableReleases = releasesData.get("available_releases");
		} catch (Exception e) {
			fail("Failed to fetch available releases", e);
			return Collections.emptyList();
		}

		if (availableReleases == null || !availableReleases.isArray()) {
			fail("No available releases found", null);
			return Collections.emptyList();
		}

		try {
			// Process each release version
			for (JsonNode releaseNode : availableReleases) {
				int release = releaseNode.asInt();
				log("Processing release: " + release);

				// Fetch assets for this release with pagination
				int page = 0;
				boolean hasMore = true;

				while (hasMore) {
					String assetsUrl = String.format(
							"%s/assets/feature_releases/%d/ga?page=%d&page_size=50&project=jdk&sort_order=ASC&vendor=adoptium",
							API_BASE, release, page);

					JsonNode assets;
					try {
						String assetsJson = httpUtils.downloadString(assetsUrl);
						assets = readJson(assetsJson);
					} catch (Exception e) {
						fail("Could not download list of assets", e);
						continue;
					}

					if (assets.isArray() && assets.size() > 0) {
						processAssets(assets, allMetadata);
						page++;
					} else {
						hasMore = false;
					}
				}
			}
		} catch (InterruptedProgressException e) {
			log("Reached progress limit, aborting");
		}

		return allMetadata;
	}

	private void processAssets(JsonNode assets, List<JdkMetadata> allMetadata) throws Exception {
		for (JsonNode asset : assets) {
			String javaVersion =
					asset.path("version_data").path("openjdk_version").asText();
			String version = asset.path("version_data").path("semver").asText();

			JsonNode binaries = asset.get("binaries");
			if (binaries != null && binaries.isArray()) {
				for (JsonNode binary : binaries) {
					String filename =
							binary.has("package") && binary.get("package").has("name")
									? binary.get("package").get("name").asText()
									: "unknown";

					if (metadataExists(filename)) {
						allMetadata.add(skipped(filename));
						skip(filename);
						continue;
					}

					try {
						JdkMetadata metadata = processBinary(binary, version, javaVersion, allMetadata);
						if (metadata != null) {
							saveMetadataFile(metadata);
							allMetadata.add(metadata);
							success(filename);
						}
					} catch (InterruptedProgressException | TooManyFailuresException e) {
						throw e;
					} catch (Exception e) {
						fail(filename, e);
					}
				}
			}
		}
	}

	private JdkMetadata processBinary(
			JsonNode binary, String version, String javaVersion, List<JdkMetadata> allMetadata) throws Exception {

		String imageType = binary.path("image_type").asText();

		// Only process JDK and JRE
		if (!imageType.equals("jdk") && !imageType.equals("jre")) {
			return null;
		}

		JsonNode packageNode = binary.get("package");
		if (packageNode == null) {
			return null;
		}

		String filename = packageNode.path("name").asText();
		String url = packageNode.path("link").asText();

		String os = binary.path("os").asText();
		String arch = binary.path("architecture").asText();
		String heapSize = binary.path("heap_size").asText();
		String jvmImpl = binary.path("jvm_impl").asText();

		// Determine file extension
		String ext = filename.endsWith(".tar.gz") ? "tar.gz" : "zip";

		// Normalize version for OpenJ9
		String normalizedVersion = version;
		if (jvmImpl.equals("openj9") && filename.contains("openj9") && !version.contains("openj9")) {
			// Extract OpenJ9 version from filename if present
			if (filename.matches(".*[_-]openj9[-_]\\d+\\.\\d+\\.\\d+.*")) {
				String openj9Part = filename.replaceAll(".*([_-]openj9[-_]\\d+\\.\\d+\\.\\d+[a-z]?).*", "$1");
				normalizedVersion = version + "." + openj9Part.replace("_", "-");
			}
		}

		// Build features list
		List<String> features = new ArrayList<>();
		if (heapSize.equals("large")) {
			features.add("large_heap");
		}
		if (os.equals("alpine-linux")) {
			features.add("musl");
		}

		// Download and compute hashes
		DownloadResult download = downloadFile(url, filename);

		// Create metadata using builder
		return JdkMetadata.builder()
				.vendor(VENDOR)
				.releaseType("ga")
				.version(normalizedVersion)
				.javaVersion(javaVersion)
				.jvmImpl(jvmImpl)
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(ext)
				.imageType(imageType)
				.features(features)
				.url(url)
				.download(filename, download)
				.build();
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
			return new Temurin(config);
		}
	}
}
