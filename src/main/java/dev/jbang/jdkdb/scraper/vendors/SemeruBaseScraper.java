package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.BaseScraper;
import dev.jbang.jdkdb.scraper.InterruptedProgressException;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import dev.jbang.jdkdb.scraper.TooManyFailuresException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Base class for IBM Semeru scrapers (both Open and Certified editions) */
public abstract class SemeruBaseScraper extends BaseScraper {
	private static final String ORG = "ibmruntimes";
	private static final String GITHUB_API_BASE = "https://api.github.com/repos";

	public SemeruBaseScraper(ScraperConfig config) {
		super(config);
	}

	/** Get the list of Java versions to scrape */
	protected abstract List<String> getJavaVersions();

	/** Get the filename prefix to accept (e.g., "ibm-semeru-open-" or "ibm-semeru-certified-") */
	protected abstract String getFilenamePrefix();

	/** Get the vendor identifier for metadata */
	protected abstract String getVendor();

	/** Get additional features to add to metadata */
	protected abstract List<String> getAdditionalFeatures();

	@Override
	protected List<JdkMetadata> scrape() throws Exception {
		List<JdkMetadata> allMetadata = new ArrayList<>();

		try {
			// Process each Java version
			for (String javaVersion : getJavaVersions()) {
				log("Processing " + getVendor() + " version: " + javaVersion);
				allMetadata.addAll(scrapeVersion(javaVersion));
			}
		} catch (InterruptedProgressException e) {
			log("Reached progress limit, aborting");
		}

		return allMetadata;
	}

	private List<JdkMetadata> scrapeVersion(String javaVersion) throws Exception {
		List<JdkMetadata> metadataList = new ArrayList<>();

		String repo = "semeru" + javaVersion + "-binaries";
		String releasesUrl = String.format("%s/%s/%s/releases?per_page=100", GITHUB_API_BASE, ORG, repo);

		String json = httpUtils.downloadString(releasesUrl);
		JsonNode releases = readJson(json);

		if (!releases.isArray()) {
			log("No releases found for version " + javaVersion);
			return metadataList;
		}

		for (JsonNode release : releases) {
			metadataList.addAll(processRelease(release, javaVersion));
		}

		return metadataList;
	}

	private List<JdkMetadata> processRelease(JsonNode release, String javaVersion) throws Exception {
		List<JdkMetadata> metadataList = new ArrayList<>();

		String tagName = release.get("tag_name").asText();

		// Parse version from tag name
		Pattern versionPattern = Pattern.compile("jdk-(.*)_openj9-(.*)");
		Matcher versionMatcher = versionPattern.matcher(tagName);

		if (!versionMatcher.matches()) {
			return metadataList;
		}

		String parsedJavaVersion = versionMatcher.group(1);
		String openj9Version = versionMatcher.group(2);
		String version = parsedJavaVersion + "_openj9-" + openj9Version;

		JsonNode assets = release.get("assets");
		if (assets == null || !assets.isArray()) {
			return metadataList;
		}

		for (JsonNode asset : assets) {
			String assetName = asset.get("name").asText();
			String downloadUrl = asset.get("browser_download_url").asText();

			// Skip files that don't match our prefix
			if (!assetName.startsWith(getFilenamePrefix())) {
				continue;
			}

			if (metadataExists(assetName)) {
				log("Skipping " + assetName + " (already exists)");
				continue;
			}

			try {
				JdkMetadata metadata = processAsset(assetName, downloadUrl, version, parsedJavaVersion);
				if (metadata != null) {
					metadataList.add(metadata);
				}
			} catch (InterruptedProgressException | TooManyFailuresException e) {
				throw e;
			} catch (Exception e) {
				fail(assetName, e);
			}
		}

		return metadataList;
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

		// Create metadata
		JdkMetadata metadata = new JdkMetadata();
		metadata.setVendor(getVendor());
		metadata.setFilename(filename);
		metadata.setReleaseType("ga");
		metadata.setVersion(version);
		metadata.setJavaVersion(javaVersion);
		metadata.setJvmImpl("openj9");
		metadata.setOs(normalizeOs(os));
		metadata.setArchitecture(normalizeArch(arch));
		metadata.setFileType(extension);
		metadata.setImageType(imageType);
		metadata.setFeatures(features);
		metadata.setUrl(url);
		metadata.setMd5(download.md5());
		metadata.setMd5File(filename + ".md5");
		metadata.setSha1(download.sha1());
		metadata.setSha1File(filename + ".sha1");
		metadata.setSha256(download.sha256());
		metadata.setSha256File(filename + ".sha256");
		metadata.setSha512(download.sha512());
		metadata.setSha512File(filename + ".sha512");
		metadata.setSize(download.size());

		saveMetadataFile(metadata);
		success(filename);

		return metadata;
	}
}
