package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.GitHubReleaseScraper;
import dev.jbang.jdkdb.scraper.InterruptedProgressException;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import dev.jbang.jdkdb.scraper.TooManyFailuresException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Unified scraper for all Tencent Kona releases across multiple Java versions */
public class Kona extends GitHubReleaseScraper {
	private static final String VENDOR = "kona";

	// Pattern for Kona 8
	private static final Pattern KONA8_SIMPLE_PATTERN =
			Pattern.compile("^TencentKona-([0-9.]{1,}-[0-9]+)\\.x86_64\\.tar\\.gz$");
	private static final Pattern KONA8_PATTERN = Pattern.compile(
			"^TencentKona([0-9b.]{1,})[-_]jdk[-_](fiber)?[-_]?(linux|macosx|windows)[-_](aarch64|x86_64)[-_](8u[0-9]+)(?:_(notarized|signed))?\\.(?:tar\\.gz|tgz|zip)$");

	// Pattern for Kona 11
	private static final Pattern KONA11_PATTERN = Pattern.compile(
			"^TencentKona-([0-9b.]{1,})[-_]jdk_(fiber)?_?(linux|macosx|windows)-(aarch64|x86_64).*\\.(tar\\.gz|zip)$");
	private static final Pattern KONA11_SIMPLE_PATTERN = Pattern.compile("^TencentKona([0-9b.]+)\\.tgz$");

	// Pattern for Kona 17 and 21
	private static final Pattern KONA_STANDARD_PATTERN = Pattern.compile(
			"^TencentKona-([0-9b.]{1,})(?:[_-](ea))?[-_]jdk_(linux|macosx|windows)-(aarch64|x86_64)(?:_(notarized|signed))?\\.(?:tar\\.gz|zip)$");

	public Kona(ScraperConfig config) {
		super(config);
	}

	@Override
	protected String getGitHubOrg() {
		return "Tencent";
	}

	@Override
	protected List<String> getGitHubRepos() {
		return List.of("TencentKona-8", "TencentKona-11", "TencentKona-17", "TencentKona-21");
	}

	@Override
	protected List<JdkMetadata> processRelease(JsonNode release) throws Exception {
		List<JdkMetadata> metadataList = new ArrayList<>();

		JsonNode assets = release.get("assets");
		if (assets == null || !assets.isArray()) {
			return metadataList;
		}

		for (JsonNode asset : assets) {
			String assetName = asset.get("name").asText();
			String downloadUrl = asset.get("browser_download_url").asText();

			if (metadataExists(assetName)) {
				log("Skipping " + assetName + " (already exists)");
				continue;
			}

			try {
				JdkMetadata metadata = processAsset(assetName, downloadUrl);
				if (metadata != null) {
					saveMetadataFile(metadata);
					metadataList.add(metadata);
					success(assetName);
				}
			} catch (InterruptedProgressException | TooManyFailuresException e) {
				throw e;
			} catch (Exception e) {
				fail(assetName, e);
			}
		}

		return metadataList;
	}

	private JdkMetadata processAsset(String filename, String url) throws Exception {
		ParsedFilename parsed = parseFilename(filename);
		if (parsed == null || parsed.version == null) {
			log("Could not parse filename: " + filename);
			return null;
		}

		// Build features list
		List<String> features = new ArrayList<>();
		if (parsed.features != null) {
			String[] featureArray = parsed.features.trim().split("\\s+");
			for (String feature : featureArray) {
				if (!feature.isEmpty() && !feature.equals("notarized") && !feature.equals("signed")) {
					features.add(feature);
				}
			}
		}

		// Determine release type
		String releaseType = parsed.releaseType != null && parsed.releaseType.equals("ea") ? "ea" : "ga";

		// Download and compute hashes
		DownloadResult download = downloadFile(url, filename);

		// Create metadata
		JdkMetadata metadata = new JdkMetadata();
		metadata.setVendor(VENDOR);
		metadata.setFilename(filename);
		metadata.setReleaseType(releaseType);
		metadata.setVersion(parsed.version);
		metadata.setJavaVersion(parsed.javaVersion);
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

		return metadata;
	}

	private ParsedFilename parseFilename(String filename) {
		ParsedFilename result = new ParsedFilename();
		Matcher matcher;

		// Try Kona 8 simple pattern
		matcher = KONA8_SIMPLE_PATTERN.matcher(filename);
		if (matcher.matches()) {
			result.version = matcher.group(1);
			result.javaVersion = "8u" + result.version.split("-")[1];
			result.os = "linux";
			result.arch = "x86_64";
			result.ext = "tar.gz";
			return result;
		}

		// Try Kona 8 pattern
		matcher = KONA8_PATTERN.matcher(filename);
		if (matcher.matches()) {
			result.version = matcher.group(1);
			result.features = matcher.group(2);
			result.os = matcher.group(3);
			result.arch = matcher.group(4);
			result.javaVersion = matcher.group(5);
			String extraFeatures = matcher.group(6);
			if (extraFeatures != null) {
				result.features = (result.features != null ? result.features + " " : "") + extraFeatures;
			}
			result.ext = filename.substring(filename.lastIndexOf('.') + 1);
			if (filename.endsWith(".tar.gz")) {
				result.ext = "tar.gz";
			}
			return result;
		}

		// Try Kona 11 pattern
		matcher = KONA11_PATTERN.matcher(filename);
		if (matcher.matches()) {
			result.version = matcher.group(1);
			result.features = matcher.group(2);
			result.os = matcher.group(3);
			result.arch = matcher.group(4);
			result.javaVersion = result.version;
			result.ext = matcher.group(5);
			return result;
		}

		// Try Kona 11 simple pattern
		matcher = KONA11_SIMPLE_PATTERN.matcher(filename);
		if (matcher.matches()) {
			result.version = matcher.group(1);
			result.javaVersion = result.version;
			result.os = "linux";
			result.arch = "x86_64";
			result.ext = "tgz";
			return result;
		}

		// Try standard pattern (Kona 17, 21)
		matcher = KONA_STANDARD_PATTERN.matcher(filename);
		if (matcher.matches()) {
			result.version = matcher.group(1);
			result.releaseType = matcher.group(2);
			result.os = matcher.group(3);
			result.arch = matcher.group(4);
			result.features = matcher.group(5);
			result.javaVersion = result.version;
			result.ext = filename.substring(filename.lastIndexOf('.') + 1);
			if (filename.endsWith(".tar.gz")) {
				result.ext = "tar.gz";
			}
			return result;
		}

		return null;
	}

	private static class ParsedFilename {
		String version;
		String javaVersion;
		String releaseType;
		String os;
		String arch;
		String ext;
		String features;
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
			return new Kona(config);
		}
	}
}
