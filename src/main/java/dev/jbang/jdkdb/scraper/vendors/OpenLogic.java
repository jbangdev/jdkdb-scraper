package dev.jbang.jdkdb.scraper.vendors;

import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.BaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for OpenLogic OpenJDK releases */
public class OpenLogic extends BaseScraper {
	private static final String VENDOR = "openlogic";
	private static final String PROPERTIES_URL =
			"https://github.com/foojayio/openjdk_releases/raw/main/openlogic.properties";
	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^openlogic-openjdk-(?:(jre|jdk)-)?([0-9]+(?:u[0-9]+)?(?:\\.[0-9.]+)?(?:-[0-9]+)?)-(linux|windows|mac)-(aarch64|x64|x32|arm32)\\.(tar\\.gz|zip|msi|dmg|deb|rpm)$");

	public OpenLogic(ScraperConfig config) {
		super(config);
	}

	@Override
	protected List<JdkMetadata> scrape() throws Exception {
		List<JdkMetadata> allMetadata = new ArrayList<>();

		log("Fetching properties from " + PROPERTIES_URL);
		String propertiesContent = httpUtils.downloadString(PROPERTIES_URL);

		Properties props = new Properties();
		props.load(new StringReader(propertiesContent));

		log("Found " + props.size() + " entries in properties file");

		for (String key : props.stringPropertyNames()) {
			String url = props.getProperty(key);
			String filename = url.substring(url.lastIndexOf('/') + 1);

			if (metadataExists(filename)) {
				log("Skipping " + filename + " (already exists)");
				continue;
			}

			try {
				JdkMetadata metadata = processFile(filename, url);
				if (metadata != null) {
					allMetadata.add(metadata);
				}
			} catch (Exception e) {
				fail(filename, e);
			}
		}

		return allMetadata;
	}

	private JdkMetadata processFile(String filename, String url) throws Exception {
		Matcher matcher = FILENAME_PATTERN.matcher(filename);
		if (!matcher.matches()) {
			log("Filename doesn't match pattern: " + filename);
			return null;
		}

		String imageType = matcher.group(1);
		if (imageType == null) {
			// If not specified, assume JDK
			imageType = "jdk";
		} else {
			// Remove trailing dash from capture group
			imageType = imageType.replace("-", "");
		}

		String version = matcher.group(2);
		String os = matcher.group(3);
		String arch = matcher.group(4);
		String ext = matcher.group(5);

		// All OpenLogic releases are GA
		String releaseType = "ga";

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
		metadata.setFeatures(new ArrayList<>());
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

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "openlogic";
		}

		@Override
		public String vendor() {
			return "OpenLogic";
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new OpenLogic(config);
		}
	}
}
