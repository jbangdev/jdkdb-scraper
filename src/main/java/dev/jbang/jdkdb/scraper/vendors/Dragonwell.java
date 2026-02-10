package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.DownloadResult;
import dev.jbang.jdkdb.scraper.GitHubReleaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Unified scraper for all Alibaba Dragonwell releases across multiple Java versions */
public class Dragonwell extends GitHubReleaseScraper {
	private static final String VENDOR = "dragonwell";

	// Multiple patterns to handle different filename formats
	private static final Pattern STANDARD_EXTENDED_PATTERN_8 = Pattern.compile(
			"^Alibaba_Dragonwell_(?:Standard|Extended)[-_]([0-9.+]{1,}[^_]*)[-_](x64|aarch64)[-_](Linux|linux|Windows|windows)\\.(tar\\.gz|zip)$");
	private static final Pattern ALIBABA_PATTERN_8 = Pattern.compile(
			"^Alibaba_Dragonwell_([0-9.+]{1,}[^_]*)[-_](?:(GA|Experimental|GA_Experimental|FP1)_)?(Linux|linux|Windows|windows)_(x64|aarch64)\\.(tar\\.gz|zip)$");
	private static final Pattern STANDARD_EXTENDED_PATTERN = Pattern.compile(
			"^Alibaba_Dragonwell_(?:Standard|Extended)[–_]([0-9.+]{1,}[^_]*)_(aarch64|x64)(?:_alpine)?[-_](Linux|linux|Windows|windows)\\.(tar\\.gz|zip)$");
	private static final Pattern ALIBABA_PATTERN = Pattern.compile(
			"^Alibaba_Dragonwell_([0-9.+]{1,}[^_]*)(?:_alpine)?[_-](?:(GA|Experimental|GA_Experimental|FP1)_)?(Linux|linux|Windows|windows)_(aarch64|x64)\\.(tar\\.gz|zip)$");
	private static final Pattern OPENJDK_PATTERN = Pattern.compile(
			"^OpenJDK(?:[0-9.+]{1,})_(x64|aarch64)_(linux|windows)_dragonwell_dragonwell-([0-9.]+)(?:_jdk)?[-_]([0-9._]+)-?(ga|.*?)\\.(tar\\.gz|zip)$");
	private static final Pattern FALLBACK_PATTERN = Pattern.compile(
			"^Alibaba_Dragonwell_([0-9.+]{1,}[^_]*)(?:_alpine)?_(aarch64|x64)_(Linux|linux|Windows|windows)\\.(tar\\.gz|zip)$");

	public Dragonwell(ScraperConfig config) {
		super(config);
	}

	@Override
	protected String getGitHubOrg() {
		return "dragonwell-project";
	}

	@Override
	protected List<String> getGitHubRepos() throws Exception {
		// Use the helper method to fetch all dragonwell repositories
		return getGitHubReposFromOrg(getGitHubOrg(), "dragonwell", "^dragonwell\\d+$");
	}

	@Override
	protected void processRelease(List<JdkMetadata> allMetadata, JsonNode release) throws Exception {
		processReleaseAssets(allMetadata, release, this::processAsset);
	}

	@Override
	protected boolean shouldProcessAsset(JsonNode release, JsonNode asset) {
		// Only process tar.gz and zip files
		String assetName = asset.get("name").asText();
		return assetName.endsWith(".tar.gz") || assetName.endsWith(".zip");
	}

	private JdkMetadata processAsset(JsonNode release, JsonNode asset) throws Exception {
		String assetName = asset.get("name").asText();
		String downloadUrl = asset.get("browser_download_url").asText();

		ParsedFilename parsed = parseFilename(assetName);
		if (parsed == null || parsed.version == null) {
			log("Skipping " + assetName + " (does not match pattern)");
			return null;
		}

		// Determine release type
		String releaseType;
		if (parsed.releaseType == null || parsed.releaseType.isEmpty() || parsed.releaseType.equals("jdk")) {
			releaseType = "ga";
		} else if (parsed.releaseType.equals("ea")
				|| parsed.releaseType.contains("Experimental")
				|| parsed.releaseType.equals("FP1")) {
			releaseType = "ea";
		} else {
			releaseType = "ga";
		}

		// Handle alpine feature
		List<String> features = new ArrayList<>();
		if (assetName.contains("_alpine")) {
			features.add("musl");
		}

		// Download and compute hashes
		DownloadResult download = downloadFile(downloadUrl, assetName);

		// Create metadata using builder
		return JdkMetadata.builder()
				.vendor(VENDOR)
				.releaseType(releaseType)
				.version(parsed.version)
				.javaVersion(parsed.javaVersion != null ? parsed.javaVersion : parsed.version)
				.jvmImpl("hotspot")
				.os(normalizeOs(parsed.os))
				.arch(normalizeArch(parsed.arch))
				.fileType(parsed.ext)
				.imageType("jdk")
				.features(features)
				.url(downloadUrl)
				.download(assetName, download)
				.build();
	}

	private ParsedFilename parseFilename(String filename) {
		ParsedFilename result = new ParsedFilename();

		// Try patterns in order
		Matcher matcher;

		// Try Standard/Extended pattern (version 8)
		matcher = STANDARD_EXTENDED_PATTERN_8.matcher(filename);
		if (matcher.matches()) {
			result.version = matcher.group(1);
			result.javaVersion = result.version;
			result.arch = matcher.group(2);
			result.os = matcher.group(3);
			result.ext = matcher.group(4);
			return result;
		}

		// Try Alibaba pattern (version 8)
		matcher = ALIBABA_PATTERN_8.matcher(filename);
		if (matcher.matches()) {
			result.version = matcher.group(1);
			result.javaVersion = result.version;
			result.releaseType = matcher.group(2);
			result.os = matcher.group(3);
			result.arch = matcher.group(4);
			result.ext = matcher.group(5);
			return result;
		}

		// Try Standard/Extended pattern (newer versions)
		matcher = STANDARD_EXTENDED_PATTERN.matcher(filename);
		if (matcher.matches()) {
			result.version = matcher.group(1);
			result.javaVersion = result.version;
			result.arch = matcher.group(2);
			result.os = matcher.group(3);
			result.ext = matcher.group(4);
			return result;
		}

		// Try Alibaba pattern (newer versions)
		matcher = ALIBABA_PATTERN.matcher(filename);
		if (matcher.matches()) {
			result.version = matcher.group(1);
			result.javaVersion = result.version;
			result.releaseType = matcher.group(2);
			result.os = matcher.group(3);
			result.arch = matcher.group(4);
			result.ext = matcher.group(5);
			return result;
		}

		// Try OpenJDK pattern
		matcher = OPENJDK_PATTERN.matcher(filename);
		if (matcher.matches()) {
			result.arch = matcher.group(1);
			result.os = matcher.group(2);
			result.version = matcher.group(3);
			result.javaVersion = matcher.group(4);
			result.releaseType = matcher.group(5);
			result.ext = matcher.group(6);
			return result;
		}

		// Try fallback pattern
		matcher = FALLBACK_PATTERN.matcher(filename);
		if (matcher.matches()) {
			result.version = matcher.group(1);
			result.javaVersion = result.version;
			result.arch = matcher.group(2);
			result.os = matcher.group(3);
			result.ext = matcher.group(4);
			result.releaseType = "jdk";
			return result;
		}

		return null;
	}

	private static class ParsedFilename {
		String version;
		String javaVersion;
		String releaseType;
		String os;
		String arch;
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
			return new Dragonwell(config);
		}
	}
}
