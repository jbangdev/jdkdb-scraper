package dev.jbang.jdkdb.scraper.vendors;

import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.BaseScraper;
import dev.jbang.jdkdb.scraper.DownloadResult;
import dev.jbang.jdkdb.scraper.InterruptedProgressException;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import dev.jbang.jdkdb.scraper.TooManyFailuresException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for Azul Zulu Prime releases */
public class ZuluPrime extends BaseScraper {
	private static final String VENDOR = "zulu-prime";
	private static final String PROPERTIES_URL =
			"https://github.com/foojayio/openjdk_releases/raw/main/zulu_prime.properties";
	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^prime-([0-9]+\\.[0-9]+\\.[0-9]+)(?:-([0-9]+))-(linux|macos|windows)_(x64|aarch64)-(jdk|jre)-(hotspot|openj9)(?:-(.+?))?\\.(tar\\.gz|zip)$");

	public ZuluPrime(ScraperConfig config) {
		super(config);
	}

	@Override
	protected List<JdkMetadata> scrape() throws Exception {
		List<JdkMetadata> allMetadata = new ArrayList<>();

		String propertiesContent;
		try {
			log("Fetching properties from " + PROPERTIES_URL);
			propertiesContent = httpUtils.downloadString(PROPERTIES_URL);
		} catch (Exception e) {
			fail("Failed to fetch properties file", e);
			return Collections.emptyList();
		}

		Properties props = new Properties();
		props.load(new StringReader(propertiesContent));

		log("Found " + props.size() + " entries in properties file");

		try {
			for (String key : props.stringPropertyNames()) {
				String url = props.getProperty(key);
				String filename = url.substring(url.lastIndexOf('/') + 1);

				if (metadataExists(filename)) {
					allMetadata.add(skipped(filename));
					skip(filename);
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

	private JdkMetadata processFile(String filename, String url) throws Exception {
		Matcher matcher = FILENAME_PATTERN.matcher(filename);
		if (!matcher.matches()) {
			log("Skipping " + filename + " (does not match pattern)");
			return null;
		}

		String version = matcher.group(1);
		String buildNumber = matcher.group(2);
		String os = matcher.group(3);
		String arch = matcher.group(4);
		String imageType = matcher.group(5);
		String jvmImpl = matcher.group(6);
		String additionalInfo = matcher.group(7);
		String ext = matcher.group(8);

		// Build full version if build number present
		String fullVersion = buildNumber != null ? version + "-" + buildNumber : version;

		// All Zulu Prime releases are GA (commercial distribution)
		String releaseType = "ga";

		// Build features list
		List<String> features = new ArrayList<>();
		features.add("prime");
		if (additionalInfo != null && !additionalInfo.isEmpty()) {
			// Parse additional features like "musl", "fx", etc.
			for (String feature : additionalInfo.split("-")) {
				if (!feature.isEmpty()) {
					features.add(feature);
				}
			}
		}

		// Download and compute hashes
		DownloadResult download = downloadFile(url, filename);

		// Create metadata using builder
		return JdkMetadata.builder()
				.vendor(VENDOR)
				.releaseType(releaseType)
				.version(fullVersion)
				.javaVersion(version)
				.jvmImpl(jvmImpl)
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(ext)
				.imageType(imageType)
				.features(features)
				.url(url)
				.download(filename, download)
				.build();
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
			// Right now this scraper is not working as expected
			// It needs to be converted to using an API or scraping
			// a website insteado f relying on Foojay properties
			return When.NEVER;
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new ZuluPrime(config);
		}
	}
}
