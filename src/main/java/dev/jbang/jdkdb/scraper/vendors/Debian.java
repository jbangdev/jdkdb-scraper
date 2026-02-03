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
			"(openjdk-)([0-9]{1,2})-(jre|jdk)_(([1-9]\\d*)((u(\\d+))|(\\.?(\\d+)?\\.?(\\d+)?\\.?(\\d+)?\\.?(\\d+)?\\.(\\d+)))?((_|b)(\\d+))?((-|\\+|\\.)([a-zA-Z0-9\\-\\+]+)(\\.[0-9]+)?)?)_(.*)(\\.deb)");

	// LTS versions to scrape
	private static final String[] LTS_VERSIONS = {"8", "11", "11-jre-dcevm", "17", "21"};

	public Debian(ScraperConfig config) {
		super(config);
	}

	@Override
	protected List<JdkMetadata> scrape() throws Exception {
		List<JdkMetadata> allMetadata = new ArrayList<>();

		try {
			// Process each LTS version
			for (String majorVersion : LTS_VERSIONS) {
				String cdnUrl = CDN_URL + "openjdk-" + majorVersion + "/";
				log("Processing OpenJDK " + majorVersion + " from " + cdnUrl);

				try {
					allMetadata.addAll(scrapeVersionDirectory(cdnUrl));
				} catch (InterruptedProgressException e) {
					throw e;
				} catch (Exception e) {
					log("Failed to scrape " + cdnUrl + ": " + e.getMessage());
				}
			}
		} catch (InterruptedProgressException e) {
			log("Reached progress limit, aborting");
		}

		return allMetadata;
	}

	private List<JdkMetadata> scrapeVersionDirectory(String cdnUrl) throws Exception {
		List<JdkMetadata> metadata = new ArrayList<>();

		// Download the directory listing
		String html = httpUtils.downloadString(cdnUrl);
		if (html.isEmpty()) {
			log("Empty response from " + cdnUrl);
			return metadata;
		}

		// Extract all hrefs from the HTML
		List<String> hrefs = HtmlUtils.extractHrefs(html);

		for (String href : hrefs) {
			String filename = HtmlUtils.extractFilename(href);

			// Only process .deb files
			if (!filename.endsWith(".deb")) {
				continue;
			}

			// Skip source packages
			if (filename.contains("-src") || filename.contains("-source")) {
				continue;
			}

			try {
				JdkMetadata meta = processDebianPackage(filename, cdnUrl);
				if (meta != null) {
					saveMetadataFile(meta);
					metadata.add(meta);
					success(filename);
				}
			} catch (InterruptedProgressException | TooManyFailuresException e) {
				throw e;
			} catch (Exception e) {
				fail(filename, e);
			}
		}

		return metadata;
	}

	private JdkMetadata processDebianPackage(String filename, String cdnUrl) throws Exception {
		if (metadataExists(filename)) {
			log("Skipping " + filename + " (already exists)");
			return null;
		}

		Matcher matcher = DEB_PKG_PATTERN.matcher(filename);
		if (!matcher.matches()) {
			fine("Filename doesn't match pattern: " + filename);
			return null;
		}

		// Extract information from the filename
		String imageType = matcher.group(3); // jre or jdk
		String version = matcher.group(4); // Full version string
		String archString = matcher.group(22); // Architecture string

		// Build the download URL
		String url = cdnUrl + filename;

		// Check if URL exists
		if (!httpUtils.urlExists(url)) {
			fine("URL does not exist: " + url);
			return null;
		}

		// Normalize architecture
		String architecture = normalizeDebianArch(archString);
		if (architecture.startsWith("unknown-arch-")) {
			fine("Unknown architecture: " + archString);
			return null;
		}

		// Build features list
		List<String> features = new ArrayList<>();

		// Add specific features based on architecture
		if (archString.equals("armel")) {
			features.add("soft-float");
		} else if (archString.equals("armhf")) {
			features.add("hard-float");
		}

		// Download and compute hashes
		DownloadResult download = downloadFile(url, filename);

		// Create metadata
		JdkMetadata metadata = new JdkMetadata();
		metadata.setVendor(VENDOR);
		metadata.setFilename(filename);
		metadata.setReleaseType("ga"); // Debian only packages GA releases
		metadata.setVersion(version);
		metadata.setJavaVersion(version);
		metadata.setJvmImpl("hotspot"); // Debian packages HotSpot
		metadata.setOs("linux"); // Debian is Linux-only
		metadata.setArchitecture(architecture);
		metadata.setFileType("deb");
		metadata.setImageType(imageType);
		metadata.setFeatures(features);
		metadata.setUrl(url);
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

	/** Normalize Debian architecture names to our standard names */
	private String normalizeDebianArch(String arch) {
		if (arch == null || arch.isEmpty()) {
			return "unknown";
		}

		return switch (arch.toLowerCase()) {
			case "amd64" -> "x86_64";
			case "i386", "i586", "i686" -> "i686";
			case "arm64", "aarch64" -> "aarch64";
			case "arm", "armv7" -> "arm32";
			case "armhf" -> "arm32-vfp-hflt";
			case "armel" -> "arm32";
			case "ppc" -> "ppc32";
			case "ppc64" -> "ppc64";
			case "ppc64le", "ppc64el" -> "ppc64le";
			case "s390x" -> "s390x";
			case "mips" -> "mips";
			case "mipsel" -> "mipsel";
			default -> "unknown-arch-" + arch;
		};
	}

	/** Discovery for creating Debian scraper instances */
	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "debian";
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
