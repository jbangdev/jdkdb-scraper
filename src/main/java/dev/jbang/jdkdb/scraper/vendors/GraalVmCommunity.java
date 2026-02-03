package dev.jbang.jdkdb.scraper.vendors;

import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.DownloadResult;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for GraalVM Community releases (starting from GraalVM 23) */
public class GraalVmCommunity extends GraalVmBaseScraper {
	private static final String VENDOR = "graalvm-community";
	private static final String GITHUB_ORG = "graalvm";
	private static final String GITHUB_REPO = "graalvm-ce-builds";

	// Starting from graalvm 23: graalvm-community-jdk-17.0.7_macos-aarch64_bin.tar.gz
	// or: graalvm-community-jdk-17.0.7_linux-x64_bin.tar.gz
	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^graalvm-community-jdk-(\\d{1,2}\\.\\d{1}\\.\\d{1,3})_(linux|macos|windows)-(aarch64|x64)_bin\\.(zip|tar\\.gz)$");

	public GraalVmCommunity(ScraperConfig config) {
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
		return tagName.startsWith("jdk");
	}

	@Override
	protected boolean shouldProcessAsset(String assetName) {
		return assetName.startsWith("graalvm-community") && (assetName.endsWith("tar.gz") || assetName.endsWith("zip"));
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
		String ext = matcher.group(4);

		String url = String.format(
				"https://github.com/%s/%s/releases/download/%s/%s", GITHUB_ORG, GITHUB_REPO, tagName, assetName);

		// Download and compute hashes
		DownloadResult download = downloadFile(url, assetName);

		// Create metadata
		return JdkMetadata.builder()
				.vendor(VENDOR)
				.releaseType("ga")
				.version(javaVersion)
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
			return "graalvm-community";
		}

		@Override
		public String vendor() {
			return VENDOR;
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new GraalVmCommunity(config);
		}
	}
}
