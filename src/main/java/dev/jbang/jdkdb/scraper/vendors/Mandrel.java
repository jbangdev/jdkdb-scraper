package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.GitHubReleaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for Mandrel (Red Hat's downstream distribution of GraalVM) releases */
public class Mandrel extends GitHubReleaseScraper {
	private static final String VENDOR = "mandrel";
	private static final String GITHUB_ORG = "graalvm";
	private static final String GITHUB_REPO = "mandrel";

	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^mandrel-java(\\d{1,2})-(linux|macos|windows)-(amd64|aarch64)-([\\d+.]{2,}.*)\\.tar\\.gz$");

	public Mandrel(ScraperConfig config) {
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
		processReleaseAssets(release, this::processAsset);
	}

	private JdkMetadata processAsset(JsonNode release, JsonNode asset) {
		// Only process mandrel-java files with tar.gz extension
		String assetName = asset.get("name").asText();
		if (!assetName.startsWith("mandrel-java") || !assetName.endsWith(".tar.gz")) {
			fine("Skipping " + assetName + " (non-Mandrel Java tar.gz asset)");
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

		String tagName = release.get("tag_name").asText();

		matcher.matches();
		String javaVersion = matcher.group(1);
		String os = matcher.group(2);
		String arch = matcher.group(3);
		String version = matcher.group(4);

		// Determine release type
		String releaseType;
		if (version.endsWith("Final")) {
			releaseType = "ga";
		} else if (version.contains("Alpha") || version.contains("Beta")) {
			releaseType = "ea";
		} else {
			releaseType = "ea";
		}

		String url = String.format(
				"https://github.com/%s/%s/releases/download/%s/%s", getGitHubOrg(), GITHUB_REPO, tagName, assetName);

		// Create metadata
		return JdkMetadata.create()
				.vendor(VENDOR)
				.releaseType(releaseType)
				.version(version + "+java" + javaVersion)
				.javaVersion(javaVersion)
				.jvmImpl("graalvm")
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType("tar.gz")
				.imageType("jdk")
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
			return new Mandrel(config);
		}
	}
}
