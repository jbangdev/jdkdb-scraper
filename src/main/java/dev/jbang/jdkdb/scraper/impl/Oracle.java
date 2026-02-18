package dev.jbang.jdkdb.scraper.impl;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.BaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scraper for Oracle JDK releases. Downloads from Oracle Cloud API for latest releases and archive
 * pages for historical versions.
 */
public class Oracle extends BaseScraper {
	private static final String VENDOR = "oracle";
	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^jdk-([0-9+.]{2,})_(linux|macos|windows)-(x64|aarch64)_bin\\.(tar\\.gz|zip|msi|dmg|exe|deb|rpm)$");
	private static final Pattern LINK_PATTERN = Pattern.compile(
			"<a href=\"(https://download\\.oracle\\.com/java/.+/archive/(jdk-.+_(linux|macos|windows)-(x64|aarch64)_bin\\.(tar\\.gz|zip|msi|dmg|exe|deb|rpm)))\">");

	public Oracle(ScraperConfig config) {
		super(config);
	}

	@Override
	protected void scrape() throws Exception {
		log("Scraping Oracle JDK releases");

		// First, scrape latest releases from Oracle Cloud API
		scrapeLatestReleases();

		// Then scrape archive releases for various major versions
		int[] archiveVersions = {17, 18, 19, 20, 21, 22, 23, 24};
		for (int version : archiveVersions) {
			scrapeArchive(version);
		}
	}

	private void scrapeLatestReleases() throws Exception {
		JsonNode versionsNode;
		try {
			String versionsUrl = "https://java.oraclecloud.com/javaVersions";
			log("Fetching Oracle JDK versions from " + versionsUrl);
			String versionsJson = httpUtils.downloadString(versionsUrl);
			versionsNode = readJson(versionsJson);
		} catch (Exception e) {
			fail("Could not fetch Oracle JDK versions", e);
			return;
		}

		for (JsonNode item : versionsNode.get("items")) {
			String latestVersion = item.get("latestReleaseVersion").asText();
			String releaseUrl = "https://java.oraclecloud.com/javaReleases/" + latestVersion;

			log("Fetching release info for version " + latestVersion);
			JsonNode releaseNode;
			try {
				String releaseJson = httpUtils.downloadString(releaseUrl);
				releaseNode = readJson(releaseJson);
			} catch (Exception e) {
				fail("Could not fetch release info for version " + latestVersion, e);
				continue;
			}

			// Skip OTN licensed versions (Oracle Technology Network - requires acceptance)
			String licenseType =
					releaseNode.path("licenseDetails").path("licenseType").asText();
			if ("OTN".equals(licenseType)) {
				log("Skipping OTN licensed version " + latestVersion);
				continue;
			}

			JsonNode artifacts = releaseNode.get("artifacts");
			if (artifacts != null) {
				for (JsonNode artifact : artifacts) {
					String downloadUrl = artifact.get("downloadUrl").asText();
					String filename = downloadUrl.substring(downloadUrl.lastIndexOf('/') + 1);

					JdkMetadata jdkMetadata = processAsset(filename, downloadUrl);
					if (jdkMetadata != null) {
						process(jdkMetadata);
					}
				}
			}
		}
	}

	private void scrapeArchive(int majorVersion) throws Exception {
		String archiveUrl = String.format(
				"https://www.oracle.com/java/technologies/javase/jdk%d-archive-downloads.html", majorVersion);

		log("Scraping Oracle JDK " + majorVersion + " archive from " + archiveUrl);

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
				.jvmImpl("hotspot")
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
			return new Oracle(config);
		}
	}
}
