package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.GitHubReleaseScraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Abstract base class for Trava OpenJDK releases with DCEVM */
public abstract class TravaBaseScraper extends GitHubReleaseScraper {
	protected static final String VENDOR = "trava";
	private static final String GITHUB_ORG = "TravaOpenJDK";

	public TravaBaseScraper(ScraperConfig config) {
		super(config);
	}

	@Override
	protected String getGitHubOrg() {
		return GITHUB_ORG;
	}

	@Override
	protected Iterable<String> getGitHubRepos() throws Exception {
		return List.of(getRepo());
	}

	@Override
	protected void processRelease(JsonNode release) throws Exception {
		processReleaseAssets(release, this::processAsset);
	}

	private JdkMetadata processAsset(JsonNode release, JsonNode asset) {
		// Skip source files and jar files
		String assetName = asset.get("name").asText();
		if (assetName.contains("_source") || assetName.endsWith(".jar")) {
			return null;
		}
		String contentType = asset.path("content_type").asText("");
		if (!contentType.startsWith("application")) {
			return null;
		}
		Matcher matcher = getFilenamePattern().matcher(assetName);
		if (!matcher.matches()) {
			warn("Skipping " + assetName + " (does not match pattern)");
			return null;
		}

		String metadataFilename = toMetadataFilename(release, asset);
		if (metadataExists(metadataFilename)) {
			return skipped(metadataFilename);
		}

		String tagName = release.get("tag_name").asText();
		String version = extractVersion(tagName);

		Matcher filenameMatcher = getFilenamePattern().matcher(assetName);
		String os = filenameMatcher.group(1);
		String arch = extractArch(filenameMatcher);
		String ext = extractExtension(filenameMatcher);

		String url = String.format(
				"https://github.com/%s/%s/releases/download/%s/%s", getGitHubOrg(), getRepo(), tagName, assetName);

		// Create metadata
		return JdkMetadata.create()
				.vendor(VENDOR)
				.releaseType("ga")
				.version(version)
				.javaVersion(version)
				.jvmImpl("hotspot")
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(ext)
				.imageType("jdk")
				.url(url)
				.filename(metadataFilename);
	}

	@Override
	protected String toMetadataFilename(JsonNode release, JsonNode asset) {
		String tagName = release.get("tag_name").asText();
		String version = extractVersion(tagName);
		String assetName = asset.get("name").asText();
		Matcher filenameMatcher = getFilenamePattern().matcher(assetName);
		if (!filenameMatcher.matches()) {
			warn("Skipping " + assetName + " (does not match pattern)");
			return null;
		}
		String os = filenameMatcher.group(1);
		String arch = extractArch(filenameMatcher);
		String ext = extractExtension(filenameMatcher);
		return VENDOR + "-" + version + "-" + os + "-" + arch + "." + ext;
	}

	/** @return The GitHub repository name for this Trava variant */
	protected abstract String getRepo();

	/** @return The Java version for this Trava variant */
	protected abstract String getJavaVersion();

	/** @return The regex pattern for matching release tags */
	protected abstract Pattern getTagPattern();

	/** @return The regex pattern for matching asset filenames */
	protected abstract Pattern getFilenamePattern();

	/** @return Extract version from the tag matcher */
	protected abstract String extractVersion(String tagName);

	/** @return Extract architecture from the filename matcher */
	protected abstract String extractArch(Matcher filenameMatcher);

	/** @return Extract file extension from the filename matcher */
	protected abstract String extractExtension(Matcher filenameMatcher);
}
