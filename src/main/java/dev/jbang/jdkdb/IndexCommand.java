package dev.jbang.jdkdb;

import dev.jbang.jdkdb.util.MetadataUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Index command to generate all.json files for distro directories */
@Command(
		name = "index",
		description = "Generate all.json files for distro directories by aggregating individual metadata files",
		mixinStandardHelpOptions = true)
public class IndexCommand implements Callable<Integer> {
	private static final Logger logger = LoggerFactory.getLogger("command");

	@Option(
			names = {"-m", "--metadata-dir"},
			description = "Directory containing metadata files (default: db/metadata)",
			defaultValue = "db/metadata")
	private Path metadataDir;

	@Option(
			names = {"-x", "--index-dir"},
			description = "Directory to write generated index files to (default: db/metadata)",
			defaultValue = "db/metadata")
	private Path indexDir;

	@Option(
			names = {"-v", "--distros"},
			description =
					"Comma-separated list of distro names to regenerate all.json for (if not specified, all distros are processed)",
			split = ",")
	private List<String> distroNames;

	@Option(
			names = {"--allow-incomplete"},
			description = "Allow incomplete metadata files (missing checksums) to be included")
	private boolean allowIncomplete;

	@Override
	public Integer call() throws Exception {
		logger.info("Java Metadata Scraper - Index");
		logger.info("=============================");
		logger.info("Metadata directory: {}", metadataDir.toAbsolutePath());
		logger.info("Index directory: {}", indexDir.toAbsolutePath());
		logger.info("");

		Path distroDir = metadataDir;
		if (!Files.exists(distroDir) || !Files.isDirectory(distroDir)) {
			logger.error("Error: Distro directory not found: {}", distroDir.toAbsolutePath());
			return 1;
		}

		// Determine which distros to process
		List<String> distrosToProcess;
		if (distroNames == null || distroNames.isEmpty()) {
			// Process all distros
			try (Stream<Path> paths = Files.list(distroDir)) {
				distrosToProcess = paths.filter(Files::isDirectory)
						.map(Path::getFileName)
						.map(Path::toString)
						.sorted()
						.toList();
			}
			logger.info("Processing all distros...");
		} else {
			distrosToProcess = distroNames;
			logger.info("Processing specified distros: {}", String.join(", ", distroNames));
		}
		logger.info("");

		int result = generateIndices(metadataDir, indexDir, distrosToProcess, allowIncomplete);

		return result;
	}

	public static Integer generateIndices(
			Path metadataDir, Path indexDir, List<String> distrosToProcess, boolean allowIncomplete) {
		int successful = 0;
		int failed = 0;
		int indexFilesCreated = 0;

		Path distroDir = metadataDir;
		if (!Files.exists(distroDir) || !Files.isDirectory(distroDir)) {
			logger.error("Error: Distro directory not found: {}", distroDir.toAbsolutePath());
			return 1;
		}

		for (String distroName : distrosToProcess) {
			Path distroPath = distroDir.resolve(distroName);
			if (!Files.exists(distroPath) || !Files.isDirectory(distroPath)) {
				logger.warn("Warning: Distro directory not found: {}", distroName);
				failed++;
				continue;
			}

			try {
				MetadataUtils.generateAllJsonFromDirectory(distroPath, indexDir.resolve(distroName), allowIncomplete);
				successful++;
			} catch (Exception e) {
				logger.error("Failed for distro {}: {}", distroName, e.getMessage(), e);
				failed++;
			}
		}

		try {
			indexFilesCreated = MetadataUtils.generateComprehensiveIndices(metadataDir, indexDir, allowIncomplete);
		} catch (Exception e) {
			logger.error("Failed to generate comprehensive indices: {}", e.getMessage(), e);
			failed++;
		}

		logger.info("");
		logger.info("Index Generation Summary");
		logger.info("========================");
		logger.info("Index files created: {}", indexFilesCreated);
		logger.info("Total distros: {}", distrosToProcess.size());
		logger.info("Successful: {}", successful);
		logger.info("Failed: {}", failed);

		return successful > 0 ? 0 : 1;
	}
}
