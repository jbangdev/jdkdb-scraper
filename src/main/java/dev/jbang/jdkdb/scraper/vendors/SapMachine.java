package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.GitHubReleaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for SAP Machine releases */
public class SapMachine extends GitHubReleaseScraper {
	private static final String VENDOR = "sapmachine";
	private static final Pattern RPM_PATTERN =
			Pattern.compile("^sapmachine-(jdk|jre)-([0-9].+)\\.(aarch64|ppc64le|x86_64)\\.rpm$");
	private static final Pattern BIN_PATTERN = Pattern.compile(
			"^sapmachine-(jdk|jre)-([0-9].+)_(aix|linux|macos|osx|windows)-(x64|aarch64|ppc64|ppc64le|x64)-?(.*)_bin\\.(.+)$");

	public SapMachine(ScraperConfig config) {
		super(config);
	}

	@Override
	protected String getGitHubOrg() {
		return "SAP";
	}

	@Override
	protected String getGitHubRepo() {
		return "SapMachine";
	}

	@Override
	protected List<JdkMetadata> processRelease(JsonNode release) throws Exception {
		List<JdkMetadata> metadataList = new ArrayList<>();

		String tagName = release.get("tag_name").asText();

		JsonNode assets = release.get("assets");
		if (assets == null || !assets.isArray()) {
			return metadataList;
		}

		for (JsonNode asset : assets) {
			String assetName = asset.get("name").asText();
			String downloadUrl = asset.get("browser_download_url").asText();

			if (metadataExists(assetName)) {
				log("Skipping " + assetName + " (already exists)");
				continue;
			}

			try {
				JdkMetadata metadata = processAsset(tagName, assetName, downloadUrl);
				if (metadata != null) {
					metadataList.add(metadata);
				}
			} catch (Exception e) {
				fail(assetName, e);
			}
		}

		return metadataList;
	}

	private JdkMetadata processAsset(String tagName, String filename, String url) throws Exception {
		String imageType = null;
		String version = null;
		String os = null;
		String arch = null;
		String features = "";
		String ext = null;

		// Try RPM pattern first
		Matcher rpmMatcher = RPM_PATTERN.matcher(filename);
		if (rpmMatcher.matches()) {
			imageType = rpmMatcher.group(1);
			version = rpmMatcher.group(2);
			os = "linux";
			arch = rpmMatcher.group(3);
			ext = "rpm";
		} else {
			// Try BIN pattern
			Matcher binMatcher = BIN_PATTERN.matcher(filename);
			if (binMatcher.matches()) {
				imageType = binMatcher.group(1);
				version = binMatcher.group(2);
				os = binMatcher.group(3);
				arch = binMatcher.group(4);
				features = binMatcher.group(5) != null ? binMatcher.group(5) : "";
				ext = binMatcher.group(6);
			}
		}

		if (imageType == null) {
			log("Filename doesn't match pattern: " + filename);
			return null;
		}

		// Determine release type
		String releaseType = version.contains("ea") ? "ea" : "ga";

		// Download and compute hashes
		DownloadResult download = downloadFile(url, filename);

		// Create metadata
		JdkMetadata metadata = new JdkMetadata();
		metadata.setVendor(VENDOR);
		metadata.setFilename(filename);
		metadata.setReleaseType(releaseType);
		metadata.setVersion(version);
		metadata.setJavaVersion(version);
		metadata.setJvmImpl("hotspot");
		metadata.setOs(normalizeOs(os));
		metadata.setArchitecture(normalizeArch(arch));
		metadata.setFileType(ext);
		metadata.setImageType(imageType);
		metadata.setFeatures(features.isEmpty() ? new ArrayList<>() : List.of(features.split(",")));
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

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "sapmachine";
		}

		@Override
		public String vendor() {
			return "sapmachine";
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new SapMachine(config);
		}
	}
}
