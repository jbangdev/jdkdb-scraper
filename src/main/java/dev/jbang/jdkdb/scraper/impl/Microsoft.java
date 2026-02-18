package dev.jbang.jdkdb.scraper.impl;

import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.BaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.ArrayList;
import java.util.regex.Pattern;

/** Scraper for Microsoft OpenJDK builds */
public class Microsoft extends BaseScraper {
	private static final String VENDOR = "microsoft";
	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"microsoft-jdk-([0-9.]+)-(linux|macos|macOS|windows)-(x64|aarch64)\\.(tar\\.gz|zip|msi|dmg|pkg)$");

	public Microsoft(ScraperConfig config) {
		super(config);
	}

	@Override
	protected void scrape() throws Exception {
		// Download the Microsoft JDK download page
		String html;
		try {
			var indexUrl = "https://docs.microsoft.com/en-us/java/openjdk/download";
			log("Fetching index from " + indexUrl);
			html = httpUtils.downloadString(indexUrl);
		} catch (Exception e) {
			fail("Failed to fetch index page", e);
			throw e;
		}

		// Extract download links using regex
		var linkPattern = Pattern.compile(
				"<a href=\"https://aka\\.ms/download-jdk/(microsoft-jdk-.+-(linux|macos|macOS|windows)-(x64|aarch64)\\.(tar\\.gz|zip|msi|dmg|pkg))\"");
		var matcher = linkPattern.matcher(html);

		var files = new ArrayList<String>();
		while (matcher.find()) {
			files.add(matcher.group(1));
		}

		log("Found " + files.size() + " files to process");

		for (var filename : files) {
			var metadata = processAsset(filename);
			if (metadata != null) {
				process(metadata);
			}
		}
	}

	private JdkMetadata processAsset(String filename) {
		var matcher = FILENAME_PATTERN.matcher(filename);
		if (!matcher.matches()) {
			if (!filename.contains("-debugsymbols-")) {
				warn("Skipping " + filename + " (does not match pattern)");
			}
			return null;
		}

		if (metadataExists(filename)) {
			return skipped(filename);
		}

		var version = matcher.group(1);
		var os = matcher.group(2);
		var arch = matcher.group(3);
		var extension = matcher.group(4);

		var url = "https://aka.ms/download-jdk/" + filename;

		// Determine release type (aarch64 is EA for Microsoft)
		var releaseType = arch.equals("aarch64") ? "ea" : "ga";

		// Create metadata
		return JdkMetadata.create()
				.vendor(VENDOR)
				.releaseType(normalizeReleaseType(releaseType))
				.version(version)
				.javaVersion(version)
				.jvmImpl("hotspot")
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(extension)
				.imageType("jdk")
				.url(url)
				.filename(filename);
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
			return new Microsoft(config);
		}
	}
}
