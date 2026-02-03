package dev.jbang.jdkdb.scraper.vendors;

import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.BaseScraper;
import dev.jbang.jdkdb.scraper.InterruptedProgressException;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import dev.jbang.jdkdb.scraper.TooManyFailuresException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Base scraper for OpenJDK releases from jdk.java.net */
public abstract class OpenJdkBaseScraper extends BaseScraper {
	protected static final String VENDOR = "openjdk";
	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^openjdk-([0-9]{1,}[^_]*)_(linux|osx|macos|windows)-(aarch64|x64-musl|x64)_bin\\.(tar\\.gz|zip)$");
	private static final Pattern URL_PATTERN =
			Pattern.compile("href=\"(https://download\\.java\\.net/java/[^\"]*\\.(tar\\.gz|zip))\"");

	public OpenJdkBaseScraper(ScraperConfig config) {
		super(config);
	}

	/** Get the list of URLs to fetch index pages from */
	protected abstract List<String> getIndexUrls();

	/** Get features for this scraper (e.g., "leyden", "loom", etc.) */
	protected String getFeature() {
		return null; // No feature by default
	}

	@Override
	protected List<JdkMetadata> scrape() throws Exception {
		List<JdkMetadata> allMetadata = new ArrayList<>();

		// Fetch all index pages and extract download URLs
		List<String> downloadUrls = new ArrayList<>();
		for (String indexUrl : getIndexUrls()) {
			log("Fetching index from " + indexUrl);
			String html = httpUtils.downloadString(indexUrl);
			extractUrls(html, downloadUrls);
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
		Matcher matcher = URL_PATTERN.matcher(html);
		while (matcher.find()) {
			String url = matcher.group(1);
			if (!downloadUrls.contains(url)) {
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
		Matcher matcher = FILENAME_PATTERN.matcher(filename);
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
