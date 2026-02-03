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

/** Scraper for Mandrel (Red Hat's downstream distribution of GraalVM) releases */
public class Mandrel extends GitHubReleaseScraper {
	private static final String VENDOR = "mandrel";

	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^mandrel-java(\\d{1,2})-(linux|macos|windows)-(amd64|aarch64)-([\\d+.]{2,}.*)\\.tar\\.gz$");

	public Mandrel(ScraperConfig config) {
		super(config);
	}

	@Override
	protected String getGitHubOrg() {
		return "graalvm";
	}

	@Override
	protected List<String> getGitHubRepos() {
		return List.of("mandrel");
	}

	@Override
	protected List<JdkMetadata> processRelease(JsonNode release) throws Exception {
		List<JdkMetadata> allMetadata = new ArrayList<>();

		String tagName = release.get("tag_name").asText();
		log("Processing release: " + tagName);

		JsonNode assets = release.get("assets");
		if (assets != null && assets.isArray()) {
			for (JsonNode asset : assets) {
				String assetName = asset.get("name").asText();

				// Only process mandrel tar.gz files
				if (assetName.startsWith("mandrel-") && assetName.endsWith("tar.gz")) {
					try {
						processAsset(tagName, assetName, allMetadata);
					} catch (InterruptedProgressException | TooManyFailuresException e) {
						throw e;
					} catch (Exception e) {
						log("Failed to process " + assetName + ": " + e.getMessage());
					}
				}
			}
		}

		return allMetadata;
	}

	private void processAsset(String tagName, String assetName, List<JdkMetadata> allMetadata) throws Exception {

		if (metadataExists(assetName)) {
			log("Skipping " + assetName + " (already exists)");
			return;
		}

		Matcher matcher = FILENAME_PATTERN.matcher(assetName);
		if (!matcher.matches()) {
			log("Skipping " + assetName + " (does not match pattern)");
			return;
		}

		String javaVersion = matcher.group(1);
		String os = matcher.group(2);
		String arch = matcher.group(3);
		String version = matcher.group(4);

		// Determine release type
		String releaseType = determineReleaseType(version);

		String url = String.format(
				"https://github.com/%s/%s/releases/download/%s/%s",
				getGitHubOrg(), getGitHubRepos().get(0), tagName, assetName);

		// Download and compute hashes
		DownloadResult download = downloadFile(url, assetName);

		// Create metadata
		JdkMetadata metadata = new JdkMetadata();
		metadata.setVendor(VENDOR);
		metadata.setFilename(assetName);
		metadata.setReleaseType(releaseType);
		metadata.setVersion(version + "+java" + javaVersion);
		metadata.setJavaVersion(javaVersion);
		metadata.setJvmImpl("graalvm");
		metadata.setOs(normalizeOs(os));
		metadata.setArchitecture(normalizeArch(arch));
		metadata.setFileType("tar.gz");
		metadata.setImageType("jdk");
		metadata.setFeatures(new ArrayList<>());
		metadata.setUrl(url);
		metadata.setMd5(download.md5());
		metadata.setMd5File(assetName + ".md5");
		metadata.setSha1(download.sha1());
		metadata.setSha1File(assetName + ".sha1");
		metadata.setSha256(download.sha256());
		metadata.setSha256File(assetName + ".sha256");
		metadata.setSha512(download.sha512());
		metadata.setSha512File(assetName + ".sha512");
		metadata.setSize(download.size());

		saveMetadataFile(metadata);
		allMetadata.add(metadata);
		log("Processed " + assetName);
	}

	private String determineReleaseType(String version) {
		if (version.endsWith("Final")) {
			return "ga";
		} else if (version.contains("Alpha") || version.contains("Beta")) {
			return "ea";
		}
		return "ea";
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
			return new Mandrel(config);
		}
	}
}
