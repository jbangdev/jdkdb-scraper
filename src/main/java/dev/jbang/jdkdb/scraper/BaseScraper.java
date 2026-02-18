package dev.jbang.jdkdb.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.util.HttpUtils;
import dev.jbang.jdkdb.util.MetadataUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;

/** Base class for all vendor scrapers */
public abstract class BaseScraper implements Scraper {
	protected final Path metadataDir;
	protected final Path checksumDir;
	protected final Logger logger;
	protected final HttpUtils httpUtils;
	protected final boolean fromStart;
	protected final int maxFailureCount;
	protected final int limitProgress;
	protected final Duration skipEaDuration;
	protected final Consumer<JdkMetadata> submitDownload;

	private List<JdkMetadata> allMetadata = new ArrayList<>();
	private int failureCount = 0;
	private int processingCount = 0;
	private int skippedCount = 0;

	public BaseScraper(ScraperConfig config) {
		this.metadataDir = config.metadataDir();
		this.checksumDir = config.checksumDir();
		this.logger = config.logger();
		this.fromStart = config.fromStart();
		this.maxFailureCount = config.maxFailureCount();
		this.limitProgress = config.limitProgress();
		this.skipEaDuration = config.skipEaDuration();
		this.submitDownload = config.submitDownload();
		this.httpUtils = new HttpUtils();
	}

	/** Execute the scraping logic */
	protected abstract void scrape() throws Exception;

	@Override
	public ScraperResult call() {
		try {
			// Ensure directories exist
			Files.createDirectories(metadataDir);
			Files.createDirectories(checksumDir);

			log("Starting scraper");

			// Execute the scraping logic
			try {
				scrape();
			} catch (InterruptedProgressException e) {
				// We can simply ignore these
			}

			log("Completed successfully. Marked " + processingCount + " items for processing, skipped " + skippedCount
					+ " existing items, and had " + failureCount + " failures.");

			return ScraperResult.success(processingCount, skippedCount, failureCount);
		} catch (TooManyFailuresException e) {
			warn("Aborted due to too many failures. Marked " + processingCount + " items for processing, skipped "
					+ skippedCount + " existing items.");
			return ScraperResult.failure(e);
		} catch (Exception e) {
			warn("Failed with error: " + e.getMessage() + " (processed " + processingCount + " items, skipped "
					+ skippedCount + " existing items)");
			return ScraperResult.failure(e);
		}
	}

	/** Log a informative message */
	protected void log(String message) {
		logger.info(message);
	}

	/** Log a informative message */
	protected void fine(String message) {
		logger.trace(message);
	}

	/** Log a warning message */
	protected void warn(String message) {
		logger.warn(message);
	}

	protected void skip(Path metadataFile) {
		logger.debug("Skipping " + metadataFile + " (already exists)");
		skippedCount++;
	}

	/** Log failure to process single metadata item */
	protected void fail(String message, Exception error) {
		logger.error("Failed " + message + ": " + error.getMessage());
		failureCount++;
		if (maxFailureCount > 0 && failureCount >= maxFailureCount) {
			throw new TooManyFailuresException("Too many failures, aborting");
		}
	}

	protected void process(JdkMetadata metadata) {
		allMetadata.add(metadata);
		downloadAndProcess(metadata);
	}

	protected void downloadAndProcess(JdkMetadata metadata) {
		// Skip if this is a skipped placeholder (no filename set)
		String filename = metadata.filename();
		if (metadata.filename() == null || metadata.url() == null) {
			skip(metadata.metadataFile());
			return;
		}

		// Skip if already processed (has checksums)
		if (metadata.md5() != null) {
			return;
		}

		// Submit to download manager for parallel download
		try {
			String url = metadata.url();
			if (url != null) {
				// We save the metadata file before downloading, meaning the checksums
				// will be missing until the download completes!
				saveMetadataFile(metadata);
				submitDownload.accept(metadata);
				processingCount++;
				if (limitProgress > 0 && processingCount >= limitProgress) {
					logger.info("Reached progress limit of " + limitProgress + " items, aborting");
					throw new InterruptedProgressException(
							"Reached progress limit of " + limitProgress + " items, aborting");
				}
			}
		} catch (InterruptedProgressException e) {
			throw e;
		} catch (Exception e) {
			fail("Failed to submit download for " + filename, e);
		}
	}

	protected JsonNode readJson(String json) throws IOException {
		ObjectMapper objectMapper = new ObjectMapper();
		return objectMapper.readTree(json);
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
		if (!MetadataUtils.isValidMetadata(metadata)) {
			throw new IllegalStateException("Metadata is missing required fields or has invalid values for "
					+ metadata.vendor() + " - " + metadata.filename());
		}
		Path metadataFile = metadataDir.resolve(metadata.metadataFile());
		MetadataUtils.saveMetadataFile(metadataFile, metadata);
	}

	public static JdkMetadata skipped(String metadataFilename) {
		if (!metadataFilename.endsWith(".json")) {
			metadataFilename += ".json";
		}
		return JdkMetadata.create().metadataFile(Path.of(metadataFilename));
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
			default -> {
				logger.warn("Unknown OS: " + os);
				yield "unknown-os-" + os;
			}
		};
	}

	/** Normalize architecture name */
	protected String normalizeArch(String arch) {
		if (arch == null) return "unknown";
		var lower = arch.toLowerCase();
		return switch (lower) {
			case String s when s.matches("amd64|x64|x86_64|x86-64") -> "x86_64";
			case String s when s.matches("x32|x86|x86_32|x86-32|i386|i586|i686") -> "i686";
			case String s when s.matches("aarch64|arm64|m\\d") -> "aarch64";
			case String s when s.matches("arm|arm32|armv7|aarch32sf|armel") -> "arm32";
			case String s when s.matches("arm32-vfp-hflt|armhf|aarch32hf") -> "arm32-vfp-hflt";
			case "ppc" -> "ppc32";
			case "ppc64" -> "ppc64";
			case String s when s.matches("ppc64le|ppc64el") -> "ppc64le";
			case String s when s.matches("s390x|s390") -> "s390x";
			case String s when s.matches("sparc|sparcv9") -> "sparcv9";
			case "riscv64" -> "riscv64";
			case "mips" -> "mips";
			case String s when s.matches("mipsel|mipsle") -> "mipsel";
			case "mips64" -> "mips64";
			case String s when s.matches("mips64el|mips64le") -> "mips64el";
			case String s when s.matches("loong64|loongarch64") -> "loong64";
			default -> {
				logger.warn("Unknown architecture: " + arch);
				yield "unknown-architecture-" + arch;
			}
		};
	}

	/** Normalize release type */
	protected String normalizeReleaseType(String releaseType) {
		if (releaseType == null) return "ga";
		var lower = releaseType.toLowerCase();
		return (lower.contains("ea") || lower.contains("early")) ? "ea" : "ga";
	}
}
