package dev.jbang.jdkdb.scraper.impl;

import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.BaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
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
			"^zulu([0-9+_.]{2,})-(?:(ca-crac|ca-fx-dbg|ca-fx|ca-hl|ea-hl|ca-dbg|ca-cp\\d|ea-cp\\d|ca|ea|dbg|oem|beta)-)?(jdk|jre)(.*)-(linux|macosx|win|solaris)_(musl_aarch64|musl_x64|x64|i686|aarch32hf|aarch32sf|aarch64|ppc64|sparcv9)\\.(tar\\.gz|zip|msi|dmg)$");

	public Zulu(ScraperConfig config) {
		super(config);
	}

	@Override
	protected void scrape() throws Exception {
		// Download index page
		String html;
		try {
			log("Fetching index from " + INDEX_URL);
			html = httpUtils.downloadString(INDEX_URL);
		} catch (Exception e) {
			fail("Failed to fetch index page", e);
			return;
		}

		// Extract file links
		Matcher linkMatcher = LINK_PATTERN.matcher(html);
		List<String> files = new ArrayList<>();
		while (linkMatcher.find()) {
			files.add(linkMatcher.group(1));
		}

		log("Found " + files.size() + " files to process");

		for (String filename : files) {
			JdkMetadata metadata = processAsset(filename);
			if (metadata != null) {
				process(metadata);
			}
		}
	}

	private JdkMetadata processAsset(String filename) {
		Matcher matcher = FILENAME_PATTERN.matcher(filename);
		if (!matcher.matches()) {
			if (!filename.contains("-dbg-")) {
				warn("Skipping " + filename + " (does not match pattern)");
			}
			return null;
		}

		if (metadataExists(filename)) {
			return skipped(filename);
		}

		String version = matcher.group(1);
		String releaseTypeStr = matcher.group(2) != null ? matcher.group(2) : "";
		String imageType = matcher.group(3);
		String javaVersion = matcher.group(4);
		String os = matcher.group(5);
		String archStr = matcher.group(6);
		String ext = matcher.group(7);

		String url = INDEX_URL + filename;

		// Normalize release type
		String releaseType = normalizeZuluReleaseType(releaseTypeStr);
		if (releaseType == null) {
			warn("Unknown release type for: " + filename);
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

		// Create metadata
		return JdkMetadata.create()
				.vendor(VENDOR)
				.releaseType(releaseType)
				.version(version)
				.javaVersion(javaVersion)
				.jvmImpl("hotspot")
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(normalizeFileType(ext))
				.imageType(imageType)
				.features(features)
				.url(url)
				.filename(filename);
	}

	private String normalizeZuluReleaseType(String releaseType) {
		if ("ea".equals(releaseType) || "beta".equals(releaseType)) {
			return "ea";
		}
		return "ga";
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
