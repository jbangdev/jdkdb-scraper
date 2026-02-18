package dev.jbang.jdkdb.scraper.impl;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.GitHubReleaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for JetBrains Runtime releases */
public class Jetbrains extends GitHubReleaseScraper {
	private static final String VENDOR = "jetbrains";
	private static final String GITHUB_ORG = "JetBrains";
	private static final String GITHUB_REPO = "JetBrainsRuntime";

	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^jbr(sdk)?(?:_\\w+)?-([0-9][0-9\\+._]{1,})-(linux-musl|linux|osx|macos|windows)-(aarch64|x64|x86)(?:-\\w+)?-(b[0-9\\+.]{1,})(?:_\\w+)?\\.(tar\\.gz|zip|pkg)$");

	private static final Pattern BODY_PATTERN = Pattern.compile(
			"\\|\\s*(?:\\*\\*)?(?<description>[^|]+?)(?:\\*\\*)?\\s*\\|\\s*\\[(?<file>[^\\]]+)\\]\\((?<url>[^\\)]+)\\)\\s*\\|\\s*\\[checksum\\]\\((?<checksumUrl>[^\\)]+)\\)");

	public Jetbrains(ScraperConfig config) {
		super(config);
	}

	@Override
	protected String getGitHubOrg() {
		return GITHUB_ORG;
	}

	@Override
	protected Iterable<String> getGitHubRepos() {
		return List.of(GITHUB_REPO);
	}

	@Override
	protected void processRelease(JsonNode release) throws Exception {
		boolean prerelease = release.get("prerelease").asBoolean();

		if (prerelease && isOldRelease(release)) {
			fine("Skipping old prerelease " + release.path("tag_name").asText());
			return;
		}

		String releaseType = prerelease ? "ea" : "ga";
		String body = release.get("body").asText("");

		// Parse assets from the body
		Matcher matcher = BODY_PATTERN.matcher(body);
		while (matcher.find()) {
			String description = matcher.group("description");
			String file = matcher.group("file");
			String url = matcher.group("url");

			JdkMetadata metadata = processAsset(file, url, releaseType, description);
			if (metadata != null) {
				process(metadata);
			}
		}
	}

	private JdkMetadata processAsset(String assetName, String url, String releaseType, String description) {
		Matcher matcher = FILENAME_PATTERN.matcher(assetName);
		if (!matcher.matches()) {
			if (!assetName.endsWith(".checksum")) {
				warn("Skipping " + assetName + " (does not match pattern)");
			}
			return null;
		}

		if (assetName.contains("_diz.")) {
			fine("Skipping " + assetName + " (not a JDK asset)");
			return null;
		}

		if (metadataExists(assetName)) {
			return skipped(assetName);
		}

		String sdkMarker = matcher.group(1);
		String versionPart = matcher.group(2).replace("_", ".");
		String os = matcher.group(3);
		String arch = matcher.group(4);
		String buildNumber = matcher.group(5);
		String ext = matcher.group(6);

		String version = versionPart + buildNumber;
		String imageType = (sdkMarker != null && !sdkMarker.isEmpty()) ? "jdk" : "jre";

		// Build features list
		List<String> features = new ArrayList<>();
		if (description.contains("fastdebug")) {
			features.add("fastdebug");
		}
		if (description.contains("debug symbols")) {
			features.add("debug");
		}
		if (description.contains("FreeType")) {
			features.add("freetype");
		}
		if (description.contains("JCEF")) {
			features.add("jcef");
		}
		if (description.contains("Legacy Binary")) {
			features.add("legacy");
		}
		if (os.equals("linux-musl")) {
			features.add("musl");
			os = "linux";
		}

		// Create metadata
		return JdkMetadata.create()
				.vendor(VENDOR)
				.releaseType(releaseType)
				.version(version)
				.javaVersion(versionPart)
				.jvmImpl("hotspot")
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(ext)
				.imageType(imageType)
				.features(features)
				.url(url)
				.filename(assetName);
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
			return new Jetbrains(config);
		}
	}
}
