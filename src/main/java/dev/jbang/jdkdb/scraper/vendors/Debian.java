package dev.jbang.jdkdb.scraper.vendors;

import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.BaseScraper;
import dev.jbang.jdkdb.scraper.InterruptedProgressException;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import dev.jbang.jdkdb.scraper.TooManyFailuresException;
import dev.jbang.jdkdb.util.HtmlUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scraper for Debian OpenJDK packages
 *
 * <p>Scrapes OpenJDK packages from Debian's FTP repository. The packages are organized by major
 * version in directories like openjdk-8, openjdk-11, openjdk-17, etc.
 *
 * <p>The scraper only processes LTS versions as Debian primarily packages those.
 */
public class Debian extends BaseScraper {
	private static final String VENDOR = "debian";
	private static final String CDN_URL = "http://ftp.debian.org/debian/pool/main/o/";

	// Pattern to match Debian package filenames
	// Example: openjdk-17-jdk_17.0.8+7-1~deb12u1_amd64.deb
	private static final Pattern DEB_PKG_PATTERN = Pattern.compile(
			"^openjdk-(?:[0-9]{1,2})(?:-jvmci)?-(jre|jdk)(?:-(dcevm|headless|zero))?_([^_~-]+)([^_]*)_(.*)\\.deb$");

	// LTS versions to scrape
	private static final String[] LTS_VERSIONS = {"8", "11", "11-jre-dcevm", "17", "21", "25", "26"};

	public Debian(ScraperConfig config) {
		super(config);
	}

	@Override
	protected void scrape() throws Exception {
		// Process each LTS version
		for (String majorVersion : LTS_VERSIONS) {
			String cdnUrl = CDN_URL + "openjdk-" + majorVersion + "/";
			log("Processing OpenJDK " + majorVersion + " from " + cdnUrl);

			try {
				scrapeVersionDirectory(cdnUrl);
			} catch (InterruptedProgressException | TooManyFailuresException e) {
				throw e;
			} catch (Exception e) {
				warn("Failed to scrape " + cdnUrl + ": " + e.getMessage());
			}
		}
	}

	private void scrapeVersionDirectory(String cdnUrl) throws Exception {
		// Download the directory listing
		String html;
		try {
			html = httpUtils.downloadString(cdnUrl);
			if (html.isEmpty()) {
				fail("Empty response from " + cdnUrl, null);
				return;
			}
		} catch (Exception e) {
			fail("Could not download directory listing", e);
			return;
		}

		// Extract all hrefs from the HTML
		List<String> hrefs = HtmlUtils.extractHrefs(html);

		for (String href : hrefs) {
			String filename = HtmlUtils.extractFilename(href);
			JdkMetadata metadata = processAsset(filename, cdnUrl);
			if (metadata != null) {
				process(metadata);
			}
		}
	}

	private JdkMetadata processAsset(String filename, String cdnUrl) {
		// Silently ignore anything that is not a .deb file
		if (filename == null || !filename.endsWith(".deb")) {
			return null;
		}
		Matcher matcher = DEB_PKG_PATTERN.matcher(filename);
		if (!matcher.matches()) {
			if (filename.startsWith("openjdk-")
					&& !filename.contains("-dbg_")
					&& !filename.contains("-demo_")
					&& !filename.contains("-doc_")
					&& !filename.contains("-source_")
					&& !filename.contains("-testsupport_")) {
				warn("Skipping " + filename + " (does not match pattern)");
			}
			return null;
		}

		if (metadataExists(filename)) {
			return skipped(filename);
		}

		// Extract information from the filename
		String imageType = matcher.group(1); // jre or jdk
		String pkgfeat = matcher.group(2); // headless or zero (if present)
		String version = matcher.group(3); // Java version string
		String extraVersion = matcher.group(4); // Extra version string
		String architecture = matcher.group(5); // Architecture string

		// Build the download URL
		String url = cdnUrl + filename;

		// Build features list
		List<String> features = new ArrayList<>();

		// Add package feature if present
		if (pkgfeat != null) {
			features.add(pkgfeat);
		}
		if (filename.contains("jvmci")) {
			features.add("jvmci");
		}
		// Add specific features based on architecture
		if (architecture.equals("armel")) {
			features.add("soft-float");
		} else if (architecture.equals("armhf")) {
			features.add("hard-float");
		}

		return JdkMetadata.create()
				.vendor(VENDOR)
				.releaseType("ga") // Debian only packages GA releases
				.version(version + extraVersion) // Combine version and extra version for full version string
				.javaVersion(version)
				.jvmImpl("hotspot") // Debian packages HotSpot
				.os("linux") // Debian is Linux-only
				.arch(normalizeArch(architecture))
				.fileType("deb")
				.imageType(imageType)
				.features(features)
				.url(url)
				.filename(filename);
	}

	/** Discovery for creating Debian scraper instances */
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
			return new Debian(config);
		}
	}
}
