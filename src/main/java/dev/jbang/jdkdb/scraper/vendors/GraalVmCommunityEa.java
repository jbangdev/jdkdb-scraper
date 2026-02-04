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

/** Scraper for GraalVM Community Early Access releases from graalvm-ce-dev-builds repository */
public class GraalVmCommunityEa extends GitHubReleaseScraper {
	private static final String VENDOR = "graalvm-community";

	// Pattern for community dev builds: graalvm-community-jdk-17.0.8_linux-x64_bin.tar.gz
	// or with build number: graalvm-community-jdk-21.0.1-dev_linux-x64_bin.tar.gz
	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^graalvm-community-jdk-(\\d{1,2}\\.\\d{1}\\.\\d{1,3}(?:-dev)?)_(linux|macos|windows)-(aarch64|x64)_bin\\.(zip|tar\\.gz)$");

	public GraalVmCommunityEa(ScraperConfig config) {
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
	protected List<JdkMetadata> processRelease(JsonNode release) throws Exception {
		// Only process prereleases (EA releases)
		boolean isPrerelease = release.path("prerelease").asBoolean(false);
		if (!isPrerelease) {
			return null;
		}

		String tagName = release.path("tag_name").asText();

		if (!shouldProcessTag(tagName)) {
			return List.of();
		}

		return processReleaseAssets(release, asset -> {
			String assetName = asset.path("name").asText();

			if (!shouldProcessAsset(assetName)) {
				return null;
			}

			return processAsset(tagName, assetName);
		});
	}

	@Override
	protected boolean shouldProcessTag(String tagName) {
		// Only process Community releases (which start with "jdk")
		return tagName.startsWith("jdk");
	}

	@Override
	protected boolean shouldProcessAsset(String assetName) {
		return assetName.startsWith("graalvm-community") && (assetName.endsWith("tar.gz") || assetName.endsWith("zip"));
	}

	private JdkMetadata processAsset(String tagName, String assetName) throws Exception {

		Matcher matcher = FILENAME_PATTERN.matcher(assetName);
		if (!matcher.matches()) {
			log("Skipping " + assetName + " (does not match pattern)");
			return null;
		}

		String javaVersion = matcher.group(1);
		String os = matcher.group(2);
		String arch = matcher.group(3);
		String ext = matcher.group(4);

		String url = String.format(
				"https://github.com/%s/%s/releases/download/%s/%s",
				getGitHubOrg(), getGitHubRepos().get(0), tagName, assetName);

		// Download and compute hashes
		DownloadResult download = downloadFile(url, assetName);

		// Create metadata using builder
		return JdkMetadata.builder()
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
				.download(assetName, download)
				.build();
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
