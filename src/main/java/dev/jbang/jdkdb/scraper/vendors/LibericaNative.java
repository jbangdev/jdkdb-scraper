package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
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

/** Scraper for BellSoft Liberica Native Image Kit releases */
public class LibericaNative extends BaseScraper {
	private static final String VENDOR = "liberica-native";
	private static final String API_BASE_URL = "https://api.bell-sw.com/v1/liberica/releases";
	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^bellsoft-liberica-vm-(?:openjdk|core)([0-9]+(?:u[0-9]+)?(?:\\.[0-9]+)?)-([0-9.]+(?:-[0-9]+)?)-(?:(glibc|musl)-)?(linux|windows|macos)-(amd64|aarch64|arm32-vfp-hflt)\\.(tar\\.gz|zip)$");

	public LibericaNative(ScraperConfig config) {
		super(config);
	}

	@Override
	protected List<JdkMetadata> scrape() throws Exception {
		List<JdkMetadata> allMetadata = new ArrayList<>();

		// Query Liberica API for native image releases
		String apiUrl = API_BASE_URL + "?bundle-type=nik&release-type=all&page-size=1000";

		log("Fetching releases from " + apiUrl);
		var res = httpUtils.downloadString(apiUrl);
		if (!res.isSuccess()) {
			log("Failed to fetch releases: " + res.errorMessage());
			return allMetadata;
		}
		JsonNode releases = readJson(res.body());

		if (!releases.isArray()) {
			log("No releases found");
			return allMetadata;
		}

		log("Found " + releases.size() + " potential releases");

		try {
			for (JsonNode release : releases) {
				JsonNode downloadUrl = release.get("downloadUrl");
				if (downloadUrl == null || !downloadUrl.isTextual()) {
					continue;
				}

				String url = downloadUrl.asText();

				// Get additional info from API response
				JsonNode releaseTypeNode = release.get("releaseType");
				String apiReleaseType = releaseTypeNode != null ? releaseTypeNode.asText() : "GA";
				String releaseType = apiReleaseType.equalsIgnoreCase("EA") ? "ea" : "ga";

				String filename = url.substring(url.lastIndexOf('/') + 1);

				// Skip if not a native image kit file
				if (!filename.startsWith("bellsoft-liberica-vm-")) {
					continue;
				}

				if (metadataExists(filename)) {
					log("Skipping " + filename + " (already exists)");
					allMetadata.add(skipped(filename));
					continue;
				}

				try {
					JdkMetadata metadata = processFile(url, releaseType);
					if (metadata != null) {
						saveMetadataFile(metadata);
						allMetadata.add(metadata);
						success(filename);
					}
				} catch (InterruptedProgressException | TooManyFailuresException e) {
					throw e;
				} catch (Exception e) {
					fail(filename, e);
				}
			}
		} catch (InterruptedProgressException e) {
			log("Reached progress limit, aborting");
		}

		return allMetadata;
	}

	private JdkMetadata processFile(String url, String releaseType) throws Exception {
		String filename = url.substring(url.lastIndexOf('/') + 1);

		Matcher matcher = FILENAME_PATTERN.matcher(filename);
		if (!matcher.matches()) {
			log("Filename doesn't match pattern: " + filename);
			return null;
		}

		String javaVersion = matcher.group(1);
		String version = matcher.group(2);
		String libcType = matcher.group(3); // null, "glibc", or "musl"
		String os = matcher.group(4);
		String arch = matcher.group(5);
		String ext = matcher.group(6);

		// Build features list
		List<String> features = new ArrayList<>();
		features.add("native-image");
		if (libcType != null) {
			features.add(libcType);
		}

		// Download and compute hashes
		DownloadResult download = downloadFile(url, filename);

		// Create metadata using builder
		return JdkMetadata.builder()
				.vendor(VENDOR)
				.releaseType(releaseType)
				.version(version)
				.javaVersion(javaVersion)
				.jvmImpl("graalvm")
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(ext)
				.imageType("jdk")
				.features(features)
				.url(url)
				.download(filename, download)
				.build();
	}

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "liberica-native";
		}

		@Override
		public String vendor() {
			return VENDOR;
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new LibericaNative(config);
		}
	}
}
