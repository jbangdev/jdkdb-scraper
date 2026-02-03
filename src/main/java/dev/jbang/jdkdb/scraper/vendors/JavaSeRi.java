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

/** Scraper for Java SE Reference Implementation releases */
public class JavaSeRi extends BaseScraper {
	private static final String VENDOR = "java-se-ri";
	private static final String BASE_URL = "https://jdk.java.net/java-se-ri/";

	private static final String[] URL_VERSIONS = {
		"7", "8-MR3", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24",
		"25"
	};

	private static final Pattern URL_PATTERN =
			Pattern.compile("href=\"(https://download\\.java\\.net/.*/openjdk-[^/]*\\.(tar\\.gz|zip))\"");
	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^openjdk-([0-9ub-]{1,}[^_]*)[-_](linux|osx|windows)-(aarch64|x64-musl|x64|i586).*\\.(tar\\.gz|zip)$");

	public JavaSeRi(ScraperConfig config) {
		super(config);
	}

	@Override
	protected List<JdkMetadata> scrape() throws Exception {
		List<JdkMetadata> allMetadata = new ArrayList<>();
		List<String> urls = new ArrayList<>();

		// Fetch all URLs from index pages
		for (String urlVersion : URL_VERSIONS) {
			log("Fetching index for version: " + urlVersion);

			String indexHtml = httpUtils.downloadString(BASE_URL + urlVersion);
			Matcher urlMatcher = URL_PATTERN.matcher(indexHtml);

			while (urlMatcher.find()) {
				String url = urlMatcher.group(1);
				// Skip source files
				if (!url.contains("-src") && !url.contains("_src")) {
					urls.add(url);
				}
			}
		}

		log("Found " + urls.size() + " files to process");

		try {
			// Process each URL
			for (String url : urls) {
				String filename = url.substring(url.lastIndexOf('/') + 1);

				try {
					JdkMetadata metadata = processFile(url, filename, allMetadata);
					if (metadata != null) {
						saveMetadataFile(metadata);
						allMetadata.add(metadata);
						success(filename);
					}
				} catch (InterruptedProgressException | TooManyFailuresException e) {
					throw e;
				} catch (Exception e) {
					fail(filename, e);
				}
			}
		} catch (InterruptedProgressException e) {
			log("Reached progress limit, aborting");
		}

		return allMetadata;
	}

	private JdkMetadata processFile(String url, String filename, List<JdkMetadata> allMetadata) throws Exception {

		if (metadataExists(filename)) {
			log("Skipping " + filename + " (already exists)");
			return null;
		}

		Matcher matcher = FILENAME_PATTERN.matcher(filename);
		if (!matcher.matches()) {
			log("Skipping " + filename + " (does not match pattern)");
			return null;
		}

		String version = matcher.group(1);
		String os = matcher.group(2);
		String arch = matcher.group(3);
		String ext = matcher.group(4);

		// Determine release type
		String releaseType = version.contains("-ea") ? "ea" : "ga";

		// Handle musl feature
		List<String> features = new ArrayList<>();
		if (arch.equals("x64-musl")) {
			features.add("musl");
			arch = "x64";
		}

		// Download and compute hashes
		DownloadResult download = downloadFile(url, filename);

		// Create metadata
		JdkMetadata metadata = new JdkMetadata();
		metadata.setVendor(VENDOR);
		metadata.setFilename(filename);
		metadata.setReleaseType(releaseType);
		metadata.setVersion(version);
		metadata.setJavaVersion(version);
		metadata.setJvmImpl("hotspot");
		metadata.setOs(normalizeOs(os));
		metadata.setArchitecture(normalizeArch(arch));
		metadata.setFileType(ext);
		metadata.setImageType("jdk");
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
			return new JavaSeRi(config);
		}
	}
}
