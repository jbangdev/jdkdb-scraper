package dev.jbang.jdkdb.scraper.impl;

import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.BaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import dev.jbang.jdkdb.util.HtmlUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for OpenLogic OpenJDK releases */
public class OpenLogic extends BaseScraper {
	private static final String VENDOR = "openlogic";
	private static final String BASE_URL = "https://www.openlogic.com/openjdk-downloads";
	private static final String DOWNLOAD_PREFIX = "https://builds.openlogic.com/";
	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^openlogic-openjdk-(?:(jre|jdk)-)?([0-9]+(?:u[0-9]+)?(?:\\.[0-9.]+)?(?:[-+]b?[0-9]+)?)-(linux|windows|mac)-(aarch64|x64|x32|arm32)(?:-deb|-el)?\\.(tar\\.gz|zip|msi|dmg|deb|rpm|pkg)$");
	private static final Random random = new Random();

	public OpenLogic(ScraperConfig config) {
		super(config);
	}

	@Override
	protected void scrape() throws Exception {
		int page = 0;
		boolean hasMore = true;

		while (hasMore) {
			String pageUrl = BASE_URL + "?page=" + page;
			log("Fetching page " + page + " from " + pageUrl);

			String html;
			try {
				html = httpUtils.downloadString(pageUrl);
			} catch (Exception e) {
				fail("Failed to fetch page " + page, e);
				break;
			}

			// Extract all hrefs from the page
			List<String> hrefs = HtmlUtils.extractHrefs(html);

			// Find download links
			List<String> downloadLinks = new ArrayList<>();
			for (String href : hrefs) {
				if (href.startsWith(DOWNLOAD_PREFIX)) {
					downloadLinks.add(href);
				}
			}

			// Check stop criteria:
			// 1. No download links found on this page
			if (downloadLinks.isEmpty()) {
				warn("No download links found on page " + page + ", stopping");
				hasMore = false;
				break;
			}

			// 2. Check if "last" page link is missing (indicating we're on the last page)
			boolean hasLastPageLink = false;
			for (String href : hrefs) {
				if (href.startsWith("?page=")) {
					// Check if this anchor tag has "last" in its class
					// We need to check the HTML more carefully
					Pattern lastLinkPattern = Pattern.compile(
							"<a[^>]*class=\"[^\"]*last[^\"]*\"[^>]*href=\"\\?page=[^\"]*\"", Pattern.CASE_INSENSITIVE);
					if (lastLinkPattern.matcher(html).find()) {
						hasLastPageLink = true;
						break;
					}
				}
			}

			log("Found " + downloadLinks.size() + " download links on page " + page);

			// Process download links
			for (String url : downloadLinks) {
				String filename = HtmlUtils.extractFilename(url);

				JdkMetadata metadata = processAsset(filename, url);
				if (metadata != null) {
					process(metadata);
				}
			}

			// If there's no "last" page link, we're on the last page
			if (!hasLastPageLink) {
				log("No 'last' page link found, stopping");
				hasMore = false;
			} else {
				page++;
				// Add random delay between 2-6 seconds before next page
				if (hasMore) {
					int delaySeconds = 2 + random.nextInt(5); // 2 to 6 seconds
					log("Waiting " + delaySeconds + " seconds before fetching next page...");
					Thread.sleep(delaySeconds * 1000L);
				}
			}
		}
	}

	private JdkMetadata processAsset(String filename, String url) {
		Matcher matcher = FILENAME_PATTERN.matcher(filename);
		if (!matcher.matches()) {
			warn("Skipping " + filename + " (does not match pattern)");
			return null;
		}

		if (metadataExists(filename)) {
			return skipped(filename);
		}

		String imageType = matcher.group(1);
		if (imageType == null) {
			// If not specified, assume JDK
			imageType = "jdk";
		} else {
			// Remove trailing dash from capture group
			imageType = imageType.replace("-", "");
		}

		String version = matcher.group(2);
		String os = matcher.group(3);
		String arch = matcher.group(4);
		String ext = matcher.group(5);

		// All OpenLogic releases are GA
		String releaseType = "ga";

		// Create metadata
		return JdkMetadata.create()
				.vendor(VENDOR)
				.releaseType(releaseType)
				.version(version)
				.javaVersion(version)
				.jvmImpl("hotspot")
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(normalizeFileType(ext))
				.imageType(imageType)
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
		public When when() {
			return When.ALWAYS;
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new OpenLogic(config);
		}
	}
}
