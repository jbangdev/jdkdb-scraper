package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.BaseScraper;
import dev.jbang.jdkdb.scraper.InterruptedProgressException;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import dev.jbang.jdkdb.scraper.TooManyFailuresException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for GraalVM CE Early Access releases from graalvm-ce-dev-builds repository */
public class GraalVmCeEa extends BaseScraper {
	private static final String VENDOR = "graalvm";
	private static final String GITHUB_ORG = "graalvm";
	private static final String GITHUB_REPO = "graalvm-ce-dev-builds";
	private static final String GITHUB_API_BASE = "https://api.github.com/repos";

	// Pattern for dev builds: graalvm-ce-java17-darwin-amd64-dev.tar.gz or similar
	// Also handles: graalvm-ce-java11-linux-aarch64-22.3.0-dev-20220823_1000.tar.gz
	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^graalvm-ce-(?:complete-)?java(\\d{1,2})-(linux|darwin|windows)-(aarch64|amd64)-(.*?)\\.(zip|tar\\.gz)$");

	public GraalVmCeEa(ScraperConfig config) {
		super(config);
	}

	@Override
	protected List<JdkMetadata> scrape() throws Exception {
		List<JdkMetadata> allMetadata = new ArrayList<>();

		log("Fetching EA releases from GitHub");
		String releasesUrl = String.format("%s/%s/%s/releases?per_page=100", GITHUB_API_BASE, GITHUB_ORG, GITHUB_REPO);

		String json = httpUtils.downloadString(releasesUrl);
		JsonNode releases = readJson(json);

		if (!releases.isArray()) {
			log("No releases found");
			return allMetadata;
		}

		try {
			for (JsonNode release : releases) {
				// Only process prereleases (EA releases)
				boolean isPrerelease = release.path("prerelease").asBoolean(false);
				if (!isPrerelease) {
					continue;
				}

				String tagName = release.path("tag_name").asText();

				// Exclude Community releases (which start with "jdk")
				if (tagName.startsWith("jdk")) {
					continue;
				}

				log("Processing EA release: " + tagName);

				JsonNode assets = release.path("assets");
				if (!assets.isArray()) {
					continue;
				}

				for (JsonNode asset : assets) {
					String assetName = asset.path("name").asText();

					if (!assetName.startsWith("graalvm-ce")
							|| (!assetName.endsWith("tar.gz") && !assetName.endsWith("zip"))) {
						continue;
					}

					if (metadataExists(assetName)) {
						log("Skipping " + assetName + " (already exists)");
						continue;
					}

					try {
						processAsset(tagName, assetName, allMetadata);
					} catch (InterruptedProgressException | TooManyFailuresException e) {
						throw e;
					} catch (Exception e) {
						log("Failed to process " + assetName + ": " + e.getMessage());
					}
				}
			}
		} catch (InterruptedProgressException e) {
			log("Reached progress limit, aborting");
		}

		return allMetadata;
	}

	private void processAsset(String tagName, String assetName, List<JdkMetadata> allMetadata) throws Exception {

		Matcher matcher = FILENAME_PATTERN.matcher(assetName);
		if (!matcher.matches()) {
			log("Skipping " + assetName + " (does not match pattern)");
			return;
		}

		String javaVersion = matcher.group(1);
		String os = matcher.group(2);
		String arch = matcher.group(3);
		String version = matcher.group(4);
		String ext = matcher.group(5);

		String url = String.format(
				"https://github.com/%s/%s/releases/download/%s/%s", GITHUB_ORG, GITHUB_REPO, tagName, assetName);

		// Download and compute hashes
		DownloadResult download = downloadFile(url, assetName);

		// Create metadata
		JdkMetadata metadata = new JdkMetadata();
		metadata.setVendor(VENDOR);
		metadata.setFilename(assetName);
		metadata.setReleaseType("ea");
		metadata.setVersion(version + "+java" + javaVersion);
		metadata.setJavaVersion(javaVersion);
		metadata.setJvmImpl("graalvm");
		metadata.setOs(normalizeOs(os));
		metadata.setArchitecture(normalizeArch(arch));
		metadata.setFileType(ext);
		metadata.setImageType("jdk");
		metadata.setFeatures(new ArrayList<>());
		metadata.setUrl(url);
		metadata.setMd5(download.md5());
		metadata.setMd5File(assetName + ".md5");
		metadata.setSha1(download.sha1());
		metadata.setSha1File(assetName + ".sha1");
		metadata.setSha256(download.sha256());
		metadata.setSha256File(assetName + ".sha256");
		metadata.setSha512(download.sha512());
		metadata.setSha512File(assetName + ".sha512");
		metadata.setSize(download.size());

		saveMetadataFile(metadata);
		allMetadata.add(metadata);
		log("Processed " + assetName);
	}

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "graalvm-ce-ea";
		}

		@Override
		public String vendor() {
			return "graalvm";
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new GraalVmCeEa(config);
		}
	}
}
