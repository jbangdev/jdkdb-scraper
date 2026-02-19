package dev.jbang.jdkdb.scraper.impl;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.GitHubReleaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for Gluon GraalVM releases */
public class GluonGraalVm extends GitHubReleaseScraper {
	private static final String VENDOR = "gluon-graalvm";
	private static final String GITHUB_ORG = "gluonhq";
	private static final String GITHUB_REPO = "graal";
	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^graalvm-svm(?:-java([0-9]+))?-(linux|darwin|windows)(?:-(aarch64|x86_64|amd64|m\\d))?-gluon-([0-9.+]+(?:-dev|-[Ff]inal)?)\\.(zip|tar\\.gz)$");

	public GluonGraalVm(ScraperConfig config) {
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
		String tagName = release.get("tag_name").asText();
		String assetName = asset.get("name").asText();

		// Skip non-matching files
		if (!assetName.startsWith("graalvm-svm-") || assetName.endsWith(".sha256")) {
			fine("Skipping " + assetName + " (non-GraalVM asset)");
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

		if (javaVersion == null) {
			javaVersion = "11";
		}
		if (arch == null) {
			arch = "x86_64";
		}

		String releaseType;
		if (assetName.contains("-dev.") || tagName.contains("-dev-")) {
			releaseType = "ea";
		} else {
			releaseType = "ga";
		}

		if (releaseType.equals("ea") && isOldRelease(release)) {
			fine("Skipping old EA release " + tagName);
			return null;
		}

		String url = String.format("https://github.com/gluonhq/graal/releases/download/%s/%s", tagName, assetName);

		// Create metadata using builder
		return JdkMetadata.create()
				.vendor(VENDOR)
				.releaseType(releaseType)
				.version(version)
				.javaVersion(javaVersion)
				.jvmImpl("graalvm")
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(normalizeFileType(ext))
				.imageType("jdk")
				.features(List.of("native-image", "substrate-vm"))
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
			return new GluonGraalVm(config);
		}
	}
}
