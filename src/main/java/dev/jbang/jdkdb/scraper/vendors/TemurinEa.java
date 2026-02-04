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
	protected List<JdkMetadata> processRelease(JsonNode release) throws Exception {
		String tagName = release.get("tag_name").asText();

		if (!shouldProcessTag(tagName)) {
			return null;
		}

		// Only process prereleases (EA releases)
		boolean isPrerelease = release.path("prerelease").asBoolean(false);
		if (!isPrerelease) {
			return null;
		}

		return processReleaseAssets(release, asset -> {
			String filename = asset.path("name").asText();
			String downloadUrl = asset.path("browser_download_url").asText();

			if (!shouldProcessAsset(filename)) {
				return null;
			}

			return processAsset(filename, downloadUrl);
		});
	}

	protected boolean shouldProcessAsset(String assetName) {
		// Only process OpenJDK files with known extensions
		return assetName.startsWith("OpenJDK")
				&& (assetName.endsWith(".tar.gz")
						|| assetName.endsWith(".zip")
						|| assetName.endsWith(".pkg")
						|| assetName.endsWith(".msi"));
	}

	private JdkMetadata processAsset(String filename, String url) throws Exception {

		Matcher matcher = FILENAME_PATTERN.matcher(filename);
		if (!matcher.matches()) {
			log("Filename doesn't match pattern: " + filename);
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
		DownloadResult download = downloadFile(url, filename);

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
				.url(url)
				.download(filename, download)
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
