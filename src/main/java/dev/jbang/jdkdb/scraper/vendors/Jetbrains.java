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

/** Scraper for JetBrains Runtime releases */
public class Jetbrains extends BaseScraper {
	private static final String VENDOR = "jetbrains";
	private static final String GITHUB_ORG = "JetBrains";
	private static final String GITHUB_REPO = "JetBrainsRuntime";
	private static final String GITHUB_API_BASE = "https://api.github.com/repos";

	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^jbr(sdk)?(?:_\\w+)?-([0-9][0-9\\+._]{1,})-(linux-musl|linux|osx|macos|windows)-(aarch64|x64|x86)(?:-\\w+)?-(b[0-9\\+.]{1,})(?:_\\w+)?\\.(tar\\.gz|zip|pkg)$");

	private static final Pattern BODY_PATTERN = Pattern.compile(
			"\\|\\s*(?:\\*\\*)?(?<description>[^|]+?)(?:\\*\\*)?\\s*\\|\\s*\\[(?<file>[^\\]]+)\\]\\((?<url>[^\\)]+)\\)\\s*\\|\\s*\\[checksum\\]\\((?<checksumUrl>[^\\)]+)\\)");

	public Jetbrains(ScraperConfig config) {
		super(config);
	}

	@Override
	protected List<JdkMetadata> scrape() throws Exception {
		List<JdkMetadata> allMetadata = new ArrayList<>();

		log("Fetching releases from GitHub");
		String releasesUrl = String.format("%s/%s/%s/releases?per_page=100", GITHUB_API_BASE, GITHUB_ORG, GITHUB_REPO);
		String json = httpUtils.downloadString(releasesUrl);
		JsonNode releases = readJson(json);

		if (!releases.isArray()) {
			log("No releases found");
			return allMetadata;
		}

		try {
			for (JsonNode release : releases) {
				String tagName = release.get("tag_name").asText();
				boolean prerelease = release.get("prerelease").asBoolean();
				String releaseType = prerelease ? "ea" : "ga";
				String body = release.get("body").asText("");

				log("Processing release: " + tagName);

				// Parse assets from the body
				Matcher matcher = BODY_PATTERN.matcher(body);
				while (matcher.find()) {
					String description = matcher.group("description");
					String file = matcher.group("file");
					String url = matcher.group("url");

					try {
						processAsset(file, url, releaseType, description, allMetadata);
					} catch (InterruptedProgressException | TooManyFailuresException e) {
						throw e;
					} catch (Exception e) {
						log("Failed to process " + file + ": " + e.getMessage());
					}
				}
			}
		} catch (InterruptedProgressException e) {
			log("Reached progress limit, aborting");
		}

		return allMetadata;
	}

	private void processAsset(
			String assetName, String url, String releaseType, String description, List<JdkMetadata> allMetadata)
			throws Exception {

		if (metadataExists(assetName)) {
			log("Skipping " + assetName + " (already exists)");
			return;
		}

		// Only process files ending in tar.gz, zip, or pkg
		if (!assetName.matches(".+\\.(tar\\.gz|zip|pkg)$")) {
			return;
		}

		Matcher matcher = FILENAME_PATTERN.matcher(assetName);
		if (!matcher.matches()) {
			log("Skipping " + assetName + " (does not match pattern)");
			return;
		}

		String sdkMarker = matcher.group(1);
		String versionPart = matcher.group(2).replace("_", ".");
		String os = matcher.group(3);
		String arch = matcher.group(4);
		String buildNumber = matcher.group(5);
		String ext = matcher.group(6);

		String version = versionPart + buildNumber;
		String imageType = (sdkMarker != null && !sdkMarker.isEmpty()) ? "jdk" : "jre";

		// Build features list
		List<String> features = new ArrayList<>();
		if (description.contains("fastdebug")) {
			features.add("fastdebug");
		}
		if (description.contains("debug symbols")) {
			features.add("debug");
		}
		if (description.contains("FreeType")) {
			features.add("freetype");
		}
		if (description.contains("JCEF")) {
			features.add("jcef");
		}
		if (description.contains("Legacy Binary")) {
			features.add("legacy");
		}
		if (os.equals("linux-musl")) {
			features.add("musl");
			os = "linux";
		}

		// Download and compute hashes
		DownloadResult download = downloadFile(url, assetName);

		// Create metadata
		JdkMetadata metadata = new JdkMetadata();
		metadata.setVendor(VENDOR);
		metadata.setFilename(assetName);
		metadata.setReleaseType(releaseType);
		metadata.setVersion(version);
		metadata.setJavaVersion(os); // Note: in bash script this was set to OS, likely a bug
		metadata.setJvmImpl("hotspot");
		metadata.setOs(normalizeOs(os));
		metadata.setArchitecture(normalizeArch(arch));
		metadata.setFileType(ext);
		metadata.setImageType(imageType);
		metadata.setFeatures(features);
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
			return "jetbrains";
		}

		@Override
		public String vendor() {
			return "jetbrains";
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new Jetbrains(config);
		}
	}
}
