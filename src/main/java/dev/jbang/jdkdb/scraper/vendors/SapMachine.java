package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.DownloadResult;
import dev.jbang.jdkdb.scraper.GitHubReleaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for SAP Machine releases */
public class SapMachine extends GitHubReleaseScraper {
	private static final String VENDOR = "sapmachine";
	private static final Pattern RPM_PATTERN =
			Pattern.compile("^sapmachine-(jdk|jre)-([0-9].+)\\.(aarch64|ppc64le|x86_64)\\.rpm$");
	private static final Pattern BIN_PATTERN = Pattern.compile(
			"^sapmachine-(jdk|jre)-([0-9].+)_(aix|linux|macos|osx|windows)-(x64|aarch64|ppc64|ppc64le|x64)-?(.*)_bin\\.(tar\\.gz|zip|msi|dmg)$");

	public SapMachine(ScraperConfig config) {
		super(config);
	}

	@Override
	protected String getGitHubOrg() {
		return "SAP";
	}

	@Override
	protected List<String> getGitHubRepos() {
		return List.of("SapMachine");
	}

	@Override
	protected void processRelease(List<JdkMetadata> allMetadata, JsonNode release) throws Exception {
		processReleaseAssets(allMetadata, release, this::processAsset);
	}

	@Override
	protected boolean shouldProcessAsset(JsonNode release, JsonNode asset) {
		String assetName = asset.get("name").asText();
		// Try RPM pattern first
		Matcher rpmMatcher = RPM_PATTERN.matcher(assetName);
		if (!rpmMatcher.matches()) {
			// Try BIN pattern
			Matcher binMatcher = BIN_PATTERN.matcher(assetName);
			if (!binMatcher.matches()) {
				if (!assetName.endsWith(".txt") && !assetName.contains("-symbols.")) {
					warn("Skipping " + assetName + " (does not match pattern)");
				}
				return false;
			}
		}
		return true;
	}

	private JdkMetadata processAsset(JsonNode release, JsonNode asset) throws Exception {
		String assetName = asset.get("name").asText();
		String downloadUrl = asset.get("browser_download_url").asText();

		String imageType = null;
		String version = null;
		String os = null;
		String arch = null;
		String features = "";
		String ext = null;

		// Try RPM pattern first
		Matcher rpmMatcher = RPM_PATTERN.matcher(assetName);
		if (rpmMatcher.matches()) {
			imageType = rpmMatcher.group(1);
			version = rpmMatcher.group(2);
			os = "linux";
			arch = rpmMatcher.group(3);
			ext = "rpm";
		} else {
			// Try BIN pattern
			Matcher binMatcher = BIN_PATTERN.matcher(assetName);
			if (binMatcher.matches()) {
				imageType = binMatcher.group(1);
				version = binMatcher.group(2);
				os = binMatcher.group(3);
				arch = binMatcher.group(4);
				features = binMatcher.group(5) != null ? binMatcher.group(5) : "";
				ext = binMatcher.group(6);
			}
		}

		// Download and compute hashes
		DownloadResult download = downloadFile(downloadUrl, assetName);

		// Create metadata using builder
		return JdkMetadata.builder()
				.vendor(VENDOR)
				.releaseType(determineReleaseType(version))
				.version(version)
				.javaVersion(version)
				.jvmImpl("hotspot")
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(ext)
				.imageType(imageType)
				.features(features.isEmpty() ? null : List.of(features.split(",")))
				.url(downloadUrl)
				.download(assetName, download)
				.build();
	}

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return VENDOR;
		}

		@Override
		public String vendor() {
			return VENDOR;
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new SapMachine(config);
		}
	}
}
