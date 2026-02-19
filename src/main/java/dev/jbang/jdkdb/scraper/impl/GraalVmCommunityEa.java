package dev.jbang.jdkdb.scraper.impl;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.GitHubReleaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for GraalVM Community Early Access releases from graalvm-ce-dev-builds repository */
public class GraalVmCommunityEa extends GitHubReleaseScraper {
	private static final String VENDOR = "graalvm-community";
	private static final String GITHUB_ORG = "graalvm";
	private static final String GITHUB_REPO = "graalvm-ce-dev-builds";

	// Pattern for community dev builds: graalvm-community-dev-linux-amd64.tar.gz
	private static final Pattern FILENAME_PATTERN =
			Pattern.compile("^graalvm-community-dev-(linux|macos|darwin|windows)-(aarch64|amd64)\\.(zip|tar\\.gz)$");
	private static final Pattern VERSION_PATTERN = Pattern.compile("^(.*)-dev-.*$");

	public GraalVmCommunityEa(ScraperConfig config) {
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
		String tagName = release.get("tag_name").asText();
		Matcher versionMatcher = VERSION_PATTERN.matcher(tagName);
		if (!versionMatcher.matches()) {
			warn("Skipping release " + tagName + " (does not match version pattern)");
			return;
		}
		String javaVersion = versionMatcher.group(1);

		processReleaseAssets(release, (r, asset) -> processAsset(release, asset, javaVersion));
	}

	private JdkMetadata processAsset(JsonNode release, JsonNode asset, String javaVersion) {
		String tagName = release.get("tag_name").asText();
		String assetName = asset.get("name").asText();

		if (isOldRelease(release)) {
			fine("Skipping old EA release " + tagName);
			return null;
		}

		if (!assetName.startsWith("graalvm-community-dev-")
				|| !(assetName.endsWith("tar.gz") || assetName.endsWith("zip"))) {
			fine("Skipping " + assetName + " (non-GraalVM Community asset)");
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

		String os = matcher.group(1);
		String arch = matcher.group(2);
		String ext = matcher.group(3);

		String url = String.format(
				"https://github.com/%s/%s/releases/download/%s/%s", getGitHubOrg(), GITHUB_REPO, tagName, assetName);

		// Create metadata using builder
		return JdkMetadata.create()
				.vendor(VENDOR)
				.releaseType("ea")
				.version(tagName)
				.javaVersion(javaVersion)
				.jvmImpl("graalvm")
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(normalizeFileType(ext))
				.imageType("jdk")
				.url(url)
				.filename(assetName);
	}

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "graalvm-community-ea";
		}

		@Override
		public String vendor() {
			return VENDOR;
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new GraalVmCommunityEa(config);
		}
	}
}
