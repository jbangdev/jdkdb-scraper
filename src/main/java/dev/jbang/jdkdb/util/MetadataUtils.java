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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class MetadataUtils {
	private static ObjectMapper readMapper = new ObjectMapper();

	private static ObjectMapper writeOneMapper =
			new ObjectMapper().configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);

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
		md.metadataFilename(metadataFile.getFileName().toString());
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
		List<JdkMetadata> sortedList = metadataList.stream()
				.sorted(Comparator.<JdkMetadata, String>comparing(md -> md.version(), VersionComparator.INSTANCE)
						.thenComparing(md -> md.metadataFilename()))
				.toList();

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

		List<JdkMetadata> allMetadata = collectAllMetadata(vendorDir, allowIncomplete);
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

		List<JdkMetadata> allMetadata = collectAllMetadata(vendorDir, allowIncomplete);
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
	 * @param allowIncomplete If true, include metadata entries that are missing checksum values
	 */
	private static List<JdkMetadata> collectAllMetadata(Path dir, boolean allowIncomplete) throws IOException {
		return collectAllMetadata(dir, 2, allowIncomplete);
	}

	/**
	 * Collect all metadata from the given directory and subdirectories, excluding all.json.
	 *
	 * @param dir The directory to search for metadata files
	 * @param maxDepth The maximum depth to search for metadata files
	 * @param allowIncomplete If true, include metadata entries that are missing checksum values
	 */
	private static List<JdkMetadata> collectAllMetadata(Path dir, int maxDepth, boolean allowIncomplete)
			throws IOException {
		List<JdkMetadata> allMetadata = new ArrayList<>();

		try (Stream<Path> paths = Files.walk(dir, maxDepth)) {
			paths.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().endsWith(".json"))
					.filter(p -> !p.getFileName().toString().equals("all.json"))
					.forEach(metadataFile -> {
						try {
							JdkMetadata metadata = readMetadataFile(metadataFile);
							if (!allowIncomplete
									&& (metadata.md5() == null
											|| metadata.sha1() == null
											|| metadata.sha256() == null
											|| metadata.sha512() == null)) {
								// Skip incomplete metadata
								return;
							}
							allMetadata.add(metadata);
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
}
