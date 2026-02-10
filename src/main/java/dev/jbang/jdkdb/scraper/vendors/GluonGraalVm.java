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

/** Scraper for Gluon GraalVM releases */
public class GluonGraalVm extends GitHubReleaseScraper {
	private static final String VENDOR = "gluon-graalvm";
	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^graalvm-svm(?:-java([0-9]+))?-(linux|darwin|windows)(?:-(aarch64|x86_64|amd64|m\\d))?-gluon-([0-9.+]+(?:-dev|-[Ff]inal)?)\\.(zip|tar\\.gz)$");

	public GluonGraalVm(ScraperConfig config) {
		super(config);
	}

	@Override
	protected String getGitHubOrg() {
		return "gluonhq";
	}

	@Override
	protected List<String> getGitHubRepos() {
		return List.of("graal");
	}

	@Override
	protected void processRelease(List<JdkMetadata> allMetadata, JsonNode release) throws Exception {
		processReleaseAssets(allMetadata, release, this::processAsset);
	}

	@Override
	protected boolean shouldProcessAsset(JsonNode release, JsonNode asset) {
		// Skip non-matching files
		String assetName = asset.get("name").asText();
		return assetName.startsWith("graalvm-svm-") && !assetName.endsWith(".sha256");
	}

	private JdkMetadata processAsset(JsonNode release, JsonNode asset) throws Exception {
		String tagName = release.get("tag_name").asText();
		boolean isPrerelease = release.get("prerelease").asBoolean();
		String assetName = asset.get("name").asText();

		Matcher matcher = FILENAME_PATTERN.matcher(assetName);
		if (!matcher.matches()) {
			log("Skipping " + assetName + " (does not match pattern)");
			return null;
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

		String url = String.format("https://github.com/gluonhq/graal/releases/download/%s/%s", tagName, assetName);

		// Download and compute hashes
		DownloadResult download = downloadFile(url, assetName);
		// Create metadata using builder
		return JdkMetadata.builder()
				.vendor(VENDOR)
				.releaseType(determineReleaseType(version, isPrerelease))
				.version(version)
				.javaVersion(javaVersion)
				.jvmImpl("graalvm")
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(ext)
				.imageType("jdk")
				.features(List.of("native-image", "substrate-vm"))
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
			return new GluonGraalVm(config);
		}
	}
}
