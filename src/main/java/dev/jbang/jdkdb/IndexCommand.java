package dev.jbang.jdkdb;

import dev.jbang.jdkdb.util.MetadataUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Index command to generate all.json files for vendor directories */
@Command(
		name = "index",
		description = "Generate all.json files for vendor directories by aggregating individual metadata files",
		mixinStandardHelpOptions = true)
public class IndexCommand implements Callable<Integer> {

	@Option(
			names = {"-m", "--metadata-dir"},
			description = "Directory containing metadata files (default: docs/metadata)",
			defaultValue = "docs/metadata")
	private Path metadataDir;

	@Option(
			names = {"-v", "--vendors"},
			description =
					"Comma-separated list of vendor names to regenerate all.json for (if not specified, all vendors are processed)",
			split = ",")
	private List<String> vendorNames;

	@Option(
			names = {"--allow-incomplete"},
			description = "Allow incomplete metadata files (missing checksums) to be included")
	private boolean allowIncomplete;

	@Override
	public Integer call() throws Exception {
		System.out.println("Java Metadata Scraper - Index");
		System.out.println("=============================");
		System.out.println("Metadata directory: " + metadataDir.toAbsolutePath());
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

		int result = generateIndices(metadataDir, vendorsToProcess, allowIncomplete);

		return result;
	}

	public static Integer generateIndices(Path metadataDir, List<String> vendorsToProcess, boolean allowIncomplete) {
		int successful = 0;
		int failed = 0;

		Path vendorDir = metadataDir.resolve("vendor");
		if (!Files.exists(vendorDir) || !Files.isDirectory(vendorDir)) {
			System.err.println("Error: Vendor directory not found: " + vendorDir.toAbsolutePath());
			return 1;
		}

		for (String vendorName : vendorsToProcess) {
			Path vendorPath = vendorDir.resolve(vendorName);
			if (!Files.exists(vendorPath) || !Files.isDirectory(vendorPath)) {
				System.err.println("Warning: Vendor directory not found: " + vendorName);
				failed++;
				continue;
			}

			try {
				MetadataUtils.generateAllJsonFromDirectory(vendorPath, allowIncomplete);
				successful++;
			} catch (Exception e) {
				System.err.println("  Failed for vendor " + vendorName + ": " + e.getMessage());
				e.printStackTrace();
				failed++;
			}
		}

		try {
			MetadataUtils.generateComprehensiveIndices(metadataDir, allowIncomplete);
		} catch (Exception e) {
			System.err.println("  Failed to generate comprehensive indices: " + e.getMessage());
			e.printStackTrace();
			failed++;
		}

		System.out.println();
		System.out.println("Index Generation Summary");
		System.out.println("========================");
		System.out.println("Total vendors: " + vendorsToProcess.size());
		System.out.println("Successful: " + successful);
		System.out.println("Failed: " + failed);

		return failed > 0 ? 1 : 0;
	}
}
