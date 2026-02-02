package dev.jbang.jdkdb.scraper.vendors;

import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for GraalVM Legacy releases (old releases from oracle/graal repo) */
public class GraalVmLegacy extends GraalVmBaseScraper {
	private static final String VENDOR = "graalvm";
	private static final String GITHUB_ORG = "oracle";
	private static final String GITHUB_REPO = "graal";

	// RC releases: graalvm-ce-1.0.0-rc1-linux-amd64.tar.gz
	private static final Pattern RC_PATTERN =
			Pattern.compile("^graalvm-ce-([\\d+.]{2,}-rc\\d+)-(linux|macos)-(amd64|aarch64)\\.(zip|tar\\.gz)$");

	// Regular releases: graalvm-ce-linux-aarch64-19.3.1.tar.gz
	private static final Pattern REGULAR_PATTERN =
			Pattern.compile("^graalvm-ce-(linux|darwin|windows)-(aarch64|amd64)-([\\d+.]{2,}[^.]*)\\.(zip|tar\\.gz)$");

	public GraalVmLegacy(ScraperConfig config) {
		super(config);
	}

	@Override
	protected String getGithubOrg() {
		return GITHUB_ORG;
	}

	@Override
	protected String getGithubRepo() {
		return GITHUB_REPO;
	}

	@Override
	protected boolean shouldProcessTag(String tagName) {
		return tagName.startsWith("vm-");
	}

	@Override
	protected boolean shouldProcessAsset(String assetName) {
		return assetName.startsWith("graalvm-ce");
	}

	@Override
	protected void processAsset(String tagName, String assetName, List<JdkMetadata> allMetadata) throws Exception {

		if (metadataExists(assetName)) {
			log("Skipping " + assetName + " (already exists)");
			return;
		}

		String releaseType;
		String os;
		String arch;
		String version;
		String ext;

		// Try RC pattern first
		Matcher rcMatcher = RC_PATTERN.matcher(assetName);
		if (rcMatcher.matches()) {
			releaseType = "ea";
			version = rcMatcher.group(1);
			os = rcMatcher.group(2);
			arch = rcMatcher.group(3);
			ext = rcMatcher.group(4);
		} else {
			// Try regular pattern
			Matcher regularMatcher = REGULAR_PATTERN.matcher(assetName);
			if (!regularMatcher.matches()) {
				log("Skipping " + assetName + " (does not match pattern)");
				return;
			}

			// Check if it's a dev build
			releaseType = assetName.contains("dev-b") ? "ea" : "ga";
			os = regularMatcher.group(1);
			arch = regularMatcher.group(2);
			version = regularMatcher.group(3);
			ext = regularMatcher.group(4);
		}

		String url = String.format(
				"https://github.com/%s/%s/releases/download/%s/%s", GITHUB_ORG, GITHUB_REPO, tagName, assetName);

		// Download and compute hashes
		DownloadResult download = downloadFile(url, assetName);

		// Create metadata
		JdkMetadata metadata = createMetadata(
				VENDOR,
				assetName,
				releaseType,
				version,
				"8", // Legacy GraalVM was based on Java 8
				os,
				arch,
				ext,
				url,
				download);

		saveMetadataFile(metadata);
		allMetadata.add(metadata);
		log("Processed " + assetName);
	}

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "graalvm-legacy";
		}

		@Override
		public String vendor() {
			return "graalvm";
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new GraalVmLegacy(config);
		}
	}
}
