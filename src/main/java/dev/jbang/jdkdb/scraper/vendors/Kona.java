package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.GitHubReleaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Unified scraper for all Tencent Kona releases across multiple Java versions */
public class Kona extends GitHubReleaseScraper {
	private static final String VENDOR = "kona";
	private static final String GITHUB_ORG = "Tencent";

	// Pattern for Kona 8
	private static final Pattern KONA8_SIMPLE_PATTERN =
			Pattern.compile("^TencentKona-([0-9.]{1,}-[0-9]+)\\.x86_64\\.tar\\.gz$");
	private static final Pattern KONA8_PATTERN = Pattern.compile(
			"^TencentKona([0-9b.]{1,})[-_](jdk|jre)[-_](fiber)?[-_]?(linux|linux-musl|macosx|windows)[-_](aarch64|x86_64)[-_](8u[0-9]+)(?:_(notarized|signed))?\\.(?:tar\\.gz|tgz|zip)$");

	// Pattern for Kona 11
	private static final Pattern KONA11_PATTERN = Pattern.compile(
			"^TencentKona-([0-9b.]{1,})[-_](jdk|jre)_(fiber)?_?(linux|macosx|windows)-(aarch64|x86_64).*\\.(tar\\.gz|zip)$");
	private static final Pattern KONA11_SIMPLE_PATTERN = Pattern.compile("^TencentKona([0-9b.]+)\\.tgz$");

	// Pattern for Kona 17 and 21
	private static final Pattern KONA_STANDARD_PATTERN = Pattern.compile(
			"^TencentKona-([0-9b.]{1,})(?:[_-](ea))?[-_](jdk|jre)_(linux|linux_musl|macosx|windows)-(aarch64|x86_64)(?:_(notarized|signed))?\\.(?:tar\\.gz|zip)$");

	public Kona(ScraperConfig config) {
		super(config);
	}

	@Override
	protected String getGitHubOrg() {
		return GITHUB_ORG;
	}

	@Override
	protected Iterable<String> getGitHubRepos() throws Exception {
		// Use the helper method to fetch all TencentKona repositories
		return getReposFromOrg(getGitHubOrg(), "TencentKona", "^TencentKona-\\d+$");
	}

	@Override
	protected void processRelease(JsonNode release) throws Exception {
		processReleaseAssets(release, this::processAsset);
	}

	private JdkMetadata processAsset(JsonNode release, JsonNode asset) {
		String assetName = asset.get("name").asText();
		String downloadUrl = asset.get("browser_download_url").asText();
		ParsedFilename parsed = parseFilename(assetName);
		if (parsed == null || parsed.version == null) {
			if (!assetName.endsWith(".md5") && !assetName.contains("_javadoc.")) {
				warn("Skipping " + assetName + " (does not match pattern)");
			}
			return null;
		}

		String metadataFilename = toMetadataFilename(release, asset);
		if (metadataExists(metadataFilename)) {
			return skipped(metadataFilename);
		}

		// Build features list
		List<String> features = new ArrayList<>();
		if (parsed.features != null) {
			String[] featureArray = parsed.features.trim().split("\\s+");
			for (String feature : featureArray) {
				if (!feature.isEmpty() && !feature.equals("notarized") && !feature.equals("signed")) {
					features.add(feature);
				}
			}
		}

		// Determine release type
		String releaseType = parsed.releaseType != null && parsed.releaseType.equals("ea") ? "ea" : "ga";

		// Create metadata
		return JdkMetadata.create()
				.vendor(VENDOR)
				.releaseType(releaseType)
				.version(parsed.version)
				.javaVersion(parsed.javaVersion)
				.jvmImpl("hotspot")
				.os(normalizeOs(parsed.os))
				.arch(normalizeArch(parsed.arch))
				.fileType(parsed.ext)
				.imageType(parsed.imageType)
				.features(features)
				.url(downloadUrl)
				.filename(assetName);
	}

	private ParsedFilename parseFilename(String filename) {
		ParsedFilename result = new ParsedFilename();
		Matcher matcher;

		// Try Kona 8 simple pattern
		matcher = KONA8_SIMPLE_PATTERN.matcher(filename);
		if (matcher.matches()) {
			result.version = matcher.group(1);
			result.javaVersion = "8u" + result.version.split("-")[1];
			result.os = "linux";
			result.arch = "x86_64";
			result.ext = "tar.gz";
			result.imageType = "jdk";
			return result;
		}

		// Try Kona 8 pattern
		matcher = KONA8_PATTERN.matcher(filename);
		if (matcher.matches()) {
			result.version = matcher.group(1);
			result.imageType = matcher.group(2);
			result.features = matcher.group(3);
			result.os = matcher.group(4);
			result.arch = matcher.group(5);
			result.javaVersion = matcher.group(6);
			String extraFeatures = matcher.group(7);
			if (extraFeatures != null) {
				result.features = (result.features != null ? result.features + " " : "") + extraFeatures;
			}
			if (result.os.equals("linux-musl")) {
				result.os = "linux";
				result.features = (result.features != null ? result.features + " " : "") + "musl";
			}
			result.ext = filename.substring(filename.lastIndexOf('.') + 1);
			if (filename.endsWith(".tar.gz")) {
				result.ext = "tar.gz";
			}
			return result;
		}

		// Try Kona 11 pattern
		matcher = KONA11_PATTERN.matcher(filename);
		if (matcher.matches()) {
			result.version = matcher.group(1);
			result.imageType = matcher.group(2);
			result.features = matcher.group(3);
			result.os = matcher.group(4);
			result.arch = matcher.group(5);
			result.javaVersion = result.version;
			result.ext = matcher.group(6);
			return result;
		}

		// Try Kona 11 simple pattern
		matcher = KONA11_SIMPLE_PATTERN.matcher(filename);
		if (matcher.matches()) {
			result.version = matcher.group(1);
			result.javaVersion = result.version;
			result.os = "linux";
			result.arch = "x86_64";
			result.ext = "tgz";
			result.imageType = "jdk";
			return result;
		}

		// Try standard pattern (Kona 17, 21)
		matcher = KONA_STANDARD_PATTERN.matcher(filename);
		if (matcher.matches()) {
			result.version = matcher.group(1);
			result.releaseType = matcher.group(2);
			result.imageType = matcher.group(3);
			result.os = matcher.group(4);
			result.arch = matcher.group(5);
			result.features = matcher.group(6);
			if (result.os.equals("linux_musl")) {
				result.os = "linux";
				result.features = (result.features != null ? result.features + " " : "") + "musl";
			}
			result.javaVersion = result.version;
			result.ext = filename.substring(filename.lastIndexOf('.') + 1);
			if (filename.endsWith(".tar.gz")) {
				result.ext = "tar.gz";
			}
			return result;
		}

		return null;
	}

	private static class ParsedFilename {
		String version;
		String imageType;
		String os;
		String arch;
		String javaVersion;
		String releaseType;
		String features;
		String ext;
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
			return new Kona(config);
		}
	}
}
