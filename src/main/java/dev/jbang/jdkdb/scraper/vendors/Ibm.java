package dev.jbang.jdkdb.scraper.vendors;

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

/** Scraper for IBM JDK releases */
public class Ibm extends BaseScraper {
	private static final String VENDOR = "ibm";
	private static final String BASE_URL = "https://public.dhe.ibm.com/ibmdl/export/pub/systems/cloud/runtimes/java/";

	private static final Pattern VERSION_PATTERN = Pattern.compile("<a href=\"([8]\\.[01]\\.[0-9]+\\.[0-9]+)/\">");
	private static final Pattern ARCH_PATTERN = Pattern.compile("<a href=\"([a-z0-9_]+)/\">");
	private static final Pattern FILE_PATTERN = Pattern.compile("<a href=\"(.*\\.tgz)\">");

	public Ibm(ScraperConfig config) {
		super(config);
	}

	@Override
	protected List<JdkMetadata> scrape() throws Exception {
		List<JdkMetadata> allMetadata = new ArrayList<>();

		log("Fetching index");
		String indexHtml = httpUtils.downloadString(BASE_URL);

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
				String archHtml = httpUtils.downloadString(archUrl);

				Matcher archMatcher = ARCH_PATTERN.matcher(archHtml);
				List<String> architectures = new ArrayList<>();
				while (archMatcher.find()) {
					architectures.add(archMatcher.group(1));
				}

				for (String architecture : architectures) {
					log("Processing architecture: " + architecture);

					// Fetch file list
					String filesUrl = BASE_URL + jdkVersion + "/linux/" + architecture + "/";
					String filesHtml = httpUtils.downloadString(filesUrl);

					Matcher fileMatcher = FILE_PATTERN.matcher(filesHtml);
					while (fileMatcher.find()) {
						String ibmFile = fileMatcher.group(1);

						// Skip SFJ files
						if (ibmFile.contains("sfj")) {
							log("Ignoring " + ibmFile);
							continue;
						}

						try {
							processFile(ibmFile, jdkVersion, architecture, allMetadata);
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

	private void processFile(String ibmFile, String jdkVersion, String architecture, List<JdkMetadata> allMetadata)
			throws Exception {

		if (metadataExists(ibmFile)) {
			log("Skipping " + ibmFile + " (already exists)");
			return;
		}

		String imageType = ibmFile.contains("sdk") ? "jdk" : "jre";
		String url = BASE_URL + jdkVersion + "/linux/" + architecture + "/" + ibmFile;

		// Download and compute hashes
		DownloadResult download = downloadFile(url, ibmFile);

		// Create metadata
		JdkMetadata metadata = new JdkMetadata();
		metadata.setVendor(VENDOR);
		metadata.setFilename(ibmFile);
		metadata.setReleaseType("ga");
		metadata.setVersion(jdkVersion);
		metadata.setJavaVersion(jdkVersion);
		metadata.setJvmImpl("openj9");
		metadata.setOs("linux");
		metadata.setArchitecture(normalizeArch(architecture));
		metadata.setFileType("tgz");
		metadata.setImageType(imageType);
		metadata.setFeatures(new ArrayList<>());
		metadata.setUrl(url);
		metadata.setMd5(download.md5());
		metadata.setMd5File(ibmFile + ".md5");
		metadata.setSha1(download.sha1());
		metadata.setSha1File(ibmFile + ".sha1");
		metadata.setSha256(download.sha256());
		metadata.setSha256File(ibmFile + ".sha256");
		metadata.setSha512(download.sha512());
		metadata.setSha512File(ibmFile + ".sha512");
		metadata.setSize(download.size());

		saveMetadataFile(metadata);
		allMetadata.add(metadata);
		log("Processed " + ibmFile);
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
