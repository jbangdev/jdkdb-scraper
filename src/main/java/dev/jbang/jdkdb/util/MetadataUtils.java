package dev.jbang.jdkdb.util;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.MinimalPrettyPrinter;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.jbang.jdkdb.model.JdkMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class MetadataUtils {
	private static ObjectMapper readMapper = new ObjectMapper();

	private static ObjectMapper writeOneMapper =
			new ObjectMapper().configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);

	private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)([dwmy])$");

	// Custom pretty printer with proper formatting
	private static MinimalPrettyPrinter printer = new MinimalPrettyPrinter() {
		private int depth = 0;

		private void indent(JsonGenerator g) throws IOException {
			g.writeRaw("\n");
			for (int i = 0; i < depth; i++) {
				g.writeRaw("  ");
			}
		}

		@Override
		public void writeStartArray(JsonGenerator g) throws IOException {
			g.writeRaw('[');
			depth++;
		}

		@Override
		public void beforeArrayValues(JsonGenerator g) throws IOException {
			indent(g);
		}

		@Override
		public void writeArrayValueSeparator(JsonGenerator g) throws IOException {
			g.writeRaw(',');
			indent(g);
		}

		@Override
		public void writeEndArray(JsonGenerator g, int nrOfValues) throws IOException {
			depth--;
			if (nrOfValues > 0) {
				indent(g);
			}
			g.writeRaw(']');
		}

		@Override
		public void writeStartObject(JsonGenerator g) throws IOException {
			g.writeRaw('{');
			depth++;
		}

		@Override
		public void beforeObjectEntries(JsonGenerator g) throws IOException {
			indent(g);
		}

		@Override
		public void writeObjectFieldValueSeparator(JsonGenerator g) throws IOException {
			g.writeRaw(": ");
		}

		@Override
		public void writeObjectEntrySeparator(JsonGenerator g) throws IOException {
			g.writeRaw(',');
			indent(g);
		}

		@Override
		public void writeEndObject(JsonGenerator g, int nrOfEntries) throws IOException {
			depth--;
			if (nrOfEntries > 0) {
				indent(g);
			}
			g.writeRaw('}');
		}
	};

	static {
		printer.setRootValueSeparator("\n");
	}

	// Use a mix-in to override the JsonPropertyOrder annotation
	private static ObjectMapper writeAllMapper = JsonMapper.builder()
			.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)
			.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
			.enable(SerializationFeature.INDENT_OUTPUT)
			.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
			.defaultPrettyPrinter(printer)
			.build();

	// Add a mix-in to override the @JsonPropertyOrder annotation
	static {
		writeAllMapper.addMixIn(JdkMetadata.class, AlphabeticPropertyOrder.class);
	}

	/** Mix-in to force alphabetical property ordering */
	@JsonPropertyOrder(alphabetic = true)
	private interface AlphabeticPropertyOrder {}

	public static JdkMetadata readMetadataFile(Path metadataFile) throws IOException {
		JdkMetadata md = readMapper.readValue(metadataFile.toFile(), JdkMetadata.class);
		md.metadataFile(metadataFile);
		return md;
	}

	/** Save individual metadata to file */
	public static void saveMetadataFile(Path metadataFile, JdkMetadata metadata) throws IOException {
		try (var writer = Files.newBufferedWriter(metadataFile)) {
			writeOneMapper.writeValue(writer, metadata);
			// This is to ensure we write the files exactly as the original code did
			writer.write("\n");
		}
	}
	/** Save all metadata and create combined all.json file */
	public static void saveMetadata(Path metadataFile, List<JdkMetadata> metadataList) throws IOException {
		// Sort by version first (using VersionComparator) and filename second
		Comparator<JdkMetadata> comparator = Comparator.<JdkMetadata, String>comparing(
						md -> md.version(), VersionComparator.INSTANCE)
				.thenComparing(md -> md.metadataFile().getFileName().toString());

		// Only for debugging purposes
		if (System.getProperty("index.sort", "numerical").equalsIgnoreCase("lexical")) {
			System.out.println(
					"WARNING: FOR DEBUGGING ONLY - Using lexical version sorting for " + metadataFile.getFileName());
			comparator = Comparator.<JdkMetadata, String>comparing(
					md -> md.metadataFile().getFileName().toString());
		}

		List<JdkMetadata> sortedList = metadataList.stream().sorted(comparator).toList();

		// Clear release_info from all metadata entries before writing lists!
		for (JdkMetadata md : sortedList) {
			md.releaseInfo(null);
		}

		// Create all.json
		if (!sortedList.isEmpty()) {
			try (var writer = Files.newBufferedWriter(metadataFile)) {
				writeAllMapper.writeValue(writer, sortedList);
				// This is to ensure we write the files exactly as the original code did
				writer.write("\n");
			}
		}
	}

	/**
	 * Generate all.json file from all .json files in the vendor directory. This reads all individual
	 * metadata files (excluding all.json itself) and creates a combined all.json file.
	 */
	public static void generateAllJsonFromDirectory(Path vendorDir, boolean allowIncomplete) throws IOException {
		// Collect all metadata
		if (!Files.exists(vendorDir) || !Files.isDirectory(vendorDir)) {
			System.out.println("No metadata found to generate indices for: " + vendorDir);
			return;
		}

		List<JdkMetadata> allMetadata = collectAllMetadata(vendorDir, 1, true, allowIncomplete);
		if (allMetadata.isEmpty()) {
			System.out.println("No metadata found to generate indices for: " + vendorDir);
			return;
		}

		// Save the combined all.json
		System.out.println("Generating " + vendorDir.getFileName() + "/all.json (" + allMetadata.size() + " entries)");
		saveMetadata(vendorDir.resolve("all.json"), allMetadata);
	}

	/**
	 * Generate comprehensive indices including nested directory structures for release_type,
	 * OS, architecture, image_type, jvm_impl, and vendor
	 */
	public static void generateComprehensiveIndices(Path metadataDir, boolean allowIncomplete) throws IOException {
		System.out.println("Generating comprehensive indices...");

		// Collect all metadata
		Path vendorDir = metadataDir.resolve("vendor");
		if (!Files.exists(vendorDir) || !Files.isDirectory(vendorDir)) {
			System.out.println("No metadata found to generate comprehensive indices.");
			return;
		}

		List<JdkMetadata> allMetadata = collectAllMetadata(vendorDir, 2, true, allowIncomplete);
		if (allMetadata.isEmpty()) {
			System.out.println("No metadata found to generate comprehensive indices.");
			return;
		}

		// Generate metadata/all.json
		System.out.println("Generating metadata/all.json (" + allMetadata.size() + " entries)");
		saveMetadata(metadataDir.resolve("all.json"), allMetadata);

		generateReleaseTypeIndices(metadataDir, metadataDir, allMetadata);

		System.out.println("Comprehensive indices generated successfully.");
	}

	private static void generateReleaseTypeIndices(Path metadataDir, Path baseDir, List<JdkMetadata> metadata)
			throws IOException {
		// Group by release_type
		var byReleaseType = metadata.stream()
				.collect(java.util.stream.Collectors.groupingBy(
						md -> normalizeValue(md.releaseType(), "unknown-release-type-"),
						java.util.LinkedHashMap::new,
						java.util.stream.Collectors.toList()));

		for (var releaseEntry : byReleaseType.entrySet()) {
			String releaseType = releaseEntry.getKey();
			List<JdkMetadata> releaseMetadata = releaseEntry.getValue();
			Path releaseDir = baseDir.resolve(releaseType);
			Files.createDirectories(releaseDir);

			// Save release_type-level JSON
			System.out.println("Generating " + releaseType + ".json (" + releaseMetadata.size() + " entries)");
			saveMetadata(baseDir.resolve(releaseType + ".json"), releaseMetadata);

			generateOsIndices(metadataDir, releaseDir, releaseMetadata);
		}
	}

	private static void generateOsIndices(Path metadataDir, Path baseDir, List<JdkMetadata> metadata)
			throws IOException {
		// Group by OS
		var byOs = metadata.stream()
				.collect(java.util.stream.Collectors.groupingBy(
						md -> normalizeValue(md.os(), "unknown-os-"),
						java.util.LinkedHashMap::new,
						java.util.stream.Collectors.toList()));

		for (var osEntry : byOs.entrySet()) {
			String os = osEntry.getKey();
			List<JdkMetadata> osMetadata = osEntry.getValue();
			Path osDir = baseDir.resolve(os);
			Files.createDirectories(osDir);

			// Save OS-level JSON
			Path osJsonFile = baseDir.resolve(os + ".json");
			System.out.println(
					"Generating " + metadataDir.relativize(osJsonFile) + " (" + osMetadata.size() + " entries)");
			saveMetadata(osJsonFile, osMetadata);

			generateArchitectureIndices(metadataDir, osDir, osMetadata);
		}
	}

	private static void generateArchitectureIndices(Path metadataDir, Path baseDir, List<JdkMetadata> osMetadata)
			throws IOException {
		// Group by architecture
		var byArch = osMetadata.stream()
				.collect(java.util.stream.Collectors.groupingBy(
						md -> normalizeValue(md.arch(), "unknown-architecture-"),
						java.util.LinkedHashMap::new,
						java.util.stream.Collectors.toList()));

		for (var archEntry : byArch.entrySet()) {
			String arch = archEntry.getKey();
			List<JdkMetadata> archMetadata = archEntry.getValue();
			Path archDir = baseDir.resolve(arch);
			Files.createDirectories(archDir);

			// Save architecture-level JSON
			Path archJsonFile = baseDir.resolve(arch + ".json");
			System.out.println(
					"Generating " + metadataDir.relativize(archJsonFile) + " (" + archMetadata.size() + " entries)");
			saveMetadata(archJsonFile, archMetadata);

			generateImageTypeIndices(metadataDir, archDir, archMetadata);
		}
	}

	private static void generateImageTypeIndices(Path metadataDir, Path baseDir, List<JdkMetadata> archMetadata)
			throws IOException {
		// Group by image_type
		var byImageType = archMetadata.stream()
				.collect(java.util.stream.Collectors.groupingBy(
						md -> normalizeValue(md.imageType(), "unknown-image-type"),
						java.util.LinkedHashMap::new,
						java.util.stream.Collectors.toList()));

		for (var imageEntry : byImageType.entrySet()) {
			String imageType = imageEntry.getKey();
			List<JdkMetadata> imageMetadata = imageEntry.getValue();
			Path imageDir = baseDir.resolve(imageType);
			Files.createDirectories(imageDir);

			// Save image_type-level JSON
			Path imageJsonFile = baseDir.resolve(imageType + ".json");
			System.out.println(
					"Generating " + metadataDir.relativize(imageJsonFile) + " (" + imageMetadata.size() + " entries)");
			saveMetadata(imageJsonFile, imageMetadata);

			generateJvmImplIndices(metadataDir, imageDir, imageMetadata);
		}
	}

	private static void generateJvmImplIndices(Path metadataDir, Path baseDir, List<JdkMetadata> imageMetadata)
			throws IOException {
		// Group by jvm_impl
		var byJvmImpl = imageMetadata.stream()
				.collect(java.util.stream.Collectors.groupingBy(
						md -> normalizeValue(md.jvmImpl(), "unknown-jvm-impl"),
						java.util.LinkedHashMap::new,
						java.util.stream.Collectors.toList()));

		for (var jvmEntry : byJvmImpl.entrySet()) {
			String jvmImpl = jvmEntry.getKey();
			List<JdkMetadata> jvmMetadata = jvmEntry.getValue();
			Path jvmDir = baseDir.resolve(jvmImpl);
			Files.createDirectories(jvmDir);

			// Save jvm_impl-level JSON
			Path jvmJsonFile = baseDir.resolve(jvmImpl + ".json");
			System.out.println(
					"Generating " + metadataDir.relativize(jvmJsonFile) + " (" + jvmMetadata.size() + " entries)");
			saveMetadata(jvmJsonFile, jvmMetadata);

			generateVendorIndices(metadataDir, jvmDir, jvmMetadata);
		}
	}

	private static void generateVendorIndices(Path metadataDir, Path baseDir, List<JdkMetadata> jvmMetadata)
			throws IOException {
		// Group by vendor
		var byVendor = jvmMetadata.stream()
				.collect(java.util.stream.Collectors.groupingBy(
						md -> normalizeValue(md.vendor(), "unknown-vendor"),
						java.util.LinkedHashMap::new,
						java.util.stream.Collectors.toList()));

		for (var vendorEntry : byVendor.entrySet()) {
			String vendor = vendorEntry.getKey();
			List<JdkMetadata> vendorMetadata = vendorEntry.getValue();

			// Save vendor-level JSON
			Path vendorJsonFile = baseDir.resolve(vendor + ".json");
			System.out.println("Generating " + metadataDir.relativize(vendorJsonFile) + " (" + vendorMetadata.size()
					+ " entries)");
			saveMetadata(vendorJsonFile, vendorMetadata);
		}
	}

	/**
	 * Collect all metadata from the given directory and subdirectories, excluding all.json.
	 *
	 * @param dir The directory to search for metadata files
	 * @param maxDepth The maximum depth to search for metadata files
	 * @param includeComplete If true, include metadata entries that have checksum values and release info
	 * @param includeIncomplete If true, include metadata entries that are missing checksum values or release info
	 */
	public static List<JdkMetadata> collectAllMetadata(
			Path dir, int maxDepth, boolean includeComplete, boolean includeIncomplete) throws IOException {
		List<JdkMetadata> allMetadata = new ArrayList<>();

		try (Stream<Path> paths = Files.walk(dir, maxDepth)) {
			paths.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().endsWith(".json"))
					.filter(p -> !p.getFileName().toString().equals("all.json"))
					.forEach(metadataFile -> {
						try {
							JdkMetadata metadata = readMetadataFile(metadataFile);
							boolean isIncomplete = MetadataUtils.hasMissingChecksums(metadata)
									|| MetadataUtils.hasMissingReleaseInfo(metadata);
							if ((includeComplete && !isIncomplete) || (includeIncomplete && isIncomplete)) {
								allMetadata.add(metadata);
							}
						} catch (IOException e) {
							System.err.println(
									"Failed to read metadata file: " + metadataFile + " - " + e.getMessage());
						}
					});
		}

		return allMetadata;
	}

	/**
	 * Normalize a value, replacing null or empty strings with a default value
	 */
	private static String normalizeValue(String value, String defaultValue) {
		if (value == null || value.trim().isEmpty()) {
			return defaultValue;
		}
		return value.trim();
	}

	/**
	 * Parse duration string (e.g., "30d", "3w", "6m", "1y") into Duration.
	 *
	 * @param durationStr Duration string with format [number][d|w|m|y]
	 * @return Duration object or null if format is invalid
	 */
	public static Duration parseDuration(String durationStr) {
		Matcher matcher = DURATION_PATTERN.matcher(durationStr.toLowerCase());
		if (!matcher.matches()) {
			return null;
		}

		long amount = Long.parseLong(matcher.group(1));
		String unit = matcher.group(2);

		return switch (unit) {
			case "d" -> Duration.ofDays(amount);
			case "w" -> Duration.ofDays(amount * 7);
			case "m" -> Duration.ofDays(amount * 30); // Approximate month as 30 days
			case "y" -> Duration.ofDays(amount * 365); // Approximate year as 365 days
			default -> null;
		};
	}

	/**
	 * Check if metadata has missing checksums.
	 *
	 * @param metadata The metadata to check
	 * @return true if any of the checksums (md5, sha1, sha256, sha512) are missing
	 */
	public static boolean hasMissingChecksums(JdkMetadata metadata) {
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

	/**
	 * Check if metadata has missing checksums.
	 *
	 * @param metadata The metadata to check
	 * @return true if the release info is missing
	 */
	public static boolean hasMissingReleaseInfo(JdkMetadata metadata) {
		// Only check files that have a URL (otherwise we can't download them)
		if (metadata.url() == null || metadata.filename() == null) {
			return false;
		}

		// Check if any of the primary release info fields is missing
		return metadata.releaseInfo() == null;
	}
}
