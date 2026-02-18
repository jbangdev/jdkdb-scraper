package dev.jbang.jdkdb.scraper.impl;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.GitHubReleaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for GraalVM CE (legacy) releases */
public class GraalVmCe extends GitHubReleaseScraper {
	private static final String VENDOR = "graalvm";
	private static final String GITHUB_ORG = "graalvm";
	private static final String GITHUB_REPO = "graalvm-ce-builds";

	// Prior graalvm 23: graalvm-ce-java17-darwin-amd64-22.3.2.tar.gz
	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^graalvm-ce-(?:complete-)?java(\\d{1,2})-(linux|darwin|windows)-(aarch64|amd64)-([\\d+.]{2,})\\.(zip|tar\\.gz)$");
	private static final Pattern EA_PATTERN = Pattern.compile("-(dev|rc\\d)-");

	public GraalVmCe(ScraperConfig config) {
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
		// Exclude Community releases (which start with "jdk")
		String tagName = release.get("tag_name").asText();
		if (tagName.startsWith("jdk")) {
			return;
		}
		processReleaseAssets(release, this::processAsset);
	}

	protected JdkMetadata processAsset(JsonNode release, JsonNode asset) {
		String tagName = release.get("tag_name").asText();
		String assetName = asset.get("name").asText();

		if (!assetName.startsWith("graalvm-ce") || !(assetName.endsWith("tar.gz") || assetName.endsWith("zip"))) {
			fine("Skipping " + assetName + " (non-GraalVM CE asset)");
			return null;
		}

		Matcher matcher = FILENAME_PATTERN.matcher(assetName);
		if (!matcher.matches()) {
			warn("Skipping " + assetName + " (does not match pattern)");
			return null;
		}

		String metadataFilename = toMetadataFilename(release, asset);
		if (metadataExists(metadataFilename)) {
			return skipped(metadataFilename);
		}

		String javaVersion = matcher.group(1);
		String os = matcher.group(2);
		String arch = matcher.group(3);
		String version = matcher.group(4);
		String ext = matcher.group(5);

		String releaseType = "ga";
		if (EA_PATTERN.matcher(assetName).find()) {
			releaseType = "ea";
		}

		if (releaseType.equals("ea") && isOldRelease(release)) {
			fine("Skipping old EA release " + tagName);
			return null;
		}

		String url = String.format(
				"https://github.com/%s/%s/releases/download/%s/%s", GITHUB_ORG, GITHUB_REPO, tagName, assetName);

		// Create metadata
		return JdkMetadata.create()
				.vendor(VENDOR)
				.releaseType(releaseType)
				.version(version + "+java" + javaVersion)
				.javaVersion(javaVersion)
				.jvmImpl("graalvm")
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(ext)
				.imageType("jdk")
				.url(url)
				.filename(assetName);
	}

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "graalvm-ce";
		}

		@Override
		public String vendor() {
			return VENDOR;
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new GraalVmCe(config);
		}
	}
}
