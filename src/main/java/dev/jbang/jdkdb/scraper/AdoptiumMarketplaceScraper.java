package dev.jbang.jdkdb.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for scrapers that fetch releases from Adoptium Marketplace API or legacy AdoptOpenJDK
 * API
 */
public abstract class AdoptiumMarketplaceScraper extends BaseScraper {

	public AdoptiumMarketplaceScraper(ScraperConfig config) {
		super(config);
	}

	/** Get the vendor name */
	public abstract String getVendorName();

	/** Get the API base URL */
	protected abstract String getApiBase();

	/** Get the available releases endpoint path */
	protected abstract String getAvailableReleasesPath();

	/** Get the assets endpoint path template (will be formatted with release number) */
	protected abstract String getAssetsPathTemplate();

	/**
	 * Extract Java version from asset JSON
	 *
	 * @param asset the asset JSON node
	 * @return the Java version string
	 */
	protected abstract String extractJavaVersion(JsonNode asset);

	/**
	 * Extract version from asset JSON
	 *
	 * @param asset the asset JSON node
	 * @return the version string
	 */
	protected abstract String extractVersion(JsonNode asset);

	/**
	 * Process a single binary from an asset
	 *
	 * @param binary the binary JSON node
	 * @param version the version string
	 * @param javaVersion the Java version string
	 * @param allMetadata list to add processed metadata to
	 * @return the JdkMetadata object, or null if not processed
	 * @throws Exception on processing errors
	 */
	protected abstract JdkMetadata processBinary(
			JsonNode binary, String version, String javaVersion, List<JdkMetadata> allMetadata) throws Exception;

	@Override
	protected List<JdkMetadata> scrape() throws Exception {
		List<JdkMetadata> allMetadata = new ArrayList<>();

		// Get list of available releases
		log("Fetching available releases");
		String releasesJson = httpUtils.downloadString(getApiBase() + getAvailableReleasesPath());
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
							"%s%s?page=%d&page_size=20&sort_order=ASC",
							getApiBase(), String.format(getAssetsPathTemplate(), release), page);

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
			String javaVersion = extractJavaVersion(asset);
			String version = extractVersion(asset);

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

	/** Helper to normalize version for OpenJ9 */
	protected String normalizeVersionForOpenJ9(String jvmImpl, String filename, String version) {
		if (jvmImpl.equals("openj9") && filename.contains("openj9") && !version.contains("openj9")) {
			// Extract OpenJ9 version from filename if present
			if (filename.matches(".*[_-]openj9[-_]\\d+\\.\\d+\\.\\d+[a-z]?.*")) {
				String openj9Part = filename.replaceAll(".*([_-]openj9[-_]\\d+\\.\\d+\\.\\d+[a-z]?).*", "$1");
				return version + "." + openj9Part.replace("_", "-");
			}
		}
		return version;
	}

	/** Helper to create standard metadata from binary JSON */
	protected JdkMetadata createStandardMetadata(
			JsonNode binary,
			String version,
			String javaVersion,
			List<JdkMetadata> allMetadata,
			List<String> additionalFeatures)
			throws Exception {

		String imageType = binary.path("image_type").asText();

		// Only process JDK and JRE
		if (!imageType.equals("jdk") && !imageType.equals("jre")) {
			log("Skipping non-JRE, non-JDK image: " + imageType);
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
		String jvmImpl = binary.path("jvm_impl").asText();

		// Determine file extension
		String ext;
		if (filename.endsWith(".tar.gz")) {
			ext = "tar.gz";
		} else if (filename.endsWith(".tar.xz")) {
			ext = "tar.xz";
		} else {
			ext = "zip";
		}

		// Normalize version
		String normalizedVersion = normalizeVersionForOpenJ9(jvmImpl, filename, version);

		// Build features list
		List<String> features = new ArrayList<>(additionalFeatures);

		// Download and compute hashes
		DownloadResult download = downloadFile(url, filename);

		// Create metadata using builder
		return JdkMetadata.builder()
				.vendor(getVendorName())
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
}
