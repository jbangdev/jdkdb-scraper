package dev.jbang.jdkdb;

import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.DefaultDownloadManager;
import dev.jbang.jdkdb.scraper.DownloadManager;
import dev.jbang.jdkdb.scraper.InterruptedProgressException;
import dev.jbang.jdkdb.scraper.NoOpDownloadManager;
import dev.jbang.jdkdb.util.ArchiveUtils;
import dev.jbang.jdkdb.util.GitHubUtils;
import dev.jbang.jdkdb.util.MetadataUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
	private static final Logger logger = LoggerFactory.getLogger("command");

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
			names = {"--limit-total"},
			description = "Maximum total number of downloads to accept before stopping (default: unlimited)",
			defaultValue = "-1")
	private int limitTotal;

	@Option(
			names = {"--stats-only"},
			description = "Skip downloading files and only show statistics (for testing/dry-run)")
	private boolean statsOnly;

	@Option(
			names = {"--include"},
			description =
					"Include only these file types (e.g., tar_gz,zip). If specified, only these types will be downloaded.",
			split = ",")
	private List<MetadataUtils.FileType> includeFileTypes;

	@Option(
			names = {"--exclude"},
			description = "Exclude these file types (e.g., msi,exe). These types will not be downloaded.",
			split = ",")
	private List<MetadataUtils.FileType> excludeFileTypes;

	@Override
	public Integer call() throws Exception {
		GitHubUtils.setupGitHubToken();

		// Process file type filter
		Set<MetadataUtils.FileType> fileTypeFilter = processFileTypeFilter(includeFileTypes, excludeFileTypes);

		logger.info("Java Metadata Scraper - Download");
		logger.info("=================================");
		logger.info("Metadata directory: {}", metadataDir.toAbsolutePath());
		logger.info("Checksum directory: {}", checksumDir.toAbsolutePath());
		logger.info("");

		Path vendorDir = metadataDir.resolve("vendor");
		if (!Files.exists(vendorDir) || !Files.isDirectory(vendorDir)) {
			logger.error("Error: Vendor directory not found: {}", vendorDir.toAbsolutePath());
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
			logger.info("Processing all vendors...");
		} else {
			vendorsToProcess = vendorNames;
			logger.info("Processing specified vendors: {}", String.join(", ", vendorNames));
		}
		logger.info("");

		// Create download manager
		var threadCount = maxThreads > 0 ? maxThreads : Runtime.getRuntime().availableProcessors();
		DownloadManager downloadManager = statsOnly
				? new NoOpDownloadManager(fileTypeFilter)
				: new DefaultDownloadManager(threadCount, metadataDir, checksumDir, 3, limitTotal, fileTypeFilter);
		downloadManager.start();
		if (fileTypeFilter != null) {
			logger.info("File type filter enabled: {}", fileTypeFilter);
		}

		List<JdkMetadata> metadataList = MetadataUtils.collectAllMetadata(vendorDir, 2, false, true).stream()
				// Don't try to download macOS PKG files if the only thing we need is
				// the release info, since we won't be able to extract it on non-macOS
				// platforms anyway!
				.filter(m -> !"macosx".equals(m.os())
						|| !"pkg".equals(m.fileType())
						|| MetadataUtils.hasMissingChecksums(m)
						|| !MetadataUtils.hasMissingReleaseInfo(m)
						|| ArchiveUtils.isMacOS())
				// The same goes for Windows MSI files - but in this case we always
				// ignore missing release info since we can't extract it from them
				// on any platform!
				.filter(m -> !"windows".equals(m.os())
						|| !"msi".equals(m.fileType())
						|| MetadataUtils.hasMissingChecksums(m)
						|| !MetadataUtils.hasMissingReleaseInfo(m))
				.sorted((m1, m2) -> {
					boolean m1MissingChecksums = MetadataUtils.hasMissingChecksums(m1);
					boolean m2MissingChecksums = MetadataUtils.hasMissingChecksums(m2);
					if (m1MissingChecksums && !m2MissingChecksums) {
						return -1; // m1 comes before m2
					} else if (!m1MissingChecksums && m2MissingChecksums) {
						return 1; // m2 comes before m1
					} else {
						return 0; // Both are equal in terms of missing data
					}
				})
				.toList();

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
					dl.info(
							"Reached progress limit of {} items for vendor {}, skipping remaining files for this vendor",
							limitProgress,
							vendorName);
					logger.info(
							"Reached progress limit of {} items for vendor {}, skipping remaining files for this vendor",
							limitProgress,
							vendorName);
					break;
				}
			} catch (InterruptedProgressException e) {
				logger.info("Progress limit reached, stopping submission of new downloads");
				break;
			} catch (Exception e) {
				logger.error(
						"Failed to read metadata file: {} - {}",
						Path.of(metadata.filename()).getFileName(),
						e.getMessage());
			}
		}

		downloadManager.shutdown();

		int filesWithMissingData = metadataList.size();
		int totalCompleted = 0;
		int totalFailed = 0;

		logger.info("Waiting for downloads to complete...");
		try {
			downloadManager.awaitCompletion();
			totalCompleted = downloadManager.getCompletedCount();
			totalFailed = downloadManager.getFailedCount();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.error("Download manager interrupted while waiting for completion");
			return 1;
		}

		logger.info("Summary");
		logger.info("=======");
		logger.info("Files with missing data: {}", filesWithMissingData);
		if (filesWithMissingData > 0) {
			int filesWithMissingChecksums = (int) metadataList.stream()
					.filter(MetadataUtils::hasMissingChecksums)
					.count();
			int filesWithMissingReleaseInfo = (int) metadataList.stream()
					.filter(m -> MetadataUtils.hasMissingReleaseInfo(m))
					.count();
			logger.info("Files with missing checksums: {}", filesWithMissingChecksums);
			logger.info("Files with missing release info: {}", filesWithMissingReleaseInfo);
			logger.info("Total downloads completed: {}", totalCompleted);
			logger.info("Total downloads failed: {}", totalFailed);
		}

		return totalCompleted > 0 ? 0 : 1;
	}

	/**
	 * Process the include and exclude file type options to create a filter set.
	 *
	 * @param includeFileTypes List of file types to include (null or empty means include all)
	 * @param excludeFileTypes List of file types to exclude (null or empty means exclude none)
	 * @return A set of file types to accept, or null if no filtering should be applied
	 */
	private Set<MetadataUtils.FileType> processFileTypeFilter(
			List<MetadataUtils.FileType> includeFileTypes, List<MetadataUtils.FileType> excludeFileTypes) {
		if ((includeFileTypes == null || includeFileTypes.isEmpty())
				&& (excludeFileTypes == null || excludeFileTypes.isEmpty())) {
			return null; // No filtering
		}

		Set<MetadataUtils.FileType> result;
		if (includeFileTypes != null && !includeFileTypes.isEmpty()) {
			// Start with only the included types
			result = EnumSet.copyOf(includeFileTypes);
		} else {
			// Start with all types
			result = EnumSet.allOf(MetadataUtils.FileType.class);
		}

		// Remove excluded types
		if (excludeFileTypes != null && !excludeFileTypes.isEmpty()) {
			result.removeAll(excludeFileTypes);
		}

		return result.isEmpty() ? null : result;
	}
}
