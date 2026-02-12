package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.GitHubReleaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for GraalVM Legacy releases (old releases from oracle/graal repo) */
public class GraalVmLegacy extends GitHubReleaseScraper {
	private static final String VENDOR = "graalvm";
	private static final String GITHUB_ORG = "oracle";
	private static final String GITHUB_REPO = "graal";

	// RC releases: graalvm-ce-1.0.0-rc1-linux-amd64.tar.gz
	private static final Pattern RC_PATTERN =
			Pattern.compile("^graalvm-ce-([\\d+.]{2,}-rc\\d+)-(linux|macos)-(amd64|aarch64)\\.(zip|tar\\.gz)$");

	// Regular releases: graalvm-ce-linux-aarch64-19.3.1.tar.gz
	private static final Pattern REGULAR_PATTERN =
			Pattern.compile("^graalvm-ce-(linux|darwin|windows)-(aarch64|amd64)-([\\d+.]{2,}[^.]*)\\.(zip|tar\\.gz)$");

	public GraalVmLegacy(ScraperConfig config) {
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
		if (!tagName.startsWith("vm-")) {
			return;
		}
		processReleaseAssets(release, this::processAsset);
	}

	protected JdkMetadata processAsset(JsonNode release, JsonNode asset) {
		String assetName = asset.get("name").asText();
		String tagName = release.get("tag_name").asText();

		if (!assetName.startsWith("graalvm-ce")) {
			fine("Skipping " + assetName + " (non-GraalVM CE asset)");
			return null;
		}

		// Try RC pattern first
		String releaseType = null;
		String os;
		String arch;
		String version;
		String ext;

		// Check RC pattern again for data extraction
		Matcher rcMatcher = RC_PATTERN.matcher(assetName);
		if (rcMatcher.matches()) {
			releaseType = "ea";
			version = rcMatcher.group(1);
			os = rcMatcher.group(2);
			arch = rcMatcher.group(3);
			ext = rcMatcher.group(4);
		} else {
			// Try regular pattern
			Matcher regularMatcher = REGULAR_PATTERN.matcher(assetName);
			if (!regularMatcher.matches()) {
				warn("Skipping " + assetName + " (does not match pattern)");
				return null;
			}
			// Check if it's a dev build
			releaseType = assetName.contains("dev-b") ? "ea" : "ga";
			os = regularMatcher.group(1);
			arch = regularMatcher.group(2);
			version = regularMatcher.group(3);
			ext = regularMatcher.group(4);
		}

		String metadataFilename = toMetadataFilename(release, asset);
		if (metadataExists(metadataFilename)) {
			return skipped(metadataFilename);
		}

		String url = String.format(
				"https://github.com/%s/%s/releases/download/%s/%s", GITHUB_ORG, GITHUB_REPO, tagName, assetName);

		// Create metadata
		return JdkMetadata.create()
				.vendor(VENDOR)
				.releaseType(releaseType)
				.version(version)
				.javaVersion("8")
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
			return "graalvm-legacy";
		}

		@Override
		public String vendor() {
			return VENDOR;
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new GraalVmLegacy(config);
		}
	}
}
