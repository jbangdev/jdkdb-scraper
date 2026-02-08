package dev.jbang.jdkdb.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.reporting.ProgressReporter;
import dev.jbang.jdkdb.reporting.ScraperState;
import dev.jbang.jdkdb.util.HashUtils;
import dev.jbang.jdkdb.util.HttpResult;
import dev.jbang.jdkdb.util.HttpUtils;
import dev.jbang.jdkdb.util.MetadataUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Base class for all vendor scrapers */
public abstract class BaseScraper implements Scraper {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	protected final Path metadataDir;
	protected final Path checksumDir;
	protected final Logger logger;
	protected final ProgressReporter reporter;
	protected final HttpUtils httpUtils;
	protected final boolean fromStart;
	protected final int maxFailureCount;
	protected final int limitProgress;
	protected final ScraperState state;

	private int failureCount = 0;
	private int processedCount = 0;
	private int skippedCount = 0;

	public BaseScraper(ScraperConfig config) {
		this.metadataDir = config.metadataDir();
		this.checksumDir = config.checksumDir();
		this.logger = config.logger();
		this.reporter = config.reporter();
		this.state = config.state();
		this.fromStart = config.fromStart();
		this.maxFailureCount = config.maxFailureCount();
		this.limitProgress = config.limitProgress();
		this.httpUtils = new HttpUtils();
	}

	/** Execute the scraping logic */
	protected abstract List<JdkMetadata> scrape() throws Exception;

	@Override
	public ScraperResult call() {
		try {
			// Ensure directories exist
			Files.createDirectories(metadataDir);
			Files.createDirectories(checksumDir);

			log("Starting scraper");

			// Execute the scraping logic
			var metadataList = scrape();

			int processed = 0;
			int skipped = 0;
			if (metadataList != null) {
				for (var metadata : metadataList) {
					if (metadata.getVendor() != null) {
						processed++;
					} else {
						skipped++;
					}
				}
			}
			log("Completed successfully. Processed " + processed + " items, skipped " + skipped + " items");
			if (state != null) {
				state.complete();
			}
			return ScraperResult.success(processed, skipped);
		} catch (Exception e) {
			log("Failed with error: " + e.getMessage());
			if (state != null) {
				state.fail();
			}
			return ScraperResult.failure(e);
		}
	}

	/** Log a progress message at FINE level */
	protected void log(String message) {
		logger.fine(message);
	}

	/** Log a progress message at FINER level (more verbose) */
	protected void fine(String message) {
		logger.finer(message);
	}

	/** Log successful processing of single metadata item */
	protected void success(String filename) {
		logger.info("Processed " + filename);
		if (state != null) state.incrementProcessed();
		if (limitProgress > 0 && ++processedCount >= limitProgress) {
			throw new InterruptedProgressException("Reached progress limit of " + limitProgress + " items, aborting");
		}
	}

	/** Log failure to process single metadata item */
	protected void fail(String message, Exception error) {
		logger.log(Level.SEVERE, "Failed " + message + ": " + error.getMessage(), error);
		if (state != null) state.incrementFailed();
		if (maxFailureCount > 0 && ++failureCount >= maxFailureCount) {
			throw new TooManyFailuresException("Too many failures, aborting");
		}
	}

	protected JsonNode readJson(String json) throws IOException {
		return OBJECT_MAPPER.readTree(json);
	}

	/** Check if metadata file already exists */
	protected boolean metadataExists(String filename) {
		if (fromStart) {
			return false;
		}
		if (!filename.endsWith(".json")) {
			filename += ".json";
		}
		Path metadataFile = metadataDir.resolve(filename);
		return Files.exists(metadataFile);
	}

	/** Save individual metadata to file */
	protected void saveMetadataFile(JdkMetadata metadata) throws IOException {
		Path metadataFile = metadataDir.resolve(metadata.getMetadataFilename());
		MetadataUtils.saveMetadataFile(metadataFile, metadata);
	}

	protected JdkMetadata skipped(String metadataFilename) {
		if (!metadataFilename.endsWith(".json")) {
			metadataFilename += ".json";
		}
		skippedCount++;
		if (state != null) state.incrementSkipped();
		return JdkMetadata.builder().metadataFilename(metadataFilename).build();
	}

	/** Download a file and compute its hashes */
	protected DownloadResult downloadFile(String url, String filename) throws IOException, InterruptedException {
		Path tempFile = Files.createTempFile("jdk-metadata-", "-" + filename);

		try {
			log("Downloading " + filename + " to " + tempFile);
			HttpResult<String> result = httpUtils.downloadFile(url, tempFile);
			if (!result.isSuccess()) {
				throw new IOException(result.errorMessage());
			}

			long size = Files.size(tempFile);

			// Compute hashes
			log("Computing hashes for " + filename);
			String md5 = HashUtils.computeHash(tempFile, "MD5");
			String sha1 = HashUtils.computeHash(tempFile, "SHA-1");
			String sha256 = HashUtils.computeHash(tempFile, "SHA-256");
			String sha512 = HashUtils.computeHash(tempFile, "SHA-512");

			// Save checksum files
			saveChecksumFile(filename, "md5", md5);
			saveChecksumFile(filename, "sha1", sha1);
			saveChecksumFile(filename, "sha256", sha256);
			saveChecksumFile(filename, "sha512", sha512);

			return new DownloadResult(md5, sha1, sha256, sha512, size);

		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	/** Save checksum to file */
	private void saveChecksumFile(String filename, String algorithm, String checksum) throws IOException {
		Path checksumFile = checksumDir.resolve(filename + "." + algorithm);
		Files.writeString(checksumFile, checksum + "  " + filename + "\n");
	}

	/** Normalize OS name */
	protected String normalizeOs(String os) {
		if (os == null) return "unknown";
		var lower = os.toLowerCase();

		return switch (lower) {
			case String s when s.matches("linux|alpine-linux") -> "linux";
			case String s when s.matches("mac|macos|macosx|osx|darwin") -> "macosx";
			case String s when s.matches("win|windows") -> "windows";
			case "solaris" -> "solaris";
			case "aix" -> "aix";
			default -> "unknown-os-" + os;
		};
	}

	/** Normalize architecture name */
	protected String normalizeArch(String arch) {
		if (arch == null) return "unknown";
		var lower = arch.toLowerCase();

		return switch (lower) {
			case String s when s.matches("amd64|x64|x86_64|x86-64") -> "x86_64";
			case String s when s.matches("x32|x86|x86_32|x86-32|i386|i586|i686") -> "i686";
			case String s when s.matches("aarch64|arm64") -> "aarch64";
			case String s when s.matches("arm|arm32|armv7|aarch32sf") -> "arm32";
			case String s when s.matches("arm32-vfp-hflt|aarch32hf") -> "arm32-vfp-hflt";
			case "ppc" -> "ppc32";
			case "ppc64" -> "ppc64";
			case "ppc64le" -> "ppc64le";
			case String s when s.matches("s390x|s390") -> "s390x";
			case "sparcv9" -> "sparcv9";
			case "riscv64" -> "riscv64";
			default -> "unknown-arch-" + arch;
		};
	}

	/** Normalize release type */
	protected String normalizeReleaseType(String releaseType) {
		if (releaseType == null) return "ga";
		var lower = releaseType.toLowerCase();

		return (lower.contains("ea") || lower.contains("early")) ? "ea" : "ga";
	}
}
