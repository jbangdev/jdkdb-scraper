package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.BaseScraper;
import dev.jbang.jdkdb.scraper.InterruptedProgressException;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import dev.jbang.jdkdb.scraper.TooManyFailuresException;
import java.util.ArrayList;
import java.util.List;
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
	protected List<JdkMetadata> scrape() throws Exception {
		log("Scraping Oracle JDK releases");

		List<JdkMetadata> allMetadata = new ArrayList<>();

		try {
			// First, scrape latest releases from Oracle Cloud API
			allMetadata.addAll(scrapeLatestReleases());

			// Then scrape archive releases for various major versions
			int[] archiveVersions = {17, 18, 19, 20, 21, 22, 23, 24};
			for (int version : archiveVersions) {
				allMetadata.addAll(scrapeArchive(version));
			}
		} catch (InterruptedProgressException e) {
			log("Reached progress limit, aborting");
		}

		return allMetadata;
	}

	private List<JdkMetadata> scrapeLatestReleases() throws Exception {
		List<JdkMetadata> metadata = new ArrayList<>();
		String versionsUrl = "https://java.oraclecloud.com/javaVersions";

		log("Fetching Oracle JDK versions from " + versionsUrl);
		String versionsJson = httpUtils.downloadString(versionsUrl);
		JsonNode versionsNode = readJson(versionsJson);

		for (JsonNode item : versionsNode.get("items")) {
			String latestVersion = item.get("latestReleaseVersion").asText();
			String releaseUrl = "https://java.oraclecloud.com/javaReleases/" + latestVersion;

			log("Fetching release info for version " + latestVersion);
			String releaseJson = httpUtils.downloadString(releaseUrl);
			JsonNode releaseNode = readJson(releaseJson);

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

					if (metadataExists(filename)) {
						continue;
					}

					JdkMetadata jdkMetadata = parseFilename(filename, downloadUrl);
					if (jdkMetadata != null) {
						metadata.add(jdkMetadata);
					}
				}
			}
		}

		return metadata;
	}

	private List<JdkMetadata> scrapeArchive(int majorVersion) throws Exception {
		List<JdkMetadata> metadata = new ArrayList<>();
		String archiveUrl = String.format(
				"https://www.oracle.com/java/technologies/javase/jdk%d-archive-downloads.html", majorVersion);

		log("Scraping Oracle JDK " + majorVersion + " archive from " + archiveUrl);

		String html = httpUtils.downloadString(archiveUrl);

		// Find all download links using regex
		Matcher matcher = LINK_PATTERN.matcher(html);
		while (matcher.find()) {
			String downloadUrl = matcher.group(1);
			String filename = matcher.group(2);

			if (metadataExists(filename)) {
				continue;
			}

			try {
				JdkMetadata jdkMetadata = parseFilename(filename, downloadUrl);
				if (jdkMetadata != null) {
					metadata.add(jdkMetadata);
				}
			} catch (InterruptedProgressException | TooManyFailuresException e) {
				throw e;
			} catch (Exception e) {
				fail(filename, e);
			}
		}

		return metadata;
	}

	private JdkMetadata parseFilename(String filename, String downloadUrl) throws Exception {
		Matcher matcher = FILENAME_PATTERN.matcher(filename);
		if (!matcher.matches()) {
			log("Filename does not match pattern: " + filename);
			return null;
		}

		String version = matcher.group(1);
		String os = matcher.group(2);
		String arch = matcher.group(3);
		String extension = matcher.group(4);

		// Download and calculate checksums
		DownloadResult download = downloadFile(downloadUrl, filename);

		JdkMetadata metadata = new JdkMetadata();
		metadata.setVendor(VENDOR);
		metadata.setFilename(filename);
		metadata.setReleaseType("ga");
		metadata.setVersion(version);
		metadata.setJavaVersion(version);
		metadata.setJvmImpl("hotspot");
		metadata.setOs(normalizeOs(os));
		metadata.setArchitecture(normalizeArch(arch));
		metadata.setFileType(extension);
		metadata.setImageType("jdk");
		metadata.setFeatures(new ArrayList<>());
		metadata.setUrl(downloadUrl);
		metadata.setMd5(download.md5());
		metadata.setMd5File(filename + ".md5");
		metadata.setSha1(download.sha1());
		metadata.setSha1File(filename + ".sha1");
		metadata.setSha256(download.sha256());
		metadata.setSha256File(filename + ".sha256");
		metadata.setSha512(download.sha512());
		metadata.setSha512File(filename + ".sha512");
		metadata.setSize(download.size());

		saveMetadataFile(metadata);
		success(filename);

		return metadata;
	}

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "oracle";
		}

		@Override
		public String vendor() {
			return "oracle";
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new Oracle(config);
		}
	}
}
