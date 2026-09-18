package dev.jbang.jdkdb;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.model.JdkMetadataOld;
import dev.jbang.jdkdb.util.MetadataUtils;
import java.io.IOException;
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

/** Convert all json files to latest format */
@Command(name = "convert", description = "Convert all json files to latest format", mixinStandardHelpOptions = true)
public class ConvertCommand implements Callable<Integer> {
	private static final Logger logger = LoggerFactory.getLogger("command");
	private static final ObjectMapper readMapper = new ObjectMapper();

	@Option(
			names = {"-m", "--metadata-dir"},
			description = "Directory containing metadata files (default: db/metadata)",
			defaultValue = "db/metadata")
	private Path metadataDir;

	@Option(
			names = {"--delete-invalid"},
			description = "Delete invalid metadata files")
	private boolean deleteInvalid;

	@Option(
			names = {"--dry-run"},
			description = "Show statistics without actually converting files")
	private boolean dryRun;

	@Override
	public Integer call() throws Exception {
		logger.info("Java Metadata Scraper - Convert");
		logger.info("===============================");
		logger.info("Metadata directory: {}", metadataDir.toAbsolutePath());
		logger.info("");

		Path distroDir = metadataDir;
		if (!Files.exists(distroDir) || !Files.isDirectory(distroDir)) {
			logger.error("Error: Distro directory not found: {}", distroDir.toAbsolutePath());
			return 1;
		}

		logger.info("Converting all files...");
		logger.info("");

		int result = convert(metadataDir);

		return result;
	}

	public Integer convert(Path metadataDir) throws IOException {
		int processed = 0;
		int successful = 0;
		int failed = 0;

		Path distroDir = metadataDir;
		if (!Files.exists(distroDir) || !Files.isDirectory(distroDir)) {
			logger.error("Error: Distro directory not found: {}", distroDir.toAbsolutePath());
			return 1;
		}

		List<JdkMetadataOld> allMetadata = collectAllOldMetadata(distroDir, 3);
		if (allMetadata.isEmpty()) {
			logger.info("No metadata found to generate indices for: {}", distroDir);
			return 1;
		}

		for (JdkMetadataOld md : allMetadata) {
			processed++;
			try {
				if (md.isValid()) {
					JdkMetadata newmd = JdkMetadata.create()
							.setArchitecture(md.getArchitecture())
							.setDistro(md.getVendor())
							.setFeatures(md.getFeatures())
							.setFilename(md.getFilename())
							.setFileType(md.getFileType())
							.setImageType(md.getImageType())
							.setJavaVersion(md.getJavaVersion())
							.setJvmImpl(md.getJvmImpl())
							.setOs(md.getOs())
							.setReleaseType(md.getReleaseType())
							.setUrl(md.getUrl())
							.setVendor(MetadataUtils.getDistroVendorMapping().get(md.getVendor()))
							.setVersion(md.getVersion())
							.setMd5(md.getMd5())
							.setSha1(md.getSha1())
							.setSha256(md.getSha256())
							.setSha512(md.getSha512())
							.setSize(md.getSize())
							.setReleaseInfo(md.getReleaseInfo())
							.metadataFile(md.metadataFile());
					if (newmd.isValid()) {
						if (!dryRun) {
							// Save converted metadata back to file
							MetadataUtils.saveMetadataFile(md.metadataFile(), newmd);
						}
						successful++;
					} else {
						failed++;
						logger.warn("Skipping invalid metadata after conversion: {}", md.metadataFile());
					}
				} else {
					failed++;
					if (deleteInvalid) {
						try {
							if (!dryRun) {
								Files.deleteIfExists(md.metadataFile());
							}
							logger.warn("Deleted invalid metadata file: {}", md.metadataFile());
						} catch (IOException e) {
							logger.error(
									"Failed to delete invalid metadata file: {} - {}",
									md.metadataFile(),
									e.getMessage());
						}
					} else {
						logger.warn("Skipping invalid metadata file: {}", md.metadataFile());
					}
				}
			} catch (Exception e) {
				logger.error(
						"Failed to convert metadata for {} {}: {}", md.getVendor(), md.getVersion(), e.getMessage());
				failed++;
			}
		}

		logger.info("Convert Summary");
		logger.info("===============");
		logger.info("Files processed: {}", processed);
		logger.info("Files converted: {}", successful);
		logger.info("Files failed: {}", failed);

		return 0;
	}

	public static List<JdkMetadataOld> collectAllOldMetadata(Path dir, int maxDepth) throws IOException {
		List<JdkMetadataOld> allMetadata = new ArrayList<>();

		try (Stream<Path> paths = Files.walk(dir, maxDepth)) {
			paths.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().endsWith(".json"))
					.filter(p -> !p.getFileName().toString().equals("all.json"))
					.filter(p -> !p.getFileName().toString().equals("latest.json"))
					.forEach(metadataFile -> {
						try {
							JdkMetadataOld md = readOldMetadataFile(metadataFile);
							allMetadata.add(md);
						} catch (IOException e) {
							logger.error("Failed to read metadata file: {} - {}", metadataFile, e.getMessage());
						}
					});
		}

		return allMetadata;
	}

	public static JdkMetadataOld readOldMetadataFile(Path metadataFile) throws IOException {
		JdkMetadataOld md = readMapper.readValue(metadataFile.toFile(), JdkMetadataOld.class);
		md.metadataFile(metadataFile.toAbsolutePath());
		return md;
	}
}
