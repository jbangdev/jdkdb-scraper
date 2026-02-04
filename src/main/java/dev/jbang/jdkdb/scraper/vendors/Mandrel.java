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

/** Scraper for Mandrel (Red Hat's downstream distribution of GraalVM) releases */
public class Mandrel extends GitHubReleaseScraper {
	private static final String VENDOR = "mandrel";

	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^mandrel-java(\\d{1,2})-(linux|macos|windows)-(amd64|aarch64)-([\\d+.]{2,}.*)\\.tar\\.gz$");

	public Mandrel(ScraperConfig config) {
		super(config);
	}

	@Override
	protected String getGitHubOrg() {
		return "graalvm";
	}

	@Override
	protected List<String> getGitHubRepos() {
		return List.of("mandrel");
	}

	@Override
	protected List<JdkMetadata> processRelease(JsonNode release) throws Exception {
		String tagName = release.get("tag_name").asText();

		if (!shouldProcessTag(tagName)) {
			return null;
		}

		return processReleaseAssets(release, asset -> {
			String assetName = asset.get("name").asText();

			if (!shouldProcessAsset(assetName)) {
				return null;
			}

			return processAsset(tagName, assetName);
		});
	}

	@Override
	protected boolean shouldProcessAsset(String assetName) {
		// Only process mandrel-java files with tar.gz extension
		return assetName.startsWith("mandrel-java") && assetName.endsWith(".tar.gz");
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
				"https://github.com/%s/%s/releases/download/%s/%s",
				getGitHubOrg(), getGitHubRepos().get(0), tagName, assetName);

		// Download and compute hashes
		DownloadResult download = downloadFile(url, assetName);

		// Create metadata using builder
		return JdkMetadata.builder()
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
				.download(assetName, download)
				.build();
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
