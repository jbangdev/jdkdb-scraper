package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.DownloadResult;
import dev.jbang.jdkdb.scraper.GitHubReleaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for Adoptium Eclipse Temurin Early Access releases from GitHub */
public class TemurinEa extends GitHubReleaseScraper {
	private static final String VENDOR = "temurin";

	// Filename pattern: OpenJDK{version}U-{type}_{arch}_{os}_{timestamp}_{version}.{ext}
	private static final Pattern FILENAME_PATTERN =
			Pattern.compile("^OpenJDK([0-9]+)U?-([a-z]+)_([^_]+)_([^_]+)_([^_]+)_([^.]+)\\.(tar\\.gz|zip|pkg|msi)$");

	public TemurinEa(ScraperConfig config) {
		super(config);
	}

	@Override
	protected String getGitHubOrg() {
		return "adoptium";
	}

	@Override
	protected List<String> getGitHubRepos() throws Exception {
		// Use the helper method to fetch all temurin EA repositories
		return getGitHubReposFromOrg(getGitHubOrg(), "temurin", "^temurin\\d+-binaries$");
	}

	@Override
	protected void processRelease(List<JdkMetadata> allMetadata, JsonNode release) throws Exception {
		// Only process prereleases (EA releases)
		boolean isPrerelease = release.path("prerelease").asBoolean(false);
		if (!isPrerelease) {
			return;
		}

		processReleaseAssets(allMetadata, release, this::processAsset);
	}

	protected boolean shouldProcessAsset(JsonNode asset) {
		// Only process OpenJDK files with known extensions
		String assetName = asset.get("name").asText();
		return assetName.startsWith("OpenJDK")
				&& (assetName.endsWith(".tar.gz")
						|| assetName.endsWith(".zip")
						|| assetName.endsWith(".pkg")
						|| assetName.endsWith(".msi"));
	}

	private JdkMetadata processAsset(JsonNode release, JsonNode asset) throws Exception {
		String assetName = asset.get("name").asText();
		String downloadUrl = asset.get("browser_download_url").asText();

		Matcher matcher = FILENAME_PATTERN.matcher(assetName);
		if (!matcher.matches()) {
			log("Filename doesn't match pattern: " + assetName);
			return null;
		}

		String versionStr = matcher.group(1);
		String imageType = matcher.group(2);
		String arch = matcher.group(3);
		String os = matcher.group(4);
		// String timestamp = matcher.group(5); // Not currently used
		String version = matcher.group(6);
		String ext = matcher.group(7);

		// Only process JDK and JRE
		if (!imageType.equals("jdk") && !imageType.equals("jre")) {
			return null;
		}

		// Extract Java version from filename
		int javaVersion = Integer.parseInt(versionStr);

		// Download and compute hashes
		DownloadResult download = downloadFile(downloadUrl, assetName);

		// Build features list
		List<String> features = new ArrayList<>();
		if (os.contains("alpine")) {
			features.add("musl");
		}

		// Create metadata using builder
		return JdkMetadata.builder()
				.vendor(VENDOR)
				.releaseType("ea")
				.version(version)
				.javaVersion(String.valueOf(javaVersion))
				.jvmImpl("hotspot")
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(ext)
				.imageType(imageType)
				.features(features)
				.url(downloadUrl)
				.download(assetName, download)
				.build();
	}

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "temurin-ea";
		}

		@Override
		public String vendor() {
			return VENDOR;
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new TemurinEa(config);
		}
	}
}
