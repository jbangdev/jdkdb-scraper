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

/** Scraper for BellSoft Liberica releases */
public class Liberica extends GitHubReleaseScraper {
	private static final String VENDOR = "liberica";
	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^bellsoft-(jre|jdk)(.+?)-(?:ea-)?(linux|windows|macos|solaris)-(amd64|i386|i586|aarch64|arm64|ppc64le|arm32-vfp-hflt|x64|sparcv9|riscv64)-?(fx|lite|full|musl|musl-lite|crac|musl-crac|leyden|musl-leyden|lite-leyden|musl-lite-leyden)?\\.(apk|deb|rpm|msi|dmg|pkg|tar\\.gz|zip)$");

	public Liberica(ScraperConfig config) {
		super(config);
	}

	@Override
	protected String getGitHubOrg() {
		return "bell-sw";
	}

	@Override
	protected String getGitHubRepo() {
		return "Liberica";
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
			String contentType = asset.get("content_type").asText("application/octet-stream");

			// Skip non-binary files
			if (contentType.equals("text/plain")
					|| assetName.endsWith(".txt")
					|| assetName.endsWith(".bom")
					|| assetName.endsWith(".json")
					|| assetName.endsWith("-src.tar.gz")
					|| assetName.endsWith("-src-full.tar.gz")
					|| assetName.endsWith("-src-crac.tar.gz")
					|| assetName.endsWith("-src-leyden.tar.gz")
					|| assetName.contains("-full-nosign")) {
				continue;
			}

			if (metadataExists(assetName)) {
				log("Skipping " + assetName + " (already exists)");
				continue;
			}

			try {
				JdkMetadata metadata = processAsset(tagName, assetName, isPrerelease);
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

	private JdkMetadata processAsset(String tagName, String filename, boolean isPrerelease) throws Exception {
		Matcher matcher = FILENAME_PATTERN.matcher(filename);
		if (!matcher.matches()) {
			log("Filename doesn't match pattern: " + filename);
			return null;
		}

		String imageType = matcher.group(1);
		String version = matcher.group(2);
		String os = matcher.group(3);
		String arch = matcher.group(4);
		String featuresStr = matcher.group(5) != null ? matcher.group(5) : "";
		String ext = matcher.group(6);

		String url = String.format("https://github.com/bell-sw/Liberica/releases/download/%s/%s", tagName, filename);

		// Determine release type
		String releaseType;
		if (isPrerelease) {
			releaseType = "ea";
		} else if (version.contains("ea")) {
			releaseType = "ea";
		} else {
			releaseType = "ga";
		}

		// Build features list
		List<String> features = buildFeatures(featuresStr);

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
		metadata.setImageType(imageType);
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

	private List<String> buildFeatures(String featuresStr) {
		List<String> features = new ArrayList<>();

		if (featuresStr.equals("lite")
				|| featuresStr.equals("musl-lite")
				|| featuresStr.equals("lite-leyden")
				|| featuresStr.equals("musl-lite-leyden")) {
			features.add("lite");
		}

		if (featuresStr.equals("full")) {
			features.add("libericafx");
			features.add("minimal-vm");
			features.add("javafx");
		}

		if (featuresStr.equals("fx")) {
			features.add("javafx");
		}

		if (featuresStr.equals("musl")
				|| featuresStr.equals("musl-lite")
				|| featuresStr.equals("musl-crac")
				|| featuresStr.equals("musl-leyden")
				|| featuresStr.equals("musl-lite-leyden")) {
			features.add("musl");
		}

		if (featuresStr.equals("crac") || featuresStr.equals("musl-crac")) {
			features.add("crac");
		}

		if (featuresStr.equals("leyden")
				|| featuresStr.equals("musl-leyden")
				|| featuresStr.equals("lite-leyden")
				|| featuresStr.equals("musl-lite-leyden")) {
			features.add("leyden");
		}

		return features;
	}

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "liberica";
		}

		@Override
		public String vendor() {
			return "liberica";
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new Liberica(config);
		}
	}
}
