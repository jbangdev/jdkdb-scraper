package dev.jbang.jdkdb.scraper;

import dev.jbang.jdkdb.model.JdkMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Base scraper for Java releases from jdk.java.net */
public abstract class JavaNetBaseScraper extends BaseScraper {
	protected static final String VENDOR = "openjdk";
	protected static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^openjdk-([0-9]{1,}[^_]*)_(linux|osx|macos|windows)-(aarch64|x64-musl|x64)_bin\\.(tar\\.gz|zip)$");
	protected static final Pattern URL_PATTERN =
			Pattern.compile("href=\"(https://download\\.java\\.net/java/[^\"]*\\.(tar\\.gz|zip))\"");

	public JavaNetBaseScraper(ScraperConfig config) {
		super(config);
	}

	/** Get the list of URLs to fetch index pages from */
	protected abstract List<String> getIndexUrls();

	/** Get features for this scraper (e.g., "leyden", "loom", etc.) */
	protected String getFeature() {
		return null; // No feature by default
	}

	/** Get the vendor name for this scraper */
	protected String getVendor() {
		return VENDOR;
	}

	/** Get the filename pattern for this scraper */
	protected Pattern getFilenamePattern() {
		return FILENAME_PATTERN;
	}

	/** Get the URL pattern for this scraper */
	protected Pattern getUrlPattern() {
		return URL_PATTERN;
	}

	/** Check if a URL should be processed (e.g., filter out source files) */
	protected boolean shouldProcessUrl(String url) {
		return true; // Process all URLs by default
	}

	@Override
	protected List<JdkMetadata> scrape() throws Exception {
		List<JdkMetadata> allMetadata = new ArrayList<>();

		// Fetch all index pages and extract download URLs
		List<String> downloadUrls = new ArrayList<>();
		for (String indexUrl : getIndexUrls()) {
			log("Fetching index from " + indexUrl);
			var res = httpUtils.downloadString(indexUrl);
			if (!res.isSuccess()) {
				fail(indexUrl, new java.io.IOException(res.errorMessage()));
				continue;
			}
			extractUrls(res.body(), downloadUrls);
		}

		log("Found " + downloadUrls.size() + " download URLs");

		try {
			// Process each download URL
			for (String url : downloadUrls) {
				String filename = extractFilename(url);
				if (filename == null) {
					continue;
				}

				if (metadataExists(filename)) {
					log("Skipping " + filename + " (already exists)");
					allMetadata.add(skipped(filename));
					continue;
				}

				try {
					JdkMetadata metadata = processFile(filename, url);
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

	private void extractUrls(String html, List<String> downloadUrls) {
		Matcher matcher = getUrlPattern().matcher(html);
		while (matcher.find()) {
			String url = matcher.group(1);
			if (shouldProcessUrl(url) && !downloadUrls.contains(url)) {
				downloadUrls.add(url);
			}
		}
	}

	private String extractFilename(String url) {
		int lastSlash = url.lastIndexOf('/');
		if (lastSlash >= 0 && lastSlash < url.length() - 1) {
			return url.substring(lastSlash + 1);
		}
		return null;
	}

	private JdkMetadata processFile(String filename, String url) throws Exception {
		Matcher matcher = getFilenamePattern().matcher(filename);
		if (!matcher.matches()) {
			log("Filename doesn't match pattern: " + filename);
			return null;
		}

		String version = matcher.group(1);
		String os = matcher.group(2);
		String archStr = matcher.group(3);
		String ext = matcher.group(4);

		// Handle musl architecture
		String arch = archStr;
		List<String> features = new ArrayList<>();
		if (archStr.equals("x64-musl")) {
			arch = "x64";
			features.add("musl");
		}

		// Add scraper-specific feature
		String feature = getFeature();
		if (feature != null && !feature.isEmpty()) {
			features.add(feature);
		}

		// Determine release type
		String releaseType = determineReleaseType(version);

		// Download and compute hashes
		DownloadResult download = downloadFile(url, filename);

		// Create metadata using builder
		return JdkMetadata.builder()
				.vendor(getVendor())
				.releaseType(releaseType)
				.version(version)
				.javaVersion(version)
				.jvmImpl("hotspot")
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(ext)
				.imageType("jdk")
				.features(features)
				.url(url)
				.download(filename, download)
				.build();
	}

	protected String determineReleaseType(String version) {
		if (version.contains("-ea")) {
			return "ea";
		}
		String feature = getFeature();
		if (feature != null && (feature.equals("leyden") || feature.equals("loom") || feature.equals("valhalla"))) {
			return "ea"; // Project builds are always early access
		}
		return "ga";
	}
}
