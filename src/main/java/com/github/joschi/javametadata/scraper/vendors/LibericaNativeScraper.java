package com.github.joschi.javametadata.scraper.vendors;

import com.github.joschi.javametadata.scraper.Scraper;
import com.github.joschi.javametadata.model.JdkMetadata;
import com.github.joschi.javametadata.scraper.BaseScraper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;

/** Scraper for BellSoft Liberica Native Image Kit releases */
public class LibericaNativeScraper extends BaseScraper {
    private static final String VENDOR = "liberica-native";
    private static final String API_BASE_URL = "https://api.bell-sw.com/v1/liberica/releases";
    private static final Pattern FILENAME_PATTERN =
            Pattern.compile(
                    "^bellsoft-liberica-vm-(?:openjdk|core)([0-9]+(?:u[0-9]+)?(?:\\.[0-9]+)?)-([0-9.]+(?:-[0-9]+)?)-(?:(glibc|musl)-)?(linux|windows|macos)-(amd64|aarch64|arm32-vfp-hflt)\\.(tar\\.gz|zip)$");

    public LibericaNativeScraper(Path metadataDir, Path checksumDir, Logger logger) {
        super(metadataDir, checksumDir, logger);
    }

    @Override
    public String getScraperId() {
        return "liberica-native";
    }

    @Override
    protected List<JdkMetadata> scrape() throws Exception {
        List<JdkMetadata> allMetadata = new ArrayList<>();

        // Query Liberica API for native image releases
        String apiUrl = API_BASE_URL + "?bundle-type=nik&release-type=all&page-size=1000";
        
        log("Fetching releases from " + apiUrl);
        String json = httpUtils.downloadString(apiUrl);
        JsonNode releases = objectMapper.readTree(json);

        if (!releases.isArray()) {
            log("No releases found");
            return allMetadata;
        }

        log("Found " + releases.size() + " potential releases");

        for (JsonNode release : releases) {
            try {
                JdkMetadata metadata = processRelease(release);
                if (metadata != null) {
                    allMetadata.add(metadata);
                }
            } catch (Exception e) {
                log("Failed to process release: " + e.getMessage());
            }
        }

        return allMetadata;
    }

    private JdkMetadata processRelease(JsonNode release) throws Exception {
        JsonNode downloadUrl = release.get("downloadUrl");
        if (downloadUrl == null || !downloadUrl.isTextual()) {
            return null;
        }

        String url = downloadUrl.asText();
        String filename = url.substring(url.lastIndexOf('/') + 1);

        // Skip if not a native image kit file
        if (!filename.startsWith("bellsoft-liberica-vm-")) {
            return null;
        }

        if (metadataExists(filename)) {
            log("Skipping " + filename + " (already exists)");
            return null;
        }

        Matcher matcher = FILENAME_PATTERN.matcher(filename);
        if (!matcher.matches()) {
            log("Filename doesn't match pattern: " + filename);
            return null;
        }

        String javaVersion = matcher.group(1);
        String version = matcher.group(2);
        String libcType = matcher.group(3); // null, "glibc", or "musl"
        String os = matcher.group(4);
        String arch = matcher.group(5);
        String ext = matcher.group(6);

        // Get additional info from API response
        JsonNode releaseTypeNode = release.get("releaseType");
        String apiReleaseType = releaseTypeNode != null ? releaseTypeNode.asText() : "GA";
        String releaseType = apiReleaseType.equalsIgnoreCase("EA") ? "ea" : "ga";

        // Build features list
        List<String> features = new ArrayList<>();
        features.add("native-image");
        if (libcType != null) {
            features.add(libcType);
        }

        // Download and compute hashes
        DownloadResult download = downloadFile(url, filename);

        // Create metadata
        JdkMetadata metadata = new JdkMetadata();
        metadata.setVendor(VENDOR);
        metadata.setFilename(filename);
        metadata.setReleaseType(releaseType);
        metadata.setVersion(version);
        metadata.setJavaVersion(javaVersion);
        metadata.setJvmImpl("graalvm");
        metadata.setOs(normalizeOs(os));
        metadata.setArchitecture(normalizeArch(arch));
        metadata.setFileType(ext);
        metadata.setImageType("jdk");
        metadata.setFeatures(features);
        metadata.setUrl(url);
        metadata.setMd5(download.md5());
        metadata.setMd5File(filename + ".md5");
        metadata.setSha1(download.sha1());
        metadata.setSha1File(filename + ".sha1");
        metadata.setSha256(download.sha256());
        metadata.setSha256File(filename + ".sha256");
        metadata.setSha512(download.sha512());
        metadata.setSha512File(filename + ".sha512");
        metadata.setSize(download.size());

        saveMetadataFile(metadata);
        log("Processed " + filename);

        return metadata;
    }

    public static class Discovery implements Scraper.Discovery {
        @Override
        public String name() {
            return "liberica-native";
        }

        @Override
        public String vendor() {
            return "Liberica Native Image Kit";
        }

        @Override
        public Scraper create(Path metadataDir, Path checksumDir, Logger logger) {
            return new LibericaNativeScraper(metadataDir, checksumDir, logger);
        }
    }
}
