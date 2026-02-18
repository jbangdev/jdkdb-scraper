package dev.jbang.jdkdb;

import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.util.MetadataUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Clean command to remove incomplete metadata files and prune old EA releases */
@Command(
		name = "clean",
		description = "Clean up metadata by removing incomplete files and pruning old EA releases",
		mixinStandardHelpOptions = true)
public class CleanCommand implements Callable<Integer> {

	/** Enum for incomplete metadata types */
	public enum IncompleteType {
		checksums,
		release_info,
		all
	}

	@Option(
			names = {"-m", "--metadata-dir"},
			description = "Directory containing metadata files (default: docs/metadata)",
			defaultValue = "docs/metadata")
	private Path metadataDir;

	@Option(
			names = {"--remove-incomplete"},
			description =
					"Remove metadata files with incomplete data. Options: checksums (missing checksums), release-info (missing release info), all (either missing checksums or release info) (default: all)")
	private IncompleteType removeIncomplete;

	@Option(
			names = {"--remove-invalid"},
			description = "Remove metadata files that fail validation (MetadataUtils.isValidMetadata)")
	private boolean removeInvalid;

	@Option(
			names = {"--prune-ea"},
			description =
					"Prune EA releases older than specified duration (e.g., 30d, 3w, 6m, 1y). Duration format: [number][d|w|m|y]")
	private String pruneEa;

	@Option(
			names = {"--dry-run"},
			description = "Show statistics without actually deleting files")
	private boolean dryRun;

	@Override
	public Integer call() throws Exception {
		System.out.println("Java Metadata Scraper - Clean");
		System.out.println("=============================");
		System.out.println("Metadata directory: " + metadataDir.toAbsolutePath());

		// Apply default values if no options specified
		if (removeIncomplete == null && !removeInvalid && pruneEa == null && !dryRun) {
			System.out.println("No options specified, using defaults: --remove-incomplete=all --prune-ea=6m --dry-run");
			System.out.println();
			removeIncomplete = IncompleteType.all;
			pruneEa = "6m";
			dryRun = true;
		}

		System.out.println("Configuration:");
		System.out.println("  Remove incomplete: "
				+ (removeIncomplete != null
						? removeIncomplete.toString().toLowerCase().replace("_", "-")
						: "disabled"));
		System.out.println("  Remove invalid: " + removeInvalid);
		System.out.println("  Prune EA: " + (pruneEa != null ? pruneEa : "disabled"));
		System.out.println("  Dry run: " + dryRun);
		System.out.println();

		Path vendorDir = metadataDir.resolve("vendor");
		if (!Files.exists(vendorDir) || !Files.isDirectory(vendorDir)) {
			System.err.println("Error: Vendor directory not found: " + vendorDir.toAbsolutePath());
			return 1;
		}

		// Parse prune-ea duration if specified
		Instant pruneThreshold = null;
		if (pruneEa != null) {
			Duration duration = MetadataUtils.parseDuration(pruneEa);
			if (duration == null) {
				System.err.println("Error: Invalid duration format: " + pruneEa);
				System.err.println("Expected format: [number][d|w|m|y] (e.g., 30d, 3w, 6m, 1y)");
				return 1;
			}
			pruneThreshold = Instant.now().minus(duration);
			System.out.println("Pruning EA releases older than: " + pruneEa + " (before " + pruneThreshold + ")");
			System.out.println();
		}

		// Collect files to delete
		final CleanStats stats = new CleanStats();
		final List<Path> filesToDelete = new ArrayList<>();
		final Instant finalPruneThreshold = pruneThreshold;

		List<JdkMetadata> metadataList = MetadataUtils.collectAllMetadata(vendorDir, 2, true, true);
		for (JdkMetadata metadata : metadataList) {
			try {
				processMetadataFile(metadata, stats, filesToDelete, finalPruneThreshold);
			} catch (IOException e) {
				System.err.println(
						"  Failed to process " + metadata.metadataFile().getFileName() + ": " + e.getMessage());
				stats.errors++;
			}
		}

		// Print summary
		System.out.println();
		System.out.println("Summary:");
		System.out.println("========");
		System.out.println("Total files scanned: " + stats.totalFiles);
		System.out.println("Incomplete files: " + stats.incompleteFiles);
		System.out.println("Invalid files: " + stats.invalidFiles);
		System.out.println("Old EA releases: " + stats.oldEaReleases);
		System.out.println("Errors: " + stats.errors);
		System.out.println();

		if (filesToDelete.isEmpty()) {
			System.out.println("No files to delete.");
			return 0;
		}

		System.out.println("Files to delete: " + filesToDelete.size());

		if (dryRun) {
			System.out.println();
			System.out.println("DRY RUN - No files were actually deleted.");
			System.out.println("Run without --dry-run to perform actual deletion.");
		} else {
			System.out.println();
			System.out.println("Deleting files...");
			int deletedCount = 0;
			int failedCount = 0;

			for (Path file : filesToDelete) {
				try {
					Files.delete(file);
					deletedCount++;
					System.out.println("  Deleted: " + file.getFileName());
				} catch (IOException e) {
					System.err.println("  Failed to delete " + file.getFileName() + ": " + e.getMessage());
					failedCount++;
				}
			}

			System.out.println();
			System.out.println("Deleted: " + deletedCount + " files");
			if (failedCount > 0) {
				System.out.println("Failed: " + failedCount + " files");
			}
		}

		return 0;
	}

	private void processMetadataFile(
			JdkMetadata metadata, CleanStats stats, List<Path> filesToDelete, Instant pruneThreshold)
			throws IOException {
		stats.totalFiles++;

		Path metadataFile = metadata.metadataFile();

		boolean shouldDelete = false;
		String reason = null;

		// Check for invalid metadata
		if (removeInvalid && !shouldDelete) {
			if (!MetadataUtils.isValidMetadata(metadata)) {
				stats.invalidFiles++;
				shouldDelete = true;
				reason = "invalid (failed validation)";
			}
		}

		// Check for incomplete metadata
		if (removeIncomplete != null && !shouldDelete) {
			boolean missingChecksums = metadata.md5() == null
					|| metadata.sha1() == null
					|| metadata.sha256() == null
					|| metadata.sha512() == null;
			boolean missingReleaseInfo = metadata.releaseInfo() == null;

			boolean isIncomplete =
					switch (removeIncomplete) {
						case checksums -> missingChecksums;
						case release_info -> missingReleaseInfo;
						case all -> missingChecksums || missingReleaseInfo;
					};

			if (isIncomplete) {
				stats.incompleteFiles++;
				shouldDelete = true;
				if (removeIncomplete == IncompleteType.checksums || (missingChecksums && !missingReleaseInfo)) {
					reason = "incomplete (missing checksums)";
				} else if (removeIncomplete == IncompleteType.release_info
						|| (missingReleaseInfo && !missingChecksums)) {
					reason = "incomplete (missing release info)";
				} else {
					reason = "incomplete (missing checksums and release info)";
				}
			}
		}

		// Check for old EA releases
		if (pruneThreshold != null && "ea".equalsIgnoreCase(metadata.releaseType()) && !shouldDelete) {
			FileTime lastModified = Files.getLastModifiedTime(metadataFile);
			if (lastModified.toInstant().isBefore(pruneThreshold)) {
				stats.oldEaReleases++;
				shouldDelete = true;
				reason = "old EA release (last modified: " + lastModified.toInstant() + ")";
			}
		}

		if (shouldDelete) {
			filesToDelete.add(metadataFile);
			System.out.println("  - " + metadataFile.getFileName() + " (" + reason + ")");
		}
	}

	/** Statistics for clean operation */
	private static class CleanStats {
		int totalFiles = 0;
		int incompleteFiles = 0;
		int invalidFiles = 0;
		int oldEaReleases = 0;
		int errors = 0;
	}
}
