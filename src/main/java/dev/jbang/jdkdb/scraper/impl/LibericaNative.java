package dev.jbang.jdkdb.scraper.impl;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.BaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for BellSoft Liberica Native Image Kit releases */
public class LibericaNative extends BaseScraper {
	private static final String VENDOR = "liberica-native";
	private static final String API_BASE_URL = "https://api.bell-sw.com/v1/nik/releases";

	public LibericaNative(ScraperConfig config) {
		super(config);
	}

	@Override
	protected void scrape() throws Exception {
		// Query Liberica API for native image releases
		String apiUrl = API_BASE_URL + "?bundle-type=full&components=liberica&components=nik";

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
			return;
		}

		log("Found " + assets.size() + " assets");
		for (JsonNode asset : assets) {
			JdkMetadata metadata = processAsset(asset);
			if (metadata != null) {
				process(metadata);
			}
		}
	}

	private JdkMetadata processAsset(JsonNode asset) {
		JsonNode filenameNode = asset.get("filename");
		if (filenameNode == null || !filenameNode.isTextual()) {
			warn("Skipping asset (missing or invalid filename)");
			return null;
		}

		String filename = filenameNode.asText();
		if (filename.endsWith(".src.tar.gz")) {
			return null;
		}

		JsonNode downloadUrlNode = asset.get("downloadUrl");
		if (downloadUrlNode == null
				|| !downloadUrlNode.isTextual()
				|| downloadUrlNode.asText().isEmpty()) {
			warn("Skipping " + filename + " (missing or invalid downloadUrl)");
			return null;
		}
		JsonNode versionNode = asset.get("version");
		if (versionNode == null
				|| !versionNode.isTextual()
				|| versionNode.asText().isEmpty()) {
			warn("Skipping " + filename + " (missing or invalid version)");
			return null;
		}
		JsonNode componentsNode = asset.get("components");
		if (componentsNode == null || !componentsNode.isArray() || componentsNode.size() == 0) {
			warn("Skipping asset with missing or invalid 'components' object");
			return null;
		}
		JsonNode compNode = componentsNode.get(0);
		JsonNode javaVersionNode = compNode.get("version");
		if (javaVersionNode == null
				|| !javaVersionNode.isTextual()
				|| javaVersionNode.asText().isEmpty()) {
			warn("Skipping asset with missing or invalid javaVersion");
			return null;
		}

		if (metadataExists(filename)) {
			return skipped(filename);
		}

		String downloadUrl = downloadUrlNode.asText();

		// Determine release type (GA vs EA) based on "GA" field (default to GA if missing)
		JsonNode gaNode = asset.get("GA");
		String isGa = gaNode != null ? gaNode.asText() : "true";
		String releaseType = isGa.equalsIgnoreCase("true") ? "ga" : "ea";

		// Extract version from "version" field
		String version = versionNode.asText();

		// Extract Java version from "components" node
		String javaVersion = javaVersionNode.asText();

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

		// Build features list
		List<String> features = new ArrayList<>();
		features.add("native-image");
		if (libcType != null) {
			features.add(libcType);
		}

		// Create metadata
		return JdkMetadata.create()
				.vendor(VENDOR)
				.releaseType(releaseType)
				.version(version)
				.javaVersion(javaVersion)
				.jvmImpl("graalvm")
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(ext)
				.imageType("jdk")
				.features(features)
				.url(downloadUrl)
				.filename(filename);
	}

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "liberica-native";
		}

		@Override
		public String vendor() {
			return VENDOR;
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new LibericaNative(config);
		}
	}
}
