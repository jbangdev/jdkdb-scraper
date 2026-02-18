package dev.jbang.jdkdb.scraper.impl;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.GitHubReleaseScraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Base class for IBM Semeru scrapers (both Open and Certified editions) */
public abstract class SemeruBaseScraper extends GitHubReleaseScraper {
	// Parse filename patterns for both open and certified releases
	protected static final Pattern rpmPattern = Pattern.compile(
			"^ibm-semeru-(?:open|certified)-[0-9]+-(jre|jdk)-(.+)\\.(x86_64|s390x|ppc64|ppc64le|aarch64)\\.rpm$");
	protected static final Pattern tarPattern = Pattern.compile(
			"^ibm-semeru-(?:open|certified)-(jre|jdk)_(x64|x86-32|s390x|ppc64|ppc64le|aarch64)_(aix|linux|mac|windows)_.+\\.(tar\\.gz|zip|msi|pkg)$");
	protected static final Pattern versionPattern = Pattern.compile("jdk(.*)[_-]openj9-(.*)");

	public SemeruBaseScraper(ScraperConfig config) {
		super(config);
	}

	@Override
	protected String getGitHubOrg() {
		return "ibmruntimes";
	}

	@Override
	protected Iterable<String> getGitHubRepos() throws Exception {
		// Use the helper method to fetch all semeru repositories
		return getReposFromOrg(getGitHubOrg(), getRepoSearchString(), getRepoPattern());
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

	@Override
	protected void processRelease(JsonNode release) throws Exception {
		String tagName = release.get("tag_name").asText();
		Matcher versionMatcher = versionPattern.matcher(tagName);
		if (!versionMatcher.matches()) {
			warn("Skipping release " + tagName + " (tag does not match expected pattern)");
			return;
		}

		processReleaseAssets(release, this::processAsset);
	}

	protected JdkMetadata processAsset(JsonNode release, JsonNode asset) {
		String imageType;
		String arch;
		String os;
		String extension;

		String filename = asset.get("name").asText();
		if (filename == null) {
			warn("Skipping asset with no name");
			return null;
		}
		if (filename.endsWith(".rpm")) {
			Matcher rpmMatcher = rpmPattern.matcher(filename);
			if (!rpmMatcher.matches()) {
				if (!filename.endsWith(".src.rpm")) {
					warn("Skipping " + filename + " (RPM does not match pattern)");
				}
				return null;
			}
			imageType = rpmMatcher.group(1);
			arch = rpmMatcher.group(3);
			os = "linux";
			extension = "rpm";
		} else {
			Matcher tarMatcher = tarPattern.matcher(filename);
			if (!tarMatcher.matches()) {
				if (!filename.endsWith(".txt")
						&& !filename.endsWith(".json")
						&& !filename.endsWith(".sig")
						&& !filename.endsWith(".tap.zip")
						&& !filename.endsWith(".bin")
						&& !filename.contains("-debugimage_")
						&& !filename.contains("-testimage_")) {
					// Only show message for unexpected files
					warn("Skipping " + filename + " (does not match pattern)");
				}
				return null;
			}
			imageType = tarMatcher.group(1);
			arch = tarMatcher.group(2);
			os = tarMatcher.group(3);
			extension = tarMatcher.group(4);
		}

		String assetName = asset.get("name").asText();
		if (!assetName.startsWith(getFilenamePrefix())) {
			warn("Skipping " + assetName + " (does not start with expected prefix)");
			return null;
		}

		String metadataFilename = toMetadataFilename(release, asset);
		if (metadataExists(metadataFilename)) {
			return skipped(metadataFilename);
		}

		String tagName = release.get("tag_name").asText();
		Matcher versionMatcher = versionPattern.matcher(tagName);
		versionMatcher.matches(); // Already verified in processRelease()
		String parsedJavaVersion = versionMatcher.group(1);
		String openj9Version = versionMatcher.group(2);
		String version = parsedJavaVersion + "_openj9-" + openj9Version;

		String url = asset.get("browser_download_url").asText();

		// Build features list
		List<String> features = new ArrayList<>(getAdditionalFeatures());

		// Create metadata
		return JdkMetadata.create()
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
				.filename(filename);
	}
}
