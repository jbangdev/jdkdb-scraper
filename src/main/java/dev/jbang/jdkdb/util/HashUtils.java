package dev.jbang.jdkdb.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Utility class for computing file hashes */
public class HashUtils {

	/**
	 * Compute hash for a file
	 *
	 * @param file The file to hash
	 * @param algorithm The hash algorithm (MD5, SHA-1, SHA-256, SHA-512)
	 * @return The hex-encoded hash
	 */
	public static String computeHash(Path file, String algorithm) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance(algorithm);

			try (InputStream is = Files.newInputStream(file)) {
				byte[] buffer = new byte[8192];
				int read;
				while ((read = is.read(buffer)) > 0) {
					digest.update(buffer, 0, read);
				}
			}

			return bytesToHex(digest.digest());

		} catch (NoSuchAlgorithmException e) {
			throw new IOException("Hash algorithm not supported: " + algorithm, e);
		}
	}

	/** Convert byte array to hex string */
	private static String bytesToHex(byte[] bytes) {
		StringBuilder result = new StringBuilder();
		for (byte b : bytes) {
			result.append(String.format("%02x", b));
		}
		return result.toString();
	}
}
