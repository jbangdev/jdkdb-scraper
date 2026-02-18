package dev.jbang.jdkdb.util;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utilities for extracting release information from JDK archive files. */
public class ArchiveUtils {
	private static final Logger logger = LoggerFactory.getLogger(ArchiveUtils.class);

	private ArchiveUtils() {
		// Utility class
	}

	/**
	 * Extract release info from a JDK archive. The release file should be in the root of the
	 * archive or inside any folder in the archive.
	 *
	 * @param archiveFile The archive file (zip, tar.gz, pkg, etc.)
	 * @param filename The filename to determine archive type
	 * @return Map of release properties, or null if not found or parsing failed
	 */
	public static Map<String, String> extractReleaseInfo(Path archiveFile, String filename) throws IOException {
		String lowerFilename = filename.toLowerCase();

		if (lowerFilename.endsWith(".zip")) {
			return extractReleaseFromZip(archiveFile);
		} else if (lowerFilename.endsWith(".tar.gz") || lowerFilename.endsWith(".tgz")) {
			return extractReleaseFromTarGz(archiveFile);
		} else if (lowerFilename.endsWith(".pkg")) {
			// PKG extraction only supported on macOS using pkgutil
			if (isMacOS()) {
				return extractReleaseFromPkg(archiveFile);
			} else {
				logger.debug("PKG file format only supported on macOS - skipping: {}", filename);
				return null;
			}
		}

		// Unsupported archive format
		return null;
	}

	/**
	 * Extract release file from ZIP archive.
	 *
	 * @param zipFile The ZIP file
	 * @return Map of release properties or null if not found
	 */
	private static Map<String, String> extractReleaseFromZip(Path zipFile) throws IOException {
		try (ZipFile zip = new ZipFile(zipFile.toFile())) {
			// Search for any file named "release" in the archive
			// This handles various layouts including macOS packages with nested structures
			var entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String name = entry.getName();

				// Check if this is a "release" file (not a directory)
				if (!entry.isDirectory() && name.endsWith("/release")) {
					// Prefer files in standard locations (shorter paths first)
					// This naturally prioritizes root or shallow release files
					return parseReleaseProperties(zip.getInputStream(entry));
				} else if (!entry.isDirectory() && name.equals("release")) {
					// Found release in root
					return parseReleaseProperties(zip.getInputStream(entry));
				}
			}
		}
		return null;
	}

	/**
	 * Extract release file from TAR.GZ archive.
	 *
	 * @param tarGzFile The TAR.GZ file
	 * @return Map of release properties or null if not found
	 */
	private static Map<String, String> extractReleaseFromTarGz(Path tarGzFile) throws IOException {
		// Search for any file named "release" in the archive
		// This handles various layouts including macOS packages with nested structures
		try (InputStream fis = Files.newInputStream(tarGzFile);
				GZIPInputStream gzis = new GZIPInputStream(fis);
				TarArchiveInputStream tis = new TarArchiveInputStream(gzis)) {

			TarArchiveEntry entry;

			while ((entry = tis.getNextEntry()) != null) {
				String name = entry.getName();

				// Check if this is a "release" file (not a directory)
				if (!entry.isDirectory() && (name.equals("release") || name.endsWith("/release"))) {
					// Found a release file - extract it
					return parseReleaseProperties(tis);
				}
			}
		}

		return null;
	}

	/**
	 * Check if we are running on macOS.
	 *
	 * @return true if running on macOS, false otherwise
	 */
	public static boolean isMacOS() {
		String osName = System.getProperty("os.name", "").toLowerCase();
		return osName.contains("mac") || osName.contains("darwin");
	}

	/**
	 * Extract release file from PKG archive using pkgutil command (macOS only).
	 * PKG files contain CPIO archives (Payload files) that need to be extracted.
	 *
	 * @param pkgFile The PKG file
	 * @return Map of release properties or null if not found
	 */
	private static Map<String, String> extractReleaseFromPkg(Path pkgFile) {
		Path tempDir = null;
		try {
			// Create temporary directory for extraction
			tempDir = Files.createTempDirectory("jdk-pkg-extract-");

			// Step 1: Extract PKG contents using pkgutil (gets full package structure)
			Process process = new ProcessBuilder(
							"pkgutil",
							"--expand-full",
							pkgFile.toAbsolutePath().toString(),
							tempDir.toAbsolutePath().toString())
					.redirectOutput(ProcessBuilder.Redirect.PIPE)
					.redirectError(ProcessBuilder.Redirect.PIPE)
					.start();

			int exitCode = process.waitFor();
			if (exitCode != 0) {
				logger.debug("pkgutil extraction failed with exit code: {}", exitCode);
				return null;
			}

			// Step 2: Search for release file in the expanded directory
			Path releaseFile = findReleaseFile(tempDir);
			if (releaseFile == null) {
				logger.debug("No release file found in PKG archive");
				return null;
			}

			// Step 3: Parse the release file
			try (InputStream is = Files.newInputStream(releaseFile)) {
				return parseReleaseProperties(is);
			}
		} catch (Exception e) {
			logger.debug("Failed to extract release from PKG file", e);
			return null;
		} finally {
			// Clean up temporary directory
			if (tempDir != null) {
				try {
					deleteDirectory(tempDir);
				} catch (IOException e) {
					logger.debug("Failed to delete temporary directory: {}", tempDir, e);
				}
			}
		}
	}

	/**
	 * Find the release file in an extracted directory.
	 *
	 * @param dir The directory to search
	 * @return Path to the release file, or null if not found
	 */
	private static Path findReleaseFile(Path dir) throws IOException {
		try (var stream = Files.walk(dir)) {
			return stream.filter(Files::isRegularFile)
					.filter(p -> {
						String name = p.getFileName().toString();
						return name.equals("release");
					})
					.findFirst()
					.orElse(null);
		}
	}

	/**
	 * Recursively delete a directory and all its contents.
	 *
	 * @param dir The directory to delete
	 */
	private static void deleteDirectory(Path dir) throws IOException {
		if (Files.exists(dir)) {
			try (var stream = Files.walk(dir)) {
				stream.sorted(Comparator.reverseOrder()).forEach(path -> {
					try {
						Files.delete(path);
					} catch (IOException e) {
						// Ignore individual file deletion failures
					}
				});
			}
		}
	}

	/**
	 * Parse release properties from an input stream.
	 *
	 * @param inputStream The input stream containing the release file content
	 * @return Map of release properties
	 */
	private static Map<String, String> parseReleaseProperties(InputStream inputStream) throws IOException {
		Properties props = new Properties();
		props.load(inputStream);

		// Convert Properties to Map<String, String>
		Map<String, String> result = new HashMap<>();
		for (String key : props.stringPropertyNames()) {
			String value = props.getProperty(key);
			// Remove surrounding quotes if present
			if (value != null && value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
				value = value.substring(1, value.length() - 1);
			}
			result.put(key, value);
		}

		return result;
	}
}
