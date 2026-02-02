package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.BaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for Adoptium Eclipse Temurin Early Access releases from GitHub */
public class TemurinEa extends BaseScraper {
	private static final String VENDOR = "temurin";
	private static final String GITHUB_ORG = "adoptium";
	private static final String GITHUB_API_BASE = "https://api.github.com/repos";

	// Filename pattern: OpenJDK{version}U-{type}_{arch}_{os}_{timestamp}_{version}.{ext}
	private static final Pattern FILENAME_PATTERN =
			Pattern.compile("^OpenJDK([0-9]+)U?-([a-z]+)_([^_]+)_([^_]+)_([^_]+)_([^.]+)\\.(tar\\.gz|zip|pkg|msi)$");

	// List of Java versions to check for EA releases
	private static final List<Integer> EA_VERSIONS = List.of(24, 25, 26, 27);

	public TemurinEa(ScraperConfig config) {
		super(config);
	}

	@Override
	protected List<JdkMetadata> scrape() throws Exception {
		List<JdkMetadata> allMetadata = new ArrayList<>();

		for (int version : EA_VERSIONS) {
			log("Checking EA releases for Java " + version);
			allMetadata.addAll(scrapeVersion(version));
		}

		return allMetadata;
	}

	private List<JdkMetadata> scrapeVersion(int javaVersion) throws Exception {
		List<JdkMetadata> metadataList = new ArrayList<>();

		String repo = "temurin" + javaVersion + "-binaries";
		String releasesUrl = String.format("%s/%s/%s/releases?per_page=100", GITHUB_API_BASE, GITHUB_ORG, repo);

		log("Fetching releases from " + releasesUrl);
		String json = httpUtils.downloadString(releasesUrl);
		JsonNode releases = objectMapper.readTree(json);

		if (!releases.isArray()) {
			log("No releases found for version " + javaVersion);
			return metadataList;
		}

		for (JsonNode release : releases) {
			// Only process prereleases (EA releases)
			boolean isPrerelease = release.path("prerelease").asBoolean(false);
			if (!isPrerelease) {
				continue;
			}

			metadataList.addAll(processRelease(release, javaVersion));
		}

		return metadataList;
	}

	private List<JdkMetadata> processRelease(JsonNode release, int javaVersion) throws Exception {
		List<JdkMetadata> metadataList = new ArrayList<>();

		String tagName = release.path("tag_name").asText();
		log("Processing EA release: " + tagName);

		JsonNode assets = release.path("assets");
		if (!assets.isArray()) {
			return metadataList;
		}

		for (JsonNode asset : assets) {
			String filename = asset.path("name").asText();
			String downloadUrl = asset.path("browser_download_url").asText();

			// Skip non-JDK files
			if (!filename.startsWith("OpenJDK")
					|| filename.endsWith(".txt")
					|| filename.endsWith(".json")
					|| filename.contains("debugimage")
					|| filename.contains("testimage")) {
				continue;
			}

			if (metadataExists(filename)) {
				log("Skipping " + filename + " (already exists)");
				continue;
			}

			try {
				JdkMetadata metadata = processAsset(filename, downloadUrl, javaVersion);
				if (metadata != null) {
					metadataList.add(metadata);
				}
			} catch (Exception e) {
				fail(filename, e);
			}
		}

		return metadataList;
	}

	private JdkMetadata processAsset(String filename, String url, int javaVersion) throws Exception {

		Matcher matcher = FILENAME_PATTERN.matcher(filename);
		if (!matcher.matches()) {
			log("Filename doesn't match pattern: " + filename);
			return null;
		}

		String versionStr = matcher.group(1);
		String imageType = matcher.group(2);
		String arch = matcher.group(3);
		String os = matcher.group(4);
		String timestamp = matcher.group(5);
		String version = matcher.group(6);
		String ext = matcher.group(7);

		// Only process JDK and JRE
		if (!imageType.equals("jdk") && !imageType.equals("jre")) {
			return null;
		}

		// Download and compute hashes
		DownloadResult download = downloadFile(url, filename);

		// Build features list
		List<String> features = new ArrayList<>();
		if (os.contains("alpine")) {
			features.add("musl");
		}

		// Create metadata
		JdkMetadata metadata = new JdkMetadata();
		metadata.setVendor(VENDOR);
		metadata.setFilename(filename);
		metadata.setReleaseType("ea");
		metadata.setVersion(version);
		metadata.setJavaVersion(String.valueOf(javaVersion));
		metadata.setJvmImpl("hotspot");
		metadata.setOs(normalizeOs(os));
		metadata.setArchitecture(normalizeArch(arch));
		metadata.setFileType(ext);
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

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "temurin-ea";
		}

		@Override
		public String vendor() {
			return "Temurin EA";
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new TemurinEa(config);
		}
	}
}
