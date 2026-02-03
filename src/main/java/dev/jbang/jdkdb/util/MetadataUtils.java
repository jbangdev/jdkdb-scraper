package dev.jbang.jdkdb.util;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.jbang.jdkdb.model.JdkMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class MetadataUtils {

	/** Mix-in to force alphabetical property ordering */
	@JsonPropertyOrder(alphabetic = true)
	private interface AlphabeticPropertyOrder {}

	/** Save individual metadata to file */
	public static void saveMetadataFile(Path metadataDir, JdkMetadata metadata) throws IOException {
		Path metadataFile = metadataDir.resolve(metadata.getFilename() + ".json");
		try (var writer = Files.newBufferedWriter(metadataFile)) {
			ObjectMapper objectMapper = new ObjectMapper().configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
			objectMapper.writeValue(writer, metadata);
			// This is to ensure we write the files exactly as the original code did
			writer.write("\n");
		}
	}

	/** Save all metadata and create combined all.json file */
	public static void saveMetadata(Path metadataDir, List<JdkMetadata> metadataList) throws IOException {
		// Create all.json
		if (!metadataList.isEmpty()) {
			Path allJsonPath = metadataDir.resolve("all.json");

			DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
			DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
			printer.indentObjectsWith(indenter);
			printer.indentArraysWith(indenter);

			try (var writer = Files.newBufferedWriter(allJsonPath)) {
				// Use a mix-in to override the JsonPropertyOrder annotation
				ObjectMapper objectMapper = JsonMapper.builder()
						.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)
						.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
						.enable(SerializationFeature.INDENT_OUTPUT)
						.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
						.defaultPrettyPrinter(printer)
						.build();

				// Add a mix-in to override the @JsonPropertyOrder annotation
				objectMapper.addMixIn(JdkMetadata.class, AlphabeticPropertyOrder.class);

				objectMapper.writeValue(writer, metadataList);
				// This is to ensure we write the files exactly as the original code did
				writer.write("\n");
			}
		}
	}
}
