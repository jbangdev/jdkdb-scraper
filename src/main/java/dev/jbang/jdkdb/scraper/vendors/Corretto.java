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

/** Scraper for Amazon Corretto releases */
public class Corretto extends GitHubReleaseScraper {
	private static final String VENDOR = "corretto";
	private static final String GITHUB_ORG = "corretto";

	// Pattern to extract download links from MD table in release body
	private static final Pattern DOWNLOAD_URL_PATTERN = Pattern.compile(
			"(https:\\/\\/corretto.aws\\/downloads\\/resources\\/([0-9.]+)\\/(amazon-corretto-([0-9.]+)-([a-z]+)-([a-z0-9]+)\\.(tar\\.gz|zip|pkg|msi)))\\)");

	public Corretto(ScraperConfig config) {
		super(config);
	}

	@Override
	protected String getGitHubOrg() {
		return GITHUB_ORG;
	}

	@Override
	protected Iterable<String> getGitHubRepos() throws Exception {
		// Use the helper method to fetch all corretto repositories
		return getReposFromOrg(getGitHubOrg(), "corretto", "^corretto-(\\d+|jdk)$");
	}

	@Override
	protected void processRelease(List<JdkMetadata> allMetadata, JsonNode release) throws Exception {
		String version = release.get("tag_name").asText();

		String body = release.get("body").asText("");

		// Parse download links from the HTML table in the release body
		Matcher matcher = DOWNLOAD_URL_PATTERN.matcher(body);

		while (matcher.find()) {
			String url = matcher.group(1);
			String filename = matcher.group(3);

			JdkMetadata metadata = processAsset(filename, url, version);
			if (metadata != null) {
				allMetadata.add(metadata);
			}
		}
	}

	private JdkMetadata processAsset(String filename, String url, String version) {
		// Parse filename to extract OS, architecture, extension, and image type
		// Examples:
		// amazon-corretto-8.482.08.1-linux-x64.tar.gz
		// amazon-corretto-8.482.08.1-windows-x64-jdk.zip
		// java-1.8.0-amazon-corretto-jdk_8.482.08-1_amd64.deb
		// java-1.8.0-amazon-corretto-devel-1.8.0_482.b08-1.x86_64.rpm
		String os = extractOs(filename);
		String arch = extractArch(filename);
		String ext = extractExtension(filename);

		if (os == null || arch == null || ext == null) {
			warn("Could not parse OS/arch/extension from filename: " + filename);
			return null;
		}

		if (metadataExists(filename)) {
			return skipped(filename);
		}

		String imageType = extractImageType(filename);

		// Build features list
		List<String> features = new ArrayList<>();
		if (filename.contains("-alpine-") || filename.contains("alpine-linux")) {
			features.add("musl");
		}

		// Create metadata using builder
		return JdkMetadata.create()
				.vendor(VENDOR)
				.releaseType("ga")
				.version(version)
				.javaVersion(version)
				.jvmImpl("hotspot")
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(ext)
				.imageType(imageType)
				.features(features)
				.url(url)
				.filename(filename);
	}

	private String extractOs(String filename) {
		if (filename.contains("-linux-") || filename.endsWith(".deb") || filename.endsWith(".rpm")) {
			if (filename.contains("-alpine-")) {
				return "alpine-linux";
			}
			return "linux";
		}
		if (filename.contains("-windows-")) {
			return "windows";
		}
		if (filename.contains("-macosx-") || filename.contains("-macos-")) {
			return "macosx";
		}
		return null;
	}

	private String extractArch(String filename) {
		if (filename.contains("-x64") || filename.contains("_amd64") || filename.contains(".x86_64")) {
			return "x64";
		}
		if (filename.contains("-x86") || filename.contains("_i686")) {
			return "x86";
		}
		if (filename.contains("-aarch64") || filename.contains("_arm64") || filename.contains(".aarch64")) {
			return "aarch64";
		}
		if (filename.contains("-armv7") || filename.contains("_armhf")) {
			return "armv7";
		}
		return null;
	}

	private String extractExtension(String filename) {
		if (filename.endsWith(".tar.gz")) {
			return "tar.gz";
		}
		if (filename.endsWith(".zip")) {
			return "zip";
		}
		if (filename.endsWith(".deb")) {
			return "deb";
		}
		if (filename.endsWith(".rpm")) {
			return "rpm";
		}
		if (filename.endsWith(".msi")) {
			return "msi";
		}
		if (filename.endsWith(".pkg")) {
			return "pkg";
		}
		return null;
	}

	private String extractImageType(String filename) {
		if (filename.contains("-jre") || filename.contains("_jre")) {
			return "jre";
		}
		// Default to jdk for all other cases
		return "jdk";
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
			return new Corretto(config);
		}
	}
}
