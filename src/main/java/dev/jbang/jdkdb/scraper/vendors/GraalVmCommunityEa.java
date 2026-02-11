package dev.jbang.jdkdb.scraper.vendors;

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

	// Pattern for community dev builds: graalvm-community-jdk-17.0.8_linux-x64_bin.tar.gz
	// or with build number: graalvm-community-jdk-21.0.1-dev_linux-x64_bin.tar.gz
	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^graalvm-community-jdk-(\\d{1,2}\\.\\d{1}\\.\\d{1,3}(?:-dev)?)_(linux|macos|windows)-(aarch64|x64)_bin\\.(zip|tar\\.gz)$");

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
	protected void processRelease(List<JdkMetadata> allMetadata, JsonNode release) throws Exception {
		// Only process Community releases (which start with "jdk")
		String tagName = release.get("tag_name").asText();
		if (!tagName.startsWith("jdk")) {
			return;
		}

		boolean isPrerelease = release.path("prerelease").asBoolean(false);
		// Only process prereleases (EA releases)
		if (!isPrerelease) {
			return;
		}

		processReleaseAssets(allMetadata, release, this::processAsset);
	}

	private JdkMetadata processAsset(JsonNode release, JsonNode asset) {
		String tagName = release.get("tag_name").asText();
		String assetName = asset.get("name").asText();

		if (!assetName.startsWith("graalvm-community")
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

		String javaVersion = matcher.group(1);
		String os = matcher.group(2);
		String arch = matcher.group(3);
		String ext = matcher.group(4);

		String url = String.format(
				"https://github.com/%s/%s/releases/download/%s/%s", getGitHubOrg(), GITHUB_REPO, tagName, assetName);

		// Create metadata using builder
		return JdkMetadata.create()
				.vendor(VENDOR)
				.releaseType("ea")
				.version(javaVersion)
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
