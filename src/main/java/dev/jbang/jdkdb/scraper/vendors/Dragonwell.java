package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
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

/** Unified scraper for all Alibaba Dragonwell releases across multiple Java versions */
public class Dragonwell extends BaseScraper {
	private static final String VENDOR = "dragonwell";
	private static final String ORG = "dragonwell-project";
	private static final String GITHUB_API_BASE = "https://api.github.com/repos";

	// List of all Java versions to scrape
	private static final List<String> JAVA_VERSIONS = List.of("8", "11", "17", "21");

	// Multiple patterns to handle different filename formats
	private static final Pattern STANDARD_EXTENDED_PATTERN_8 = Pattern.compile(
			"^Alibaba_Dragonwell_(?:Standard|Extended)[-_]([0-9.+]{1,}[^_]*)[-_](x64|aarch64)[-_](Linux|linux|Windows|windows)\\.(.*)$");
	private static final Pattern ALIBABA_PATTERN_8 = Pattern.compile(
			"^Alibaba_Dragonwell_([0-9.+]{1,}[^_]*)[-_](?:(GA|Experimental|GA_Experimental|FP1)_)?(Linux|linux|Windows|windows)_(x64|aarch64)\\.(.*)$");
	private static final Pattern STANDARD_EXTENDED_PATTERN = Pattern.compile(
			"^Alibaba_Dragonwell_(?:Standard|Extended)[–_]([0-9.+]{1,}[^_]*)_(aarch64|x64)(?:_alpine)?[-_](Linux|linux|Windows|windows)\\.(.*)$");
	private static final Pattern ALIBABA_PATTERN = Pattern.compile(
			"^Alibaba_Dragonwell_([0-9.+]{1,}[^_]*)(?:_alpine)?[_-](?:(GA|Experimental|GA_Experimental|FP1)_)?(Linux|linux|Windows|windows)_(aarch64|x64)\\.(.*)$");
	private static final Pattern OPENJDK_PATTERN = Pattern.compile(
			"^OpenJDK(?:[0-9.+]{1,})_(x64|aarch64)_(linux|windows)_dragonwell_dragonwell-([0-9.]+)(?:_jdk)?[-_]([0-9._]+)-?(ga|.*?)\\.(tar\\.gz|zip)$");
	private static final Pattern FALLBACK_PATTERN = Pattern.compile(
			"^Alibaba_Dragonwell_([0-9.+]{1,}[^_]*)(?:_alpine)?_(aarch64|x64)_(Linux|linux|Windows|windows)\\.(.*)$");

	public Dragonwell(ScraperConfig config) {
		super(config);
	}

	@Override
	protected List<JdkMetadata> scrape() throws Exception {
		List<JdkMetadata> allMetadata = new ArrayList<>();

		try {
			// Process each Java version
			for (String javaVersion : JAVA_VERSIONS) {
				log("Processing Dragonwell version: " + javaVersion);
				allMetadata.addAll(scrapeVersion(javaVersion));
			}
		} catch (InterruptedProgressException e) {
			log("Reached progress limit, aborting");
		}

		return allMetadata;
	}

	private List<JdkMetadata> scrapeVersion(String javaVersion) throws Exception {
		List<JdkMetadata> metadataList = new ArrayList<>();

		String repo = "dragonwell" + javaVersion;
		String releasesUrl = String.format("%s/%s/%s/releases?per_page=100", GITHUB_API_BASE, ORG, repo);

		String json = httpUtils.downloadString(releasesUrl);
		JsonNode releases = readJson(json);

		if (!releases.isArray()) {
			log("No releases found for version " + javaVersion);
			return metadataList;
		}

		for (JsonNode release : releases) {
			metadataList.addAll(processRelease(release));
		}

		return metadataList;
	}

	private List<JdkMetadata> processRelease(JsonNode release) throws Exception {
		List<JdkMetadata> metadataList = new ArrayList<>();

		String tagName = release.get("tag_name").asText();

		JsonNode assets = release.get("assets");
		if (assets == null || !assets.isArray()) {
			return metadataList;
		}

		for (JsonNode asset : assets) {
			String assetName = asset.get("name").asText();
			String downloadUrl = asset.get("browser_download_url").asText();

			// Only process tar.gz and zip files
			if (!assetName.endsWith(".tar.gz") && !assetName.endsWith(".zip")) {
				continue;
			}

			if (metadataExists(assetName)) {
				log("Skipping " + assetName + " (already exists)");
				continue;
			}

			try {
				JdkMetadata metadata = processAsset(tagName, assetName, downloadUrl);
				if (metadata != null) {
					metadataList.add(metadata);
				}
			} catch (InterruptedProgressException | TooManyFailuresException e) {
				throw e;
			} catch (Exception e) {
				fail(assetName, e);
			}
		}

		return metadataList;
	}

	private JdkMetadata processAsset(String tagName, String filename, String url) throws Exception {
		ParsedFilename parsed = parseFilename(filename);
		if (parsed == null || parsed.version == null) {
			log("Could not parse filename: " + filename);
			return null;
		}

		// Determine release type
		String releaseType = determineReleaseType(parsed.releaseType);

		// Handle alpine feature
		List<String> features = new ArrayList<>();
		if (filename.contains("_alpine")) {
			features.add("musl");
		}

		// Download and compute hashes
		DownloadResult download = downloadFile(url, filename);

		// Create metadata
		JdkMetadata metadata = new JdkMetadata();
		metadata.setVendor(VENDOR);
		metadata.setFilename(filename);
		metadata.setReleaseType(releaseType);
		metadata.setVersion(parsed.version);
		metadata.setJavaVersion(parsed.javaVersion != null ? parsed.javaVersion : parsed.version);
		metadata.setJvmImpl("hotspot");
		metadata.setOs(normalizeOs(parsed.os));
		metadata.setArchitecture(normalizeArch(parsed.arch));
		metadata.setFileType(parsed.ext);
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

		saveMetadataFile(metadata);
		success(filename);

		return metadata;
	}

	private ParsedFilename parseFilename(String filename) {
		ParsedFilename result = new ParsedFilename();

		// Try patterns in order
		Matcher matcher;

		// Try Standard/Extended pattern (version 8)
		matcher = STANDARD_EXTENDED_PATTERN_8.matcher(filename);
		if (matcher.matches()) {
			result.version = matcher.group(1);
			result.javaVersion = result.version;
			result.arch = matcher.group(2);
			result.os = matcher.group(3);
			result.ext = matcher.group(4);
			return result;
		}

		// Try Alibaba pattern (version 8)
		matcher = ALIBABA_PATTERN_8.matcher(filename);
		if (matcher.matches()) {
			result.version = matcher.group(1);
			result.javaVersion = result.version;
			result.releaseType = matcher.group(2);
			result.os = matcher.group(3);
			result.arch = matcher.group(4);
			result.ext = matcher.group(5);
			return result;
		}

		// Try Standard/Extended pattern (newer versions)
		matcher = STANDARD_EXTENDED_PATTERN.matcher(filename);
		if (matcher.matches()) {
			result.version = matcher.group(1);
			result.javaVersion = result.version;
			result.arch = matcher.group(2);
			result.os = matcher.group(3);
			result.ext = matcher.group(4);
			return result;
		}

		// Try Alibaba pattern (newer versions)
		matcher = ALIBABA_PATTERN.matcher(filename);
		if (matcher.matches()) {
			result.version = matcher.group(1);
			result.javaVersion = result.version;
			result.releaseType = matcher.group(2);
			result.os = matcher.group(3);
			result.arch = matcher.group(4);
			result.ext = matcher.group(5);
			return result;
		}

		// Try OpenJDK pattern
		matcher = OPENJDK_PATTERN.matcher(filename);
		if (matcher.matches()) {
			result.arch = matcher.group(1);
			result.os = matcher.group(2);
			result.version = matcher.group(3);
			result.javaVersion = matcher.group(4);
			result.releaseType = matcher.group(5);
			result.ext = matcher.group(6);
			return result;
		}

		// Try fallback pattern
		matcher = FALLBACK_PATTERN.matcher(filename);
		if (matcher.matches()) {
			result.version = matcher.group(1);
			result.javaVersion = result.version;
			result.arch = matcher.group(2);
			result.os = matcher.group(3);
			result.ext = matcher.group(4);
			result.releaseType = "jdk";
			return result;
		}

		return null;
	}

	private String determineReleaseType(String releaseType) {
		if (releaseType == null || releaseType.isEmpty() || releaseType.equals("jdk")) {
			return "ga";
		}
		if (releaseType.equals("ea") || releaseType.contains("Experimental") || releaseType.equals("FP1")) {
			return "ea";
		}
		return "ga";
	}

	private static class ParsedFilename {
		String version;
		String javaVersion;
		String releaseType;
		String os;
		String arch;
		String ext;
	}

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "dragonwell";
		}

		@Override
		public String vendor() {
			return "dragonwell";
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new Dragonwell(config);
		}
	}
}
