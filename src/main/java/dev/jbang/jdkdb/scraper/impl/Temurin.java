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

/** Scraper for Adoptium Eclipse Temurin Early Access releases from GitHub */
public class Temurin extends GitHubReleaseScraper {
	private static final String VENDOR = "temurin";
	private static final String GITHUB_ORG = "adoptium";

	// Filename pattern: OpenJDK{version}U-{type}_{arch}_{os}_{jvmimpl}_{version}.{ext}
	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^OpenJDK([0-9]+U?)?-(jdk|jre+)_([^_]+)_(aix|alpine-linux|linux|mac|solaris|windows)_hotspot_(.+)\\.(tar\\.gz|zip|pkg|msi)$");
	private static final Pattern GA_VERSION_PATTERN = Pattern.compile("^jdk-?(.+)");
	private static final Pattern REPO_VERSION_PATTERN = Pattern.compile("temurin\\d+-binaries");

	public Temurin(ScraperConfig config) {
		super(config);
	}

	@Override
	protected String getGitHubOrg() {
		return GITHUB_ORG;
	}

	@Override
	protected Iterable<String> getGitHubRepos() throws Exception {
		// Use the helper method to fetch all temurin EA repositories
		return getReposFromOrg(getGitHubOrg(), "temurin", "^temurin\\d+-binaries$");
	}

	@Override
	protected void processRelease(JsonNode release) throws Exception {
		String repoUrl = release.get("url").asText();
		Matcher repoVersionMatcher = REPO_VERSION_PATTERN.matcher(repoUrl);
		if (!repoVersionMatcher.find()) {
			warn("Skipping release " + release.get("tag_name").asText()
					+ " (repository URL does not match expected pattern)");
			return;
		}
		String javaMajorVersion = repoVersionMatcher.group(0);

		// Extract versions from tagname/url
		String tagName = release.get("tag_name").asText();
		String version = tagName.replaceFirst("^jdk-?", "");
		boolean isPrerelease = release.path("prerelease").asBoolean(false);
		String javaVersion;
		if (!isPrerelease) {
			Matcher versionMatcher = GA_VERSION_PATTERN.matcher(tagName);
			if (!versionMatcher.matches()) {
				warn("Skipping release " + tagName + " (tag name does not match expected pattern)");
				return;
			}
			javaVersion = versionMatcher.group(1);
		} else {
			javaVersion = javaMajorVersion;
		}

		processReleaseAssets(release, (r, asset) -> processAsset(release, asset, version, javaVersion));
	}

	private JdkMetadata processAsset(JsonNode release, JsonNode asset, String version, String javaVersion) {
		String assetName = asset.get("name").asText();
		Matcher matcher = FILENAME_PATTERN.matcher(assetName);
		if (!matcher.matches()) {
			if (!assetName.endsWith(".txt")
					&& !assetName.endsWith(".json")
					&& !assetName.endsWith(".sig")
					&& !assetName.contains("-sources")
					&& !assetName.contains("-sbom")
					&& !assetName.contains("-jmods")
					&& !assetName.contains("-static-libs")
					&& !assetName.contains("-debugimage")
					&& !assetName.contains("-testimage")
					&& !assetName.equals("AQAvitTapFiles.tar.gz")) {
				warn("Skipping " + assetName + " (does not match pattern)");
			}
			return null;
		}

		// String majorVersion = matcher.group(1);
		String imageType = matcher.group(2);
		String arch = matcher.group(3);
		String os = matcher.group(4);
		// String version = matcher.group(5);
		String ext = matcher.group(6);

		// Only process JDK and JRE
		if (!imageType.equals("jdk") && !imageType.equals("jre")) {
			return null;
		}

		String metadataFilename = toMetadataFilename(release, asset);
		if (metadataExists(metadataFilename)) {
			return skipped(metadataFilename);
		}

		String downloadUrl = asset.get("browser_download_url").asText();

		// Only process prereleases (EA releases)
		boolean isPrerelease = release.path("prerelease").asBoolean(false);
		String releaseType = isPrerelease ? "ea" : "ga";

		// Build features list
		List<String> features = new ArrayList<>();
		if (os.contains("alpine")) {
			features.add("musl");
		}

		// Create metadata
		return JdkMetadata.create()
				.vendor(VENDOR)
				.releaseType(releaseType)
				.version(version)
				.javaVersion(javaVersion)
				.jvmImpl("hotspot")
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(ext)
				.imageType(imageType)
				.features(features)
				.url(downloadUrl)
				.filename(assetName);
	}

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "temurin";
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
