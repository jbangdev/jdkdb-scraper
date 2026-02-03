package dev.jbang.jdkdb.scraper.vendors;

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
	protected List<JdkMetadata> scrape() throws Exception {
		log("Scraping Oracle GraalVM releases");

		List<JdkMetadata> allMetadata = new ArrayList<>();

		try {
			// Scrape archive releases for various major versions
			int[] archiveVersions = {17, 20, 21, 22, 23, 24};
			for (int version : archiveVersions) {
				allMetadata.addAll(scrapeArchive(version));
			}
		} catch (InterruptedProgressException e) {
			log("Reached progress limit, aborting");
		}

		return allMetadata;
	}

	private List<JdkMetadata> scrapeArchive(int majorVersion) throws Exception {
		List<JdkMetadata> metadata = new ArrayList<>();
		String archiveUrl = String.format(
				"https://www.oracle.com/java/technologies/javase/graalvm-jdk%d-archive-downloads.html", majorVersion);

		log("Scraping Oracle GraalVM " + majorVersion + " archive from " + archiveUrl);

		// First try to get current releases
		try {
			metadata.addAll(scrapeCurrentRelease(majorVersion));
		} catch (Exception e) {
			// Current release not found, continue with archive
		}

		// Then scrape archive
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

	private List<JdkMetadata> scrapeCurrentRelease(int majorVersion) throws Exception {
		List<JdkMetadata> metadata = new ArrayList<>();

		// Try to find current release version from downloads page
		String downloadsUrl = "https://www.oracle.com/java/technologies/downloads/";
		String html = httpUtils.downloadString(downloadsUrl);

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

				if (metadataExists(filename)) {
					continue;
				}

				try {
					JdkMetadata jdkMetadata = parseFilename(filename, downloadUrl);
					if (jdkMetadata != null) {
						saveMetadataFile(jdkMetadata);
						metadata.add(jdkMetadata);
						success(filename);
					}
				} catch (Exception e) {
					fail(filename, e);
				}
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
		metadata.setJvmImpl("graalvm");
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

		return metadata;
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
