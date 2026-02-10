package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.DownloadResult;
import dev.jbang.jdkdb.scraper.GitHubReleaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for GraalVM CE Early Access releases from graalvm-ce-dev-builds repository */
public class GraalVmCeEa extends GitHubReleaseScraper {
	private static final String VENDOR = "graalvm";

	// Pattern for dev builds: graalvm-ce-java17-darwin-amd64-dev.tar.gz or similar
	// Also handles: graalvm-ce-java11-linux-aarch64-22.3.0-dev-20220823_1000.tar.gz
	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^graalvm-ce-(?:complete-)?java(\\d{1,2})-(linux|darwin|windows)-(aarch64|amd64)-(.*?)\\.(zip|tar\\.gz)$");

	public GraalVmCeEa(ScraperConfig config) {
		super(config);
	}

	@Override
	protected String getGitHubOrg() {
		return "graalvm";
	}

	@Override
	protected List<String> getGitHubRepos() {
		return List.of("graalvm-ce-dev-builds");
	}

	@Override
	protected void processRelease(List<JdkMetadata> allMetadata, JsonNode release) throws Exception {
		// Exclude Community releases (which start with "jdk")
		String tagName = release.get("tag_name").asText();
		if (tagName.startsWith("jdk")) {
			return;
		}

		boolean isPrerelease = release.path("prerelease").asBoolean(false);
		// Only process prereleases (EA releases)
		if (!isPrerelease) {
			return;
		}

		processReleaseAssets(allMetadata, release, this::processAsset);
	}

	@Override
	protected boolean shouldProcessAsset(JsonNode release, JsonNode asset) {
		String assetName = asset.get("name").asText();
		return assetName.startsWith("graalvm-ce") && (assetName.endsWith("tar.gz") || assetName.endsWith("zip"));
	}

	private JdkMetadata processAsset(JsonNode release, JsonNode asset) throws Exception {
		String tagName = release.get("tag_name").asText();
		String assetName = asset.get("name").asText();

		Matcher matcher = FILENAME_PATTERN.matcher(assetName);
		if (!matcher.matches()) {
			warn("Skipping " + assetName + " (does not match pattern)");
			return null;
		}

		String javaVersion = matcher.group(1);
		String os = matcher.group(2);
		String arch = matcher.group(3);
		String version = matcher.group(4);
		String ext = matcher.group(5);

		String url = String.format(
				"https://github.com/%s/%s/releases/download/%s/%s",
				getGitHubOrg(), getGitHubRepos().get(0), tagName, assetName);

		// Download and compute hashes
		DownloadResult download = downloadFile(url, assetName);

		// Create metadata using builder
		return JdkMetadata.builder()
				.vendor(VENDOR)
				.releaseType("ea")
				.version(version + "+java" + javaVersion)
				.javaVersion(javaVersion)
				.jvmImpl("graalvm")
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(ext)
				.imageType("jdk")
				.url(url)
				.download(assetName, download)
				.build();
	}

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "graalvm-ce-ea";
		}

		@Override
		public String vendor() {
			return VENDOR;
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new GraalVmCeEa(config);
		}
	}
}
