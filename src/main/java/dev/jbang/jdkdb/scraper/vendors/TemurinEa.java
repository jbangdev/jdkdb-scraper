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

	// List of Java versions to check for EA releases
	private static final List<Integer> EA_VERSIONS = List.of(24, 25, 26, 27);

	public TemurinEa(ScraperConfig config) {
		super(config);
	}

	@Override
	protected String getGitHubOrg() {
		return "adoptium";
	}

	@Override
	protected List<String> getGitHubRepos() {
		return EA_VERSIONS.stream().map(v -> "temurin" + v + "-binaries").toList();
	}

	@Override
	protected List<JdkMetadata> processRelease(JsonNode release) throws Exception {
		// Only process prereleases (EA releases)
		boolean isPrerelease = release.path("prerelease").asBoolean(false);
		if (!isPrerelease) {
			return List.of();
		}

		return processReleaseAssets(release, asset -> {
			String filename = asset.path("name").asText();
			String downloadUrl = asset.path("browser_download_url").asText();

			// Skip non-JDK files
			if (!filename.startsWith("OpenJDK")
					|| filename.endsWith(".txt")
					|| filename.endsWith(".json")
					|| filename.contains("debugimage")
					|| filename.contains("testimage")) {
				return null;
			}

			return processAsset(filename, downloadUrl);
		});
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
