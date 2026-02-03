package dev.jbang.jdkdb.scraper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.util.HashUtils;
import dev.jbang.jdkdb.util.HttpUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

/** Base class for all vendor scrapers */
public abstract class BaseScraper implements Scraper {
	protected final Path metadataDir;
	protected final Path checksumDir;
	protected final Logger logger;
	protected final HttpUtils httpUtils;
	protected final boolean fromStart;
	protected final int maxFailureCount;
	protected final int limitProgress;

	private int failureCount = 0;
	private int processedCount = 0;

	public BaseScraper(ScraperConfig config) {
		this.metadataDir = config.metadataDir();
		this.checksumDir = config.checksumDir();
		this.logger = config.logger();
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

			// Save all metadata
			saveMetadata(metadataList);

			log("Completed successfully. Processed " + metadataList.size() + " items");

			return ScraperResult.success(metadataList.size());

		} catch (Exception e) {
			log("Failed with error: " + e.getMessage());
			return ScraperResult.failure(e);
		}
	}

	/** Log a progress message */
	protected void log(String message) {
		logger.fine(message);
	}

	/** Log a progress message */
	protected void fine(String message) {
		logger.finer(message);
	}

	/** Log successful processing of single metadata item */
	protected void success(String filename) {
		logger.info("Processed " + filename);
		if (limitProgress > 0 && ++processedCount >= limitProgress) {
			throw new InterruptedProgressException("Reached progress limit of " + limitProgress + " items, aborting");
		}
	}

	/** Log failure to process single metadata item */
	protected void fail(String message, Exception error) {
		logger.severe("Failed " + message + ": " + error.getMessage());
		if (failureCount > 0 && ++failureCount >= maxFailureCount) {
			throw new TooManyFailuresException("Too many failures, aborting");
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
		Path metadataFile = metadataDir.resolve(filename + ".json");
		return Files.exists(metadataFile);
	}

	/** Save individual metadata to file */
	protected void saveMetadataFile(JdkMetadata metadata) throws IOException {
		Path metadataFile = metadataDir.resolve(metadata.getFilename() + ".json");
		try (var writer = Files.newBufferedWriter(metadataFile)) {
			ObjectMapper objectMapper = new ObjectMapper().configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
			objectMapper.writeValue(writer, metadata);
			// This is to ensure we write the files exactly as the original code did
			writer.write("\n");
		}
	}

	/** Save all metadata and create combined all.json file */
	protected void saveMetadata(List<JdkMetadata> metadataList) throws IOException {
		// Save individual files
		for (JdkMetadata metadata : metadataList) {
			saveMetadataFile(metadata);
		}

		// Create all.json
		if (!metadataList.isEmpty()) {
			Path allJsonPath = metadataDir.resolve("all.json");

			DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
			DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
			printer.indentObjectsWith(indenter);
			printer.indentArraysWith(indenter);

			try (var writer = Files.newBufferedWriter(allJsonPath)) {
				ObjectMapper objectMapper = new ObjectMapper()
						.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)
						.enable(SerializationFeature.INDENT_OUTPUT)
						.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
						.setDefaultPrettyPrinter(printer);
				objectMapper.writeValue(writer, metadataList);
				// This is to ensure we write the files exactly as the original code did
				writer.write("\n");
			}
			log("Created all.json with " + metadataList.size() + " entries");
		}
	}

	/** Download a file and compute its hashes */
	protected DownloadResult downloadFile(String url, String filename) throws IOException, InterruptedException {
		Path tempFile = Files.createTempFile("jdk-metadata-", "-" + filename);

		try {
			log("Downloading " + filename);
			httpUtils.downloadFile(url, tempFile);

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

	/** Result of a file download */
	protected record DownloadResult(String md5, String sha1, String sha256, String sha512, long size) {}
}
