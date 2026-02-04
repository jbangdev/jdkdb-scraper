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
	protected List<JdkMetadata> processRelease(JsonNode release) throws Exception {
		String tagName = release.get("tag_name").asText();

		if (!shouldProcessTag(tagName)) {
			return null;
		}

		// Parse version from tag name
		Matcher versionMatcher = versionPattern.matcher(tagName);
		versionMatcher.matches(); // We know this will succeed
		String parsedJavaVersion = versionMatcher.group(1);
		String openj9Version = versionMatcher.group(2);
		String version = parsedJavaVersion + "_openj9-" + openj9Version;

		return processReleaseAssets(release, asset -> {
			String assetName = asset.get("name").asText();
			String downloadUrl = asset.get("browser_download_url").asText();

			if (!shouldProcessAsset(assetName)) {
				return null;
			}

			return processAsset(assetName, downloadUrl, version, parsedJavaVersion);
		});
	}

	@Override
	protected boolean shouldProcessTag(String tagName) {
		Matcher versionMatcher = versionPattern.matcher(tagName);
		if (!versionMatcher.matches()) {
			return false;
		}
		return true;
	}

	@Override
	protected boolean shouldProcessAsset(String assetName) {
		return assetName.startsWith(getFilenamePrefix());
	}

	private JdkMetadata processAsset(String filename, String url, String version, String javaVersion) throws Exception {

		// Parse filename patterns for both open and certified releases
		Pattern rpmPattern = Pattern.compile(
				"ibm-semeru-(?:open|certified)-[0-9]+-(jre|jdk)-(.+)\\.(x86_64|s390x|ppc64|ppc64le|aarch64)\\.rpm$");
		Pattern tarPattern = Pattern.compile(
				"ibm-semeru-(?:open|certified)-(jre|jdk)_(x64|x86-32|s390x|ppc64|ppc64le|aarch64)_(aix|linux|mac|windows)_.+_openj9-.+\\.(tar\\.gz|zip|msi)$");

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
			log("Could not parse filename: " + filename);
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
				.javaVersion(javaVersion)
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
