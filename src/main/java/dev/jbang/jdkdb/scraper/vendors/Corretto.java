package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.BaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Scraper for Amazon Corretto releases */
public class Corretto extends BaseScraper {
	private static final String VENDOR = "corretto";
	private static final String GITHUB_ORG = "corretto";
	private static final String GITHUB_API_BASE = "https://api.github.com/repos";
	private static final List<String> CORRETTO_REPOS = Arrays.asList(
			"corretto-8",
			"corretto-11",
			"corretto-17",
			"corretto-18",
			"corretto-19",
			"corretto-20",
			"corretto-21",
			"corretto-22",
			"corretto-23",
			"corretto-24",
			"corretto-25",
			"corretto-jdk");

	public Corretto(ScraperConfig config) {
		super(config);
	}

	@Override
	protected List<JdkMetadata> scrape() throws Exception {
		List<JdkMetadata> allMetadata = new ArrayList<>();

		// Fetch releases from all Corretto repositories
		for (String repo : CORRETTO_REPOS) {
			log("Processing repository: " + repo);

			String releasesUrl = String.format("%s/%s/%s/releases?per_page=100", GITHUB_API_BASE, GITHUB_ORG, repo);
			String json = httpUtils.downloadString(releasesUrl);
			JsonNode releases = objectMapper.readTree(json);

			if (releases.isArray()) {
				for (JsonNode release : releases) {
					String version = release.get("tag_name").asText();
					processVersion(version, allMetadata);
				}
			}
		}

		return allMetadata;
	}

	private void processVersion(String version, List<JdkMetadata> allMetadata) {
		// Define OS, architecture, extension, and image type combinations
		String[][] osConfigs = {
			{"linux", "x86,x64,aarch64,armv7", "tar.gz,rpm,deb", "none"},
			{"alpine-linux", "x64,aarch64", "tar.gz", "none"},
			{"macosx", "x64,aarch64", "tar.gz,pkg", "none"},
			{"windows", "x64,x86", "zip,msi", "jre,jdk,none"}
		};

		for (String[] config : osConfigs) {
			String os = config[0];
			String[] archs = config[1].split(",");
			String[] exts = config[2].split(",");
			String[] imageTypes = config[3].split(",");

			for (String arch : archs) {
				for (String ext : exts) {
					for (String imageType : imageTypes) {
						// Skip jre/jdk image types for non-zip Windows files
						if (os.equals("windows") && !ext.equals("zip") && !imageType.equals("none")) {
							continue;
						}

						String filename = buildFilename(version, os, arch, ext, imageType);
						try {
							downloadVersion(filename, version, os, arch, ext, imageType, allMetadata);
						} catch (Exception e) {
							fail(filename, e);
						}
					}
				}
			}
		}
	}

	private void downloadVersion(
			String filename,
			String version,
			String os,
			String arch,
			String ext,
			String imageType,
			List<JdkMetadata> allMetadata)
			throws Exception {

		if (metadataExists(filename)) {
			log("Skipping " + filename + " (already exists)");
			return;
		}

		// Try both CDN URLs
		String url = null;
		String primaryUrl = String.format("https://corretto.aws/downloads/resources/%s/%s", version, filename);
		String fallbackUrl = String.format("https://d3pxv6yz143wms.cloudfront.net/%s/%s", version, filename);

		try {
			if (httpUtils.checkUrlExists(primaryUrl)) {
				url = primaryUrl;
			} else if (httpUtils.checkUrlExists(fallbackUrl)) {
				url = fallbackUrl;
			}
		} catch (Exception e) {
			// URL doesn't exist, skip
			return;
		}

		if (url == null) {
			return;
		}

		// Download and compute hashes
		DownloadResult download = downloadFile(url, filename);

		// Normalize image type
		String normalizedImageType = imageType.equals("none") ? "jdk" : imageType;

		// Build features list
		List<String> features = new ArrayList<>();
		if (os.equals("alpine-linux")) {
			features.add("musl");
		}

		// Create metadata
		JdkMetadata metadata = new JdkMetadata();
		metadata.setVendor(VENDOR);
		metadata.setFilename(filename);
		metadata.setReleaseType("ga");
		metadata.setVersion(version);
		metadata.setJavaVersion(version);
		metadata.setJvmImpl("hotspot");
		metadata.setOs(normalizeOs(os));
		metadata.setArchitecture(normalizeArch(arch));
		metadata.setFileType(ext);
		metadata.setImageType(normalizedImageType);
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
		allMetadata.add(metadata);
		success(filename);
	}

	private String buildFilename(String version, String os, String arch, String ext, String imageType) {
		if (imageType.equals("none")) {
			return String.format("amazon-corretto-%s-%s-%s.%s", version, os, arch, ext);
		} else {
			return String.format("amazon-corretto-%s-%s-%s-%s.%s", version, os, arch, imageType, ext);
		}
	}

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "corretto";
		}

		@Override
		public String vendor() {
			return "corretto";
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new Corretto(config);
		}
	}
}
