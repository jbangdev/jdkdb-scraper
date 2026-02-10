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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for BellSoft Liberica releases */
public class Liberica extends BaseScraper {
	private static final String VENDOR = "liberica";
	private static final String API_BASE_URL = "https://api.bell-sw.com/v1/liberica/releases";

	public Liberica(ScraperConfig config) {
		super(config);
	}

	@Override
	protected List<JdkMetadata> scrape() throws Exception {
		List<JdkMetadata> allMetadata = new ArrayList<>();

		// Query Liberica API for native image releases
		String apiUrl = API_BASE_URL + "?";

		JsonNode assets;
		try {
			log("Fetching assets from " + apiUrl);
			String json = httpUtils.downloadString(apiUrl);
			assets = readJson(json);
		} catch (Exception e) {
			fail("Failed to fetch assets from API", e);
			throw e;
		}

		if (!assets.isArray()) {
			warn("No assets found");
			return allMetadata;
		}

		log("Found " + assets.size() + " assets");
		try {
			for (JsonNode asset : assets) {
				JsonNode filenameNode = asset.get("filename");
				if (filenameNode == null || !filenameNode.isTextual()) {
					warn("Skipping asset with missing or invalid filename");
					continue;
				}
				String filename = filenameNode.asText();

				if (metadataExists(filename)) {
					allMetadata.add(skipped(filename));
					skip(filename);
					continue;
				}

				try {
					JdkMetadata metadata = processAsset(asset, filename);
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
		} catch (InterruptedProgressException e) {
			log("Reached progress limit, aborting");
		}

		return allMetadata;
	}

	private JdkMetadata processAsset(JsonNode asset, String filename) throws Exception {
		JsonNode downloadUrlNode = asset.get("downloadUrl");
		if (downloadUrlNode == null
				|| !downloadUrlNode.isTextual()
				|| downloadUrlNode.asText().isEmpty()) {
			warn("Skipping asset with missing or invalid downloadUrl");
			return null;
		}
		String downloadUrl = downloadUrlNode.asText();

		// Determine release type (GA vs EA) based on "GA" field (default to GA if missing)
		JsonNode gaNode = asset.get("GA");
		String isGa = gaNode != null ? gaNode.asText() : "true";
		String releaseType = isGa.equalsIgnoreCase("true") ? "ga" : "ea";

		// Determine if JavaFX is included based on "FX" field (default to false if missing)
		JsonNode fxNode = asset.get("FX");
		boolean hasFx = fxNode != null ? fxNode.asText().equalsIgnoreCase("true") : false;

		// Extract version from "version" field
		JsonNode versionNode = asset.get("version");
		if (versionNode == null
				|| !versionNode.isTextual()
				|| versionNode.asText().isEmpty()) {
			warn("Skipping asset with missing or invalid version");
			return null;
		}
		String version = versionNode.asText();

		// Determine OS and possibly glibc type from "os" field
		JsonNode osNode = asset.get("os");
		String os = osNode != null ? osNode.asText() : "unknown";
		String libcType = null;
		if (os.endsWith("-musl")) {
			os = os.replace("-musl", "");
			libcType = "musl";
		}

		// Determine architecture from "architecture" field
		JsonNode archNode = asset.get("architecture");
		String arch = archNode != null ? archNode.asText() : "unknown";

		// Determine file extension from package type (default to .tar.gz)
		String ext = "";
		JsonNode pkgTypeNode = asset.get("packageType");
		if (pkgTypeNode != null
				&& pkgTypeNode.isTextual()
				&& !pkgTypeNode.asText().isEmpty()) {
			ext = pkgTypeNode.asText();
		} else {
			// extension not provided, try to infer from filename
			Pattern extPattern = Pattern.compile("\\.(tar\\.gz|zip|msi|rpm|deb|apk)$");
			Matcher matcher = extPattern.matcher(filename);
			if (matcher.find()) {
				ext = matcher.group(1);
			}
		}
		if (ext.isEmpty()) {
			warn("Skipping asset with unknown file extension");
			return null;
		}
		if (ext.equals("src.tar.gz")) {
			log("Skipping source asset");
			return null;
		}

		// Determine image type from "bundleType" field
		String imageType = "jdk";
		String bundleType = "";
		JsonNode bundleTypeNode = asset.get("bundleType");
		if (bundleTypeNode != null
				&& bundleTypeNode.isTextual()
				&& !bundleTypeNode.asText().isEmpty()) {
			bundleType = bundleTypeNode != null ? bundleTypeNode.asText() : "unknown";
			if (bundleType.startsWith("jre")) {
				imageType = "jre";
			}
		}

		// Build features list
		List<String> features = new ArrayList<>();
		if (libcType != null) {
			features.add(libcType);
		}
		if (hasFx) {
			features.add("javafx");
		}
		if (bundleType.endsWith("lite")) {
			features.add("lite");
		}

		// Download and compute hashes
		DownloadResult download = downloadFile(downloadUrl, filename);

		// Create metadata using builder
		return JdkMetadata.builder()
				.vendor(VENDOR)
				.releaseType(releaseType)
				.version(version)
				.javaVersion(version)
				.jvmImpl("hotspot")
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(ext)
				.imageType(imageType)
				.features(features)
				.url(downloadUrl)
				.download(filename, download)
				.build();
	}

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "liberica";
		}

		@Override
		public String vendor() {
			return VENDOR;
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new Liberica(config);
		}
	}
}
