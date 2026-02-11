package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.GitHubReleaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for Oracle GraalVM Early Access (EA) builds from GitHub. */
public class OracleGraalVmEa extends GitHubReleaseScraper {
	private static final String VENDOR = "oracle-graalvm";
	private static final String GITHUB_ORG = "graalvm";
	private static final String GITHUB_REPO = "oracle-graalvm-ea-builds";

	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^graalvm-jdk(?:-[0-9]{1,2}e[0-9]{1})?-([0-9]{1,2}\\.[0-9]{1}\\.[0-9]{1,3}-ea\\.[0-9]{1,2})_(linux|macos|windows)-(aarch64|x64)_bin(?:-(notarized))?\\.(?:zip|tar\\.gz)$");

	public OracleGraalVmEa(ScraperConfig config) {
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
		// Only process releases with jdk tag prefix
		String tagName = release.get("tag_name").asText();
		if (!tagName.startsWith("jdk")) {
			return;
		}
		processReleaseAssets(allMetadata, release, this::processAsset);
	}

	private JdkMetadata processAsset(JsonNode release, JsonNode asset) {
		String assetName = asset.get("name").asText();
		String downloadUrl = asset.get("browser_download_url").asText();

		Matcher matcher = FILENAME_PATTERN.matcher(assetName);
		if (!matcher.matches()) {
			if (!assetName.endsWith(".sha256") && !assetName.startsWith("maven-resource-bundle-")) {
				warn("Skipping " + assetName + " (does not match pattern)");
			}
			return null;
		}

		String metadataFilename = toMetadataFilename(release, asset);
		if (metadataExists(metadataFilename)) {
			return skipped(metadataFilename);
		}

		String javaVersion = matcher.group(1);
		String os = matcher.group(2);
		String arch = matcher.group(3);
		String features = matcher.group(4) != null ? matcher.group(4) : "";
		String extension = assetName.endsWith(".zip") ? "zip" : "tar.gz";

		// Create metadata
		return JdkMetadata.create()
				.vendor(VENDOR)
				.releaseType("ea")
				.version(javaVersion)
				.javaVersion(javaVersion)
				.jvmImpl("graalvm")
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(extension)
				.imageType("jdk")
				.features(features.isEmpty() ? null : List.of(features))
				.url(downloadUrl)
				.filename(assetName);
	}

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "oracle-graalvm-ea";
		}

		@Override
		public String vendor() {
			return VENDOR;
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new OracleGraalVmEa(config);
		}
	}
}
