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

/** Scraper for BellSoft Liberica releases */
public class Liberica extends GitHubReleaseScraper {
	private static final String VENDOR = "liberica";
	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^bellsoft-(jre|jdk)(.+?)-(?:ea-)?(linux|windows|macos|solaris)-(amd64|i386|i586|aarch64|arm64|ppc64le|arm32-vfp-hflt|x64|sparcv9|riscv64)-?(fx|lite|full|musl|musl-lite|crac|musl-crac|leyden|musl-leyden|lite-leyden|musl-lite-leyden)?\\.(apk|deb|rpm|msi|dmg|pkg|tar\\.gz|zip)$");

	public Liberica(ScraperConfig config) {
		super(config);
	}

	@Override
	protected String getGitHubOrg() {
		return "bell-sw";
	}

	@Override
	protected List<String> getGitHubRepos() {
		return List.of("Liberica");
	}

	@Override
	protected void processRelease(List<JdkMetadata> allMetadata, JsonNode release) throws Exception {
		processReleaseAssets(allMetadata, release, this::processAsset);
	}

	@Override
	protected boolean shouldProcessAsset(JsonNode release, JsonNode asset) {
		// Skip non-binary files
		String assetName = asset.get("name").asText();
		if (assetName.endsWith(".txt")
				|| assetName.endsWith(".bom")
				|| assetName.endsWith(".json")
				|| assetName.endsWith("-src.tar.gz")
				|| assetName.endsWith("-src-full.tar.gz")
				|| assetName.endsWith("-src-crac.tar.gz")
				|| assetName.endsWith("-src-leyden.tar.gz")
				|| assetName.contains("-full-nosign")) {
			return false;
		}
		String contentType = asset.get("content_type").asText("application/octet-stream");
		if (contentType.equals("text/plain")) {
			return false;
		}
		return true;
	}

	private JdkMetadata processAsset(JsonNode release, JsonNode asset) throws Exception {
		String tagName = release.get("tag_name").asText();
		boolean isPrerelease = release.get("prerelease").asBoolean();
		String filename = asset.get("name").asText();

		Matcher matcher = FILENAME_PATTERN.matcher(filename);
		if (!matcher.matches()) {
			log("Filename doesn't match pattern: " + filename);
			return null;
		}

		String imageType = matcher.group(1);
		String version = matcher.group(2);
		String os = matcher.group(3);
		String arch = matcher.group(4);
		String featuresStr = matcher.group(5) != null ? matcher.group(5) : "";
		String ext = matcher.group(6);

		String url = String.format("https://github.com/bell-sw/Liberica/releases/download/%s/%s", tagName, filename);

		// Determine release type
		String releaseType;
		if (isPrerelease) {
			releaseType = "ea";
		} else if (version.contains("ea")) {
			releaseType = "ea";
		} else {
			releaseType = "ga";
		}

		// Build features list
		List<String> features = buildFeatures(featuresStr);

		// Download and compute hashes
		DownloadResult download = downloadFile(url, filename);

		// Create metadata using builder
		return JdkMetadata.builder()
				.vendor(VENDOR)
				.releaseType(releaseType)
				.version(version)
				.javaVersion(version)
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

	private List<String> buildFeatures(String featuresStr) {
		List<String> features = new ArrayList<>();

		if (featuresStr.equals("lite")
				|| featuresStr.equals("musl-lite")
				|| featuresStr.equals("lite-leyden")
				|| featuresStr.equals("musl-lite-leyden")) {
			features.add("lite");
		}

		if (featuresStr.equals("full")) {
			features.add("libericafx");
			features.add("minimal-vm");
			features.add("javafx");
		}

		if (featuresStr.equals("fx")) {
			features.add("javafx");
		}

		if (featuresStr.equals("musl")
				|| featuresStr.equals("musl-lite")
				|| featuresStr.equals("musl-crac")
				|| featuresStr.equals("musl-leyden")
				|| featuresStr.equals("musl-lite-leyden")) {
			features.add("musl");
		}

		if (featuresStr.equals("crac") || featuresStr.equals("musl-crac")) {
			features.add("crac");
		}

		if (featuresStr.equals("leyden")
				|| featuresStr.equals("musl-leyden")
				|| featuresStr.equals("lite-leyden")
				|| featuresStr.equals("musl-lite-leyden")) {
			features.add("leyden");
		}

		return features;
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
			return new Liberica(config);
		}
	}
}
