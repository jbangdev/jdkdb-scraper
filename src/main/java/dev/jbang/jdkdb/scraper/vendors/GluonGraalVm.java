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

/** Scraper for Gluon GraalVM releases */
public class GluonGraalVm extends GitHubReleaseScraper {
	private static final String VENDOR = "gluon-graalvm";
	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^graalvm-svm-java([0-9]+)-(linux|darwin|windows)-(aarch64|x86_64|amd64)-([0-9.]+(?:-dev)?)\\.(zip|tar\\.gz)$");

	public GluonGraalVm(ScraperConfig config) {
		super(config);
	}

	@Override
	protected String getGitHubOrg() {
		return "gluonhq";
	}

	@Override
	protected List<String> getGitHubRepos() {
		return List.of("graal");
	}

	@Override
	protected List<JdkMetadata> processRelease(JsonNode release) throws Exception {
		List<JdkMetadata> metadataList = new ArrayList<>();

		String tagName = release.get("tag_name").asText();
		boolean isPrerelease = release.get("prerelease").asBoolean();

		JsonNode assets = release.get("assets");
		if (assets == null || !assets.isArray()) {
			return metadataList;
		}

		for (JsonNode asset : assets) {
			String assetName = asset.get("name").asText();

			// Skip non-matching files
			if (!assetName.startsWith("graalvm-svm-") || assetName.endsWith(".sha256")) {
				continue;
			}

			if (metadataExists(assetName)) {
				log("Skipping " + assetName + " (already exists)");
				continue;
			}

			try {
				JdkMetadata metadata = processAsset(tagName, assetName, isPrerelease);
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

	private JdkMetadata processAsset(String tagName, String filename, boolean isPrerelease) throws Exception {
		Matcher matcher = FILENAME_PATTERN.matcher(filename);
		if (!matcher.matches()) {
			log("Filename doesn't match pattern: " + filename);
			return null;
		}

		String javaVersion = matcher.group(1);
		String os = matcher.group(2);
		String arch = matcher.group(3);
		String version = matcher.group(4);
		String ext = matcher.group(5);

		String url = String.format("https://github.com/gluonhq/graal/releases/download/%s/%s", tagName, filename);

		// Determine release type
		String releaseType = isPrerelease || version.contains("-dev") ? "ea" : "ga";

		// Normalize architecture
		String normalizedArch = arch.equals("x86_64") || arch.equals("amd64") ? "x64" : arch;

		// Download and compute hashes
		DownloadResult download = downloadFile(url, filename);

		// Create metadata
		JdkMetadata metadata = new JdkMetadata();
		metadata.setVendor(VENDOR);
		metadata.setFilename(filename);
		metadata.setReleaseType(releaseType);
		metadata.setVersion(version);
		metadata.setJavaVersion(javaVersion);
		metadata.setJvmImpl("graalvm");
		metadata.setOs(normalizeOs(os));
		metadata.setArchitecture(normalizeArch(normalizedArch));
		metadata.setFileType(ext);
		metadata.setImageType("jdk");
		metadata.setFeatures(List.of("native-image", "substrate-vm"));
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
			return new GluonGraalVm(config);
		}
	}
}
