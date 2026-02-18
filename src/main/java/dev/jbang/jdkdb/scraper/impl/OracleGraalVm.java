package dev.jbang.jdkdb.scraper.impl;

import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.BaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scraper for Oracle GraalVM GA releases. Downloads from Oracle archive pages and current release
 * page.
 */
public class OracleGraalVm extends BaseScraper {
	private static final String VENDOR = "oracle-graalvm";
	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^graalvm-jdk-([0-9+.]{2,})_(linux|macos|windows)-(x64|aarch64)_bin\\.(tar\\.gz|zip|msi|dmg|exe|deb|rpm)$");
	private static final Pattern LINK_PATTERN = Pattern.compile(
			"<a href=\"(https://download\\.oracle\\.com/graalvm/.+/archive/(graalvm-jdk-.+_(linux|macos|windows)-(x64|aarch64)_bin\\.(tar\\.gz|zip|msi|dmg|exe|deb|rpm)))\">");

	public OracleGraalVm(ScraperConfig config) {
		super(config);
	}

	@Override
	protected void scrape() throws Exception {
		log("Scraping Oracle GraalVM releases");

		// Scrape archive releases for various major versions
		int[] archiveVersions = {17, 20, 21, 22, 23, 24};
		for (int version : archiveVersions) {
			scrapeCurrentRelease(version);
			scrapeArchive(version);
		}
	}

	private void scrapeArchive(int majorVersion) throws Exception {
		String archiveUrl = String.format(
				"https://www.oracle.com/java/technologies/javase/graalvm-jdk%d-archive-downloads.html", majorVersion);

		log("Scraping Oracle GraalVM " + majorVersion + " archive from " + archiveUrl);

		// Then scrape archive
		String html;
		try {
			html = httpUtils.downloadString(archiveUrl);
		} catch (Exception e) {
			fail("Could not download archive page for version " + majorVersion, e);
			return;
		}

		// Find all download links using regex
		Matcher matcher = LINK_PATTERN.matcher(html);
		while (matcher.find()) {
			String downloadUrl = matcher.group(1);
			String filename = matcher.group(2);

			JdkMetadata jdkMetadata = processAsset(filename, downloadUrl);
			if (jdkMetadata != null) {
				process(jdkMetadata);
			}
		}
	}

	private void scrapeCurrentRelease(int majorVersion) throws Exception {
		// Try to find current release version from downloads page
		String html;
		try {
			String downloadsUrl = "https://www.oracle.com/java/technologies/downloads/";
			html = httpUtils.downloadString(downloadsUrl);
		} catch (Exception e) {
			fail("Could not download downloads page to find current release version", e);
			return;
		}

		Pattern versionPattern = Pattern.compile(
				String.format("<h3 id=\"graalvmjava%d\">GraalVM for JDK ([0-9.+]+) downloads</h3>", majorVersion));
		Matcher versionMatcher = versionPattern.matcher(html);

		if (versionMatcher.find()) {
			String currentVersion = versionMatcher.group(1);
			log("Found current GraalVM " + majorVersion + " release: " + currentVersion);

			// Generate current release filenames (using script-friendly URLs)
			String[][] platforms = {
				{"linux", "aarch64", "tar.gz"},
				{"linux", "x64", "tar.gz"},
				{"macos", "aarch64", "tar.gz"},
				{"macos", "x64", "tar.gz"},
				{"windows", "x64", "zip"}
			};

			for (String[] platform : platforms) {
				String os = platform[0];
				String arch = platform[1];
				String ext = platform[2];
				String filename = String.format("graalvm-jdk-%s_%s-%s_bin.%s", currentVersion, os, arch, ext);
				String downloadUrl =
						String.format("https://download.oracle.com/graalvm/%d/archive/%s", majorVersion, filename);

				JdkMetadata jdkMetadata = processAsset(filename, downloadUrl);
				if (jdkMetadata != null) {
					process(jdkMetadata);
				}
			}
		}
	}

	private JdkMetadata processAsset(String filename, String downloadUrl) {
		Matcher matcher = FILENAME_PATTERN.matcher(filename);
		if (!matcher.matches()) {
			warn("Skipping " + filename + " (does not match pattern)");
			return null;
		}

		if (metadataExists(filename)) {
			return skipped(filename);
		}

		String version = matcher.group(1);
		String os = matcher.group(2);
		String arch = matcher.group(3);
		String extension = matcher.group(4);

		// Create metadata
		return JdkMetadata.create()
				.vendor(VENDOR)
				.releaseType("ga")
				.version(version)
				.javaVersion(version)
				.jvmImpl("graalvm")
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(extension)
				.imageType("jdk")
				.url(downloadUrl)
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
			return new OracleGraalVm(config);
		}
	}
}
