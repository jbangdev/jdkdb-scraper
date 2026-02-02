package com.github.joschi.javametadata.scraper.vendors;

import com.github.joschi.javametadata.scraper.Scraper;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.joschi.javametadata.model.JdkMetadata;
import com.github.joschi.javametadata.scraper.BaseScraper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for Mandrel (Red Hat's downstream distribution of GraalVM) releases */
public class Mandrel extends BaseScraper {
    private static final String VENDOR = "mandrel";
    private static final String GITHUB_ORG = "graalvm";
    private static final String GITHUB_REPO = "mandrel";
    private static final String GITHUB_API_BASE = "https://api.github.com/repos";
    
    private static final Pattern FILENAME_PATTERN = Pattern.compile(
            "^mandrel-java(\\d{1,2})-(linux|macos|windows)-(amd64|aarch64)-([\\d+.]{2,}.*)\\.tar\\.gz$");

    public Mandrel(Path metadataDir, Path checksumDir, Logger logger) {
        super(metadataDir, checksumDir, logger);
    }


    @Override
    protected List<JdkMetadata> scrape() throws Exception {
        List<JdkMetadata> allMetadata = new ArrayList<>();

        log("Fetching releases from GitHub");
        String releasesUrl =
                String.format(
                        "%s/%s/%s/releases?per_page=100",
                        GITHUB_API_BASE, GITHUB_ORG, GITHUB_REPO);
        String json = httpUtils.downloadString(releasesUrl);
        JsonNode releases = objectMapper.readTree(json);

        if (!releases.isArray()) {
            log("No releases found");
            return allMetadata;
        }

        for (JsonNode release : releases) {
            String tagName = release.get("tag_name").asText();
            log("Processing release: " + tagName);

            JsonNode assets = release.get("assets");
            if (assets != null && assets.isArray()) {
                for (JsonNode asset : assets) {
                    String assetName = asset.get("name").asText();
                    
                    // Only process mandrel tar.gz files
                    if (assetName.startsWith("mandrel-") && assetName.endsWith("tar.gz")) {
                        try {
                            processAsset(tagName, assetName, allMetadata);
                        } catch (Exception e) {
                            log("Failed to process " + assetName + ": " + e.getMessage());
                        }
                    }
                }
            }
        }

        return allMetadata;
    }

    private void processAsset(String tagName, String assetName, List<JdkMetadata> allMetadata)
            throws Exception {
        
        if (metadataExists(assetName)) {
            log("Skipping " + assetName + " (already exists)");
            return;
        }

        Matcher matcher = FILENAME_PATTERN.matcher(assetName);
        if (!matcher.matches()) {
            log("Skipping " + assetName + " (does not match pattern)");
            return;
        }

        String javaVersion = matcher.group(1);
        String os = matcher.group(2);
        String arch = matcher.group(3);
        String version = matcher.group(4);

        // Determine release type
        String releaseType = determineReleaseType(version);

        String url =
                String.format(
                        "https://github.com/%s/%s/releases/download/%s/%s",
                        GITHUB_ORG, GITHUB_REPO, tagName, assetName);

        // Download and compute hashes
        DownloadResult download = downloadFile(url, assetName);

        // Create metadata
        JdkMetadata metadata = new JdkMetadata();
        metadata.setVendor(VENDOR);
        metadata.setFilename(assetName);
        metadata.setReleaseType(releaseType);
        metadata.setVersion(version + "+java" + javaVersion);
        metadata.setJavaVersion(javaVersion);
        metadata.setJvmImpl("graalvm");
        metadata.setOs(normalizeOs(os));
        metadata.setArchitecture(normalizeArch(arch));
        metadata.setFileType("tar.gz");
        metadata.setImageType("jdk");
        metadata.setFeatures(new ArrayList<>());
        metadata.setUrl(url);
        metadata.setMd5(download.md5());
        metadata.setMd5File(assetName + ".md5");
        metadata.setSha1(download.sha1());
        metadata.setSha1File(assetName + ".sha1");
        metadata.setSha256(download.sha256());
        metadata.setSha256File(assetName + ".sha256");
        metadata.setSha512(download.sha512());
        metadata.setSha512File(assetName + ".sha512");
        metadata.setSize(download.size());

        saveMetadataFile(metadata);
        allMetadata.add(metadata);
        log("Processed " + assetName);
    }

    private String determineReleaseType(String version) {
        if (version.endsWith("Final")) {
            return "ga";
        } else if (version.contains("Alpha") || version.contains("Beta")) {
            return "ea";
        }
        return "ea";
    }

    public static class Discovery implements Scraper.Discovery {
        @Override
        public String name() {
            return "mandrel";
        }

        @Override
        public String vendor() {
            return "mandrel";
        }

        @Override
        public Scraper create(Path metadataDir, Path checksumDir, Logger logger) {
            return new Mandrel(metadataDir, checksumDir, logger);
        }
    }

}
