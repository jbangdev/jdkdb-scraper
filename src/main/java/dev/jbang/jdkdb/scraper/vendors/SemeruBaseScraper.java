package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.DownloadResult;
import dev.jbang.jdkdb.scraper.GitHubReleaseScraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Base class for IBM Semeru scrapers (both Open and Certified editions) */
public abstract class SemeruBaseScraper extends GitHubReleaseScraper {
	// Parse filename patterns for both open and certified releases
	protected Pattern rpmPattern = Pattern.compile(
			"^ibm-semeru-(?:open|certified)-[0-9]+-(jre|jdk)-(.+)\\.(x86_64|s390x|ppc64|ppc64le|aarch64)\\.rpm$");
	protected Pattern tarPattern = Pattern.compile(
			"^ibm-semeru-(?:open|certified)-(jre|jdk)_(x64|x86-32|s390x|ppc64|ppc64le|aarch64)_(aix|linux|mac|windows)_.+_openj9-.+\\.(tar\\.gz|zip|msi|pkg)$");

	public SemeruBaseScraper(ScraperConfig config) {
		super(config);
	}

	@Override
	protected String getGitHubOrg() {
		return "ibmruntimes";
	}

	@Override
	protected List<String> getGitHubRepos() throws Exception {
		// Use the helper method to fetch all semeru repositories
		return getGitHubReposFromOrg(getGitHubOrg(), getRepoSearchString(), getRepoPattern());
	}

	/** Get the search string for repository names */
	protected abstract String getRepoSearchString();

	/** Get the pattern for matching repository names */
	protected abstract String getRepoPattern();

	/** Get the filename prefix to accept (e.g., "ibm-semeru-open-" or "ibm-semeru-certified-") */
	protected abstract String getFilenamePrefix();

	/** Get the vendor identifier for metadata */
	protected abstract String getVendor();

	/** Get additional features to add to metadata */
	protected abstract List<String> getAdditionalFeatures();

	private static final Pattern versionPattern = Pattern.compile("jdk-(.*)_openj9-(.*)");

	@Override
	protected void processRelease(List<JdkMetadata> allMetadata, JsonNode release) throws Exception {
		String tagName = release.get("tag_name").asText();
		Matcher versionMatcher = versionPattern.matcher(tagName);
		if (!versionMatcher.matches()) {
			return;
		}
		String parsedJavaVersion = versionMatcher.group(1);
		String openj9Version = versionMatcher.group(2);
		String version = parsedJavaVersion + "_openj9-" + openj9Version;
		processReleaseAssets(
				allMetadata, release, (r, asset) -> processAsset(release, asset, version, parsedJavaVersion));
	}

	@Override
	protected boolean shouldProcessAsset(JsonNode release, JsonNode asset) {
		String assetName = asset.get("name").asText();
		return assetName.startsWith(getFilenamePrefix());
	}

	private JdkMetadata processAsset(JsonNode release, JsonNode asset, String version, String parsedJavaVersion)
			throws Exception {
		String filename = asset.get("name").asText();
		String url = asset.get("browser_download_url").asText();

		String imageType = null;
		String arch = null;
		String os = null;
		String extension = null;

		Matcher rpmMatcher = rpmPattern.matcher(filename);
		if (rpmMatcher.matches()) {
			imageType = rpmMatcher.group(1);
			arch = rpmMatcher.group(3);
			os = "linux";
			extension = "rpm";
		} else {
			Matcher tarMatcher = tarPattern.matcher(filename);
			if (tarMatcher.matches()) {
				imageType = tarMatcher.group(1);
				arch = tarMatcher.group(2);
				os = tarMatcher.group(3);
				extension = tarMatcher.group(4);
			}
		}

		if (imageType == null) {
			if (!filename.endsWith(".txt")
					&& !filename.endsWith(".sig")
					&& !filename.contains("-debugimage_")
					&& !filename.contains("-testimage_")) {
				// Only show message for unexpected files
				warn("Skipping " + filename + " (does not match pattern)");
			}
			return null;
		}

		// Download and compute hashes
		DownloadResult download = downloadFile(url, filename);

		// Build features list
		List<String> features = new ArrayList<>(getAdditionalFeatures());

		// Create metadata using builder
		return JdkMetadata.builder()
				.vendor(getVendor())
				.releaseType("ga")
				.version(version)
				.javaVersion(parsedJavaVersion)
				.jvmImpl("openj9")
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(extension)
				.imageType(imageType)
				.features(features)
				.url(url)
				.download(filename, download)
				.build();
	}
}
