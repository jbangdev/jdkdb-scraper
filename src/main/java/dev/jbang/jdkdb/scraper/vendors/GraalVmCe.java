package dev.jbang.jdkdb.scraper.vendors;

import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.DownloadResult;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for GraalVM CE (legacy) releases */
public class GraalVmCe extends GraalVmBaseScraper {
	private static final String VENDOR = "graalvm";
	private static final String GITHUB_ORG = "graalvm";
	private static final String GITHUB_REPO = "graalvm-ce-builds";

	// Prior graalvm 23: graalvm-ce-java17-darwin-amd64-22.3.2.tar.gz
	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^graalvm-ce-(?:complete-)?java(\\d{1,2})-(linux|darwin|windows)-(aarch64|amd64)-([\\d+.]{2,})\\.(zip|tar\\.gz)$");

	public GraalVmCe(ScraperConfig config) {
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
		// Exclude Community releases (which start with "jdk")
		return !tagName.startsWith("jdk");
	}

	@Override
	protected boolean shouldProcessAsset(String assetName) {
		return assetName.startsWith("graalvm-ce") && (assetName.endsWith("tar.gz") || assetName.endsWith("zip"));
	}

	@Override
	protected JdkMetadata processAsset(String tagName, String assetName) throws Exception {
		Matcher matcher = FILENAME_PATTERN.matcher(assetName);
		if (!matcher.matches()) {
			log("Skipping " + assetName + " (does not match pattern)");
			return null;
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
		return JdkMetadata.builder()
				.vendor(VENDOR)
				.releaseType("ga")
				.version(version + "+java" + javaVersion)
				.javaVersion(javaVersion)
				.jvmImpl("graalvm")
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(ext)
				.imageType("jdk")
				.url(url)
				.download(assetName, download)
				.build();
	}

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "graalvm-ce";
		}

		@Override
		public String vendor() {
			return VENDOR;
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new GraalVmCe(config);
		}
	}
}
