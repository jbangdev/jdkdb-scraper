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
	/** Save all metadata and create combined all.json file.
	 * @param lexicalSort if true, compare versions lexically (string order) for comparable all.json; if false, use numerical version order. Sort is always version then filename. */
	public static void saveMetadata(Path metadataDir, List<JdkMetadata> metadataList, boolean lexicalSort)
			throws IOException {
		Comparator<JdkMetadata> versionThenFilename = lexicalSort
				? Comparator.<JdkMetadata, String>comparing(md -> md.version())
						.thenComparing(md -> md.metadataFilename())
				: Comparator.<JdkMetadata, String>comparing(md -> md.version(), VersionComparator.INSTANCE)
						.thenComparing(md -> md.metadataFilename());
		List<JdkMetadata> sortedList =
				metadataList.stream().sorted(versionThenFilename).toList();

		// Create all.json
		if (!sortedList.isEmpty()) {
			Path allJsonPath = metadataDir.resolve("all.json");

			try (var writer = Files.newBufferedWriter(allJsonPath)) {
				writeAllMapper.writeValue(writer, sortedList);
				// This is to ensure we write the files exactly as the original code did
				writer.write("\n");
			}
		}
	}

	/**
	 * Generate all.json file from all .json files in the vendor directory. This reads all individual
	 * metadata files (excluding all.json itself) and creates a combined all.json file.
	 * @param lexicalSort if true, compare versions lexically for comparable output
	 */
	public static void generateAllJsonFromDirectory(Path vendorDir, boolean lexicalSort) throws IOException {
		if (!Files.exists(vendorDir) || !Files.isDirectory(vendorDir)) {
			return;
		}

		List<JdkMetadata> metadataList = new ArrayList<>();

		// Read all .json files except all.json
		try (Stream<Path> files = Files.list(vendorDir)) {
			files.filter(p -> p.getFileName().toString().endsWith(".json"))
					.filter(p -> !p.getFileName().toString().equals("all.json"))
					.forEach(metadataFile -> {
						try {
							JdkMetadata metadata = readMetadataFile(metadataFile);
							metadataList.add(metadata);
						} catch (IOException e) {
							// Log or handle parse errors, but continue processing other files
							System.err.println(
									"Failed to read metadata file: " + metadataFile + " - " + e.getMessage());
						}
					});
		}

		// Save the combined all.json
		if (!metadataList.isEmpty()) {
			saveMetadata(vendorDir, metadataList, lexicalSort);
		}
	}
}
