package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.BaseScraper;
import dev.jbang.jdkdb.scraper.InterruptedProgressException;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import dev.jbang.jdkdb.scraper.TooManyFailuresException;
import java.util.ArrayList;
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
		log("Fetching available releases");
		String releasesJson = httpUtils.downloadString(API_BASE + "/info/available_releases");
		JsonNode releasesData = readJson(releasesJson);
		JsonNode availableReleases = releasesData.get("available_releases");

		if (availableReleases == null || !availableReleases.isArray()) {
			log("No available releases found");
			return allMetadata;
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

					String assetsJson = httpUtils.downloadString(assetsUrl);
					JsonNode assets = readJson(assetsJson);

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

		if (metadataExists(filename)) {
			log("Skipping " + filename + " (already exists)");
			return null;
		}

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

		// Create metadata
		JdkMetadata metadata = new JdkMetadata();
		metadata.setVendor(VENDOR);
		metadata.setFilename(filename);
		metadata.setReleaseType("ga");
		metadata.setVersion(normalizedVersion);
		metadata.setJavaVersion(javaVersion);
		metadata.setJvmImpl(jvmImpl);
		metadata.setOs(normalizeOs(os));
		metadata.setArchitecture(normalizeArch(arch));
		metadata.setFileType(ext);
		metadata.setImageType(imageType);
		metadata.setFeatures(features);
		metadata.setUrl(url);
		metadata.setMd5(download.md5());
		metadata.setMd5File(filename + ".md5");
		metadata.setSha1(download.sha1());
		metadata.setSha1File(filename + ".sha1");
		metadata.setSha256(download.sha256());
		metadata.setSha256File(filename + ".sha256");
		metadata.setSha512(download.sha512());
		metadata.setSha512File(filename + ".sha512");
		metadata.setSize(download.size());

		return metadata;
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
