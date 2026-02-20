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
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
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
		} else if (lowerFilename.endsWith(".tar.xz") || lowerFilename.endsWith(".txz")) {
			return extractReleaseFromTarGz(archiveFile);
		} else if (lowerFilename.endsWith(".apk")) {
			return extractReleaseFromTarGz(archiveFile);
		} else if (lowerFilename.endsWith(".pkg")) {
			// PKG extraction only supported on macOS using pkgutil
			if (isMacOS()) {
				return extractReleaseFromPkg(archiveFile);
			} else {
				logger.warn("PKG file format only supported on macOS - skipping: {}", filename);
				return null;
			}
		} else if (lowerFilename.endsWith(".rpm")) {
			return extractReleaseFromRpm(archiveFile);
		} else if (lowerFilename.endsWith(".deb")) {
			return extractReleaseFromDeb(archiveFile);
		} else if (lowerFilename.endsWith(".msi")) {
			return extractReleaseFromMsi(archiveFile);
		}

		// Unsupported archive format
		logger.info("Unsupported archive format for file: {}", filename);
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
	 * Extract release file from TAR.GZ or TAR.XZ archive.
	 *
	 * @param tarFile The TAR.GZ or TAR.XZ file
	 * @return Map of release properties or null if not found
	 */
	private static Map<String, String> extractReleaseFromTarGz(Path tarFile) throws IOException {
		// Search for any file named "release" in the archive
		// This handles various layouts including macOS packages with nested structures
		boolean isXz = tarFile.toString().toLowerCase().endsWith(".tar.xz")
				|| tarFile.toString().toLowerCase().endsWith(".txz");
		try (InputStream fis = Files.newInputStream(tarFile);
				InputStream compressedStream = isXz ? new XZCompressorInputStream(fis) : new GZIPInputStream(fis);
				TarArchiveInputStream tis = new TarArchiveInputStream(compressedStream)) {

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
	 * @throws IOException
	 */
	private static Map<String, String> extractReleaseFromPkg(Path pkgFile) throws IOException {
		Path tempDir = null;
		try {
			// Create temporary directory for extraction
			tempDir = Files.createTempDirectory("jdk-pkg-extract-");
			// pkgutil requires the destination folder to NOT exist!
			Path expandDir = tempDir.resolve("pkg-expanded");

			// Step 1: Extract PKG contents using pkgutil (gets full package structure)
			Process process = new ProcessBuilder(
							"pkgutil",
							"--expand-full",
							pkgFile.toAbsolutePath().toString(),
							expandDir.toAbsolutePath().toString())
					.redirectOutput(ProcessBuilder.Redirect.PIPE)
					.redirectError(ProcessBuilder.Redirect.PIPE)
					.start();

			try {
				int exitCode = process.waitFor();
				if (exitCode != 0) {
					logger.warn("pkgutil extraction failed with exit code: {}", exitCode);
					return null;
				}
			} catch (InterruptedException e) {
				logger.info("pkgutil extraction interrupted");
				return null;
			}

			// Step 2: Search for release file in the expanded directory
			Path releaseFile = findReleaseFile(expandDir);
			if (releaseFile == null) {
				logger.warn("No release file found in PKG archive");
				return null;
			}

			// Step 3: Parse the release file
			try (InputStream is = Files.newInputStream(releaseFile)) {
				return parseReleaseProperties(is);
			}
		} finally {
			// Clean up temporary directory
			if (tempDir != null) {
				FileUtils.deleteDirectory(tempDir);
			}
		}
	}

	/**
	 * Extract release file from RPM archive using rpm2cpio and cpio commands.
	 *
	 * @param rpmFile The RPM file
	 * @return Map of release properties or null if not found
	 * @throws IOException
	 */
	private static Map<String, String> extractReleaseFromRpm(Path rpmFile) throws IOException {
		Path tempDir = null;
		try {
			// Create temporary directory for extraction
			tempDir = Files.createTempDirectory("jdk-rpm-extract-");

			// Step 1: Convert RPM to CPIO and extract using cpio
			// rpm2cpio outputs to stdout, pipe to cpio for extraction
			ProcessBuilder pb = new ProcessBuilder(
							"/bin/sh",
							"-c",
							String.format(
									"rpm2cpio '%s' | cpio -idm 2>/dev/null",
									rpmFile.toAbsolutePath().toString()))
					.directory(tempDir.toFile())
					.redirectOutput(ProcessBuilder.Redirect.PIPE)
					.redirectError(ProcessBuilder.Redirect.PIPE);

			Process process = pb.start();

			try {
				int exitCode = process.waitFor();
				if (exitCode != 0) {
					logger.warn("RPM extraction failed with exit code: {}", exitCode);
					return null;
				}
			} catch (InterruptedException e) {
				logger.info("RPM extraction interrupted");
				return null;
			}

			// Step 2: Search for release file in the extracted directory
			Path releaseFile = findReleaseFile(tempDir);
			if (releaseFile == null) {
				logger.warn("No release file found in RPM archive");
				return null;
			}

			// Step 3: Parse the release file
			try (InputStream is = Files.newInputStream(releaseFile)) {
				return parseReleaseProperties(is);
			}
		} finally {
			// Clean up temporary directory
			if (tempDir != null) {
				FileUtils.deleteDirectory(tempDir);
			}
		}
	}

	/**
	 * Extract release file from DEB archive using ar and tar commands.
	 *
	 * @param debFile The DEB file
	 * @return Map of release properties or null if not found
	 * @throws IOException
	 */
	private static Map<String, String> extractReleaseFromDeb(Path debFile) throws IOException {
		Path tempDir = null;
		try {
			// Create temporary directory for extraction
			tempDir = Files.createTempDirectory("jdk-deb-extract-");

			// Step 1: Extract DEB archive using ar
			Process arProcess = new ProcessBuilder(
							"ar", "x", debFile.toAbsolutePath().toString())
					.directory(tempDir.toFile())
					.redirectOutput(ProcessBuilder.Redirect.PIPE)
					.redirectError(ProcessBuilder.Redirect.PIPE)
					.start();

			try {
				int exitCode = arProcess.waitFor();
				if (exitCode != 0) {
					logger.warn("DEB extraction (ar) failed with exit code: {}", exitCode);
					return null;
				}
			} catch (InterruptedException e) {
				logger.info("DEB extraction (ar) interrupted");
				return null;
			}

			// Step 2: Find the data.tar.* file
			Path dataArchive = null;
			for (String name : new String[] {"data.tar.xz", "data.tar.gz", "data.tar.bz2", "data.tar"}) {
				Path candidate = tempDir.resolve(name);
				if (Files.exists(candidate)) {
					dataArchive = candidate;
					break;
				}
			}

			if (dataArchive == null) {
				logger.warn("No data.tar.* file found in DEB archive");
				return null;
			}

			// Step 3: Extract the data archive
			Path dataExtractDir = tempDir.resolve("data");
			Files.createDirectories(dataExtractDir);

			Process tarProcess = new ProcessBuilder(
							"tar", "xf", dataArchive.toAbsolutePath().toString())
					.directory(dataExtractDir.toFile())
					.redirectOutput(ProcessBuilder.Redirect.PIPE)
					.redirectError(ProcessBuilder.Redirect.PIPE)
					.start();

			try {
				int exitCode = tarProcess.waitFor();
				if (exitCode != 0) {
					logger.warn("DEB extraction (tar) failed with exit code: {}", exitCode);
					return null;
				}
			} catch (InterruptedException e) {
				logger.info("DEB extraction (tar) interrupted");
				return null;
			}

			// Step 4: Search for release file in the extracted directory
			Path releaseFile = findReleaseFile(dataExtractDir);
			if (releaseFile == null) {
				logger.warn("No release file found in DEB archive");
				return null;
			}

			// Step 5: Parse the release file
			try (InputStream is = Files.newInputStream(releaseFile)) {
				return parseReleaseProperties(is);
			}
		} finally {
			// Clean up temporary directory
			if (tempDir != null) {
				FileUtils.deleteDirectory(tempDir);
			}
		}
	}

	/**
	 * Extract release file from MSI archive using msiextract command.
	 *
	 * @param msiFile The MSI file
	 * @return Map of release properties or null if not found
	 * @throws IOException
	 */
	private static Map<String, String> extractReleaseFromMsi(Path msiFile) throws IOException {
		Path tempDir = null;
		try {
			// Create temporary directory for extraction
			tempDir = Files.createTempDirectory("jdk-msi-extract-");

			// Step 1: Extract MSI archive using msiextract
			Process process = new ProcessBuilder(
							"msiextract",
							"-C",
							tempDir.toAbsolutePath().toString(),
							msiFile.toAbsolutePath().toString())
					.redirectOutput(ProcessBuilder.Redirect.PIPE)
					.redirectError(ProcessBuilder.Redirect.PIPE)
					.start();

			try {
				int exitCode = process.waitFor();
				if (exitCode != 0) {
					logger.warn("MSI extraction failed with exit code: {}", exitCode);
					return null;
				}
			} catch (InterruptedException e) {
				logger.info("MSI extraction interrupted");
				return null;
			}

			// Step 2: Search for release file in the extracted directory
			Path releaseFile = findReleaseFile(tempDir);
			if (releaseFile == null) {
				logger.warn("No release file found in MSI archive");
				return null;
			}

			// Step 3: Parse the release file
			try (InputStream is = Files.newInputStream(releaseFile)) {
				return parseReleaseProperties(is);
			}
		} finally {
			// Clean up temporary directory
			if (tempDir != null) {
				FileUtils.deleteDirectory(tempDir);
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
