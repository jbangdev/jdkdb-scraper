package dev.jbang.jdkdb;

import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.DefaultDownloadManager;
import dev.jbang.jdkdb.scraper.DownloadManager;
import dev.jbang.jdkdb.scraper.NoOpDownloadManager;
import dev.jbang.jdkdb.util.MetadataUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

		// Create download manager
		var threadCount = maxThreads > 0 ? maxThreads : Runtime.getRuntime().availableProcessors();
		DownloadManager downloadManager = statsOnly
				? new NoOpDownloadManager()
				: new DefaultDownloadManager(threadCount, metadataDir, checksumDir);
		downloadManager.start();

		List<JdkMetadata> metadataList = MetadataUtils.collectAllMetadata(vendorDir, 2, false, true);

		// Sort list first by missing checksums
		metadataList.sort((m1, m2) -> {
			boolean m1MissingChecksums = MetadataUtils.hasMissingChecksums(m1);
			boolean m2MissingChecksums = MetadataUtils.hasMissingChecksums(m2);
			if (m1MissingChecksums && !m2MissingChecksums) {
				return -1; // m1 comes before m2
			} else if (!m1MissingChecksums && m2MissingChecksums) {
				return 1; // m2 comes before m1
			} else {
				return 0; // Both are equal in terms of missing data
			}
		});

		Map<String, Integer> vendorMissingCounts = new HashMap<>();
		for (JdkMetadata metadata : metadataList) {
			try {
				String vendorName = metadata.vendor();
				if (!vendorsToProcess.contains(vendorName)) {
					continue; // Skip vendors not in the specified list
				}
				Logger dl = LoggerFactory.getLogger("vendors." + vendorName);
				downloadManager.submit(metadata, vendorName, dl);
				vendorMissingCounts.put(vendorName, vendorMissingCounts.getOrDefault(vendorName, 0) + 1);
				int vendorMissing = vendorMissingCounts.get(vendorName);
				if (limitProgress > 0 && vendorMissing >= limitProgress) {
					dl.info("Reached progress limit of " + limitProgress + " items for vendor " + vendorName
							+ ", skipping remaining files for this vendor");
					System.out.println("Reached progress limit of " + limitProgress + " items for vendor " + vendorName
							+ ", skipping remaining files for this vendor");
					break;
				}
			} catch (Exception e) {
				System.err.println("  Failed to read metadata file: "
						+ Path.of(metadata.filename()).getFileName() + " - " + e.getMessage());
			}
		}

		downloadManager.shutdown();

		int filesWithMissingData = metadataList.size();
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
		System.out.println("Files with missing checksums: " + filesWithMissingData);
		if (filesWithMissingData > 0) {
			System.out.println("Total downloads completed: " + totalCompleted);
			System.out.println("Total downloads failed: " + totalFailed);
		}

		return totalFailed > 0 ? 1 : 0;
	}
}
