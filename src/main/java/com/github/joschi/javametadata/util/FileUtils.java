package com.github.joschi.javametadata.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Utility class for file operations */
public class FileUtils {

    /** Ensure a directory exists, creating it if necessary */
    public static void ensureDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }
    }

    /** Get the size of a file in bytes */
    public static long getFileSize(Path file) throws IOException {
        return Files.size(file);
    }
}
