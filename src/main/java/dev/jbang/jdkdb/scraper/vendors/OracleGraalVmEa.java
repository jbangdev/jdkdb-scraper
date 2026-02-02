package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.GitHubReleaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for Oracle GraalVM Early Access (EA) builds from GitHub. */
public class OracleGraalVmEa extends GitHubReleaseScraper {
	private static final String VENDOR = "oracle-graalvm";
	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^graalvm-jdk-([0-9]{1,2}\\.[0-9]{1}\\.[0-9]{1,3}-ea\\.[0-9]{1,2})_(linux|macos|windows)-(aarch64|x64)_bin(?:-(notarized))?\\.(?:zip|tar\\.gz)$");

	public OracleGraalVmEa(ScraperConfig config) {
		super(config);
	}

	@Override
	protected String getGitHubOrg() {
		return "graalvm";
	}

	@Override
	protected String getGitHubRepo() {
		return "oracle-graalvm-ea-builds";
	}

	@Override
	protected List<JdkMetadata> processRelease(JsonNode release) throws Exception {
		List<JdkMetadata> metadata = new ArrayList<>();
		String tagName = release.get("tag_name").asText();

		// Only process releases with jdk tag prefix
		if (!tagName.startsWith("jdk")) {
			return metadata;
		}

		JsonNode assets = release.get("assets");
		if (assets == null) {
			return metadata;
		}

		for (JsonNode asset : assets) {
			String assetName = asset.get("name").asText();

			// Only process graalvm-jdk files with tar.gz or zip extensions
			if (!assetName.startsWith("graalvm-jdk-")
					|| (!assetName.endsWith(".tar.gz") && !assetName.endsWith(".zip"))) {
				continue;
			}

			if (metadataExists(assetName)) {
				continue;
			}

			try {
				JdkMetadata jdkMetadata = parseAsset(assetName, asset);
				if (jdkMetadata != null) {
					metadata.add(jdkMetadata);
				}
			} catch (Exception e) {
				fail(assetName, e);
			}
		}

		return metadata;
	}

	private JdkMetadata parseAsset(String assetName, JsonNode asset) throws Exception {
		Matcher matcher = FILENAME_PATTERN.matcher(assetName);
		if (!matcher.matches()) {
			log("Filename does not match pattern: " + assetName);
			return null;
		}

		String javaVersion = matcher.group(1);
		String os = matcher.group(2);
		String arch = matcher.group(3);
		String features = matcher.group(4) != null ? matcher.group(4) : "";
		String extension = assetName.endsWith(".zip") ? "zip" : "tar.gz";

		String downloadUrl = asset.get("browser_download_url").asText();

		// Download and calculate checksums
		DownloadResult download = downloadFile(downloadUrl, assetName);

		JdkMetadata metadata = new JdkMetadata();
		metadata.setVendor(VENDOR);
		metadata.setFilename(assetName);
		metadata.setReleaseType("ea");
		metadata.setVersion(javaVersion);
		metadata.setJavaVersion(javaVersion);
		metadata.setJvmImpl("graalvm");
		metadata.setOs(normalizeOs(os));
		metadata.setArchitecture(normalizeArch(arch));
		metadata.setFileType(extension);
		metadata.setImageType("jdk");
		metadata.setFeatures(features.isEmpty() ? new ArrayList<>() : List.of(features));
		metadata.setUrl(downloadUrl);
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
		success(assetName);

		return metadata;
	}

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "oracle-graalvm-ea";
		}

		@Override
		public String vendor() {
			return "oracle-graalvm";
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new OracleGraalVmEa(config);
		}
	}
}
