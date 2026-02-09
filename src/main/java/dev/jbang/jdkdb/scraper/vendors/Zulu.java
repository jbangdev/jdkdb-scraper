package dev.jbang.jdkdb.scraper.vendors;

import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.BaseScraper;
import dev.jbang.jdkdb.scraper.DownloadResult;
import dev.jbang.jdkdb.scraper.InterruptedProgressException;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import dev.jbang.jdkdb.scraper.TooManyFailuresException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for Azul Zulu releases */
public class Zulu extends BaseScraper {
	private static final String VENDOR = "zulu";
	private static final String INDEX_URL = "https://static.azul.com/zulu/bin/";
	private static final Pattern LINK_PATTERN = Pattern.compile(
			"<a href=\"[^\"]*/(zulu[0-9]+[^\"]+-(linux|macosx|win|solaris)_(musl_x64|musl_aarch64|x64|i686|aarch32hf|aarch32sf|aarch64|ppc64|sparcv9)\\.(tar\\.gz|zip|msi|dmg))\">");
	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^zulu([0-9+_.]{2,})-(?:(ca-crac|ca-fx-dbg|ca-fx|ca-hl|ca-dbg|ea-cp3|ca|ea|dbg|oem|beta)-)?(jdk|jre)(.*)-(linux|macosx|win|solaris)_(musl_aarch64|musl_x64|x64|i686|aarch32hf|aarch32sf|aarch64|ppc64|sparcv9)\\.(tar\\.gz|zip|msi|dmg)$");

	public Zulu(ScraperConfig config) {
		super(config);
	}

	@Override
	protected List<JdkMetadata> scrape() throws Exception {
		List<JdkMetadata> allMetadata = new ArrayList<>();

		// Download index page
		log("Fetching index from " + INDEX_URL);
		String html = httpUtils.downloadString(INDEX_URL);

		// Extract file links
		Matcher linkMatcher = LINK_PATTERN.matcher(html);
		List<String> files = new ArrayList<>();
		while (linkMatcher.find()) {
			files.add(linkMatcher.group(1));
		}

		log("Found " + files.size() + " files to process");

		try {
			for (String filename : files) {
				if (metadataExists(filename)) {
					allMetadata.add(skipped(filename));
					skip(filename);
					continue;
				}

				try {
					JdkMetadata metadata = processFile(filename);
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

	private JdkMetadata processFile(String filename) throws Exception {
		Matcher matcher = FILENAME_PATTERN.matcher(filename);
		if (!matcher.matches()) {
			log("Filename doesn't match pattern: " + filename);
			return null;
		}

		String version = matcher.group(1);
		String releaseTypeStr = matcher.group(2) != null ? matcher.group(2) : "";
		String imageType = matcher.group(3);
		String javaVersion = matcher.group(4);
		String os = matcher.group(5);
		String archStr = matcher.group(6);
		String archive = matcher.group(7);

		String url = INDEX_URL + filename;

		// Normalize release type
		String releaseType = normalizeZuluReleaseType(releaseTypeStr);
		if (releaseType == null) {
			log("Unknown release type for: " + filename);
			return null;
		}

		// Build features list
		List<String> features = buildFeatures(releaseTypeStr, archStr);

		// Normalize architecture (handle musl variants)
		String arch = archStr;
		if (archStr.equals("musl_aarch64")) {
			arch = "aarch64";
		} else if (archStr.equals("musl_x64")) {
			arch = "x64";
		}

		// Download and compute hashes
		DownloadResult download = downloadFile(url, filename);

		// Create metadata using builder
		return JdkMetadata.builder()
				.vendor(VENDOR)
				.releaseType(releaseType)
				.version(version)
				.javaVersion(javaVersion)
				.jvmImpl("hotspot")
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(archive)
				.imageType(imageType)
				.features(features)
				.url(url)
				.download(filename, download)
				.build();
	}

	private String normalizeZuluReleaseType(String releaseType) {
		return switch (releaseType) {
			case "ca", "ca-fx", "ca-crac", "" -> "ga";
			case "ea", "beta" -> "ea";
			case "ca-dbg", "ca-fx-dbg", "dbg" -> "debug";
			default -> null; // Unknown type
		};
	}

	private List<String> buildFeatures(String releaseType, String arch) {
		List<String> features = new ArrayList<>();

		if (releaseType.equals("ca-fx") || releaseType.equals("ca-fx-dbg")) {
			features.add("javafx");
		}
		if (releaseType.equals("ca-crac")) {
			features.add("crac");
		}
		if (arch.equals("musl_x64") || arch.equals("musl_aarch64")) {
			features.add("musl");
		}

		return features;
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
			return new Zulu(config);
		}
	}
}
