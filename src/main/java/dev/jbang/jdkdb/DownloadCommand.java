package dev.jbang.jdkdb;

import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.DefaultDownloadManager;
import dev.jbang.jdkdb.scraper.DownloadManager;
import dev.jbang.jdkdb.scraper.NoOpDownloadManager;
import dev.jbang.jdkdb.util.MetadataUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Download command to download missing checksums for existing metadata files */
@Command(
		name = "download",
		description = "Download and compute checksums for metadata files that have missing checksum values",
		mixinStandardHelpOptions = true)
public class DownloadCommand implements Callable<Integer> {

	@Option(
			names = {"-m", "--metadata-dir"},
			description = "Directory containing metadata files (default: docs/metadata)",
			defaultValue = "docs/metadata")
	private Path metadataDir;

	@Option(
			names = {"-c", "--checksum-dir"},
			description = "Directory to store checksum files (default: docs/checksums)",
			defaultValue = "docs/checksums")
	private Path checksumDir;

	@Option(
			names = {"-v", "--vendors"},
			description =
					"Comma-separated list of vendor names to process (if not specified, all vendors are processed)",
			split = ",")
	private List<String> vendorNames;

	@Option(
			names = {"-t", "--threads"},
			description = "Maximum number of parallel download threads (default: number of processors)",
			defaultValue = "-1")
	private int maxThreads;

	@Option(
			names = {"--limit-progress"},
			description =
					"Maximum number of metadata items to process per scraper before aborting (default: unlimited)",
			defaultValue = "-1")
	private int limitProgress;

	@Option(
			names = {"--stats-only"},
			description = "Skip downloading files and only show statistics (for testing/dry-run)")
	private boolean statsOnly;

	@Override
	public Integer call() throws Exception {
		System.out.println("Java Metadata Scraper - Download");
		System.out.println("=================================");
		System.out.println("Metadata directory: " + metadataDir.toAbsolutePath());
		System.out.println("Checksum directory: " + checksumDir.toAbsolutePath());
		System.out.println();

		Path vendorDir = metadataDir.resolve("vendor");
		if (!Files.exists(vendorDir) || !Files.isDirectory(vendorDir)) {
			System.err.println("Error: Vendor directory not found: " + vendorDir.toAbsolutePath());
			return 1;
		}

		// Determine which vendors to process
		List<String> vendorsToProcess;
		if (vendorNames == null || vendorNames.isEmpty()) {
			// Process all vendors
			try (Stream<Path> paths = Files.list(vendorDir)) {
				vendorsToProcess = paths.filter(Files::isDirectory)
						.map(Path::getFileName)
						.map(Path::toString)
						.sorted()
						.toList();
			}
			System.out.println("Processing all vendors...");
		} else {
			vendorsToProcess = vendorNames;
			System.out.println("Processing specified vendors: " + String.join(", ", vendorNames));
		}
		System.out.println();

		// Create vendor-specific download manager
		var threadCount = maxThreads > 0 ? maxThreads : Runtime.getRuntime().availableProcessors();
		DownloadManager downloadManager = statsOnly
				? new NoOpDownloadManager()
				: new DefaultDownloadManager(threadCount, metadataDir, checksumDir);
		downloadManager.start();

		int totalFiles = 0;
		int filesWithMissingChecksums = 0;
		int filesProcessed = 0;
		List<String> processedVendors = new ArrayList<>();

		// Scan each vendor directory
		for (String vendorName : vendorsToProcess) {
			Path vendorPath = vendorDir.resolve(vendorName);
			if (!Files.exists(vendorPath) || !Files.isDirectory(vendorPath)) {
				System.err.println("Warning: Vendor directory not found: " + vendorName);
				continue;
			}

			System.out.println("Scanning vendor: " + vendorName);

			int vendorMissing = 0;

			try (Stream<Path> files = Files.list(vendorPath)) {
				List<Path> metadataFiles = files.filter(Files::isRegularFile)
						.filter(p -> p.getFileName().toString().endsWith(".json"))
						.filter(p -> !p.getFileName().toString().equals("all.json"))
						.toList();

				totalFiles += metadataFiles.size();

				for (Path metadataFile : metadataFiles) {
					try {
						JdkMetadata metadata = MetadataUtils.readMetadataFile(metadataFile);

						// The metadataFilename is already set to just the filename by readMetadataFile
						// which is what we want since we're using vendor-specific dirs

						// Check if any checksums are missing
						if (hasMissingChecksums(metadata)) {
							Logger dl = LoggerFactory.getLogger("vendors." + vendorName);
							downloadManager.submit(metadata, vendorName, dl);
							filesWithMissingChecksums++;
							vendorMissing++;
							if (limitProgress > 0 && vendorMissing >= limitProgress) {
								dl.info("Reached progress limit of " + limitProgress + " items for vendor " + vendorName
										+ ", skipping remaining files for this vendor");
								System.out.println("Reached progress limit of " + limitProgress + " items for vendor "
										+ vendorName + ", skipping remaining files for this vendor");
								break;
							}
						} else {
							filesProcessed++;
						}
					} catch (Exception e) {
						System.err.println("  Failed to read metadata file: " + metadataFile.getFileName() + " - "
								+ e.getMessage());
					}
				}

				if (!metadataFiles.isEmpty()) {
					processedVendors.add(vendorName);
				}
			}
		}
		downloadManager.shutdown();

		int totalCompleted = 0;
		int totalFailed = 0;

		System.out.println("Waiting for downloads to complete...");
		try {
			downloadManager.awaitCompletion();
			totalCompleted = downloadManager.getCompletedCount();
			totalFailed = downloadManager.getFailedCount();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			System.err.println("Download manager interrupted while waiting for completion");
			return 1;
		}

		System.out.println("Summary");
		System.out.println("=======");
		System.out.println("Total metadata files scanned: " + totalFiles);
		System.out.println("Files already complete: " + filesProcessed);
		System.out.println("Files with missing checksums: " + filesWithMissingChecksums);
		if (filesWithMissingChecksums > 0) {
			System.out.println("Total downloads completed: " + totalCompleted);
			System.out.println("Total downloads failed: " + totalFailed);
		}

		return totalFailed > 0 ? 1 : 0;
	}

	/**
	 * Check if metadata has missing checksums.
	 *
	 * @param metadata The metadata to check
	 * @return true if any of the checksums (md5, sha1, sha256, sha512) are missing
	 */
	private boolean hasMissingChecksums(JdkMetadata metadata) {
		// Only check files that have a URL (otherwise we can't download them)
		if (metadata.url() == null || metadata.filename() == null) {
			return false;
		}

		// Check if any of the primary checksums are missing
		return metadata.md5() == null
				|| metadata.sha1() == null
				|| metadata.sha256() == null
				|| metadata.sha512() == null;
	}
}
