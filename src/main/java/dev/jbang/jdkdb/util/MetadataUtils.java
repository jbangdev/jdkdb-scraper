package dev.jbang.jdkdb.util;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.MinimalPrettyPrinter;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.ScraperFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetadataUtils {
	private static final Logger logger = LoggerFactory.getLogger(MetadataUtils.class);

	public static final EnumSet<JdkMetadata.FileType> UNPACKABLE_FILE_TYPES = EnumSet.of(
			JdkMetadata.FileType.apk,
			JdkMetadata.FileType.deb,
			JdkMetadata.FileType.msi,
			JdkMetadata.FileType.pkg,
			JdkMetadata.FileType.rpm,
			JdkMetadata.FileType.tar_gz,
			JdkMetadata.FileType.tar_xz,
			JdkMetadata.FileType.zip);

	public static final EnumSet<JdkMetadata.FileType> MACOS_FILE_TYPES =
			EnumSet.of(JdkMetadata.FileType.pkg, JdkMetadata.FileType.dmg);

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
		md.metadataFile(metadataFile.toAbsolutePath());
		return md;
	}

	/**
	 * Validate metadata fields and return true if valid, false if invalid.
	 * This checks that required fields are present and that enum values
	 * are valid (or properly marked as unknown).
	 */
	public static boolean isValidMetadata(JdkMetadata metadata) {
		if (metadata.url() == null || metadata.filename() == null) {
			return false;
		}
		if (metadata.version() == null
				|| metadata.version().trim().isEmpty()
				|| !metadata.version().matches("^\\d.*")) {
			return false;
		}
		if (metadata.javaVersion() == null
				|| metadata.javaVersion().trim().isEmpty()
				|| !metadata.javaVersion().matches("^\\d.*")) {
			return false;
		}
		if (!isValidEnumOrUnknown(JdkMetadata.Os.class, metadata.os())
				|| !isValidEnum(JdkMetadata.ImageType.class, metadata.imageType())
				|| !isValidEnum(JdkMetadata.JvmImpl.class, metadata.jvmImpl())
				|| !isValidEnum(JdkMetadata.ReleaseType.class, metadata.releaseType())) {
			return false;
		}
		if (!isValidEnumOrUnknown(JdkMetadata.Arch.class, metadata.arch().replace("-", "_"))) {
			return false;
		}
		if (!isValidEnumOrUnknown(
				JdkMetadata.FileType.class, metadata.fileType().replace(".", "_"))) {
			return false;
		}
		return true;
	}

	private static <T extends Enum<T>> boolean isValidEnum(Class<T> enumClass, String value) {
		return findEnum(enumClass, value).isPresent();
	}

	private static <T extends Enum<T>> boolean isValidEnumOrUnknown(Class<T> enumClass, String value) {
		if (value == null || value.trim().isEmpty()) {
			return false;
		}
		// We will allow "unknown" values, but only if they are properly defined
		// (meaning they will be of the form "unknown-[os|architecture]-<value>")
		// We will NOT allow "unknown" and "unknown-[os|architecture]-" without
		// the value, as that would mean something was not properly normalized
		// and probably the scraper needs to be updated/fixed
		return (value.startsWith("unknown-") && !value.endsWith("-"))
				|| (value.startsWith("unknown_") && !value.endsWith("_"))
				|| findEnum(enumClass, value).isPresent();
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
			logger.warn(
					"WARNING: FOR DEBUGGING ONLY - Using lexical version sorting for {}", metadataFile.getFileName());
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
			logger.info("No metadata found to generate indices for: {}", vendorDir);
			return;
		}

		List<JdkMetadata> allMetadata = collectAllMetadata(vendorDir, 1, true, allowIncomplete);
		if (allMetadata.isEmpty()) {
			logger.info("No metadata found to generate indices for: {}", vendorDir);
			return;
		}

		// Save the combined all.json
		logger.info("Generating {}/all.json ({} entries)", vendorDir.getFileName(), allMetadata.size());
		saveMetadata(vendorDir.resolve("all.json"), allMetadata);

		// Save the combined latest.json
		logger.info("Generating {}/latest.json", vendorDir.getFileName());
		List<JdkMetadata> filteredList = filterLatestVersions(allMetadata);
		saveMetadata(vendorDir.resolve("latest.json"), filteredList);
	}

	/**
	 * Generate comprehensive indices including nested directory structures for release_type,
	 * OS, architecture, image_type, jvm_impl, and vendor
	 *
	 * @return The number of index files created
	 */
	public static int generateComprehensiveIndices(Path metadataDir, boolean allowIncomplete) throws IOException {
		logger.info("Generating comprehensive indices...");

		// Collect all metadata
		Path vendorDir = metadataDir.resolve("vendor");
		if (!Files.exists(vendorDir) || !Files.isDirectory(vendorDir)) {
			logger.info("No metadata found to generate comprehensive indices.");
			return 0;
		}

		List<JdkMetadata> allMetadata = collectAllMetadata(vendorDir, 2, true, allowIncomplete);
		if (allMetadata.isEmpty()) {
			logger.info("No metadata found to generate comprehensive indices.");
			return 0;
		}

		int fileCount = 0;

		// Generate metadata/all.json
		// (no longer needed as it is being generated in generateReleaseTypeIndices)
		// logger.info("Generating metadata/all.json ({} entries)", allMetadata.size());
		// saveMetadata(metadataDir.resolve("all.json"), allMetadata);

		// Generate metadata/latest.json
		logger.debug("Generating metadata/latest.json");
		List<JdkMetadata> filteredList = filterLatestVersions(allMetadata);
		saveMetadata(metadataDir.resolve("latest.json"), filteredList);
		fileCount++;

		fileCount += generateReleaseTypeIndices(metadataDir, metadataDir, allMetadata);

		logger.debug("Comprehensive indices generated successfully.");
		return fileCount;
	}

	private static int generateReleaseTypeIndices(Path metadataDir, Path baseDir, List<JdkMetadata> metadata)
			throws IOException {
		int fileCount = 0;

		// Group by release_type
		var byReleaseType = metadata.stream()
				.collect(java.util.stream.Collectors.groupingBy(
						md -> normalizeValue(md.releaseType(), "unknown-release-type-"),
						java.util.LinkedHashMap::new,
						java.util.stream.Collectors.toList()));

		// We add a special "all" release type that combines GA and EA releases
		byReleaseType.put("all", metadata);

		for (var releaseEntry : byReleaseType.entrySet()) {
			String releaseType = releaseEntry.getKey();

			// We allow the valid names "ga" and "ea" but also the "all" combined type
			if (!isValidEnum(JdkMetadata.ReleaseType.class, releaseType) && !releaseType.equals("all")) {
				logger.warn("Skipping invalid release type: {}", releaseType);
				continue;
			}

			List<JdkMetadata> releaseMetadata = releaseEntry.getValue();
			Path releaseDir = baseDir.resolve(releaseType);
			// First recuirsively delete existing release type directory
			// to ensure we remove old data that is no longer present
			FileUtils.deleteDirectory(releaseDir);
			// Then create a new empty release type directory
			Files.createDirectories(releaseDir);

			// Save release_type-level JSON
			logger.debug("Generating {}.json ({} entries)", releaseType, releaseMetadata.size());
			saveMetadata(baseDir.resolve(releaseType + ".json"), releaseMetadata);
			fileCount++;

			fileCount += generateOsIndices(metadataDir, releaseDir, releaseMetadata);
		}

		return fileCount;
	}

	private static int generateOsIndices(Path metadataDir, Path baseDir, List<JdkMetadata> metadata)
			throws IOException {
		int fileCount = 0;

		// Group by OS
		var byOs = metadata.stream()
				.collect(java.util.stream.Collectors.groupingBy(
						md -> normalizeValue(md.os(), "unknown-os-"),
						java.util.LinkedHashMap::new,
						java.util.stream.Collectors.toList()));

		for (var osEntry : byOs.entrySet()) {
			String os = osEntry.getKey();

			if (!isValidEnumOrUnknown(JdkMetadata.Os.class, os)) {
				logger.warn("Skipping invalid OS: {}", os);
				continue;
			}

			List<JdkMetadata> osMetadata = osEntry.getValue();
			Path osDir = baseDir.resolve(os);
			Files.createDirectories(osDir);

			// Save OS-level JSON
			Path osJsonFile = baseDir.resolve(os + ".json");
			logger.debug("Generating {} ({} entries)", metadataDir.relativize(osJsonFile), osMetadata.size());
			saveMetadata(osJsonFile, osMetadata);
			fileCount++;

			fileCount += generateArchitectureIndices(metadataDir, osDir, osMetadata);
		}

		return fileCount;
	}

	private static int generateArchitectureIndices(Path metadataDir, Path baseDir, List<JdkMetadata> osMetadata)
			throws IOException {
		int fileCount = 0;

		// Group by architecture
		var byArch = osMetadata.stream()
				.collect(java.util.stream.Collectors.groupingBy(
						md -> normalizeValue(md.arch(), "unknown-architecture-"),
						java.util.LinkedHashMap::new,
						java.util.stream.Collectors.toList()));

		for (var archEntry : byArch.entrySet()) {
			String arch = archEntry.getKey();

			if (!isValidEnumOrUnknown(JdkMetadata.Arch.class, arch.replace("-", "_"))) {
				logger.warn("Skipping invalid architecture: {}", arch);
				continue;
			}

			List<JdkMetadata> archMetadata = archEntry.getValue();
			Path archDir = baseDir.resolve(arch);
			Files.createDirectories(archDir);

			// Save architecture-level JSON
			Path archJsonFile = baseDir.resolve(arch + ".json");
			logger.debug("Generating {} ({} entries)", metadataDir.relativize(archJsonFile), archMetadata.size());
			saveMetadata(archJsonFile, archMetadata);
			fileCount++;

			fileCount += generateImageTypeIndices(metadataDir, archDir, archMetadata);
		}

		return fileCount;
	}

	private static int generateImageTypeIndices(Path metadataDir, Path baseDir, List<JdkMetadata> archMetadata)
			throws IOException {
		int fileCount = 0;

		// Group by image_type
		var byImageType = archMetadata.stream()
				.collect(java.util.stream.Collectors.groupingBy(
						md -> normalizeValue(md.imageType(), "unknown-image-type"),
						java.util.LinkedHashMap::new,
						java.util.stream.Collectors.toList()));

		// We add a special "all" image type that combines jdk and jre types
		byImageType.put("all", archMetadata);

		for (var imageEntry : byImageType.entrySet()) {
			String imageType = imageEntry.getKey();

			// We allow the valid names "jdk" and "jre" but also the "all" combined type
			if (!isValidEnum(JdkMetadata.ImageType.class, imageType) && !imageType.equals("all")) {
				logger.warn("Skipping invalid image type: {}", imageType);
				continue;
			}

			List<JdkMetadata> imageMetadata = imageEntry.getValue();
			Path imageDir = baseDir.resolve(imageType);
			Files.createDirectories(imageDir);

			// Save image_type-level JSON
			Path imageJsonFile = baseDir.resolve(imageType + ".json");
			logger.debug("Generating {} ({} entries)", metadataDir.relativize(imageJsonFile), imageMetadata.size());
			saveMetadata(imageJsonFile, imageMetadata);
			fileCount++;

			fileCount += generateJvmImplIndices(metadataDir, imageDir, imageMetadata);
		}

		return fileCount;
	}

	private static int generateJvmImplIndices(Path metadataDir, Path baseDir, List<JdkMetadata> imageMetadata)
			throws IOException {
		int fileCount = 0;

		// Group by jvm_impl
		var byJvmImpl = imageMetadata.stream()
				.collect(java.util.stream.Collectors.groupingBy(
						md -> normalizeValue(md.jvmImpl(), "unknown-jvm-impl"),
						java.util.LinkedHashMap::new,
						java.util.stream.Collectors.toList()));

		for (var jvmEntry : byJvmImpl.entrySet()) {
			String jvmImpl = jvmEntry.getKey();

			if (!isValidEnum(JdkMetadata.JvmImpl.class, jvmImpl)) {
				logger.warn("Skipping invalid JVM implementation: {}", jvmImpl);
				continue;
			}

			List<JdkMetadata> jvmMetadata = jvmEntry.getValue();
			Path jvmDir = baseDir.resolve(jvmImpl);
			Files.createDirectories(jvmDir);

			// Save jvm_impl-level JSON
			Path jvmJsonFile = baseDir.resolve(jvmImpl + ".json");
			logger.debug("Generating {} ({} entries)", metadataDir.relativize(jvmJsonFile), jvmMetadata.size());
			saveMetadata(jvmJsonFile, jvmMetadata);
			fileCount++;

			fileCount += generateVendorIndices(metadataDir, jvmDir, jvmMetadata);
		}

		return fileCount;
	}

	private static int generateVendorIndices(Path metadataDir, Path baseDir, List<JdkMetadata> jvmMetadata)
			throws IOException {
		int fileCount = 0;

		// Group by vendor
		var byVendor = jvmMetadata.stream()
				.collect(Collectors.groupingBy(
						md -> normalizeValue(md.vendor(), "unknown-vendor"), LinkedHashMap::new, Collectors.toList()));

		// We add a special "all" and "latest" vendor
		byVendor.put("all", jvmMetadata);
		byVendor.put("latest", filterLatestVersions(jvmMetadata));

		Set<String> allVendors = ScraperFactory.getAvailableScraperDiscoveries().values().stream()
				.map(v -> v.vendor())
				.collect(Collectors.toSet());

		for (var vendorEntry : byVendor.entrySet()) {
			String vendor = vendorEntry.getKey();

			if (!allVendors.contains(vendor)
					&& !vendor.startsWith("unknown-vendor-")
					&& !vendor.equals("all")
					&& !vendor.equals("latest")) {
				logger.warn("Skipping invalid vendor: {}", vendor);
				continue;
			}

			List<JdkMetadata> vendorMetadata = vendorEntry.getValue();

			// Save vendor.json
			Path vendorJsonFile = baseDir.resolve(vendor + ".json");
			logger.debug("Generating {} ({} entries)", metadataDir.relativize(vendorJsonFile), vendorMetadata.size());
			saveMetadata(vendorJsonFile, vendorMetadata);
			fileCount++;

			// Save vendor-latest.json
			Path vendorLatestJsonFile = baseDir.resolve(vendor + "-latest.json");
			logger.debug(
					"Generating {} ({} entries)", metadataDir.relativize(vendorLatestJsonFile), vendorMetadata.size());
			saveMetadata(vendorLatestJsonFile, filterLatestVersions(vendorMetadata));
			fileCount++;
		}

		return fileCount;
	}

	/**
	 * Extract the major version number from a java_version string.
	 * Takes the first digits from java_version up to the end or the first non-digit.
	 *
	 * @param javaVersion The java_version string (e.g., "17", "11.0.1", "8u302")
	 * @return The major version as a string (e.g., "17", "11", "8")
	 */
	private static String extractMajorVersion(String javaVersion) {
		if (javaVersion == null || javaVersion.isEmpty()) {
			return "unknown";
		}

		StringBuilder majorVersion = new StringBuilder();
		for (char c : javaVersion.toCharArray()) {
			if (Character.isDigit(c)) {
				majorVersion.append(c);
			} else {
				break;
			}
		}

		return majorVersion.length() > 0 ? majorVersion.toString() : "unknown";
	}

	/**
	 * Filter metadata to keep only the latest version per major version for each unique group.
	 * Groups by: os, architecture, image_type, jvm_impl, vendor, major_version
	 * Special handling: If both GA and EA exist for the same version, only GA is kept.
	 * All assets for the winning version are included in the result.
	 *
	 * @param metadataList The list of metadata to filter
	 * @return Filtered list containing all assets for the latest version per major version per group
	 */
	private static List<JdkMetadata> filterLatestVersions(List<JdkMetadata> metadataList) {
		// Group by all the required fields including major version
		var grouped = metadataList.stream().collect(Collectors.groupingBy(md -> {
			String os = normalizeValue(md.os(), "unknown-os");
			String arch = normalizeValue(md.arch(), "unknown-arch");
			String imageType = normalizeValue(md.imageType(), "unknown-image-type");
			String jvmImpl = normalizeValue(md.jvmImpl(), "unknown-jvm-impl");
			String vendor = normalizeValue(md.vendor(), "unknown-vendor");
			String majorVersion = extractMajorVersion(md.javaVersion());

			return String.format("%s|%s|%s|%s|%s|%s", os, arch, imageType, jvmImpl, vendor, majorVersion);
		}));

		List<JdkMetadata> result = new ArrayList<>();

		// For each group (which represents a unique major version + platform combination)
		for (var group : grouped.values()) {
			// Sort by release_type first (ga before ea), then by version (descending, latest first)
			var sorted = group.stream()
					.sorted(Comparator.<JdkMetadata, String>comparing(md -> md.releaseType(), Comparator.reverseOrder())
							.thenComparing(md -> md.version(), VersionComparator.INSTANCE.reversed()))
					.toList();

			if (!sorted.isEmpty()) {
				// The first element is the winner (highest version, GA preferred over EA)
				JdkMetadata winner = sorted.get(0);
				String winningVersion = winner.version();
				String winningReleaseType = winner.releaseType();

				// Add all assets that match the winning version and release type
				group.stream()
						.filter(md -> md.version().equals(winningVersion)
								&& md.releaseType().equals(winningReleaseType))
						.forEach(result::add);
			}
		}

		return result;
	}

	/**
	 * Collect all metadata from the given directory and subdirectories, excluding all.json and latest.json.
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
					.filter(p -> !p.getFileName().toString().equals("latest.json"))
					.forEach(metadataFile -> {
						try {
							JdkMetadata metadata = readMetadataFile(metadataFile);
							boolean isIncomplete = MetadataUtils.hasMissingChecksums(metadata)
									|| MetadataUtils.hasMissingReleaseInfo(metadata);
							if ((includeComplete && !isIncomplete) || (includeIncomplete && isIncomplete)) {
								allMetadata.add(metadata);
							}
						} catch (IOException e) {
							logger.error("Failed to read metadata file: {} - {}", metadataFile, e.getMessage());
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

		if (!isValidEnum(JdkMetadata.FileType.class, metadata.fileType())) {
			// For unknown file types we can't reliably determine if the release
			// info is missing or not, so we will not consider it incomplete
			return false;
		}

		if (!UNPACKABLE_FILE_TYPES.contains(metadata.fileTypeEnum())) {
			// For non-unpackable file types we can't extract release info,
			// so we will not consider it incomplete
			return false;
		}

		// Check if any of the primary release info fields is missing
		return metadata.releaseInfo() == null;
	}

	public static <T extends Enum<T>> Optional<T> findEnum(Class<T> enumClass, String value) {
		try {
			return Optional.of(Enum.valueOf(enumClass, value));
		} catch (IllegalArgumentException | NullPointerException e) {
			return Optional.empty();
		}
	}
}
