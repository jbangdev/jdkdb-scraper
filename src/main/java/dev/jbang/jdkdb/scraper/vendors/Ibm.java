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

/** Scraper for IBM JDK releases */
public class Ibm extends BaseScraper {
	private static final String VENDOR = "ibm";
	private static final String BASE_URL = "https://public.dhe.ibm.com/ibmdl/export/pub/systems/cloud/runtimes/java/";

	private static final Pattern VERSION_PATTERN = Pattern.compile("<a href=\"([8]\\.[01]\\.[0-9]+\\.[0-9]+)/\">");
	private static final Pattern ARCH_PATTERN = Pattern.compile("<a href=\"([a-z0-9_]+)/\">");
	private static final Pattern FILE_PATTERN = Pattern.compile("<a href=\"(.*\\.(tgz|rpm))\">");

	public Ibm(ScraperConfig config) {
		super(config);
	}

	@Override
	protected List<JdkMetadata> scrape() throws Exception {
		List<JdkMetadata> allMetadata = new ArrayList<>();

		String indexHtml;
		try {
			log("Fetching index");
			indexHtml = httpUtils.downloadString(BASE_URL);
		} catch (Exception e) {
			fail("Failed to fetch index page", e);
			throw e;
		}

		// Extract JDK versions
		Matcher versionMatcher = VERSION_PATTERN.matcher(indexHtml);
		List<String> jdkVersions = new ArrayList<>();
		while (versionMatcher.find()) {
			jdkVersions.add(versionMatcher.group(1));
		}

		log("Found " + jdkVersions.size() + " JDK versions");

		try {
			for (String jdkVersion : jdkVersions) {
				log("Processing JDK version: " + jdkVersion);

				// Fetch architecture list
				String archUrl = BASE_URL + jdkVersion + "/linux/";
				String archHtml;
				try {
					archHtml = httpUtils.downloadString(archUrl);
				} catch (Exception e) {
					log("Failed to fetch architecture list for " + jdkVersion + ": " + e.getMessage());
					continue;
				}

				Matcher archMatcher = ARCH_PATTERN.matcher(archHtml);
				List<String> architectures = new ArrayList<>();
				while (archMatcher.find()) {
					architectures.add(archMatcher.group(1));
				}

				for (String architecture : architectures) {
					log("Processing architecture: " + architecture);

					// Fetch file list
					String filesUrl = BASE_URL + jdkVersion + "/linux/" + architecture + "/";
					String filesHtml;
					try {
						filesHtml = httpUtils.downloadString(filesUrl);
					} catch (Exception e) {
						log("Failed to fetch file list for " + jdkVersion + " " + architecture + ": " + e.getMessage());
						continue;
					}

					Matcher fileMatcher = FILE_PATTERN.matcher(filesHtml);
					while (fileMatcher.find()) {
						String ibmFile = fileMatcher.group(1);

						// Skip SFJ files
						if (ibmFile.contains("sfj")) {
							log("Ignoring " + ibmFile);
							continue;
						}

						if (metadataExists(ibmFile)) {
							allMetadata.add(skipped(ibmFile));
							skip(ibmFile);
							continue;
						}

						try {
							JdkMetadata metadata = processFile(ibmFile, jdkVersion, architecture, allMetadata);
							if (metadata != null) {
								saveMetadataFile(metadata);
								allMetadata.add(metadata);
								success(ibmFile);
							}
						} catch (InterruptedProgressException | TooManyFailuresException e) {
							throw e;
						} catch (Exception e) {
							log("Failed to process " + ibmFile + ": " + e.getMessage());
						}
					}
				}
			}
		} catch (InterruptedProgressException e) {
			log("Reached progress limit, aborting");
		}

		return allMetadata;
	}

	private JdkMetadata processFile(
			String ibmFile, String jdkVersion, String architecture, List<JdkMetadata> allMetadata) throws Exception {

		String fileType;
		if (ibmFile.endsWith(".tgz")) {
			fileType = "tar.gz";
		} else if (ibmFile.endsWith(".rpm")) {
			fileType = "rpm";
		} else {
			return null;
		}

		String imageType = ibmFile.contains("sdk") ? "jdk" : "jre";
		String url = BASE_URL + jdkVersion + "/linux/" + architecture + "/" + ibmFile;

		// Download and compute hashes
		DownloadResult download = downloadFile(url, ibmFile);

		// Create metadata using builder
		return JdkMetadata.builder()
				.vendor(VENDOR)
				.releaseType("ga")
				.version(jdkVersion)
				.javaVersion(jdkVersion)
				.jvmImpl("openj9")
				.os("linux")
				.arch(normalizeArch(architecture))
				.fileType(fileType)
				.imageType(imageType)
				.url(url)
				.download(ibmFile, download)
				.build();
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
			return new Ibm(config);
		}
	}
}
